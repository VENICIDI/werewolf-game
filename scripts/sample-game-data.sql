-- ================================================================
-- 狼人杀 标准9人局 模拟游戏数据
-- 模式: standard_9 (3狼人 + 3村民 + 预言家 + 女巫 + 猎人)
-- 结果: 村民阵营获胜 (经过2个完整回合)
-- ================================================================

USE werewolf;

-- ================================================================
-- 1. 用户数据 (1个真人 + 8个AI用户)
-- ================================================================
-- 密码统一为 BCrypt 加密的 "123456"
INSERT INTO users (id, username, password, email, avatar_url, total_games, win_games, rating, login_type, created_at, updated_at) VALUES
(1, 'admin',       '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', 'admin@werewolf.com',  '/avatars/admin.png',  5, 3, 1500, 'APP', '2026-04-01 10:00:00', '2026-04-06 23:00:00'),
(2, '[AI]小明',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', 'ai_ming@werewolf.com', NULL, 0, 0, 1000, 'APP', '2026-04-06 23:00:00', '2026-04-06 23:00:00'),
(3, '[AI]小红',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', 'ai_hong@werewolf.com', NULL, 0, 0, 1000, 'APP', '2026-04-06 23:00:00', '2026-04-06 23:00:00'),
(4, '[AI]小刚',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', 'ai_gang@werewolf.com', NULL, 0, 0, 1000, 'APP', '2026-04-06 23:00:00', '2026-04-06 23:00:00'),
(5, '[AI]小丽',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', 'ai_li@werewolf.com',   NULL, 0, 0, 1000, 'APP', '2026-04-06 23:00:00', '2026-04-06 23:00:00'),
(6, '[AI]小强',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', 'ai_qiang@werewolf.com',NULL, 0, 0, 1000, 'APP', '2026-04-06 23:00:00', '2026-04-06 23:00:00'),
(7, '[AI]小芳',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', 'ai_fang@werewolf.com', NULL, 0, 0, 1000, 'APP', '2026-04-06 23:00:00', '2026-04-06 23:00:00'),
(8, '[AI]小伟',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', 'ai_wei@werewolf.com',  NULL, 0, 0, 1000, 'APP', '2026-04-06 23:00:00', '2026-04-06 23:00:00'),
(9, '[AI]小雪',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', 'ai_xue@werewolf.com', NULL, 0, 0, 1000, 'APP', '2026-04-06 23:00:00', '2026-04-06 23:00:00')
ON DUPLICATE KEY UPDATE username=VALUES(username);

-- ================================================================
-- 2. 房间数据
-- ================================================================
INSERT INTO rooms (id, room_code, room_name, host_id, max_players, current_players, status, has_password, password, created_at, updated_at) VALUES
(1, 'ABC123', 'admin的房间', 1, 9, 9, 'FINISHED', FALSE, NULL, '2026-04-06 23:00:00', '2026-04-06 23:30:00')
ON DUPLICATE KEY UPDATE room_code=VALUES(room_code);

-- ================================================================
-- 3. 房间成员数据 (1真人 + 8个AI)
-- ================================================================
INSERT INTO room_members (id, room_id, user_id, is_host, is_ready, joined_at) VALUES
(1, 1, 1, TRUE,  TRUE, '2026-04-06 23:00:00'),  -- admin (房主/真人)
(2, 1, 2, FALSE, TRUE, '2026-04-06 23:00:10'),  -- [AI]小明
(3, 1, 3, FALSE, TRUE, '2026-04-06 23:00:10'),  -- [AI]小红
(4, 1, 4, FALSE, TRUE, '2026-04-06 23:00:10'),  -- [AI]小刚
(5, 1, 5, FALSE, TRUE, '2026-04-06 23:00:10'),  -- [AI]小丽
(6, 1, 6, FALSE, TRUE, '2026-04-06 23:00:10'),  -- [AI]小强
(7, 1, 7, FALSE, TRUE, '2026-04-06 23:00:10'),  -- [AI]小芳
(8, 1, 8, FALSE, TRUE, '2026-04-06 23:00:10'),  -- [AI]小伟
(9, 1, 9, FALSE, TRUE, '2026-04-06 23:00:10')   -- [AI]小雪
ON DUPLICATE KEY UPDATE room_id=VALUES(room_id);

-- ================================================================
-- 4. 游戏数据 (已结束，村民获胜)
-- ================================================================
INSERT INTO games (id, room_id, status, current_round, current_phase, winner, started_at, ended_at, created_at) VALUES
(1, 1, 'FINISHED', 2, 'EXECUTION', 'VILLAGER', '2026-04-06 23:01:00', '2026-04-06 23:28:00', '2026-04-06 23:00:30')
ON DUPLICATE KEY UPDATE room_id=VALUES(room_id);

-- ================================================================
-- 5. 玩家数据 (9人标准局角色分配)
-- ================================================================
-- 角色分配: 3狼人(4号小刚,6号小强,8号小伟) + 3村民(2号小明,5号小丽,9号小雪)
--           + 预言家(1号admin) + 女巫(3号小红) + 猎人(7号小芳)
--
-- 游戏结局时状态:
--   存活: 1号admin(预言家), 3号小红(女巫), 5号小丽(村民), 7号小芳(猎人), 9号小雪(村民)
--   死亡: 2号小明(村民-第1夜被杀), 4号小刚(狼人-第1天投票处决),
--         6号小强(狼人-第2夜女巫毒杀), 8号小伟(狼人-第2天投票处决)
-- ================================================================
INSERT INTO players (id, game_id, user_id, is_ai, ai_name, seat_number, role, status, is_captain, can_speak, can_vote, created_at) VALUES
-- 座位1: admin (真人, 预言家, 存活)
(1,  1, 1, FALSE, NULL,        1, 'SEER',     'ALIVE', FALSE, TRUE,  TRUE,  '2026-04-06 23:01:00'),
-- 座位2: [AI]小明 (村民, 第1夜被狼人杀死)
(2,  1, 2, TRUE,  '[AI]小明',  2, 'VILLAGER', 'DEAD',  FALSE, FALSE, FALSE, '2026-04-06 23:01:00'),
-- 座位3: [AI]小红 (女巫, 存活)
(3,  1, 3, TRUE,  '[AI]小红',  3, 'WITCH',    'ALIVE', FALSE, TRUE,  TRUE,  '2026-04-06 23:01:00'),
-- 座位4: [AI]小刚 (狼人, 第1天被投票处决)
(4,  1, 4, TRUE,  '[AI]小刚',  4, 'WEREWOLF', 'DEAD',  FALSE, FALSE, FALSE, '2026-04-06 23:01:00'),
-- 座位5: [AI]小丽 (村民, 存活)
(5,  1, 5, TRUE,  '[AI]小丽',  5, 'VILLAGER', 'ALIVE', FALSE, TRUE,  TRUE,  '2026-04-06 23:01:00'),
-- 座位6: [AI]小强 (狼人, 第2夜被女巫毒杀)
(6,  1, 6, TRUE,  '[AI]小强',  6, 'WEREWOLF', 'DEAD',  FALSE, FALSE, FALSE, '2026-04-06 23:01:00'),
-- 座位7: [AI]小芳 (猎人, 存活)
(7,  1, 7, TRUE,  '[AI]小芳',  7, 'HUNTER',   'ALIVE', FALSE, TRUE,  TRUE,  '2026-04-06 23:01:00'),
-- 座位8: [AI]小伟 (狼人, 第2天被投票处决 → 狼人全灭 → 村民胜)
(8,  1, 8, TRUE,  '[AI]小伟',  8, 'WEREWOLF', 'DEAD',  FALSE, FALSE, FALSE, '2026-04-06 23:01:00'),
-- 座位9: [AI]小雪 (村民, 存活)
(9,  1, 9, TRUE,  '[AI]小雪',  9, 'VILLAGER', 'ALIVE', FALSE, TRUE,  TRUE,  '2026-04-06 23:01:00')
ON DUPLICATE KEY UPDATE game_id=VALUES(game_id);

-- ================================================================
-- 6. 游戏日志 (完整2回合流水)
-- ================================================================
INSERT INTO game_logs (id, game_id, round, phase, player_id, action_type, action_data, created_at) VALUES

-- ===================== 游戏开始 =====================
(1,  1, 0, 'GAME_START', NULL, 'START', NULL, '2026-04-06 23:01:00'),

-- ===================== 第1回合 - 夜晚 =====================
-- 狼人行动: 4号小刚(狼人)投票杀2号小明
(2,  1, 1, 'WEREWOLF', 4, 'KILL', '{"targetId":2}', '2026-04-06 23:01:35'),
-- 狼人行动: 6号小强(狼人)投票杀2号小明
(3,  1, 1, 'WEREWOLF', 6, 'KILL', '{"targetId":2}', '2026-04-06 23:01:38'),
-- 狼人行动: 8号小伟(狼人)投票杀2号小明
(4,  1, 1, 'WEREWOLF', 8, 'KILL', '{"targetId":2}', '2026-04-06 23:01:40'),
-- 预言家行动: 1号admin(预言家)查验4号小刚 → 发现是狼人
(5,  1, 1, 'SEER', 1, 'CHECK', '{"targetId":4}', '2026-04-06 23:02:05'),
-- 女巫行动: 3号小红(女巫)选择不救人(不使用解药)
(6,  1, 1, 'WITCH', 3, 'SKIP', NULL, '2026-04-06 23:02:35'),
-- 夜晚结算: 2号小明死亡
(7,  1, 1, 'NIGHT_DEATH', 2, 'DEATH', NULL, '2026-04-06 23:03:00'),

-- ===================== 第1回合 - 白天 =====================
-- 投票: 1号admin 投 4号小刚 (查验到的狼人)
(8,  1, 1, 'VOTING', 1, 'VOTE', '{"targetId":4}', '2026-04-06 23:08:10'),
-- 投票: 3号小红 投 4号小刚
(9,  1, 1, 'VOTING', 3, 'VOTE', '{"targetId":4}', '2026-04-06 23:08:12'),
-- 投票: 4号小刚 投 1号admin (狼人反咬)
(10, 1, 1, 'VOTING', 4, 'VOTE', '{"targetId":1}', '2026-04-06 23:08:14'),
-- 投票: 5号小丽 投 4号小刚
(11, 1, 1, 'VOTING', 5, 'VOTE', '{"targetId":4}', '2026-04-06 23:08:16'),
-- 投票: 6号小强 投 1号admin (狼人帮队友)
(12, 1, 1, 'VOTING', 6, 'VOTE', '{"targetId":1}', '2026-04-06 23:08:18'),
-- 投票: 7号小芳 投 4号小刚
(13, 1, 1, 'VOTING', 7, 'VOTE', '{"targetId":4}', '2026-04-06 23:08:20'),
-- 投票: 8号小伟 投 1号admin (狼人帮队友)
(14, 1, 1, 'VOTING', 8, 'VOTE', '{"targetId":1}', '2026-04-06 23:08:22'),
-- 投票: 9号小雪 投 4号小刚
(15, 1, 1, 'VOTING', 9, 'VOTE', '{"targetId":4}', '2026-04-06 23:08:24'),
-- 投票结果: 4号小刚 5票 vs 1号admin 3票 → 4号被处决
(16, 1, 1, 'EXECUTION', 4, 'EXECUTED', NULL, '2026-04-06 23:08:30'),

-- ===================== 第2回合 - 夜晚 =====================
-- 狼人行动: 6号小强(狼人)投票杀1号admin
(17, 1, 2, 'WEREWOLF', 6, 'KILL', '{"targetId":1}', '2026-04-06 23:12:35'),
-- 狼人行动: 8号小伟(狼人)投票杀1号admin
(18, 1, 2, 'WEREWOLF', 8, 'KILL', '{"targetId":1}', '2026-04-06 23:12:38'),
-- 预言家行动: 1号admin(预言家)查验6号小强 → 发现是狼人
(19, 1, 2, 'SEER', 1, 'CHECK', '{"targetId":6}', '2026-04-06 23:13:05'),
-- 女巫行动: 3号小红(女巫)使用解药救1号admin
(20, 1, 2, 'WITCH', 3, 'SAVE', '{"targetId":1}', '2026-04-06 23:13:20'),
-- 女巫行动: 3号小红(女巫)使用毒药毒6号小强
(21, 1, 2, 'WITCH', 3, 'POISON', '{"targetId":6}', '2026-04-06 23:13:25'),
-- 夜晚结算: 1号admin被救活; 6号小强被毒杀
(22, 1, 2, 'NIGHT_DEATH', 6, 'DEATH', NULL, '2026-04-06 23:14:00'),

-- ===================== 第2回合 - 白天 =====================
-- 投票: 1号admin 投 8号小伟 (推理出最后一个狼人)
(23, 1, 2, 'VOTING', 1, 'VOTE', '{"targetId":8}', '2026-04-06 23:20:10'),
-- 投票: 3号小红 投 8号小伟
(24, 1, 2, 'VOTING', 3, 'VOTE', '{"targetId":8}', '2026-04-06 23:20:12'),
-- 投票: 5号小丽 投 8号小伟
(25, 1, 2, 'VOTING', 5, 'VOTE', '{"targetId":8}', '2026-04-06 23:20:14'),
-- 投票: 7号小芳 投 8号小伟
(26, 1, 2, 'VOTING', 7, 'VOTE', '{"targetId":8}', '2026-04-06 23:20:16'),
-- 投票: 8号小伟 投 1号admin (最后的挣扎)
(27, 1, 2, 'VOTING', 8, 'VOTE', '{"targetId":1}', '2026-04-06 23:20:18'),
-- 投票: 9号小雪 投 8号小伟
(28, 1, 2, 'VOTING', 9, 'VOTE', '{"targetId":8}', '2026-04-06 23:20:20'),
-- 投票结果: 8号小伟 5票 vs 1号admin 1票 → 8号被处决
(29, 1, 2, 'EXECUTION', 8, 'EXECUTED', NULL, '2026-04-06 23:20:30'),

-- ===================== 游戏结束 =====================
-- 狼人全灭(4号、6号、8号均死亡) → 村民阵营获胜
(30, 1, 2, 'GAME_END', NULL, 'END', '{"winner":"VILLAGER"}', '2026-04-06 23:28:00')

ON DUPLICATE KEY UPDATE game_id=VALUES(game_id);

-- ================================================================
-- 验证查询
-- ================================================================

-- 查看游戏状态
-- SELECT id, status, current_round, current_phase, winner, started_at, ended_at FROM games WHERE id = 1;

-- 查看玩家角色和存活状态
-- SELECT p.seat_number, u.username, p.role, p.status, p.is_ai
-- FROM players p LEFT JOIN users u ON p.user_id = u.id
-- WHERE p.game_id = 1 ORDER BY p.seat_number;

-- 查看完整游戏日志
-- SELECT gl.round, gl.phase, gl.action_type, gl.action_data, u.username, gl.created_at
-- FROM game_logs gl
-- LEFT JOIN players p ON gl.player_id = p.id
-- LEFT JOIN users u ON p.user_id = u.id
-- WHERE gl.game_id = 1 ORDER BY gl.created_at;
