from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, Field, model_validator


class IngestRequest(BaseModel):
    title: str = Field(..., min_length=1, max_length=255)
    content: Optional[str] = None
    file_path: Optional[str] = None
    biz_type: str = Field(default="general", min_length=1, max_length=32)
    source_type: str = Field(default="manual", min_length=1, max_length=32)
    source_path: Optional[str] = None
    created_by: Optional[int] = None
    replace_by_source: bool = False

    @model_validator(mode="after")
    def validate_source(self):
        if not (self.content and self.content.strip()) and not (self.file_path and self.file_path.strip()):
            raise ValueError("Either content or file_path is required")
        return self


class SearchRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=1000)
    top_k: int = Field(default=5, ge=1, le=20)
    biz_type: Optional[str] = None


class MovieSyncRequest(BaseModel):
    query: Optional[str] = Field(default=None, max_length=255)
    limit: int = Field(default=12, ge=1, le=50)
