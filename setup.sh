#!/bin/bash

# ============================================================
# 狼人杀项目 - 环境初始化脚本
# 首次运行项目前执行此脚本，完成环境配置
# 支持自动通过 Homebrew 安装缺失依赖
# ============================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }

echo -e "${CYAN}"
echo "╔══════════════════════════════════════════════╗"
echo "║   🐺 AI 狼人杀游戏平台 - 环境初始化         ║"
echo "╚══════════════════════════════════════════════╝"
echo -e "${NC}"

# -------------------- Java 环境 --------------------
if [ -z "$JAVA_HOME" ] && [ -d "/opt/homebrew/opt/openjdk" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# -------------------- 基础设施模式检测 --------------------
# Docker 模式 vs 本地模式
USE_DOCKER=false
USE_LOCAL_INFRA=false

# -------------------- 1. 检查并安装依赖 --------------------
log_step "1/6 检查基础环境..."

HAS_BREW=false
if command -v brew >/dev/null 2>&1; then
    HAS_BREW=true
    log_info "✅ Homebrew 已安装"
else
    log_warn "⚠️  Homebrew 未安装，无法自动安装依赖"
    log_warn "   安装方式: /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
fi

errors=0
missing_deps=()

# ---- Docker (可选) ----
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    log_info "✅ Docker $(docker --version | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')"
    USE_DOCKER=true
else
    log_warn "⚠️  Docker 未安装或未运行 (将使用本地 MySQL/Redis)"
    USE_LOCAL_INFRA=true
fi

# ---- Java ----
if command -v java >/dev/null 2>&1; then
    java_version=$(java -version 2>&1 | head -1)
    log_info "✅ Java $java_version"
else
    log_warn "⚠️  Java 未安装，后端需要 Java 17+"
    missing_deps+=("java")
    if [ "$HAS_BREW" = true ]; then
        echo -ne "   是否通过 brew 安装 OpenJDK 17? [Y/n] "
        read -r answer
        if [ "$answer" != "n" ] && [ "$answer" != "N" ]; then
            log_info "正在安装 OpenJDK 17..."
            brew install openjdk@17
            # 配置 JAVA_HOME 让当前 shell 生效
            if [ -d "$(brew --prefix openjdk@17)" ]; then
                export JAVA_HOME="$(brew --prefix openjdk@17)"
                export PATH="$JAVA_HOME/bin:$PATH"
                # 创建系统 symlink
                sudo ln -sfn "$(brew --prefix openjdk@17)/libexec/openjdk.jdk" /Library/Java/JavaVirtualMachines/openjdk-17.jdk 2>/dev/null || true
                log_info "✅ Java 17 已安装"
                log_warn "⚠️  请将以下内容添加到 ~/.zshrc 使其永久生效:"
                echo -e "   ${CYAN}export JAVA_HOME=\"$(brew --prefix openjdk@17)\"${NC}"
                echo -e "   ${CYAN}export PATH=\"\$JAVA_HOME/bin:\$PATH\"${NC}"
            fi
        else
            errors=$((errors + 1))
        fi
    else
        log_error "  推荐: brew install openjdk@17"
        errors=$((errors + 1))
    fi
fi

# ---- Maven ----
if command -v mvn >/dev/null 2>&1; then
    log_info "✅ Maven $(mvn --version 2>/dev/null | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')"
else
    log_warn "⚠️  Maven 未安装"
    missing_deps+=("maven")
    if [ "$HAS_BREW" = true ]; then
        echo -ne "   是否通过 brew 安装 Maven? [Y/n] "
        read -r answer
        if [ "$answer" != "n" ] && [ "$answer" != "N" ]; then
            log_info "正在安装 Maven..."
            brew install maven
            log_info "✅ Maven 已安装"
        else
            log_warn "将尝试使用 Maven Wrapper"
        fi
    else
        log_warn "  推荐: brew install maven"
    fi
fi

# ---- Node.js ----
if command -v node >/dev/null 2>&1; then
    log_info "✅ Node.js $(node --version)"
else
    log_warn "⚠️  Node.js 未安装，前端需要 Node.js 16+"
    missing_deps+=("node")
    if [ "$HAS_BREW" = true ]; then
        echo -ne "   是否通过 brew 安装 Node.js? [Y/n] "
        read -r answer
        if [ "$answer" != "n" ] && [ "$answer" != "N" ]; then
            log_info "正在安装 Node.js..."
            brew install node
            log_info "✅ Node.js 已安装"
        else
            errors=$((errors + 1))
        fi
    else
        log_error "  推荐: brew install node"
        errors=$((errors + 1))
    fi
fi

# ---- Python ----
PYTHON_CMD=""
if command -v python3 >/dev/null 2>&1; then
    PYTHON_CMD="python3"
    log_info "✅ Python $($PYTHON_CMD --version | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')"
elif command -v python >/dev/null 2>&1; then
    PYTHON_CMD="python"
    log_info "✅ Python $($PYTHON_CMD --version | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')"
else
    log_warn "⚠️  Python 未安装 (AI 服务需要 Python 3.9+)"
    log_warn "  推荐: brew install python@3.12"
fi

# ---- MySQL (本地模式下检查) ----
if [ "$USE_LOCAL_INFRA" = true ]; then
    if command -v mysql >/dev/null 2>&1; then
        log_info "✅ MySQL (本地) $(mysql --version 2>/dev/null | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)"
    else
        log_warn "⚠️  MySQL 未安装 (无 Docker 模式下需要本地 MySQL)"
        if [ "$HAS_BREW" = true ]; then
            echo -ne "   是否通过 brew 安装 MySQL 8.0? [Y/n] "
            read -r answer
            if [ "$answer" != "n" ] && [ "$answer" != "N" ]; then
                log_info "正在安装 MySQL..."
                brew install mysql
                log_info "✅ MySQL 已安装"
            else
                errors=$((errors + 1))
            fi
        else
            log_error "  推荐: brew install mysql"
            errors=$((errors + 1))
        fi
    fi

    # Redis
    if command -v redis-server >/dev/null 2>&1; then
        log_info "✅ Redis (本地) $(redis-server --version 2>/dev/null | grep -oE 'v=[0-9]+\.[0-9]+\.[0-9]+' | cut -d= -f2)"
    else
        log_warn "⚠️  Redis 未安装"
        if [ "$HAS_BREW" = true ]; then
            echo -ne "   是否通过 brew 安装 Redis? [Y/n] "
            read -r answer
            if [ "$answer" != "n" ] && [ "$answer" != "N" ]; then
                log_info "正在安装 Redis..."
                brew install redis
                log_info "✅ Redis 已安装"
            else
                errors=$((errors + 1))
            fi
        else
            log_error "  推荐: brew install redis"
            errors=$((errors + 1))
        fi
    fi
fi

if [ $errors -gt 0 ]; then
    log_error "有 $errors 个必要依赖缺失，请安装后重新运行"
    exit 1
fi

log_info "环境检查通过 ✅"
echo ""

# -------------------- 2. 创建必要目录 --------------------
log_step "2/6 创建项目目录..."

mkdir -p "$PROJECT_ROOT/logs"
mkdir -p "$PROJECT_ROOT/.pids"
log_info "✅ 已创建 logs/ 和 .pids/ 目录"
echo ""

# -------------------- 3. 后端初始化 --------------------
log_step "3/6 初始化后端..."

cd "$PROJECT_ROOT/packages/backend"

# 生成 Maven Wrapper（如果不存在）
if [ ! -f "mvnw" ]; then
    if command -v mvn >/dev/null 2>&1; then
        log_info "生成 Maven Wrapper..."
        mvn wrapper:wrapper -Dmaven=3.9.6 -q 2>/dev/null || {
            log_warn "Maven Wrapper 生成失败，将使用系统 Maven"
        }
    else
        log_warn "Maven 未安装且无 Maven Wrapper，后端启动可能失败"
    fi
fi

if [ -f "mvnw" ]; then
    chmod +x mvnw
    log_info "✅ Maven Wrapper 已就绪"
fi

# 下载后端依赖
log_info "下载后端依赖 (首次可能较慢)..."
if [ -f "mvnw" ]; then
    ./mvnw dependency:resolve -q 2>/dev/null || log_warn "后端依赖下载失败，将在首次启动时下载"
elif command -v mvn >/dev/null 2>&1; then
    mvn dependency:resolve -q 2>/dev/null || log_warn "后端依赖下载失败，将在首次启动时下载"
fi

log_info "✅ 后端初始化完成"
echo ""

# -------------------- 4. 前端初始化 --------------------
log_step "4/6 初始化前端..."

cd "$PROJECT_ROOT/packages/frontend"

log_info "安装前端依赖..."
npm install

log_info "✅ 前端初始化完成"
echo ""

# -------------------- 5. AI 服务初始化 --------------------
log_step "5/6 初始化 AI 服务..."

cd "$PROJECT_ROOT/packages/ai-service"

if [ -n "$PYTHON_CMD" ]; then
    # 创建虚拟环境
    if [ ! -d "venv" ]; then
        log_info "创建 Python 虚拟环境..."
        $PYTHON_CMD -m venv venv
    fi

    # 安装依赖
    source venv/bin/activate
    log_info "安装 Python 依赖..."
    pip install -r requirements.txt -q 2>/dev/null || {
        log_warn "部分 Python 依赖安装失败，请手动检查"
    }

    # 配置 .env
    if [ ! -f ".env" ]; then
        if [ -f ".env.example" ]; then
            cp .env.example .env
            log_warn "⚠️  已创建 .env 文件，请编辑填写 OPENAI_API_KEY 等配置"
            log_warn "   文件位置: $PROJECT_ROOT/packages/ai-service/.env"
        fi
    else
        log_info ".env 配置文件已存在"
    fi

    deactivate 2>/dev/null || true
    log_info "✅ AI 服务初始化完成"
else
    log_warn "⚠️  跳过 AI 服务初始化 (Python 未安装)"
fi
echo ""

# -------------------- 6. 启动基础设施 --------------------
log_step "6/6 启动基础设施..."

cd "$PROJECT_ROOT"

# 保存基础设施模式到配置文件
INFRA_CONF="$PROJECT_ROOT/.infra_mode"

if [ "$USE_DOCKER" = true ]; then
    log_info "使用 Docker 模式启动基础设施..."
    echo "docker" > "$INFRA_CONF"

    if docker compose version >/dev/null 2>&1; then
        docker compose up -d
    else
        docker-compose up -d
    fi

    log_info "等待 MySQL 就绪..."
    count=0
    while ! docker exec werewolf-mysql mysqladmin ping -h localhost -u root -p123456 --silent 2>/dev/null; do
        if [ $count -ge 60 ]; then
            log_warn "MySQL 启动超时，可能需要更多时间"
            break
        fi
        sleep 2
        count=$((count + 2))
    done
    if [ $count -lt 60 ]; then
        log_info "✅ Docker 基础设施已启动 (MySQL + Redis + ChromaDB)"
    fi
else
    log_info "使用本地模式启动基础设施..."
    echo "local" > "$INFRA_CONF"

    # 启动本地 MySQL
    if command -v mysql >/dev/null 2>&1; then
        if ! pgrep -x mysqld >/dev/null 2>&1; then
            log_info "启动本地 MySQL..."
            brew services start mysql 2>/dev/null || {
                # 尝试直接启动
                mysqld_safe &
                sleep 3
            }
        fi

        # 等待 MySQL 就绪
        count=0
        while ! mysqladmin ping --silent 2>/dev/null; do
            if [ $count -ge 30 ]; then
                log_warn "MySQL 启动超时"
                break
            fi
            sleep 2
            count=$((count + 2))
        done

        if [ $count -lt 30 ]; then
            log_info "✅ 本地 MySQL 已启动"

            # 初始化数据库和用户
            log_info "初始化数据库..."
            mysql -u root 2>/dev/null <<EOF || log_warn "数据库初始化可能需要手动执行"
CREATE DATABASE IF NOT EXISTS werewolf DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'werewolf'@'localhost' IDENTIFIED BY 'werewolf123';
GRANT ALL PRIVILEGES ON werewolf.* TO 'werewolf'@'localhost';
FLUSH PRIVILEGES;
EOF

            # 导入初始化 SQL
            if [ -f "$PROJECT_ROOT/scripts/init.sql" ]; then
                mysql -u werewolf -pwerewolf123 werewolf < "$PROJECT_ROOT/scripts/init.sql" 2>/dev/null || {
                    log_warn "初始化 SQL 导入可能需要手动执行"
                }
            fi
        fi
    fi

    # 启动本地 Redis
    if command -v redis-server >/dev/null 2>&1; then
        if ! pgrep -x redis-server >/dev/null 2>&1; then
            log_info "启动本地 Redis..."
            brew services start redis 2>/dev/null || {
                redis-server --daemonize yes 2>/dev/null
            }
        fi
        log_info "✅ 本地 Redis 已启动"
    fi
fi

echo ""

# -------------------- 完成 --------------------
echo -e "${GREEN}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║          🎉 环境初始化完成！                    ║${NC}"
echo -e "${GREEN}╠══════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║                                                  ║${NC}"
echo -e "${GREEN}║  基础设施模式: $(printf '%-34s' "$([ "$USE_DOCKER" = true ] && echo "Docker 🐳" || echo "本地 💻")")║${NC}"
echo -e "${GREEN}║                                                  ║${NC}"
echo -e "${GREEN}║  启动项目:                                       ║${NC}"
echo -e "${GREEN}║    ./start.sh start                              ║${NC}"
echo -e "${GREEN}║                                                  ║${NC}"
echo -e "${GREEN}║  查看帮助:                                       ║${NC}"
echo -e "${GREEN}║    ./start.sh help                               ║${NC}"
echo -e "${GREEN}║                                                  ║${NC}"
echo -e "${GREEN}║  服务地址:                                       ║${NC}"
echo -e "${GREEN}║    后端 API:  http://localhost:8080               ║${NC}"
echo -e "${GREEN}║    前端 H5:   http://localhost:10086              ║${NC}"
echo -e "${GREEN}║    AI 文档:   http://localhost:8000/docs          ║${NC}"
echo -e "${GREEN}║                                                  ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════╝${NC}"
echo ""
