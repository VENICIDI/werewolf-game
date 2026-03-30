package com.werewolf.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.werewolf.config.ConfigLoader;
import com.werewolf.entity.*;
import com.werewolf.game.*;
import com.werewolf.game.action.ActionContext;
import com.werewolf.game.action.ActionDispatcher;
import com.werewolf.repository.*;
import com.werewolf.websocket.RoomWebSocketHandler;
import com.werewolf.websocket.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 游戏服务 - 通过命令模式统一分发所有游戏行动
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final GameLogRepository gameLogRepository;

    private final ConfigLoader configLoader;
    private final RoleAssigner roleAssigner;
    private final GameStateMachine stateMachine;
    private final PhaseScheduler phaseScheduler;
    private final NightActionStore nightActionStore;
    private final VoteManager voteManager;
    private final ActionDispatcher actionDispatcher;
    private final RoomWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    // ========================= 统一行动入口 =========================

    /**
     * 统一行动入口 — 所有游戏行动（夜晚/投票/猎人等）通过此方法分发
     *
     * @param gameId   游戏ID
     * @param userId   用户ID
     * @param action   行动类型 (kill/check/save/poison/guard/vote/shoot/skip)
     * @param targetId 目标玩家ID (部分行动可为null)
     * @return 执行结果
     */
    @Transactional
    public Map<String, Object> executeAction(Long gameId, Long userId, String action, Long targetId) {
        Game game = getGameStatus(gameId);
        Player player = playerRepository.findByGameIdAndUserId(gameId, userId)
                .orElseThrow(() -> new RuntimeException("您不在此游戏中"));

        // 死亡校验 — shoot 命令允许死亡玩家（猎人）执行
        if (!"shoot".equals(action) && player.getStatus() != Player.PlayerStatus.ALIVE) {
            throw new RuntimeException("您已死亡，无法行动");
        }

        // 重复提交校验（skip/vote/shoot 不受夜间重复提交限制）
        NightActionStore.RoundActions nightActions = nightActionStore.getOrCreate(gameId);
        String phaseKey = game.getCurrentPhase().name() + "_" + player.getId();
        boolean isNightAction = Set.of("kill", "check", "save", "poison", "guard").contains(action);
        if (isNightAction && nightActions.isActionSubmitted(phaseKey)) {
            throw new RuntimeException("您已提交过行动");
        }

        // 构建行动上下文
        ActionContext context = ActionContext.builder()
                .game(game)
                .player(player)
                .targetId(targetId)
                .nightActions(nightActions)
                .voteManager(voteManager)
                .roleAssigner(roleAssigner)
                .playerRepository(playerRepository)
                .webSocketHandler(webSocketHandler)
                .build();

        // 通过行动分发器执行：validate + execute
        Map<String, Object> result = actionDispatcher.dispatch(action, context);

        // 夜间行动标记已提交
        if (isNightAction) {
            nightActions.markActionSubmitted(phaseKey);
        }

        // 记录日志
        String logData = targetId != null ? "{\"targetId\":" + targetId + "}" : null;
        saveLog(gameId, game.getCurrentRound(), game.getCurrentPhase().name(),
                player.getId(), action.toUpperCase(), logData);

        // WebSocket 确认（仅给操作者本人）
        String roomCode = game.getRoom().getRoomCode();
        WebSocketMessage confirmMsg = new WebSocketMessage(
                WebSocketMessage.Type.ACTION_CONFIRM, result);
        webSocketHandler.sendToUser(roomCode, userId, confirmMsg);

        // 投票完成后自动结算
        if ("vote".equals(action)) {
            VoteManager.VoteSession session = voteManager.getVoteSession(gameId);
            if (session != null && session.isAllVoted()) {
                resolveVoting(gameId);
            }
        }

        // 猎人开枪后检查胜负
        if ("shoot".equals(action)) {
            checkWinCondition(gameId);
        }

        return result;
    }

    // ========================= 游戏启动 =========================

    @Transactional
    public Game startGame(Long roomId, String gameModeId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("房间不存在"));

        if (room.getStatus() != Room.RoomStatus.WAITING) {
            throw new RuntimeException("房间不在等待状态");
        }
        if (room.getCurrentPlayers() < 6) {
            throw new RuntimeException("人数不足，至少需要6人");
        }

        Game game = Game.builder()
                .room(room)
                .status(Game.GameStatus.PREPARING)
                .currentRound(0)
                .currentPhase(Game.GamePhase.NONE)
                .winner(Game.Winner.NONE)
                .createdAt(LocalDateTime.now())
                .build();
        game = gameRepository.save(game);

        List<Player> players = createPlayers(game, room.getId());

        Map<Long, Player.Role> roleAssignments = roleAssigner.assignRoles(gameModeId, players);
        for (Player player : players) {
            player.setRole(roleAssignments.get(player.getId()));
            playerRepository.save(player);
        }

        game.setStatus(Game.GameStatus.RUNNING);
        game.setCurrentRound(1);
        game.setCurrentPhase(Game.GamePhase.NIGHT_START);
        game.setStartedAt(LocalDateTime.now());
        gameRepository.save(game);

        room.setStatus(Room.RoomStatus.PLAYING);
        roomRepository.save(room);

        nightActionStore.getOrCreate(game.getId());
        broadcastGameStart(room.getRoomCode(), game.getId(), players);
        saveLog(game.getId(), 0, "GAME_START", null, "START", null);
        phaseScheduler.startGame(game.getId(), gameModeId);

        log.info("游戏开始 - ID: {}, 房间: {}, 模式: {}, 玩家数: {}",
                game.getId(), room.getRoomCode(), gameModeId, players.size());
        return game;
    }

    private List<Player> createPlayers(Game game, Long roomId) {
        List<RoomMember> members = roomMemberRepository.findByRoomId(roomId);
        List<Player> players = new ArrayList<>();

        int seatNumber = 1;
        for (RoomMember member : members) {
            Player player = Player.builder()
                    .game(game)
                    .user(member.getUser())
                    .isAi(false)
                    .seatNumber(seatNumber++)
                    .role(Player.Role.UNKNOWN)
                    .status(Player.PlayerStatus.ALIVE)
                    .isCaptain(false)
                    .canSpeak(true)
                    .canVote(true)
                    .build();
            player = playerRepository.save(player);
            players.add(player);
        }

        log.info("创建 {} 个玩家", players.size());
        return players;
    }

    private void broadcastGameStart(String roomCode, Long gameId, List<Player> players) {
        List<Map<String, Object>> publicPlayerInfos = players.stream().map(p -> {
            Map<String, Object> info = new HashMap<>();
            info.put("playerId", p.getId());
            info.put("userId", p.getUser().getId());
            info.put("username", p.getUser().getUsername());
            info.put("seatNumber", p.getSeatNumber());
            info.put("avatarUrl", p.getUser().getAvatarUrl());
            return info;
        }).collect(Collectors.toList());

        Map<String, Object> gameStartData = new HashMap<>();
        gameStartData.put("gameId", gameId);
        gameStartData.put("players", publicPlayerInfos);

        webSocketHandler.broadcastToRoom(roomCode,
                new WebSocketMessage(WebSocketMessage.Type.GAME_START, gameStartData));

        for (Player player : players) {
            if (player.getUser() != null) {
                Map<String, Object> roleData = new HashMap<>();
                roleData.put("playerId", player.getId());
                roleData.put("role", player.getRole().name());
                roleData.put("seatNumber", player.getSeatNumber());

                webSocketHandler.sendToUser(roomCode, player.getUser().getId(),
                        new WebSocketMessage(WebSocketMessage.Type.ROLE_ASSIGN, roleData));
            }
        }
    }

    // ========================= 夜晚结算 =========================

    @Transactional
    public List<Player> resolveNight(Long gameId) {
        Game game = getGameStatus(gameId);
        NightActionStore.RoundActions actions = nightActionStore.getOrCreate(gameId);

        List<Long> deadPlayerIds = actions.resolveNight();
        List<Player> deadPlayers = new ArrayList<>();

        for (Long playerId : deadPlayerIds) {
            Player player = playerRepository.findById(playerId).orElse(null);
            if (player != null && player.getStatus() == Player.PlayerStatus.ALIVE) {
                player.setStatus(Player.PlayerStatus.DEAD);
                player.setCanSpeak(false);
                player.setCanVote(false);
                playerRepository.save(player);
                deadPlayers.add(player);

                saveLog(gameId, game.getCurrentRound(), "NIGHT_DEATH",
                        playerId, "DEATH", null);
            }
        }

        broadcastDeathAnnounce(game, deadPlayers);

        for (Player dead : deadPlayers) {
            if (dead.getRole() == Player.Role.HUNTER) {
                boolean poisoned = dead.getId().equals(actions.getWitchPoisonTarget());
                if (!poisoned) {
                    notifyHunterCanShoot(game, dead);
                }
            }
        }

        log.info("夜晚结算完成 - 游戏: {}, 死亡: {} 人", gameId, deadPlayers.size());
        return deadPlayers;
    }

    private void broadcastDeathAnnounce(Game game, List<Player> deadPlayers) {
        String roomCode = game.getRoom().getRoomCode();

        List<Map<String, Object>> deathInfos = deadPlayers.stream().map(p -> {
            Map<String, Object> info = new HashMap<>();
            info.put("playerId", p.getId());
            info.put("seatNumber", p.getSeatNumber());
            info.put("username", p.getUser() != null ? p.getUser().getUsername() : p.getAiName());
            return info;
        }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("deaths", deathInfos);
        data.put("round", game.getCurrentRound());
        data.put("message", deadPlayers.isEmpty() ? "昨晚是平安夜" :
                deadPlayers.stream()
                        .map(p -> p.getSeatNumber() + "号")
                        .collect(Collectors.joining("、")) + "玩家死亡");

        webSocketHandler.broadcastToRoom(roomCode,
                new WebSocketMessage(WebSocketMessage.Type.DEATH_ANNOUNCE, data));
    }

    private void notifyHunterCanShoot(Game game, Player hunter) {
        if (hunter.getUser() != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("message", "你已死亡，可以选择开枪带走一个人");
            data.put("canShoot", true);

            webSocketHandler.sendToUser(game.getRoom().getRoomCode(), hunter.getUser().getId(),
                    new WebSocketMessage(WebSocketMessage.Type.HUNTER_SHOOT, data));
        }
    }

    // ========================= 投票处决 =========================

    @Transactional
    public void startVoting(Long gameId) {
        Game game = getGameStatus(gameId);
        List<Player> alivePlayers = playerRepository.findByGameIdAndStatus(gameId, Player.PlayerStatus.ALIVE);

        Set<Long> voterIds = alivePlayers.stream()
                .filter(Player::getCanVote)
                .map(Player::getId)
                .collect(Collectors.toSet());

        voteManager.startVote(gameId, voterIds);

        String roomCode = game.getRoom().getRoomCode();
        List<Map<String, Object>> candidates = alivePlayers.stream().map(p -> {
            Map<String, Object> info = new HashMap<>();
            info.put("playerId", p.getId());
            info.put("seatNumber", p.getSeatNumber());
            info.put("username", p.getUser() != null ? p.getUser().getUsername() : p.getAiName());
            return info;
        }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("candidates", candidates);
        data.put("message", "投票阶段开始，请投票");

        webSocketHandler.broadcastToRoom(roomCode,
                new WebSocketMessage(WebSocketMessage.Type.VOTE_START, data));
    }

    @Transactional
    public void resolveVoting(Long gameId) {
        Game game = getGameStatus(gameId);
        VoteManager.VoteSession session = voteManager.getVoteSession(gameId);
        if (session == null) {
            log.warn("投票会话不存在 - 游戏: {}", gameId);
            return;
        }

        VoteManager.VoteResult result = session.resolve();
        String roomCode = game.getRoom().getRoomCode();

        Map<String, Object> data = new HashMap<>();
        data.put("voteDetails", result.getVoteDetails());
        data.put("isTie", result.isTie());

        if (result.isTie()) {
            data.put("message", "平票，无人被处决");
            data.put("eliminatedPlayerId", null);
        } else if (result.getEliminatedPlayerId() != null) {
            Player eliminated = playerRepository.findById(result.getEliminatedPlayerId()).orElse(null);
            if (eliminated != null) {
                eliminated.setStatus(Player.PlayerStatus.DEAD);
                eliminated.setCanSpeak(false);
                eliminated.setCanVote(false);
                playerRepository.save(eliminated);

                data.put("eliminatedPlayerId", eliminated.getId());
                data.put("eliminatedSeat", eliminated.getSeatNumber());
                data.put("message", eliminated.getSeatNumber() + "号玩家被投票处决");

                saveLog(gameId, game.getCurrentRound(), "EXECUTION",
                        eliminated.getId(), "EXECUTED", null);

                if (eliminated.getRole() == Player.Role.HUNTER) {
                    notifyHunterCanShoot(game, eliminated);
                }
            }
        } else {
            data.put("message", "无人被处决");
            data.put("eliminatedPlayerId", null);
        }

        webSocketHandler.broadcastToRoom(roomCode,
                new WebSocketMessage(WebSocketMessage.Type.VOTE_RESULT, data));

        voteManager.clearVote(gameId);
        log.info("投票结算 - 游戏: {}, 平票: {}, 处决: {}",
                gameId, result.isTie(), result.getEliminatedPlayerId());

        checkWinCondition(gameId);
    }

    // ========================= 阶段管理 =========================

    @Transactional
    public void updatePhase(Long gameId, Game.GamePhase phase) {
        Game game = getGameStatus(gameId);
        game.setCurrentPhase(phase);
        gameRepository.save(game);

        String roomCode = game.getRoom().getRoomCode();
        Map<String, Object> data = new HashMap<>();
        data.put("gameId", gameId);
        data.put("phase", phase.name());
        data.put("round", game.getCurrentRound());

        webSocketHandler.broadcastToRoom(roomCode,
                new WebSocketMessage(WebSocketMessage.Type.PHASE_CHANGE, data));
    }

    @Transactional
    public void enterNewNight(Long gameId) {
        Game game = getGameStatus(gameId);
        game.setCurrentRound(game.getCurrentRound() + 1);
        game.setCurrentPhase(Game.GamePhase.NIGHT_START);
        gameRepository.save(game);

        nightActionStore.resetRound(gameId);
        log.info("进入第 {} 夜 - 游戏: {}", game.getCurrentRound(), gameId);
    }

    public void broadcastToGame(Long gameId, String type, Object data) {
        Game game = gameRepository.findById(gameId).orElse(null);
        if (game == null) return;

        webSocketHandler.broadcastToRoom(game.getRoom().getRoomCode(),
                new WebSocketMessage(type, data));
    }

    // ========================= 查询 =========================

    public Game getGameStatus(Long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("游戏不存在"));
    }

    public Optional<Game> getGameByRoomId(Long roomId) {
        return gameRepository.findByRoomId(roomId);
    }

    public List<Player> getPlayers(Long gameId) {
        return playerRepository.findByGameId(gameId);
    }

    public List<Player> getAlivePlayers(Long gameId) {
        return playerRepository.findByGameIdAndStatus(gameId, Player.PlayerStatus.ALIVE);
    }

    public List<GameLog> getGameLogs(Long gameId) {
        return gameLogRepository.findByGameIdOrderByCreatedAtAsc(gameId);
    }

    // ========================= 胜负判定 =========================

    @Transactional
    public void checkWinCondition(Long gameId) {
        List<Player> players = playerRepository.findByGameId(gameId);

        long werewolfCount = players.stream()
                .filter(p -> p.getStatus() == Player.PlayerStatus.ALIVE)
                .filter(p -> roleAssigner.isWerewolfCamp(p.getRole()))
                .count();

        long aliveVillagerSide = players.stream()
                .filter(p -> p.getStatus() == Player.PlayerStatus.ALIVE)
                .filter(p -> !roleAssigner.isWerewolfCamp(p.getRole()))
                .count();

        long godCount = players.stream()
                .filter(p -> p.getStatus() == Player.PlayerStatus.ALIVE)
                .filter(p -> roleAssigner.isGodRole(p.getRole()))
                .count();

        long civilianCount = aliveVillagerSide - godCount;

        if (werewolfCount == 0) {
            endGame(gameId, Game.Winner.VILLAGER);
        } else if (godCount == 0 || civilianCount <= 0) {
            endGame(gameId, Game.Winner.WEREWOLF);
        } else if (werewolfCount >= aliveVillagerSide) {
            endGame(gameId, Game.Winner.WEREWOLF);
        }
    }

    @Transactional
    public void endGame(Long gameId, Game.Winner winner) {
        Game game = getGameStatus(gameId);
        if (game.getStatus() == Game.GameStatus.FINISHED) return;

        game.setStatus(Game.GameStatus.FINISHED);
        game.setWinner(winner);
        game.setEndedAt(LocalDateTime.now());
        gameRepository.save(game);

        Room room = game.getRoom();
        room.setStatus(Room.RoomStatus.FINISHED);
        roomRepository.save(room);

        String roomCode = room.getRoomCode();
        List<Player> allPlayers = playerRepository.findByGameId(gameId);

        List<Map<String, Object>> playerRoles = allPlayers.stream().map(p -> {
            Map<String, Object> info = new HashMap<>();
            info.put("playerId", p.getId());
            info.put("seatNumber", p.getSeatNumber());
            info.put("username", p.getUser() != null ? p.getUser().getUsername() : p.getAiName());
            info.put("role", p.getRole().name());
            info.put("status", p.getStatus().name());
            return info;
        }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("winner", winner.name());
        data.put("message", getWinnerMessage(winner));
        data.put("players", playerRoles);

        webSocketHandler.broadcastToRoom(roomCode,
                new WebSocketMessage(WebSocketMessage.Type.GAME_OVER, data));

        phaseScheduler.stopGame(gameId);
        nightActionStore.clear(gameId);
        voteManager.clearVote(gameId);

        saveLog(gameId, game.getCurrentRound(), "GAME_END", null, "END",
                "{\"winner\":\"" + winner.name() + "\"}");

        log.info("游戏结束 - ID: {}, 获胜方: {}", gameId, winner);
    }

    private String getWinnerMessage(Game.Winner winner) {
        return switch (winner) {
            case VILLAGER -> "好人阵营获胜！";
            case WEREWOLF -> "狼人阵营获胜！";
            case THIRD_PARTY -> "第三方阵营获胜！";
            case NONE -> "平局";
        };
    }

    // ========================= 日志 =========================

    private void saveLog(Long gameId, int round, String phase, Long playerId,
                         String actionType, String actionData) {
        GameLog gameLog = GameLog.builder()
                .gameId(gameId)
                .round(round)
                .phase(phase)
                .playerId(playerId)
                .actionType(actionType)
                .actionData(actionData)
                .build();
        gameLogRepository.save(gameLog);
    }
}
