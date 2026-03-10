-- 狼人杀游戏数据库初始化脚本

CREATE DATABASE IF NOT EXISTS werewolf CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE werewolf;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    avatar_url VARCHAR(255),
    total_games INT DEFAULT 0,
    win_games INT DEFAULT 0,
    rating INT DEFAULT 1000,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_rating (rating)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 房间表
CREATE TABLE IF NOT EXISTS rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_code VARCHAR(10) NOT NULL UNIQUE,
    room_name VARCHAR(50) NOT NULL,
    host_id BIGINT NOT NULL,
    max_players INT DEFAULT 12,
    current_players INT DEFAULT 0,
    status ENUM('WAITING', 'PLAYING', 'FINISHED') DEFAULT 'WAITING',
    has_password BOOLEAN DEFAULT FALSE,
    password VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (host_id) REFERENCES users(id),
    INDEX idx_room_code (room_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 游戏表
CREATE TABLE IF NOT EXISTS games (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    status ENUM('PREPARING', 'RUNNING', 'PAUSED', 'FINISHED') DEFAULT 'PREPARING',
    current_round INT DEFAULT 0,
    current_phase ENUM('NONE', 'NIGHT_START', 'WEREWOLF', 'SEER', 'WITCH', 'HUNTER', 'DAY_START', 'DISCUSSION', 'VOTING', 'EXECUTION') DEFAULT 'NONE',
    winner ENUM('NONE', 'WEREWOLF', 'VILLAGER', 'THIRD_PARTY') DEFAULT 'NONE',
    started_at DATETIME,
    ended_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (room_id) REFERENCES rooms(id),
    INDEX idx_room_id (room_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 玩家表
CREATE TABLE IF NOT EXISTS players (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id BIGINT NOT NULL,
    user_id BIGINT,
    is_ai BOOLEAN DEFAULT FALSE,
    ai_name VARCHAR(50),
    seat_number INT,
    role ENUM('UNKNOWN', 'VILLAGER', 'WEREWOLF', 'SEER', 'WITCH', 'HUNTER', 'GUARD', 'IDIOT') DEFAULT 'UNKNOWN',
    status ENUM('ALIVE', 'DEAD', 'DISCONNECTED') DEFAULT 'ALIVE',
    is_captain BOOLEAN DEFAULT FALSE,
    can_speak BOOLEAN DEFAULT TRUE,
    can_vote BOOLEAN DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (game_id) REFERENCES games(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_game_id (game_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 游戏日志表
CREATE TABLE IF NOT EXISTS game_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id BIGINT NOT NULL,
    round INT NOT NULL,
    phase VARCHAR(50) NOT NULL,
    player_id BIGINT,
    action_type VARCHAR(50) NOT NULL,
    action_data JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (game_id) REFERENCES games(id),
    FOREIGN KEY (player_id) REFERENCES players(id),
    INDEX idx_game_id (game_id),
    INDEX idx_round (round)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入测试数据
INSERT INTO users (username, password, email, rating) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', 'admin@werewolf.com', 1500),
('test', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', 'test@werewolf.com', 1000);
