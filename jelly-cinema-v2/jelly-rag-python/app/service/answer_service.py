from __future__ import annotations

from app.config import Settings
from app.infra.llm_client import LlmClient
from app.model.entity import ChunkRecord
from app.model.response import ReferenceItem, SearchResponse, SearchResult


class AnswerService:
    def __init__(self, settings: Settings, llm_client: LlmClient):
        self.settings = settings
        self.llm_client = llm_client

    def generate_answer(self, query: str, chunks: list[ChunkRecord]) -> str:
        if not chunks:
            return "知识库中未找到足够依据。"

        context = self._build_context(chunks[: self.settings.answer_context_limit])
        answer = self.llm_client.generate_answer(query, context)
        if answer:
            return answer

        summary = chunks[0].chunk_text.replace("\n", " ").strip()
        if len(summary) > self.settings.fallback_answer_chars:
            summary = summary[: self.settings.fallback_answer_chars].rstrip() + "..."
        return f"根据知识库检索结果，最相关的内容是：{summary}"

    def build_search_response(
        self,
        *,
        query: str,
        chunks: list[ChunkRecord],
        answer: str,
        took_ms: float,
    ) -> SearchResponse:
        return SearchResponse(
            results=[
                SearchResult(
                    document_id=chunk.document_id,
                    chunk_id=chunk.chunk_id,
                    title=chunk.title,
                    content=chunk.chunk_text,
                    score=chunk.score,
                    source=chunk.source,
                    source_type=chunk.source_type,
                    film_id=chunk.film_id,
                    knowledge_base=chunk.knowledge_base,
                )
                for chunk in chunks
            ],
            answer=answer,
            references=[
                ReferenceItem(
                    document_id=chunk.document_id,
                    title=chunk.title,
                    chunk_id=chunk.chunk_id,
                    score=chunk.score,
                )
                for chunk in chunks
            ],
            query=query,
            took_ms=took_ms,
        )

    def _build_context(self, chunks: list[ChunkRecord]) -> str:
        parts = []
        for index, chunk in enumerate(chunks, start=1):
            parts.append(
                f"[{index}] 标题：{chunk.title}\n"
                f"业务类型：{chunk.biz_type}\n"
                f"内容：{chunk.chunk_text}"
            )
        return "\n\n".join(parts)
