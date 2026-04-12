#!/bin/bash

# ============================================================
# 狼人杀项目 - 统一运行脚本
# 项目: 基于 RAG 增强的 AI 狼人杀游戏平台
# 支持 Docker 模式和本地模式
# ============================================================

set -e

# -------------------- 颜色定义 --------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# -------------------- 项目路径 --------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
BACKEND_DIR="$PROJECT_ROOT/packages/backend"
FRONTEND_DIR="$PROJECT_ROOT/packages/frontend"
AI_SERVICE_DIR="$PROJECT_ROOT/packages/ai-service"
AI_SPEECH_DIR="$PROJECT_ROOT/packages/ai-speech"
PID_DIR="$PROJECT_ROOT/.pids"
LOG_DIR="$PROJECT_ROOT/logs"
INFRA_CONF="$PROJECT_ROOT/.infra_mode"

# -------------------- Java 环境 --------------------
# macOS + Homebrew 安装的 OpenJDK 可能不在默认 PATH 中
if [ -z "$JAVA_HOME" ] && [ -d "/opt/homebrew/opt/openjdk" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# -------------------- 端口配置 --------------------
BACKEND_PORT=8080
FRONTEND_PORT=10086
AI_SERVICE_PORT=8000
AI_SPEECH_PORT=8001
MYSQL_PORT=3306
REDIS_PORT=6379

# -------------------- 基础设施模式 --------------------
# 读取 setup.sh 保存的模式，默认 local
get_infra_mode() {
    if [ -f "$INFRA_CONF" ]; then
        cat "$INFRA_CONF"
    elif command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
        echo "docker"
    else
        echo "local"
    fi
}

INFRA_MODE="$(get_infra_mode)"

# -------------------- 工具函数 --------------------

print_banner() {
    echo -e "${CYAN}"
    echo "╔══════════════════════════════════════════════╗"
    echo "║     🐺 AI 狼人杀游戏平台 - 运行脚本        ║"
    echo "╚══════════════════════════════════════════════╝"
    echo -e "${NC}"
}

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# 创建必要目录
ensure_dirs() {
    mkdir -p "$PID_DIR"
    mkdir -p "$LOG_DIR"
}

# 检查端口是否被占用
check_port() {
    local port=$1
    if lsof -i :"$port" -sTCP:LISTEN >/dev/null 2>&1; then
        return 0 # 端口被占用
    else
        return 1 # 端口可用
    fi
}

# 等待端口可用（服务启动）
wait_for_port() {
    local port=$1
    local service_name=$2
    local max_wait=${3:-60}
    local count=0

    while ! check_port "$port"; do
        if [ $count -ge $max_wait ]; then
            log_error "$service_name 启动超时 (端口 $port)"
            log_warn "请查看日志: $LOG_DIR/"
            return 1
        fi
        sleep 1
        count=$((count + 1))
    done
    log_info "$service_name 已启动 ✅ (端口 $port, 耗时 ${count}s)"
}

# 根据 PID 文件停止进程
stop_by_pid_file() {
    local pid_file=$1
    local service_name=$2

    if [ -f "$pid_file" ]; then
        local pid
        pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            log_step "停止 $service_name (PID: $pid)..."
            kill "$pid" 2>/dev/null || true
            # 等待进程退出
            local count=0
            while kill -0 "$pid" 2>/dev/null && [ $count -lt 10 ]; do
                sleep 1
                count=$((count + 1))
            done
            # 如果还没退出，强制杀死
            if kill -0 "$pid" 2>/dev/null; then
                kill -9 "$pid" 2>/dev/null || true
            fi
            log_info "$service_name 已停止"
        else
            log_warn "$service_name 进程不存在 (PID: $pid)"
        fi
        rm -f "$pid_file"
    fi
}

# 根据端口杀死进程
kill_by_port() {
    local port=$1
    local service_name=$2

    if check_port "$port"; then
        local pid
        pid=$(lsof -ti :"$port" -sTCP:LISTEN 2>/dev/null)
        if [ -n "$pid" ]; then
            log_step "停止 $service_name (端口: $port, PID: $pid)..."
            kill "$pid" 2>/dev/null || true
            sleep 2
            if kill -0 "$pid" 2>/dev/null; then
                kill -9 "$pid" 2>/dev/null || true
            fi
            log_info "$service_name 已停止"
        fi
    fi
}

# -------------------- 环境检查 --------------------

check_dependencies() {
    log_step "检查运行环境..."
    echo ""

    local mode_label
    if [ "$INFRA_MODE" = "docker" ]; then
        mode_label="Docker 🐳"
    else
        mode_label="本地 💻"
    fi
    echo -e "  基础设施模式: ${CYAN}${mode_label}${NC}"
    echo ""

    # Docker（仅 Docker 模式检查）
    if [ "$INFRA_MODE" = "docker" ]; then
        if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
            log_info "Docker: $(docker --version | head -1)"
        else
            log_error "Docker 未安装或未启动"
        fi
    fi

    # Java
    if command -v java >/dev/null 2>&1; then
        log_info "Java: $(java -version 2>&1 | head -1)"
    else
        log_warn "Java 未安装 (后端需要 Java 17+, 安装: brew install openjdk@17)"
    fi

    # Maven
    if command -v mvn >/dev/null 2>&1; then
        log_info "Maven: $(mvn --version 2>/dev/null | head -1)"
    elif [ -f "$BACKEND_DIR/mvnw" ]; then
        log_info "Maven Wrapper: 已就绪"
    else
        log_warn "Maven 未安装 (安装: brew install maven)"
    fi

    # Node.js
    if command -v node >/dev/null 2>&1; then
        log_info "Node.js: $(node --version)"
    else
        log_warn "Node.js 未安装 (前端需要, 安装: brew install node)"
    fi

    # npm
    if command -v npm >/dev/null 2>&1; then
        log_info "npm: $(npm --version)"
    fi

    # Python
    if command -v python3 >/dev/null 2>&1; then
        log_info "Python: $(python3 --version)"
    elif command -v python >/dev/null 2>&1; then
        log_info "Python: $(python --version)"
    else
        log_warn "Python 未安装 (AI 服务需要)"
    fi

    # 本地模式下检查 MySQL / Redis
    if [ "$INFRA_MODE" = "local" ]; then
        if command -v mysql >/dev/null 2>&1; then
            log_info "MySQL: $(mysql --version 2>/dev/null | head -1)"
        else
            log_warn "MySQL 未安装 (安装: brew install mysql)"
        fi

        if command -v redis-server >/dev/null 2>&1; then
            log_info "Redis: $(redis-server --version 2>/dev/null | head -1)"
        else
            log_warn "Redis 未安装 (安装: brew install redis)"
        fi
    fi

    echo ""
}

# -------------------- 基础设施管理 --------------------

start_infra() {
    if [ "$INFRA_MODE" = "docker" ]; then
        start_infra_docker
    else
        start_infra_local
    fi
}

stop_infra() {
    if [ "$INFRA_MODE" = "docker" ]; then
        stop_infra_docker
    else
        stop_infra_local
    fi
}

# ---- Docker 模式 ----
start_infra_docker() {
    log_step "启动基础设施 [Docker 模式] (MySQL + Redis + ChromaDB)..."
    cd "$PROJECT_ROOT"

    if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
        log_error "Docker 未安装或未启动，请先启动 Docker Desktop"
        log_warn "或运行 ./setup.sh 切换到本地模式"
        return 1
    fi

    if docker compose version >/dev/null 2>&1; then
        docker compose up -d
    else
        docker-compose up -d
    fi

    log_info "等待 MySQL 就绪..."
    local count=0
    while ! docker exec werewolf-mysql mysqladmin ping -h localhost -u root -proot123 --silent 2>/dev/null; do
        if [ $count -ge 60 ]; then
            log_error "MySQL 启动超时"
            return 1
        fi
        sleep 2
        count=$((count + 2))
    done
    log_info "MySQL 已就绪 ✅ (端口: $MYSQL_PORT)"
    log_info "Redis 已就绪 ✅ (端口: $REDIS_PORT)"
    log_info "ChromaDB 已就绪 ✅ (端口: 8000)"
    echo ""
}

stop_infra_docker() {
    log_step "停止基础设施 [Docker 模式]..."
    cd "$PROJECT_ROOT"

    if command -v docker >/dev/null 2>&1; then
        if docker compose version >/dev/null 2>&1; then
            docker compose down 2>/dev/null || true
        else
            docker-compose down 2>/dev/null || true
        fi
        log_info "Docker 基础设施已停止 ✅"
    else
        log_warn "Docker 未安装，跳过"
    fi
}

# ---- 本地模式 ----
start_infra_local() {
    log_step "启动基础设施 [本地模式] (MySQL + Redis)..."

    # 启动 MySQL
    if command -v mysqld >/dev/null 2>&1 || command -v mysql.server >/dev/null 2>&1; then
        if check_port $MYSQL_PORT; then
            log_info "MySQL 已在运行中 ✅ (端口: $MYSQL_PORT)"
        else
            log_info "启动 MySQL..."
            brew services start mysql 2>/dev/null || mysql.server start 2>/dev/null || {
                log_error "MySQL 启动失败，请手动启动"
                return 1
            }
            # 等待就绪
            local count=0
            while ! check_port $MYSQL_PORT; do
                if [ $count -ge 30 ]; then
                    log_error "MySQL 启动超时"
                    return 1
                fi
                sleep 1
                count=$((count + 1))
            done
            log_info "MySQL 已启动 ✅ (端口: $MYSQL_PORT)"
        fi
    else
        if check_port $MYSQL_PORT; then
            log_info "MySQL 已在运行中 ✅ (端口: $MYSQL_PORT)"
        else
            log_error "MySQL 未安装，请运行: brew install mysql"
            return 1
        fi
    fi

    # 启动 Redis
    if command -v redis-server >/dev/null 2>&1; then
        if check_port $REDIS_PORT; then
            log_info "Redis 已在运行中 ✅ (端口: $REDIS_PORT)"
        else
            log_info "启动 Redis..."
            brew services start redis 2>/dev/null || redis-server --daemonize yes 2>/dev/null || {
                log_error "Redis 启动失败"
                return 1
            }
            sleep 1
            log_info "Redis 已启动 ✅ (端口: $REDIS_PORT)"
        fi
    else
        if check_port $REDIS_PORT; then
            log_info "Redis 已在运行中 ✅ (端口: $REDIS_PORT)"
        else
            log_error "Redis 未安装，请运行: brew install redis"
            return 1
        fi
    fi

    echo ""
}

stop_infra_local() {
    log_step "停止基础设施 [本地模式]..."

    # 停止 MySQL
    if command -v mysql.server >/dev/null 2>&1; then
        mysql.server stop 2>/dev/null || true
    fi
    brew services stop mysql 2>/dev/null || true
    log_info "MySQL 已停止"

    # 停止 Redis
    brew services stop redis 2>/dev/null || true
    redis-cli shutdown 2>/dev/null || true
    log_info "Redis 已停止"

    log_info "本地基础设施已停止 ✅"
}

# -------------------- 后端服务 --------------------

start_backend() {
    log_step "启动后端服务 (Spring Boot)..."
    ensure_dirs

    if check_port $BACKEND_PORT; then
        log_warn "后端服务已在运行中 (端口: $BACKEND_PORT)"
        return 0
    fi

    # 检查 Java
    if ! command -v java >/dev/null 2>&1; then
        log_error "Java 未安装，无法启动后端"
        log_error "安装: brew install openjdk@17"
        return 1
    fi

    cd "$BACKEND_DIR"

    # 优先使用 mvnw，否则使用系统 mvn
    local mvn_cmd
    if [ -f "./mvnw" ]; then
        chmod +x ./mvnw
        mvn_cmd="./mvnw"
    elif command -v mvn >/dev/null 2>&1; then
        mvn_cmd="mvn"
    else
        log_error "未找到 Maven，请安装: brew install maven"
        return 1
    fi

    log_info "使用 $mvn_cmd 启动后端..."
    nohup $mvn_cmd spring-boot:run > "$LOG_DIR/backend.log" 2>&1 &
    local pid=$!
    echo "$pid" > "$PID_DIR/backend.pid"

    log_info "后端服务启动中... (PID: $pid)"
    wait_for_port $BACKEND_PORT "后端服务" 300
    echo ""
}

stop_backend() {
    log_step "停止后端服务..."
    stop_by_pid_file "$PID_DIR/backend.pid" "后端服务"
    # 兜底：按端口杀
    kill_by_port $BACKEND_PORT "后端服务"
    echo ""
}

# -------------------- 前端服务 --------------------

start_frontend() {
    log_step "启动前端服务 (Taro H5)..."
    ensure_dirs

    if check_port $FRONTEND_PORT; then
        log_warn "前端服务已在运行中 (端口: $FRONTEND_PORT)"
        return 0
    fi

    if ! command -v node >/dev/null 2>&1; then
        log_error "Node.js 未安装，无法启动前端"
        log_error "安装: brew install node"
        return 1
    fi

    cd "$FRONTEND_DIR"

    # 检查 node_modules
    if [ ! -d "node_modules" ]; then
        log_info "安装前端依赖..."
        npm install
    fi

    nohup npm run dev > "$LOG_DIR/frontend.log" 2>&1 &
    local pid=$!
    echo "$pid" > "$PID_DIR/frontend.pid"

    log_info "前端服务启动中... (PID: $pid)"
    wait_for_port $FRONTEND_PORT "前端服务" 60
    echo ""
}

stop_frontend() {
    log_step "停止前端服务..."
    stop_by_pid_file "$PID_DIR/frontend.pid" "前端服务"
    kill_by_port $FRONTEND_PORT "前端服务"
    echo ""
}

# -------------------- AI 服务 --------------------

start_ai_service() {
    log_step "启动 AI 服务 (FastAPI)..."
    ensure_dirs

    if check_port $AI_SERVICE_PORT; then
        log_warn "AI 服务端口 $AI_SERVICE_PORT 已被占用"
        if [ "$INFRA_MODE" = "docker" ]; then
            log_warn "可能是 ChromaDB 容器占用，请先停止: docker stop werewolf-chroma"
        fi
        return 1
    fi

    cd "$AI_SERVICE_DIR"

    # 检查 .env 文件
    if [ ! -f ".env" ]; then
        if [ -f ".env.example" ]; then
            log_warn ".env 文件不存在，从 .env.example 复制..."
            cp .env.example .env
            log_warn "请编辑 $AI_SERVICE_DIR/.env 填写 OPENAI_API_KEY 等配置"
        fi
    fi

    # 检查 Python
    local python_cmd
    if command -v python3 >/dev/null 2>&1; then
        python_cmd="python3"
    elif command -v python >/dev/null 2>&1; then
        python_cmd="python"
    else
        log_error "Python 未安装"
        return 1
    fi

    if [ ! -d "venv" ]; then
        log_info "创建 Python 虚拟环境..."
        $python_cmd -m venv venv
    fi

    # 激活虚拟环境并安装依赖
    source venv/bin/activate

    if [ -f "requirements.txt" ]; then
        log_info "检查 Python 依赖..."
        pip install -r requirements.txt -q 2>/dev/null || true
    fi

    nohup $python_cmd main.py > "$LOG_DIR/ai-service.log" 2>&1 &
    local pid=$!
    echo "$pid" > "$PID_DIR/ai-service.pid"

    log_info "AI 服务启动中... (PID: $pid)"
    wait_for_port $AI_SERVICE_PORT "AI 服务" 30
    echo ""
}

stop_ai_service() {
    log_step "停止 AI 服务..."
    stop_by_pid_file "$PID_DIR/ai-service.pid" "AI 服务"
    kill_by_port $AI_SERVICE_PORT "AI 服务"
    echo ""
}

# -------------------- AI Speech 服务 --------------------

start_ai_speech() {
    log_step "启动 AI Speech 服务 (语音识别/合成)..."
    ensure_dirs

    if check_port $AI_SPEECH_PORT; then
        log_warn "AI Speech 服务已在运行中 (端口: $AI_SPEECH_PORT)"
        return 0
    fi

    cd "$AI_SPEECH_DIR"

    # 检查 .env 文件
    if [ ! -f ".env" ]; then
        if [ -f ".env.example" ]; then
            log_warn ".env 文件不存在，从 .env.example 复制..."
            cp .env.example .env
        fi
    fi

    # 检查 Python
    local python_cmd
    if command -v python3 >/dev/null 2>&1; then
        python_cmd="python3"
    elif command -v python >/dev/null 2>&1; then
        python_cmd="python"
    else
        log_error "Python 未安装"
        return 1
    fi

    if [ ! -d "venv" ]; then
        log_info "创建 Python 虚拟环境..."
        $python_cmd -m venv venv
    fi

    source venv/bin/activate

    if [ -f "requirements.txt" ]; then
        log_info "检查 Python 依赖..."
        pip install -r requirements.txt -q 2>/dev/null || true
    fi

    nohup $python_cmd main.py > "$LOG_DIR/ai-speech.log" 2>&1 &
    local pid=$!
    echo "$pid" > "$PID_DIR/ai-speech.pid"

    log_info "AI Speech 服务启动中... (PID: $pid)"
    log_warn "首次启动需下载 Whisper 模型 (~480MB)，请耐心等待"
    wait_for_port $AI_SPEECH_PORT "AI Speech 服务" 120
    echo ""
}

stop_ai_speech() {
    log_step "停止 AI Speech 服务..."
    stop_by_pid_file "$PID_DIR/ai-speech.pid" "AI Speech 服务"
    kill_by_port $AI_SPEECH_PORT "AI Speech 服务"
    echo ""
}

# -------------------- 构建命令 --------------------

build_backend() {
    log_step "构建后端..."
    cd "$BACKEND_DIR"

    local mvn_cmd
    if [ -f "./mvnw" ]; then
        chmod +x ./mvnw
        mvn_cmd="./mvnw"
    elif command -v mvn >/dev/null 2>&1; then
        mvn_cmd="mvn"
    else
        log_error "未找到 Maven"
        return 1
    fi

    $mvn_cmd clean package -DskipTests
    log_info "后端构建完成 ✅"
    echo ""
}

build_frontend() {
    log_step "构建前端..."
    cd "$FRONTEND_DIR"

    if [ ! -d "node_modules" ]; then
        npm install
    fi

    npm run build
    log_info "前端构建完成 ✅"
    echo ""
}

# -------------------- 组合命令 --------------------

start_all() {
    print_banner
    check_dependencies
    ensure_dirs

    start_infra
    start_backend
    start_ai_service
    start_ai_speech

    echo -e "${GREEN}╔══════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║          🎉 所有服务已启动！                ║${NC}"
    echo -e "${GREEN}╠══════════════════════════════════════════════╣${NC}"
    echo -e "${GREEN}║  📦 MySQL:       localhost:${MYSQL_PORT}              ║${NC}"
    echo -e "${GREEN}║  📦 Redis:       localhost:${REDIS_PORT}              ║${NC}"
    echo -e "${GREEN}║  🔧 后端 API:    http://localhost:${BACKEND_PORT}       ║${NC}"
    echo -e "${GREEN}║  🤖 AI 服务:     http://localhost:${AI_SERVICE_PORT}       ║${NC}"
    echo -e "${GREEN}║  🎙️ 语音服务:    http://localhost:${AI_SPEECH_PORT}       ║${NC}"
    echo -e "${GREEN}╚══════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${YELLOW}提示: 日志文件位于 $LOG_DIR/ 目录${NC}"
    echo -e "${YELLOW}提示: 若需启动前端，请运行: $0 frontend${NC}"
}

stop_all() {
    print_banner
    log_step "停止所有服务..."
    echo ""

    stop_ai_speech
    stop_ai_service
    stop_frontend
    stop_backend
    stop_infra

    # 清理 PID 文件
    rm -rf "$PID_DIR"

    echo -e "${GREEN}所有服务已停止 ✅${NC}"
}

show_status() {
    print_banner
    local mode_label
    if [ "$INFRA_MODE" = "docker" ]; then
        mode_label="Docker 🐳"
    else
        mode_label="本地 💻"
    fi
    echo -e "${CYAN}基础设施模式: ${mode_label}${NC}"
    echo ""
    echo -e "${CYAN}服务状态:${NC}"
    echo "────────────────────────────────────────"

    # MySQL
    if [ "$INFRA_MODE" = "docker" ]; then
        if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "werewolf-mysql"; then
            echo -e "  MySQL (3306)      ${GREEN}● 运行中${NC}  [Docker]"
        else
            echo -e "  MySQL (3306)      ${RED}○ 已停止${NC}  [Docker]"
        fi
    else
        if check_port $MYSQL_PORT; then
            echo -e "  MySQL (3306)      ${GREEN}● 运行中${NC}  [本地]"
        else
            echo -e "  MySQL (3306)      ${RED}○ 已停止${NC}  [本地]"
        fi
    fi

    # Redis
    if [ "$INFRA_MODE" = "docker" ]; then
        if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "werewolf-redis"; then
            echo -e "  Redis (6379)      ${GREEN}● 运行中${NC}  [Docker]"
        else
            echo -e "  Redis (6379)      ${RED}○ 已停止${NC}  [Docker]"
        fi
    else
        if check_port $REDIS_PORT; then
            echo -e "  Redis (6379)      ${GREEN}● 运行中${NC}  [本地]"
        else
            echo -e "  Redis (6379)      ${RED}○ 已停止${NC}  [本地]"
        fi
    fi

    # ChromaDB (仅 Docker 模式)
    if [ "$INFRA_MODE" = "docker" ]; then
        if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "werewolf-chroma"; then
            echo -e "  ChromaDB (8000)   ${GREEN}● 运行中${NC}  [Docker]"
        else
            echo -e "  ChromaDB (8000)   ${RED}○ 已停止${NC}  [Docker]"
        fi
    fi

    # Backend
    if check_port $BACKEND_PORT; then
        echo -e "  后端 (8080)       ${GREEN}● 运行中${NC}"
    else
        echo -e "  后端 (8080)       ${RED}○ 已停止${NC}"
    fi

    # Frontend
    if check_port $FRONTEND_PORT; then
        echo -e "  前端 (10086)      ${GREEN}● 运行中${NC}"
    else
        echo -e "  前端 (10086)      ${RED}○ 已停止${NC}"
    fi

    # AI Service
    if [ -f "$PID_DIR/ai-service.pid" ] && kill -0 "$(cat "$PID_DIR/ai-service.pid" 2>/dev/null)" 2>/dev/null; then
        echo -e "  AI 服务 (8000)    ${GREEN}● 运行中${NC}"
    elif check_port $AI_SERVICE_PORT; then
        echo -e "  AI 服务 (8000)    ${YELLOW}● 端口占用${NC}"
    else
        echo -e "  AI 服务 (8000)    ${RED}○ 已停止${NC}"
    fi

    # AI Speech Service
    if [ -f "$PID_DIR/ai-speech.pid" ] && kill -0 "$(cat "$PID_DIR/ai-speech.pid" 2>/dev/null)" 2>/dev/null; then
        echo -e "  语音服务 (8001)   ${GREEN}● 运行中${NC}"
    elif check_port $AI_SPEECH_PORT; then
        echo -e "  语音服务 (8001)   ${YELLOW}● 端口占用${NC}"
    else
        echo -e "  语音服务 (8001)   ${RED}○ 已停止${NC}"
    fi

    echo "────────────────────────────────────────"
    echo ""
}

show_logs() {
    local service=$1
    local log_file

    case "$service" in
        backend)
            log_file="$LOG_DIR/backend.log"
            ;;
        frontend)
            log_file="$LOG_DIR/frontend.log"
            ;;
        ai|ai-service)
            log_file="$LOG_DIR/ai-service.log"
            ;;
        speech|ai-speech)
            log_file="$LOG_DIR/ai-speech.log"
            ;;
        *)
            log_error "未知服务: $service"
            echo "  可选: backend, frontend, ai"
            return 1
            ;;
    esac

    if [ -f "$log_file" ]; then
        log_info "查看日志: $log_file (Ctrl+C 退出)"
        echo "────────────────────────────────────────"
        tail -f "$log_file"
    else
        log_error "日志文件不存在: $log_file"
        log_warn "服务可能尚未启动过"
    fi
}

