from __future__ import annotations

import json

import redis

from app.config import Settings


class RedisClient:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._client = None
        self._last_error = ""

    def connect(self) -> bool:
        if self._client is not None:
            return True
        try:
            self._client = redis.Redis(
                host=self.settings.redis_host,
                port=self.settings.redis_port,
                password=self.settings.redis_password or None,
                db=self.settings.redis_db,
                decode_responses=True,
            )
            self._client.ping()
            self._last_error = ""
            return True
        except Exception as exc:
            self._client = None
            self._last_error = str(exc)
            return False

    def health(self) -> dict:
        if self.connect():
            return {"status": "connected", "detail": f"{self.settings.redis_host}:{self.settings.redis_port}"}
        return {"status": "unavailable", "detail": self._last_error or "not configured"}

    def get_json(self, key: str):
        if not self.connect():
            return None
        value = self._client.get(key)
        return json.loads(value) if value else None

    def set_json(self, key: str, value, ttl: int | None = None) -> None:
        if not self.connect():
            return
        self._client.setex(key, ttl or self.settings.redis_cache_ttl_seconds, json.dumps(value, ensure_ascii=False))

    def clear_namespace(self, prefix: str) -> None:
        if not self.connect():
            return
        keys = list(self._client.scan_iter(f"{prefix}*"))
        if keys:
            self._client.delete(*keys)
