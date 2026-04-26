package com.werewolf.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.werewolf.entity.Game;
import com.werewolf.entity.Player;
import com.werewolf.game.NightActionStore;
import com.werewolf.repository.PlayerRepository;
import com.werewolf.service.GameService;
import com.werewolf.websocket.RoomWebSocketHandler;
import com.werewolf.websocket.WebSocketMessage;
import com.werewolf.logging.GameLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * AI 玩家桥接器
 * 
 * 核心职责:
 * - 在 PhaseScheduler 各阶段为 AI 玩家调用 Python AI Service
 * - 将 AI 决策结果转换为游戏引擎可执行的 Action
 * - 在讨论阶段调用 AI 发言接口，并通过 WebSocket 广播
 * - 通过 GameService.executeAIAction() 执行夜间/投票决策
 * 
 * 集成点: PhaseScheduler.executePhaseStart() 中调用
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AIPlayerBridge {

    private final PlayerRepository playerRepository;
    private final GameService gameService;
    private final RoomWebSocketHandler webSocketHandler;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    @Value("${ai.service.timeout:30000}")
    private int aiServiceTimeout;

    /**
     * 游戏开始时，为所有 AI 玩家在 AI Service 中创建 Agent 实例
     * 必须在任何决策调用之前执行
     */
    public void initializeAgents(Long gameId, List<Player> allPlayers) {
        log.info("为游戏 {} 初始化 AI Agent...", gameId);

        List<Player> aiPlayers = allPlayers.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsAi()))
                .toList();

        if (aiPlayers.isEmpty()) {
            log.info("游戏 {} 没有 AI 玩家，跳过 Agent 初始化", gameId);
            return;
        }

        // Agent 侧统一使用"座位号"作为玩家标识 (与 alive_players / LLM Prompt 一致)
        // seat_map: {数据库ID -> 座位号}, player_ids: 所有座位号列表
        Map<String, Integer> seatMap = new HashMap<>();
        List<Integer> playerIds = new java.util.ArrayList<>();
        for (Player p : allPlayers) {
            seatMap.put(String.valueOf(p.getId()), p.getSeatNumber());
            playerIds.add(p.getSeatNumber());
        }

        // 找出狼人队友 (座位号)
        List<Integer> werewolfSeats = allPlayers.stream()
                .filter(p -> p.getRole() == Player.Role.WEREWOLF)
                .map(Player::getSeatNumber)
                .toList();

        int successCount = 0;
        for (Player aiPlayer : aiPlayers) {
            try {
                Map<String, Object> request = new HashMap<>();
                request.put("game_id", String.valueOf(gameId));
                // player_id 用座位号, 与 buildGameState / alive_players 保持一致
                request.put("player_id", aiPlayer.getSeatNumber());
                request.put("role", aiPlayer.getRole().name());
                request.put("persona", randomPersona());
                request.put("seat_number", aiPlayer.getSeatNumber());
                request.put("player_ids", playerIds);
                request.put("seat_map", seatMap);

                // 狼人需要知道队友 (座位号)
                if (aiPlayer.getRole() == Player.Role.WEREWOLF) {
                    List<Integer> teammates = werewolfSeats.stream()
                            .filter(seat -> !seat.equals(aiPlayer.getSeatNumber()))
                            .toList();
                    request.put("teammates", teammates);
                }

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

                String url = aiServiceUrl + "/api/agents/create";
                ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    successCount++;
                    log.info("AI Agent 创建成功: {}号 ({}), 角色: {}", 
                        aiPlayer.getSeatNumber(), aiPlayer.getAiName(), aiPlayer.getRole());
                }
            } catch (Exception e) {
                log.error("AI Agent 创建失败: {}号 ({}): {}", 
                    aiPlayer.getSeatNumber(), aiPlayer.getAiName(), e.getMessage());
            }
        }

        log.info("游戏 {} AI Agent 初始化完成: {}/{} 成功", gameId, successCount, aiPlayers.size());
    }

    /**
     * 随机分配人格
     */
    private String randomPersona() {
        String[] personas = {"aggressive", "analytical", "cautious", "charming"};
        return personas[ThreadLocalRandom.current().nextInt(personas.length)];
    }

    /**
     * 为当前阶段的 AI 玩家调度决策
     */
    @Async
    public void scheduleAIActions(Long gameId, Game.GamePhase phase) {
        log.info("为游戏 {} 的阶段 {} 调度 AI 决策", gameId, phase);

        try {
            List<Player> aiPlayers = getAIPlayersForPhase(gameId, phase);

            if (aiPlayers.isEmpty()) {
                log.debug("游戏 {} 阶段 {} 没有 AI 玩家需要行动", gameId, phase);
                return;
            }

            log.info("游戏 {} 阶段 {} 有 {} 个 AI 玩家需要行动", gameId, phase, aiPlayers.size());

            if (phase == Game.GamePhase.DISCUSSION) {
                // 讨论阶段: 由 DiscussionManager 按序驱动, 这里不需要做任何事
                // (轮到 AI 时 DiscussionManager 会主动调 executeAISpeechAndAdvance)
                log.debug("游戏 {} 讨论阶段由 DiscussionManager 驱动, 跳过 AIPlayerBridge 批量调度", gameId);
            } else if (phase == Game.GamePhase.WEREWOLF) {
                // 狼人阶段：AI 狼人等待真人狼行动后跟随
                scheduleAIWerewolfActions(gameId, aiPlayers);
            } else {
                // 其他夜间/投票阶段：并行执行
                for (Player aiPlayer : aiPlayers) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            executeAIDecision(gameId, aiPlayer, phase);
                        } catch (Exception e) {
                            log.error("AI 玩家 {} ({}号) 决策执行失败", 
                                aiPlayer.getId(), aiPlayer.getSeatNumber(), e);
                        }
                    });
                }
            }

        } catch (Exception e) {
            log.error("调度 AI 决策失败 - 游戏: {}, 阶段: {}", gameId, phase, e);
        }
    }

    /**
     * 狼人阶段：AI 狼人行动
     * 
     * 策略:
     * - 有真人狼 → 等待真人选目标后 AI 跟随（真人狼担任队长）
     * - 全是 AI 狼 → 推选第一个 AI 狼调用 Python LLM 决策,
     *                其余狼跟随同一目标（保证狼队一致）
     *                LLM 失败时降级为随机选非狼目标
     */
    private void scheduleAIWerewolfActions(Long gameId, List<Player> aiWolves) {
        List<Player> allAlive = playerRepository.findByGameIdAndStatus(gameId, Player.PlayerStatus.ALIVE);
        
        // 非狼存活玩家（可选目标）
        List<Player> targets = allAlive.stream()
                .filter(p -> p.getRole() != Player.Role.WEREWOLF)
                .toList();
        
        if (targets.isEmpty()) return;

        boolean hasHumanWolf = allAlive.stream()
                .filter(p -> p.getRole() == Player.Role.WEREWOLF)
                .anyMatch(p -> !Boolean.TRUE.equals(p.getIsAi()));

        if (!hasHumanWolf) {
            // 全是 AI 狼 → 推选"队长"调用 Python LLM, 其余狼跟随
            scheduleAllAIWolvesWithLLM(gameId, aiWolves, targets);
            return;
        }

        // 有真人狼 → 等待真人行动后跟随
        CompletableFuture.runAsync(() -> {
            try {
                NightActionStore.RoundActions actions = gameService.getNightActions(gameId);
                
                int waited = 0;
                while (waited < 25000) {
                    // 检查真人狼是否已提交
                    for (Player wolf : allAlive) {
                        if (wolf.getRole() == Player.Role.WEREWOLF && !Boolean.TRUE.equals(wolf.getIsAi())) {
                            Long humanTarget = actions.getWerewolfVotes().get(wolf.getId());
                            if (humanTarget != null) {
                                // 跟随真人目标
                                for (Player aiWolf : aiWolves) {
                                    gameService.executeAIAction(gameId, aiWolf.getId(), "kill", humanTarget);
                                    log.info("AI 狼人 {} ({}号) 跟随真人目标: {}", 
                                        aiWolf.getId(), aiWolf.getSeatNumber(), humanTarget);
                                }
                                return;
                            }
                        }
                    }
                    TimeUnit.MILLISECONDS.sleep(500);
                    waited += 500;
                }

                // 超时 → 降级: 让 AI 狼队长走 LLM 决策
                log.warn("游戏 {} 真人狼超时未行动, AI 狼降级 LLM 决策", gameId);
                scheduleAllAIWolvesWithLLM(gameId, aiWolves, targets);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("AI 狼人跟随逻辑异常 - 游戏: {}", gameId, e);
            }
        });
    }

    /**
     * 全 AI 狼场景:
     * - 按座位号排序, 推选首位 AI 狼调用 Python LLM 决策
     * - 其余 AI 狼跟随同一目标
     * - LLM 失败/非法目标 → 随机选非狼目标
     */
    private void scheduleAllAIWolvesWithLLM(Long gameId, List<Player> aiWolves, List<Player> targets) {
        CompletableFuture.runAsync(() -> {
            try {
                // 按座位号排序, 推选"队长"
                List<Player> sorted = aiWolves.stream()
                        .sorted(java.util.Comparator.comparingInt(p ->
                                p.getSeatNumber() == null ? Integer.MAX_VALUE : p.getSeatNumber()))
                        .toList();
                Player captain = sorted.get(0);

                log.info("全 AI 狼队长: {} ({}号), 调用 Python LLM 决策", 
                        captain.getId(), captain.getSeatNumber());

                // 队长调用 Python LLM (player_id 用座位号, 与 Agent 注册一致)
                Map<String, Object> gameState = buildGameState(gameId);
                Map<String, Object> decision = callAINightAction(gameId, captain.getSeatNumber().longValue(), gameState);

                Long targetPlayerId = resolveKillTarget(decision, targets);

                if (targetPlayerId == null) {
                    // 降级: 随机
                    Player random = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
                    targetPlayerId = random.getId();
                    log.warn("狼队长 LLM 决策未返回合法目标, 降级随机: {}号", random.getSeatNumber());
                }

                // 所有 AI 狼提交同一目标
                for (Player aiWolf : aiWolves) {
                    try {
                        gameService.executeAIAction(gameId, aiWolf.getId(), "kill", targetPlayerId);
                        log.info("AI 狼人 {} ({}号) 击杀(队长LLM决策): {}", 
                                aiWolf.getId(), aiWolf.getSeatNumber(), targetPlayerId);
                    } catch (Exception e) {
                        log.error("AI 狼人 {} 击杀提交异常", aiWolf.getId(), e);
                    }
                }
            } catch (Exception e) {
                log.error("全 AI 狼 LLM 决策异常 - 游戏: {}, 降级随机", gameId, e);
                // 最兜底: 全狼随机投同一目标
                Player random = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
                for (Player aiWolf : aiWolves) {
                    try {
                        gameService.executeAIAction(gameId, aiWolf.getId(), "kill", random.getId());
                        log.info("AI 狼人 {} ({}号) 兜底随机击杀: {}号", 
                                aiWolf.getId(), aiWolf.getSeatNumber(), random.getSeatNumber());
                    } catch (Exception ex) {
                        log.error("AI 狼人 {} 兜底击杀异常", aiWolf.getId(), ex);
                    }
                }
            }
        });
    }

    /**
     * 解析 Python LLM 返回的 target_id (座位号) → Player.id (数据库主键)
     * 
     * Python Agent 侧基于座位号决策, 这里需要把座位号换算回 Player.id。
     * 目标必须在 targets (非狼存活玩家) 列表中。
     */
    private Long resolveKillTarget(Map<String, Object> decision, List<Player> targets) {
        if (decision == null) return null;
        Object targetObj = decision.get("target_id");
        if (targetObj == null) return null;

        int targetSeat;
        try {
            targetSeat = ((Number) targetObj).intValue();
        } catch (ClassCastException e) {
            return null;
        }

        if (targetSeat <= 0) return null;

        return targets.stream()
                .filter(p -> p.getSeatNumber() != null && p.getSeatNumber() == targetSeat)
                .map(Player::getId)
                .findFirst()
                .orElse(null);
    }

    /**
     * 讨论阶段：AI 玩家依次发言
     */
    private void scheduleAISpeeches(Long gameId, List<Player> aiPlayers) {
        CompletableFuture.runAsync(() -> {
            for (Player aiPlayer : aiPlayers) {
                try {
                    // 确认游戏仍在讨论阶段
                    Game game = gameService.getGameStatus(gameId);
                    if (game.getCurrentPhase() != Game.GamePhase.DISCUSSION) {
                        log.info("讨论阶段已结束，停止 AI 发言");
                        break;
                    }

                    executeAISpeech(gameId, aiPlayer);

                    // AI 发言后短暂间隔 1~2 秒（模拟自然节奏）
                    int delay = ThreadLocalRandom.current().nextInt(1000, 2000);
                    TimeUnit.MILLISECONDS.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("AI 玩家 {} ({}号) 发言失败", 
                        aiPlayer.getId(), aiPlayer.getSeatNumber(), e);
                }
            }
        });
    }

    /**
     * AI 发言 + 异步推进讨论
     * 
     * 由 DiscussionManager 在"轮到 AI 发言"时调用:
     * 1. 调 Python LLM 生成发言内容
     * 2. 广播为 PLAYER_CHAT
     * 3. 推送 PLAYER_SPEECH 事件给所有 AI (更新记忆)
     * 4. 回调 onFinished(true/false) 通知 DiscussionManager 推进
     *
     * @param gameId     游戏 id
     * @param aiPlayer   AI 玩家
     * @param onFinished 完成回调 (true=成功, false=失败)
     */
    public void executeAISpeechAndAdvance(Long gameId, Player aiPlayer, java.util.function.Consumer<Boolean> onFinished) {
        CompletableFuture.runAsync(() -> {
            boolean ok = false;
            try {
                ok = doExecuteAISpeech(gameId, aiPlayer);
            } catch (Exception e) {
                log.error("AI 玩家 {} 发言执行异常", aiPlayer.getId(), e);
            } finally {
                try {
                    onFinished.accept(ok);
                } catch (Exception ignored) {}
            }
        });
    }

    /**
     * 推送真人发言的 PLAYER_SPEECH 事件给所有 AI Agent, 用于更新 AI 记忆
     *
     * @param gameId          游戏 id
     * @param speakerPlayerId 发言玩家 Player.id
     * @param content         发言内容
     */
    public void pushSpeechEventToAllAIs(Long gameId, Long speakerPlayerId, String content) {
        if (content == null || content.isBlank()) return;

        try {
            Player speaker = playerRepository.findById(speakerPlayerId).orElse(null);
            if (speaker == null || speaker.getSeatNumber() == null) {
                log.warn("pushSpeechEventToAllAIs: 玩家不存在 - id={}", speakerPlayerId);
                return;
            }
            Integer speakerSeat = speaker.getSeatNumber();

            // 找出所有 AI 玩家(存活或死亡都推, 保持记忆完整)
            List<Player> aiPlayers = playerRepository.findByGameId(gameId).stream()
                    .filter(p -> Boolean.TRUE.equals(p.getIsAi()))
                    .toList();
            if (aiPlayers.isEmpty()) return;

            Game game = gameService.getGameStatus(gameId);
            int round = game.getCurrentRound();
            String phase = game.getCurrentPhase().name();

            log.info("[SPEECH-EVENT] 推送 PLAYER_SPEECH: speaker座位={}, AI数量={}, content={}",
                    speakerSeat, aiPlayers.size(),
                    content.length() > 40 ? content.substring(0, 40) + "..." : content);

            // 并发推送
            for (Player ai : aiPlayers) {
                CompletableFuture.runAsync(() -> {
                    try {
                        pushSpeechEvent(gameId, ai.getSeatNumber(), speakerSeat, content, round, phase);
                    } catch (Exception e) {
                        log.warn("推送 PLAYER_SPEECH 给 AI {} 失败: {}", ai.getId(), e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            log.error("pushSpeechEventToAllAIs 异常 - gameId={}", gameId, e);
        }
    }

    /**
     * 向单个 AI 推送 PLAYER_SPEECH 事件
     */
    private void pushSpeechEvent(Long gameId, Integer targetAiSeat, Integer speakerSeat,
                                 String content, int round, String phase) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("player_id", speakerSeat);    // 发言者座位号
        eventData.put("speaker_id", speakerSeat);
        eventData.put("content", content);
        pushEventInternal(gameId, targetAiSeat, "PLAYER_SPEECH", eventData, round, phase);
    }

    // ============================================================
    // ✨ 通用事件推送 API — 让所有 AI 拥有真正的"私有 + 公共"记忆通道
    // ============================================================

    /**
     * 推送私有事件给特定 AI (按座位号定位)
     *
     * 适用场景:
     *   - 预言家查验结果 (SEER_CHECK_RESULT) → 仅该预言家
     *   - 女巫救/毒信息 (WITCH_SAVE_USED / WITCH_POISON_USED) → 仅该女巫
     *   - 守卫守人 (GUARD_PROTECT_USED) → 仅该守卫
     *   - 狼人队友互见的击杀投票 (WEREWOLF_KILL_VOTE) → 仅狼队
     *
     * 异常吞掉,Python 服务不可用不影响主游戏流程
     */
    public void pushPrivateEventToAI(Long gameId, Player targetAi,
                                     String eventType, Map<String, Object> eventData) {
        if (targetAi == null || targetAi.getSeatNumber() == null) return;
        if (!Boolean.TRUE.equals(targetAi.getIsAi())) return;  // 真人玩家走 WebSocket,不走这里

        try {
            Game game = gameService.getGameStatus(gameId);
            int round = game.getCurrentRound();
            String phase = game.getCurrentPhase().name();

            log.info("[PRIVATE-EVENT] 推送 {} → AI 座位{} (gameId={}): {}",
                    eventType, targetAi.getSeatNumber(), gameId, eventData);

            CompletableFuture.runAsync(() -> {
                try {
                    pushEventInternal(gameId, targetAi.getSeatNumber(), eventType, eventData, round, phase);
                } catch (Exception e) {
                    log.warn("推送私有事件 {} 给 AI {} 失败: {}", eventType, targetAi.getId(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("pushPrivateEventToAI 异常 - gameId={}, eventType={}", gameId, eventType, e);
        }
    }

    /**
     * 推送公共事件给该局所有 AI (含死亡的,保持记忆完整)
     *
     * 适用场景:
     *   - 死亡公告 (PLAYER_DIED)
     *   - 投票结果 (VOTE_RESULT)
     *   - 阶段变化 (PHASE_CHANGE)
     */
    public void pushPublicEventToAllAIs(Long gameId, String eventType, Map<String, Object> eventData) {
        try {
            List<Player> aiPlayers = playerRepository.findByGameId(gameId).stream()
                    .filter(p -> Boolean.TRUE.equals(p.getIsAi()))
                    .toList();
            if (aiPlayers.isEmpty()) return;

            Game game = gameService.getGameStatus(gameId);
            int round = game.getCurrentRound();
            String phase = game.getCurrentPhase().name();

            log.info("[PUBLIC-EVENT] 推送 {} 给 {} 个 AI (gameId={}): {}",
                    eventType, aiPlayers.size(), gameId, eventData);

            for (Player ai : aiPlayers) {
                CompletableFuture.runAsync(() -> {
                    try {
                        pushEventInternal(gameId, ai.getSeatNumber(), eventType, eventData, round, phase);
                    } catch (Exception e) {
                        log.warn("推送公共事件 {} 给 AI {} 失败: {}", eventType, ai.getId(), e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            log.error("pushPublicEventToAllAIs 异常 - gameId={}, eventType={}", gameId, eventType, e);
        }
    }

    /**
     * 推送公共事件给指定的多个 AI (例如:仅推给所有狼人 AI)
     */
    public void pushEventToAIs(Long gameId, List<Player> targetAis,
                               String eventType, Map<String, Object> eventData) {
        if (targetAis == null || targetAis.isEmpty()) return;
        try {
            Game game = gameService.getGameStatus(gameId);
            int round = game.getCurrentRound();
            String phase = game.getCurrentPhase().name();

            log.info("[GROUP-EVENT] 推送 {} 给 {} 个 AI (gameId={}): {}",
                    eventType, targetAis.size(), gameId, eventData);

            for (Player ai : targetAis) {
                if (!Boolean.TRUE.equals(ai.getIsAi()) || ai.getSeatNumber() == null) continue;
                CompletableFuture.runAsync(() -> {
                    try {
                        pushEventInternal(gameId, ai.getSeatNumber(), eventType, eventData, round, phase);
                    } catch (Exception e) {
                        log.warn("推送组事件 {} 给 AI {} 失败: {}", eventType, ai.getId(), e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            log.error("pushEventToAIs 异常 - gameId={}, eventType={}", gameId, eventType, e);
        }
    }

    /**
     * 底层共用:向 Python AI Service 推送一条事件
     */
    private void pushEventInternal(Long gameId, Integer targetAiSeat, String eventType,
                                   Map<String, Object> eventData, int round, String phase) {
        String url = aiServiceUrl + "/api/agents/event";

        Map<String, Object> request = new HashMap<>();
        request.put("game_id", String.valueOf(gameId));
        request.put("player_id", targetAiSeat);     // 接收事件的 AI 座位号
        request.put("event_type", eventType);
        request.put("event_data", eventData);
        request.put("round", round);
        request.put("phase", phase);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        try {
            restTemplate.postForEntity(url, entity, Map.class);
        } catch (Exception e) {
            // Agent 可能不存在(真人玩家 / 未注册座位),静默
            log.debug("推送事件 {} 给 AI 座位{} 失败: {}", eventType, targetAiSeat, e.getMessage());
        }
    }

    /**
     * 内部: 执行 AI 发言 (调 Python + 广播 + 推 event), 返回是否成功
     */
    private boolean doExecuteAISpeech(Long gameId, Player aiPlayer) {
        log.info("AI 玩家 {} ({}号) 开始生成发言", aiPlayer.getId(), aiPlayer.getSeatNumber());

        Map<String, Object> gameState = buildGameState(gameId);
        Long aiSeatAsPlayerId = aiPlayer.getSeatNumber().longValue();
        Map<String, Object> speechResult = callAISpeech(gameId, aiSeatAsPlayerId, gameState);

        String content = (String) speechResult.get("content");
        if (content == null || content.isBlank()) {
            log.warn("AI 玩家 {} 发言内容为空", aiPlayer.getId());
            return false;
        }

        // 广播 AI 发言 (与真人聊天消息格式一致)
        Game game = gameService.getGameStatus(gameId);
        String roomCode = game.getRoom().getRoomCode();
        String aiName = aiPlayer.getSeatNumber() + "号 " +
                (aiPlayer.getAiName() != null ? aiPlayer.getAiName() : "AI");

        Map<String, Object> chatData = new HashMap<>();
        chatData.put("content", content);

        WebSocketMessage chatMsg = new WebSocketMessage(
                WebSocketMessage.Type.PLAYER_CHAT,
                aiPlayer.getId(),
                aiName,
                chatData,
                roomCode
        );
        webSocketHandler.broadcastToRoom(roomCode, chatMsg);

        // 推送 PLAYER_SPEECH 给所有 AI (含自己, 用于记忆一致性)
        pushSpeechEventToAllAIs(gameId, aiPlayer.getId(), content);

        log.info("AI 玩家 {} ({}号) 发言成功: {}...",
                aiPlayer.getId(), aiPlayer.getSeatNumber(),
                content.length() > 30 ? content.substring(0, 30) : content);
        return true;
    }

    /**
     * @deprecated 讨论阶段已由 DiscussionManager 驱动, 此方法不再使用
     */
    @Deprecated
    @SuppressWarnings("unused")
    private void executeAISpeech(Long gameId, Player aiPlayer) {
        doExecuteAISpeech(gameId, aiPlayer);
    }

    /**
     * 执行 AI 夜间/投票决策
     * 
     * 注意: Python AI Service 返回的 target_id 是"座位号", 
     * 这里需要换算成 Player.id 后再交给 GameService.executeAIAction。
     */
    private void executeAIDecision(Long gameId, Player aiPlayer, Game.GamePhase phase) {
        try {
            log.info("AI 玩家 {} ({}号) 开始决策", aiPlayer.getId(), aiPlayer.getSeatNumber());

            Map<String, Object> gameState = buildGameState(gameId);
            // Python Agent 侧以"座位号"作为 player_id
            Long aiSeatAsPlayerId = aiPlayer.getSeatNumber().longValue();

            Map<String, Object> decision;
            String action;
            if (phase == Game.GamePhase.VOTING) {
                decision = callAIVoteDecision(gameId, aiSeatAsPlayerId, gameState);
                action = "vote";  // 投票接口不返回 action 字段
            } else {
                decision = callAINightAction(gameId, aiSeatAsPlayerId, gameState);
                action = (String) decision.get("action");
                if (action == null) action = "skip";
            }

            // Python 返回的是座位号, 需换算为 Player.id
            Long targetId = resolveTargetIdFromSeat(gameId, decision.get("target_id"));

            gameService.executeAIAction(gameId, aiPlayer.getId(), action, targetId);

            log.info("AI 玩家 {} ({}号) 决策执行成功: action={}, targetPlayerId={}",
                aiPlayer.getId(), aiPlayer.getSeatNumber(), action, targetId);

        } catch (Exception e) {
            log.error("AI 玩家 {} 决策执行异常", aiPlayer.getId(), e);
        }
    }

    /**
     * 将 Python 返回的座位号换算为数据库 Player.id
     * 
     * - 座位号 = 0 或 null → 返回 null (表示弃票/不行动)
     * - 找不到对应存活玩家 → 返回 null
     * - 否则返回对应 Player.id
     */
    private Long resolveTargetIdFromSeat(Long gameId, Object targetIdObj) {
        if (targetIdObj == null) return null;
        int seat;
        try {
            seat = ((Number) targetIdObj).intValue();
        } catch (ClassCastException e) {
            log.warn("AI 返回的 target_id 格式非法: {}", targetIdObj);
            return null;
        }
        if (seat <= 0) return null;

        // 从所有玩家(含死亡)中找, 上层会再做存活校验
        List<Player> allPlayers = playerRepository.findByGameId(gameId);
        return allPlayers.stream()
                .filter(p -> p.getSeatNumber() != null && p.getSeatNumber() == seat)
                .map(Player::getId)
                .findFirst()
                .orElse(null);
    }

    /**
     * 调用 AI Service 发言接口
     */
    private Map<String, Object> callAISpeech(Long gameId, Long playerId, Map<String, Object> gameState) {
        String url = aiServiceUrl + "/api/agents/action/speak";
        
        Map<String, Object> request = new HashMap<>();
        request.put("game_id", String.valueOf(gameId));
        request.put("player_id", playerId.intValue());
        request.put("game_state", gameState);
        request.put("speak_context", "discussion");

        GameLogger.aiRequest(gameId, playerId, "/api/agents/action/speak", request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                GameLogger.aiResponse(gameId, playerId, "/api/agents/action/speak", body);
                Object data = body.get("data");
                if (data instanceof Map) {
                    return (Map<String, Object>) data;
                }
            }
        } catch (Exception e) {
            GameLogger.aiFallback(gameId, playerId, "/api/agents/action/speak", e.getMessage());
            log.warn("AI 发言服务调用失败，使用默认发言: {}", e.getMessage());
        }

        // 降级：返回默认发言
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("content", "我还在观察局势，暂时没有明确的怀疑对象。");
        fallback.put("emotion", "neutral");
        return fallback;
    }

    /**
     * 调用 AI Service 夜间行动接口
     */
    private Map<String, Object> callAINightAction(Long gameId, Long playerId, Map<String, Object> gameState) {
        String url = aiServiceUrl + "/api/agents/action/night";
        
        Map<String, Object> request = new HashMap<>();
        request.put("game_id", String.valueOf(gameId));
        request.put("player_id", playerId.intValue());
        request.put("game_state", gameState);

        GameLogger.aiRequest(gameId, playerId, "/api/agents/action/night", request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                GameLogger.aiResponse(gameId, playerId, "/api/agents/action/night", body);
                return (Map<String, Object>) body.get("data");
            }
        } catch (Exception e) {
            GameLogger.aiFallback(gameId, playerId, "/api/agents/action/night", e.getMessage());
            log.warn("AI 夜间行动服务调用失败，降级为 skip: {}", e.getMessage());
        }

        // 降级：跳过行动
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("action", "skip");
        return fallback;
    }

    /**
     * 调用 AI Service 投票接口
     */
    private Map<String, Object> callAIVoteDecision(Long gameId, Long playerId, Map<String, Object> gameState) {
        String url = aiServiceUrl + "/api/agents/action/vote";
        
        Map<String, Object> request = new HashMap<>();
        request.put("game_id", String.valueOf(gameId));
        request.put("player_id", playerId.intValue());
        request.put("game_state", gameState);

        GameLogger.aiRequest(gameId, playerId, "/api/agents/action/vote", request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                GameLogger.aiResponse(gameId, playerId, "/api/agents/action/vote", body);
                return (Map<String, Object>) body.get("data");
            }
        } catch (Exception e) {
            GameLogger.aiFallback(gameId, playerId, "/api/agents/action/vote", e.getMessage());
            log.warn("AI 投票服务调用失败，使用随机投票: {}", e.getMessage());
        }

        // ✨ FIX #21: 降级 — 随机投一个存活玩家 (返回座位号)
        // 注意: 此处 playerId 已是座位号, 按座位号过滤排除自己
        int selfSeat = playerId.intValue();
        List<Player> targets = gameService.getAlivePlayers(gameId).stream()
                .filter(p -> p.getSeatNumber() != null && p.getSeatNumber() != selfSeat)
                .toList();
        Map<String, Object> fallback = new HashMap<>();
        if (!targets.isEmpty()) {
            Player target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
            fallback.put("target_id", target.getSeatNumber());
        }
        fallback.put("reason", "随机投票（AI 服务不可用）");
        return fallback;
    }

    /**
     * 构建游戏状态
     */
    private Map<String, Object> buildGameState(Long gameId) {
        Game game = gameService.getGameStatus(gameId);
        List<Player> alivePlayers = gameService.getAlivePlayers(gameId);
        List<Player> deadPlayers = playerRepository.findByGameIdAndStatus(gameId, Player.PlayerStatus.DEAD);

        Map<String, Object> state = new HashMap<>();
        state.put("game_id", String.valueOf(gameId));
        state.put("round", game.getCurrentRound());
        state.put("phase", game.getCurrentPhase().name());
        // 使用座位号而非数据库 ID，让 AI 发言时说"1号、2号"而不是"172号"
        state.put("alive_players", alivePlayers.stream().map(Player::getSeatNumber).toList());
        state.put("dead_players", deadPlayers.stream().map(Player::getSeatNumber).toList());

        // 构建 player_infos — key 使用座位号
        Map<String, Map<String, Object>> playerInfos = new HashMap<>();
        List<Player> allPlayers = new java.util.ArrayList<>(alivePlayers);
        allPlayers.addAll(deadPlayers);
        for (Player p : allPlayers) {
            Map<String, Object> info = new HashMap<>();
            info.put("player_id", p.getSeatNumber());
            info.put("is_alive", p.getStatus() == Player.PlayerStatus.ALIVE);
            playerInfos.put(String.valueOf(p.getSeatNumber()), info);
        }
        state.put("player_infos", playerInfos);

        return state;
    }

    /**
     * 获取当前阶段应该行动的 AI 玩家
     */
    private List<Player> getAIPlayersForPhase(Long gameId, Game.GamePhase phase) {
        List<Player> alivePlayers = playerRepository.findByGameIdAndStatus(gameId, Player.PlayerStatus.ALIVE);

        if (phase == Game.GamePhase.VOTING) {
            // 投票阶段：可投票的 AI 玩家
            return alivePlayers.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsAi()))
                .filter(p -> Boolean.TRUE.equals(p.getCanVote()))
                .toList();
        } else if (phase == Game.GamePhase.DISCUSSION) {
            // 讨论阶段：仅当前可发言的 AI 玩家
            return alivePlayers.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsAi()))
                .filter(p -> Boolean.TRUE.equals(p.getCanSpeak()))
                .toList();
        } else {
            // 夜晚阶段：特定角色的 AI 玩家
            Player.Role targetRole = getRoleForPhase(phase);
            if (targetRole == null) return List.of();

            return alivePlayers.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsAi()))
                .filter(p -> p.getRole() == targetRole)
                .toList();
        }
    }

    /**
     * 根据阶段获取应该行动的角色
     */
    private Player.Role getRoleForPhase(Game.GamePhase phase) {
        return switch (phase) {
            case GUARD -> Player.Role.GUARD;
            case WEREWOLF -> Player.Role.WEREWOLF;
            case SEER -> Player.Role.SEER;
            case WITCH -> Player.Role.WITCH;
            default -> null;
        };
    }
}
