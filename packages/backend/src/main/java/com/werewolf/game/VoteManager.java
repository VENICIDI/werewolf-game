package com.werewolf.game;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 投票管理器 - 管理白天投票阶段
 */
@Component
@Slf4j
public class VoteManager {

    // gameId -> VoteSession
    private final Map<Long, VoteSession> voteSessions = new ConcurrentHashMap<>();

    /**
     * 开始新的投票会话
     */
    public VoteSession startVote(Long gameId, Set<Long> eligibleVoterIds) {
        VoteSession session = new VoteSession(eligibleVoterIds);
        voteSessions.put(gameId, session);
        return session;
    }

    /**
     * 获取投票会话
     */
    public VoteSession getVoteSession(Long gameId) {
        return voteSessions.get(gameId);
    }

    /**
     * 清除投票会话
     */
    public void clearVote(Long gameId) {
        voteSessions.remove(gameId);
    }

    /**
     * 投票会话
     */
    @Data
    public static class VoteSession {
        // 有投票权的玩家
        private final Set<Long> eligibleVoterIds;

        // 投票记录: voterId -> targetId (0 = 弃票)
        private final Map<Long, Long> votes = new ConcurrentHashMap<>();

        public VoteSession(Set<Long> eligibleVoterIds) {
            this.eligibleVoterIds = eligibleVoterIds;
        }

        /**
         * 提交投票
         */
        public boolean submitVote(Long voterId, Long targetId) {
            if (!eligibleVoterIds.contains(voterId)) {
                return false;
            }
            if (votes.containsKey(voterId)) {
                return false; // 已投过票
            }
            votes.put(voterId, targetId);
            return true;
        }

        /**
         * 是否所有人已投票
         */
        public boolean isAllVoted() {
            return votes.size() >= eligibleVoterIds.size();
        }

        /**
         * 获取投票结果
         * @return VoteResult 包含被票出的玩家或平票信息
         */
        public VoteResult resolve() {
            if (votes.isEmpty()) {
                return new VoteResult(null, true, Collections.emptyMap());
            }

            // 统计票数 (排除弃票 targetId=0)
            Map<Long, Integer> voteCount = new HashMap<>();
            for (Map.Entry<Long, Long> entry : votes.entrySet()) {
                Long targetId = entry.getValue();
                if (targetId != null && targetId > 0) {
                    voteCount.merge(targetId, 1, Integer::sum);
                }
            }

            if (voteCount.isEmpty()) {
                return new VoteResult(null, false, voteCount);
            }

            // 找最高票数
            int maxVotes = voteCount.values().stream().mapToInt(Integer::intValue).max().orElse(0);

            // 检查是否平票
            List<Long> topPlayers = voteCount.entrySet().stream()
                    .filter(e -> e.getValue() == maxVotes)
                    .map(Map.Entry::getKey)
                    .toList();

            if (topPlayers.size() > 1) {
                // 平票
                return new VoteResult(null, true, voteCount);
            }

            return new VoteResult(topPlayers.get(0), false, voteCount);
        }
    }

    /**
     * 投票结果
     */
    @Data
    public static class VoteResult {
        private final Long eliminatedPlayerId; // null表示无人被处决
        private final boolean isTie;           // 是否平票
        private final Map<Long, Integer> voteDetails; // 投票详情

        public VoteResult(Long eliminatedPlayerId, boolean isTie, Map<Long, Integer> voteDetails) {
            this.eliminatedPlayerId = eliminatedPlayerId;
            this.isTie = isTie;
            this.voteDetails = voteDetails;
        }
    }
}
