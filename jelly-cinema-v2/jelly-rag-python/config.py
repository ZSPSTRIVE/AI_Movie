"""
果冻影院 RAG 服务配置
企业级配置管理

Author: Jelly Cinema Team
Version: 2.0.0
"""
from pydantic_settings import BaseSettings
from functools import lru_cache
from typing import Optional


class Settings(BaseSettings):
    """应用配置"""
    
    # ==================== Milvus ====================
    enable_milvus: bool = False  # 是否启用 Milvus，没有 Docker 时设为 False
    milvus_host: str = "localhost"
    milvus_port: int = 19530
    milvus_collection_name: str = "jelly_film_collection"
    
    # ==================== Embedding ====================
    embedding_model: str = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    embedding_dim: int = 384  # MiniLM-L12 维度
    embedding_batch_size: int = 32
    
    # ==================== Reranker ====================
    enable_reranker: bool = True
    reranker_model: str = "bge-reranker-base"  # bge-reranker-base, bge-reranker-large, ms-marco
    reranker_top_k: int = 50  # 粗排取 top-k 送入 reranker
    
    # ==================== Hybrid Search ====================
    enable_hybrid_search: bool = True
    bm25_weight: float = 0.3
    vector_weight: float = 0.7
    rrf_k: int = 60  # RRF 参数
    bm25_refresh_minutes: int = 60  # BM25 索引刷新间隔（分钟）
    
    # ==================== Query Enhancement ====================
    enable_query_enhancement: bool = True
    enable_hyde: bool = False  # HyDE 需要 LLM，默认关闭
    llm_model_path: Optional[str] = None  # 本地 LLM 路径或模型 ID
    llm_local_only: bool = True  # 仅使用本地模型文件，避免联网下载
    llm_device: Optional[str] = None  # cuda / cpu，None 为自动
    llm_max_new_tokens: int = 128
    llm_temperature: float = 0.7
    llm_top_p: float = 0.9
    
    # ==================== Redis Cache ====================
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_password: Optional[str] = None
    redis_db: int = 0
    cache_ttl_search: int = 600      # 10 分钟
    cache_ttl_embedding: int = 3600  # 1 小时
    cache_ttl_film: int = 1800       # 30 分钟
    
    # ==================== MySQL ====================
    mysql_host: str = "localhost"
    mysql_port: int = 3306
    mysql_database: str = "jelly_cinema"
    mysql_user: str = "root"
    mysql_password: str = "123456"
    
    # ==================== Rate Limiting ====================
    enable_rate_limit: bool = True
    rate_limit_requests: int = 100  # 每分钟请求数
    rate_limit_burst: int = 20      # 突发容量
    
    # ==================== Retry & Circuit Breaker ====================
    retry_max_attempts: int = 3
    retry_wait_seconds: float = 1.0
    circuit_breaker_threshold: int = 5  # 失败次数阈值
    circuit_breaker_timeout: int = 30   # 熔断恢复时间(秒)
    
    # ==================== Service ====================
    service_port: int = 8500
    service_name: str = "jelly-rag-python"
    service_version: str = "2.0.0"
    debug: bool = False
    
    # ==================== Logging ====================
    log_level: str = "INFO"
    log_format: str = "json"  # json or text
    
    class Config:
        env_file = (".env", ".env.runtime")
        env_file_encoding = "utf-8"


@lru_cache()
def get_settings() -> Settings:
    """获取配置单例"""
    return Settings()
