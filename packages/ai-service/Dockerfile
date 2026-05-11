# ============================================================
# 狼人杀 ai-service Dockerfile（FastAPI + LangChain + ChromaDB）
# ============================================================

FROM python:3.11-slim

WORKDIR /app

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

# 系统依赖（sentence-transformers / torch 需要 git、build 工具）
RUN apt-get update && apt-get install -y --no-install-recommends \
      build-essential \
      git \
      curl \
      ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# 先装依赖（缓存层）
COPY requirements.txt ./
# pip 26+ 默认不再带 pkg_resources，老库的 setup.py 容易挂；锁定 pip 并显式装 setuptools
RUN pip install -i https://mirrors.tencent.com/pypi/simple/ --upgrade "pip<25" "setuptools>=68" wheel \
 && pip install -i https://mirrors.tencent.com/pypi/simple/ -r requirements.txt

# 拷业务代码
COPY . .

# 默认端口（与 main.py SERVICE_PORT 默认一致）
EXPOSE 8000

# 云托管建议的环境变量：
#   OPENAI_API_KEY=xxx
#   OPENAI_BASE_URL=https://api.deepseek.com
#   OPENAI_MODEL=deepseek-v4-flash
#   SERVICE_HOST=0.0.0.0
#   SERVICE_PORT=8000
#   JAVA_BACKEND_URL=http://werewolf-backend:8088
#   CHROMA_PERSIST_DIR=/app/chroma_db     # 如需持久化建议挂 CFS
#   USE_LOCAL_EMBEDDING=true
#   LOCAL_EMBEDDING_MODEL=BAAI/bge-small-zh-v1.5

CMD ["python", "main.py"]
