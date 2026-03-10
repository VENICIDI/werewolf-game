package com.werewolf.repository;

import com.werewolf.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByRoomCode(String roomCode);
    List<Room> findByStatus(Room.RoomStatus status);
    List<Room> findByHostId(Long hostId);
    boolean existsByRoomCode(String roomCode);
}
