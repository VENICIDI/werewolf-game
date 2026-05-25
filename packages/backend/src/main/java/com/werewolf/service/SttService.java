package com.werewolf.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ASR 代理服务（SiliconFlow SenseVoiceSmall）
 *
 * <p>2026-05-24 从 Fish Audio 迁移到 SiliconFlow，
 * 原因：国内服务器无法直连 api.fish.audio（连接超时）。
 *
 * <p>API 格式兼容 OpenAI Audio Transcriptions：
 * <pre>POST https://api.siliconflow.cn/v1/audio/transcriptions</pre>
 *
 * <p>响应保持与原 ai-speech /api/stt/transcribe 一致：
 * <pre>{ "success": true, "data": { "text": "...", "language": "zh", "duration": 5.17 } }</pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SttService {

    private final RestTemplate restTemplate;

    @Value("${stt.api-key:${siliconflow.stt.api-key:}}")
    private String apiKey;

    @Value("${stt.api-url:https://api.siliconflow.cn/v1/audio/transcriptions}")
    private String apiUrl;

    @Value("${stt.model:FunAudioLLM/SenseVoiceSmall}")
    private String model;

    /**
     * 上传一段音频，返回识别结果。
     *
     * @param audioBytes 音频原始字节（建议 mp3/wav，<=20MB）
     * @param filename   原文件名（透传给 API，便于按扩展名识别）
     * @param language   语言代码（zh/en/...），可空
     * @return 响应 Map（包含 text 等字段）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> transcribe(byte[] audioBytes, String filename, String language) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("stt.api-key 未配置");
        }
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException("audio 为空");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // 用 ByteArrayResource 给 multipart 一个虚拟文件名
        ByteArrayResource audioResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return (filename == null || filename.isBlank()) ? "audio.mp3" : filename;
            }
        };

        // SiliconFlow 兼容 OpenAI Audio Transcriptions 格式
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", audioResource);
        body.add("model", model);
        if (language != null && !language.isBlank()) {
            body.add("language", language);
        }

        HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(body, headers);

        long start = System.currentTimeMillis();
        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(apiUrl, req, Map.class);
            long cost = System.currentTimeMillis() - start;
            Map<String, Object> data = resp.getBody();
            String text = data == null ? null : String.valueOf(data.getOrDefault("text", ""));
            log.info("[STT] SiliconFlow ok, audio={}B, cost={}ms, text_len={}",
                    audioBytes.length, cost, text == null ? 0 : text.length());
            if (data == null) {
                throw new RuntimeException("ASR 返回空响应");
            }
            return data;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("[STT] SiliconFlow 调用失败: cost={}ms, err={}", cost, e.getMessage());
            throw new RuntimeException("STT 转写失败: " + e.getMessage(), e);
        }
    }
}
