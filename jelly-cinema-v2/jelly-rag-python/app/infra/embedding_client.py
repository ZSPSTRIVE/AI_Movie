from __future__ import annotations

import logging
from typing import List

import httpx

from app.config import Settings

logger = logging.getLogger(__name__)


class EmbeddingClient:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._local_model = None

    def health(self) -> dict:
        if self._uses_remote():
            return {"status": "configured", "detail": self.settings.ai_embedding_model}
        return {"status": "local", "detail": self.settings.embedding_model_local}

    def embed_text(self, text: str) -> List[float]:
        return self.embed_texts([text])[0]

    def embed_texts(self, texts: list[str]) -> list[list[float]]:
        cleaned = [text.strip() for text in texts if text and text.strip()]
        if not cleaned:
            raise ValueError("No text available for embedding")

        if self._uses_remote():
            try:
                return self._embed_remote(cleaned)
            except Exception as exc:
                logger.warning("Remote embedding failed, fallback to local model: %s", exc)

        return self._embed_local(cleaned)

    def _uses_remote(self) -> bool:
        return bool(self.settings.ai_api_key and self.settings.ai_embedding_model and self.settings.ai_base_url)

    def _embed_remote(self, texts: list[str]) -> list[list[float]]:
        payload = {"model": self.settings.ai_embedding_model, "input": texts}
        headers = {
            "Authorization": f"Bearer {self.settings.ai_api_key}",
            "Content-Type": "application/json",
        }
        url = self.settings.ai_base_url.rstrip("/") + "/embeddings"
        with httpx.Client(timeout=self.settings.ai_timeout_seconds) as client:
            response = client.post(url, json=payload, headers=headers)
            response.raise_for_status()
            data = response.json().get("data", [])
        if not data:
            raise RuntimeError("Embedding API returned empty data")
        data = sorted(data, key=lambda item: item.get("index", 0))
        return [item["embedding"] for item in data]

    def _embed_local(self, texts: list[str]) -> list[list[float]]:
        model = self._get_local_model()
        embeddings = model.encode(
            texts,
            batch_size=self.settings.embedding_batch_size,
            convert_to_numpy=True,
            normalize_embeddings=True,
            show_progress_bar=False,
        )
        return embeddings.tolist()

    def _get_local_model(self):
        if self._local_model is None:
            from sentence_transformers import SentenceTransformer

            logger.info("Loading local embedding model: %s", self.settings.embedding_model_local)
            self._local_model = SentenceTransformer(self.settings.embedding_model_local)
        return self._local_model
