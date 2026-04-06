package com.werewolf.dto;

import com.werewolf.entity.Room;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomResponse {
    private Long id;
    private String roomCode;
    private String roomName;
    private Long hostId;
    private String hostName;
    private Integer maxPlayers;
    private Integer currentPlayers;
    private Room.RoomStatus status;
    private Boolean hasPassword;
    private LocalDateTime createdAt;
    private List<PlayerInfo> players;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlayerInfo {
        private Long userId;
        private String username;
        private String avatarUrl;
        private Boolean isHost;
        private Boolean isAi;
        private Boolean isReady;
    }
}
