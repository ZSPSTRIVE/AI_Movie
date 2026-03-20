from __future__ import annotations

import re

from app.config import Settings
from app.infra.embedding_client import EmbeddingClient
from app.infra.milvus_store import MilvusStore
from app.infra.pg_client import PgClient
from app.model.entity import ChunkRecord


class RetrievalService:
    def __init__(
        self,
        *,
        settings: Settings,
        pg_client: PgClient,
        milvus_store: MilvusStore,
        embedding_client: EmbeddingClient,
    ):
        self.settings = settings
        self.pg_client = pg_client
        self.milvus_store = milvus_store
        self.embedding_client = embedding_client

    def retrieve(self, query: str, *, top_k: int, biz_type: str | None = None) -> list[ChunkRecord]:
        try:
            query_embedding = self.embedding_client.embed_text(query)
            hits = self.milvus_store.search(query_embedding, top_k=top_k, biz_type=biz_type)
        except Exception:
            hits = []
        if not hits:
            return self._fallback_keyword_search(query, top_k=top_k, biz_type=biz_type)

        chunk_rows = self.pg_client.fetch_chunks_by_ids([hit["chunk_id"] for hit in hits])
        row_map = {int(row["chunk_id"]): row for row in chunk_rows}
        chunks: list[ChunkRecord] = []
        for hit in hits:
            row = row_map.get(hit["chunk_id"])
            if not row:
                continue
            chunks.append(
                ChunkRecord(
                    chunk_id=int(row["chunk_id"]),
                    document_id=int(row["document_id"]),
                    title=row["title"],
                    chunk_text=row["chunk_text"],
                    score=float(hit["score"]),
                    biz_type=row["biz_type"],
                    source="vector",
                    source_type=row.get("source_type", "knowledge"),
                    film_id=self._extract_film_id(row.get("source_path")),
                    knowledge_base=self._resolve_knowledge_base(row),
                )
            )
        return chunks

    def _fallback_keyword_search(self, query: str, *, top_k: int, biz_type: str | None) -> list[ChunkRecord]:
        # Milvus/embedding 不可用时，退化到最近入库 chunk 的关键词检索，保证服务不断。
        rows = self.pg_client.fetch_recent_chunks(self.settings.keyword_fallback_candidates, biz_type=biz_type)
        query_text = query.strip()
        if not query_text or not rows:
            return []

        scored: list[ChunkRecord] = []
        for row in rows:
            score = self._keyword_score(query_text, row["title"], row["chunk_text"])
            if score <= 0:
                continue
            scored.append(
                ChunkRecord(
                    chunk_id=int(row["chunk_id"]),
                    document_id=int(row["document_id"]),
                    title=row["title"],
                    chunk_text=row["chunk_text"],
                    score=score,
                    biz_type=row["biz_type"],
                    source="keyword",
                    source_type=row.get("source_type", "knowledge"),
                    film_id=self._extract_film_id(row.get("source_path")),
                    knowledge_base=self._resolve_knowledge_base(row),
                )
            )

        scored.sort(key=lambda item: item.score, reverse=True)
        return scored[:top_k]

    def _keyword_score(self, query: str, title: str, chunk_text: str) -> float:
        title_lower = title.lower()
        chunk_lower = chunk_text.lower()
        query_lower = query.lower()
        tokens = self._extract_terms(query)

        score = 0.0
        if query_lower in title_lower:
            score += 12.0
        if query_lower in chunk_lower:
            score += 9.0

        for token in tokens:
            token_lower = token.lower()
            if token_lower in title_lower:
                score += min(6.0, 2.0 + len(token) * 0.5)
            occurrences = chunk_lower.count(token_lower)
            if occurrences:
                score += min(5.0, occurrences * max(1.0, len(token) / 3.0))
        return score

    def _extract_terms(self, query: str) -> list[str]:
        lowered = query.strip().lower()
        terms: list[str] = []
        if lowered:
            terms.append(lowered)
        terms.extend(re.findall(r"[a-z0-9]{2,}", lowered))
        for block in re.findall(r"[\u4e00-\u9fff]{2,}", query):
            terms.append(block)
            if len(block) > 4:
                for size in (2, 3, 4):
                    for index in range(0, len(block) - size + 1):
                        terms.append(block[index : index + size])

        unique: list[str] = []
        seen = set()
        for term in sorted((item.strip() for item in terms if item.strip()), key=len, reverse=True):
            if term in seen:
                continue
            seen.add(term)
            unique.append(term)
        return unique[:16]

    def _extract_film_id(self, source_path: str | None) -> int | None:
        if not source_path:
            return None
        match = re.search(r"/(\d+)$", source_path.strip())
        if not match:
            return None
        try:
            return int(match.group(1))
        except ValueError:
            return None

    def _resolve_knowledge_base(self, row: dict) -> str | None:
        file_name = row.get("file_name")
        if isinstance(file_name, str) and file_name.strip():
            return file_name.strip()

        source_path = row.get("source_path")
        if not isinstance(source_path, str) or not source_path.strip():
            return None

        normalized = source_path.strip().replace("\\", "/")
        if normalized.startswith("mysql://"):
            return "mysql:t_film"
        if normalized.startswith("tvbox://"):
            return "tvbox"
        return normalized.rsplit("/", 1)[-1] or normalized
