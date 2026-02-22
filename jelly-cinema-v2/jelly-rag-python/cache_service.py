"""
Redis cache service for RAG endpoints.
"""
import hashlib
import json
import logging
from functools import wraps
from typing import Any, Dict, List, Optional

import redis
from redis.exceptions import RedisError

from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


class CacheService:
    """Redis-backed cache with graceful degradation."""

    PREFIX_SEARCH = "rag:search:"
    PREFIX_EMBEDDING = "rag:embed:"
    PREFIX_FILM = "rag:film:"

    TTL_SEARCH = settings.cache_ttl_search
    TTL_EMBEDDING = settings.cache_ttl_embedding
    TTL_FILM = settings.cache_ttl_film

    def __init__(self):
        self.client: Optional[redis.Redis] = None
        self._connected = False

    def connect(self) -> bool:
        """Connect to Redis and validate with ping."""
        try:
            self.client = redis.Redis(
                host=settings.redis_host,
                port=settings.redis_port,
                password=settings.redis_password or None,
                db=settings.redis_db,
                decode_responses=True,
                socket_connect_timeout=5,
                socket_timeout=5,
                retry_on_timeout=True,
            )
            self.client.ping()
            self._connected = True
            logger.info("Redis connected successfully")
            return True
        except RedisError as exc:
            logger.warning("Redis connection failed, cache disabled: %s", exc)
            self._connected = False
            return False

    @property
    def is_connected(self) -> bool:
        if not self._connected or not self.client:
            return False
        try:
            self.client.ping()
            return True
        except RedisError:
            self._connected = False
            return False

    @staticmethod
    def _dump_json(payload: Any) -> str:
        return json.dumps(payload, ensure_ascii=False, sort_keys=True)

    @staticmethod
    def _load_json(payload: Optional[str]) -> Optional[Any]:
        if not payload:
            return None
        try:
            return json.loads(payload)
        except json.JSONDecodeError:
            return None

    @staticmethod
    def _normalize_search_request(search_request: Any) -> Dict[str, Any]:
        def to_bool(value: Any, default: bool) -> bool:
            if value is None:
                return default
            if isinstance(value, bool):
                return value
            if isinstance(value, str):
                return value.strip().lower() in {"1", "true", "yes", "on"}
            return bool(value)

        if hasattr(search_request, "model_dump"):
            raw = search_request.model_dump()
        elif isinstance(search_request, dict):
            raw = dict(search_request)
        else:
            raw = {}

        query = str(raw.get("query", "")).strip().lower()
        top_k = int(raw.get("top_k", 5))
        enable_hybrid = to_bool(raw.get("enable_hybrid"), True)
        enable_rerank = to_bool(raw.get("enable_rerank"), True)

        return {
            "query": query,
            "top_k": top_k,
            "enable_hybrid": enable_hybrid,
            "enable_rerank": enable_rerank,
        }

    @staticmethod
    def _record_cache_event(cache_type: str, hit: bool) -> None:
        try:
            from metrics import record_cache_hit, record_cache_miss

            if hit:
                record_cache_hit(cache_type)
            else:
                record_cache_miss(cache_type)
        except Exception:
            # Metrics collection should not affect request path.
            return

    def _generate_key(self, prefix: str, content: str) -> str:
        digest = hashlib.md5(content.encode("utf-8")).hexdigest()[:16]
        return f"{prefix}{digest}"

    def _build_search_key(self, search_request: Any) -> str:
        normalized = self._normalize_search_request(search_request)
        return self._generate_key(self.PREFIX_SEARCH, self._dump_json(normalized))

    def get_search_response(self, search_request: Any) -> Optional[Dict[str, Any]]:
        """Read full search response payload from cache."""
        if not self.is_connected:
            return None
        try:
            key = self._build_search_key(search_request)
            payload = self._load_json(self.client.get(key))
            hit = isinstance(payload, dict)
            self._record_cache_event("search", hit)
            return payload if hit else None
        except RedisError as exc:
            logger.warning("Redis read error: %s", exc)
            return None

    def set_search_response(
        self,
        search_request: Any,
        response_payload: Dict[str, Any],
        ttl: Optional[int] = None,
    ) -> bool:
        """Cache full search response payload."""
        if not self.is_connected:
            return False
        try:
            key = self._build_search_key(search_request)
            self.client.setex(
                key,
                ttl if ttl is not None else self.TTL_SEARCH,
                self._dump_json(response_payload),
            )
            return True
        except RedisError as exc:
            logger.warning("Redis write error: %s", exc)
            return False

    # Legacy methods kept for backward compatibility.
    def get_search_results(self, query: str, top_k: int) -> Optional[List[Dict]]:
        cached = self.get_search_response({"query": query, "top_k": top_k})
        if cached and isinstance(cached.get("results"), list):
            return cached["results"]
        return None

    def set_search_results(self, query: str, top_k: int, results: List[Dict]) -> bool:
        payload = {"query": query, "results": results}
        return self.set_search_response({"query": query, "top_k": top_k}, payload)

    def get_embedding(self, text: str) -> Optional[List[float]]:
        if not self.is_connected:
            return None
        try:
            key = self._generate_key(self.PREFIX_EMBEDDING, text.strip())
            payload = self._load_json(self.client.get(key))
            hit = isinstance(payload, list)
            self._record_cache_event("embedding", hit)
            return payload if hit else None
        except RedisError as exc:
            logger.warning("Redis read error: %s", exc)
            return None

    def set_embedding(self, text: str, embedding: List[float]) -> bool:
        if not self.is_connected:
            return False
        try:
            key = self._generate_key(self.PREFIX_EMBEDDING, text.strip())
            self.client.setex(key, self.TTL_EMBEDDING, self._dump_json(embedding))
            return True
        except RedisError as exc:
            logger.warning("Redis write error: %s", exc)
            return False

    def get_film(self, film_id: int) -> Optional[Dict]:
        if not self.is_connected:
            return None
        try:
            key = f"{self.PREFIX_FILM}{film_id}"
            payload = self._load_json(self.client.get(key))
            hit = isinstance(payload, dict)
            self._record_cache_event("film", hit)
            return payload if hit else None
        except RedisError as exc:
            logger.warning("Redis read error: %s", exc)
            return None

    def set_film(self, film_id: int, film_data: Dict) -> bool:
        if not self.is_connected:
            return False
        try:
            key = f"{self.PREFIX_FILM}{film_id}"
            self.client.setex(key, self.TTL_FILM, self._dump_json(film_data))
            return True
        except RedisError as exc:
            logger.warning("Redis write error: %s", exc)
            return False

    def get_films_batch(self, film_ids: List[int]) -> Dict[int, Optional[Dict]]:
        result = {fid: None for fid in film_ids}
        if not self.is_connected:
            return result
        try:
            keys = [f"{self.PREFIX_FILM}{fid}" for fid in film_ids]
            values = self.client.mget(keys)
            for fid, val in zip(film_ids, values):
                decoded = self._load_json(val)
                if isinstance(decoded, dict):
                    result[fid] = decoded
            return result
        except RedisError as exc:
            logger.warning("Redis batch read error: %s", exc)
            return result

    def clear_search_cache(self) -> int:
        if not self.is_connected:
            return 0
        try:
            keys = list(self.client.scan_iter(f"{self.PREFIX_SEARCH}*"))
            return self.client.delete(*keys) if keys else 0
        except RedisError as exc:
            logger.warning("Redis delete error: %s", exc)
            return 0

    def clear_all_cache(self) -> int:
        if not self.is_connected:
            return 0
        try:
            keys = list(self.client.scan_iter("rag:*"))
            return self.client.delete(*keys) if keys else 0
        except RedisError as exc:
            logger.warning("Redis delete error: %s", exc)
            return 0

    def get_cache_stats(self) -> Dict[str, Any]:
        if not self.is_connected:
            return {"connected": False}
        try:
            info = self.client.info("memory")
            search_keys = sum(1 for _ in self.client.scan_iter(f"{self.PREFIX_SEARCH}*"))
            embed_keys = sum(1 for _ in self.client.scan_iter(f"{self.PREFIX_EMBEDDING}*"))
            film_keys = sum(1 for _ in self.client.scan_iter(f"{self.PREFIX_FILM}*"))
            return {
                "connected": True,
                "used_memory_human": info.get("used_memory_human", "N/A"),
                "search_cached": search_keys,
                "embeddings_cached": embed_keys,
                "films_cached": film_keys,
            }
        except RedisError as exc:
            return {"connected": False, "error": str(exc)}


