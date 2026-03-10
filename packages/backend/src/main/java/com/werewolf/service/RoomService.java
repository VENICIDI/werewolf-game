package com.werewolf.service;

import com.werewolf.entity.Room;
import com.werewolf.entity.RoomMember;
import com.werewolf.entity.User;
import com.werewolf.repository.RoomMemberRepository;
import com.werewolf.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class RoomService {
    
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    
    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }
    
    /**
     * 创建房间
     */
    @Transactional
    public Room createRoom(String roomName, User host, Integer maxPlayers, String password) {
        String roomCode;
        do {
            roomCode = generateRoomCode();
        } while (roomRepository.existsByRoomCode(roomCode));
        
        Room room = Room.builder()
                .roomCode(roomCode)
                .roomName(roomName)
                .host(host)
                .maxPlayers(maxPlayers != null ? maxPlayers : 12)
                .currentPlayers(1)
                .status(Room.RoomStatus.WAITING)
                .hasPassword(password != null && !password.isEmpty())
                .password(password)
                .build();
        
        Room savedRoom = roomRepository.save(room);
        
        // 添加房主到房间成员
        RoomMember member = RoomMember.builder()
                .room(savedRoom)
                .user(host)
                .isHost(true)
                .isReady(true)
                .build();
        roomMemberRepository.save(member);
        
        return savedRoom;
    }
    
    /**
     * 获取等待中的房间列表
     */
    public List<Room> getWaitingRooms() {
        return roomRepository.findByStatus(Room.RoomStatus.WAITING);
    }
    
    /**
     * 根据房间号查找
     */
    public Optional<Room> findByRoomCode(String roomCode) {
        return roomRepository.findByRoomCode(roomCode.toUpperCase());
    }
    
    /**
     * 根据ID查找
     */
    public Optional<Room> findById(Long id) {
        return roomRepository.findById(id);
    }
    
    /**
     * 用户加入房间
     */
    @Transactional
    public void joinRoom(Room room, User user) {
        // 检查是否已在房间
        if (roomMemberRepository.existsByRoomIdAndUserId(room.getId(), user.getId())) {
            throw new RuntimeException("您已在该房间中");
        }
        
        // 检查房间是否已满
        if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
            throw new RuntimeException("房间已满");
        }
        
        // 更新房间人数
        room.setCurrentPlayers(room.getCurrentPlayers() + 1);
        roomRepository.save(room);
        
        // 添加成员
        RoomMember member = RoomMember.builder()
                .room(room)
                .user(user)
                .isHost(false)
                .isReady(false)
                .build();
        roomMemberRepository.save(member);
    }
    
    /**
     * 用户离开房间
     */
    @Transactional
    public void leaveRoom(Room room, User user) {
        RoomMember member = roomMemberRepository.findByRoomIdAndUserId(room.getId(), user.getId())
                .orElseThrow(() -> new RuntimeException("您不在该房间中"));
        
        // 删除成员记录
        roomMemberRepository.delete(member);
        
        // 更新房间人数
        int newCount = Math.max(0, room.getCurrentPlayers() - 1);
        room.setCurrentPlayers(newCount);
        
        // 如果房间没人了，结束房间
        if (newCount == 0) {
            room.setStatus(Room.RoomStatus.FINISHED);
        } else if (member.getIsHost()) {
            // 如果房主离开，转让房主给第一个成员
            List<RoomMember> members = roomMemberRepository.findByRoomId(room.getId());
            if (!members.isEmpty()) {
                RoomMember newHost = members.get(0);
                newHost.setIsHost(true);
                roomMemberRepository.save(newHost);
                room.setHost(newHost.getUser());
            }
        }
        
        roomRepository.save(room);
    }
    
    /**
     * 验证房间密码
     */
    public boolean validatePassword(Room room, String password) {
        if (!room.getHasPassword()) {
            return true;
        }
        return room.getPassword() != null && room.getPassword().equals(password);
    }
    
    /**
     * 检查用户是否在房间
     */
    public boolean isUserInRoom(Long roomId, Long userId) {
        return roomMemberRepository.existsByRoomIdAndUserId(roomId, userId);
    }
    
    /**
     * 获取房间成员列表
     */
    public List<RoomMember> getRoomMembers(Long roomId) {
        return roomMemberRepository.findByRoomId(roomId);
    }
    
    /**
     * 设置准备状态
     */
    @Transactional
    public void setReady(Long roomId, Long userId, boolean ready) {
        RoomMember member = roomMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new RuntimeException("您不在该房间中"));
        member.setIsReady(ready);
        roomMemberRepository.save(member);
    }
    
    /**
     * 开始游戏
     */
    @Transactional
    public void startGame(Room room) {
        room.setStatus(Room.RoomStatus.PLAYING);
        roomRepository.save(room);
    }
    
    /**
     * 结束游戏
     */
    @Transactional
    public void endGame(Room room) {
        room.setStatus(Room.RoomStatus.FINISHED);
        roomRepository.save(room);
    }
}
