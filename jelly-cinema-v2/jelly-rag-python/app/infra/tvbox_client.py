from __future__ import annotations

from urllib.parse import quote

import httpx

from app.config import Settings


class TvboxClient:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.base_url = settings.tvbox_proxy_base_url.rstrip("/")

    def recommend(self, limit: int) -> list[dict]:
        data = self._get_json("/api/tvbox/recommend", params={"limit": limit})
        return data if isinstance(data, list) else []

    def search(self, keyword: str, limit: int) -> list[dict]:
        data = self._get_json("/api/tvbox/search", params={"keyword": keyword})
        if not isinstance(data, list):
            return []
        return data[:limit]

    def detail(self, film_id: str) -> dict:
        encoded_id = quote(str(film_id), safe="")
        data = self._get_json(f"/api/tvbox/detail/{encoded_id}")
        return data if isinstance(data, dict) else {}

    def _get_json(self, path: str, params: dict | None = None):
        url = f"{self.base_url}{path}"
        response = httpx.get(url, params=params, timeout=self.settings.tvbox_timeout_seconds)
        response.raise_for_status()
        payload = response.json()
        code = payload.get("code", 200)
        if code != 200:
            raise RuntimeError(f"TVBox proxy returned code={code} for {url}")
        return payload.get("data")
