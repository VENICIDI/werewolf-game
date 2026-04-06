# AI Speech Service (语音服务)

狼人杀项目的语音转文字 (STT) + 文字转语音 (TTS) 服务。**全部离线运行，不依赖任何外部在线服务。**

## 技术选型

| 功能 | HuggingFace 模型 | 架构 | 大小 | 说明 |
|------|-------------------|------|------|------|
| STT (语音转文字) | `openai/whisper-small` | Whisper | ~480MB | 中文识别效果好，CPU 3-5s/条 |
| TTS (文字转语音) | `facebook/mms-tts-zho` | VITS | ~75MB | 中文语音合成，CPU <1s/条 |

### 为什么选这两个？

- **Whisper Small**: OpenAI 出品，480MB 体积适中，中文识别准确率高，CPU 上 3-5 秒处理一条语音，适合狼人杀短语音场景
- **MMS-TTS-ZHO**: Facebook MMS (大规模多语言语音) 项目的中文 TTS 模型，基于 VITS 架构，仅 75MB，推理快（CPU <1秒），通过 HuggingFace Transformers 直接加载

两个模型首次启动时自动从 HuggingFace 下载到 `models/` 目录，之后**完全离线运行**。

## 快速开始

```bash
cd packages/ai-speech

# 1. 创建虚拟环境
python3 -m venv venv
source venv/bin/activate

# 2. 安装依赖
pip install -r requirements.txt

# 3. 配置环境变量
cp .env.example .env
# 编辑 .env (默认配置即可直接使用)

# 4. 启动服务
python main.py
# 服务运行在 http://localhost:8001
# API 文档: http://localhost:8001/docs
```

> 首次启动会自动从 HuggingFace 下载模型 (Whisper ~480MB + VITS ~75MB)，之后缓存到本地 `models/` 目录，无需再次下载。

## API 接口

### STT - 语音转文字

#### `POST /api/stt/transcribe`

上传音频文件，返回识别出的中文文字。

```bash
curl -X POST http://localhost:8001/api/stt/transcribe \
  -F "audio=@speech.wav" \
  -F "language=zh"

# 响应
{
  "success": true,
  "data": {
    "text": "我觉得三号很可疑，大家集中投三号",
    "language": "zh",
    "duration": 2.35,
    "segments": [
      {"start": 0.0, "end": 3.5, "text": "我觉得三号很可疑，大家集中投三号"}
    ]
  }
}
```

### TTS - 文字转语音

#### `POST /api/tts/synthesize`

文字合成语音，返回 WAV 音频文件。

```bash
curl -X POST http://localhost:8001/api/tts/synthesize \
  -H "Content-Type: application/json" \
  -d '{"text": "我觉得三号很可疑", "speaking_rate": 1.0}' \
  --output speech.wav
```

#### `POST /api/tts/synthesize/json`

文字合成语音，返回 base64 编码的 WAV 音频数据（适合前端直接播放）。

```bash
curl -X POST http://localhost:8001/api/tts/synthesize/json \
  -H "Content-Type: application/json" \
  -d '{"text": "大家听我说"}'

# 响应
{
  "success": true,
  "data": {
    "audio_base64": "UklGRgAA...",
    "content_type": "audio/wav",
    "text": "大家听我说",
    "sample_rate": 16000,
    "size": 80000
  }
}
```

#### `GET /api/stt/info` / `GET /api/tts/info`

获取服务状态信息。

#### `GET /api/health`

健康检查。

## 配置说明

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `SERVICE_PORT` | 8001 | 服务端口 |
| `WHISPER_MODEL_SIZE` | small | Whisper 模型: tiny/base/small/medium |
| `WHISPER_DEVICE` | cpu | STT 运行设备: cpu/cuda |
| `WHISPER_CACHE_DIR` | ./models | STT 模型缓存目录 |
| `TTS_MODEL_ID` | facebook/mms-tts-zho | TTS 模型 ID |
| `TTS_DEVICE` | cpu | TTS 运行设备: cpu/cuda |
| `TTS_CACHE_DIR` | ./models | TTS 模型缓存目录 |
| `TTS_SPEAKING_RATE` | 1.0 | 默认语速 (0.5~2.0) |
| `TTS_OUTPUT_DIR` | ./audio_output | 音频输出目录 |

## 目录结构

```
packages/ai-speech/
├── main.py                 # FastAPI 入口 (端口 8001)
├── requirements.txt        # Python 依赖
├── .env.example            # 环境变量示例
├── .gitignore
├── README.md
├── services/
│   ├── stt_service.py      # Whisper STT 服务 (openai/whisper-small)
│   └── tts_service.py      # VITS TTS 服务 (facebook/mms-tts-zho)
├── routers/
│   ├── stt_router.py       # STT API 路由
│   ├── tts_router.py       # TTS API 路由
│   └── health_router.py    # 健康检查路由
├── models/                 # 模型缓存 (自动下载, ~555MB)
└── audio_output/           # TTS 音频输出 (临时文件)
```
