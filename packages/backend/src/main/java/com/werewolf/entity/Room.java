package com.werewolf.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "room_code", unique = true, nullable = false, length = 10)
    private String roomCode;
    
    @Column(name = "room_name", nullable = false, length = 50)
    private String roomName;
    
    @ManyToOne
    @JoinColumn(name = "host_id", nullable = false)
    private User host;
    
    @Column(name = "max_players")
    private Integer maxPlayers = 12;
    
    @Column(name = "current_players")
    private Integer currentPlayers = 0;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RoomStatus status = RoomStatus.WAITING;
    
    @Column(name = "has_password")
    private Boolean hasPassword = false;
    
    @Column(name = "password")
    private String password;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum RoomStatus {
        WAITING,    // 等待中
        PLAYING,    // 游戏中
        FINISHED    // 已结束
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
