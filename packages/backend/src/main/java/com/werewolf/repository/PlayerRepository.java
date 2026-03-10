package com.werewolf.repository;

import com.werewolf.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {
    List<Player> findByGameId(Long gameId);
    Optional<Player> findByGameIdAndUserId(Long gameId, Long userId);
    List<Player> findByGameIdAndStatus(Long gameId, Player.PlayerStatus status);
}
