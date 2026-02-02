"""
果冻影院 RAG 服务
企业级智能检索服务

集成特性:
- 混合检索(BM25 + Vector)
- 多级缓存 (Redis)
- 查询增强 (HyDE + 扩展)
- 重排序(Cross-Encoder)
- 可观测性(Prometheus)
- 限流与熔断

Author: Jelly Cinema Team
Version: 2.0.0
"""
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from contextlib import asynccontextmanager
import logging
import time
from typing import List, Optional
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded
from pydantic import BaseModel

from config import get_settings
from milvus_client import (
    connect_milvus,
    get_or_create_collection,
    insert_films,
    search_similar
)
from embedding_service import embed_text, embed_texts, build_film_content
from mysql_sync import fetch_all_films, fetch_films_by_ids

# 企业级组件
from cache_service import cache_service, cache_search
from reranker import init_reranker, reranker
from hybrid_search import hybrid_search, hybrid_searcher
from query_enhancer import enhance_query
from metrics import (
    MetricsMiddleware, 
    track_search, 
    monitor_milvus_status, 
    monitor_redis_status
)

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s"
)
logger = logging.getLogger(__name__)
settings = get_settings()

_milvus_ready: bool = False
_bm25_last_build_at: float = 0.0

# 限流器
limiter = Limiter(key_func=get_remote_address)

# ==================== Pydantic Models ====================

class SearchRequest(BaseModel):
    """搜索请求"""
    query: str
    top_k: int = 5
    enable_hybrid: bool = True
    enable_rerank: bool = True

class SearchResult(BaseModel):
    """搜索结果"""
    film_id: int
    title: str
    content: str
    score: float
    source: str = "vector"

class SearchResponse(BaseModel):
    """搜索响应"""
    results: List[SearchResult]
    query: str
    enhanced_query: Optional[str] = None
    took_ms: float

class SyncResponse(BaseModel):
    """同步响应"""
    success: bool
    count: int
    message: str

class FilmDetail(BaseModel):
    """电影详情"""
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

# ==================== Lifespan ====================

