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
    private final com.werewolf.repository.UserRepository userRepository;
    
    // AI 玩家名字池
    private static final String[] AI_NAMES = {
        "暗影猎手", "月光行者", "银狼", "血爪", "夜枭",
        "迷雾先知", "铁卫", "毒蛇", "孤狼", "守夜人",
        "幽灵", "黑鸦"
    };
    
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
     * 创建房间（自动退出其他等待中的房间）
     */
    @Transactional
    public Room createRoom(String roomName, User host, Integer maxPlayers, String password) {
        leaveAllWaitingRooms(host);
        
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
     * 用户加入房间（幂等：已在房间则直接返回；自动退出其他等待中的房间）
     */
    @Transactional
    public void joinRoom(Room room, User user) {
        if (roomMemberRepository.existsByRoomIdAndUserId(room.getId(), user.getId())) {
            return;
        }
        
        if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
            throw new RuntimeException("房间已满");
        }
        
        leaveAllWaitingRooms(user);
        
        room.setCurrentPlayers(room.getCurrentPlayers() + 1);
        roomRepository.save(room);
        
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
     * 退出用户所在的所有等待中的房间
     */
    @Transactional
    public void leaveAllWaitingRooms(User user) {
        List<RoomMember> memberships = roomMemberRepository.findByUserId(user.getId());
        for (RoomMember membership : memberships) {
            Room memberRoom = membership.getRoom();
            if (memberRoom.getStatus() == Room.RoomStatus.WAITING) {
                roomMemberRepository.delete(membership);
                
                int newCount = Math.max(0, memberRoom.getCurrentPlayers() - 1);
                memberRoom.setCurrentPlayers(newCount);
                
                if (newCount == 0) {
                    memberRoom.setStatus(Room.RoomStatus.FINISHED);
                } else if (membership.getIsHost()) {
                    List<RoomMember> remaining = roomMemberRepository.findByRoomId(memberRoom.getId());
                    if (!remaining.isEmpty()) {
                        RoomMember newHost = remaining.get(0);
                        newHost.setIsHost(true);
                        roomMemberRepository.save(newHost);
                        memberRoom.setHost(newHost.getUser());
                    }
                }
                
                roomRepository.save(memberRoom);
            }
        }
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
    
    /**
     * 添加 AI 玩家到房间
     */
    @Transactional
    public User addAiPlayer(Room room) {
        if (room.getStatus() != Room.RoomStatus.WAITING) {
            throw new RuntimeException("房间已开始游戏，无法添加人机");
        }
        if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
            throw new RuntimeException("房间已满，无法添加人机");
        }
        
        // 获取已在房间中的 AI 数量，用于取名
        List<RoomMember> members = roomMemberRepository.findByRoomId(room.getId());
        int aiCount = 0;
        for (RoomMember m : members) {
            if (m.getUser().getUsername().startsWith("[AI]")) {
                aiCount++;
            }
        }
        
        // 生成 AI 名字
        String aiName = AI_NAMES[aiCount % AI_NAMES.length];
        final String firstUsername = "[AI] " + aiName;
        
        // 查找或创建 AI 虚拟用户
        User aiUser = userRepository.findByUsername(firstUsername)
                .orElseGet(() -> {
                    User newAi = User.builder()
                            .username(firstUsername)
                            .password("AI_PLAYER_NO_LOGIN")
                            .email("ai_" + System.currentTimeMillis() + "@werewolf.bot")
                            .loginType(User.LoginType.APP)
                            .build();
                    return userRepository.save(newAi);
                });
        
        // 检查 AI 是否已在房间
        if (roomMemberRepository.existsByRoomIdAndUserId(room.getId(), aiUser.getId())) {
            // 换个名字重试
            final String retryUsername = "[AI] " + AI_NAMES[(aiCount + 1) % AI_NAMES.length] + (aiCount + 1);
            final int finalAiCount = aiCount;
            aiUser = userRepository.findByUsername(retryUsername)
                    .orElseGet(() -> {
                        User newAi = User.builder()
                                .username(retryUsername)
                                .password("AI_PLAYER_NO_LOGIN")
                                .email("ai_" + System.currentTimeMillis() + "_" + finalAiCount + "@werewolf.bot")
                                .loginType(User.LoginType.APP)
                                .build();
                        return userRepository.save(newAi);
                    });
        }
        
        // 添加到房间
        room.setCurrentPlayers(room.getCurrentPlayers() + 1);
        roomRepository.save(room);
        
        RoomMember member = RoomMember.builder()
                .room(room)
                .user(aiUser)
                .isHost(false)
                .isReady(true)  // AI 自动准备
                .build();
        roomMemberRepository.save(member);
        
        return aiUser;
    }
    
    /**
     * 移除房间中的一个 AI 玩家
     */
    @Transactional
    public void removeAiPlayer(Room room) {
        if (room.getStatus() != Room.RoomStatus.WAITING) {
            throw new RuntimeException("房间已开始游戏，无法移除人机");
        }
        
        List<RoomMember> members = roomMemberRepository.findByRoomId(room.getId());
        RoomMember aiMember = null;
        // 从后往前找，移除最后加入的 AI
        for (int i = members.size() - 1; i >= 0; i--) {
            if (members.get(i).getUser().getUsername().startsWith("[AI]")) {
                aiMember = members.get(i);
                break;
            }
        }
        
        if (aiMember == null) {
            throw new RuntimeException("房间中没有人机玩家");
        }
        
        roomMemberRepository.delete(aiMember);
        room.setCurrentPlayers(Math.max(0, room.getCurrentPlayers() - 1));
        roomRepository.save(room);
    }
}
