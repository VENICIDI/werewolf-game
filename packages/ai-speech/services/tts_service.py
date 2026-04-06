"""
TTS 服务 - 文字转语音

使用 HuggingFace 上的 facebook/mms-tts-zho 模型 (VITS 架构)
- 模型大小: ~75MB
- 完全离线运行，无需联网
- 支持中文合成
- 首次运行自动从 HuggingFace 下载模型并缓存到本地
"""

import os
import io
import logging
import uuid
import time
from typing import Optional

import torch
import numpy as np
import scipy.io.wavfile as wav_io
from transformers import VitsModel, AutoTokenizer

logger = logging.getLogger(__name__)

# HuggingFace 模型 ID
DEFAULT_MODEL_ID = "facebook/mms-tts-zho"


class TTSService:
    """
    文字转语音服务

    基于 facebook/mms-tts-zho (VITS 架构)
    HuggingFace: https://huggingface.co/facebook/mms-tts-zho
    """

    def __init__(self):
        self.model = None
        self.tokenizer = None
        self.model_id = os.getenv("TTS_MODEL_ID", DEFAULT_MODEL_ID)
        self.cache_dir = os.getenv("TTS_CACHE_DIR", "./models")
        self.device = os.getenv("TTS_DEVICE", "cpu")
        self.output_dir = os.getenv("TTS_OUTPUT_DIR", "./audio_output")
        self.speaking_rate = float(os.getenv("TTS_SPEAKING_RATE", "1.0"))

        os.makedirs(self.cache_dir, exist_ok=True)
        os.makedirs(self.output_dir, exist_ok=True)

    def load_model(self):
        """
        加载 VITS TTS 模型

        模型会从 HuggingFace 自动下载并缓存:
          https://huggingface.co/facebook/mms-tts-zho
        缓存后完全离线运行。
        """
        logger.info(f"Loading TTS model: {self.model_id} (device: {self.device})")
        start = time.time()

        self.tokenizer = AutoTokenizer.from_pretrained(
            self.model_id,
            cache_dir=self.cache_dir,
        )
        self.model = VitsModel.from_pretrained(
            self.model_id,
            cache_dir=self.cache_dir,
        )
        self.model = self.model.to(self.device)
        self.model.eval()

        elapsed = time.time() - start
        logger.info(f"TTS model loaded in {elapsed:.1f}s")

    def is_available(self) -> bool:
        return self.model is not None and self.tokenizer is not None

    def synthesize(
        self,
        text: str,
        speaking_rate: Optional[float] = None,
        output_path: Optional[str] = None,
    ) -> dict:
        """
        将文字合成为语音文件 (WAV)

        Args:
            text: 要合成的中文文字
            speaking_rate: 语速倍率 (0.5~2.0, 默认 1.0)
            output_path: 输出文件路径 (不指定则自动生成)

        Returns:
            {
                "audio_path": "/path/to/output.wav",
                "text": "原始文字",
                "sample_rate": 16000,
                "duration_seconds": 2.5,
                "synthesis_time": 0.8,
                "file_size": 80000
            }
        """
        if not self.is_available():
            raise RuntimeError("TTS model not loaded")

        rate = speaking_rate or self.speaking_rate

        logger.info(f"TTS synthesize: text='{text[:30]}...', rate={rate}")
        start = time.time()

        # Tokenize
        inputs = self.tokenizer(text, return_tensors="pt").to(self.device)

        # 生成音频
        with torch.no_grad():
            output = self.model(**inputs, speaking_rate=rate)

        waveform = output.waveform[0].cpu().numpy()
        sample_rate = self.model.config.sampling_rate

        # 生成输出路径
        if not output_path:
            filename = f"{uuid.uuid4().hex[:12]}.wav"
            output_path = os.path.join(self.output_dir, filename)

        # 写入 WAV 文件
        # VITS 输出是 float32 [-1, 1]，转换为 int16 写 WAV
        waveform_int16 = np.int16(waveform * 32767)
        wav_io.write(output_path, sample_rate, waveform_int16)

        elapsed = time.time() - start
        file_size = os.path.getsize(output_path)
        duration_seconds = len(waveform) / sample_rate

        logger.info(
            f"TTS done in {elapsed:.1f}s, "
            f"audio duration: {duration_seconds:.1f}s, "
            f"file: {output_path} ({file_size} bytes)"
        )

        return {
            "audio_path": output_path,
            "text": text,
            "sample_rate": sample_rate,
            "duration_seconds": round(duration_seconds, 2),
            "synthesis_time": round(elapsed, 2),
            "file_size": file_size,
        }

    def synthesize_bytes(
        self,
        text: str,
        speaking_rate: Optional[float] = None,
    ) -> tuple:
        """
        将文字合成为音频字节流 (WAV 格式, 不写入文件)

        Returns:
            (wav_bytes, sample_rate)
        """
        if not self.is_available():
            raise RuntimeError("TTS model not loaded")

        rate = speaking_rate or self.speaking_rate

        inputs = self.tokenizer(text, return_tensors="pt").to(self.device)

        with torch.no_grad():
            output = self.model(**inputs, speaking_rate=rate)

        waveform = output.waveform[0].cpu().numpy()
        sample_rate = self.model.config.sampling_rate

        # 转为 WAV 字节流
        waveform_int16 = np.int16(waveform * 32767)
        buf = io.BytesIO()
        wav_io.write(buf, sample_rate, waveform_int16)
        buf.seek(0)

        return buf.read(), sample_rate

    def cleanup(self):
        """清理临时音频文件"""
        if os.path.exists(self.output_dir):
            for f in os.listdir(self.output_dir):
                filepath = os.path.join(self.output_dir, f)
                try:
                    if os.path.isfile(filepath):
                        os.remove(filepath)
                except Exception as e:
                    logger.warning(f"Failed to cleanup {filepath}: {e}")
            logger.info("TTS output directory cleaned up")
