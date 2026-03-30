package com.werewolf.game;

import com.werewolf.config.ConfigLoader;
import com.werewolf.config.GameConfig;
import com.werewolf.entity.Game;
import com.werewolf.service.GameService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 阶段调度器 - 管理游戏阶段执行
 * 使用 ApplicationContext 延迟获取 GameService 避免循环依赖
 */
@Component
@Slf4j
public class PhaseScheduler {

    private final ConfigLoader configLoader;
    private final ApplicationContext applicationContext;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);

    // 存储正在运行的游戏任务
    private final Map<Long, ScheduledFuture<?>> gameTasks = new ConcurrentHashMap<>();

    // 存储游戏模式（用于夜晚结束后重新进入）
    private final Map<Long, GameConfig.GameMode> gameModes = new ConcurrentHashMap<>();

    // GameService 延迟获取
    private GameService gameService;

    public PhaseScheduler(ConfigLoader configLoader, ApplicationContext applicationContext) {
        this.configLoader = configLoader;
        this.applicationContext = applicationContext;
    }

    private GameService getGameService() {
        if (gameService == null) {
            gameService = applicationContext.getBean(GameService.class);
        }
        return gameService;
    }

    /**
     * 开始游戏阶段调度
     */
    public void startGame(Long gameId, String gameModeId) {
        GameConfig.GameMode gameMode = configLoader.getGameMode(gameModeId);
        if (gameMode == null) {
            throw new RuntimeException("未知的游戏模式: " + gameModeId);
        }

        gameModes.put(gameId, gameMode);
        log.info("开始游戏阶段调度 - 游戏ID: {}, 模式: {}", gameId, gameMode.getName());

        // 启动夜晚第一个阶段
        scheduleNextPhase(gameId, gameMode, 0, true);
    }

    /**
     * 调度下一阶段
     */
    private void scheduleNextPhase(Long gameId, GameConfig.GameMode gameMode,
                                   int phaseIndex, boolean isNight) {
        List<GameConfig.PhaseConfig> phases = isNight ?
                gameMode.getFlow().getNight() : gameMode.getFlow().getDay();

        if (phases == null || phaseIndex >= phases.size()) {
            if (isNight) {
                // 夜晚阶段全部结束 → 结算夜晚 → 进入白天
                log.info("夜晚阶段结束，开始结算");
                executor.schedule(() -> {
                    try {
                        getGameService().resolveNight(gameId);
                        // 检查胜负
                        Game game = getGameService().getGameStatus(gameId);
                        if (game.getStatus() == Game.GameStatus.FINISHED) return;
                        // 进入白天
                        scheduleNextPhase(gameId, gameMode, 0, false);
                    } catch (Exception e) {
                        log.error("夜晚结算失败 - 游戏: {}", gameId, e);
                    }
                }, 1, TimeUnit.SECONDS);
            } else {
                // 白天阶段全部结束 → 检查胜负 → 进入新一夜
                log.info("白天阶段结束，检查胜负");
                executor.schedule(() -> {
                    try {
                        getGameService().checkWinCondition(gameId);
                        Game game = getGameService().getGameStatus(gameId);
                        if (game.getStatus() == Game.GameStatus.FINISHED) return;
                        // 进入新一夜
                        getGameService().enterNewNight(gameId);
                        scheduleNextPhase(gameId, gameMode, 0, true);
                    } catch (Exception e) {
                        log.error("白天结算失败 - 游戏: {}", gameId, e);
                    }
                }, 1, TimeUnit.SECONDS);
            }
            return;
        }

        GameConfig.PhaseConfig phase = phases.get(phaseIndex);
        int durationMs = Math.max(phase.getDuration(), 1000); // 最少1秒

        log.info("调度阶段 - 游戏: {}, 阶段: {}, 时长: {}ms, 夜晚: {}",
                gameId, phase.getPhase(), durationMs, isNight);

        // 立即执行阶段开始
        executor.execute(() -> {
            try {
                executePhaseStart(gameId, phase);
            } catch (Exception e) {
                log.error("阶段开始失败 - 游戏: {}, 阶段: {}", gameId, phase.getPhase(), e);
            }
        });

        // 定时执行阶段结束，进入下一阶段
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                executePhaseEnd(gameId, phase);

                // 特殊处理：投票阶段结束时强制结算
                if ("voting".equals(phase.getPhase())) {
                    getGameService().resolveVoting(gameId);
                }

                // 调度下一阶段
                scheduleNextPhase(gameId, gameMode, phaseIndex + 1, isNight);
            } catch (Exception e) {
                log.error("阶段结束失败 - 游戏: {}, 阶段: {}", gameId, phase.getPhase(), e);
            }
        }, durationMs, TimeUnit.MILLISECONDS);

        gameTasks.put(gameId, future);
    }

    /**
     * 执行阶段开始
     */
    private void executePhaseStart(Long gameId, GameConfig.PhaseConfig phase) {
        log.info("阶段开始 - 游戏: {}, 阶段: {}", gameId, phase.getPhase());

        // 根据阶段类型更新游戏状态
        Game.GamePhase gamePhase = mapToGamePhase(phase.getPhase());
        if (gamePhase != null) {
            getGameService().updatePhase(gameId, gamePhase);
        }

        // 广播阶段消息
        if (phase.getBroadcast() != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("phase", phase.getPhase());
            data.put("message", phase.getBroadcast());
            data.put("duration", phase.getDuration());
            getGameService().broadcastToGame(gameId, "PHASE_CHANGE", data);
        }

        // 特殊处理
        switch (phase.getPhase()) {
            case "voting":
                getGameService().startVoting(gameId);
                break;
            case "announce_death":
                // 死亡公告在 resolveNight 中已处理
                break;
        }
    }

    /**
     * 执行阶段结束
     */
    private void executePhaseEnd(Long gameId, GameConfig.PhaseConfig phase) {
        log.info("阶段结束 - 游戏: {}, 阶段: {}", gameId, phase.getPhase());

        if (phase.getEndBroadcast() != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("phase", phase.getPhase());
            data.put("message", phase.getEndBroadcast());
            getGameService().broadcastToGame(gameId, "SYSTEM", data);
        }
    }

    /**
     * 映射阶段名到枚举
     */
    private Game.GamePhase mapToGamePhase(String phaseName) {
        return switch (phaseName) {
            case "night_start" -> Game.GamePhase.NIGHT_START;
            case "guard_action" -> Game.GamePhase.GUARD;
            case "werewolf_action" -> Game.GamePhase.WEREWOLF;
            case "seer_action" -> Game.GamePhase.SEER;
            case "witch_action" -> Game.GamePhase.WITCH;
            case "day_start" -> Game.GamePhase.DAY_START;
            case "announce_death" -> Game.GamePhase.DAY_START;
            case "discussion" -> Game.GamePhase.DISCUSSION;
            case "voting" -> Game.GamePhase.VOTING;
            case "execution" -> Game.GamePhase.EXECUTION;
            default -> null;
        };
    }

    /**
     * 停止游戏调度
     */
    public void stopGame(Long gameId) {
        ScheduledFuture<?> future = gameTasks.remove(gameId);
        if (future != null) {
            future.cancel(false);
        }
        gameModes.remove(gameId);
        log.info("停止游戏调度 - 游戏ID: {}", gameId);
    }

    /**
     * 暂停游戏
     */
    public void pauseGame(Long gameId) {
        ScheduledFuture<?> future = gameTasks.get(gameId);
        if (future != null) {
            future.cancel(false);
        }
        log.info("暂停游戏 - 游戏ID: {}", gameId);
    }

    /**
     * 恢复游戏
     */
    public void resumeGame(Long gameId) {
        GameConfig.GameMode gameMode = gameModes.get(gameId);
        if (gameMode != null) {
            // 简化：重新从当前阶段开始
            log.info("恢复游戏 - 游戏ID: {}", gameId);
        }
    }
}
