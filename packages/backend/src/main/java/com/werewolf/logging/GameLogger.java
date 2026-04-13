package com.werewolf.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 游戏日志工具
 * 
 * 提供两种专用日志通道:
 * 1. GAME_ROOM — 按房间ID分文件，记录游戏流程
 * 2. AI_DIALOG — 按游戏ID分文件，记录AI模型输入输出
 * 
 * 使用 MDC (Mapped Diagnostic Context) 实现动态路由。
 */
public final class GameLogger {

    private static final Logger ROOM_LOG = LoggerFactory.getLogger("GAME_ROOM");
    private static final Logger AI_LOG = LoggerFactory.getLogger("AI_DIALOG");

    private GameLogger() {}

    // ========================= 房间/游戏流程日志 =========================

    /**
     * 设置当前线程的游戏上下文（用于自动路由日志文件）
     */
    public static void setContext(String roomCode, Long gameId) {
        if (roomCode != null) MDC.put("roomCode", roomCode);
        if (gameId != null) MDC.put("gameId", String.valueOf(gameId));
    }

    /**
     * 清除当前线程的游戏上下文
     */
    public static void clearContext() {
        MDC.remove("roomCode");
        MDC.remove("gameId");
    }

    /**
     * 记录游戏流程日志（自动写入对应房间文件）
     */
    public static void room(String roomCode, Long gameId, String format, Object... args) {
        try {
            if (roomCode != null) MDC.put("roomCode", roomCode);
            if (gameId != null) MDC.put("gameId", String.valueOf(gameId));
            ROOM_LOG.info(format, args);
        } finally {
            MDC.remove("roomCode");
            MDC.remove("gameId");
        }
    }

    /**
     * 记录游戏警告日志
     */
    public static void roomWarn(String roomCode, Long gameId, String format, Object... args) {
        try {
            if (roomCode != null) MDC.put("roomCode", roomCode);
            if (gameId != null) MDC.put("gameId", String.valueOf(gameId));
            ROOM_LOG.warn(format, args);
        } finally {
            MDC.remove("roomCode");
            MDC.remove("gameId");
        }
    }

    /**
     * 记录游戏错误日志
     */
    public static void roomError(String roomCode, Long gameId, String format, Object... args) {
        try {
            if (roomCode != null) MDC.put("roomCode", roomCode);
            if (gameId != null) MDC.put("gameId", String.valueOf(gameId));
            ROOM_LOG.error(format, args);
        } finally {
            MDC.remove("roomCode");
            MDC.remove("gameId");
        }
    }

    // ========================= AI 对话日志 =========================

    /**
     * 记录 AI 请求（模型输入）
     */
    public static void aiRequest(Long gameId, Long playerId, String endpoint, Object requestBody) {
        try {
            if (gameId != null) MDC.put("gameId", String.valueOf(gameId));
            AI_LOG.info("[AI-REQ] player={} endpoint={} body={}", playerId, endpoint, requestBody);
        } finally {
            MDC.remove("gameId");
        }
    }

    /**
     * 记录 AI 响应（模型输出）
     */
    public static void aiResponse(Long gameId, Long playerId, String endpoint, Object responseBody) {
        try {
            if (gameId != null) MDC.put("gameId", String.valueOf(gameId));
            AI_LOG.info("[AI-RSP] player={} endpoint={} body={}", playerId, endpoint, responseBody);
        } finally {
            MDC.remove("gameId");
        }
    }

    /**
     * 记录 AI 降级（服务不可用时的回退）
     */
    public static void aiFallback(Long gameId, Long playerId, String endpoint, String reason) {
        try {
            if (gameId != null) MDC.put("gameId", String.valueOf(gameId));
            AI_LOG.warn("[AI-FALLBACK] player={} endpoint={} reason={}", playerId, endpoint, reason);
        } finally {
            MDC.remove("gameId");
        }
    }

    /**
     * 记录 AI 错误
     */
    public static void aiError(Long gameId, Long playerId, String endpoint, Exception e) {
        try {
            if (gameId != null) MDC.put("gameId", String.valueOf(gameId));
            AI_LOG.error("[AI-ERR] player={} endpoint={} error={}", playerId, endpoint, e.getMessage(), e);
        } finally {
            MDC.remove("gameId");
        }
    }
}
