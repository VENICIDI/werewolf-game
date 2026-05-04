package com.werewolf.game.action;

import com.werewolf.entity.Game;
import com.werewolf.entity.Player;
import com.werewolf.game.VoteManager;
import com.werewolf.websocket.WebSocketMessage;
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

        // 实时广播当前投票进度（voterId -> targetId），供前端展示"谁投了谁"
        broadcastVoteUpdate(context, session);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "投票成功");
        result.put("votedFor", targetId);
        result.put("allVoted", session.isAllVoted());
        return result;
    }

    /**
     * 广播当前投票进度(仅计数,不透露投票明细)
     *
     * ⚠️ 设计要点:投票阶段内不向前端发送 votesByVoter / voteDetails,
     *   避免玩家在 30s 投票期间就能看到"谁投了谁 / 当前票数"。
     *   投票明细与最终结果只在阶段结束后由 GameService.resolveVoting
     *   统一通过 VOTE_RESULT 广播。
     */
    private void broadcastVoteUpdate(ActionContext context, VoteManager.VoteSession session) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("votedCount", session.getVotedCount());
            data.put("eligibleCount", session.getEligibleCount());

            String roomCode = context.getGame().getRoom().getRoomCode();
            context.getWebSocketHandler().broadcastToRoom(roomCode,
                    new WebSocketMessage("VOTE_UPDATE", data));
        } catch (Exception e) {
            log.warn("广播 VOTE_UPDATE 失败: {}", e.getMessage());
        }
    }
}
