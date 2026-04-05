package com.werewolf.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.werewolf.entity.Game;
import com.werewolf.entity.Player;
import com.werewolf.repository.PlayerRepository;
import com.werewolf.service.GameService;
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

/**
 * AI 玩家桥接器
 * 
 * 核心职责:
 * - 在 PhaseScheduler 各阶段为 AI 玩家调用 Python AI Service
 * - 将 AI 决策结果转换为游戏引擎可执行的 Action
 * - 通过 GameService.executeAIAction() 执行
 * 
 * 集成点: PhaseScheduler.executePhaseStart() 中调用
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AIPlayerBridge {

    private final PlayerRepository playerRepository;
    private final GameService gameService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    @Value("${ai.service.timeout:30000}")
    private int aiServiceTimeout;

    /**
     * 为当前阶段的 AI 玩家调度决策
     * 
     * @param gameId 游戏 ID
     * @param phase 当前阶段
     */
    @Async
    public void scheduleAIActions(Long gameId, Game.GamePhase phase) {
        log.info("为游戏 {} 的阶段 {} 调度 AI 决策", gameId, phase);

        try {
            // 获取当前阶段应该行动的 AI 玩家
            List<Player> aiPlayers = getAIPlayersForPhase(gameId, phase);

            if (aiPlayers.isEmpty()) {
                log.debug("游戏 {} 阶段 {} 没有 AI 玩家需要行动", gameId, phase);
                return;
            }

            log.info("游戏 {} 阶段 {} 有 {} 个 AI 玩家需要行动", gameId, phase, aiPlayers.size());

            // 为每个 AI 玩家异步执行决策
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

        } catch (Exception e) {
            log.error("调度 AI 决策失败 - 游戏: {}, 阶段: {}", gameId, phase, e);
        }
    }

    /**
     * 执行 AI 决策
     */
    private void executeAIDecision(Long gameId, Player aiPlayer, Game.GamePhase phase) {
        try {
            log.info("AI 玩家 {} ({}号) 开始决策", aiPlayer.getId(), aiPlayer.getSeatNumber());

            // 构建游戏状态
            Map<String, Object> gameState = buildGameState(gameId);

            // 调用 AI Service
            Map<String, Object> decision;
            if (phase == Game.GamePhase.VOTING) {
                decision = callAIVoteDecision(gameId, aiPlayer.getId(), gameState);
            } else {
                decision = callAINightAction(gameId, aiPlayer.getId(), gameState);
            }

            // 提取决策结果
            String action = (String) decision.get("action");
            Object targetIdObj = decision.get("target_id");
            Long targetId = targetIdObj != null ? ((Number) targetIdObj).longValue() : null;

            // 执行决策
            gameService.executeAIAction(gameId, aiPlayer.getId(), action, targetId);

            log.info("AI 玩家 {} ({}号) 决策执行成功: action={}, target={}",
                aiPlayer.getId(), aiPlayer.getSeatNumber(), action, targetId);

        } catch (Exception e) {
            log.error("AI 玩家 {} 决策执行异常", aiPlayer.getId(), e);
        }
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
        state.put("round", game.getCurrentRound());
        state.put("phase", game.getCurrentPhase().name());
        state.put("alive_players", alivePlayers.stream().map(Player::getId).toList());
        state.put("dead_players", deadPlayers.stream().map(Player::getId).toList());

        return state;
    }

    /**
     * 获取当前阶段应该行动的 AI 玩家
     */
    private List<Player> getAIPlayersForPhase(Long gameId, Game.GamePhase phase) {
        List<Player> alivePlayers = playerRepository.findByGameIdAndStatus(gameId, Player.PlayerStatus.ALIVE);

        if (phase == Game.GamePhase.VOTING) {
            // 投票阶段：所有存活的 AI 玩家
            return alivePlayers.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsAi()))
                .filter(Player::getCanVote)
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
