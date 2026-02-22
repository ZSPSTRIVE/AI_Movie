"""
Jelly Cinema enterprise RAG service.
"""
from contextlib import asynccontextmanager
import logging
import time
from typing import List, Optional

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.util import get_remote_address

from cache_service import cache_service
from config import get_settings
from embedding_service import build_film_content, embed_text, embed_texts
from hybrid_search import hybrid_search, hybrid_searcher
from knowledge_base_loader import build_knowledge_search_items
from metrics import MetricsMiddleware, monitor_milvus_status, monitor_redis_status, track_search
from milvus_client import connect_milvus, get_or_create_collection, insert_films, search_similar
from mysql_sync import fetch_all_films, fetch_films_by_ids
from query_enhancer import enhance_query
from reranker import init_reranker, reranker
from resilience import CircuitBreakerOpenError, run_with_resilience

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(name)s | %(message)s")
logger = logging.getLogger(__name__)
settings = get_settings()

_milvus_ready: bool = False
_bm25_last_build_at: float = 0.0
_kb_item_count: int = 0

limiter = Limiter(key_func=get_remote_address)


class SearchRequest(BaseModel):
    query: str
    top_k: int = 5
    enable_hybrid: bool = True
    enable_rerank: bool = True


class SearchResult(BaseModel):
    film_id: int
    title: str
    content: str
    score: float
    source: str = "vector"
    source_type: str = "film"
    knowledge_base: Optional[str] = None


class SearchResponse(BaseModel):
    results: List[SearchResult]
    query: str
    enhanced_query: Optional[str] = None
    took_ms: float


class SyncResponse(BaseModel):
    success: bool
    count: int
    message: str


class FilmDetail(BaseModel):
    film_id: int
    title: str
    description: Optional[str] = None
    cover_url: Optional[str] = None
    video_url: Optional[str] = None
    year: Optional[int] = None
    director: Optional[str] = None
    actors: Optional[str] = None
    region: Optional[str] = None
    rating: Optional[float] = None
    category_name: Optional[str] = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting Enterprise RAG Service...")

    global _milvus_ready
    _milvus_ready = False

    if settings.enable_milvus:
        try:
            connect_milvus()
            get_or_create_collection()
            _milvus_ready = True
            monitor_milvus_status(True)
            logger.info("Milvus connected")
        except Exception as exc:
            monitor_milvus_status(False)
            logger.error("Milvus init failed: %s", exc)
    else:
        monitor_milvus_status(False)
        logger.info("Milvus disabled by config")

    if cache_service.connect():
        monitor_redis_status(True)
    else:
        monitor_redis_status(False)

    if settings.enable_reranker:
        init_reranker()

    try:
        indexed = build_bm25_index_from_db()
        logger.info("BM25 index ready with %s docs", indexed)
    except Exception as exc:
        logger.warning("Initial BM25 index build skipped: %s", exc)

    logger.info("Service initialized successfully")
    yield
    logger.info("Shutting down service")


def build_bm25_index_from_db() -> int:
    global _bm25_last_build_at, _kb_item_count

    films = []
    try:
        films = run_with_resilience("mysql.fetch_all_films", fetch_all_films)
    except Exception as exc:
        logger.warning("Fetch films failed during BM25 build: %s", exc)

    searchable_items = []
    for film in films or []:
        content = build_film_content(film)
        searchable_items.append(
            {
                **film,
                "content": content,
                "source": "db",
                "source_type": "film",
                "knowledge_base": None,
            }
        )

    kb_items = []
    if settings.enable_knowledge_base:
        kb_items = build_knowledge_search_items(
            base_dir=settings.knowledge_base_dir,
            max_docs=settings.knowledge_base_max_docs,
        )
        searchable_items.extend(kb_items)

    _kb_item_count = len(kb_items)

    if not searchable_items:
        hybrid_searcher._indexed = False
        _bm25_last_build_at = time.time()
        return 0

    bm25_built = run_with_resilience("bm25.rebuild", hybrid_searcher.build_bm25_index, searchable_items)
    if not bm25_built:
        raise RuntimeError("BM25 index build returned false")

    _bm25_last_build_at = time.time()
    return len(searchable_items)


app = FastAPI(
    title="Jelly Enterprise RAG",
    description="Enterprise retrieval augmented search service",
    version="2.1.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.add_middleware(MetricsMiddleware)

app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)


@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "components": {
            "milvus": "connected" if _milvus_ready else "disconnected",
            "redis": "connected" if cache_service.is_connected else "disconnected",
            "reranker": "ready" if reranker.is_ready else "disabled/loading",
            "bm25": "ready" if hybrid_searcher._indexed else "empty",
        },
        "knowledge_base": {
            "enabled": settings.enable_knowledge_base,
            "dir": settings.knowledge_base_dir,
            "indexed_docs": _kb_item_count,
        },
        "bm25_last_build_at": _bm25_last_build_at,
    }


@app.get("/metrics")
async def metrics():
    from fastapi.responses import Response
    from metrics import get_metrics, get_metrics_content_type

    return Response(content=get_metrics(), media_type=get_metrics_content_type())


