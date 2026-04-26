package com.werewolf.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.werewolf.entity.Game;
import com.werewolf.entity.Player;
import com.werewolf.entity.Room;
import com.werewolf.entity.User;
import com.werewolf.game.DiscussionManager;
import com.werewolf.repository.GameRepository;
import com.werewolf.repository.PlayerRepository;
import com.werewolf.service.RoomService;
import com.werewolf.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
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
    private final ApplicationContext applicationContext;

    // 延迟注入 (避免循环依赖)
    private DiscussionManager discussionManager;

    public RoomWebSocketHandler(
            ObjectMapper objectMapper,
            RoomService roomService,
            UserService userService,
            GameRepository gameRepository,
            PlayerRepository playerRepository,
            ApplicationContext applicationContext) {
        this.objectMapper = objectMapper;
        this.roomService = roomService;
        this.userService = userService;
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.applicationContext = applicationContext;
    }

    private DiscussionManager getDiscussionManager() {
        if (discussionManager == null) {
            discussionManager = applicationContext.getBean(DiscussionManager.class);
        }
        return discussionManager;
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

                case WebSocketMessage.Type.SKIP_SPEECH:
                    handleSkipSpeech(roomCode, userId);
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
        log.info("[CHAT-IN] roomCode={}, userId={}, content={}", roomCode, userId, extractContent(message));
        Room room = roomService.findByRoomCode(roomCode).orElse(null);
        if (room != null) {
            Game game = gameRepository.findByRoomId(room.getId()).orElse(null);
            if (game != null && game.getStatus() == Game.GameStatus.RUNNING) {
                if (game.getCurrentPhase() != Game.GamePhase.DISCUSSION) {
                    log.warn("[CHAT-REJECT] 阶段不对: phase={}, userId={}", game.getCurrentPhase(), userId);
                    sendToUser(roomCode, userId, new WebSocketMessage(WebSocketMessage.Type.ERROR, "当前阶段禁止发言"));
                    return;
                }

                Player player = playerRepository.findByGameIdAndUserId(game.getId(), userId).orElse(null);
                if (player == null || player.getStatus() != Player.PlayerStatus.ALIVE) {
                    log.warn("[CHAT-REJECT] 玩家死亡或不存在: userId={}", userId);
                    sendToUser(roomCode, userId, new WebSocketMessage(WebSocketMessage.Type.ERROR, "你已死亡，不能发言"));
                    return;
                }
                if (!Boolean.TRUE.equals(player.getCanSpeak())) {
                    log.warn("[CHAT-REJECT] 还没轮到: userId={}, playerId={}, canSpeak=false", userId, player.getId());
                    sendToUser(roomCode, userId, new WebSocketMessage(WebSocketMessage.Type.ERROR, "还没轮到你发言"));
                    return;
                }
            }
        }

        message.setSenderId(userId);
        message.setSenderName(username);
        message.setRoomCode(roomCode);
        broadcastToRoom(roomCode, message);

        // 讨论阶段: 不再"发一次即切人", 改为记录发言到所有 AI 的记忆
        if (room != null) {
            Game game = gameRepository.findByRoomId(room.getId()).orElse(null);
            if (game != null && game.getStatus() == Game.GameStatus.RUNNING
                    && game.getCurrentPhase() == Game.GamePhase.DISCUSSION) {
                Player speaker = playerRepository.findByGameIdAndUserId(game.getId(), userId).orElse(null);
                if (speaker != null) {
                    // 提取内容
                    String content = extractContent(message);
                    if (content != null && !content.isBlank()) {
                        log.info("[CHAT-PUSH-AI] gameId={}, speakerId={}, content={}",
                                game.getId(), speaker.getId(),
                                content.length() > 50 ? content.substring(0, 50) + "..." : content);
                        try {
                            getDiscussionManager().onHumanChat(game.getId(), speaker.getId(), content);
                        } catch (Exception e) {
                            log.warn("推送真人发言到 AI 记忆失败: {}", e.getMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * 从 PLAYER_CHAT 消息提取发言内容
     */
    @SuppressWarnings("unchecked")
    private String extractContent(WebSocketMessage message) {
        Object data = message.getData();
        if (data instanceof String) return (String) data;
        if (data instanceof Map) {
            Object c = ((Map<String, Object>) data).get("content");
            if (c != null) return c.toString();
        }
        return null;
    }

    /**
     * 处理"结束发言"按钮: 真人主动跳过剩余发言时间
     */
    private void handleSkipSpeech(String roomCode, Long userId) {
        try {
            Room room = roomService.findByRoomCode(roomCode).orElse(null);
            if (room == null) return;
            Game game = gameRepository.findByRoomId(room.getId()).orElse(null);
            if (game == null || game.getStatus() != Game.GameStatus.RUNNING
                    || game.getCurrentPhase() != Game.GamePhase.DISCUSSION) {
                sendToUser(roomCode, userId, new WebSocketMessage(WebSocketMessage.Type.ERROR, "当前阶段不能结束发言"));
                return;
            }
            Player speaker = playerRepository.findByGameIdAndUserId(game.getId(), userId).orElse(null);
            if (speaker == null || speaker.getStatus() != Player.PlayerStatus.ALIVE) {
                return;
            }
            if (!Boolean.TRUE.equals(speaker.getCanSpeak())) {
                sendToUser(roomCode, userId, new WebSocketMessage(WebSocketMessage.Type.ERROR, "还没轮到你发言"));
                return;
            }
            getDiscussionManager().advanceNext(game.getId(), speaker.getId(), "skip_button");
        } catch (Exception e) {
            log.error("handleSkipSpeech 异常: {}", e.getMessage(), e);
        }
    }

    // ✨ 讨论阶段的"切换发言人"逻辑已迁移到 DiscussionManager, 此处旧实现已移除
    
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
