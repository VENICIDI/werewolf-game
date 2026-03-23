package com.werewolf.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信登录响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WxLoginResponse {
    
    private Long id;
    private String username;
    private String email;
    private String avatarUrl;
    private Integer rating;
    private String token;
    
    /**
     * 是否为首次登录（新用户）
     */
    private Boolean isNewUser;
    
    /**
     * 登录类型
     */
    private String loginType;
}
