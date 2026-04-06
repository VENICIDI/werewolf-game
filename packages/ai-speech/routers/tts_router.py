"""
TTS 路由 - 文字转语音 API

POST /api/tts/synthesize       文字合成语音，返回 WAV 音频文件
POST /api/tts/synthesize/json  文字合成语音，返回 base64 音频
GET  /api/tts/info             获取 TTS 服务信息
"""

import os
import logging
import base64
from typing import Optional

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)

router = APIRouter()


class TTSRequest(BaseModel):
    """TTS 请求体"""
    text: str = Field(
        ..., min_length=1, max_length=500,
        description="要合成的中文文字 (最长500字)"
    )
    speaking_rate: Optional[float] = Field(
        default=None, ge=0.5, le=2.0,
        description="语速倍率 (0.5=慢速, 1.0=正常, 2.0=快速)"
    )


@router.post("/synthesize")
async def synthesize_to_file(request: Request, body: TTSRequest):
    """
    文字转语音 - 返回 WAV 音频文件

    将中文文字合成为语音，返回 WAV 音频文件下载。
    使用本地 VITS 模型 (facebook/mms-tts-zho)，完全离线运行。

    参数:
    - text: 要合成的中文文字 (1-500字)
    - speaking_rate: 语速倍率 (0.5~2.0, 默认1.0)
    """
    tts_service = request.app.state.tts_service
    if tts_service is None or not tts_service.is_available():
        raise HTTPException(status_code=503, detail="TTS 服务不可用，模型未加载")

    try:
        result = tts_service.synthesize(
            text=body.text,
            speaking_rate=body.speaking_rate,
        )

        audio_path = result["audio_path"]
        if not os.path.exists(audio_path):
            raise HTTPException(status_code=500, detail="音频文件生成失败")

        return FileResponse(
            path=audio_path,
            media_type="audio/wav",
            filename=os.path.basename(audio_path),
            headers={
                "X-TTS-Duration": str(result["duration_seconds"]),
                "X-TTS-SampleRate": str(result["sample_rate"]),
                "X-TTS-SynthesisTime": str(result["synthesis_time"]),
            }
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"TTS synthesis failed: {e}")
        raise HTTPException(status_code=500, detail=f"语音合成失败: {str(e)}")


@router.post("/synthesize/json")
async def synthesize_to_json(request: Request, body: TTSRequest):
    """
    文字转语音 - 返回 JSON (含 base64 音频)

    将中文文字合成为语音，返回 base64 编码的 WAV 音频数据。
    适合前端直接播放。

    Returns:
        - audio_base64: base64 编码的 WAV 音频
        - content_type: audio/wav
        - text: 原始文字
        - sample_rate: 采样率
        - size: 音频数据大小(字节)
    """
    tts_service = request.app.state.tts_service
    if tts_service is None or not tts_service.is_available():
        raise HTTPException(status_code=503, detail="TTS 服务不可用，模型未加载")

    try:
        audio_bytes, sample_rate = tts_service.synthesize_bytes(
            text=body.text,
            speaking_rate=body.speaking_rate,
        )

        audio_base64 = base64.b64encode(audio_bytes).decode("utf-8")

        return {
            "success": True,
            "data": {
                "audio_base64": audio_base64,
                "content_type": "audio/wav",
                "text": body.text,
                "sample_rate": sample_rate,
                "size": len(audio_bytes),
            }
        }

    except Exception as e:
        logger.error(f"TTS synthesis failed: {e}")
        raise HTTPException(status_code=500, detail=f"语音合成失败: {str(e)}")


@router.get("/info")
async def tts_info(request: Request):
    """获取 TTS 服务信息"""
    tts_service = request.app.state.tts_service
    available = tts_service is not None and tts_service.is_available()

    return {
        "service": "VITS TTS (facebook/mms-tts-zho)",
        "available": available,
        "model": tts_service.model_id if tts_service else "N/A",
        "device": tts_service.device if tts_service else "N/A",
        "offline": True,
        "output_format": "wav",
        "max_text_length": 500,
        "speaking_rate_range": "0.5 ~ 2.0",
    }
