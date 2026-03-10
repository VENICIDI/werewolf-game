package com.werewolf.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.werewolf.entity.Room;
import com.werewolf.service.RoomService;
import lombok.extern.slf4j.Slf4j;
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
    
    public RoomWebSocketHandler(ObjectMapper objectMapper, RoomService roomService) {
        this.objectMapper = objectMapper;
        this.roomService = roomService;
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
            WebSocketMessage message = objectMapper.readValue(textMessage.getPayload(), WebSocketMessage
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
        
        // 从房间移除
        if (roomCode != null && userId != null) {
            Map<Long, WebSocketSession> sessions = roomSessions.get(roomCode);
            if (sessions != null) {
                sessions.remove(userId);
                if (sessions.isEmpty()) {
                    roomSessions.remove(roomCode);
                }
            }
            userRoomMap.remove(userId);
            
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
        message.setSenderId(userId);
        message.setSenderName(username);
        message.setRoomCode(roomCode);
        broadcastToRoom(roomCode, message);
    }
    
    // 处理准备消息
    private void handleReadyMessage(String roomCode, Long userId, String username, WebSocketMessage message) {
        try {
            boolean ready = (Boolean) message.getData();
            roomService.setReady(roomCode, userId, ready);
            
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
