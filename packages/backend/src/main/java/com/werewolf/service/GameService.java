package com.werewolf.service;

import com.werewolf.entity.*;
import com.werewolf.game.*;
import com.werewolf.game.action.ActionContext;
import com.werewolf.game.action.ActionDispatcher;
import com.werewolf.repository.*;
import com.werewolf.websocket.RoomWebSocketHandler;
import com.werewolf.websocket.WebSocketMessage;
import com.werewolf.logging.GameLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 游戏服务 - 通过命令模式统一分发所有游戏行动
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final GameLogRepository gameLogRepository;

    private final RoleAssigner roleAssigner;
    private final PhaseScheduler phaseScheduler;
    private final NightActionStore nightActionStore;
    private final VoteManager voteManager;
    private final ActionDispatcher actionDispatcher;
    private final RoomWebSocketHandler webSocketHandler;

    // 惰性获取 DiscussionManager,避免构造器循环依赖(DiscussionManager 也会反向引用 GameService)
    @Autowired
    private ApplicationContext applicationContext;
    private volatile DiscussionManager discussionManagerCache;
    private volatile com.werewolf.ai.AIPlayerBridge aiPlayerBridgeCache;

    private DiscussionManager getDiscussionManager() {
        if (discussionManagerCache == null) {
            discussionManagerCache = applicationContext.getBean(DiscussionManager.class);
        }
        return discussionManagerCache;
    }

    /** 惰性获取 AIPlayerBridge — 用于 ActionContext 注入,避免循环依赖 */
    private com.werewolf.ai.AIPlayerBridge getAIPlayerBridge() {
        if (aiPlayerBridgeCache == null) {
            aiPlayerBridgeCache = applicationContext.getBean(com.werewolf.ai.AIPlayerBridge.class);
        }
        return aiPlayerBridgeCache;
    }

    // ✨ FIX #7: 猎人开枪一次性限制
    private final Set<Long> hunterShot = ConcurrentHashMap.newKeySet();

    // ✨ 投票结算幂等锁已内聚到 VoteSession.resolved (AtomicBoolean),此处不再维护双 Set

    // ========================= 统一行动入口 =========================

    /**
     * ✨ 构建提交锁 key — 统一格式 "round_phase_playerId"
     *
     * 设计:
     *   - 带 round 维度,跨回合天然失效,与前端 lock key (`${phase}:${round}`) 语义一致
     *   - 新一夜 nightActions.reset() 已清 submittedActions,带 round 是双重防御
     */
    private String buildPhaseKey(Game game, Player player) {
        return game.getCurrentRound() + "_" + game.getCurrentPhase().name() + "_" + player.getId();
    }

    /**
     * ✨ 构建快照场景下的 phaseKey(玩家维度) — 与 buildPhaseKey 同源,仅参数不同
     */
    private String buildPhaseKey(int round, Game.GamePhase phase, Long playerId) {
        return round + "_" + phase.name() + "_" + playerId;
    }

    /**
     * 统一行动入口 — 所有游戏行动（夜晚/投票/猎人等）通过此方法分发
     *
     * @param gameId   游戏ID
     * @param userId   用户ID
     * @param action   行动类型 (kill/check/save/poison/guard/vote/shoot/skip)
     * @param targetId 目标玩家ID (部分行动可为null)
     * @return 执行结果
     */
    @Transactional
    public Map<String, Object> executeAction(Long gameId, Long userId, String action, Long targetId) {
        Game game = getGameStatus(gameId);
        Player player = playerRepository.findByGameIdAndUserId(gameId, userId)
                .orElseThrow(() -> new RuntimeException("您不在此游戏中"));

        // 死亡校验 — shoot 命令允许死亡玩家（猎人）执行
        if (!"shoot".equals(action) && player.getStatus() != Player.PlayerStatus.ALIVE) {
            throw new RuntimeException("您已死亡，无法行动");
        }

        // 重复提交校验（skip/vote/shoot 不受夜间重复提交限制）
        NightActionStore.RoundActions nightActions = nightActionStore.getOrCreate(gameId);
        String phaseKey = buildPhaseKey(game, player);
        boolean isNightAction = Set.of("kill", "check", "save", "poison", "guard").contains(action);
        // ✨ 夜间角色阶段的 skip 也应标记为已提交，用于"全员提交即推进"判定
        boolean isNightPhaseSkip = "skip".equals(action) && isNightRoleActionPhase(game.getCurrentPhase())
                && isRolePhaseActor(player, game.getCurrentPhase());
        if ((isNightAction || isNightPhaseSkip) && nightActions.isActionSubmitted(phaseKey)) {
            throw new RuntimeException("您已提交过行动");
        }

        // ✨ FIX #7: 猎人开枪一次性校验
        if ("shoot".equals(action) && !hunterShot.add(player.getId())) {
            throw new RuntimeException("您已经开过枪了");
        }

        // 构建行动上下文
        ActionContext context = ActionContext.builder()
                .game(game)
                .player(player)
                .targetId(targetId)
                .nightActions(nightActions)
                .voteManager(voteManager)
                .roleAssigner(roleAssigner)
                .playerRepository(playerRepository)
                .webSocketHandler(webSocketHandler)
                .aiPlayerBridge(getAIPlayerBridge())
                .build();

        // 通过行动分发器执行：validate + execute
        Map<String, Object> result = actionDispatcher.dispatch(action, context);

        // 夜间行动标记已提交（含夜间阶段的 skip）
        if (isNightAction || isNightPhaseSkip) {
            nightActions.markActionSubmitted(phaseKey);
        }

        // 记录日志
        String logData = targetId != null ? "{\"targetId\":" + targetId + "}" : null;
        saveLog(gameId, game.getCurrentRound(), game.getCurrentPhase().name(),
                player.getId(), action.toUpperCase(), logData);

        // WebSocket 确认（仅给操作者本人，check 除外因已发 SEER_RESULT）
        if (!"check".equals(action)) {
            String roomCode = game.getRoom().getRoomCode();
            Map<String, Object> confirmData = new HashMap<>(result != null ? result : Map.of());
            confirmData.put("action", action);
            confirmData.put("phase", game.getCurrentPhase().name());
            confirmData.put("round", game.getCurrentRound());
            WebSocketMessage confirmMsg = new WebSocketMessage(
                    WebSocketMessage.Type.ACTION_CONFIRM, confirmData);
            webSocketHandler.sendToUser(roomCode, userId, confirmMsg);
        }

        // 投票完成后自动结算(幂等:通过 VoteSession.tryMarkResolved 抢占)
        if ("vote".equals(action)) {
            VoteManager.VoteSession session = voteManager.getVoteSession(gameId);
            if (session != null && session.isAllVoted() && session.tryMarkResolved()) {
                resolveVoting(gameId);
            }
        }

        // 猎人开枪后检查胜负
        if ("shoot".equals(action)) {
            checkWinCondition(gameId);
        }

        // ✨ 全员提交即推进 — 在事务提交后执行,避免事务回滚时已广播/已推进阶段
        advanceAfterCommit(gameId);

        return result;
    }
    
    /**
     * AI 玩家专用行动入口 - 直接使用 playerId 而非 userId
     * 
     * @param gameId   游戏ID
     * @param playerId 玩家ID
     * @param action   行动类型
     * @param targetId 目标玩家ID
     * @return 执行结果
     */
    @Transactional
    public Map<String, Object> executeAIAction(Long gameId, Long playerId, String action, Long targetId) {
        Game game = getGameStatus(gameId);
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("玩家不存在"));
        
        // 验证是 AI 玩家
        if (!Boolean.TRUE.equals(player.getIsAi())) {
            throw new RuntimeException("该方法仅供 AI 玩家使用");
        }
        
        // 验证属于当前游戏
        if (!player.getGame().getId().equals(gameId)) {
            throw new RuntimeException("玩家不属于此游戏");
        }
        
        // 死亡校验
        if (!"shoot".equals(action) && player.getStatus() != Player.PlayerStatus.ALIVE) {
            log.warn("AI 玩家 {} 已死亡，跳过行动", playerId);
            return Map.of("message", "玩家已死亡");
        }

        // 重复提交校验
        NightActionStore.RoundActions nightActions = nightActionStore.getOrCreate(gameId);
        String phaseKey = buildPhaseKey(game, player);
        boolean isNightAction = Set.of("kill", "check", "save", "poison", "guard").contains(action);
        boolean isNightPhaseSkip = "skip".equals(action) && isNightRoleActionPhase(game.getCurrentPhase())
                && isRolePhaseActor(player, game.getCurrentPhase());
        if ((isNightAction || isNightPhaseSkip) && nightActions.isActionSubmitted(phaseKey)) {
            log.warn("AI 玩家 {} 已提交过行动，跳过", playerId);
            return Map.of("message", "已提交过行动");
        }

        // 构建行动上下文
        ActionContext context = ActionContext.builder()
                .game(game)
                .player(player)
                .targetId(targetId)
                .nightActions(nightActions)
                .voteManager(voteManager)
                .roleAssigner(roleAssigner)
                .playerRepository(playerRepository)
                .webSocketHandler(webSocketHandler)
                .aiPlayerBridge(getAIPlayerBridge())
                .build();

        // 执行行动
        Map<String, Object> result = actionDispatcher.dispatch(action, context);

        // 夜间行动标记已提交（含夜间阶段的 skip）
        if (isNightAction || isNightPhaseSkip) {
            nightActions.markActionSubmitted(phaseKey);
        }

        // 记录日志
        String logData = targetId != null ? "{\"targetId\":" + targetId + "}" : null;
        saveLog(gameId, game.getCurrentRound(), game.getCurrentPhase().name(),
                player.getId(), action.toUpperCase(), logData);

        // ⚠️ AI 不发送 WebSocket 确认（因为没有 userId）
        log.info("AI 玩家 {} ({}号) 执行行动: {}, 目标: {}", 
                player.getAiName(), player.getSeatNumber(), action, targetId);
        GameLogger.room(game.getRoom().getRoomCode(), gameId,
                "AI {}号({}) 执行: action={}, target={}",
                player.getSeatNumber(), player.getRole(), action, targetId);

        // 投票完成后自动结算(幂等:通过 VoteSession.tryMarkResolved 抢占)
        if ("vote".equals(action)) {
            VoteManager.VoteSession session = voteManager.getVoteSession(gameId);
            if (session != null && session.isAllVoted() && session.tryMarkResolved()) {
                resolveVoting(gameId);
            }
        }

        // 猎人开枪后检查胜负
        if ("shoot".equals(action)) {
            checkWinCondition(gameId);
        }

        // ✨ 全员提交即推进 — 在事务提交后执行
        advanceAfterCommit(gameId);

        return result;
    }

    // ========================= 游戏启动 =========================

    @Transactional
    public Game startGame(Long roomId, String gameModeId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("房间不存在"));

        if (room.getStatus() != Room.RoomStatus.WAITING) {
            throw new RuntimeException("房间不在等待状态");
        }
        if (room.getCurrentPlayers() < 6) {
            throw new RuntimeException("人数不足，至少需要6人");
        }

        Game game = Game.builder()
                .room(room)
                .status(Game.GameStatus.PREPARING)
                .currentRound(0)
                .currentPhase(Game.GamePhase.NONE)
                .winner(Game.Winner.NONE)
                .createdAt(LocalDateTime.now())
                .build();
        game = gameRepository.save(game);

        List<Player> players = createPlayers(game, room.getId());

        Map<Long, Player.Role> roleAssignments = roleAssigner.assignRoles(gameModeId, players);
        for (Player player : players) {
            player.setRole(roleAssignments.get(player.getId()));
            playerRepository.save(player);
        }

        game.setStatus(Game.GameStatus.RUNNING);
        game.setCurrentRound(1);
        game.setCurrentPhase(Game.GamePhase.NIGHT_START);
        game.setStartedAt(LocalDateTime.now());
        gameRepository.save(game);

        room.setStatus(Room.RoomStatus.PLAYING);
        roomRepository.save(room);

        nightActionStore.getOrCreate(game.getId());
        broadcastGameStart(room.getRoomCode(), game.getId(), players);
        saveLog(game.getId(), 0, "GAME_START", null, "START", null);
        phaseScheduler.startGame(game.getId(), gameModeId, players);

        log.info("游戏开始 - ID: {}, 房间: {}, 模式: {}, 玩家数: {}",
                game.getId(), room.getRoomCode(), gameModeId, players.size());
        GameLogger.room(room.getRoomCode(), game.getId(),
                "=== 游戏开始 === 模式: {}, 玩家数: {}, 角色分配: {}",
                gameModeId, players.size(),
                players.stream().map(p -> p.getSeatNumber() + "号(" 
                    + (Boolean.TRUE.equals(p.getIsAi()) ? "AI" : "人类") + ")=" + p.getRole())
                    .collect(Collectors.joining(", ")));
        return game;
    }

    private List<Player> createPlayers(Game game, Long roomId) {
        List<RoomMember> members = new ArrayList<>(roomMemberRepository.findByRoomId(roomId));
        // 随机打乱座位顺序，避免座位号与房间加入顺序一致
        Collections.shuffle(members);
        List<Player> players = new ArrayList<>();

        int seatNumber = 1;
        for (RoomMember member : members) {
            boolean isAi = member.getUser().getUsername().startsWith("[AI]");
            Player player = Player.builder()
                    .game(game)
                    .user(member.getUser())
                    .isAi(isAi)
                    .aiName(isAi ? member.getUser().getUsername() : null)
                    .seatNumber(seatNumber++)
                    .role(Player.Role.UNKNOWN)
                    .status(Player.PlayerStatus.ALIVE)
                    .isCaptain(false)
                    .canSpeak(true)
                    .canVote(true)
                    .build();
            player = playerRepository.save(player);
            players.add(player);
        }

        log.info("创建 {} 个玩家 (其中 AI: {})", players.size(),
                players.stream().filter(p -> Boolean.TRUE.equals(p.getIsAi())).count());
        return players;
    }

    private void broadcastGameStart(String roomCode, Long gameId, List<Player> players) {
        List<Map<String, Object>> publicPlayerInfos = players.stream().map(p -> {
            Map<String, Object> info = new HashMap<>();
            info.put("playerId", p.getId());
            info.put("userId", p.getUser() != null ? p.getUser().getId() : null);
            info.put("username", p.getUser() != null ? p.getUser().getUsername() : p.getAiName());
            info.put("seatNumber", p.getSeatNumber());
            info.put("avatarUrl", p.getUser() != null ? p.getUser().getAvatarUrl() : null);
            info.put("isAi", Boolean.TRUE.equals(p.getIsAi()));
            return info;
        }).collect(Collectors.toList());

        Map<String, Object> gameStartData = new HashMap<>();
        gameStartData.put("gameId", gameId);
        gameStartData.put("players", publicPlayerInfos);

        webSocketHandler.broadcastToRoom(roomCode,
                new WebSocketMessage(WebSocketMessage.Type.GAME_START, gameStartData));

        // 预先收集狼人 ID 列表（用于发送队友信息）
        List<Long> werewolfPlayerIds = players.stream()
                .filter(p -> p.getRole() == Player.Role.WEREWOLF)
                .map(Player::getId)
                .collect(Collectors.toList());

        for (Player player : players) {
            if (player.getUser() != null) {
                Map<String, Object> roleData = new HashMap<>();
                roleData.put("playerId", player.getId());
                roleData.put("role", player.getRole().name());
                roleData.put("seatNumber", player.getSeatNumber());

                // 狼人额外发送队友列表
                if (player.getRole() == Player.Role.WEREWOLF) {
                    List<Long> teammates = werewolfPlayerIds.stream()
                            .filter(id -> !id.equals(player.getId()))
                            .collect(Collectors.toList());
                    roleData.put("teammates", teammates);
                }

                webSocketHandler.sendToUser(roomCode, player.getUser().getId(),
                        new WebSocketMessage(WebSocketMessage.Type.ROLE_ASSIGN, roleData));
            }
        }
    }

    // ========================= 夜晚结算 =========================

    @Transactional
    public List<Player> resolveNight(Long gameId) {
        Game game = getGameStatus(gameId);
        NightActionStore.RoundActions actions = nightActionStore.getOrCreate(gameId);

        List<Long> deadPlayerIds = actions.resolveNight();
        List<Player> deadPlayers = new ArrayList<>();

        for (Long playerId : deadPlayerIds) {
            Player player = playerRepository.findById(playerId).orElse(null);
            if (player != null && player.getStatus() == Player.PlayerStatus.ALIVE) {
                player.setStatus(Player.PlayerStatus.DEAD);
                player.setCanSpeak(false);
                player.setCanVote(false);
                playerRepository.save(player);
                deadPlayers.add(player);

                saveLog(gameId, game.getCurrentRound(), "NIGHT_DEATH",
                        playerId, "DEATH", null);
            }
        }

        broadcastDeathAnnounce(game, deadPlayers);

        for (Player dead : deadPlayers) {
            if (dead.getRole() == Player.Role.HUNTER) {
                boolean poisoned = dead.getId().equals(actions.getWitchPoisonTarget());
                if (!poisoned) {
                    notifyHunterCanShoot(game, dead);
                }
            }
        }

        log.info("夜晚结算完成 - 游戏: {}, 死亡: {} 人", gameId, deadPlayers.size());
        GameLogger.room(game.getRoom().getRoomCode(), gameId,
                "夜晚结算: 死亡 {} 人 [{}]", deadPlayers.size(),
                deadPlayers.stream().map(p -> p.getSeatNumber() + "号(" + p.getRole() + ")")
                    .collect(Collectors.joining(", ")));
        return deadPlayers;
    }

    private void broadcastDeathAnnounce(Game game, List<Player> deadPlayers) {
        String roomCode = game.getRoom().getRoomCode();

        List<Map<String, Object>> deathInfos = deadPlayers.stream().map(p -> {
            Map<String, Object> info = new HashMap<>();
            info.put("playerId", p.getId());
            info.put("seatNumber", p.getSeatNumber());
            info.put("username", p.getUser() != null ? p.getUser().getUsername() : p.getAiName());
            return info;
        }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("deaths", deathInfos);
        data.put("round", game.getCurrentRound());
        data.put("message", deadPlayers.isEmpty() ? "昨晚是平安夜" :
                deadPlayers.stream()
                        .map(p -> p.getSeatNumber() + "号")
                        .collect(Collectors.joining("、")) + "玩家死亡");

        webSocketHandler.broadcastToRoom(roomCode,
                new WebSocketMessage(WebSocketMessage.Type.DEATH_ANNOUNCE, data));

        // ✨ 公共事件:逐个玩家死亡推送给所有 AI 的记忆系统
        // 用座位号(对齐 Python Agent 的 player_id),按"PLAYER_DIED"事件类型分发
        try {
            for (Player dead : deadPlayers) {
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("player_id", dead.getSeatNumber());
                eventData.put("cause", "killed");  // 当前未细分被杀/被毒,后续可扩展
                getAIPlayerBridge().pushPublicEventToAllAIs(
                        game.getId(), "PLAYER_DIED", eventData);
            }
        } catch (Exception e) {
            log.warn("推送死亡事件给 AI 失败 - gameId={}", game.getId(), e);
        }
    }

    private void notifyHunterCanShoot(Game game, Player hunter) {
        // ✨ FIX #7: 猎人只能开一次枪
        if (!hunterShot.add(hunter.getId())) {
            log.warn("猎人 {}号 已经开过枪了", hunter.getSeatNumber());
            return;
        }

        if (Boolean.TRUE.equals(hunter.getIsAi())) {
            // ✨ FIX #5: AI 猎人自动开枪（随机选一个存活的非狼人）
            List<Player> targets = playerRepository.findByGameIdAndStatus(
                    game.getId(), Player.PlayerStatus.ALIVE).stream()
                    .filter(p -> !p.getId().equals(hunter.getId()))
                    .toList();
            if (!targets.isEmpty()) {
                Player target = targets.get(new java.util.Random().nextInt(targets.size()));
                try {
                    executeAIAction(game.getId(), hunter.getId(), "shoot", target.getId());
                    log.info("AI 猎人 {}号 自动开枪射杀 {}号", hunter.getSeatNumber(), target.getSeatNumber());
                } catch (Exception e) {
                    log.error("AI 猎人开枪异常", e);
                }
            }
        } else if (hunter.getUser() != null) {
            // 真人猎人：通知前端
            Map<String, Object> data = new HashMap<>();
            data.put("message", "你已死亡，可以选择开枪带走一个人");
            data.put("canShoot", true);

            webSocketHandler.sendToUser(game.getRoom().getRoomCode(), hunter.getUser().getId(),
                    new WebSocketMessage(WebSocketMessage.Type.HUNTER_SHOOT, data));
        }
    }

    /**
     * ✨ FIX #3: 女巫阶段通知被杀者信息 + 自动跳过无意义场景
     *
     * 自动跳过规则:女巫"完全无可行动选项"时,直接预提交 skip 推进阶段:
     *   - 解药已用完 (无 save 选项)
     *   - 且当前没人被杀 (即使有解药也没目标可救) 或 解药已用完
     *   - 且毒药已用完 (无 poison 选项)
     * 这避免了平安夜或女巫两药都用完时,她必须手动点"不使用"才能推进。
     */
    public void notifyWitchKillTarget(Long gameId) {
        Game game = getGameStatus(gameId);
        NightActionStore.RoundActions actions = nightActionStore.getOrCreate(gameId);
        Long killTarget = actions.getWerewolfKillTarget();

        // 找到女巫玩家
        List<Player> witches = playerRepository.findByGameIdAndStatus(gameId, Player.PlayerStatus.ALIVE)
                .stream()
                .filter(p -> p.getRole() == Player.Role.WITCH)
                .toList();

        // 判断本夜女巫是否完全无可行动选项
        boolean canSave = !actions.isWitchSaveUsed() && killTarget != null;
        boolean canPoison = !actions.isWitchPoisonUsed();
        boolean noActionPossible = !canSave && !canPoison;

        for (Player witch : witches) {
            // ✨ 完全无可行动选项 → 预提交 skip 让阶段尽快推进
            if (noActionPossible) {
                String key = buildPhaseKey(game, witch);
                if (!actions.isActionSubmitted(key)) {
                    actions.markActionSubmitted(key);
                    log.info("女巫 {}号 本夜无可行动选项(killTarget={}, saveUsed={}, poisonUsed={}),自动跳过",
                            witch.getSeatNumber(), killTarget,
                            actions.isWitchSaveUsed(), actions.isWitchPoisonUsed());
                }
                // 推进可能由此触发
                advanceAfterCommit(gameId);
                continue;
            }

            Map<String, Object> witchData = new HashMap<>();
            witchData.put("hasSave", !actions.isWitchSaveUsed());
            witchData.put("hasPoison", !actions.isWitchPoisonUsed());

            if (killTarget != null) {
                Player killed = playerRepository.findById(killTarget).orElse(null);
                if (killed != null) {
                    witchData.put("killTargetId", killTarget);
                    witchData.put("killTargetSeat", killed.getSeatNumber());
                    witchData.put("killTargetName", killed.getUser() != null ? killed.getUser().getUsername() : killed.getAiName());
                }
            }

            if (Boolean.TRUE.equals(witch.getIsAi())) {
                // AI 女巫: 不在这里硬编码决策, 交由 AIPlayerBridge.scheduleAIActions
                // 异步调用 Python LLM 完成 (见 PhaseScheduler.executePhaseStart)
                // 这里仅记录被杀目标信息供 Python 侧参考 (目前通过 game_state 传递)
                log.debug("AI 女巫 {}号 将由 Python LLM 决策", witch.getSeatNumber());
            } else if (witch.getUser() != null) {
                log.info("女巫阶段-发送WITCH_INFO给真人女巫 {}号(userId={}) data={}",
                        witch.getSeatNumber(), witch.getUser().getId(), witchData);
                webSocketHandler.sendToUser(game.getRoom().getRoomCode(), witch.getUser().getId(),
                        new WebSocketMessage("WITCH_INFO", witchData));
            }
        }
    }

    /**
     * @deprecated AI 女巫决策已迁移到 Python ActionPlanner (LLM + RAG)
     *             保留此方法作为"Python 服务不可用时"的兜底, 但 AIPlayerBridge 默认走 Python。
     */
    @Deprecated
    @SuppressWarnings("unused")
    private void handleAIWitch(Long gameId, Player witch, NightActionStore.RoundActions actions, Long killTarget) {
        java.util.Random rand = new java.util.Random();
        try {
            // 救人判断
            if (killTarget != null && !actions.isWitchSaveUsed() && rand.nextDouble() < 0.5) {
                executeAIAction(gameId, witch.getId(), "save", null);
                log.info("AI 女巫 {}号 使用解药", witch.getSeatNumber());
                return; // 同一晚只能用一药
            }
            // 毒人判断
            if (!actions.isWitchPoisonUsed() && rand.nextDouble() < 0.3) {
                List<Player> targets = playerRepository.findByGameIdAndStatus(gameId, Player.PlayerStatus.ALIVE)
                        .stream()
                        .filter(p -> !p.getId().equals(witch.getId()))
                        .toList();
                if (!targets.isEmpty()) {
                    Player target = targets.get(rand.nextInt(targets.size()));
                    executeAIAction(gameId, witch.getId(), "poison", target.getId());
                    log.info("AI 女巫 {}号 毒杀 {}号", witch.getSeatNumber(), target.getSeatNumber());
                    return;
                }
            }
            // 跳过
            executeAIAction(gameId, witch.getId(), "skip", null);
        } catch (Exception e) {
            log.error("AI 女巫决策异常", e);
        }
    }

    // ========================= 投票处决 =========================

    @Transactional
    public void startVoting(Long gameId) {
        Game game = getGameStatus(gameId);
        List<Player> alivePlayers = playerRepository.findByGameIdAndStatus(gameId, Player.PlayerStatus.ALIVE);

        Set<Long> voterIds = alivePlayers.stream()
                .filter(Player::getCanVote)
                .map(Player::getId)
                .collect(Collectors.toSet());

        voteManager.startVote(gameId, voterIds);
        // 新一轮投票:幂等标志由新 VoteSession 的 AtomicBoolean(false) 自动重置,无需外部干预

        String roomCode = game.getRoom().getRoomCode();
        List<Map<String, Object>> candidates = alivePlayers.stream().map(p -> {
            Map<String, Object> info = new HashMap<>();
            info.put("playerId", p.getId());
            info.put("seatNumber", p.getSeatNumber());
            info.put("username", p.getUser() != null ? p.getUser().getUsername() : p.getAiName());
            return info;
        }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("candidates", candidates);
        data.put("voterIds", new ArrayList<>(voterIds));
        data.put("eligibleCount", voterIds.size());
        data.put("message", "投票阶段开始，请投票");

        webSocketHandler.broadcastToRoom(roomCode,
                new WebSocketMessage(WebSocketMessage.Type.VOTE_START, data));
    }

    @Transactional
    public void resolveVoting(Long gameId) {
        Game game = getGameStatus(gameId);
        VoteManager.VoteSession session = voteManager.getVoteSession(gameId);
        if (session == null) {
            log.warn("投票会话不存在 - 游戏: {}", gameId);
            return;
        }

        VoteManager.VoteResult result = session.resolve();
        String roomCode = game.getRoom().getRoomCode();

        // voteDetails: targetId -> count；JSON key 需字符串
        Map<String, Integer> voteDetails = new HashMap<>();
        for (Map.Entry<Long, Integer> entry : result.getVoteDetails().entrySet()) {
            voteDetails.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        // votesByVoter: voterId -> targetId (0=弃票)
        Map<String, Long> votesByVoter = new HashMap<>();
        for (Map.Entry<Long, Long> entry : result.getVotesByVoter().entrySet()) {
            votesByVoter.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("voteDetails", voteDetails);
        data.put("votesByVoter", votesByVoter);
        data.put("isTie", result.isTie());
        data.put("votedCount", session.getVotedCount());
        data.put("eligibleCount", session.getEligibleCount());

        if (result.isTie()) {
            data.put("message", "平票，无人被放逐");
            data.put("eliminatedPlayerId", null);
            data.put("eliminatedSeat", null);
        } else if (result.getEliminatedPlayerId() != null) {
            Player eliminated = playerRepository.findById(result.getEliminatedPlayerId()).orElse(null);
            if (eliminated != null) {
                eliminated.setStatus(Player.PlayerStatus.DEAD);
                eliminated.setCanSpeak(false);
                eliminated.setCanVote(false);
                playerRepository.save(eliminated);

                data.put("eliminatedPlayerId", eliminated.getId());
                data.put("eliminatedSeat", eliminated.getSeatNumber());
                data.put("eliminatedName",
                        eliminated.getUser() != null ? eliminated.getUser().getUsername() : eliminated.getAiName());
                data.put("message", eliminated.getSeatNumber() + "号玩家被投票放逐");

                saveLog(gameId, game.getCurrentRound(), "EXECUTION",
                        eliminated.getId(), "EXECUTED", null);

                if (eliminated.getRole() == Player.Role.HUNTER) {
                    notifyHunterCanShoot(game, eliminated);
                }
            }
        } else {
            data.put("message", "无人被放逐");
            data.put("eliminatedPlayerId", null);
            data.put("eliminatedSeat", null);
        }

        webSocketHandler.broadcastToRoom(roomCode,
                new WebSocketMessage(WebSocketMessage.Type.VOTE_RESULT, data));

        // ✨ 公共事件:把投票结果推给所有 AI 的记忆系统
        // 关键转换:Java 端用 Player.id,Python 端 Agent 用座位号 → 这里做映射
        try {
            // Player.id → seatNumber 映射(只查一次)
            Map<Long, Integer> idToSeat = new HashMap<>();
            for (Player p : playerRepository.findByGameId(gameId)) {
                if (p.getSeatNumber() != null) {
                    idToSeat.put(p.getId(), p.getSeatNumber());
                }
            }

            Map<String, Integer> votesBySeat = new HashMap<>();
            for (Map.Entry<Long, Long> e : result.getVotesByVoter().entrySet()) {
                Integer voterSeat = idToSeat.get(e.getKey());
                Integer targetSeat = (e.getValue() != null && e.getValue() > 0)
                        ? idToSeat.get(e.getValue()) : 0;
                if (voterSeat != null) {
                    votesBySeat.put(String.valueOf(voterSeat), targetSeat == null ? 0 : targetSeat);
                }
            }

            Map<String, Object> aiEventData = new HashMap<>();
            aiEventData.put("votes", votesBySeat);
            aiEventData.put("eliminated_player",
                    result.getEliminatedPlayerId() != null ? idToSeat.get(result.getEliminatedPlayerId()) : null);
            aiEventData.put("is_tie", result.isTie());
            getAIPlayerBridge().pushPublicEventToAllAIs(gameId, "VOTE_RESULT", aiEventData);
        } catch (Exception e) {
            log.warn("推送 VOTE_RESULT 给 AI 失败 - gameId={}", gameId, e);
        }

        voteManager.clearVote(gameId);
        // 幂等标志随 session 一起被 clearVote 清除,下一轮投票的新 session 会重置
        log.info("投票结算 - 游戏: {}, 平票: {}, 处决: {}, 明细: {}",
                gameId, result.isTie(), result.getEliminatedPlayerId(), result.getVotesByVoter());
        GameLogger.room(roomCode, gameId, "投票结算: 平票={}, 处决={}",
                result.isTie(), result.getEliminatedPlayerId());

        checkWinCondition(gameId);
    }

    // ========================= 阶段管理 =========================

    @Transactional
    public void updatePhase(Long gameId, Game.GamePhase phase) {
        Game game = getGameStatus(gameId);
        game.setCurrentPhase(phase);
        gameRepository.save(game);
        // ✨ 讨论阶段的发言人初始化与轮转,由 DiscussionManager.startDiscussion 统一管理
        // (PhaseScheduler.executePhaseStart 会在 DISCUSSION 阶段开始时调用)
        // 注意:不在此处广播 PHASE_CHANGE,由 PhaseScheduler.executePhaseStart() 统一广播(带 endsAt)
    }

    @Transactional
    public void enterNewNight(Long gameId) {
        Game game = getGameStatus(gameId);
        game.setCurrentRound(game.getCurrentRound() + 1);
        game.setCurrentPhase(Game.GamePhase.NIGHT_START);
        gameRepository.save(game);

        nightActionStore.resetRound(gameId);
        log.info("进入第 {} 夜 - 游戏: {}", game.getCurrentRound(), gameId);
    }

    public void broadcastToGame(Long gameId, String type, Object data) {
        Game game = gameRepository.findById(gameId).orElse(null);
        if (game == null) return;

        webSocketHandler.broadcastToRoom(game.getRoom().getRoomCode(),
                new WebSocketMessage(type, data));
    }

    /**
     * ✨ 事务安全地触发 PhaseScheduler.advanceIfAllActed
     *
     * - 若当前调用处于活跃事务中,注册为 afterCommit 回调(事务回滚则不会执行);
     * - 若无事务(如 AI 直接调用),立即执行。
     *
     * 解决原实现的风险:在 @Transactional 内直接调 advanceIfAllActed 会在事务回滚时
     * 仍然广播 PHASE_ADVANCE / 提交异步推进任务,造成状态不一致。
     */
    private void advanceAfterCommit(Long gameId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        phaseScheduler.advanceIfAllActed(gameId);
                    } catch (Exception e) {
                        log.warn("afterCommit advanceIfAllActed 失败 - gameId={}", gameId, e);
                    }
                }
            });
        } else {
            phaseScheduler.advanceIfAllActed(gameId);
        }
    }

    // ========================= 查询 =========================

    public Game getGameStatus(Long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("游戏不存在"));
    }

    /**
     * ✨ 构建完整游戏快照 — 供前端断线重连/首次进入时同步状态
     *
     * 返回包含:
     *   - 游戏基本状态 (status/round/phase)
     *   - 阶段绝对时间戳 (startedAt/endsAt/serverNow) — 用于前端自愈倒计时
     *   - 玩家列表 (status/canSpeak)
     *   - 当前用户私有状态:
     *       * myPlayerId, myRole, teammates
     *       * mySubmitted (本阶段本回合是否已提交行动)
     *       * witchInfo (若是女巫)
     *       * canHunterShoot (若是已死的猎人)
     *   - 讨论阶段:currentSpeakerId + speakEndsAt
     *   - 投票阶段:voteRecords + voteProgress
     *
     * 设计原则:
     *   - 所有时间用绝对 epoch ms,避免客户端-服务端时钟漂移
     *   - 私有字段按 userId 权限过滤(不泄露其他玩家角色)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildGameSnapshot(Long gameId, Long userId) {
        Game game = getGameStatus(gameId);
        Map<String, Object> snapshot = new HashMap<>();
        long serverNow = System.currentTimeMillis();

        // --- 基本状态 ---
        snapshot.put("gameId", game.getId());
        snapshot.put("status", game.getStatus().name());
        snapshot.put("round", game.getCurrentRound());
        snapshot.put("phase", game.getCurrentPhase().name());
        snapshot.put("winner", game.getWinner() != null ? game.getWinner().name() : null);
        snapshot.put("serverNow", serverNow);

        // --- 阶段绝对时间戳 ---
        PhaseScheduler.PhaseContext pctx = phaseScheduler.getActivePhase(gameId);
        if (pctx != null) {
            snapshot.put("phaseStartedAt", pctx.getPhaseStartedAt());
            snapshot.put("phaseEndsAt", pctx.getPhaseEndsAt());
            snapshot.put("phaseRemainingMs", Math.max(0, pctx.getPhaseEndsAt() - serverNow));
            snapshot.put("isNight", pctx.isNight());
        }

        // --- 玩家列表 (带存活/可发言状态,不含角色) ---
        List<Player> players = playerRepository.findByGameId(gameId);
        boolean isFinished = game.getStatus() == Game.GameStatus.FINISHED;
        Player myPlayer = players.stream()
                .filter(p -> p.getUser() != null && p.getUser().getId().equals(userId))
                .findFirst().orElse(null);

        List<Map<String, Object>> playerInfos = players.stream().map(p -> {
            Map<String, Object> info = new HashMap<>();
            info.put("playerId", p.getId());
            info.put("seatNumber", p.getSeatNumber());
            info.put("username", p.getUser() != null ? p.getUser().getUsername() : p.getAiName());
            info.put("avatarUrl", p.getUser() != null ? p.getUser().getAvatarUrl() : null);
            info.put("isAi", Boolean.TRUE.equals(p.getIsAi()));
            info.put("status", p.getStatus().name());
            info.put("canSpeak", Boolean.TRUE.equals(p.getCanSpeak()));
            // 角色仅对本人/游戏结束后可见
            if (isFinished || (myPlayer != null && p.getId().equals(myPlayer.getId()))) {
                info.put("role", p.getRole().name());
            } else {
                info.put("role", "UNKNOWN");
            }
            return info;
        }).collect(Collectors.toList());
        snapshot.put("players", playerInfos);

        // --- 个人私有状态 ---
        if (myPlayer != null) {
            snapshot.put("myPlayerId", myPlayer.getId());
            snapshot.put("myRole", myPlayer.getRole().name());
            snapshot.put("mySeat", myPlayer.getSeatNumber());

            // 狼人队友
            if (myPlayer.getRole() == Player.Role.WEREWOLF) {
                List<Long> teammates = players.stream()
                        .filter(p -> p.getRole() == Player.Role.WEREWOLF)
                        .filter(p -> !p.getId().equals(myPlayer.getId()))
                        .map(Player::getId)
                        .collect(Collectors.toList());
                snapshot.put("teammates", teammates);
            }

            // 本阶段本回合是否已提交行动
            NightActionStore.RoundActions actions = nightActionStore.getOrCreate(gameId);
            String phaseKey = buildPhaseKey(game, myPlayer);
            snapshot.put("mySubmitted", actions.isActionSubmitted(phaseKey));

            // 猎人可开枪状态
            if (myPlayer.getRole() == Player.Role.HUNTER
                    && myPlayer.getStatus() == Player.PlayerStatus.DEAD
                    && !hunterShot.contains(myPlayer.getId())) {
                snapshot.put("canHunterShoot", true);
            }

            // 女巫阶段 — 把被杀信息包进快照
            if (myPlayer.getRole() == Player.Role.WITCH
                    && myPlayer.getStatus() == Player.PlayerStatus.ALIVE
                    && game.getCurrentPhase() == Game.GamePhase.WITCH) {
                Map<String, Object> witchInfo = new HashMap<>();
                witchInfo.put("hasSave", !actions.isWitchSaveUsed());
                witchInfo.put("hasPoison", !actions.isWitchPoisonUsed());
                Long killTarget = actions.getWerewolfKillTarget();
                if (killTarget != null) {
                    Player killed = playerRepository.findById(killTarget).orElse(null);
                    if (killed != null) {
                        witchInfo.put("killTargetId", killTarget);
                        witchInfo.put("killTargetSeat", killed.getSeatNumber());
                        witchInfo.put("killTargetName",
                                killed.getUser() != null ? killed.getUser().getUsername() : killed.getAiName());
                    }
                }
                snapshot.put("witchInfo", witchInfo);
            }
        }

        // --- 讨论阶段快照 ---
        if (game.getCurrentPhase() == Game.GamePhase.DISCUSSION) {
            try {
                Map<String, Object> discussion = getDiscussionManager().getDiscussionSnapshot(gameId);
                if (discussion != null) {
                    snapshot.put("discussion", discussion);
                }
            } catch (Exception ignored) {}
        }

        // --- 投票阶段快照 ---
        if (game.getCurrentPhase() == Game.GamePhase.VOTING
                || game.getCurrentPhase() == Game.GamePhase.EXECUTION) {
            VoteManager.VoteSession session = voteManager.getVoteSession(gameId);
            if (session != null) {
                Map<String, Object> voteData = new HashMap<>();
                Map<String, Long> votesByVoter = new HashMap<>();
                for (Map.Entry<Long, Long> e : session.snapshotVotes().entrySet()) {
                    votesByVoter.put(String.valueOf(e.getKey()), e.getValue());
                }
                voteData.put("votesByVoter", votesByVoter);
                voteData.put("votedCount", session.getVotedCount());
                voteData.put("eligibleCount", session.getEligibleCount());
                snapshot.put("vote", voteData);
            }
        }

        return snapshot;
    }

    public Optional<Game> getGameByRoomId(Long roomId) {
        return gameRepository.findByRoomId(roomId);
    }

    public List<Player> getPlayers(Long gameId) {
        return playerRepository.findByGameId(gameId);
    }

    public NightActionStore.RoundActions getNightActions(Long gameId) {
        return nightActionStore.getOrCreate(gameId);
    }

    public List<Player> getAlivePlayers(Long gameId) {
        return playerRepository.findByGameIdAndStatus(gameId, Player.PlayerStatus.ALIVE);
    }

    public List<GameLog> getGameLogs(Long gameId) {
        return gameLogRepository.findByGameIdOrderByCreatedAtAsc(gameId);
    }

    // ========================= 阶段行动者辅助 =========================

    /**
     * 该阶段是否属于"夜间特定角色行动阶段"（WEREWOLF/SEER/WITCH/GUARD）
     */
    public boolean isNightRoleActionPhase(Game.GamePhase phase) {
        return phase == Game.GamePhase.WEREWOLF
                || phase == Game.GamePhase.SEER
                || phase == Game.GamePhase.WITCH
                || phase == Game.GamePhase.GUARD;
    }

    /**
     * 玩家是否是该阶段的行动者（按角色匹配）
     */
    public boolean isRolePhaseActor(Player player, Game.GamePhase phase) {
        if (player == null || player.getStatus() != Player.PlayerStatus.ALIVE) return false;
        return switch (phase) {
            case WEREWOLF -> player.getRole() == Player.Role.WEREWOLF;
            case SEER -> player.getRole() == Player.Role.SEER;
            case WITCH -> player.getRole() == Player.Role.WITCH;
            case GUARD -> player.getRole() == Player.Role.GUARD;
            default -> false;
        };
    }

    /**
     * 返回当前阶段应行动的全部玩家（含 AI 与真人）
     * - 夜间角色阶段：该角色的存活玩家
     * - 投票阶段：所有存活且有投票权的玩家
     * - 其他阶段：空列表
     */
    public List<Player> getExpectedActors(Long gameId, Game.GamePhase phase) {
        List<Player> alive = playerRepository.findByGameIdAndStatus(gameId, Player.PlayerStatus.ALIVE);
        if (isNightRoleActionPhase(phase)) {
            return alive.stream()
                    .filter(p -> isRolePhaseActor(p, phase))
                    .collect(Collectors.toList());
        }
        if (phase == Game.GamePhase.VOTING) {
            return alive.stream()
                    .filter(p -> Boolean.TRUE.equals(p.getCanVote()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    /**
     * 当前阶段是否已全员提交
     */
    public boolean isPhaseAllSubmitted(Long gameId) {
        Game game = getGameStatus(gameId);
        Game.GamePhase phase = game.getCurrentPhase();

        if (isNightRoleActionPhase(phase)) {
            List<Player> expected = getExpectedActors(gameId, phase);
            if (expected.isEmpty()) return false;
            NightActionStore.RoundActions actions = nightActionStore.getOrCreate(gameId);
            for (Player p : expected) {
                String key = buildPhaseKey(game.getCurrentRound(), phase, p.getId());
                if (!actions.isActionSubmitted(key)) {
                    return false;
                }
            }
            return true;
        }

        if (phase == Game.GamePhase.VOTING) {
            VoteManager.VoteSession session = voteManager.getVoteSession(gameId);
            // session 已被 clearVote 清理 → 说明已经结算完（等价于"全员提交"），允许推进
            if (session == null) return true;
            return session.isAllVoted();
        }

        return false;
    }

    // ========================= 胜负判定 =========================

    @Transactional
    public void checkWinCondition(Long gameId) {
        List<Player> players = playerRepository.findByGameId(gameId);

        long werewolfCount = players.stream()
                .filter(p -> p.getStatus() == Player.PlayerStatus.ALIVE)
                .filter(p -> roleAssigner.isWerewolfCamp(p.getRole()))
                .count();

        long aliveVillagerSide = players.stream()
                .filter(p -> p.getStatus() == Player.PlayerStatus.ALIVE)
                .filter(p -> !roleAssigner.isWerewolfCamp(p.getRole()))
                .count();

        long godCount = players.stream()
                .filter(p -> p.getStatus() == Player.PlayerStatus.ALIVE)
                .filter(p -> roleAssigner.isGodRole(p.getRole()))
                .count();

        long civilianCount = aliveVillagerSide - godCount;

        if (werewolfCount == 0) {
            endGame(gameId, Game.Winner.VILLAGER);
        } else if (godCount == 0 || civilianCount <= 0) {
            endGame(gameId, Game.Winner.WEREWOLF);
        } else if (werewolfCount >= aliveVillagerSide) {
            endGame(gameId, Game.Winner.WEREWOLF);
        }
    }

    @Transactional
    public void endGame(Long gameId, Game.Winner winner) {
        Game game = getGameStatus(gameId);
        if (game.getStatus() == Game.GameStatus.FINISHED) return;

        game.setStatus(Game.GameStatus.FINISHED);
        game.setWinner(winner);
        game.setEndedAt(LocalDateTime.now());
        gameRepository.save(game);

        Room room = game.getRoom();
        room.setStatus(Room.RoomStatus.FINISHED);
        roomRepository.save(room);

        String roomCode = room.getRoomCode();
        List<Player> allPlayers = playerRepository.findByGameId(gameId);

        List<Map<String, Object>> playerRoles = allPlayers.stream().map(p -> {
            Map<String, Object> info = new HashMap<>();
            info.put("playerId", p.getId());
            info.put("seatNumber", p.getSeatNumber());
            info.put("username", p.getUser() != null ? p.getUser().getUsername() : p.getAiName());
            info.put("role", p.getRole().name());
            info.put("status", p.getStatus().name());
            return info;
        }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("winner", winner.name());
        data.put("message", getWinnerMessage(winner));
        data.put("players", playerRoles);

        webSocketHandler.broadcastToRoom(roomCode,
                new WebSocketMessage(WebSocketMessage.Type.GAME_OVER, data));

        phaseScheduler.stopGame(gameId);
        nightActionStore.clear(gameId);
        voteManager.clearVote(gameId);

        saveLog(gameId, game.getCurrentRound(), "GAME_END", null, "END",
                "{\"winner\":\"" + winner.name() + "\"}");

        log.info("游戏结束 - ID: {}, 获胜方: {}", gameId, winner);
        GameLogger.room(roomCode, gameId, "=== 游戏结束 === 获胜方: {}", winner);
    }

    private String getWinnerMessage(Game.Winner winner) {
        return switch (winner) {
            case VILLAGER -> "好人阵营获胜！";
            case WEREWOLF -> "狼人阵营获胜！";
            case THIRD_PARTY -> "第三方阵营获胜！";
            case NONE -> "平局";
        };
    }

    // ========================= 日志 =========================

    private void saveLog(Long gameId, int round, String phase, Long playerId,
                         String actionType, String actionData) {
        GameLog gameLog = GameLog.builder()
                .gameId(gameId)
                .round(round)
                .phase(phase)
                .playerId(playerId)
                .actionType(actionType)
                .actionData(actionData)
                .build();
        gameLogRepository.save(gameLog);
    }
}
