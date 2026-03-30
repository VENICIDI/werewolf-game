package com.werewolf.game.action;

import com.werewolf.entity.Game;
import com.werewolf.entity.Player;
import com.werewolf.game.VoteManager;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class VoteHandler implements GameActionHandler {

    @Override
    public String getAction() {
        return "vote";
    }

    @Override
    public void validate(ActionContext context) {
        if (context.getGame().getCurrentPhase() != Game.GamePhase.VOTING) {
            throw new RuntimeException("当前不是投票阶段");
        }
        Player player = context.getPlayer();
        if (player.getStatus() != Player.PlayerStatus.ALIVE || !player.getCanVote()) {
            throw new RuntimeException("您没有投票权");
        }
        VoteManager.VoteSession session = context.getVoteManager()
                .getVoteSession(context.getGame().getId());
        if (session == null) {
            throw new RuntimeException("投票尚未开始");
        }
    }

    @Override
    public Map<String, Object> execute(ActionContext context) {
        Player voter = context.getPlayer();
        Long targetId = context.getTargetId();

        VoteManager.VoteSession session = context.getVoteManager()
                .getVoteSession(context.getGame().getId());

        boolean success = session.submitVote(voter.getId(), targetId);
        if (!success) {
            throw new RuntimeException("投票失败，可能已投过票");
        }

        log.info("{}号投票给 {}", voter.getSeatNumber(),
                targetId != null && targetId > 0 ? targetId + "号" : "弃票");

        Map<String, Object> result = new HashMap<>();
        result.put("message", "投票成功");
        result.put("votedFor", targetId);
        result.put("allVoted", session.isAllVoted());
        return result;
    }
}
