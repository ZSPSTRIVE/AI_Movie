"""
Prometheus 指标服务
企业级可观测性

Author: Jelly Cinema Team
Version: 2.0.0
"""
import time
import logging
from typing import Callable
from functools import wraps
from prometheus_client import Counter, Histogram, Gauge, Info, generate_latest, CONTENT_TYPE_LATEST
from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware

logger = logging.getLogger(__name__)


# ==================== 指标定义 ====================

# 请求计数
REQUEST_COUNT = Counter(
    "rag_requests_total",
    "Total number of requests",
    ["method", "endpoint", "status"]
)

# 请求延迟
REQUEST_LATENCY = Histogram(
    "rag_request_duration_seconds",
    "Request duration in seconds",
    ["method", "endpoint"],
    buckets=(0.01, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0)
)

# 搜索指标
SEARCH_COUNT = Counter(
    "rag_search_total",
    "Total number of searches",
    ["search_type"]  # vector, hybrid, bm25
)

SEARCH_LATENCY = Histogram(
    "rag_search_duration_seconds",
    "Search duration in seconds",
    ["search_type"],
    buckets=(0.05, 0.1, 0.25, 0.5, 1.0, 2.0, 5.0)
)

SEARCH_RESULTS = Histogram(
    "rag_search_results_count",
    "Number of search results returned",
    buckets=(0, 1, 2, 3, 5, 10, 20, 50)
)

# 缓存指标
CACHE_HITS = Counter(
    "rag_cache_hits_total",
    "Cache hit count",
    ["cache_type"]  # search, embedding, film
)

CACHE_MISSES = Counter(
    "rag_cache_misses_total",
    "Cache miss count",
    ["cache_type"]
)

# Embedding 指标
EMBEDDING_COUNT = Counter(
    "rag_embedding_total",
    "Total embeddings generated"
)

EMBEDDING_LATENCY = Histogram(
    "rag_embedding_duration_seconds",
    "Embedding generation duration",
    buckets=(0.01, 0.05, 0.1, 0.25, 0.5, 1.0)
)

# Rerank 指标
RERANK_COUNT = Counter(
    "rag_rerank_total",
    "Total rerank operations"
)

RERANK_LATENCY = Histogram(
    "rag_rerank_duration_seconds",
    "Rerank duration in seconds",
    buckets=(0.1, 0.25, 0.5, 1.0, 2.0, 5.0)
)

# 系统状态
MILVUS_STATUS = Gauge(
    "rag_milvus_connected",
    "Milvus connection status (1=connected, 0=disconnected)"
)

REDIS_STATUS = Gauge(
    "rag_redis_connected",
    "Redis connection status (1=connected, 0=disconnected)"
)

INDEX_SIZE = Gauge(
    "rag_index_size",
    "Number of documents in the index"
)

# 服务信息
SERVICE_INFO = Info(
    "rag_service",
    "RAG service information"
)


# ==================== 中间件 ====================

class MetricsMiddleware(BaseHTTPMiddleware):
    """
    Prometheus 指标中间件
    
    自动记录每个请求的:
    - 请求计数
    - 请求延迟
    - 响应状态码
    """
    
    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        # 跳过 metrics 端点本身
        if request.url.path == "/metrics":
            return await call_next(request)
        
        method = request.method
        endpoint = request.url.path
        
        start_time = time.perf_counter()
        
        try:
            response = await call_next(request)
            status = str(response.status_code)
        except Exception as e:
            status = "500"
            raise
        finally:
            duration = time.perf_counter() - start_time
            
            REQUEST_COUNT.labels(method=method, endpoint=endpoint, status=status).inc()
            REQUEST_LATENCY.labels(method=method, endpoint=endpoint).observe(duration)
        
        return response


# ==================== 装饰器 ====================

def track_search(search_type: str = "vector"):
    """搜索指标追踪装饰器"""
    def decorator(func):
        @wraps(func)
        async def wrapper(*args, **kwargs):
            start_time = time.perf_counter()
            try:
                result = await func(*args, **kwargs)
                SEARCH_COUNT.labels(search_type=search_type).inc()
                
                # 记录结果数量
                if hasattr(result, '__len__'):
                    SEARCH_RESULTS.observe(len(result))
                
                return result
            finally:
                duration = time.perf_counter() - start_time
                SEARCH_LATENCY.labels(search_type=search_type).observe(duration)
        return wrapper
    return decorator


def track_embedding():
    """Embedding 指标追踪装饰器"""
    def decorator(func):
        @wraps(func)
        def wrapper(*args, **kwargs):
            start_time = time.perf_counter()
            try:
                result = func(*args, **kwargs)
                EMBEDDING_COUNT.inc()
                return result
            finally:
                duration = time.perf_counter() - start_time
                EMBEDDING_LATENCY.observe(duration)
        return wrapper
    return decorator


def track_rerank():
    """Rerank 指标追踪装饰器"""
    def decorator(func):
        @wraps(func)
        def wrapper(*args, **kwargs):
            start_time = time.perf_counter()
            try:
                result = func(*args, **kwargs)
                RERANK_COUNT.inc()
                return result
            finally:
                duration = time.perf_counter() - start_time
                RERANK_LATENCY.observe(duration)
        return wrapper
    return decorator


# ==================== 辅助函数 ====================

def record_cache_hit(cache_type: str):
    """记录缓存命中"""
    CACHE_HITS.labels(cache_type=cache_type).inc()


def record_cache_miss(cache_type: str):
    """记录缓存未命中"""
    CACHE_MISSES.labels(cache_type=cache_type).inc()


def monitor_milvus_status(connected: bool):
    """更新 Milvus 状态"""
    MILVUS_STATUS.set(1 if connected else 0)


def monitor_redis_status(connected: bool):
    """更新 Redis 状态"""
    REDIS_STATUS.set(1 if connected else 0)


def update_index_size(size: int):
    """更新索引大小"""
    INDEX_SIZE.set(size)


def set_service_info(version: str, model: str):
    """设置服务信息"""
    SERVICE_INFO.info({
        "version": version,
        "embedding_model": model,
    })


def get_metrics() -> bytes:
    """获取 Prometheus 格式的指标"""
    return generate_latest()


def get_metrics_content_type() -> str:
    """获取指标的 Content-Type"""
    return CONTENT_TYPE_LATEST
