"""
Jelly RAG service settings.
"""
from functools import lru_cache
from typing import Optional

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Application settings."""

    # ==================== Milvus ====================
    enable_milvus: bool = False
    milvus_host: str = "localhost"
    milvus_port: int = 19530
    milvus_collection_name: str = "jelly_film_collection"

    # ==================== Embedding ====================
    embedding_model: str = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    embedding_dim: int = 384
    embedding_batch_size: int = 32

    # ==================== Reranker ====================
    enable_reranker: bool = True
    reranker_model: str = "bge-reranker-base"
    reranker_top_k: int = 50

    # ==================== Hybrid Search ====================
    enable_hybrid_search: bool = True
    bm25_weight: float = 0.3
    vector_weight: float = 0.7
    rrf_k: int = 60
    bm25_refresh_minutes: int = 60

    # ==================== Query Enhancement ====================
    enable_query_enhancement: bool = True
    enable_hyde: bool = False
    llm_model_path: Optional[str] = None
    llm_local_only: bool = True
    llm_device: Optional[str] = None
    llm_max_new_tokens: int = 128
    llm_temperature: float = 0.7
    llm_top_p: float = 0.9

    # ==================== Knowledge Base ====================
    enable_knowledge_base: bool = True
    knowledge_base_dir: str = "knowledge_bases"
    knowledge_base_max_docs: int = 2000

    # ==================== Redis Cache ====================
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_password: Optional[str] = None
    redis_db: int = 0
    cache_ttl_search: int = 600
    cache_ttl_embedding: int = 3600
    cache_ttl_film: int = 1800

    # ==================== MySQL ====================
    mysql_host: str = "localhost"
    mysql_port: int = 3306
    mysql_database: str = "jelly_cinema"
    mysql_user: str = "root"
    mysql_password: str = "123456"

    # ==================== Rate Limiting ====================
    enable_rate_limit: bool = True
    rate_limit_requests: int = 100
    rate_limit_burst: int = 20

    # ==================== Retry & Circuit Breaker ====================
    retry_max_attempts: int = 3
    retry_wait_seconds: float = 1.0
    circuit_breaker_threshold: int = 5
    circuit_breaker_timeout: int = 30

    # ==================== Service ====================
    service_port: int = 8500
    service_name: str = "jelly-rag-python"
    service_version: str = "2.0.0"
    debug: bool = False

    # ==================== Logging ====================
    log_level: str = "INFO"
    log_format: str = "json"

    class Config:
        env_file = (".env", ".env.runtime")
        env_file_encoding = "utf-8"


@lru_cache()
def get_settings() -> Settings:
    """Return singleton settings."""
    return Settings()
