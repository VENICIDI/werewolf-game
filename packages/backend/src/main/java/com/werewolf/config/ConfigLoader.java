package com.werewolf.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * 配置加载器 - 加载所有 JSON 配置文件
 */
@Component
@Slf4j
public class ConfigLoader {
    
    private final ObjectMapper objectMapper;
    
    // 缓存的配置
    private GameConfig gameConfig;
    private RoleConfig roleConfig;
    
    public ConfigLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        loadAllConfigs();
    }
    
    /**
     * 加载所有配置
     */
    private void loadAllConfigs() {
        try {
            log.info("开始加载游戏配置...");
            
            // 加载游戏流程配置
            gameConfig = loadConfig("game-flow.json", GameConfig.class);
            log.info("加载游戏流程配置成功，共 {} 种游戏模式", gameConfig.getGameModes().size());
            
            // 加载角色配置
            roleConfig = loadConfig("roles-complete.json", RoleConfig.class);
            log.info("加载角色配置成功，共 {} 个角色", roleConfig.getRoles().size());
            
            log.info("所有配置加载完成！");
            
        } catch (Exception e) {
            log.error("加载配置失败: {}", e.getMessage(), e);
            throw new RuntimeException("配置加载失败", e);
        }
    }
    
    /**
     * 加载单个配置文件
     */
    private <T> T loadConfig(String filename, Class<T> clazz) throws IOException {
        ClassPathResource resource = new ClassPathResource(filename);
        try (InputStream is = resource.getInputStream()) {
            return objectMapper.readValue(is, clazz);
        }
    }
    
    /**
     * 获取游戏配置
     */
    public GameConfig getGameConfig() {
        return gameConfig;
    }
    
    /**
     * 获取角色配置
     */
    public RoleConfig getRoleConfig() {
        return roleConfig;
    }
    
    /**
     * 获取指定游戏模式
     */
    public GameConfig.GameMode getGameMode(String modeId) {
        return gameConfig.getGameModes().get(modeId);
    }
    
    /**
     * 获取指定角色
     */
    public RoleConfig.Role getRole(String roleId) {
        return roleConfig.getRoles().get(roleId);
    }
    
    /**
     * 获取指定阵营
     */
    public RoleConfig.Camp getCamp(String campId) {
        return roleConfig.getCamps().get(campId);
    }
    
    /**
     * 重新加载配置
     */
    public void reload() {
        log.info("重新加载配置...");
        loadAllConfigs();
    }
}
