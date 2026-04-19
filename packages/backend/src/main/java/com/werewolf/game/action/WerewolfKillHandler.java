package com.werewolf.game.action;

import com.werewolf.entity.Game;
import com.werewolf.entity.Player;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class WerewolfKillHandler implements GameActionHandler {

    @Override
    public String getAction() {
        return "kill";
    }

    @Override
    public void validate(ActionContext context) {
        if (context.getPlayer().getRole() != Player.Role.WEREWOLF) {
            throw new RuntimeException("只有狼人可以执行击杀");
        }
        if (context.getGame().getCurrentPhase() != Game.GamePhase.WEREWOLF) {
            throw new RuntimeException("当前不是狼人行动阶段");
        }
        if (context.getTargetId() == null) {
            throw new RuntimeException("必须选择击杀目标");
        }
        Player target = context.getPlayerRepository().findById(context.getTargetId())
                .orElseThrow(() -> new RuntimeException("目标玩家不存在"));
        if (target.getStatus() != Player.PlayerStatus.ALIVE) {
            throw new RuntimeException("目标已死亡");
        }
        // 不能选自己
        if (target.getId().equals(context.getPlayer().getId())) {
            throw new RuntimeException("不能击杀自己");
        }
        // 不能选狼人队友
        if (target.getRole() == Player.Role.WEREWOLF) {
            throw new RuntimeException("不能击杀狼人队友");
        }
    }

    @Override
    public Map<String, Object> execute(ActionContext context) {
        Player player = context.getPlayer();
        Long targetId = context.getTargetId();

        context.getNightActions().addWerewolfVote(player.getId(), targetId);

        Player target = context.getPlayerRepository().findById(targetId).orElse(null);
        log.info("狼人 {}号 选择击杀 {}号", player.getSeatNumber(),
                target != null ? target.getSeatNumber() : targetId);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "击杀目标已选定");
        return result;
    }
}
