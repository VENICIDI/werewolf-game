package com.werewolf.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.werewolf.entity.Game;
import com.werewolf.entity.Player;
import com.werewolf.entity.Room;
import com.werewolf.entity.User;
import com.werewolf.repository.GameRepository;
import com.werewolf.repository.PlayerRepository;
import com.werewolf.service.RoomService;
import com.werewolf.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RoomWebSocketHandler extends TextWebSocketHandler {
    
    // 房间会话管理: roomCode -> Map<userId, session>
    private final Map<String, Map<Long, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    
    // 用户房间映射: userId -> roomCode
    private final Map<Long, String> userRoomMap = new ConcurrentHashMap<>();
    
    private final ObjectMapper objectMapper;
    private final RoomService roomService;
    private final UserService userService;
    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    
    public RoomWebSocketHandler(
            ObjectMapper objectMapper,
            RoomService roomService,
            UserService userService,
            GameRepository gameRepository,
            PlayerRepository playerRepository) {
        this.objectMapper = objectMapper;
        this.roomService = roomService;
        this.userService = userService;
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomCode = getRoomCodeFromSession(session);
        Long userId = getUserIdFromSession(session);
        String username = getUsernameFromSession(session);
        
        log.info("WebSocket 连接建立 - 房间: {}, 用户: {}", roomCode, username);
        
        // 加入房间会话
        roomSessions.computeIfAbsent(roomCode, k -> new ConcurrentHashMap<>()).put(userId, session);
        userRoomMap.put(userId, roomCode);
        
        // 广播用户加入消息
        WebSocketMessage message = new WebSocketMessage(
                WebSocketMessage.Type.JOIN_ROOM,
                userId,
                username,
                username + " 加入了房间",
                roomCode
        );
        broadcastToRoom(roomCode, message);
        
        // 发送房间当前状态
        sendRoomState(roomCode, userId);
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        String roomCode = getRoomCodeFromSession(session);
        Long userId = getUserIdFromSession(session);
        String username = getUsernameFromSession(session);
        
        try {
            WebSocketMessage message = objectMapper.readValue(textMessage.getPayload(), WebSocketMessage.class);
            String msgType = message.getType();
            
            switch (msgType) {
                case WebSocketMessage.Type.PLAYER_CHAT:
                    handleChatMessage(roomCode, userId, username, message);
                    break;
                    
                case WebSocketMessage.Type.PLAYER_READY:
                    handleReadyMessage(roomCode, userId, username, message);
                    break;
                    
                case WebSocketMessage.Type.HEARTBEAT:
                    handleHeartbeat(session);
                    break;
                    
                default:
                    log.warn("未知消息类型: {}", msgType);
            }
        } catch (Exception e) {
            log.error("处理消息失败: {}", e.getMessage());
            sendError(session, "消息格式错误");
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomCode = getRoomCodeFromSession(session);
        Long userId = getUserIdFromSession(session);
        String username = getUsernameFromSession(session);
        
        log.info("WebSocket 连接关闭 - 房间: {}, 用户: {}", roomCode, username);
        
        if (roomCode != null && userId != null) {
            Map<Long, WebSocketSession> sessions = roomSessions.get(roomCode);
            if (sessions != null) {
                sessions.remove(userId);
                if (sessions.isEmpty()) {
                    roomSessions.remove(roomCode);
                }
            }
            userRoomMap.remove(userId);
            
            // 如果房间处于等待状态，自动将用户从房间中移除
            try {
                Room room = roomService.findByRoomCode(roomCode).orElse(null);
                if (room != null && room.getStatus() == Room.RoomStatus.WAITING) {
                    User user = userService.findById(userId).orElse(null);
                    if (user != null && roomService.isUserInRoom(room.getId(), userId)) {
                        roomService.leaveRoom(room, user);
                        log.info("用户 {} 断连，已自动退出等待中的房间 {}", username, roomCode);
                    }
                }
            } catch (Exception e) {
                log.error("自动退出房间失败 - 用户: {}, 房间: {}, 错误: {}", username, roomCode, e.getMessage());
            }
            
            // 广播用户离开消息
            WebSocketMessage message = new WebSocketMessage(
                    WebSocketMessage.Type.LEAVE_ROOM,
                    userId,
                    username,
                    username + " 离开了房间",
                    roomCode
            );
            broadcastToRoom(roomCode, message);
        }
    }
    
    // 处理聊天消息
    private void handleChatMessage(String roomCode, Long userId, String username, WebSocketMessage message) {
        Room room = roomService.findByRoomCode(roomCode).orElse(null);
        if (room != null) {
            Game game = gameRepository.findByRoomId(room.getId()).orElse(null);
            if (game != null && game.getStatus() == Game.GameStatus.RUNNING) {
                if (game.getCurrentPhase() != Game.GamePhase.DISCUSSION) {
                    sendToUser(roomCode, userId, new WebSocketMessage(WebSocketMessage.Type.ERROR, "当前阶段禁止发言"));
                    return;
                }

                Player player = playerRepository.findByGameIdAndUserId(game.getId(), userId).orElse(null);
                if (player == null || player.getStatus() != Player.PlayerStatus.ALIVE) {
                    sendToUser(roomCode, userId, new WebSocketMessage(WebSocketMessage.Type.ERROR, "你已死亡，不能发言"));
                    return;
                }
                if (!Boolean.TRUE.equals(player.getCanSpeak())) {
                    sendToUser(roomCode, userId, new WebSocketMessage(WebSocketMessage.Type.ERROR, "还没轮到你发言"));
                    return;
                }
            }
        }

        message.setSenderId(userId);
        message.setSenderName(username);
        message.setRoomCode(roomCode);
        broadcastToRoom(roomCode, message);

        if (room != null) {
            Game game = gameRepository.findByRoomId(room.getId()).orElse(null);
            if (game != null && game.getStatus() == Game.GameStatus.RUNNING
                    && game.getCurrentPhase() == Game.GamePhase.DISCUSSION) {
                Player speaker = playerRepository.findByGameIdAndUserId(game.getId(), userId).orElse(null);
                if (speaker != null) {
                    advanceDiscussionSpeaker(game, speaker.getId());
                }
            }
        }
    }

    private void advanceDiscussionSpeaker(Game game, Long currentSpeakerId) {
        List<Player> alivePlayers = playerRepository.findByGameIdAndStatus(game.getId(), Player.PlayerStatus.ALIVE)
                .stream()
                .sorted(Comparator.comparing(Player::getSeatNumber))
                .toList();
        if (alivePlayers.size() <= 1) {
            return;
        }

        int currentIdx = -1;
        for (int i = 0; i < alivePlayers.size(); i++) {
            if (alivePlayers.get(i).getId().equals(currentSpeakerId)) {
                currentIdx = i;
                break;
            }
        }
        if (currentIdx < 0) {
            return;
        }

        Player nextSpeaker = pickNextDiscussionSpeaker(alivePlayers, currentIdx);
        for (Player p : alivePlayers) {
            p.setCanSpeak(p.getId().equals(nextSpeaker.getId()));
            playerRepository.save(p);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("playerId", nextSpeaker.getId());
        data.put("seatNumber", nextSpeaker.getSeatNumber());
        data.put("username", nextSpeaker.getUser() != null ? nextSpeaker.getUser().getUsername() : nextSpeaker.getAiName());
        data.put("message", "轮到" + nextSpeaker.getSeatNumber() + "号玩家发言");
        broadcastToRoom(game.getRoom().getRoomCode(), new WebSocketMessage("SPEAKER_CHANGE", data));
    }

    private Player pickNextDiscussionSpeaker(List<Player> alivePlayers, int currentIdx) {
        Player defaultNext = alivePlayers.get((currentIdx + 1) % alivePlayers.size());
        if (!Boolean.TRUE.equals(defaultNext.getIsAi())) {
            return defaultNext;
        }

        for (int i = 1; i <= alivePlayers.size(); i++) {
            Player candidate = alivePlayers.get((currentIdx + i) % alivePlayers.size());
            if (!Boolean.TRUE.equals(candidate.getIsAi())) {
                return candidate;
            }
        }
        return defaultNext;
    }
    
    // 处理准备消息
    private void handleReadyMessage(String roomCode, Long userId, String username, WebSocketMessage message) {
        try {
            boolean ready = (Boolean) message.getData();
            Room room = roomService.findByRoomCode(roomCode)
                    .orElseThrow(() -> new RuntimeException("房间不存在"));
            roomService.setReady(room.getId(), userId, ready);
            
            WebSocketMessage response = new WebSocketMessage(
                    WebSocketMessage.Type.PLAYER_READY,
                    userId,
                    username,
                    ready,
                    roomCode
            );
            broadcastToRoom(roomCode, response);
        } catch (Exception e) {
            log.error("设置准备状态失败: {}", e.getMessage());
        }
    }
    
    // 处理心跳
    private void handleHeartbeat(WebSocketSession session) throws IOException {
        WebSocketMessage ack = new WebSocketMessage(
                WebSocketMessage.Type.HEARTBEAT_ACK,
                "pong"
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
    }
    
    // 广播消息到房间
    public void broadcastToRoom(String roomCode, WebSocketMessage message) {
        Map<Long, WebSocketSession> sessions = roomSessions.get(roomCode);
        if (sessions == null) return;
        
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("消息序列化失败: {}", e.getMessage());
            return;
        }
        
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(jsonMessage));
                }
            } catch (IOException e) {
                log.error("发送消息失败: {}", e.getMessage());
            }
        });
    }
    
    // 发送房间状态给指定用户
    private void sendRoomState(String roomCode, Long userId) {
        try {
            Room room = roomService.findByRoomCode(roomCode).orElse(null);
            if (room == null) return;
            
            WebSocketMessage message = new WebSocketMessage(
                    WebSocketMessage.Type.ROOM_UPDATE,
                    room
            );
            
            WebSocketSession session = roomSessions.get(roomCode).get(userId);
            if (session != null && session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            }
        } catch (Exception e) {
            log.error("发送房间状态失败: {}", e.getMessage());
        }
    }
    
    /**
     * 单播消息给指定用户
     */
    public void sendToUser(String roomCode, Long userId, WebSocketMessage message) {
        Map<Long, WebSocketSession> sessions = roomSessions.get(roomCode);
        if (sessions == null) {
            log.warn("单播失败: 房间 {} 无活跃会话, type={}, userId={}", roomCode, message.getType(), userId);
            return;
        }

        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            } catch (IOException e) {
                log.error("单播消息失败 - 用户: {}, 错误: {}", userId, e.getMessage());
            }
        } else {
            log.warn("单播失败: 房间 {} 用户 {} 无会话或已关闭, type={}", roomCode, userId, message.getType());
        }
    }

    /**
     * 获取用户所在房间号
     */
    public String getRoomCodeByUserId(Long userId) {
        return userRoomMap.get(userId);
    }

    // 发送错误消息
    private void sendError(WebSocketSession session, String error) throws IOException {
        WebSocketMessage message = new WebSocketMessage(
                WebSocketMessage.Type.ERROR,
                error
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
    }
    
    // 从 Session 获取信息
    private String getRoomCodeFromSession(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
    
    private Long getUserIdFromSession(WebSocketSession session) {
        return (Long) session.getAttributes().get("userId");
    }
    
    private String getUsernameFromSession(WebSocketSession session) {
        return (String) session.getAttributes().get("username");
    }
}
