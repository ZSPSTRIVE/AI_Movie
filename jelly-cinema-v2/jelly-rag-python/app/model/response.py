from __future__ import annotations

from typing import List, Optional

from pydantic import BaseModel


class SearchResult(BaseModel):
    document_id: int
    chunk_id: int
    title: str
    content: str
    score: float
    source: str = "vector"
    source_type: str = "knowledge"
    film_id: Optional[int] = None
    knowledge_base: Optional[str] = None


class ReferenceItem(BaseModel):
    document_id: int
    title: str
    chunk_id: int
    score: float


class SearchResponse(BaseModel):
    results: List[SearchResult]
    answer: str
    references: List[ReferenceItem]
    query: str
    took_ms: float


class IngestResponse(BaseModel):
    success: bool
    document_id: int
    chunks: int
    message: str


class SyncResponse(BaseModel):
    success: bool
    count: int
    message: str
    file_path: Optional[str] = None
