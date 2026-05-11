# ============================================================
# 狼人杀 ai-speech Dockerfile（STT: Whisper-small + TTS: edge-tts）
# 注意：Whisper 模型约 480MB，容器首次启动会自动下载到 /root/.cache
#       生产建议挂 CFS 或在构建时预下载到镜像层
# ============================================================

FROM python:3.11-slim

WORKDIR /app

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    HF_HOME=/app/.hf_cache

# 系统依赖：ffmpeg 是 whisper 解码必备；libsndfile 是 soundfile 需要
RUN apt-get update && apt-get install -y --no-install-recommends \
      build-essential \
      ffmpeg \
      libsndfile1 \
      curl \
      ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# 依赖层
COPY requirements.txt ./
# 注意：pip 26+ 默认不再带 pkg_resources，openai-whisper 的 setup.py 还在用，
# 所以必须显式升级 setuptools/wheel；同时锁定 pip 在已知兼容版本
RUN pip install -i https://mirrors.tencent.com/pypi/simple/ --upgrade "pip<25" "setuptools>=68" wheel \
 && pip install -i https://mirrors.tencent.com/pypi/simple/ -r requirements.txt

# 业务代码
COPY . .

EXPOSE 8001

# 云托管建议的环境变量：
#   SERVICE_HOST=0.0.0.0
#   SERVICE_PORT=8001
#   LOG_LEVEL=INFO
#   TTS_VOICE=zh-CN-XiaoxiaoNeural            # edge-tts 默认声音
#   STT_MODEL=small                           # whisper 模型规格
# 注意：edge-tts 需要容器能访问公网（微软 TTS endpoint）

CMD ["python", "main.py"]
