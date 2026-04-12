package com.werewolf.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.werewolf.entity.Game;
import com.werewolf.entity.Player;
import com.werewolf.repository.PlayerRepository;
import com.werewolf.service.GameService;
import com.werewolf.websocket.RoomWebSocketHandler;
import com.werewolf.websocket.WebSocketMessage;
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

        // 构建座位映射和玩家 ID 列表
        Map<String, Integer> seatMap = new HashMap<>();
        List<Integer> playerIds = new java.util.ArrayList<>();
        for (Player p : allPlayers) {
            seatMap.put(String.valueOf(p.getId()), p.getSeatNumber());
            playerIds.add(p.getId().intValue());
        }

        // 找出狼人队友
        List<Integer> werewolfIds = allPlayers.stream()
                .filter(p -> p.getRole() == Player.Role.WEREWOLF)
                .map(p -> p.getId().intValue())
                .toList();

        int successCount = 0;
        for (Player aiPlayer : aiPlayers) {
            try {
                Map<String, Object> request = new HashMap<>();
                request.put("game_id", String.valueOf(gameId));
                request.put("player_id", aiPlayer.getId().intValue());
                request.put("role", aiPlayer.getRole().name());
                request.put("persona", randomPersona());
                request.put("seat_number", aiPlayer.getSeatNumber());
                request.put("player_ids", playerIds);
                request.put("seat_map", seatMap);

                // 狼人需要知道队友
                if (aiPlayer.getRole() == Player.Role.WEREWOLF) {
                    List<Integer> teammates = werewolfIds.stream()
                            .filter(id -> !id.equals(aiPlayer.getId().intValue()))
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
                // 讨论阶段：AI 玩家依次发言，模拟自然发言间隔
                scheduleAISpeeches(gameId, aiPlayers);
            } else {
                // 夜间/投票阶段：并行执行
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
     * 讨论阶段：AI 玩家依次发言
     */
    private void scheduleAISpeeches(Long gameId, List<Player> aiPlayers) {
        CompletableFuture.runAsync(() -> {
            for (Player aiPlayer : aiPlayers) {
                try {
                    // 模拟思考延迟 2~5 秒
                    int delay = ThreadLocalRandom.current().nextInt(2000, 5000);
                    TimeUnit.MILLISECONDS.sleep(delay);

                    // 确认游戏仍在讨论阶段
                    Game game = gameService.getGameStatus(gameId);
                    if (game.getCurrentPhase() != Game.GamePhase.DISCUSSION) {
                        log.info("讨论阶段已结束，停止 AI 发言");
                        break;
                    }

                    executeAISpeech(gameId, aiPlayer);
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
     * 执行 AI 发言并广播到聊天
     */
    private void executeAISpeech(Long gameId, Player aiPlayer) {
        try {
            log.info("AI 玩家 {} ({}号) 开始生成发言", aiPlayer.getId(), aiPlayer.getSeatNumber());

            Map<String, Object> gameState = buildGameState(gameId);
            Map<String, Object> speechResult = callAISpeech(gameId, aiPlayer.getId(), gameState);

            String content = (String) speechResult.get("content");
            if (content == null || content.isBlank()) {
                log.warn("AI 玩家 {} 发言内容为空，跳过", aiPlayer.getId());
                return;
            }

            // 通过 WebSocket 广播 AI 发言（与真人聊天消息格式一致）
            Game game = gameService.getGameStatus(gameId);
            String roomCode = game.getRoom().getRoomCode();
            String aiName = aiPlayer.getAiName() != null ? aiPlayer.getAiName() : 
                            aiPlayer.getSeatNumber() + "号(AI)";

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

            log.info("AI 玩家 {} ({}号) 发言成功: {}...", 
                aiPlayer.getId(), aiPlayer.getSeatNumber(), 
                content.length() > 30 ? content.substring(0, 30) : content);

        } catch (Exception e) {
            log.error("AI 玩家 {} 发言执行异常", aiPlayer.getId(), e);
        }
    }

    /**
     * 执行 AI 夜间/投票决策
     */
    private void executeAIDecision(Long gameId, Player aiPlayer, Game.GamePhase phase) {
        try {
            log.info("AI 玩家 {} ({}号) 开始决策", aiPlayer.getId(), aiPlayer.getSeatNumber());

            Map<String, Object> gameState = buildGameState(gameId);

            Map<String, Object> decision;
            if (phase == Game.GamePhase.VOTING) {
                decision = callAIVoteDecision(gameId, aiPlayer.getId(), gameState);
            } else {
                decision = callAINightAction(gameId, aiPlayer.getId(), gameState);
            }

            String action = (String) decision.get("action");
            Object targetIdObj = decision.get("target_id");
            Long targetId = targetIdObj != null ? ((Number) targetIdObj).longValue() : null;

            gameService.executeAIAction(gameId, aiPlayer.getId(), action, targetId);

            log.info("AI 玩家 {} ({}号) 决策执行成功: action={}, target={}",
                aiPlayer.getId(), aiPlayer.getSeatNumber(), action, targetId);

        } catch (Exception e) {
            log.error("AI 玩家 {} 决策执行异常", aiPlayer.getId(), e);
        }
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object data = body.get("data");
                if (data instanceof Map) {
                    return (Map<String, Object>) data;
                }
            }
        } catch (Exception e) {
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
        
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<String, Object> body = response.getBody();
            return (Map<String, Object>) body.get("data");
        }

        throw new RuntimeException("AI Service 调用失败");
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
        
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<String, Object> body = response.getBody();
            return (Map<String, Object>) body.get("data");
        }

        throw new RuntimeException("AI Service 投票调用失败");
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
        state.put("alive_players", alivePlayers.stream().map(Player::getId).toList());
        state.put("dead_players", deadPlayers.stream().map(Player::getId).toList());

        // 构建 player_infos (ai-service GameState 必需字段)
        Map<String, Map<String, Object>> playerInfos = new HashMap<>();
        List<Player> allPlayers = new java.util.ArrayList<>(alivePlayers);
        allPlayers.addAll(deadPlayers);
        for (Player p : allPlayers) {
            Map<String, Object> info = new HashMap<>();
            info.put("player_id", p.getId());
            info.put("is_alive", p.getStatus() == Player.PlayerStatus.ALIVE);
            playerInfos.put(String.valueOf(p.getId()), info);
        }
        state.put("player_infos", playerInfos);

        return state;
    }

    /**
     * 获取当前阶段应该行动的 AI 玩家
     */
    private List<Player> getAIPlayersForPhase(Long gameId, Game.GamePhase phase) {
        List<Player> alivePlayers = playerRepository.findByGameIdAndStatus(gameId, Player.PlayerStatus.ALIVE);

        if (phase == Game.GamePhase.VOTING || phase == Game.GamePhase.DISCUSSION) {
            // 投票/讨论阶段：所有存活的 AI 玩家
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