@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期: 初始化各组件"""
    logger.info("🚀 Starting Enterprise RAG Service...")
    
    # 1. 连接基础服务
    global _milvus_ready
    _milvus_ready = False
    if settings.enable_milvus:
        try:
            connect_milvus()
            get_or_create_collection()
            _milvus_ready = True
            monitor_milvus_status(True)
            logger.info("✅ Milvus connected")
        except Exception as e:
            monitor_milvus_status(False)
            logger.error(f"❌ Milvus init failed: {e}")
    else:
        monitor_milvus_status(False)
        logger.info("⚠️ Milvus disabled by config")

    # 2. 连接 Redis
    if cache_service.connect():
        monitor_redis_status(True)
    else:
        monitor_redis_status(False)

    # 3. 加载 Reranker 模型
    if settings.enable_reranker:
        init_reranker()

    # 4. 构建 BM25 索引 (启动时构建)
    try:
        build_bm25_index_from_db()
    except Exception as e:
        logger.warning(f"Initial BM25 index build skipped: {e}")

    logger.info("✅ Service initialized successfully")
    yield
    logger.info("👋 Shutting down service")

def build_bm25_index_from_db() -> int:
    """从数据库构建/刷新 BM25 索引"""
    global _bm25_last_build_at
    films = fetch_all_films()
    if not films:
        return 0
    enriched_films = []
    for f in films:
        content = build_film_content(f)
        enriched_films.append({**f, "content": content})
    hybrid_searcher.build_bm25_index(enriched_films)
    _bm25_last_build_at = time.time()
    return len(enriched_films)

# ==================== FastAPI App ====================

app = FastAPI(
    title="Jelly Enterprise RAG",
    description="企业级检索增强生成服务",
    version="2.0.0",
    lifespan=lifespan
)

# 中间件
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.add_middleware(MetricsMiddleware)

# 全局异常处理
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# ==================== API Endpoints ====================

@app.get("/health")
async def health_check():
    """健康检查(含组件状态)"""
    return {
        "status": "healthy",
        "components": {
            "milvus": "connected" if _milvus_ready else "disconnected",
            "redis": "connected" if cache_service.is_connected else "disconnected",
            "reranker": "ready" if reranker.is_ready else "disabled/loading",
            "bm25": "ready" if hybrid_searcher._indexed else "empty"
        },
        "bm25_last_build_at": _bm25_last_build_at
    }

@app.get("/metrics")
async def metrics():
    """Prometheus 指标端点"""
    from metrics import get_metrics, get_metrics_content_type
    from fastapi.responses import Response
    return Response(content=get_metrics(), media_type=get_metrics_content_type())

@app.post("/rag/search", response_model=SearchResponse)
@limiter.limit(f"{settings.rate_limit_requests}/minute")
@track_search(search_type="hybrid")
@cache_search(ttl=settings.cache_ttl_search)
async def rag_search(request: Request, body: SearchRequest):
    """
    企业级混合检索
    流程: 缓存 -> 增强 -> 向量+BM25 -> Rerank
    """
    start_time = time.perf_counter()
    
    try:
        # 1. 查询增强
        enhanced_query = body.query
        if settings.enable_query_enhancement:
            enhanced_query = enhance_query(body.query)
            logger.info(f"✨ Enhanced query: {body.query} -> {enhanced_query}")

        # 2. 定期刷新 BM25 索引，避免数据过旧
        if settings.bm25_refresh_minutes > 0:
            now = time.time()
            if (not hybrid_searcher._indexed) or (now - _bm25_last_build_at > settings.bm25_refresh_minutes * 60):
                try:
                    build_bm25_index_from_db()
                except Exception as e:
                    logger.warning(f"BM25 refresh skipped: {e}")

        # 3. 向量检索 (召回 Top-K * 2 用于重排序)
        recall_k = body.top_k * 2 if (body.enable_rerank and settings.enable_reranker) else body.top_k
        
        # Embedding
        query_embedding = embed_text(enhanced_query)
        
        # Milvus Search
        vector_results = []
        if _milvus_ready:
            try:
                vector_results = search_similar(query_embedding, top_k=recall_k)
            except Exception as e:
                logger.warning(f"⚠️ Milvus search failed, falling back to BM25-only: {e}")
                vector_results = []

        # 3. 混合检索(如果启用)
        final_candidates = vector_results
        if body.enable_hybrid and settings.enable_hybrid_search:
            final_candidates = hybrid_search(enhanced_query, vector_results, top_k=recall_k)

        # 4. 重排序(如果启用)
        if body.enable_rerank and settings.enable_reranker:
            final_results = reranker.rerank(
                query=body.query,  # Rerank 使用原始查询通常更准
                documents=final_candidates,
                top_k=body.top_k,
                content_key="content"
            )
        else:
            final_results = final_candidates[:body.top_k]

        # 5. 构建响应
        response_results = [
            SearchResult(
                film_id=r["film_id"],
                title=r["title"],
                content=r["content"],
                score=r.get("rerank_score", r.get("score", 0)),
                source=r.get("source", "vector")
            )
            for r in final_results
        ]
        
        took_ms = (time.perf_counter() - start_time) * 1000
        return SearchResponse(
            results=response_results,
            query=body.query,
            enhanced_query=enhanced_query if enhanced_query != body.query else None,
            took_ms=took_ms
        )

    except Exception as e:
        logger.error(f"❌ Search failed: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/rag/sync", response_model=SyncResponse)
async def sync_films():
    """同步数据并重建索引"""
    try:
        global _bm25_last_build_at
        # 1. MySQL -> Milvus（可选）
        films = fetch_all_films()
        if not films:
            return SyncResponse(success=True, count=0, message="No films")
        
        contents = [build_film_content(f) for f in films]
        film_data = [
            {"film_id": f["film_id"], "title": f["title"] or "", "content": c}
            for f, c in zip(films, contents)
        ]
        
        count = 0
        if _milvus_ready:
            embeddings = embed_texts(contents)
            count = insert_films(film_data, embeddings)
        
        # 2. 重建 BM25 索引
        hybrid_searcher.build_bm25_index(film_data)
        _bm25_last_build_at = time.time()
        
        # 3. 清空缓存
        cache_service.clear_all_cache()
        
        message = "Rebuilt BM25 index"
        if _milvus_ready:
            message = f"Synced {count} films & Rebuilt Index"
        return SyncResponse(success=True, count=count, message=message)
        
    except Exception as e:
        logger.error(f"❌ Sync failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))
        

@app.get("/films/{film_id}", response_model=FilmDetail)
async def get_film_detail(film_id: int):
    """获取详情 (带缓存)"""
    # 查缓存
    cached = cache_service.get_film(film_id)
    if cached:
        return FilmDetail(**cached)

    try:
        films = fetch_films_by_ids([film_id])
        if not films:
            raise HTTPException(status_code=404, detail="Not found")
        
        film = films[0]
        # 转换并缓存
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
            category_name=film.get("category_name")
        )
        
        cache_service.set_film(film_id, detail.dict())
        return detail
        
    except Exception as e:
        logger.error(f"Get film failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=settings.service_port, reload=False)
