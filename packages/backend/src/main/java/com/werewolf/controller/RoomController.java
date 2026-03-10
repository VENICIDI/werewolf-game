package com.werewolf.controller;

import com.werewolf.dto.*;
import com.werewolf.entity.Room;
import com.werewolf.entity.RoomMember;
import com.werewolf.entity.User;
import com.werewolf.service.RoomService;
import com.werewolf.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RoomController {
    
    private final RoomService roomService;
    private final UserService userService;
    
    /**
     * 获取房间列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<?>> getRoomList(
            @RequestParam(required = false) String status) {
        
        List<Room> rooms;
        if (status == null || status.isEmpty()) {
            rooms = roomService.getWaitingRooms();
        } else {
            rooms = roomService.getWaitingRooms(); // 默认只返回等待中的房间
        }
        
        List<RoomResponse> responseList = rooms.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(responseList));
    }
    
    /**
     * 创建房间
     */
    @PostMapping
    public ResponseEntity<ApiResponse<?>> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            @RequestAttribute("userId") Long userId) {
        
        try {
            User user = userService.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            Room room = roomService.createRoom(
                    request.getRoomName(),
                    user,
                    request.getMaxPlayers(),
                    request.getPassword()
            );
            
            return ResponseEntity.ok(ApiResponse.success("创建成功", convertToResponse(room)));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest(e.getMessage()));
        }
    }
    
    /**
     * 获取房间详情
     */
    @GetMapping("/{roomCode}")
    public ResponseEntity<ApiResponse<?>> getRoomDetail(@PathVariable String roomCode) {
        try {
            Room room = roomService.findByRoomCode(roomCode)
                    .orElseThrow(() -> new RuntimeException("房间不存在"));
            
            RoomResponse response = convertToResponseWithMembers(room);
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest(e.getMessage()));
        }
    }
    
    /**
     * 加入房间
     */
    @PostMapping("/{roomCode}/join")
    public ResponseEntity<ApiResponse<?>> joinRoom(
            @PathVariable String roomCode,
            @RequestBody JoinRoomRequest request,
            @RequestAttribute("userId") Long userId) {
        
        try {
            Room room = roomService.findByRoomCode(roomCode)
                    .orElseThrow(() -> new RuntimeException("房间不存在"));
            
            // 检查房间状态
            if (room.getStatus() != Room.RoomStatus.WAITING) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.badRequest("房间已开始游戏"));
            }
            
            // 验证密码
            if (!roomService.validatePassword(room, request.getPassword())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.badRequest("房间密码错误"));
            }
            
            User user = userService.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            roomService.joinRoom(room, user);
            
            return ResponseEntity.ok(ApiResponse.success("加入成功", convertToResponse(room)));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest(e.getMessage()));
        }
    }
    
    /**
     * 离开房间
     */
    @PostMapping("/{roomCode}/leave")
    public ResponseEntity<ApiResponse<?>> leaveRoom(
            @PathVariable String roomCode,
            @RequestAttribute("userId") Long userId) {
        
        try {
            Room room = roomService.findByRoomCode(roomCode)
                    .orElseThrow(() -> new RuntimeException("房间不存在"));
            
            User user = userService.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            roomService.leaveRoom(room, user);
            
            return ResponseEntity.ok(ApiResponse.success("离开成功", null));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest(e.getMessage()));
        }
    }
    
    /**
     * 设置准备状态
     */
    @PostMapping("/{roomCode}/ready")
    public ResponseEntity<ApiResponse<?>> setReady(
            @PathVariable String roomCode,
            @RequestParam boolean ready,
            @RequestAttribute("userId") Long userId) {
        
        try {
            Room room = roomService.findByRoomCode(roomCode)
                    .orElseThrow(() -> new RuntimeException("房间不存在"));
            
            roomService.setReady(room.getId(), userId, ready);
            
            return ResponseEntity.ok(ApiResponse.success(ready ? "已准备" : "已取消准备", null));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest(e.getMessage()));
        }
    }
    
    /**
     * 转换为响应对象 (简化版)
     */
    private RoomResponse convertToResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .roomCode(room.getRoomCode())
                .roomName(room.getRoomName())
                .hostId(room.getHost().getId())
                .hostName(room.getHost().getUsername())
                .maxPlayers(room.getMaxPlayers())
                .currentPlayers(room.getCurrentPlayers())
                .status(room.getStatus())
                .hasPassword(room.getHasPassword())
                .createdAt(room.getCreatedAt())
                .build();
    }
    
    /**
     * 转换为响应对象 (含成员列表)
     */
    private RoomResponse convertToResponseWithMembers(Room room) {
        List<RoomMember> members = roomService.getRoomMembers(room.getId());
        
        List<RoomResponse.PlayerInfo> playerInfos = members.stream()
                .map(m -> RoomResponse.PlayerInfo.builder()
                        .userId(m.getUser().getId())
                        .username(m.getUser().getUsername())
                        .avatarUrl(m.getUser().getAvatarUrl())
                        .isHost(m.getIsHost())
                        .build())
                .collect(Collectors.toList());
        
        return RoomResponse.builder()
                .id(room.getId())
                .roomCode(room.getRoomCode())
                .roomName(room.getRoomName())
                .hostId(room.getHost().getId())
                .hostName(room.getHost().getUsername())
                .maxPlayers(room.getMaxPlayers())
                .currentPlayers(room.getCurrentPlayers())
                .status(room.getStatus())
                .hasPassword(room.getHasPassword())
                .createdAt(room.getCreatedAt())
                .players(playerInfos)
                .build();
    }
}