cache_service = CacheService()


def _extract_search_request(args: tuple, kwargs: Dict[str, Any]) -> Optional[Any]:
    if "body" in kwargs:
        return kwargs["body"]
    if "search_request" in kwargs:
        return kwargs["search_request"]
    for arg in args:
        if isinstance(arg, dict) and "query" in arg:
            return arg
        if hasattr(arg, "query") and hasattr(arg, "top_k"):
            return arg
    return None


def _serialize_cache_payload(result: Any) -> Optional[Dict[str, Any]]:
    if hasattr(result, "model_dump"):
        return result.model_dump()
    if isinstance(result, dict):
        return result
    return None


def cache_search(ttl: int = CacheService.TTL_SEARCH):
    """
    Backward-compatible cache decorator.
    For FastAPI routes, prefer explicit cache read/write in route logic.
    """

    def decorator(func):
        @wraps(func)
        async def wrapper(*args, **kwargs):
            search_request = _extract_search_request(args, kwargs)
            if search_request is None:
                return await func(*args, **kwargs)

            cached = cache_service.get_search_response(search_request)
            if cached is not None:
                return cached

            result = await func(*args, **kwargs)
            payload = _serialize_cache_payload(result)
            if payload is not None:
                cache_service.set_search_response(search_request, payload, ttl=ttl)
            return result

        return wrapper

    return decorator
