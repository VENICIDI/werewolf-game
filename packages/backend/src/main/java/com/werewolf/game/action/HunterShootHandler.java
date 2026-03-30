package com.werewolf.game.action;

import com.werewolf.entity.Player;
import com.werewolf.websocket.WebSocketMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class HunterShootHandler implements GameActionHandler {

    @Override
    public String getAction() {
        return "shoot";
    }

    @Override
    public void validate(ActionContext context) {
        Player hunter = context.getPlayer();
        if (hunter.getRole() != Player.Role.HUNTER) {
            throw new RuntimeException("您不是猎人");
        }
        if (hunter.getStatus() != Player.PlayerStatus.DEAD) {
            throw new RuntimeException("猎人存活时不能开枪");
        }
        if (context.getTargetId() == null) {
            throw new RuntimeException("必须选择开枪目标");
        }
        Player target = context.getPlayerRepository().findById(context.getTargetId())
                .orElseThrow(() -> new RuntimeException("目标不存在"));
        if (target.getStatus() != Player.PlayerStatus.ALIVE) {
            throw new RuntimeException("目标已死亡");
        }
    }

    @Override
    public Map<String, Object> execute(ActionContext context) {
        Player hunter = context.getPlayer();
        Long targetId = context.getTargetId();

        Player target = context.getPlayerRepository().findById(targetId)
                .orElseThrow(() -> new RuntimeException("目标不存在"));

        target.setStatus(Player.PlayerStatus.DEAD);
        target.setCanSpeak(false);
        target.setCanVote(false);
        context.getPlayerRepository().save(target);

        String roomCode = context.getGame().getRoom().getRoomCode();
        Map<String, Object> broadcastData = new HashMap<>();
        broadcastData.put("hunterSeat", hunter.getSeatNumber());
        broadcastData.put("targetSeat", target.getSeatNumber());
        broadcastData.put("message",
                hunter.getSeatNumber() + "号猎人开枪带走了" + target.getSeatNumber() + "号");

        context.getWebSocketHandler().broadcastToRoom(roomCode,
                new WebSocketMessage(WebSocketMessage.Type.HUNTER_SHOOT, broadcastData));

        log.info("猎人 {}号 开枪带走 {}号", hunter.getSeatNumber(), target.getSeatNumber());

        Map<String, Object> result = new HashMap<>();
        result.put("message",
                hunter.getSeatNumber() + "号猎人开枪带走了" + target.getSeatNumber() + "号");
        result.put("targetSeat", target.getSeatNumber());
        return result;
    }
}
