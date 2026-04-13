"""
Werewolf AI Service - FastAPI Application
基于 RAG 增强的 AI 狼人杀玩家服务
"""

import os
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from dotenv import load_dotenv

from routers import game, knowledge, health
from routers import agent_router
from services.rag_service import RAGService
from services.llm_service import LLMService
from agents.agent_manager import agent_manager

# Load environment variables
load_dotenv()

# Configure logging (按游戏ID分文件 + 主日志文件)
from services.log_service import setup_main_logging
setup_main_logging()
logger = logging.getLogger(__name__)

# Global services
rag_service: RAGService = None
llm_service: LLMService = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan manager"""
    global rag_service, llm_service
    
    # Startup
    logger.info("Starting Werewolf AI Service...")
    
    # Initialize RAG service
    logger.info("Initializing RAG service...")
    try:
        rag_service = RAGService()
        logger.info("RAG service initialized successfully")
    except Exception as e:
        logger.warning(f"RAG service init failed (will work without RAG): {e}")
        rag_service = None
    
    # Initialize LLM service
    logger.info("Initializing LLM service...")
    try:
        llm_service = LLMService()
        logger.info("LLM service initialized successfully")
    except Exception as e:
        logger.warning(f"LLM service init failed (agents will use template speech): {e}")
        llm_service = None
    
    # Initialize AgentManager with services
    logger.info("Initializing Agent Manager...")
    agent_manager.set_services(llm_service, rag_service)
    
    logger.info("Werewolf AI Service started successfully!")
    
    yield
    
    # Shutdown
    logger.info("Shutting down Werewolf AI Service...")


# Create FastAPI app
app = FastAPI(
    title="Werewolf AI Service",
    description="基于 RAG 增强的 AI 狼人杀玩家服务",
    version="1.0.0",
    lifespan=lifespan
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routers
app.include_router(health.router, prefix="/api", tags=["health"])
app.include_router(agent_router.router)  # 自带 prefix="/api/agents"
app.include_router(knowledge.router, prefix="/api/knowledge", tags=["knowledge"])
app.include_router(game.router, prefix="/api/game", tags=["game"])


@app.get("/")
async def root():
    """Root endpoint"""
    return {
        "service": "Werewolf AI Service",
        "version": "1.0.0",
        "status": "running",
        "docs": "/docs"
    }


if __name__ == "__main__":
    import uvicorn
    
    host = os.getenv("SERVICE_HOST", "0.0.0.0")
    port = int(os.getenv("SERVICE_PORT", 8000))
    
    uvicorn.run(app, host=host, port=port)