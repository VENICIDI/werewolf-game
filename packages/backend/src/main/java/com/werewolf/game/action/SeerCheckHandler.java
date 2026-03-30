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
        context.getWebSocketHandler().sendToUser(roomCode, player.getUser().getId(),
                new WebSocketMessage(WebSocketMessage.Type.SEER_RESULT, result));

        log.info("预言家查验 {}号 => {}", target.getSeatNumber(), isWerewolf ? "狼人" : "好人");
        return result;
    }
}
