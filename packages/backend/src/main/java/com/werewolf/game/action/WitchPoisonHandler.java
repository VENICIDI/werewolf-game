package com.werewolf.game.action;

import com.werewolf.entity.Game;
import com.werewolf.entity.Player;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class WitchPoisonHandler implements GameActionHandler {

    @Override
    public String getAction() {
        return "poison";
    }

    @Override
    public void validate(ActionContext context) {
        if (context.getPlayer().getRole() != Player.Role.WITCH) {
            throw new RuntimeException("只有女巫可以使用毒药");
        }
        if (context.getGame().getCurrentPhase() != Game.GamePhase.WITCH) {
            throw new RuntimeException("当前不是女巫行动阶段");
        }
        if (context.getNightActions().isWitchPoisonUsed()) {
            throw new RuntimeException("毒药已用完");
        }
        if (context.getTargetId() == null) {
            throw new RuntimeException("必须选择毒杀目标");
        }
        // ✨ FIX #8: 同一晚只能用一瓶药
        if (context.getNightActions().getWitchSaveTarget() != null) {
            throw new RuntimeException("本晚已使用解药，不能再用毒药");
        }
        Player target = context.getPlayerRepository().findById(context.getTargetId())
                .orElseThrow(() -> new RuntimeException("目标不存在"));
        if (target.getStatus() != Player.PlayerStatus.ALIVE) {
            throw new RuntimeException("目标已死亡");
        }
    }

    @Override
    public Map<String, Object> execute(ActionContext context) {
        Long targetId = context.getTargetId();
        Player target = context.getPlayerRepository().findById(targetId)
                .orElseThrow(() -> new RuntimeException("目标不存在"));

        context.getNightActions().setWitchPoisonTarget(targetId);
        context.getNightActions().setWitchPoisonUsed(true);

        log.info("女巫 {}号 使用毒药毒 {}号",
                context.getPlayer().getSeatNumber(), target.getSeatNumber());

        // ✨ AI 女巫:私有事件推送
        Player witch = context.getPlayer();
        if (Boolean.TRUE.equals(witch.getIsAi()) && context.getAiPlayerBridge() != null) {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("target_id", target.getSeatNumber());
            eventData.put("witch_seat", witch.getSeatNumber());
            context.getAiPlayerBridge().pushPrivateEventToAI(
                    context.getGame().getId(), witch, "WITCH_POISON_USED", eventData);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("message", "已使用毒药");
        return result;
    }
}
