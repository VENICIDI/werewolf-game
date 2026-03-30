package com.werewolf.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "round", nullable = false)
    private Integer round;

    @Column(name = "phase", nullable = false, length = 50)
    private String phase;

    @Column(name = "player_id")
    private Long playerId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "action_data", columnDefinition = "JSON")
    private String actionData;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
