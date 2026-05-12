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
# ============ 关键：解决 openai-whisper 20231117 的 pkg_resources 报错 ============
# 根因：whisper 的 setup.py 第 5 行 `import pkg_resources`，
#       而 setuptools 80+ 已彻底移除 pkg_resources。
# 上一版只在主环境锁 setuptools<80，但 pip 在 PEP 517 隔离 build env
# (/tmp/pip-build-env-*) 里会按 whisper 的 pyproject.toml 重新装最新 setuptools，
# 主环境的版本锁根本管不到隔离环境，所以仍然 ModuleNotFoundError。
# 正解：先把主环境的 setuptools 锁到 <80，再用 --no-build-isolation 让 whisper
#       直接复用主环境的 setuptools，不再创建隔离 env。
# 代价：必须先把 whisper 的全部构建+运行时依赖塞进主环境，--no-build-isolation
#       才能拿到它需要的 numba/numpy/torch/tqdm/more-itertools/tiktoken。
ARG PIP_INDEX=https://mirrors.tencent.com/pypi/simple/

# 1) 主环境锁版本：setuptools<80 保留 pkg_resources
RUN pip install -i ${PIP_INDEX} "setuptools>=68,<80" "wheel<0.46"

# 2) 关键：单独装 CPU 版 torch（云托管无 GPU），避免被 whisper/transformers 拖来 2GB+ CUDA 工具链
RUN pip install --index-url https://download.pytorch.org/whl/cpu \
      "torch==2.2.2+cpu" "torchaudio==2.2.2+cpu"

# 3) 先装非 whisper 的依赖（torch/torchaudio 已是 CPU 版，pip 会跳过）
RUN grep -v -E "^(openai-whisper|#|$)" requirements.txt > /tmp/req-no-whisper.txt \
 && pip install -i ${PIP_INDEX} -r /tmp/req-no-whisper.txt

# 4) 补上 whisper 自己的运行时依赖（避免 --no-build-isolation 时缺包）
RUN pip install -i ${PIP_INDEX} numba tqdm more-itertools tiktoken

# 5) 单独装 whisper，关掉隔离 build env，强制使用主环境 setuptools<80
RUN pip install -i ${PIP_INDEX} --no-build-isolation openai-whisper==20231117

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
