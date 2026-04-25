package com.werewolf.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebSocketMessage {
    
    // 消息类型
    private String type;
    
    // 发送者信息
    private Long senderId;
    private String senderName;
    
    // 消息内容
    private Object data;
    
    // 时间戳
    private LocalDateTime timestamp;
    
    // 房间号
    private String roomCode;
    
    public WebSocketMessage(String type, Object data) {
        this.type = type;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }
    
    public WebSocketMessage(String type, Long senderId, String senderName, Object data, String roomCode) {
        this.type = type;
        this.senderId = senderId;
        this.senderName = senderName;
        this.data = data;
        this.roomCode = roomCode;
        this.timestamp = LocalDateTime.now();
    }
    
    // 消息类型常量
    public static class Type {
        // 系统消息
        public static final String SYSTEM = "SYSTEM";
        public static final String ERROR = "ERROR";
        
        // 房间消息
        public static final String JOIN_ROOM = "JOIN_ROOM";
        public static final String LEAVE_ROOM = "LEAVE_ROOM";
        public static final String ROOM_UPDATE = "ROOM_UPDATE";
        
        // 玩家状态
        public static final String PLAYER_READY = "PLAYER_READY";
        public static final String PLAYER_CHAT = "PLAYER_CHAT";
        
        // 游戏消息
        public static final String GAME_START = "GAME_START";
        public static final String PHASE_CHANGE = "PHASE_CHANGE";
        public static final String PLAYER_ACTION = "PLAYER_ACTION";
        public static final String GAME_OVER = "GAME_OVER";
        public static final String ROLE_ASSIGN = "ROLE_ASSIGN";
        public static final String NIGHT_RESULT = "NIGHT_RESULT";
        public static final String DEATH_ANNOUNCE = "DEATH_ANNOUNCE";
        public static final String VOTE_START = "VOTE_START";
        public static final String VOTE_UPDATE = "VOTE_UPDATE";
        public static final String VOTE_RESULT = "VOTE_RESULT";
        public static final String SEER_RESULT = "SEER_RESULT";
        public static final String HUNTER_SHOOT = "HUNTER_SHOOT";
        public static final String ACTION_CONFIRM = "ACTION_CONFIRM";
        
        // 心跳
        public static final String HEARTBEAT = "HEARTBEAT";
        public static final String HEARTBEAT_ACK = "HEARTBEAT_ACK";
    }
}
