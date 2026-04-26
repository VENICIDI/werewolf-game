package com.werewolf.game.action;

import com.werewolf.entity.Game;
import com.werewolf.entity.Player;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class GuardProtectHandler implements GameActionHandler {

    @Override
    public String getAction() {
        return "guard";
    }

    @Override
    public void validate(ActionContext context) {
        if (context.getPlayer().getRole() != Player.Role.GUARD) {
            throw new RuntimeException("只有守卫可以执行守护");
        }
        if (context.getGame().getCurrentPhase() != Game.GamePhase.GUARD) {
            throw new RuntimeException("当前不是守卫行动阶段");
        }
        if (context.getTargetId() == null) {
            throw new RuntimeException("必须选择守护目标");
        }
        if (context.getTargetId().equals(context.getNightActions().getGuardLastProtectTarget())) {
            throw new RuntimeException("不能连续两晚守护同一个人");
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

        context.getNightActions().setGuardProtectTarget(targetId);

        log.info("守卫 {}号 守护 {}号",
                context.getPlayer().getSeatNumber(), target.getSeatNumber());

        // ✨ AI 守卫:私有事件推送
        Player guard = context.getPlayer();
        if (Boolean.TRUE.equals(guard.getIsAi()) && context.getAiPlayerBridge() != null) {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("target_id", target.getSeatNumber());
            eventData.put("guard_seat", guard.getSeatNumber());
            context.getAiPlayerBridge().pushPrivateEventToAI(
                    context.getGame().getId(), guard, "GUARD_PROTECT_USED", eventData);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("message", "守护目标已选定");
        return result;
    }
}
