package com.werewolf.config;

import com.werewolf.security.UserContextInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    private final UserContextInterceptor userContextInterceptor;
    
    public WebConfig(UserContextInterceptor userContextInterceptor) {
        this.userContextInterceptor = userContextInterceptor;
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");
    }
    
    @Override
    public void addResourceHandlers(org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/avatars/**")
                .addResourceLocations("classpath:/static/avatars/");
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/");
        // 用户上传的头像，写入工作目录下 uploads/avatars/（用绝对路径更稳）
        String uploadAbsPath = java.nio.file.Paths.get("uploads/avatars").toAbsolutePath().normalize().toString();
        if (!uploadAbsPath.endsWith("/")) uploadAbsPath += "/";
        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations("file:" + uploadAbsPath);
    }
    
    /**
     * RestTemplate Bean，用于调用微信接口
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
