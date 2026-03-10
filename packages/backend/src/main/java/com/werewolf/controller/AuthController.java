package com.werewolf.controller;

import com.werewolf.dto.*;
import com.werewolf.entity.User;
import com.werewolf.security.JwtUtil;
import com.werewolf.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {
    
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    
    /**
     * 用户注册
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
     * 用户登录
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
