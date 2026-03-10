package com.werewolf.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "games")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Game {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GameStatus status = GameStatus.PREPARING;
    
    @Column(name = "current_round")
    private Integer currentRound = 0;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "current_phase")
    private GamePhase currentPhase = GamePhase.NONE;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "winner")
    private Winner winner;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "ended_at")
    private LocalDateTime endedAt;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    // 游戏状态
    public enum GameStatus {
        PREPARING,  // 准备中
        RUNNING,    // 进行中
        PAUSED,     // 暂停
        FINISHED    // 已结束
    }
    
    // 游戏阶段
    public enum GamePhase {
        NONE,           // 无
        NIGHT_START,    // 夜晚开始
        WEREWOLF,       // 狼人行动
        SEER,           // 预言家行动
        WITCH,          // 女巫行动
        HUNTER,         // 猎人行动
        DAY_START,      // 白天开始
        DISCUSSION,     // 讨论阶段
        VOTING,         // 投票阶段
        EXECUTION       // 处决阶段
    }
    
    // 获胜方
    public enum Winner {
        NONE,       // 无
        WEREWOLF,   // 狼人阵营
        VILLAGER,   // 村民阵营
        THIRD_PARTY // 第三方
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
