package com.werewolf.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fish Audio TTS 代理服务
 *
 * <p>把前端的 TTS 请求转发到 Fish Audio 官方 HTTP API：
 * <pre>POST https://api.fish.audio/v1/tts</pre>
 *
 * <p>之所以走后端代理而不是前端直调，是为了不把 api-key 暴露在小程序包里。
 *
 * <p>响应给前端的格式与历史 ai-speech 服务保持一致：
 * <pre>
 * { "success": true,
 *   "data": { "audio_base64": "...", "content_type": "audio/mpeg", "size": 12345 } }
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TtsService {

    private final RestTemplate restTemplate;

    @Value("${fish-audio.api-key:}")
    private String apiKey;

    @Value("${fish-audio.api-url:https://api.fish.audio/v1/tts}")
    private String apiUrl;

    @Value("${fish-audio.model:s2-pro}")
    private String model;

    /** 可选，默认音色（reference_id），不配则用 Fish Audio 默认音色。 */
    @Value("${fish-audio.reference-id:}")
    private String defaultReferenceId;

    /** 单次合成上限，与原 ai-speech 行为保持一致。前端会先截断，这里再做兜底。 */
    private static final int MAX_TEXT_LEN = 500;

    /**
     * 合成一段文本，返回 mp3 字节流。
     *
     * @param text          要合成的文本
     * @param speakingRate  语速倍率（0.5~2.0），传 null 用默认 1.0
     * @return mp3 字节
     * @throws IllegalStateException 配置缺失
     * @throws RuntimeException      Fish Audio 调用失败
     */
    public byte[] synthesize(String text, Double speakingRate) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("fish-audio.api-key 未配置");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }

        String safeText = text.length() > MAX_TEXT_LEN ? text.substring(0, MAX_TEXT_LEN) : text;
        double rate = speakingRate == null ? 1.0 : Math.max(0.5, Math.min(2.0, speakingRate));

        // 用 LinkedHashMap 保持字段顺序，方便排查日志
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", safeText);
        if (defaultReferenceId != null && !defaultReferenceId.isBlank()) {
            body.put("reference_id", defaultReferenceId);
        }
        body.put("temperature", 0.7);
        body.put("top_p", 0.7);

        Map<String, Object> prosody = new LinkedHashMap<>();
        prosody.put("speed", rate);
        prosody.put("volume", 0);
        prosody.put("normalize_loudness", true);
        body.put("prosody", prosody);

        body.put("chunk_length", 300);
        body.put("normalize", true);
        body.put("format", "mp3");
        body.put("sample_rate", 44100);
        body.put("mp3_bitrate", 128);
        body.put("latency", "normal");
        body.put("max_new_tokens", 1024);
        body.put("repetition_penalty", 1.2);
        body.put("min_chunk_length", 50);
        body.put("condition_on_previous_chunks", true);
        body.put("early_stop_threshold", 1);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Fish Audio 支持通过自定义 header 切换模型版本（s2-pro / speech-1.6 / ...）
        headers.set("model", model);

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        long start = System.currentTimeMillis();
        try {
            ResponseEntity<byte[]> resp = restTemplate.postForEntity(apiUrl, req, byte[].class);
            byte[] audio = resp.getBody();
            long cost = System.currentTimeMillis() - start;
            int size = audio == null ? 0 : audio.length;
            log.info("[TTS] fish-audio ok, text_len={}, rate={}, mp3_size={}B, cost={}ms",
                    safeText.length(), rate, size, cost);
            if (audio == null || audio.length == 0) {
                throw new RuntimeException("Fish Audio 返回空响应");
            }
            return audio;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("[TTS] fish-audio 调用失败: cost={}ms, err={}", cost, e.getMessage());
            throw new RuntimeException("TTS 合成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 合成并按 base64 包装为前端期望的 JSON 结构。
     */
    public Map<String, Object> synthesizeJson(String text, Double speakingRate) {
        byte[] audio = synthesize(text, speakingRate);
        String b64 = Base64.getEncoder().encodeToString(audio);

        Map<String, Object> data = new HashMap<>();
        data.put("audio_base64", b64);
        data.put("content_type", "audio/mpeg");
        data.put("size", audio.length);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("data", data);
        return resp;
    }
}
