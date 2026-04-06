"""
STT 路由 - 语音转文字 API

POST /api/stt/transcribe  上传音频文件，返回识别文字
"""

import logging
from typing import Optional

from fastapi import APIRouter, UploadFile, File, Form, HTTPException, Request

logger = logging.getLogger(__name__)

router = APIRouter()


@router.post("/transcribe")
async def transcribe_audio(
    request: Request,
    audio: UploadFile = File(..., description="音频文件 (支持 mp3/wav/m4a/webm/ogg)"),
    language: str = Form(default="zh", description="语言代码，默认 zh (中文)"),
    initial_prompt: Optional[str] = Form(default=None, description="初始提示词，可提高识别准确率"),
):
    """
    语音转文字

    上传音频文件，返回识别出的中文文字。

    支持格式: mp3, wav, m4a, webm, ogg, flac
    推荐采样率: 16kHz
    最大文件大小: 25MB

    Returns:
        - text: 识别出的完整文字
        - language: 检测到的语言
        - duration: 处理耗时(秒)
        - segments: 分段信息 [{start, end, text}]
    """
    stt_service = request.app.state.stt_service
    if stt_service is None or not stt_service.is_available():
        raise HTTPException(status_code=503, detail="STT 服务不可用，Whisper 模型未加载")

    # 检查文件大小 (25MB 限制)
    content = await audio.read()
    if len(content) > 25 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="音频文件不能超过 25MB")

    if len(content) == 0:
        raise HTTPException(status_code=400, detail="音频文件为空")

    # 提取文件后缀
    filename = audio.filename or "audio.wav"
    suffix = "." + filename.rsplit(".", 1)[-1] if "." in filename else ".wav"
    allowed_suffixes = {".mp3", ".wav", ".m4a", ".webm", ".ogg", ".flac", ".aac", ".wma"}
    if suffix.lower() not in allowed_suffixes:
        raise HTTPException(
            status_code=400,
            detail=f"不支持的音频格式: {suffix}，支持: {', '.join(allowed_suffixes)}"
        )

    try:
        result = stt_service.transcribe_bytes(
            audio_bytes=content,
            suffix=suffix,
            language=language,
            initial_prompt=initial_prompt,
        )
        return {"success": True, "data": result}

    except Exception as e:
        logger.error(f"Transcription failed: {e}")
        raise HTTPException(status_code=500, detail=f"语音识别失败: {str(e)}")


@router.get("/info")
async def stt_info(request: Request):
    """获取 STT 服务信息"""
    stt_service = request.app.state.stt_service
    available = stt_service is not None and stt_service.is_available()

    return {
        "service": "Whisper STT",
        "model": f"openai/whisper-{stt_service.model_size}" if stt_service else "N/A",
        "available": available,
        "supported_languages": ["zh", "en", "ja", "ko"],
        "supported_formats": ["mp3", "wav", "m4a", "webm", "ogg", "flac"],
        "max_file_size": "25MB",
    }
