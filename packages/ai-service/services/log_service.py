"""
AI 服务日志配置模块

功能:
- 按 game_id 自动创建独立日志文件
- 记录 LLM 输入输出（prompt + response）
- 所有日志同时输出到控制台 + 主日志文件 + 游戏专属文件
"""
import os
import logging
import json
from pathlib import Path
from logging.handlers import RotatingFileHandler
from typing import Optional, Any

# 日志根目录（与 Java 后端共用）
LOG_DIR = os.getenv("LOG_DIR", os.path.join(os.path.dirname(os.path.dirname(__file__)), "..", "logs"))
AI_LOG_DIR = os.path.join(LOG_DIR, "ai-service")
AI_GAME_LOG_DIR = os.path.join(AI_LOG_DIR, "games")
AI_LLM_LOG_DIR = os.path.join(AI_LOG_DIR, "llm")

# 确保日志目录存在
for d in [AI_LOG_DIR, AI_GAME_LOG_DIR, AI_LLM_LOG_DIR]:
    Path(d).mkdir(parents=True, exist_ok=True)

# 日志格式
_FORMATTER = logging.Formatter(
    "%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    datefmt="%H:%M:%S"
)
_LLM_FORMATTER = logging.Formatter(
    "%(asctime)s %(message)s",
    datefmt="%H:%M:%S"
)

# 缓存已创建的 game logger，避免重复创建
_game_loggers: dict = {}
_llm_loggers: dict = {}


def setup_main_logging():
    """配置主日志（控制台 + ai-service.log）"""
    level = getattr(logging, os.getenv("LOG_LEVEL", "INFO"))

    # 主日志文件
    main_handler = RotatingFileHandler(
        os.path.join(AI_LOG_DIR, "ai-service.log"),
        maxBytes=20 * 1024 * 1024,  # 20MB
        backupCount=3,
        encoding="utf-8"
    )
    main_handler.setFormatter(_FORMATTER)

    # 控制台
    console_handler = logging.StreamHandler()
    console_handler.setFormatter(_FORMATTER)

    # 配置根 logger
    root = logging.getLogger()
    root.setLevel(level)
    # 移除默认 handler，避免重复
    root.handlers.clear()
    root.addHandler(console_handler)
    root.addHandler(main_handler)

    return root


def get_game_logger(game_id: str) -> logging.Logger:
    """
    获取按 game_id 分文件的 logger
    日志写入: logs/ai-service/games/game-{game_id}.log
    """
    if game_id in _game_loggers:
        return _game_loggers[game_id]

    logger = logging.getLogger(f"game.{game_id}")
    logger.setLevel(logging.DEBUG)
    logger.propagate = True  # 同时输出到主日志

    handler = RotatingFileHandler(
        os.path.join(AI_GAME_LOG_DIR, f"game-{game_id}.log"),
        maxBytes=10 * 1024 * 1024,  # 10MB
        backupCount=2,
        encoding="utf-8"
    )
    handler.setFormatter(_FORMATTER)
    logger.addHandler(handler)

    _game_loggers[game_id] = logger
    return logger


def get_llm_logger(game_id: str) -> logging.Logger:
    """
    获取 LLM 对话日志 logger（记录完整的 prompt 和 response）
    日志写入: logs/ai-service/llm/game-{game_id}-llm.log
    """
    if game_id in _llm_loggers:
        return _llm_loggers[game_id]

    logger = logging.getLogger(f"llm.{game_id}")
    logger.setLevel(logging.DEBUG)
    logger.propagate = False  # 不传播到主日志（内容太长）

    handler = RotatingFileHandler(
        os.path.join(AI_LLM_LOG_DIR, f"game-{game_id}-llm.log"),
        maxBytes=20 * 1024 * 1024,  # 20MB
        backupCount=2,
        encoding="utf-8"
    )
    handler.setFormatter(_LLM_FORMATTER)
    logger.addHandler(handler)

    _llm_loggers[game_id] = logger
    return logger


def _safe_json(obj: Any, max_len: int = 2000) -> str:
    """安全序列化对象，截断过长内容"""
    try:
        s = json.dumps(obj, ensure_ascii=False, default=str)
    except Exception:
        s = str(obj)
    if len(s) > max_len:
        return s[:max_len] + f"... (truncated, total {len(s)} chars)"
    return s


def log_llm_call(game_id: str, player_id: int, action: str,
                 prompt: str, response: str, duration_ms: Optional[float] = None):
    """
    记录一次完整的 LLM 调用

    Args:
        game_id: 游戏ID
        player_id: 玩家ID
        action: 动作类型 (speak/night/vote)
        prompt: 发送给模型的完整 prompt
        response: 模型返回的完整内容
        duration_ms: 调用耗时（毫秒）
    """
    llm_log = get_llm_logger(game_id)
    game_log = get_game_logger(game_id)

    duration_str = f" ({duration_ms:.0f}ms)" if duration_ms else ""

    # LLM 日志：记录完整内容
    llm_log.info(
        f"\n{'='*60}\n"
        f"[LLM CALL] player={player_id} action={action}{duration_str}\n"
        f"{'- '*30}\n"
        f"PROMPT:\n{prompt}\n"
        f"{'- '*30}\n"
        f"RESPONSE:\n{response}\n"
        f"{'='*60}"
    )

    # 游戏日志：记录摘要
    prompt_summary = prompt[:200] + "..." if len(prompt) > 200 else prompt
    response_summary = response[:200] + "..." if len(response) > 200 else response
    game_log.info(
        f"[LLM] player={player_id} action={action}{duration_str} "
        f"prompt_len={len(prompt)} response_len={len(response)}"
    )
