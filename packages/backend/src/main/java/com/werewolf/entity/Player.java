package com.werewolf.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "players")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Player {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;
    
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    @Column(name = "is_ai")
    private Boolean isAi = false;
    
    @Column(name = "ai_name")
    private String aiName;
    
    @Column(name = "seat_number")
    private Integer seatNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Role role;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PlayerStatus status = PlayerStatus.ALIVE;
    
    @Column(name = "is_captain")
    private Boolean isCaptain = false;
    
    @Column(name = "can_speak")
    private Boolean canSpeak = true;
    
    @Column(name = "can_vote")
    private Boolean canVote = true;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    // 角色枚举
    public enum Role {
        UNKNOWN,    // 未知（游戏开始前）
        VILLAGER,   // 村民
        WEREWOLF,   // 狼人
        SEER,       // 预言家
        WITCH,      // 女巫
        HUNTER,     // 猎人
        GUARD,      // 守卫
        IDIOT       // 白痴
    }
    
    // 玩家状态
    public enum PlayerStatus {
        ALIVE,      // 存活
        DEAD,       // 死亡
        DISCONNECTED // 掉线
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
