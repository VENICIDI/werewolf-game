package com.werewolf.game.action;

import com.werewolf.entity.Game;
import com.werewolf.entity.Player;
import com.werewolf.websocket.WebSocketMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SeerCheckHandler implements GameActionHandler {

    @Override
    public String getAction() {
        return "check";
    }

    @Override
    public void validate(ActionContext context) {
        if (context.getPlayer().getRole() != Player.Role.SEER) {
            throw new RuntimeException("只有预言家可以执行查验");
        }
        if (context.getGame().getCurrentPhase() != Game.GamePhase.SEER) {
            throw new RuntimeException("当前不是预言家行动阶段");
        }
        if (context.getTargetId() == null) {
            throw new RuntimeException("必须选择查验目标");
        }
        context.getPlayerRepository().findById(context.getTargetId())
                .orElseThrow(() -> new RuntimeException("目标玩家不存在"));
    }

    @Override
    public Map<String, Object> execute(ActionContext context) {
        Player player = context.getPlayer();
        Long targetId = context.getTargetId();

        Player target = context.getPlayerRepository().findById(targetId)
                .orElseThrow(() -> new RuntimeException("目标玩家不存在"));

        context.getNightActions().setSeerCheckTarget(targetId);

        boolean isWerewolf = context.getRoleAssigner().isWerewolfCamp(target.getRole());

        Map<String, Object> result = new HashMap<>();
        result.put("targetId", targetId);
        result.put("targetSeat", target.getSeatNumber());
        result.put("isWerewolf", isWerewolf);
        result.put("message", String.format("%d号玩家是%s",
                target.getSeatNumber(), isWerewolf ? "狼人" : "好人"));

        String roomCode = context.getGame().getRoom().getRoomCode();

        // 真人预言家:通过 WebSocket 即时推送结果
        if (player.getUser() != null) {
            context.getWebSocketHandler().sendToUser(roomCode, player.getUser().getId(),
                    new WebSocketMessage(WebSocketMessage.Type.SEER_RESULT, result));
        }

        // ✨ AI 预言家:通过 SEER_CHECK_RESULT 事件推到 Python Agent 的记忆系统
        // 这样下一回合发言/投票时,该预言家的 Prompt 中就会包含 "1号:狼人" 等历史查验
        if (Boolean.TRUE.equals(player.getIsAi()) && context.getAiPlayerBridge() != null) {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("target_id", target.getSeatNumber());      // 用座位号(对齐 Python Agent)
            eventData.put("result", isWerewolf ? "WEREWOLF" : "GOOD");
            eventData.put("seer_seat", player.getSeatNumber());
            context.getAiPlayerBridge().pushPrivateEventToAI(
                    context.getGame().getId(), player, "SEER_CHECK_RESULT", eventData);
        }

        log.info("预言家 {}号 查验 {}号 => {}",
                player.getSeatNumber(), target.getSeatNumber(), isWerewolf ? "狼人" : "好人");
        return result;
    }
}
