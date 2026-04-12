package com.werewolf.controller;

import com.werewolf.dto.*;
import com.werewolf.entity.Game;
import com.werewolf.entity.GameLog;
import com.werewolf.entity.Player;
import com.werewolf.service.GameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GameController {

    private final GameService gameService;

    /**
     * 开始游戏
     * POST /api/games/room/{roomId}/start
     */
    @PostMapping("/room/{roomId}/start")
    public ResponseEntity<ApiResponse<?>> startGame(
            @PathVariable Long roomId,
            @Valid @RequestBody StartGameRequest request,
            @RequestAttribute("userId") Long userId) {
        try {
            Game game = gameService.startGame(roomId, request.getGameModeId());

            Map<String, Object> data = new HashMap<>();
            data.put("gameId", game.getId());
            data.put("status", game.getStatus().name());
            data.put("round", game.getCurrentRound());
            data.put("phase", game.getCurrentPhase().name());

            return ResponseEntity.ok(ApiResponse.success("游戏开始", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest(e.getMessage()));
        }
    }

    /**
     * 统一行动入口 — 所有游戏行动通过此 API 提交
     * POST /api/games/{gameId}/action
     *
     * 支持的 action 类型:
     *   kill    — 狼人击杀 (夜晚)
     *   check   — 预言家查验 (夜晚)
     *   save    — 女巫救人 (夜晚)
     *   poison  — 女巫毒人 (夜晚)
     *   guard   — 守卫守护 (夜晚)
     *   vote    — 白天投票
     *   shoot   — 猎人开枪
     *   skip    — 跳过行动
     */
    @PostMapping("/{gameId}/action")
    public ResponseEntity<ApiResponse<?>> submitAction(
            @PathVariable Long gameId,
            @Valid @RequestBody GameActionRequest request,
            @RequestAttribute("userId") Long userId) {
        try {
            Map<String, Object> result = gameService.executeAction(
                    gameId, userId, request.getAction(), request.getTargetId());

            return ResponseEntity.ok(ApiResponse.success("行动成功", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest(e.getMessage()));
        }
    }

    /**
     * 获取游戏状态
     * GET /api/games/{gameId}
     */
    @GetMapping("/{gameId}")
    public ResponseEntity<ApiResponse<?>> getGameStatus(@PathVariable Long gameId) {
        try {
            Game game = gameService.getGameStatus(gameId);

            Map<String, Object> data = new HashMap<>();
            data.put("gameId", game.getId());
            data.put("status", game.getStatus().name());
            data.put("round", game.getCurrentRound());
            data.put("phase", game.getCurrentPhase().name());
            data.put("winner", game.getWinner() != null ? game.getWinner().name() : null);
            data.put("startedAt", game.getStartedAt());
            data.put("endedAt", game.getEndedAt());

            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest(e.getMessage()));
        }
    }

    /**
     * 获取游戏玩家列表
     * GET /api/games/{gameId}/players
     */
    @GetMapping("/{gameId}/players")
    public ResponseEntity<ApiResponse<?>> getPlayers(
            @PathVariable Long gameId,
            @RequestAttribute("userId") Long userId) {
        try {
            List<Player> players = gameService.getPlayers(gameId);
            Game game = gameService.getGameStatus(gameId);
            boolean isFinished = game.getStatus() == Game.GameStatus.FINISHED;

            Player myPlayer = players.stream()
                    .filter(p -> p.getUser() != null && p.getUser().getId().equals(userId))
                    .findFirst().orElse(null);

            List<Map<String, Object>> playerInfos = players.stream().map(p -> {
                Map<String, Object> info = new HashMap<>();
                info.put("playerId", p.getId());
                info.put("seatNumber", p.getSeatNumber());
                info.put("username", p.getUser() != null ? p.getUser().getUsername() : p.getAiName());
                info.put("avatarUrl", p.getUser() != null ? p.getUser().getAvatarUrl() : null);
                info.put("isAi", p.getIsAi());
                info.put("status", p.getStatus().name());
                info.put("isCaptain", p.getIsCaptain());

                // 角色可见性：自己可见 + 游戏结束后全部公开
                if (isFinished || (myPlayer != null && p.getId().equals(myPlayer.getId()))) {
                    info.put("role", p.getRole().name());
                } else {
                    info.put("role", "UNKNOWN");
                }
                return info;
            }).collect(Collectors.toList());

            Map<String, Object> data = new HashMap<>();
            data.put("players", playerInfos);
            if (myPlayer != null) {
                data.put("myPlayerId", myPlayer.getId());
                data.put("myRole", myPlayer.getRole().name());
                data.put("mySeat", myPlayer.getSeatNumber());
                
                // 狼人可以看到队友
                if (myPlayer.getRole() == Player.Role.WEREWOLF) {
                    List<Long> teammates = players.stream()
                            .filter(p -> p.getRole() == Player.Role.WEREWOLF)
                            .filter(p -> !p.getId().equals(myPlayer.getId()))
                            .map(Player::getId)
                            .collect(Collectors.toList());
                    data.put("teammates", teammates);
                }
            }

            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest(e.getMessage()));
        }
    }

    /**
     * 获取游戏日志
     * GET /api/games/{gameId}/logs
     */
    @GetMapping("/{gameId}/logs")
    public ResponseEntity<ApiResponse<?>> getGameLogs(@PathVariable Long gameId) {
        try {
            List<GameLog> logs = gameService.getGameLogs(gameId);

            List<Map<String, Object>> logInfos = logs.stream().map(l -> {
                Map<String, Object> info = new HashMap<>();
                info.put("id", l.getId());
                info.put("round", l.getRound());
                info.put("phase", l.getPhase());
                info.put("playerId", l.getPlayerId());
                info.put("actionType", l.getActionType());
                info.put("createdAt", l.getCreatedAt());
                return info;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(logInfos));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest(e.getMessage()));
        }
    }
}
