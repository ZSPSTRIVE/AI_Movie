from __future__ import annotations

import json
import logging
from hashlib import sha256
from pathlib import Path

from app.config import Settings
from app.infra.redis_client import RedisClient
from app.infra.tvbox_client import TvboxClient
from app.model.request import IngestRequest
from app.model.response import SyncResponse
from app.service.ingest_service import IngestService

logger = logging.getLogger(__name__)


class MovieKnowledgeService:
    def __init__(
        self,
        *,
        settings: Settings,
        tvbox_client: TvboxClient,
        ingest_service: IngestService,
        redis_client: RedisClient,
    ):
        self.settings = settings
        self.tvbox_client = tvbox_client
        self.ingest_service = ingest_service
        self.redis_client = redis_client

    @property
    def knowledge_file_path(self) -> Path:
        return self.settings.knowledge_base_path / self.settings.movie_dynamic_kb_file

    def maybe_enrich_for_search(self, query: str, biz_type: str | None) -> None:
        if not self._should_sync(query, biz_type):
            return

        cache_key = "rag:movie-sync:" + sha256(f"{biz_type or ''}|{query}".encode("utf-8")).hexdigest()
        if self.redis_client.get_json(cache_key):
            return

        try:
            response = self.sync(query=query, limit=self.settings.movie_search_sync_limit)
            self.redis_client.set_json(
                cache_key,
                {"count": response.count, "message": response.message},
                ttl=self.settings.movie_sync_ttl_seconds,
            )
        except Exception as exc:  # pragma: no cover
            logger.warning("TVBox auto sync skipped for query=%s: %s", query, exc)

    def sync(self, *, query: str | None = None, limit: int | None = None) -> SyncResponse:
        safe_limit = max(1, min(limit or self.settings.movie_snapshot_limit, 50))
        films = self._fetch_films(query=query, limit=safe_limit)
        if not films:
            mode = "search" if query else "recommend"
            return SyncResponse(
                success=True,
                count=0,
                message=f"No TVBox {mode} data found",
                file_path=str(self.knowledge_file_path),
            )

        records = self._build_records(films, query=query)
        self._upsert_jsonl(records)
        for record in records:
            self.ingest_service.ingest(
                IngestRequest(
                    title=record["title"],
                    content=record["content"],
                    biz_type=self.settings.movie_biz_type,
                    source_type=record["source_type"],
                    source_path=record["source_path"],
                    replace_by_source=True,
                )
            )

        mode = f"query={query}" if query else "recommend-snapshot"
        return SyncResponse(
            success=True,
            count=len(records),
            message=f"Synced {len(records)} movies from TVBox ({mode})",
            file_path=str(self.knowledge_file_path),
        )

    def _fetch_films(self, *, query: str | None, limit: int) -> list[dict]:
        films = self.tvbox_client.search(query, limit) if query else self.tvbox_client.recommend(limit)
        enriched: list[dict] = []
        for index, film in enumerate(films):
            merged = dict(film)
            film_id = str(film.get("id") or "").strip()
            if film_id and index < self.settings.movie_detail_sync_limit:
                try:
                    detail = self.tvbox_client.detail(film_id)
                    if detail:
                        merged.update(detail)
                except Exception as exc:  # pragma: no cover
                    logger.warning("TVBox detail skipped for %s: %s", film_id, exc)
            enriched.append(merged)
        return enriched

    def _build_records(self, films: list[dict], *, query: str | None) -> list[dict]:
        records: list[dict] = []
        for film in films:
            movie_id = str(film.get("id") or "").strip()
            title = str(film.get("title") or "").strip()
            if not movie_id or not title:
                continue

            source_name = str(film.get("sourceName") or film.get("sourceKey") or "tvbox").strip()
            episodes = film.get("episodes") if isinstance(film.get("episodes"), list) else []
            year = film.get("year") or ""
            rating = film.get("rating") or ""
            region = film.get("region") or ""
            director = film.get("director") or ""
            actors = film.get("actors") or ""
            description = str(film.get("description") or "").strip()

            content_parts = [
                f"片名：{title}",
                f"来源站点：{source_name}",
                f"年份：{year}" if year else "",
                f"评分：{rating}" if rating else "",
                f"地区：{region}" if region else "",
                f"导演：{director}" if director else "",
                f"主演：{actors}" if actors else "",
                f"剧集/线路数：{len(episodes)}" if episodes else "",
                f"简介：{description}" if description else "",
                f"检索线索：{query}" if query else "检索线索：后台推荐池快照",
                "用途：用于影视搜索、电影问答、首页推荐解释和本地知识库补充。",
            ]
            tags = ["电影", "影视资源", source_name]
            if year:
                tags.append(str(year))
            if region:
                tags.append(str(region))

            records.append(
                {
                    "movie_id": movie_id,
                    "title": title,
                    "content": "\n".join(part for part in content_parts if part),
                    "tags": [tag for tag in tags if tag],
                    "source_name": source_name,
                    "source_type": "tvbox_search" if query else "tvbox_snapshot",
                    "source_path": f"tvbox://movie/{movie_id}",
                }
            )
        return records

    def _upsert_jsonl(self, records: list[dict]) -> None:
        file_path = self.knowledge_file_path
        file_path.parent.mkdir(parents=True, exist_ok=True)
        existing = self._load_jsonl(file_path)
        for record in records:
            existing[str(record["movie_id"])] = record

        ordered = sorted(existing.values(), key=lambda item: (str(item.get("title") or ""), str(item.get("movie_id") or "")))
        file_path.write_text(
            "\n".join(json.dumps(item, ensure_ascii=False) for item in ordered) + ("\n" if ordered else ""),
            encoding="utf-8",
        )

    def _load_jsonl(self, path: Path) -> dict[str, dict]:
        if not path.exists():
            return {}

        data: dict[str, dict] = {}
        for line in path.read_text(encoding="utf-8").splitlines():
            raw = line.strip()
            if not raw:
                continue
            item = json.loads(raw)
            movie_id = str(item.get("movie_id") or "").strip()
            if not movie_id:
                continue
            data[movie_id] = item
        return data

    def _should_sync(self, query: str, biz_type: str | None) -> bool:
        if not self.settings.movie_auto_sync_enabled:
            return False
        text = query.strip()
        if not text:
            return False
        normalized_biz_type = (biz_type or "").strip().lower()
        if normalized_biz_type and normalized_biz_type not in {
            self.settings.movie_biz_type,
            "film",
            "cinema",
            "video",
            "general",
        }:
            return False
        if normalized_biz_type in {self.settings.movie_biz_type, "film", "cinema", "video"}:
            return True

        # 只对“像电影查询”的请求做增量同步，避免普通业务问答被影视数据污染。
        movie_keywords = ("电影", "影片", "电视剧", "综艺", "动漫", "导演", "演员", "主演", "上映", "票房", "豆瓣", "爱奇艺")
        business_keywords = ("会员", "退款", "支付", "风控", "客服", "运营", "增长", "合规", "政策", "规则")
        if any(keyword in text for keyword in movie_keywords):
            return True
        if any(keyword in text for keyword in business_keywords):
            return False
        return len(text) <= 12
