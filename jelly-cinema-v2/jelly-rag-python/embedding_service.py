"""
Embedding service with lazy model loading.
"""
from typing import Any, List, Optional
import logging

from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()

SentenceTransformer = None
_model: Optional[Any] = None


def _get_model_class():
    global SentenceTransformer
    if SentenceTransformer is not None:
        return SentenceTransformer
    try:
        from sentence_transformers import SentenceTransformer as _SentenceTransformer

        SentenceTransformer = _SentenceTransformer
        return SentenceTransformer
    except Exception as exc:
        raise RuntimeError(f"Cannot import sentence-transformers: {exc}") from exc


def get_embedding_model():
    """Return singleton embedding model."""
    global _model
    if _model is None:
        model_cls = _get_model_class()
        logger.info("Loading embedding model: %s", settings.embedding_model)
        _model = model_cls(settings.embedding_model)
        logger.info(
            "Embedding model loaded, dim=%s",
            _model.get_sentence_embedding_dimension(),
        )
    return _model


def embed_text(text: str) -> List[float]:
    model = get_embedding_model()
    embedding = model.encode(text, convert_to_numpy=True)
    return embedding.tolist()


def embed_texts(texts: List[str]) -> List[List[float]]:
    model = get_embedding_model()
    embeddings = model.encode(texts, convert_to_numpy=True, show_progress_bar=True)
    return embeddings.tolist()


def build_film_content(film: dict) -> str:
    """Build searchable film text from structured fields."""
    parts: List[str] = []

    if film.get("title"):
        parts.append(f"电影名称：{film['title']}")
    if film.get("category_name"):
        parts.append(f"类型：{film['category_name']}")
    if film.get("year"):
        parts.append(f"年份：{film['year']}")
    if film.get("region"):
        parts.append(f"地区：{film['region']}")
    if film.get("director"):
        parts.append(f"导演：{film['director']}")
    if film.get("actors"):
        parts.append(f"主演：{film['actors']}")
    if film.get("rating"):
        parts.append(f"评分：{film['rating']}")
    if film.get("description"):
        parts.append(f"简介：{film['description']}")

    return "\n".join(parts)
