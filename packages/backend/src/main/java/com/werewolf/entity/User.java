package com.werewolf.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, length = 50)
    private String username;
    
    @Column
    private String password;
    
    @Column(unique = true, length = 100)
    private String email;
    
    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;
    
    // 手机号（微信一键登录获取）
    @Column(name = "phone", length = 20)
    private String phone;
    
    // 昵称（用户自定义，可与 username 不同）
    @Column(name = "nickname", length = 100)
    private String nickname;
    
    @Column(name = "total_games")
    private Integer totalGames = 0;
    
    @Column(name = "win_games")
    private Integer winGames = 0;
    
    @Column(name = "rating")
    private Integer rating = 1000;
    
    // 微信登录相关字段
    @Column(name = "wx_openid", unique = true, length = 100)
    private String wxOpenid;
    
    @Column(name = "wx_unionid", length = 100)
    private String wxUnionid;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "login_type", nullable = false, length = 20)
    private LoginType loginType = LoginType.APP;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * 登录类型枚举
     */
    public enum LoginType {
        APP,      // 账号密码登录
        WECHAT    // 微信登录
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
