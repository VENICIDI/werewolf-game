package com.werewolf.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信登录请求 DTO
 */
@Data
public class WxLoginRequest {
    
    /**
     * 微信登录凭证（code）
     * 通过 wx.login() 获取
     */
    @NotBlank(message = "微信登录凭证不能为空")
    private String code;
    
    /**
     * 用户昵称（可选）
     */
    private String nickName;
    
    /**
     * 用户头像（可选）
     */
    private String avatarUrl;
}
