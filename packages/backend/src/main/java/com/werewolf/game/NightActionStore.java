package com.werewolf.game;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 夜晚行动存储 - 内存中记录每局游戏每回合的夜间行动
 * key: gameId -> roundActions
 */
@Component
@Slf4j
public class NightActionStore {

    // gameId -> RoundActions
    private final Map<Long, RoundActions> store = new ConcurrentHashMap<>();

    /**
     * 获取或创建当前回合行动记录
     */
    public RoundActions getOrCreate(Long gameId) {
        return store.computeIfAbsent(gameId, k -> new RoundActions());
    }

    /**
     * 清除游戏记录
     */
    public void clear(Long gameId) {
        store.remove(gameId);
    }

    /**
     * 重置当前回合（进入新一夜时调用）
     */
    public void resetRound(Long gameId) {
        RoundActions actions = store.get(gameId);
        if (actions != null) {
            actions.reset();
        }
    }

    /**
     * 当前回合的行动记录
     */
    @Data
    public static class RoundActions {
        // 狼人击杀目标 (多个狼人投票取最终结果)
        private Long werewolfKillTarget;

        // 狼人投票: playerId -> targetId
        private final Map<Long, Long> werewolfVotes = new ConcurrentHashMap<>();

        // 预言家查验目标
        private Long seerCheckTarget;

        // 女巫救人目标 (null=不救)
        private Long witchSaveTarget;

        // 女巫毒人目标 (null=不毒)
        private Long witchPoisonTarget;

        // 女巫是否已使用解药 (整局游戏)
        private boolean witchSaveUsed = false;

        // 女巫是否已使用毒药 (整局游戏)
        private boolean witchPoisonUsed = false;

        // 守卫守护目标
        private Long guardProtectTarget;

        // 守卫上一回合守护目标 (不能连续守同一个人)
        private Long guardLastProtectTarget;

        // 已提交行动的角色集合
        private final Set<String> submittedActions = ConcurrentHashMap.newKeySet();

        /**
         * 记录狼人投票
         */
        public void addWerewolfVote(Long wolfId, Long targetId) {
            werewolfVotes.put(wolfId, targetId);
            // 统计票数取最多
            resolveWerewolfKill();
        }

        /**
         * 统计狼人投票结果
         */
        private void resolveWerewolfKill() {
            if (werewolfVotes.isEmpty()) return;

            Map<Long, Integer> voteCount = new HashMap<>();
            for (Long target : werewolfVotes.values()) {
                voteCount.merge(target, 1, Integer::sum);
            }

            // 取票数最多的目标
            werewolfKillTarget = voteCount.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }

        /**
         * 标记角色已提交行动
         */
        public void markActionSubmitted(String rolePhase) {
            submittedActions.add(rolePhase);
        }

        /**
         * 检查角色是否已提交行动
         */
        public boolean isActionSubmitted(String rolePhase) {
            return submittedActions.contains(rolePhase);
        }

        /**
         * 重置当前回合 (保留女巫药品状态和守卫上回合目标)
         */
        public void reset() {
            boolean saveUsed = this.witchSaveUsed;
            boolean poisonUsed = this.witchPoisonUsed;
            Long lastGuardTarget = this.guardProtectTarget;

            werewolfKillTarget = null;
            werewolfVotes.clear();
            seerCheckTarget = null;
            witchSaveTarget = null;
            witchPoisonTarget = null;
            guardProtectTarget = null;
            submittedActions.clear();

            // 保留跨回合状态
            this.witchSaveUsed = saveUsed;
            this.witchPoisonUsed = poisonUsed;
            this.guardLastProtectTarget = lastGuardTarget;
        }

        /**
         * 结算夜晚 — 返回死亡玩家ID列表
         */
        public List<Long> resolveNight() {
            List<Long> deadPlayers = new ArrayList<>();

            // 1. 狼人击杀
            Long killTarget = werewolfKillTarget;

            // 2. 守卫守护 — 如果守卫守护了被杀目标，则不死
            boolean isGuarded = killTarget != null && killTarget.equals(guardProtectTarget);

            // 3. 女巫救人 — 如果女巫救了被杀目标，则不死
            boolean isSaved = killTarget != null && killTarget.equals(witchSaveTarget);

            // 4. 守卫 + 女巫同守冲突：如果守卫和女巫同时保护同一个人，该玩家仍然死亡
            if (isGuarded && isSaved) {
                // 同守冲突，玩家死亡
                if (killTarget != null) {
                    deadPlayers.add(killTarget);
                }
            } else if (!isGuarded && !isSaved) {
                // 没人保护，玩家死亡
                if (killTarget != null) {
                    deadPlayers.add(killTarget);
                }
            }
            // else: 被守卫或女巫保护，存活

            // 5. 女巫毒人
            if (witchPoisonTarget != null) {
                if (!deadPlayers.contains(witchPoisonTarget)) {
                    deadPlayers.add(witchPoisonTarget);
                }
            }

            log.info("夜晚结算输入: killTarget={}, guardTarget={}, saveTarget={}, poisonTarget={}, 死亡名单={}",
                    killTarget, guardProtectTarget, witchSaveTarget, witchPoisonTarget, deadPlayers);

            return deadPlayers;
        }
    }
}
