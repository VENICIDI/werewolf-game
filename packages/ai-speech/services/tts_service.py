"""
TTS 服务 - 文字转语音

使用 edge-tts (微软 Edge 免费 TTS)
- 无需下载模型，免费使用
- 高质量中文语音合成
- 多种中文语音可选
- 需要网络连接
"""

import os
import io
import logging
import uuid
import time
import asyncio
from typing import Optional

import edge_tts

logger = logging.getLogger(__name__)

# 默认中文语音 (微软 Edge TTS)
# 可选: zh-CN-XiaoxiaoNeural (女声), zh-CN-YunxiNeural (男声),
#       zh-CN-XiaoyiNeural (女声), zh-CN-YunjianNeural (男声)
DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural"
DEFAULT_SAMPLE_RATE = 24000


class TTSService:
    """
    文字转语音服务

    基于 edge-tts (微软 Edge 免费 TTS)
    支持多种高质量中文语音
    """

    def __init__(self):
        self.model_id = os.getenv("TTS_VOICE", DEFAULT_VOICE)
        self.output_dir = os.getenv("TTS_OUTPUT_DIR", "./audio_output")
        self.speaking_rate = float(os.getenv("TTS_SPEAKING_RATE", "1.0"))
        self._available = False

        os.makedirs(self.output_dir, exist_ok=True)

    def load_model(self):
        """
        初始化 TTS 服务

        edge-tts 无需下载模型，此方法仅做可用性验证。
        """
        logger.info(f"Initializing edge-tts service (voice: {self.model_id})")
        start = time.time()

        # 验证 edge-tts 可用性（简单测试）
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                self._available = True
            else:
                loop.run_until_complete(self._test_availability())
        except RuntimeError:
            # 没有事件循环时创建一个
            asyncio.run(self._test_availability())

        elapsed = time.time() - start
        if self._available:
            logger.info(f"edge-tts service ready in {elapsed:.1f}s (voice: {self.model_id})")
        else:
            logger.warning("edge-tts service init failed")

    async def _test_availability(self):
        """测试 edge-tts 可用性"""
        try:
            communicate = edge_tts.Communicate("测试", self.model_id)
            async for _ in communicate.stream():
                break
            self._available = True
        except Exception as e:
            logger.error(f"edge-tts availability test failed: {e}")
            self._available = False

    def is_available(self) -> bool:
        return self._available

    def _get_rate_str(self, rate: float) -> str:
        """将语速倍率转为 edge-tts 格式 (如 +20%, -10%)"""
        if rate == 1.0:
            return "+0%"
        percentage = int((rate - 1.0) * 100)
        return f"{percentage:+d}%"

    def synthesize(
        self,
        text: str,
        speaking_rate: Optional[float] = None,
        output_path: Optional[str] = None,
    ) -> dict:
        """
        将文字合成为语音文件 (MP3)

        Args:
            text: 要合成的中文文字
            speaking_rate: 语速倍率 (0.5~2.0, 默认 1.0)
            output_path: 输出文件路径 (不指定则自动生成)

        Returns:
            {
                "audio_path": "/path/to/output.mp3",
                "text": "原始文字",
                "sample_rate": 24000,
                "duration_seconds": 2.5,
                "synthesis_time": 0.8,
                "file_size": 80000
            }
        """
        if not self.is_available():
            raise RuntimeError("TTS service not available")

        rate = speaking_rate or self.speaking_rate
        rate_str = self._get_rate_str(rate)

        logger.info(f"TTS synthesize: text='{text[:30]}...', rate={rate_str}")
        start = time.time()

        # 生成输出路径
        if not output_path:
            filename = f"{uuid.uuid4().hex[:12]}.mp3"
            output_path = os.path.join(self.output_dir, filename)

        # 使用 edge-tts 合成
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                import concurrent.futures
                with concurrent.futures.ThreadPoolExecutor() as pool:
                    future = pool.submit(asyncio.run, self._synthesize_async(text, rate_str, output_path))
                    future.result()
            else:
                loop.run_until_complete(self._synthesize_async(text, rate_str, output_path))
        except RuntimeError:
            asyncio.run(self._synthesize_async(text, rate_str, output_path))

        elapsed = time.time() - start
        file_size = os.path.getsize(output_path)
        # 粗略估算时长 (MP3 ~16kbps for speech)
        duration_seconds = file_size / 4000.0

        logger.info(
            f"TTS done in {elapsed:.1f}s, "
            f"~{duration_seconds:.1f}s audio, "
            f"file: {output_path} ({file_size} bytes)"
        )

        return {
            "audio_path": output_path,
            "text": text,
            "sample_rate": DEFAULT_SAMPLE_RATE,
            "duration_seconds": round(duration_seconds, 2),
            "synthesis_time": round(elapsed, 2),
            "file_size": file_size,
        }

    async def _synthesize_async(self, text: str, rate: str, output_path: str):
        """异步合成语音"""
        communicate = edge_tts.Communicate(text, self.model_id, rate=rate)
        await communicate.save(output_path)

    def synthesize_bytes(
        self,
        text: str,
        speaking_rate: Optional[float] = None,
    ) -> tuple:
        """
        将文字合成为音频字节流 (MP3 格式, 不写入文件)

        Returns:
            (mp3_bytes, sample_rate)
        """
        if not self.is_available():
            raise RuntimeError("TTS service not available")

        rate = speaking_rate or self.speaking_rate
        rate_str = self._get_rate_str(rate)

        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                import concurrent.futures
                with concurrent.futures.ThreadPoolExecutor() as pool:
                    future = pool.submit(asyncio.run, self._synthesize_bytes_async(text, rate_str))
                    result = future.result()
            else:
                result = loop.run_until_complete(self._synthesize_bytes_async(text, rate_str))
        except RuntimeError:
            result = asyncio.run(self._synthesize_bytes_async(text, rate_str))

        return result, DEFAULT_SAMPLE_RATE

    async def _synthesize_bytes_async(self, text: str, rate: str) -> bytes:
        """异步合成为字节流"""
        communicate = edge_tts.Communicate(text, self.model_id, rate=rate)
        buf = io.BytesIO()
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                buf.write(chunk["data"])
        return buf.getvalue()

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
