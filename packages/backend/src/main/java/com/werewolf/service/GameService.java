package com.werewolf.service;

import com.werewolf.config.ConfigLoader;
import com.werewolf.entity.Game;
import com.werewolf.entity.Player;
import com.werewolf.entity.Room;
import com.werewolf.game.GameStateMachine;
import com.werewolf.game.PhaseScheduler;
import com.werewolf.game.RoleAssigner;
import com.werewolf.repository.GameRepository;
import com.werewolf.repository.PlayerRepository;
import com.werewolf.repository.RoomRepository;
import com.werewolf.websocket.RoomWebSocketHandler;
import com.werewolf.websocket.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 游戏服务 - 整合游戏逻辑组件
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {
    
    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final RoomRepository roomRepository;
    
    private final ConfigLoader configLoader;
    private final RoleAssigner roleAssigner;
    private final GameStateMachine stateMachine;
    private final PhaseScheduler phaseScheduler;
    private final RoomWebSocketHandler webSocketHandler;
    
    /**
     * 开始游戏
     */
    @Transactional
    public Game startGame(Long roomId, String gameModeId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("房间不存在"));
        
        // 检查人数
        if (room.getCurrentPlayers() < 6) {
            throw new RuntimeException("人数不足，至少需要6人");
        }
        
        // 创建游戏
        Game game = Game.builder()
                .room(room)
                .status(Game.GameStatus.PREPARING)
                .currentRound(0)
                .currentPhase(Game.GamePhase.NONE)
                .winner(Game.Winner.NONE)
                .createdAt(LocalDateTime.now())
                .build();
        
        game = gameRepository.save(game);
        
        // 获取房间成员
        List<Player> players = createPlayers(game, roomId);
        
        // 分配角色
        Map<Long, Player.Role> roleAssignments = roleAssigner.assignRoles(gameModeId, players);
        
        // 更新玩家角色
        for (Player player : players) {
            Player.Role role = roleAssignments.get(player.getId());
            player.setRole(role);
            playerRepository.save(player);
        }
        
        // 更新游戏状态
        game.setStatus(Game.GameStatus.RUNNING);
        game.setCurrentRound(1);
        game.setCurrentPhase(Game.GamePhase.NIGHT_START);
        game.setStartedAt(LocalDateTime.now());
        gameRepository.save(game);
        
        // 更新房间状态
        room.setStatus(Room.RoomStatus.PLAYING);
        roomRepository.save(room);
        
        // 广播游戏开始
        broadcastGameStart(room.getRoomCode(), players);
        
        // 启动阶段调度
        phaseScheduler.startGame(game.getId(), gameModeId);
        
        log.info("游戏开始 - ID: {}, 房间: {}, 模式: {}", 
            game.getId(), room.getRoomCode(), gameModeId);
        
        return game;
    }
    
    /**
     * 创建玩家
     */
    private List<Player> createPlayers(Game game, Long roomId) {
        // TODO: 从 RoomMember 创建 Player
        // 这里简化处理
        return new ArrayList<>();
    }
    
    /**
     * 广播游戏开始
     */
    private void broadcastGameStart(String roomCode, List<Player> players) {
        WebSocketMessage message = new WebSocketMessage(
                WebSocketMessage.Type.GAME_START,
                "游戏开始！"
        );
        webSocketHandler.broadcastToRoom(roomCode, message);
        
        // 单独通知每个玩家自己的角色
        for (Player player : players) {
            // TODO: 通过 WebSocket 发送给特定玩家
        }
    }
    
    /**
     * 获取游戏状态
     */
    public Game getGameStatus(Long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("游戏不存在"));
    }
    
    /**
     * 处理夜晚行动
     */
    @Transactional
    public void handleNightAction(Long gameId, Long playerId, String action, Long targetId) {
        Game game = getGameStatus(gameId);
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("玩家不存在"));
        
        // 验证阶段
        if (!isValidNightPhase(game.getCurrentPhase(), player.getRole())) {
            throw new RuntimeException("现在不是你的行动阶段");
        }
        
        // 处理行动
        switch (action) {
            case "kill":
                handleWerewolfKill(game, player, targetId);
                break;
            case "check":
                handleSeerCheck(game, player, targetId);
                break;
            case "save":
                handleWitchSave(game, player, targetId);
                break;
            case "poison":
                handleWitchPoison(game, player, targetId);
                break;
            case "guard":
                handleGuard(game, player, targetId);
                break;
            default:
                throw new RuntimeException("未知的行动: " + action);
        }
    }
    
    /**
     * 验证夜晚阶段
     */
    private boolean isValidNightPhase(Game.GamePhase phase, Player.Role role) {
        return switch (role) {
            case WEREWOLF -> phase == Game.GamePhase.WEREWOLF;
            case SEER -> phase == Game.GamePhase.SEER;
            case WITCH -> phase == Game.GamePhase.WITCH;
            case GUARD -> phase == Game.GamePhase.NONE; // TODO: 添加守卫阶段
            default -> false;
        };
    }
    
    /**
     * 处理狼人击杀
     */
    private void handleWerewolfKill(Game game, Player player, Long targetId) {
        log.info("狼人 {} 选择击杀 {}", player.getId(), targetId);
        // TODO: 记录击杀目标
    }
    
    /**
     * 处理预言家查验
     */
    private void handleSeerCheck(Game game, Player player, Long targetId) {
        log.info("预言家 {} 查验 {}", player.getId(), targetId);
        // TODO: 返回查验结果
    }
    
    /**
     * 处理女巫救人
     */
    private void handleWitchSave(Game game, Player player, Long targetId) {
        log.info("女巫 {} 使用解药救 {}", player.getId(), targetId);
        // TODO: 记录救人
    }
    
    /**
     * 处理女巫毒人
     */
    private void handleWitchPoison(Game game, Player player, Long targetId) {
        log.info("女巫 {} 使用毒药毒 {}", player.getId(), targetId);
        // TODO: 记录毒人
    }
    
    /**
     * 处理守卫守护
     */
    private void handleGuard(Game game, Player player, Long targetId) {
        log.info("守卫 {} 守护 {}", player.getId(), targetId);
        // TODO: 记录守护
    }
    
    /**
     * 结束游戏
     */
    @Transactional
    public void endGame(Long gameId, Game.Winner winner) {
        Game game = getGameStatus(gameId);
        
        game.setStatus(Game.GameStatus.FINISHED);
        game.setWinner(winner);
        game.setEndedAt(LocalDateTime.now());
        gameRepository.save(game);
        
        // 更新房间状态
        Room room = game.getRoom();
        room.setStatus(Room.RoomStatus.FINISHED);
        roomRepository.save(room);
        
        // 广播游戏结束
        WebSocketMessage message = new WebSocketMessage(
                WebSocketMessage.Type.GAME_OVER,
                winner + "阵营获胜！"
        );
        webSocketHandler.broadcastToRoom(room.getRoomCode(), message);
        
        // 停止调度
        phaseScheduler.stopGame(gameId);
        
        log.info("游戏结束 - ID: {}, 获胜方: {}", gameId, winner);
    }
    
    /**
     * 检查胜负条件
     */
    public void checkWinCondition(Long gameId) {
        Game game = getGameStatus(gameId);
        List<Player> players = playerRepository.findByGameId(gameId);
        
        long werewolfCount = players.stream()
                .filter(p -> p.getStatus() == Player.PlayerStatus.ALIVE)
                .filter(p -> roleAssigner.isWerewolfCamp(p.getRole()))
                .count();
        
        long villagerCount = players.stream()
                .filter(p -> p.getStatus() == Player.PlayerStatus.ALIVE)
                .filter(p -> roleAssigner.isVillagerCamp(p.getRole()))
                .count();
        
        long godCount = players.stream()
                .filter(p -> p.getStatus() == Player.PlayerStatus.ALIVE)
                .filter(p -> roleAssigner.isGodRole(p.getRole()))
                .count();
        
        // 判断胜负
        if (werewolfCount == 0) {
            endGame(gameId, Game.Winner.VILLAGER);
        } else if (godCount == 0 || (villagerCount - godCount) == 0) {
            endGame(gameId, Game.Winner.WEREWOLF);
        }
    }
}