@app.post("/rag/search", response_model=SearchResponse)
@limiter.limit(f"{settings.rate_limit_requests}/minute")
@track_search(search_type="hybrid")
async def rag_search(request: Request, body: SearchRequest):
    start_time = time.perf_counter()

    try:
        cached_response = cache_service.get_search_response(body)
        if cached_response is not None:
            return SearchResponse(**cached_response)

        enhanced_query = body.query
        if settings.enable_query_enhancement:
            enhanced_query = enhance_query(body.query)
            logger.info("Enhanced query: %s -> %s", body.query, enhanced_query)

        if settings.bm25_refresh_minutes > 0:
            now = time.time()
            if (not hybrid_searcher._indexed) or (
                now - _bm25_last_build_at > settings.bm25_refresh_minutes * 60
            ):
                try:
                    build_bm25_index_from_db()
                except CircuitBreakerOpenError as exc:
                    logger.warning("BM25 refresh skipped due open circuit breaker: %s", exc)
                except Exception as exc:
                    logger.warning("BM25 refresh skipped: %s", exc)

        recall_k = body.top_k * 2 if (body.enable_rerank and settings.enable_reranker) else body.top_k

        vector_results = []
        if _milvus_ready:
            try:
                query_embedding = embed_text(enhanced_query)
                vector_results = search_similar(query_embedding, top_k=recall_k)
            except Exception as exc:
                logger.warning("Milvus search failed, fallback to BM25-only: %s", exc)
                vector_results = []

        final_candidates = vector_results
        if body.enable_hybrid and settings.enable_hybrid_search:
            final_candidates = hybrid_search(enhanced_query, vector_results, top_k=recall_k)

        if body.enable_rerank and settings.enable_reranker and final_candidates:
            try:
                final_results = run_with_resilience(
                    "reranker.inference",
                    reranker.rerank,
                    query=body.query,
                    documents=final_candidates,
                    top_k=body.top_k,
                    content_key="content",
                )
            except CircuitBreakerOpenError as exc:
                logger.warning("Reranker skipped due open circuit breaker: %s", exc)
                final_results = final_candidates[:body.top_k]
            except Exception as exc:
                logger.warning("Reranker skipped due error: %s", exc)
                final_results = final_candidates[:body.top_k]
        else:
            final_results = final_candidates[:body.top_k]

        response_results = [
            SearchResult(
                film_id=r["film_id"],
                title=r["title"],
                content=r["content"],
                score=r.get("rerank_score", r.get("score", 0)),
                source=r.get("source", "vector"),
                source_type=r.get("source_type", "film"),
                knowledge_base=r.get("knowledge_base"),
            )
            for r in final_results
        ]

        took_ms = (time.perf_counter() - start_time) * 1000
        response = SearchResponse(
            results=response_results,
            query=body.query,
            enhanced_query=enhanced_query if enhanced_query != body.query else None,
            took_ms=took_ms,
        )
        cache_service.set_search_response(body, response.model_dump())
        return response
    except Exception as exc:
        logger.error("Search failed: %s", exc, exc_info=True)
        raise HTTPException(status_code=500, detail=str(exc))


@app.post("/rag/sync", response_model=SyncResponse)
async def sync_films():
    try:
        films = run_with_resilience("mysql.fetch_all_films", fetch_all_films)

        count = 0
        if films and _milvus_ready:
            contents = [build_film_content(f) for f in films]
            film_data = [
                {
                    "film_id": f["film_id"],
                    "title": f.get("title") or "",
                    "content": c,
                }
                for f, c in zip(films, contents)
            ]
            embeddings = embed_texts(contents)
            count = insert_films(film_data, embeddings)

        indexed = build_bm25_index_from_db()
        cache_service.clear_all_cache()

        msg = f"Synced {count} films and rebuilt BM25 ({indexed} docs, kb={_kb_item_count})"
        if not _milvus_ready:
            msg = f"Rebuilt BM25 ({indexed} docs, kb={_kb_item_count})"

        return SyncResponse(success=True, count=count, message=msg)
    except Exception as exc:
        logger.error("Sync failed: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc))


@app.post("/rag/kb/reload", response_model=SyncResponse)
async def reload_knowledge_bases():
    try:
        indexed = build_bm25_index_from_db()
        cache_service.clear_all_cache()
        return SyncResponse(
            success=True,
            count=_kb_item_count,
            message=f"Knowledge base reloaded. BM25 docs={indexed}, kb={_kb_item_count}",
        )
    except Exception as exc:
        logger.error("Knowledge base reload failed: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc))


@app.get("/films/{film_id}", response_model=FilmDetail)
async def get_film_detail(film_id: int):
    cached = cache_service.get_film(film_id)
    if cached:
        return FilmDetail(**cached)

    try:
        films = run_with_resilience("mysql.fetch_films_by_ids", fetch_films_by_ids, [film_id])
        if not films:
            raise HTTPException(status_code=404, detail="Not found")

        film = films[0]
        detail = FilmDetail(
            film_id=film["film_id"],
            title=film["title"],
            description=film.get("description"),
            cover_url=film.get("cover_url"),
            video_url=film.get("video_url"),
            year=film.get("year"),
            director=film.get("director"),
            actors=film.get("actors"),
            region=film.get("region"),
            rating=float(film["rating"]) if film.get("rating") else None,
            category_name=film.get("category_name"),
        )

        cache_service.set_film(film_id, detail.model_dump())
        return detail
    except HTTPException:
        raise
    except Exception as exc:
        logger.error("Get film failed: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc))


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=settings.service_port, reload=False)
