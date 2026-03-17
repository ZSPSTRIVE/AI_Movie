from __future__ import annotations

import hashlib
import time

from fastapi import APIRouter, HTTPException, Request

from app.model.request import IngestRequest, MovieSyncRequest, SearchRequest
from app.model.response import IngestResponse, SearchResponse, SyncResponse

router = APIRouter(prefix="/rag", tags=["rag"])


def _cache_key(query: str, top_k: int, biz_type: str | None) -> str:
    raw = f"{query}|{top_k}|{biz_type or ''}"
    return "rag:search:" + hashlib.sha256(raw.encode("utf-8")).hexdigest()


@router.post("/ingest", response_model=IngestResponse)
async def ingest(body: IngestRequest, request: Request) -> IngestResponse:
    service = request.app.state.ingest_service
    try:
        return service.ingest(body)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover
        raise HTTPException(status_code=500, detail=f"Ingest failed: {exc}") from exc


@router.post("/search", response_model=SearchResponse)
async def search(body: SearchRequest, request: Request) -> SearchResponse:
    retrieval_service = request.app.state.retrieval_service
    answer_service = request.app.state.answer_service
    redis_client = request.app.state.redis_client
    movie_knowledge_service = request.app.state.movie_knowledge_service

    movie_knowledge_service.maybe_enrich_for_search(body.query, body.biz_type)

    top_k = min(body.top_k, request.app.state.settings.max_top_k)
    cache_key = _cache_key(body.query, top_k, body.biz_type)
    cached = redis_client.get_json(cache_key)
    if cached:
        return SearchResponse(**cached)

    started = time.perf_counter()
    chunks = retrieval_service.retrieve(body.query, top_k=top_k, biz_type=body.biz_type)
    answer = answer_service.generate_answer(body.query, chunks)
    response = answer_service.build_search_response(
        query=body.query,
        chunks=chunks,
        answer=answer,
        took_ms=(time.perf_counter() - started) * 1000,
    )
    redis_client.set_json(cache_key, response.model_dump())
    return response


@router.post("/sync", response_model=SyncResponse)
async def sync(request: Request) -> SyncResponse:
    service = request.app.state.ingest_service
    try:
        return service.rebuild_from_directory()
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover
        raise HTTPException(status_code=500, detail=f"Sync failed: {exc}") from exc


@router.post("/movie/sync", response_model=SyncResponse)
async def movie_sync(body: MovieSyncRequest, request: Request) -> SyncResponse:
    service = request.app.state.movie_knowledge_service
    try:
        return service.sync(query=body.query, limit=body.limit)
    except Exception as exc:  # pragma: no cover
        raise HTTPException(status_code=500, detail=f"Movie sync failed: {exc}") from exc
