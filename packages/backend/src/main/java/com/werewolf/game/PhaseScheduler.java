package com.werewolf.game;

import com.werewolf.ai.AIPlayerBridge;
import com.werewolf.config.ConfigLoader;
import com.werewolf.config.GameConfig;
import com.werewolf.entity.Game;
import com.werewolf.logging.GameLogger;
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

    // ✨ 投票结算幂等标志已内聚到 VoteSession.resolved,此处不再维护 Set

    // 存储游戏模式（用于夜晚结束后重新进入）
    private final Map<Long, GameConfig.GameMode> gameModes = new ConcurrentHashMap<>();

    // ✨ 当前阶段的上下文 (gameId -> PhaseContext)，用于"全员提交即推进"
    private final Map<Long, PhaseContext> activePhase = new ConcurrentHashMap<>();

    /**
     * 阶段运行上下文
     * 包含绝对时间戳,供断线重连/快照同步使用,避免前端本地计时漂移
     *
     * public 可见度以便 GameService (com.werewolf.service) 通过 getActivePhase 读取
     */
    public static class PhaseContext {
        final GameConfig.GameMode gameMode;
        final GameConfig.PhaseConfig phase;
        final int phaseIndex;
        final boolean isNight;
        final java.util.concurrent.atomic.AtomicBoolean advanced = new java.util.concurrent.atomic.AtomicBoolean(false);
        /** 阶段开始绝对时间(epoch ms) */
        final long phaseStartedAt;
        /** 阶段预计结束绝对时间(epoch ms) = phaseStartedAt + effectiveDurationMs */
        final long phaseEndsAt;
        /** ✨ 本阶段实际使用的时长(ms) — 讨论阶段会按存活人数动态调整,与 phase.duration 可能不同 */
        final int effectiveDurationMs;

        PhaseContext(GameConfig.GameMode gameMode, GameConfig.PhaseConfig phase, int phaseIndex,
                     boolean isNight, int effectiveDurationMs) {
            this.gameMode = gameMode;
            this.phase = phase;
            this.phaseIndex = phaseIndex;
            this.isNight = isNight;
            this.effectiveDurationMs = Math.max(effectiveDurationMs, 1000);
            this.phaseStartedAt = System.currentTimeMillis();
            this.phaseEndsAt = this.phaseStartedAt + this.effectiveDurationMs;
        }

        public GameConfig.PhaseConfig getPhase() { return phase; }
        public long getPhaseStartedAt() { return phaseStartedAt; }
        public long getPhaseEndsAt() { return phaseEndsAt; }
        public boolean isNight() { return isNight; }
        public int getEffectiveDurationMs() { return effectiveDurationMs; }
    }

    // GameService 延迟获取
    private GameService gameService;
    
    // AIPlayerBridge 延迟获取
    private AIPlayerBridge aiPlayerBridge;

    // DiscussionManager 延迟获取
    private DiscussionManager discussionManager;

    // VoteManager 延迟获取(用于投票结算幂等判定)
    private VoteManager voteManager;

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
    
    private AIPlayerBridge getAIPlayerBridge() {
        if (aiPlayerBridge == null) {
            aiPlayerBridge = applicationContext.getBean(AIPlayerBridge.class);
        }
        return aiPlayerBridge;
    }

    private DiscussionManager getDiscussionManager() {
        if (discussionManager == null) {
            discussionManager = applicationContext.getBean(DiscussionManager.class);
        }
        return discussionManager;
    }

    private VoteManager getVoteManager() {
        if (voteManager == null) {
            voteManager = applicationContext.getBean(VoteManager.class);
        }
        return voteManager;
    }

    /**
     * 开始游戏阶段调度
     */
    public void startGame(Long gameId, String gameModeId, List<com.werewolf.entity.Player> players) {
        GameConfig.GameMode gameMode = configLoader.getGameMode(gameModeId);
        if (gameMode == null) {
            throw new RuntimeException("未知的游戏模式: " + gameModeId);
        }

        gameModes.put(gameId, gameMode);
        log.info("开始游戏阶段调度 - 游戏ID: {}, 模式: {}", gameId, gameMode.getName());

        // 延迟 2 秒后启动（确保事务已提交 + Agent 初始化完成）
        executor.schedule(() -> {
            try {
                // 先在 AI Service 中创建 Agent 实例
                getAIPlayerBridge().initializeAgents(gameId, players);
                // 启动夜晚第一个阶段
                scheduleNextPhase(gameId, gameMode, 0, true);
            } catch (Exception e) {
                log.error("游戏启动失败 - 游戏: {}", gameId, e);
            }
        }, 2, TimeUnit.SECONDS);
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
                // 夜晚阶段全部结束 → 结算夜晚 → 检查胜负 → 进入白天
                log.info("夜晚阶段结束，开始结算");
                executor.schedule(() -> {
                    try {
                        getGameService().resolveNight(gameId);
                        // ✨ FIX #1: 夜晚结算后检查胜负
                        getGameService().checkWinCondition(gameId);
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

        // ✨ 讨论阶段:按存活玩家数 × speakTime 动态计算兜底阶段时长,
        //    保证所有人都能轮到发言;若配置 duration 已足够则按较大值取。
        //    真正的"所有人发完自然结束"由 DiscussionManager.advanceDiscussionPhase 触发。
        if ("discussion".equals(phase.getPhase())) {
            int speakTimeMs = phase.getSpeakTime() != null ? phase.getSpeakTime() : 30000;
            int aliveCount = 0;
            try {
                aliveCount = getGameService().getAlivePlayers(gameId).size();
            } catch (Exception e) {
                log.warn("获取存活玩家数失败,使用配置时长 - 游戏: {}", gameId, e);
            }
            if (aliveCount > 0) {
                // 留 3s 缓冲,防止最后一位发言人刚好卡在阶段倒计时边界被切
                int dynamicMs = speakTimeMs * aliveCount + 3000;
                int chosen = Math.max(durationMs, dynamicMs);
                if (chosen != durationMs) {
                    log.info("讨论阶段动态扩容时长 - 游戏: {}, 配置: {}ms, 动态: {}ms (存活 {} 人 × {}ms + 3000ms 缓冲)",
                            gameId, durationMs, dynamicMs, aliveCount, speakTimeMs);
                }
                durationMs = chosen;
            }
        }

        log.info("调度阶段 - 游戏: {}, 阶段: {}, 时长: {}ms, 夜晚: {}",
                gameId, phase.getPhase(), durationMs, isNight);

        // ✨ 记录阶段上下文（用于"全员提交即推进"）
        PhaseContext ctx = new PhaseContext(gameMode, phase, phaseIndex, isNight, durationMs);
        activePhase.put(gameId, ctx);

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
            if (!ctx.advanced.compareAndSet(false, true)) return; // 已被提前推进
            try {
                executePhaseEnd(gameId, phase);

                // 特殊处理:投票阶段结束时强制结算(通过 VoteSession.tryMarkResolved 保证幂等)
                if ("voting".equals(phase.getPhase())) {
                    VoteManager.VoteSession session = getVoteManager().getVoteSession(gameId);
                    if (session != null && session.tryMarkResolved()) {
                        getGameService().resolveVoting(gameId);
                    }
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
     * ✨ 全员提交即推进 — 由 GameService 在每次行动提交后调用
     * 若当前阶段全部应行动玩家都已提交，则取消倒计时立即进入下一阶段。
     *
     * 行为: 不区分真人/AI, 只要该阶段所有应行动玩家都已提交即推进。
     */
    public void advanceIfAllActed(Long gameId) {
        PhaseContext ctx = activePhase.get(gameId);
        if (ctx == null) return;

        try {
            GameService gameService = getGameService();
            // 仅在"全员已提交"时继续
            if (!gameService.isPhaseAllSubmitted(gameId)) return;
        } catch (Exception e) {
            log.warn("检查阶段提交状态失败 - 游戏: {}", gameId, e);
            return;
        }

        // CAS 幂等：若倒计时已触发或已提前推进则放弃
        if (!ctx.advanced.compareAndSet(false, true)) return;

        log.info("✅ 全员已提交，提前推进阶段 - 游戏: {}, 阶段: {}", gameId, ctx.phase.getPhase());

        // 取消当前阶段的倒计时 future
        ScheduledFuture<?> future = gameTasks.remove(gameId);
        if (future != null) {
            future.cancel(false);
        }

        // 广播"阶段已提前结束"提示（可选）
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("phase", ctx.phase.getPhase());
            data.put("reason", "all_submitted");
            getGameService().broadcastToGame(gameId, "PHASE_ADVANCE", data);
        } catch (Exception ignored) {}

        // 异步执行结束逻辑，避免阻塞调用方事务
        executor.execute(() -> {
            try {
                executePhaseEnd(gameId, ctx.phase);

                // 特殊处理:投票阶段结束时强制结算(通过 VoteSession.tryMarkResolved 保证幂等)
                if ("voting".equals(ctx.phase.getPhase())) {
                    VoteManager.VoteSession session = getVoteManager().getVoteSession(gameId);
                    if (session != null && session.tryMarkResolved()) {
                        getGameService().resolveVoting(gameId);
                    }
                }

                // 推进下一阶段
                scheduleNextPhase(gameId, ctx.gameMode, ctx.phaseIndex + 1, ctx.isNight);
            } catch (Exception e) {
                log.error("提前推进阶段失败 - 游戏: {}, 阶段: {}", gameId, ctx.phase.getPhase(), e);
            }
        });
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

        // 记录房间日志
        try {
            Game game = getGameService().getGameStatus(gameId);
            GameLogger.room(game.getRoom().getRoomCode(), gameId,
                    "▶ 阶段开始: {} (第{}回合)", phase.getPhase(), game.getCurrentRound());
        } catch (Exception ignored) {}

        // 广播阶段消息（所有阶段都发 PHASE_CHANGE，确保前端收到 phase + duration + endsAt）
        {
            Map<String, Object> data = new HashMap<>();
            data.put("phase", gamePhase != null ? gamePhase.name() : phase.getPhase());
            // ✨ 绝对时间戳,避免客户端本地 setInterval 漂移;前端用 (endsAt - now)/1000 自愈
            PhaseContext ctxForTs = activePhase.get(gameId);
            // ✨ duration 使用本阶段实际生效时长(可能因动态扩容而大于 phase.duration)
            data.put("duration", ctxForTs != null ? ctxForTs.effectiveDurationMs : phase.getDuration());
            data.put("round", getGameService().getGameStatus(gameId).getCurrentRound());
            if (ctxForTs != null) {
                data.put("startedAt", ctxForTs.phaseStartedAt);
                data.put("endsAt", ctxForTs.phaseEndsAt);
                data.put("serverNow", System.currentTimeMillis());
            }
            if (phase.getBroadcast() != null) {
                data.put("message", phase.getBroadcast());
            }
            getGameService().broadcastToGame(gameId, "PHASE_CHANGE", data);
        }

        // ✨ 公共事件:推 PHASE_CHANGE 给所有 AI,同步其工作记忆的 round/phase
        try {
            int round = getGameService().getGameStatus(gameId).getCurrentRound();
            Map<String, Object> aiEventData = new HashMap<>();
            aiEventData.put("phase", gamePhase != null ? gamePhase.name() : phase.getPhase());
            aiEventData.put("round", round);
            getAIPlayerBridge().pushPublicEventToAllAIs(gameId, "PHASE_CHANGE", aiEventData);
        } catch (Exception e) {
            log.warn("推送 PHASE_CHANGE 给 AI 失败 - gameId={}", gameId, e);
        }
        
        // ✨ AI 集成点: 触发 AI 玩家决策
        if (gamePhase != null && needsPlayerAction(gamePhase)) {
            getAIPlayerBridge().scheduleAIActions(gameId, gamePhase);
        }

        // 特殊处理
        switch (phase.getPhase()) {
            case "voting":
                getGameService().startVoting(gameId);
                break;
            case "announce_death":
                // 死亡公告在 resolveNight 中已处理
                break;
            case "witch_action":
                // ✨ FIX #3: 女巫阶段发送被杀者信息
                getGameService().notifyWitchKillTarget(gameId);
                break;
            case "discussion":
                // ✨ 交给 DiscussionManager 按"每人独立时长"轮流发言
                int speakTimeMs = phase.getSpeakTime() != null ? phase.getSpeakTime() : 30000;
                getDiscussionManager().startDiscussion(gameId, speakTimeMs);
                break;
        }
    }

    /**
     * ✨ 讨论阶段"所有人发完" → 提前推进
     * 由 DiscussionManager 在最后一位发完后回调
     */
    public void advanceDiscussionPhase(Long gameId) {
        PhaseContext ctx = activePhase.get(gameId);
        if (ctx == null) return;
        if (!"discussion".equals(ctx.phase.getPhase())) return;
        if (!ctx.advanced.compareAndSet(false, true)) return;

        log.info("✅ 讨论已全员发言完毕, 提前推进 - 游戏: {}", gameId);

        ScheduledFuture<?> future = gameTasks.remove(gameId);
        if (future != null) future.cancel(false);

        try {
            Map<String, Object> data = new HashMap<>();
            data.put("phase", ctx.phase.getPhase());
            data.put("reason", "all_spoken");
            getGameService().broadcastToGame(gameId, "PHASE_ADVANCE", data);
        } catch (Exception ignored) {}

        executor.execute(() -> {
            try {
                executePhaseEnd(gameId, ctx.phase);
                scheduleNextPhase(gameId, ctx.gameMode, ctx.phaseIndex + 1, ctx.isNight);
            } catch (Exception e) {
                log.error("讨论阶段推进失败 - 游戏: {}", gameId, e);
            }
        });
    }
    
    /**
     * 判断阶段是否需要玩家行动
     */
    private boolean needsPlayerAction(Game.GamePhase phase) {
        return phase == Game.GamePhase.GUARD 
            || phase == Game.GamePhase.WEREWOLF 
            || phase == Game.GamePhase.SEER 
            || phase == Game.GamePhase.WITCH
            || phase == Game.GamePhase.DISCUSSION
            || phase == Game.GamePhase.VOTING;
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
     * 获取当前阶段上下文快照(供 snapshot API 使用)
     * 注意:返回的 PhaseContext 字段为 final,外部只读,无需防御性拷贝
     */
    public PhaseContext getActivePhase(Long gameId) {
        return activePhase.get(gameId);
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
        activePhase.remove(gameId);
        // ✨ 投票幂等标志随 VoteSession 在 GameService.endGame → voteManager.clearVote 一并清理
        try {
            getDiscussionManager().stopDiscussion(gameId);
        } catch (Exception ignored) {}
        log.info("停止游戏调度 - 游戏ID: {}", gameId);
    }

    /**
     * 重置投票幂等锁
     *
     * @deprecated 幂等标志已内聚到 VoteSession.resolved,
     *             由 GameService.startVoting 通过 voteManager.startVote(新 session) 隐式重置
     */
    @Deprecated
    public void resetVotingLock(Long gameId) {
        // no-op: 已由新建 VoteSession 的 AtomicBoolean(false) 隐式完成
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
