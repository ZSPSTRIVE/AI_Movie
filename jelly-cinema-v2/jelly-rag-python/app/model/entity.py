from __future__ import annotations

from dataclasses import dataclass


@dataclass
class ChunkRecord:
    chunk_id: int
    document_id: int
    title: str
    chunk_text: str
    score: float
    biz_type: str
    source: str = "vector"
    source_type: str = "knowledge"
