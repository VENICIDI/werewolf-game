package com.werewolf.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 统一行动请求 DTO
 * 所有游戏行动（夜晚/投票/猎人等）共用此请求体，
 * 通过 action 字段路由到对应的命令实现。
 *
 * 支持的 action:
 *   kill    — 狼人击杀 (targetId=被杀玩家ID)
 *   check   — 预言家查验 (targetId=被查玩家ID)
 *   save    — 女巫救人 (targetId 可选)
 *   poison  — 女巫毒人 (targetId=被毒玩家ID)
 *   guard   — 守卫守护 (targetId=被守玩家ID)
 *   vote    — 白天投票 (targetId=投票目标ID, 0=弃票)
 *   shoot   — 猎人开枪 (targetId=被射玩家ID)
 *   skip    — 跳过行动 (targetId 可为null)
 */
@Data
public class GameActionRequest {

    @NotBlank(message = "行动类型不能为空")
    private String action;

    private Long targetId;
}
