"""
Werewolf AI Speech Service - FastAPI Application
语音转文字 (STT) + 文字转语音 (TTS) 服务

STT: openai/whisper-small (HuggingFace, ~480MB) - 离线中文语音识别
TTS: facebook/mms-tts-zho (HuggingFace, ~75MB) - 离线中文语音合成
"""

import os
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from dotenv import load_dotenv

from services.stt_service import STTService
from services.tts_service import TTSService
from routers import stt_router, tts_router, health_router

load_dotenv()

logging.basicConfig(
    level=getattr(logging, os.getenv("LOG_LEVEL", "INFO")),
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

# 全局服务实例
stt_service: STTService = None
tts_service: TTSService = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    global stt_service, tts_service

    logger.info("Starting AI Speech Service...")

    # 初始化 STT 服务 (Whisper)
    logger.info("Initializing STT service (Whisper)...")
    try:
        stt_service = STTService()
        stt_service.load_model()
        app.state.stt_service = stt_service
        logger.info("STT service initialized successfully")
    except Exception as e:
        logger.error(f"STT service init failed: {e}")
        stt_service = None

    # 初始化 TTS 服务 (VITS)
    logger.info("Initializing TTS service (VITS mms-tts-zho)...")
    try:
        tts_service = TTSService()
        tts_service.load_model()
        app.state.tts_service = tts_service
        logger.info("TTS service initialized successfully")
    except Exception as e:
        logger.error(f"TTS service init failed: {e}")
        tts_service = None

    logger.info("AI Speech Service started successfully!")

    yield

    # 清理
    logger.info("Shutting down AI Speech Service...")
    if tts_service:
        tts_service.cleanup()


app = FastAPI(
    title="Werewolf AI Speech Service",
    description="语音转文字 (STT) + 文字转语音 (TTS) 服务",
    version="1.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由
app.include_router(health_router.router, prefix="/api", tags=["health"])
app.include_router(stt_router.router, prefix="/api/stt", tags=["STT"])
app.include_router(tts_router.router, prefix="/api/tts", tags=["TTS"])


@app.get("/")
async def root():
    return {
        "service": "Werewolf AI Speech Service",
        "version": "1.0.0",
        "status": "running",
        "features": {
            "stt": "openai/whisper-small (语音转文字, 离线)",
            "tts": "facebook/mms-tts-zho (文字转语音, 离线)"
        },
        "docs": "/docs"
    }


if __name__ == "__main__":
    import uvicorn

    host = os.getenv("SERVICE_HOST", "0.0.0.0")
    port = int(os.getenv("SERVICE_PORT", 8001))

    uvicorn.run(app, host=host, port=port)
