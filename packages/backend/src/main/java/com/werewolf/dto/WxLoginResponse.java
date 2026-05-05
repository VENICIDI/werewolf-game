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
    private String nickname;
    private String email;
    private String avatarUrl;
    private String phone;
    private Integer rating;
    private String token;
    
    /**
     * 是否为首次登录（新用户）
     */
    private Boolean isNewUser;
    
    /**
     * 是否需要完善资料（没头像或没昵称时为 true）
     */
    private Boolean needProfileSetup;
    
    /**
     * 登录类型
     */
    private String loginType;
}
