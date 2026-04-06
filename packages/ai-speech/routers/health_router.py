"""
健康检查路由
"""

from fastapi import APIRouter, Request

router = APIRouter()


@router.get("/health")
async def health_check(request: Request):
    """服务健康检查"""
    stt_service = getattr(request.app.state, "stt_service", None)
    tts_service = getattr(request.app.state, "tts_service", None)

    stt_ok = stt_service is not None and stt_service.is_available()
    tts_ok = tts_service is not None and tts_service.is_available()

    status = "healthy" if (stt_ok and tts_ok) else "degraded"

    return {
        "status": status,
        "services": {
            "stt": {
                "status": "ok" if stt_ok else "unavailable",
                "model": f"whisper-{stt_service.model_size}" if stt_service else "N/A",
            },
            "tts": {
                "status": "ok" if tts_ok else "unavailable",
                "model": tts_service.model_id if tts_service else "N/A",
            },
        }
    }
