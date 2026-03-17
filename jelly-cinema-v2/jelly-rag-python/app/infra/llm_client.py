from __future__ import annotations

import logging

import httpx

from app.config import Settings
from app.rag.prompt import build_messages

logger = logging.getLogger(__name__)


class LlmClient:
    def __init__(self, settings: Settings):
        self.settings = settings

    def health(self) -> dict:
        if self.is_configured():
            return {"status": "configured", "detail": self.settings.ai_chat_model}
        return {"status": "fallback", "detail": "answer generated from retrieved chunks"}

    def is_configured(self) -> bool:
        return bool(self.settings.ai_api_key and self.settings.ai_chat_model and self.settings.ai_base_url)

    def generate_answer(self, query: str, context: str) -> str:
        if not self.is_configured():
            return ""

        payload = {
            "model": self.settings.ai_chat_model,
            "temperature": 0.2,
            "messages": build_messages(query=query, context=context),
        }
        headers = {
            "Authorization": f"Bearer {self.settings.ai_api_key}",
            "Content-Type": "application/json",
        }
        url = self.settings.ai_base_url.rstrip("/") + "/chat/completions"

        with httpx.Client(timeout=self.settings.ai_timeout_seconds) as client:
            response = client.post(url, json=payload, headers=headers)
            response.raise_for_status()
            data = response.json()

        try:
            return data["choices"][0]["message"]["content"].strip()
        except (KeyError, IndexError, TypeError) as exc:
            logger.warning("Unexpected LLM response payload: %s", data)
            raise RuntimeError("Invalid LLM response payload") from exc
