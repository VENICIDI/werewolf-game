package com.werewolf.controller;

import com.werewolf.service.SttService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * STT 接口：用户语音 → 文本
 *
 * <p>路径与历史 ai-speech 服务保持兼容：前端
 * {@code packages/frontend/src/pages/game/play/index.tsx#uploadAndTranscribe}
 * 用 {@code Taro.uploadFile} 上传到 {@code POST /api/stt/transcribe}，
 * 字段名 {@code audio}（multipart）。
 *
 * <p>这层只是对 Fish Audio /v1/asr 的代理，不需要登录态。
 */
@RestController
@RequestMapping("/api/stt")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class SttController {

    private final SttService sttService;

    /**
     * @param audio    multipart 文件字段，必传
     * @param language 语言（zh/en/...），可空
     */
    @PostMapping(value = "/transcribe", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> transcribe(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "language", required = false) String language) {
        try {
            if (audio == null || audio.isEmpty()) {
                return ResponseEntity.badRequest().body(error("audio 不能为空"));
            }
            Map<String, Object> raw = sttService.transcribe(
                    audio.getBytes(),
                    audio.getOriginalFilename(),
                    language);

            // 包装成 { success, data } 与原 ai-speech 返回结构一致
            Map<String, Object> data = new HashMap<>();
            data.put("text", raw.getOrDefault("text", ""));
            data.put("language", raw.getOrDefault("language_code", language));
            data.put("duration", raw.getOrDefault("duration", null));

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("data", data);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        } catch (Exception e) {
            log.error("[STT] transcribe 失败", e);
            return ResponseEntity.status(500).body(error(e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "ok");
        body.put("provider", "fish-audio");
        return ResponseEntity.ok(body);
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }
}
