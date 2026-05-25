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
 * TTS 代理服务（SiliconFlow CosyVoice2）
 *
 * <p>2026-05-24 从 Fish Audio 迁移到 SiliconFlow，
 * 原因：国内服务器无法直连 api.fish.audio（连接超时）。
 *
 * <p>API 格式兼容 OpenAI Audio Speech：
 * <pre>POST https://api.siliconflow.cn/v1/audio/speech</pre>
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

    @Value("${tts.api-key:${siliconflow.tts.api-key:}}")
    private String apiKey;

    @Value("${tts.api-url:https://api.siliconflow.cn/v1/audio/speech}")
    private String apiUrl;

    @Value("${tts.model:FunAudioLLM/CosyVoice2-0.5B}")
    private String model;

    @Value("${tts.voice:FunAudioLLM/CosyVoice2-0.5B:alex}")
    private String defaultVoice;

    /** 单次合成上限，与原 ai-speech 行为保持一致。前端会先截断，这里再做兜底。 */
    private static final int MAX_TEXT_LEN = 500;

    /**
     * 合成一段文本，返回 mp3 字节流。
     *
     * @param text          要合成的文本
     * @param speakingRate  语速倍率（0.5~2.0），传 null 用默认 1.0（当前接口不支持速率调节，忽略此参数）
     * @return mp3 字节
     * @throws IllegalStateException 配置缺失
     * @throws RuntimeException      TTS 调用失败
     */
    public byte[] synthesize(String text, Double speakingRate) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("tts.api-key 未配置");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }

        String safeText = text.length() > MAX_TEXT_LEN ? text.substring(0, MAX_TEXT_LEN) : text;

        // SiliconFlow TTS 兼容 OpenAI Audio Speech 格式
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", safeText);
        body.put("voice", defaultVoice);
        body.put("response_format", "mp3");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        long start = System.currentTimeMillis();
        try {
            ResponseEntity<byte[]> resp = restTemplate.postForEntity(apiUrl, req, byte[].class);
            byte[] audio = resp.getBody();
            long cost = System.currentTimeMillis() - start;
            int size = audio == null ? 0 : audio.length;
            log.info("[TTS] SiliconFlow ok, text_len={}, mp3_size={}B, cost={}ms",
                    safeText.length(), size, cost);
            if (audio == null || audio.length == 0) {
                throw new RuntimeException("TTS 返回空响应");
            }
            return audio;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("[TTS] SiliconFlow 调用失败: cost={}ms, err={}", cost, e.getMessage());
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
