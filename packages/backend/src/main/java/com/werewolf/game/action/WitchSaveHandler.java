package com.werewolf.game.action;

import com.werewolf.entity.Game;
import com.werewolf.entity.Player;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class WitchSaveHandler implements GameActionHandler {

    @Override
    public String getAction() {
        return "save";
    }

    @Override
    public void validate(ActionContext context) {
        if (context.getPlayer().getRole() != Player.Role.WITCH) {
            throw new RuntimeException("只有女巫可以使用解药");
        }
        if (context.getGame().getCurrentPhase() != Game.GamePhase.WITCH) {
            throw new RuntimeException("当前不是女巫行动阶段");
        }
        if (context.getNightActions().isWitchSaveUsed()) {
            throw new RuntimeException("解药已用完");
        }
        if (context.getNightActions().getWerewolfKillTarget() == null) {
            throw new RuntimeException("今晚没有人被杀");
        }
        // ✨ FIX #8: 同一晚只能用一瓶药
        if (context.getNightActions().getWitchPoisonTarget() != null) {
            throw new RuntimeException("本晚已使用毒药，不能再用解药");
        }
    }

    @Override
    public Map<String, Object> execute(ActionContext context) {
        Long killTarget = context.getNightActions().getWerewolfKillTarget();

        context.getNightActions().setWitchSaveTarget(killTarget);
        context.getNightActions().setWitchSaveUsed(true);

        log.info("女巫 {}号 使用解药救 {}", context.getPlayer().getSeatNumber(), killTarget);

        // ✨ AI 女巫:私有事件推送,记住自己用了解药 + 救了谁
        Player witch = context.getPlayer();
        if (Boolean.TRUE.equals(witch.getIsAi()) && context.getAiPlayerBridge() != null) {
            Map<String, Object> eventData = new HashMap<>();
            // killTarget 是数据库 Player.id,需换算为座位号
            Player saved = killTarget != null
                    ? context.getPlayerRepository().findById(killTarget).orElse(null) : null;
            if (saved != null) {
                eventData.put("target_id", saved.getSeatNumber());
            }
            eventData.put("witch_seat", witch.getSeatNumber());
            context.getAiPlayerBridge().pushPrivateEventToAI(
                    context.getGame().getId(), witch, "WITCH_SAVE_USED", eventData);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("message", "已使用解药");
        return result;
    }
}
