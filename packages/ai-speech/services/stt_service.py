"""
STT 服务 - 语音转文字

使用 HuggingFace 上的 openai/whisper-small 模型
- 模型大小: ~480MB
- 支持中文识别
- 首次运行会自动从 HuggingFace 下载模型
"""

import os
import logging
import tempfile
import time
from typing import Optional

import whisper
import numpy as np

logger = logging.getLogger(__name__)


class STTService:
    """语音转文字服务 (基于 Whisper)"""

    def __init__(self):
        self.model = None
        self.model_size = os.getenv("WHISPER_MODEL_SIZE", "small")
        self.device = os.getenv("WHISPER_DEVICE", "cpu")
        self.cache_dir = os.getenv("WHISPER_CACHE_DIR", "./models")

        os.makedirs(self.cache_dir, exist_ok=True)

    def load_model(self):
        """
        加载 Whisper 模型

        模型会从 HuggingFace 自动下载并缓存到本地:
          https://huggingface.co/openai/whisper-small
        """
        logger.info(
            f"Loading Whisper model: {self.model_size} "
            f"(device: {self.device}, cache: {self.cache_dir})"
        )
        start = time.time()

        self.model = whisper.load_model(
            self.model_size,
            device=self.device,
            download_root=self.cache_dir
        )

        elapsed = time.time() - start
        logger.info(f"Whisper model loaded in {elapsed:.1f}s")

    def is_available(self) -> bool:
        return self.model is not None

    def transcribe(
        self,
        audio_path: str,
        language: str = "zh",
        initial_prompt: Optional[str] = None,
    ) -> dict:
        """
        将音频文件转为文字

        Args:
            audio_path: 音频文件路径 (支持 mp3/wav/m4a/webm 等)
            language: 语言代码，默认 "zh" (中文)
            initial_prompt: 初始提示词，可提高特定领域识别准确率

        Returns:
            {
                "text": "识别出的文字",
                "language": "zh",
                "duration": 3.5,
                "segments": [...]
            }
        """
        if not self.is_available():
            raise RuntimeError("Whisper model not loaded")

        logger.info(f"Transcribing: {audio_path} (lang={language})")
        start = time.time()

        # Whisper 转录参数
        options = {
            "language": language,
            "task": "transcribe",
            "fp16": False,  # CPU 模式下需关闭
        }

        # 为中文设置初始提示词，提升口语识别准确率
        if initial_prompt:
            options["initial_prompt"] = initial_prompt
        elif language == "zh":
            options["initial_prompt"] = "以下是普通话的语音内容。"

        result = self.model.transcribe(audio_path, **options)

        elapsed = time.time() - start
        text = result.get("text", "").strip()

        logger.info(f"Transcription done in {elapsed:.1f}s: {text[:50]}...")

        # 构建分段信息
        segments = []
        for seg in result.get("segments", []):
            segments.append({
                "start": round(seg["start"], 2),
                "end": round(seg["end"], 2),
                "text": seg["text"].strip(),
            })

        return {
            "text": text,
            "language": result.get("language", language),
            "duration": round(elapsed, 2),
            "segments": segments,
        }

    def transcribe_bytes(
        self,
        audio_bytes: bytes,
        suffix: str = ".wav",
        language: str = "zh",
        initial_prompt: Optional[str] = None,
    ) -> dict:
        """
        从字节流转录音频

        Args:
            audio_bytes: 音频二进制数据
            suffix: 文件后缀 (用于 ffmpeg 判断格式)
            language: 语言代码
            initial_prompt: 初始提示词

        Returns:
            同 transcribe()
        """
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=True) as tmp:
            tmp.write(audio_bytes)
            tmp.flush()
            return self.transcribe(
                tmp.name,
                language=language,
                initial_prompt=initial_prompt,
            )
