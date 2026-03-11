"""RAG Service - Knowledge retrieval using ChromaDB"""

import os
import logging
from typing import List, Dict, Any

import chromadb
from chromadb.config import Settings

logger = logging.getLogger(__name__)


class RAGService:
    """RAG service for knowledge retrieval"""
    
    def __init__(self):
        self.client = None
        self.collection = None
        self.db_path = os.getenv("CHROMA_DB_PATH", "./chroma_db")
        
    async def initialize(self):
        """Initialize ChromaDB client"""
        logger.info(f"Initializing ChromaDB at {self.db_path}")
        
        # Create ChromaDB client
        self.client = chromadb.Client(Settings(
            chroma_db_impl="duckdb+parquet",
            persist_directory=self.db_path
        ))
        
        # Get or create collection
        self.collection = self.client.get_or_create_collection(
            name="werewolf_knowledge",
            metadata={"description": "Werewolf game knowledge base"}
        )
        
        logger.info(f"ChromaDB initialized, collection: {self.collection.name}")
        
    async def add_documents(self, documents: List[Dict[str, Any]]):
        """Add documents to knowledge base"""
        if not self.collection:
            raise RuntimeError("RAG service not initialized")
            
        ids = [doc["id"] for doc in documents]
        texts = [doc["text"] for doc in documents]
        metadatas = [doc.get("metadata", {}) for doc in documents]
        
        self.collection.add(
            ids=ids,
            documents=texts,
            metadatas=metadatas
        )
        
        logger.info(f"Added {len(documents)} documents to knowledge base")
        
    async def query(self, query: str, top_k: int = 3) -> List[Dict[str, Any]]:
        """Query knowledge base"""
        if not self.collection:
            raise RuntimeError("RAG service not initialized")
            
        results = self.collection.query(
            query_texts=[query],
            n_results=top_k
        )
        
        # Format results
        formatted_results = []
        for i in range(len(results["ids"][0])):
            formatted_results.append({
                "id": results["ids"][0][i],
                "content": results["documents"][0][i],
                "metadata": results["metadatas"][0][i] if results["metadatas"][0] else {},
                "score": results["distances"][0][i] if results["distances"][0] else 0.0
            })
            
        return formatted_results
        
    async def get_stats(self) -> Dict[str, Any]:
        """Get knowledge base statistics"""
        if not self.collection:
            return {"count": 0}
            
        count = self.collection.count()
        return {
            "count": count,
            "collection": self.collection.name
        }
        
    async def clear(self):
        """Clear all documents"""
        if self.collection:
            self.client.delete_collection(self.collection.name)
            self.collection = self.client.get_or_create_collection(
                name="werewolf_knowledge",
                metadata={"description": "Werewolf game knowledge base"}
            )
            logger.info("Knowledge base cleared")