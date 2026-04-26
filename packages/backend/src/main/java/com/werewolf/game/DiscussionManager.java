package com.werewolf.game;

import com.werewolf.ai.AIPlayerBridge;
import com.werewolf.entity.Game;
import com.werewolf.entity.Player;
import com.werewolf.repository.PlayerRepository;
import com.werewolf.service.GameService;
import com.werewolf.websocket.RoomWebSocketHandler;
import com.werewolf.websocket.WebSocketMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 讨论阶段管理器
 * 
 * 职责:
 * - 每人独立 30s 发言时长
 * - 真人点"结束发言"按钮或倒计时到, 切换下一位
 * - AI 轮到时调 Python LLM 生成发言, 输出后立即切换
 * - 最后一位发完 → 讨论结束, 让 PhaseScheduler 推进到投票
 * 
 * 设计:
 * - 每个 game 有一个 DiscussionContext (当前发言人索引、倒计时任务)
 * - 切换发言人时, 广播 SPEAKER_CHANGE 消息 (含 speakTimeLimit + remainSec)
 * - 讨论结束时触发 PhaseScheduler.advanceIfAllActed 或显式推进
 */
@Component
@Slf4j
public class DiscussionManager {

    private final ApplicationContext applicationContext;
    private final PlayerRepository playerRepository;
    private final RoomWebSocketHandler webSocketHandler;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    // 每个游戏当前讨论上下文
    private final Map<Long, DiscussionContext> contexts = new ConcurrentHashMap<>();

    // 延迟解析依赖, 避免循环
    private GameService gameService;
    private AIPlayerBridge aiPlayerBridge;
    private PhaseScheduler phaseScheduler;

    public DiscussionManager(
            ApplicationContext applicationContext,
            PlayerRepository playerRepository,
            RoomWebSocketHandler webSocketHandler
    ) {
        this.applicationContext = applicationContext;
        this.playerRepository = playerRepository;
        this.webSocketHandler = webSocketHandler;
    }

    private GameService getGameService() {
        if (gameService == null) gameService = applicationContext.getBean(GameService.class);
        return gameService;
    }

    private AIPlayerBridge getAIPlayerBridge() {
        if (aiPlayerBridge == null) aiPlayerBridge = applicationContext.getBean(AIPlayerBridge.class);
        return aiPlayerBridge;
    }

    private PhaseScheduler getPhaseScheduler() {
        if (phaseScheduler == null) phaseScheduler = applicationContext.getBean(PhaseScheduler.class);
        return phaseScheduler;
    }

    /**
     * 讨论上下文
     */
    private static class DiscussionContext {
        final Long gameId;
        final int speakTimeMs;
        // 按座位排序的存活玩家 id 列表 (讨论开始时固定)
        final List<Long> speakOrder;
        volatile int currentIndex = 0;
        volatile ScheduledFuture<?> timeoutFuture;
        final AtomicBoolean finished = new AtomicBoolean(false);

        DiscussionContext(Long gameId, int speakTimeMs, List<Long> speakOrder) {
            this.gameId = gameId;
            this.speakTimeMs = speakTimeMs;
            this.speakOrder = speakOrder;
        }
    }

    /**
     * 启动讨论阶段 (由 PhaseScheduler 在 DISCUSSION 阶段开始时调用)
     *
     * @param gameId       游戏 id
     * @param speakTimeMs  每人发言时长 (ms), 默认 30000
     */
    public void startDiscussion(Long gameId, int speakTimeMs) {
        // 清理旧上下文
        stopDiscussion(gameId);

        List<Player> alive = playerRepository.findByGameIdAndStatus(gameId, Player.PlayerStatus.ALIVE).stream()
                .sorted(Comparator.comparing(Player::getSeatNumber))
                .toList();
        if (alive.isEmpty()) {
            log.warn("讨论阶段无存活玩家 - 游戏: {}", gameId);
            return;
        }

        List<Long> order = alive.stream().map(Player::getId).toList();
        DiscussionContext ctx = new DiscussionContext(gameId, speakTimeMs, order);
        contexts.put(gameId, ctx);

        log.info("讨论阶段启动 - 游戏: {}, 发言人数: {}, 每人时长: {}ms", gameId, order.size(), speakTimeMs);

        // 从第一个发言
        moveToSpeaker(ctx, 0);
    }

    /**
     * 外部触发推进下一位 (真人点"结束发言"按钮 / 真人超时自动切 / AI 生成完)
     */
    public void advanceNext(Long gameId, Long currentSpeakerId, String reason) {
        DiscussionContext ctx = contexts.get(gameId);
        if (ctx == null || ctx.finished.get()) return;

        Long expected = ctx.speakOrder.get(ctx.currentIndex);
        if (!expected.equals(currentSpeakerId)) {
            log.debug("忽略非当前发言人的推进请求 - 游戏: {}, 发起者: {}, 当前: {}",
                    gameId, currentSpeakerId, expected);
            return;
        }

        log.info("推进讨论发言人 - 游戏: {}, 从 {} 切走 ({})", gameId, currentSpeakerId, reason);
        moveToSpeaker(ctx, ctx.currentIndex + 1);
    }

    /**
     * 真人在当前发言时段发送了一条聊天消息 — 推送 PLAYER_SPEECH 事件到所有 AI 记忆
     * 注意: 不切换发言人, 真人可在 30s 内多次发言
     */
    public void onHumanChat(Long gameId, Long speakerPlayerId, String content) {
        DiscussionContext ctx = contexts.get(gameId);
        if (ctx == null || ctx.finished.get()) return;

        Long current = ctx.speakOrder.get(ctx.currentIndex);
        if (!current.equals(speakerPlayerId)) {
            return;
        }
        // 推送发言事件到所有 AI (更新 Agent 记忆)
        getAIPlayerBridge().pushSpeechEventToAllAIs(gameId, speakerPlayerId, content);
    }

