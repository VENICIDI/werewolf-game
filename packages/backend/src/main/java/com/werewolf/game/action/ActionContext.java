package com.werewolf.game.action;

import com.werewolf.entity.Game;
import com.werewolf.entity.Player;
import com.werewolf.game.NightActionStore;
import com.werewolf.game.RoleAssigner;
import com.werewolf.game.VoteManager;
import com.werewolf.repository.PlayerRepository;
import com.werewolf.websocket.RoomWebSocketHandler;
import lombok.Builder;
import lombok.Data;

/**
 * 行动执行上下文 — 封装行动执行所需的全部信息
 */
@Data
@Builder
public class ActionContext {

    // ===== 请求参数 =====
    /** 当前游戏 */
    private Game game;
    /** 发起行动的玩家 */
    private Player player;
    /** 目标玩家ID (可为 null) */
    private Long targetId;

    // ===== 服务依赖 =====
    /** 夜间行动存储 */
    private NightActionStore.RoundActions nightActions;
    /** 投票管理器 */
    private VoteManager voteManager;
    /** 角色分配器（用于阵营查询）*/
    private RoleAssigner roleAssigner;
    /** 玩家仓库 */
    private PlayerRepository playerRepository;
    /** WebSocket 处理器 */
    private RoomWebSocketHandler webSocketHandler;
}
