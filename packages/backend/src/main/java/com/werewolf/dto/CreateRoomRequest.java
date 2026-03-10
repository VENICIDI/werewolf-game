package com.werewolf.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRoomRequest {
    
    @NotBlank(message = "房间名称不能为空")
    @Size(max = 50, message = "房间名称最多50个字符")
    private String roomName;
    
    @Min(value = 6, message = "最少6人")
    @Max(value = 12, message = "最多12人")
    private Integer maxPlayers = 12;
    
    @Size(max = 20, message = "密码最多20个字符")
    private String password;
}
