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
    }

    @Override
    public Map<String, Object> execute(ActionContext context) {
        Long killTarget = context.getNightActions().getWerewolfKillTarget();

        context.getNightActions().setWitchSaveTarget(killTarget);
        context.getNightActions().setWitchSaveUsed(true);

        log.info("女巫使用解药");

        Map<String, Object> result = new HashMap<>();
        result.put("message", "已使用解药");
        return result;
    }
}
