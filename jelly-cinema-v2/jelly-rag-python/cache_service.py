"""
Redis 缓存服务
企业级多层缓存策略

Author: Jelly Cinema Team
Version: 2.0.0
"""
import json
import hashlib
import logging
from typing import Optional, Any, List, Dict
from functools import wraps
import redis
from redis.exceptions import RedisError

from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


class CacheService:
    """
    Redis 缓存服务
    
    支持多级缓存:
    - 查询结果缓存 (TTL: 10min)
    - Embedding 缓存 (TTL: 1h)
    - 电影详情缓存 (TTL: 30min)
    """
    
    # 缓存 Key 前缀
    PREFIX_SEARCH = "rag:search:"
    PREFIX_EMBEDDING = "rag:embed:"
    PREFIX_FILM = "rag:film:"
    PREFIX_RERANK = "rag:rerank:"
    
    # TTL (秒)
    TTL_SEARCH = 600      # 10 分钟
    TTL_EMBEDDING = 3600  # 1 小时
    TTL_FILM = 1800       # 30 分钟
    TTL_RERANK = 300      # 5 分钟
    
    def __init__(self):
        self.client: Optional[redis.Redis] = None
        self._connected = False
        
    def connect(self) -> bool:
        """连接 Redis"""
        try:
            self.client = redis.Redis(
                host=settings.redis_host,
                port=settings.redis_port,
                password=settings.redis_password or None,
                db=settings.redis_db,
                decode_responses=True,
                socket_connect_timeout=5,
                socket_timeout=5,
                retry_on_timeout=True
            )
            # 测试连接
            self.client.ping()
            self._connected = True
            logger.info("✅ Redis connected successfully")
            return True
        except RedisError as e:
            logger.warning(f"⚠️ Redis connection failed: {e}. Cache disabled.")
            self._connected = False
            return False
    
    @property
    def is_connected(self) -> bool:
        """检查连接状态"""
        if not self._connected or not self.client:
            return False
        try:
            self.client.ping()
            return True
        except RedisError:
            self._connected = False
            return False
    
    def _generate_key(self, prefix: str, *args) -> str:
        """生成缓存 Key"""
        content = ":".join(str(a) for a in args)
        hash_val = hashlib.md5(content.encode()).hexdigest()[:16]
        return f"{prefix}{hash_val}"
    
    # ==================== 搜索结果缓存 ====================
    
    def get_search_results(self, query: str, top_k: int) -> Optional[List[Dict]]:
        """获取搜索结果缓存"""
        if not self.is_connected:
            return None
        try:
            key = self._generate_key(self.PREFIX_SEARCH, query.lower().strip(), top_k)
            data = self.client.get(key)
            if data:
                logger.debug(f"🎯 Cache HIT: search '{query[:20]}...'")
                return json.loads(data)
            logger.debug(f"❌ Cache MISS: search '{query[:20]}...'")
            return None
        except RedisError as e:
            logger.warning(f"Redis get error: {e}")
            return None
    
    def set_search_results(self, query: str, top_k: int, results: List[Dict]) -> bool:
        """设置搜索结果缓存"""
        if not self.is_connected:
            return False
        try:
            key = self._generate_key(self.PREFIX_SEARCH, query.lower().strip(), top_k)
            self.client.setex(key, self.TTL_SEARCH, json.dumps(results, ensure_ascii=False))
            return True
        except RedisError as e:
            logger.warning(f"Redis set error: {e}")
            return False
    
    # ==================== Embedding 缓存 ====================
    
    def get_embedding(self, text: str) -> Optional[List[float]]:
        """获取 Embedding 缓存"""
        if not self.is_connected:
            return None
        try:
            key = self._generate_key(self.PREFIX_EMBEDDING, text.strip())
            data = self.client.get(key)
            if data:
                return json.loads(data)
            return None
        except RedisError as e:
            logger.warning(f"Redis get error: {e}")
            return None
    
    def set_embedding(self, text: str, embedding: List[float]) -> bool:
        """设置 Embedding 缓存"""
        if not self.is_connected:
            return False
        try:
            key = self._generate_key(self.PREFIX_EMBEDDING, text.strip())
            self.client.setex(key, self.TTL_EMBEDDING, json.dumps(embedding))
            return True
        except RedisError as e:
            logger.warning(f"Redis set error: {e}")
            return False
    
    # ==================== 电影详情缓存 ====================
    
    def get_film(self, film_id: int) -> Optional[Dict]:
        """获取电影详情缓存"""
        if not self.is_connected:
            return None
        try:
            key = f"{self.PREFIX_FILM}{film_id}"
            data = self.client.get(key)
            if data:
                return json.loads(data)
            return None
        except RedisError as e:
            logger.warning(f"Redis get error: {e}")
            return None
    
    def set_film(self, film_id: int, film_data: Dict) -> bool:
        """设置电影详情缓存"""
        if not self.is_connected:
            return False
        try:
            key = f"{self.PREFIX_FILM}{film_id}"
            self.client.setex(key, self.TTL_FILM, json.dumps(film_data, ensure_ascii=False))
            return True
        except RedisError as e:
            logger.warning(f"Redis set error: {e}")
            return False
    
    def get_films_batch(self, film_ids: List[int]) -> Dict[int, Optional[Dict]]:
        """批量获取电影详情缓存"""
        result = {fid: None for fid in film_ids}
        if not self.is_connected:
            return result
        try:
            keys = [f"{self.PREFIX_FILM}{fid}" for fid in film_ids]
            values = self.client.mget(keys)
            for fid, val in zip(film_ids, values):
                if val:
                    result[fid] = json.loads(val)
            return result
        except RedisError as e:
            logger.warning(f"Redis mget error: {e}")
            return result
    
    # ==================== 缓存管理 ====================
    
    def clear_search_cache(self) -> int:
        """清空搜索缓存"""
        if not self.is_connected:
            return 0
        try:
            keys = self.client.keys(f"{self.PREFIX_SEARCH}*")
            if keys:
                return self.client.delete(*keys)
            return 0
        except RedisError as e:
            logger.warning(f"Redis delete error: {e}")
            return 0
    
    def clear_all_cache(self) -> int:
        """清空所有 RAG 缓存"""
        if not self.is_connected:
            return 0
        try:
            keys = self.client.keys("rag:*")
            if keys:
                return self.client.delete(*keys)
            return 0
        except RedisError as e:
            logger.warning(f"Redis delete error: {e}")
            return 0
    
    def get_cache_stats(self) -> Dict[str, Any]:
        """获取缓存统计"""
        if not self.is_connected:
            return {"connected": False}
        try:
            info = self.client.info("memory")
            search_keys = len(self.client.keys(f"{self.PREFIX_SEARCH}*"))
            embed_keys = len(self.client.keys(f"{self.PREFIX_EMBEDDING}*"))
            film_keys = len(self.client.keys(f"{self.PREFIX_FILM}*"))
            return {
                "connected": True,
                "used_memory_human": info.get("used_memory_human", "N/A"),
                "search_cached": search_keys,
                "embeddings_cached": embed_keys,
                "films_cached": film_keys
            }
        except RedisError as e:
            return {"connected": False, "error": str(e)}


# 全局单例
cache_service = CacheService()


def cache_search(ttl: int = CacheService.TTL_SEARCH):
    """搜索结果缓存装饰器"""
    def decorator(func):
        @wraps(func)
        async def wrapper(query: str, top_k: int = 5, *args, **kwargs):
            # 尝试从缓存获取
            cached = cache_service.get_search_results(query, top_k)
            if cached is not None:
                return cached
            
            # 执行原函数
            result = await func(query, top_k, *args, **kwargs)
            
            # 存入缓存
            if result:
                cache_service.set_search_results(query, top_k, result)
            
            return result
        return wrapper
    return decorator
