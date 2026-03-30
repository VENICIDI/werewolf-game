package com.werewolf.repository;

import com.werewolf.entity.GameLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameLogRepository extends JpaRepository<GameLog, Long> {
    List<GameLog> findByGameIdOrderByCreatedAtAsc(Long gameId);
    List<GameLog> findByGameIdAndRound(Long gameId, Integer round);
    List<GameLog> findByGameIdAndPhase(Long gameId, String phase);
    List<GameLog> findByGameIdAndPlayerId(Long gameId, Long playerId);
}
