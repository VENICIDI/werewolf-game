package com.werewolf.controller;

import com.werewolf.service.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * TTS 接口
 *
 * <p>路径与历史 ai-speech 服务保持兼容：前端 {@code packages/frontend/src/utils/tts.ts}
 * 调用 {@code POST /api/tts/synthesize/json}，把返回里的 base64 写本地文件再播。
 *
 * <p>这层只是对 Fish Audio 的代理：
 * <ul>
 *   <li>不需要登录态（前端在游戏页直接调）</li>
 *   <li>SecurityConfig 里 {@code /api/tts/**} 已加 permitAll</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class TtsController {

    private final TtsService ttsService;

    /**
     * 合成并以 base64 JSON 返回，给小程序/H5 用。
     */
    @PostMapping("/synthesize/json")
    public ResponseEntity<Map<String, Object>> synthesizeJson(@RequestBody SynthesizeRequest req) {
        try {
            Map<String, Object> resp = ttsService.synthesizeJson(req.text, req.speaking_rate);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        } catch (Exception e) {
            log.error("[TTS] synthesizeJson 失败", e);
            return ResponseEntity.status(500).body(error(e.getMessage()));
        }
    }

    /**
     * 合成并直接返回 audio/mpeg 字节流。可选用，方便用 <audio src="..."> 直链。
     */
    @PostMapping(value = "/synthesize", produces = "audio/mpeg")
    public ResponseEntity<byte[]> synthesize(@RequestBody SynthesizeRequest req) {
        try {
            byte[] audio = ttsService.synthesize(req.text, req.speaking_rate);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .body(audio);
        } catch (Exception e) {
            log.error("[TTS] synthesize 失败", e);
            return ResponseEntity.status(500).build();
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

    /** 请求体。字段名故意用下划线，与历史 ai-speech 接口保持一致，前端无需改。 */
    public static class SynthesizeRequest {
        public String text;
        public Double speaking_rate;
    }
}
