package com.werewolf.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
    
    // 获取 access_token 接口
    private static final String WX_ACCESS_TOKEN_URL =
            "https://api.weixin.qq.com/cgi-bin/token";
    
    // 通过 phoneCode 拿手机号接口（新版）
    private static final String WX_PHONE_URL =
            "https://api.weixin.qq.com/wxa/business/getuserphonenumber";
    
    // 简单内存缓存 access_token（有效期 7200 秒，提前 300 秒刷新）
    private final AtomicReference<String> cachedAccessToken = new AtomicReference<>();
    private volatile long accessTokenExpireAt = 0L;
    
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
            if (jsonNode.has("errcode") && jsonNode.get("errcode").asInt() != 0) {
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
     * 获取小程序 access_token（带内存缓存）
     */
    private String getAccessToken() {
        long now = System.currentTimeMillis() / 1000;
        String token = cachedAccessToken.get();
        if (token != null && now < accessTokenExpireAt) {
            return token;
        }
        
        try {
            String url = String.format(
                    "%s?grant_type=client_credential&appid=%s&secret=%s",
                    WX_ACCESS_TOKEN_URL, appId, secret
            );
            log.debug("请求 access_token: {}", url.replace(secret, "***"));
            String response = restTemplate.getForObject(url, String.class);
            log.debug("access_token 响应: {}", response != null ? response.replaceAll("\"access_token\":\"[^\"]+\"", "\"access_token\":\"***\"") : "null");
            
            JsonNode json = objectMapper.readTree(response);
            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                throw new RuntimeException("获取 access_token 失败: " + json.get("errmsg").asText());
            }
            
            String newToken = json.get("access_token").asText();
            int expiresIn = json.has("expires_in") ? json.get("expires_in").asInt() : 7200;
            cachedAccessToken.set(newToken);
            // 提前 300 秒过期
            accessTokenExpireAt = now + expiresIn - 300;
            return newToken;
        } catch (Exception e) {
            log.error("获取 access_token 失败", e);
            throw new RuntimeException("获取 access_token 失败: " + e.getMessage());
        }
    }
    
    /**
     * 通过 phoneCode 获取用户手机号
     * 小程序 <button open-type="getPhoneNumber"> 回调里拿到的 code
     * 
     * @param phoneCode 手机号授权 code
     * @return purePhoneNumber（纯手机号，无区号），失败返回 null
     */
    public String getPhoneNumber(String phoneCode) {
        try {
            String accessToken = getAccessToken();
            String url = WX_PHONE_URL + "?access_token=" + accessToken;
            
            Map<String, String> body = new HashMap<>();
            body.put("code", phoneCode);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            
            log.debug("请求获取手机号接口, phoneCode: {}", phoneCode);
            String response = restTemplate.postForObject(url, entity, String.class);
            log.debug("获取手机号响应: {}", response);
            
            JsonNode json = objectMapper.readTree(response);
            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                String errMsg = json.has("errmsg") ? json.get("errmsg").asText() : "未知错误";
                log.error("获取手机号失败: errcode={}, errmsg={}", json.get("errcode").asInt(), errMsg);
                throw new RuntimeException("获取手机号失败: " + errMsg);
            }
            
            JsonNode phoneInfo = json.get("phone_info");
            if (phoneInfo == null) {
                throw new RuntimeException("微信返回的 phone_info 为空");
            }
            
            String purePhoneNumber = phoneInfo.has("purePhoneNumber") 
                    ? phoneInfo.get("purePhoneNumber").asText() 
                    : phoneInfo.get("phoneNumber").asText();
            log.info("成功获取手机号: {}", maskPhone(purePhoneNumber));
            return purePhoneNumber;
            
        } catch (Exception e) {
            log.error("获取手机号失败", e);
            throw new RuntimeException("获取手机号失败: " + e.getMessage());
        }
    }
    
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(7);
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