    /**
     * 停止讨论 (阶段外部推进、游戏结束时调用)
     */
    public void stopDiscussion(Long gameId) {
        DiscussionContext ctx = contexts.remove(gameId);
        if (ctx != null) {
            ctx.finished.set(true);
            if (ctx.timeoutFuture != null) {
                ctx.timeoutFuture.cancel(false);
            }
        }
    }

    // --- 内部实现 ---

    /**
     * 切到第 index 位发言人. 如果 index >= 人数 → 讨论结束, 推进到下一阶段.
     */
    private void moveToSpeaker(DiscussionContext ctx, int index) {
        // 取消当前倒计时
        if (ctx.timeoutFuture != null) {
            ctx.timeoutFuture.cancel(false);
            ctx.timeoutFuture = null;
        }

        if (index >= ctx.speakOrder.size()) {
            // 最后一人发完 → 讨论结束
            if (!ctx.finished.compareAndSet(false, true)) return;
            log.info("讨论结束（所有人已发言）- 游戏: {}", ctx.gameId);
            contexts.remove(ctx.gameId);

            // 通知 PhaseScheduler 提前推进
            try {
                getPhaseScheduler().advanceDiscussionPhase(ctx.gameId);
            } catch (Exception e) {
                log.error("讨论结束推进阶段异常 - 游戏: {}", ctx.gameId, e);
            }
            return;
        }

        ctx.currentIndex = index;
        Long speakerId = ctx.speakOrder.get(index);
        Player speaker = playerRepository.findById(speakerId).orElse(null);
        if (speaker == null || speaker.getStatus() != Player.PlayerStatus.ALIVE) {
            // 玩家已死亡, 跳过
            moveToSpeaker(ctx, index + 1);
            return;
        }

        // 标记 canSpeak
        setSingleSpeaker(ctx.gameId, speakerId);

        // 广播 SPEAKER_CHANGE
        broadcastSpeakerChange(ctx.gameId, speaker, ctx.speakTimeMs);

        log.info("讨论轮到 - 游戏: {}, 发言人: {}({}号, {}), 时长: {}ms",
                ctx.gameId, speakerId, speaker.getSeatNumber(),
                Boolean.TRUE.equals(speaker.getIsAi()) ? "AI" : "真人",
                ctx.speakTimeMs);

        if (Boolean.TRUE.equals(speaker.getIsAi())) {
            // AI 发言: 立刻调用 Python LLM, 输出完即推进
            handleAISpeaker(ctx, speaker);
        } else {
            // 真人发言: 设置倒计时, 30s 后自动推进
            ctx.timeoutFuture = executor.schedule(() -> {
                try {
                    log.info("真人发言超时, 自动推进 - 游戏: {}, 发言人: {}", ctx.gameId, speakerId);
                    advanceNext(ctx.gameId, speakerId, "timeout");
                } catch (Exception e) {
                    log.error("真人发言超时推进异常 - 游戏: {}", ctx.gameId, e);
                }
            }, ctx.speakTimeMs, TimeUnit.MILLISECONDS);
        }
    }

    private void handleAISpeaker(DiscussionContext ctx, Player aiSpeaker) {
        // 兜底倒计时: 即使 LLM 挂了, 30s 后也自动推进
        ctx.timeoutFuture = executor.schedule(() -> {
            log.warn("AI 发言超时兜底推进 - 游戏: {}, 发言人: {}", ctx.gameId, aiSpeaker.getId());
            advanceNext(ctx.gameId, aiSpeaker.getId(), "ai_timeout");
        }, ctx.speakTimeMs, TimeUnit.MILLISECONDS);

        // 异步调 Python LLM, 完成后推进
        getAIPlayerBridge().executeAISpeechAndAdvance(ctx.gameId, aiSpeaker, (succeeded) -> {
            // 回调: 无论成功失败都推进
            advanceNext(ctx.gameId, aiSpeaker.getId(), succeeded ? "ai_finished" : "ai_failed");
        });
    }

    private void setSingleSpeaker(Long gameId, Long speakerPlayerId) {
        List<Player> alive = playerRepository.findByGameIdAndStatus(gameId, Player.PlayerStatus.ALIVE);
        for (Player p : alive) {
            boolean canSpeak = p.getId().equals(speakerPlayerId);
            if (!java.util.Objects.equals(p.getCanSpeak(), canSpeak)) {
                p.setCanSpeak(canSpeak);
                playerRepository.save(p);
            }
        }
    }

    private void broadcastSpeakerChange(Long gameId, Player speaker, int speakTimeMs) {
        try {
            Game game = getGameService().getGameStatus(gameId);
            Map<String, Object> data = new HashMap<>();
            data.put("playerId", speaker.getId());
            data.put("seatNumber", speaker.getSeatNumber());
            data.put("username", speaker.getUser() != null ? speaker.getUser().getUsername() : speaker.getAiName());
            data.put("isAi", Boolean.TRUE.equals(speaker.getIsAi()));
            data.put("speakTimeMs", speakTimeMs);
            data.put("message", "轮到" + speaker.getSeatNumber() + "号玩家发言");
            webSocketHandler.broadcastToRoom(game.getRoom().getRoomCode(),
                    new WebSocketMessage("SPEAKER_CHANGE", data));
        } catch (Exception e) {
            log.warn("广播 SPEAKER_CHANGE 失败 - 游戏: {}", gameId, e);
        }
    }
}
