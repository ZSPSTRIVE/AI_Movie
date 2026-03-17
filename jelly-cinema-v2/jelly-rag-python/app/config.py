from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    service_name: str = "jelly-rag-python"
    service_port: int = 8500
    cors_origins: str = "http://localhost:5174,http://localhost:8080"

    ai_api_key: str = ""
    ai_base_url: str = "https://api.siliconflow.cn/v1"
    ai_chat_model: str = "deepseek-ai/DeepSeek-V3"
    ai_embedding_model: str = "BAAI/bge-m3"
    ai_timeout_seconds: int = 60

    embedding_model_local: str = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    embedding_batch_size: int = 16

    milvus_host: str = "127.0.0.1"
    milvus_port: int = 19530
    milvus_collection_name: str = "rag_chunks"
    milvus_metric_type: str = "COSINE"
    milvus_index_nlist: int = 128

    postgres_host: str = "127.0.0.1"
    postgres_port: int = 5432
    postgres_database: str = "rag_meta"
    postgres_user: str = "postgres"
    postgres_password: str = "AiInfra@123"
    postgres_cli_path: str = "E:/infra/postgresql/17/app/pgsql/bin/psql.exe"

    redis_host: str = "127.0.0.1"
    redis_port: int = 6379
    redis_password: str = ""
    redis_db: int = 0
    redis_cache_ttl_seconds: int = 120

    default_biz_type: str = "general"
    default_top_k: int = 5
    max_top_k: int = 20
    chunk_size: int = 600
    chunk_overlap: int = 80
    answer_context_limit: int = 4
    fallback_answer_chars: int = 240

    knowledge_base_dir: str = "knowledge_bases"
    keyword_fallback_candidates: int = 200

    tvbox_proxy_base_url: str = "http://127.0.0.1:3001"
    tvbox_timeout_seconds: int = 20
    movie_auto_sync_enabled: bool = True
    movie_sync_ttl_seconds: int = 1800
    movie_biz_type: str = "movie"
    movie_search_sync_limit: int = 8
    movie_detail_sync_limit: int = 3
    movie_snapshot_limit: int = 24
    movie_dynamic_kb_file: str = "movie_dynamic_catalog.jsonl"

    model_config = SettingsConfigDict(
        env_file=(".env", ".env.runtime"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    @property
    def knowledge_base_path(self) -> Path:
        return Path(self.knowledge_base_dir)


@lru_cache()
def get_settings() -> Settings:
    return Settings()
