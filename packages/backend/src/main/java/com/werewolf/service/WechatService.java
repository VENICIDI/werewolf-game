package com.werewolf.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信服务类
 * 处理与微信服务器的交互
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WechatService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${wechat.appid:}")
    private String appId;
    
    @Value("${wechat.secret:}")
    private String secret;
    
    // 微信登录凭证校验接口
    private static final String WX_AUTH_URL = 
            "https://api.weixin.qq.com/sns/jscode2session";
    
    /**
     * 通过 code 获取微信用户的 openid 和 session_key
     * 
     * @param code 微信登录凭证
     * @return 包含 openid 和 unionid 的 Map
     */
    public Map<String, String> getWxSession(String code) {
        try {
            // 构建请求 URL
            String url = String.format(
                    "%s?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                    WX_AUTH_URL, appId, secret, code
            );
            
            log.debug("请求微信登录接口: {}", url.replace(secret, "***"));
            
            // 调用微信接口
            String response = restTemplate.getForObject(url, String.class);
            log.debug("微信接口响应: {}", response);
            
            // 解析响应
            JsonNode jsonNode = objectMapper.readTree(response);
            
            // 检查错误
            if (jsonNode.has("errcode")) {
                int errCode = jsonNode.get("errcode").asInt();
                String errMsg = jsonNode.get("errmsg").asText();
                log.error("微信登录失败: code={}, msg={}", errCode, errMsg);
                throw new RuntimeException("微信登录失败: " + errMsg);
            }
            
            // 提取 openid 和 unionid
            Map<String, String> result = new HashMap<>();
            result.put("openid", jsonNode.get("openid").asText());
            
            // unionid 可能不存在（未绑定微信开放平台）
            if (jsonNode.has("unionid") && !jsonNode.get("unionid").isNull()) {
                result.put("unionid", jsonNode.get("unionid").asText());
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("获取微信 session 失败", e);
            throw new RuntimeException("微信登录失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查微信配置是否已配置
     * 
     * @return true 如果 appid 和 secret 已配置
     */
    public boolean isConfigured() {
        return appId != null && !appId.isEmpty() 
                && secret != null && !secret.isEmpty();
    }
}
