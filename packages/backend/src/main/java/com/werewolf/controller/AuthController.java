package com.werewolf.controller;

import com.werewolf.dto.*;
import com.werewolf.entity.User;
import com.werewolf.security.JwtUtil;
import com.werewolf.service.UserService;
import com.werewolf.service.WechatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class AuthController {
    
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final WechatService wechatService;
    
    /**
     * 用户注册（账号密码方式）
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<?>> register(@Valid @RequestBody RegisterRequest request) {
        try {
            // 检查用户名是否已存在
            if (userService.existsByUsername(request.getUsername())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.badRequest("用户名已存在"));
            }
            
            // 检查邮箱是否已存在
            if (userService.existsByEmail(request.getEmail())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.badRequest("邮箱已被注册"));
            }
            
            // 创建用户
            User user = userService.register(
                    request.getUsername(),
                    request.getPassword(),
                    request.getEmail()
            );
            
            // 生成 Token
            String token = jwtUtil.generateToken(user.getId(), user.getUsername());
            
            // 构建响应
            LoginResponse response = new LoginResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getAvatarUrl(),
                    user.getRating(),
                    token
            );
            
            return ResponseEntity.ok(ApiResponse.success("注册成功", response));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest("注册失败: " + e.getMessage()));
        }
    }
    
    /**
     * 用户登录（账号密码方式）
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody LoginRequest request) {
        try {
            // 认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );
            
            // 获取用户
            User user = userService.findByUsername(request.getUsername())
                    .orElseThrow(() -> new BadCredentialsException("用户不存在"));
            
            // 生成 Token
            String token = jwtUtil.generateToken(user.getId(), user.getUsername());
            
            // 构建响应
            LoginResponse response = new LoginResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getAvatarUrl(),
                    user.getRating(),
                    token
            );
            
            return ResponseEntity.ok(ApiResponse.success("登录成功", response));
            
        } catch (BadCredentialsException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest("用户名或密码错误"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest("登录失败: " + e.getMessage()));
        }
    }
    
    /**
     * 微信登录
     * 微信小程序一键登录
     */
    @PostMapping("/wx-login")
    public ResponseEntity<ApiResponse<?>> wxLogin(@Valid @RequestBody WxLoginRequest request) {
        try {
            log.info("收到微信登录请求");
            
            // 检查微信配置
            if (!wechatService.isConfigured()) {
                log.warn("微信登录功能未配置");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.badRequest("微信登录功能未配置"));
            }
            
            // 调用微信接口获取 openid
            Map<String, String> wxSession = wechatService.getWxSession(request.getCode());
            String openid = wxSession.get("openid");
            String unionid = wxSession.get("unionid");
            
            log.debug("微信登录成功, openid: {}", openid);
            
            // 查找或创建用户
            boolean isNewUser = !userService.existsByWxOpenid(openid);
            User user = userService.findOrCreateByOpenid(
                    openid, 
                    unionid, 
                    request.getNickName(), 
                    request.getAvatarUrl()
            );
            
            // 生成 Token
            String token = jwtUtil.generateToken(user.getId(), user.getUsername());
            
            // 构建响应
            WxLoginResponse response = new WxLoginResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getAvatarUrl(),
                    user.getRating(),
                    token,
                    isNewUser,
                    "WECHAT"
            );
            
            String message = isNewUser ? "微信登录成功，欢迎新用户" : "微信登录成功";
            return ResponseEntity.ok(ApiResponse.success(message, response));
            
        } catch (Exception e) {
            log.error("微信登录失败", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest("微信登录失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<?>> getCurrentUser(@RequestAttribute("userId") Long userId) {
        try {
            User user = userService.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            LoginResponse response = new LoginResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getAvatarUrl(),
                    user.getRating(),
                    null
            );
            
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest(e.getMessage()));
        }
    }
}
