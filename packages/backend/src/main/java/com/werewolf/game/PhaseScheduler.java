package com.werewolf.game;

import com.werewolf.config.ConfigLoader;
import com.werewolf.config.GameConfig;
import com.werewolf.entity.Game;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 阶段调度器 - 管理游戏阶段执行
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PhaseScheduler {
    
    private final ConfigLoader configLoader;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    // 存储正在运行的游戏任务
    private final Map<Long, ScheduledFuture<?>> gameTasks = new ConcurrentHashMap<>();
    
    /**
     * 开始游戏阶段调度
     */
    public void startGame(Long gameId, String gameModeId) {
        GameConfig.GameMode gameMode = configLoader.getGameMode(gameModeId);
        if (gameMode == null) {
            throw new RuntimeException("未知的游戏模式: " + gameModeId);
        }
        
        log.info("开始游戏阶段调度 - 游戏ID: {}, 模式: {}", gameId, gameMode.getName());
        
        // 启动第一阶段
        scheduleNextPhase(gameId, gameMode, 0, true);
    }
    
    /**
     * 调度下一阶段
     */
    private void scheduleNextPhase(Long gameId, GameConfig.GameMode gameMode, 
                                   int phaseIndex, boolean isNight) {
        List<GameConfig.PhaseConfig> phases = isNight ? 
            gameMode.getFlow().getNight() : gameMode.getFlow().getDay();
        
        if (phaseIndex >= phases.size()) {
            // 当前阶段结束，切换昼夜
            if (isNight) {
                log.info("夜晚阶段结束，进入白天");
                scheduleNextPhase(gameId, gameMode, 0, false);
            } else {
                log.info("白天阶段结束，进入夜晚");
                scheduleNextPhase(gameId, gameMode, 0, true);
            }
            return;
        }
        
        GameConfig.PhaseConfig phase = phases.get(phaseIndex);
        
        // 调度阶段执行
        ScheduledFuture<?> future = CompletableFuture.delayedExecutor(
            phase.getDuration(), TimeUnit.MILLISECONDS, executor
        ).execute(() -> {
            try {
                executePhase(gameId, phase);
                
                // 调度下一阶段
                scheduleNextPhase(gameId, gameMode, phaseIndex + 1, isNight);
                
            } catch (Exception e) {
                log.error("阶段执行失败 - 游戏ID: {}, 阶段: {}", gameId, phase.getPhase(), e);
            }
        });
        
        gameTasks.put(gameId, future);
    }
    
    /**
     * 执行阶段
     */
    private void executePhase(Long gameId, GameConfig.PhaseConfig phase) {
        log.info("执行阶段 - 游戏ID: {}, 阶段: {}, 时长: {}ms", 
            gameId, phase.getPhase(), phase.getDuration());
        
        // 广播阶段开始
        broadcastPhaseStart(gameId, phase);
        
        // 根据阶段类型执行不同逻辑
        switch (phase.getPhase()) {
            case "night_start":
                handleNightStart(gameId, phase);
                break;
            case "werewolf_action":
                handleWerewolfAction(gameId, phase);
                break;
            case "seer_action":
                handleSeerAction(gameId, phase);
                break;
            case "witch_action":
                handleWitchAction(gameId, phase);
                break;
            case "day_start":
                handleDayStart(gameId, phase);
                break;
            case "announce_death":
                handleAnnounceDeath(gameId, phase);
                break;
            case "discussion":
                handleDiscussion(gameId, phase);
                break;
            case "voting":
                handleVoting(gameId, phase);
                break;
            case "execution":
                handleExecution(gameId, phase);
                break;
            default:
                log.warn("未知的阶段: {}", phase.getPhase());
        }
        
        // 广播阶段结束
        if (phase.getEndBroadcast() != null) {
            broadcastPhaseEnd(gameId, phase);
        }
    }
    
    /**
     * 处理夜晚开始
     */
    private void handleNightStart(Long gameId, GameConfig.PhaseConfig phase) {
        log.info("夜晚开始 - 游戏ID: {}", gameId);
        // 更新游戏阶段
        updateGamePhase(gameId, Game.GamePhase.NIGHT_START);
    }
    
    /**
     * 处理狼人行动
     */
    private void handleWerewolfAction(Long gameId, GameConfig.PhaseConfig phase) {
        log.info("狼人行动阶段 - 游戏ID: {}", gameId);
        updateGamePhase(gameId, Game.GamePhase.WEREWOLF);
        // TODO: 等待狼人选择目标
    }
    
    /**
     * 处理预言家行动
     */
    private void handleSeerAction(Long gameId, GameConfig.PhaseConfig phase) {
        log.info("预言家行动阶段 - 游戏ID: {}", gameId);
        updateGamePhase(gameId, Game.GamePhase.SEER);
    }
    
    /**
     * 处理女巫行动
     */
    private void handleWitchAction(Long gameId, GameConfig.PhaseConfig phase) {
        log.info("女巫行动阶段 - 游戏ID: {}", gameId);
        updateGamePhase(gameId, Game.GamePhase.WITCH);
    }
    
    /**
     * 处理白天开始
     */
    private void handleDayStart(Long gameId, GameConfig.PhaseConfig phase) {
        log.info("白天开始 - 游戏ID: {}", gameId);
        updateGamePhase(gameId, Game.GamePhase.DAY_START);
    }
    
    /**
     * 处理公布死亡
     */
    private void handleAnnounceDeath(Long gameId, GameConfig.PhaseConfig phase) {
        log.info("公布死亡 - 游戏ID: {}", gameId);
        updateGamePhase(gameId, Game.GamePhase.NONE);
        // TODO: 公布昨晚死亡玩家
    }
    
    /**
     * 处理讨论阶段
     */
    private void handleDiscussion(Long gameId, GameConfig.PhaseConfig phase) {
        log.info("讨论阶段 - 游戏ID: {}", gameId);
        updateGamePhase(gameId, Game.GamePhase.DISCUSSION);
    }
    
    /**
     * 处理投票阶段
     */
    private void handleVoting(Long gameId, GameConfig.PhaseConfig phase) {
        log.info("投票阶段 - 游戏ID: {}", gameId);
        updateGamePhase(gameId, Game.GamePhase.VOTING);
    }
    
    /**
     * 处理处决阶段
     */
    private void handleExecution(Long gameId, GameConfig.PhaseConfig phase) {
        log.info("处决阶段 - 游戏ID: {}", gameId);
        updateGamePhase(gameId, Game.GamePhase.EXECUTION);
        // TODO: 执行处决
    }
    
    /**
     * 更新游戏阶段
     */
    private void updateGamePhase(Long gameId, Game.GamePhase phase) {
        // TODO: 更新数据库中的游戏阶段
        log.info("更新游戏阶段 - 游戏ID: {}, 阶段: {}", gameId, phase);
    }
    
    /**
     * 广播阶段开始
     */
    private void broadcastPhaseStart(Long gameId, GameConfig.PhaseConfig phase) {
        if (phase.getBroadcast() != null) {
            log.info("广播 - 游戏ID: {}, 消息: {}", gameId, phase.getBroadcast());
            // TODO: WebSocket 广播
        }
    }
    
    /**
     * 广播阶段结束
     */
    private void broadcastPhaseEnd(Long gameId, GameConfig.PhaseConfig phase) {
        log.info("广播 - 游戏ID: {}, 消息: {}", gameId, phase.getEndBroadcast());
        // TODO: WebSocket 广播
    }
    
    /**
     * 停止游戏调度
     */
    public void stopGame(Long gameId) {
        ScheduledFuture<?> future = gameTasks.remove(gameId);
        if (future != null) {
            future.cancel(false);
            log.info("停止游戏调度 - 游戏ID: {}", gameId);
        }
    }
    
    /**
     * 暂停游戏
     */
    public void pauseGame(Long gameId) {
        // TODO: 实现暂停逻辑
        log.info("暂停游戏 - 游戏ID: {}", gameId);
    }
    
    /**
     * 恢复游戏
     */
    public void resumeGame(Long gameId) {
        // TODO: 实现恢复逻辑
        log.info("恢复游戏 - 游戏ID: {}", gameId);
    }
}
