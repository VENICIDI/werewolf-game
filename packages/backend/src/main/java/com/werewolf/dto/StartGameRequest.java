package com.werewolf.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StartGameRequest {

    @NotBlank(message = "游戏模式不能为空")
    private String gameModeId; // 游戏模式ID, 如 "standard_12"
}
