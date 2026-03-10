package com.werewolf.game;

import com.werewolf.config.ConfigLoader;
import com.werewolf.config.GameConfig;
import com.werewolf.config.RoleConfig;
import com.werewolf.entity.Player;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 角色分配器 - 根据游戏配置随机分配角色
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoleAssigner {
    
    private final ConfigLoader configLoader;
    
    /**
     * 分配角色
     * @param gameModeId 游戏模式ID
     * @param players 玩家列表
     * @return 角色分配结果 Map<playerId, role>
     */
    public Map<Long, Player.Role> assignRoles(String gameModeId, List<com.werewolf.entity.Player> players) {
        GameConfig.GameMode gameMode = configLoader.getGameMode(gameModeId);
        if (gameMode == null) {
            throw new RuntimeException("未知的游戏模式: " + gameModeId);
        }
        
        // 检查人数
        if (players.size() != gameMode.getPlayerCount()) {
            throw new RuntimeException(
                String.format("玩家数量不匹配，需要 %d 人，实际 %d 人", 
                    gameMode.getPlayerCount(), players.size())
            );
        }
        
        // 构建角色池
        List<Player.Role> rolePool = buildRolePool(gameMode.getRoleDistribution());
        
        // 随机打乱
        Collections.shuffle(rolePool);
        
        // 分配角色
        Map<Long, Player.Role> assignments = new HashMap<>();
        for (int i = 0; i < players.size(); i++) {
            assignments.put(players.get(i).getId(), rolePool.get(i));
        }
        
        log.info("角色分配完成 - 模式: {}, 玩家数: {}", gameMode.getName(), players.size());
        return assignments;
    }
    
    /**
     * 构建角色池
     */
    private List<Player.Role> buildRolePool(Map<String, Integer> distribution) {
        List<Player.Role> pool = new ArrayList<>();
        
        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            String roleId = entry.getKey();
            int count = entry.getValue();
            
            Player.Role role = convertToRoleEnum(roleId);
            for (int i = 0; i < count; i++) {
                pool.add(role);
            }
        }
        
        return pool;
    }
    
    /**
     * 将字符串转换为角色枚举
     */
    private Player.Role convertToRoleEnum(String roleId) {
        return switch (roleId.toLowerCase()) {
            case "villager" -> Player.Role.VILLAGER;
            case "werewolf" -> Player.Role.WEREWOLF;
            case "seer" -> Player.Role.SEER;
            case "witch" -> Player.Role.WITCH;
            case "hunter" -> Player.Role.HUNTER;
            case "guard" -> Player.Role.GUARD;
            case "idiot" -> Player.Role.IDIOT;
            default -> throw new RuntimeException("未知的角色: " + roleId);
        };
    }
    
    /**
     * 获取角色信息
     */
    public RoleConfig.Role getRoleInfo(Player.Role role) {
        String roleId = role.name().toLowerCase();
        return configLoader.getRole(roleId);
    }
    
    /**
     * 获取角色阵营
     */
    public String getRoleCamp(Player.Role role) {
        RoleConfig.Role roleInfo = getRoleInfo(role);
        return roleInfo != null ? roleInfo.getCamp() : "unknown";
    }
    
    /**
     * 是否是狼人阵营
     */
    public boolean isWerewolfCamp(Player.Role role) {
        return "werewolf".equals(getRoleCamp(role));
    }
    
    /**
     * 是否是好人阵营
     */
    public boolean isVillagerCamp(Player.Role role) {
        return "villager".equals(getRoleCamp(role));
    }
    
    /**
     * 是否是神职
     */
    public boolean isGodRole(Player.Role role) {
        RoleConfig.Role roleInfo = getRoleInfo(role);
        return roleInfo != null && "god".equals(roleInfo.getType());
    }
    
    /**
     * 获取游戏模式列表
     */
    public List<String> getAvailableGameModes() {
        return new ArrayList<>(configLoader.getGameConfig().getGameModes().keySet());
    }
}
