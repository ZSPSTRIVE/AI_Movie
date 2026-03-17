from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.rag_api import router as rag_router
from app.config import get_settings
from app.infra.embedding_client import EmbeddingClient
from app.infra.llm_client import LlmClient
from app.infra.milvus_store import MilvusStore
from app.infra.pg_client import PgClient
from app.infra.redis_client import RedisClient
from app.infra.tvbox_client import TvboxClient
from app.service.answer_service import AnswerService
from app.service.ingest_service import IngestService
from app.service.movie_knowledge_service import MovieKnowledgeService
from app.service.retrieval_service import RetrievalService

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(name)s | %(message)s")
logger = logging.getLogger(__name__)

settings = get_settings()
pg_client = PgClient(settings)
redis_client = RedisClient(settings)
embedding_client = EmbeddingClient(settings)
llm_client = LlmClient(settings)
milvus_store = MilvusStore(settings)
tvbox_client = TvboxClient(settings)
ingest_service = IngestService(
    settings=settings,
    pg_client=pg_client,
    milvus_store=milvus_store,
    embedding_client=embedding_client,
    redis_client=redis_client,
)
retrieval_service = RetrievalService(
    settings=settings,
    pg_client=pg_client,
    milvus_store=milvus_store,
    embedding_client=embedding_client,
)
answer_service = AnswerService(settings=settings, llm_client=llm_client)
movie_knowledge_service = MovieKnowledgeService(
    settings=settings,
    tvbox_client=tvbox_client,
    ingest_service=ingest_service,
    redis_client=redis_client,
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    try:
        pg_client.ensure_schema()
    except Exception as exc:
        logger.warning("PostgreSQL schema init skipped: %s", exc)
    redis_client.connect()
    yield


app = FastAPI(
    title="Jelly RAG Python",
    description="Minimal ingest/search/health RAG service",
    version="3.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[origin.strip() for origin in settings.cors_origins.split(",") if origin.strip()],
    allow_credentials=True,
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type", "Authorization", "X-Admin-Key"],
)
app.include_router(rag_router)
app.state.settings = settings
app.state.pg_client = pg_client
app.state.redis_client = redis_client
app.state.milvus_store = milvus_store
app.state.embedding_client = embedding_client
app.state.llm_client = llm_client
app.state.ingest_service = ingest_service
app.state.retrieval_service = retrieval_service
app.state.answer_service = answer_service
app.state.movie_knowledge_service = movie_knowledge_service


@app.get("/health")
async def health():
    postgres = pg_client.health()
    redis_status = redis_client.health()
    milvus = milvus_store.health()
    llm = llm_client.health()
    overall = "healthy"
    if postgres["status"] != "connected" or milvus["status"] != "connected":
        overall = "degraded"
    return {
        "status": overall,
        "components": {
            "postgres": postgres,
            "redis": redis_status,
            "milvus": milvus,
            "llm": llm,
            "embedding": embedding_client.health(),
        },
        "defaults": {
            "biz_type": settings.default_biz_type,
            "top_k": settings.default_top_k,
            "collection": settings.milvus_collection_name,
            "database": settings.postgres_database,
        },
    }