# -------------------- 使用帮助 --------------------

show_help() {
    print_banner
    echo "用法: $0 <command> [options]"
    echo ""
    echo -e "${CYAN}服务管理:${NC}"
    echo "  start           启动核心服务 (基础设施 + 后端 + AI + 语音)"
    echo "  stop            停止所有服务"
    echo "  restart         重启所有服务"
    echo "  status          查看所有服务状态"
    echo ""
    echo -e "${CYAN}单独服务:${NC}"
    echo "  infra           启动基础设施 (MySQL + Redis)"
    echo "  infra:stop      停止基础设施"
    echo "  backend         启动后端服务 (Spring Boot :8080)"
    echo "  backend:stop    停止后端服务"
    echo "  frontend        启动前端服务 (Taro H5 :10086)"
    echo "  frontend:stop   停止前端服务"
    echo "  ai              启动 AI 服务 (FastAPI :8000)"
    echo "  ai:stop         停止 AI 服务"
    echo "  speech          启动语音服务 (STT+TTS :8001)"
    echo "  speech:stop     停止语音服务"
    echo ""
    echo -e "${CYAN}构建:${NC}"
    echo "  build           构建所有 (后端 + 前端)"
    echo "  build:backend   构建后端"
    echo "  build:frontend  构建前端"
    echo ""
    echo -e "${CYAN}其他:${NC}"
    echo "  logs <service>  查看服务日志 (backend / frontend / ai / speech)"
    echo "  check           检查运行环境"
    echo "  help            显示帮助信息"
    echo ""
    echo -e "${CYAN}示例:${NC}"
    echo "  $0 start            # 一键启动核心服务 (后端+AI+语音)"
    echo "  $0 backend          # 仅启动后端"
    echo "  $0 ai               # 仅启动 AI 服务"
    echo "  $0 frontend         # 启动前端 (开发调试时)"
    echo "  $0 logs backend     # 实时查看后端日志"
    echo "  $0 status           # 查看服务状态"
    echo ""
    echo -e "${CYAN}当前基础设施模式:${NC} $([ "$INFRA_MODE" = "docker" ] && echo "Docker 🐳" || echo "本地 💻")"
    echo -e "${YELLOW}切换模式: 重新运行 ./setup.sh${NC}"
    echo ""
}

# -------------------- 主入口 --------------------

main() {
    case "${1:-help}" in
        start)
            start_all
            ;;
        stop)
            stop_all
            ;;
        restart)
            stop_all
            echo ""
            start_all
            ;;
        status)
            show_status
            ;;
        infra)
            start_infra
            ;;
        infra:stop)
            stop_infra
            ;;
        backend)
            ensure_dirs
            start_backend
            ;;
        backend:stop)
            stop_backend
            ;;
        frontend)
            ensure_dirs
            start_frontend
            ;;
        frontend:stop)
            stop_frontend
            ;;
        ai)
            ensure_dirs
            start_ai_service
            ;;
        ai:stop)
            stop_ai_service
            ;;
        speech)
            ensure_dirs
            start_ai_speech
            ;;
        speech:stop)
            stop_ai_speech
            ;;
        build)
            build_backend
            build_frontend
            ;;
        build:backend)
            build_backend
            ;;
        build:frontend)
            build_frontend
            ;;
        logs)
            show_logs "${2:-}"
            ;;
        check)
            print_banner
            check_dependencies
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            log_error "未知命令: $1"
            echo ""
            show_help
            ;;
    esac
}

main "$@"
