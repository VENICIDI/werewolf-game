package com.werewolf.controller;

import com.werewolf.dto.ApiResponse;
import com.werewolf.entity.User;
import com.werewolf.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 用户相关接口
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class UserController {
    
    private final UserService userService;
    
    // 头像上传目录（相对工作目录）
    private static final String UPLOAD_DIR = "uploads/avatars";
    
    /**
     * 上传头像文件
     * 返回图片的 URL 路径（/uploads/avatars/xxx.jpg）
     */
    @PostMapping("/avatar")
    public ResponseEntity<ApiResponse<?>> uploadAvatar(
            @RequestAttribute("userId") Long userId,
            @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.badRequest("文件为空"));
            }
            
            // 确保目录存在（用绝对路径，避免 transferTo 解析到 Tomcat 临时目录）
            Path uploadPath = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("创建上传目录: {}", uploadPath);
            }
            
            // 构建文件名：userId_uuid.ext
            String originalName = file.getOriginalFilename();
            String ext = ".jpg";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
                if (ext.length() > 6) ext = ".jpg"; // 防止怪异后缀
            }
            String filename = userId + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            Path filePath = uploadPath.resolve(filename);
            
            // 用 Files.copy 写入，避免 transferTo 的相对路径陷阱
            try (java.io.InputStream in = file.getInputStream()) {
                Files.copy(in, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("用户 {} 上传头像: {} -> {}", userId, filename, filePath);
            
            String url = "/uploads/avatars/" + filename;
            Map<String, String> result = new HashMap<>();
            result.put("url", url);
            return ResponseEntity.ok(ApiResponse.success("上传成功", result));
            
        } catch (IOException e) {
            log.error("头像上传失败", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest("上传失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新用户资料（昵称 + 头像 URL）
     * 头像先通过 /user/avatar 上传拿到 URL，再调这个接口保存
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<?>> updateProfile(
            @RequestAttribute("userId") Long userId,
            @RequestBody Map<String, String> body) {
        try {
            String nickname = body.get("nickname");
            String avatarUrl = body.get("avatarUrl");
            
            if ((nickname == null || nickname.isEmpty()) 
                    && (avatarUrl == null || avatarUrl.isEmpty())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.badRequest("昵称和头像至少填一个"));
            }
            
            User user = userService.updateProfile(userId, nickname, avatarUrl);
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", user.getId());
            result.put("username", user.getUsername());
            result.put("nickname", user.getNickname());
            result.put("avatarUrl", user.getAvatarUrl());
            result.put("phone", user.getPhone());
            result.put("rating", user.getRating());
            
            return ResponseEntity.ok(ApiResponse.success("资料更新成功", result));
        } catch (Exception e) {
            log.error("更新资料失败", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest("更新失败: " + e.getMessage()));
        }
    }
}
