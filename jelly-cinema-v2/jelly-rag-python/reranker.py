"""
Cross-encoder reranker with lazy dependency loading.
"""
import logging
from typing import Dict, List

from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


class Reranker:
    MODELS = {
        "bge-reranker-base": "BAAI/bge-reranker-base",
        "bge-reranker-large": "BAAI/bge-reranker-large",
        "ms-marco": "cross-encoder/ms-marco-MiniLM-L-6-v2",
    }

    def __init__(self, model_name: str = "bge-reranker-base"):
        self.model = None
        self.tokenizer = None
        self.device = "cpu"
        self.model_name = model_name
        self._loaded = False
        self._torch = None

    def load_model(self) -> bool:
        try:
            import torch
            from transformers import AutoModelForSequenceClassification, AutoTokenizer

            self._torch = torch
            self.device = "cuda" if torch.cuda.is_available() else "cpu"
            model_path = self.MODELS.get(self.model_name, self.model_name)
            logger.info("Loading reranker model: %s", model_path)

            self.tokenizer = AutoTokenizer.from_pretrained(model_path)
            self.model = AutoModelForSequenceClassification.from_pretrained(model_path)
            self.model.to(self.device)
            self.model.eval()

            self._loaded = True
            logger.info("Reranker loaded on %s", self.device)
            return True
        except Exception as exc:
            logger.error("Failed to load reranker: %s", exc)
            self._loaded = False
            return False

    @property
    def is_ready(self) -> bool:
        return self._loaded and self.model is not None and self._torch is not None

    def compute_score(self, query: str, document: str) -> float:
        if not self.is_ready:
            return 0.5
        try:
            inputs = self.tokenizer(
                [[query, document]],
                padding=True,
                truncation=True,
                max_length=512,
                return_tensors="pt",
            ).to(self.device)

            with self._torch.no_grad():
                scores = self.model(**inputs).logits.squeeze()
                if len(scores.shape) == 0:
                    return self._torch.sigmoid(scores).item()
                return self._torch.sigmoid(scores[0]).item()
        except Exception as exc:
            logger.warning("Rerank score failed: %s", exc)
            return 0.5

    def rerank(
        self,
        query: str,
        documents: List[Dict],
        top_k: int = 5,
        content_key: str = "content",
    ) -> List[Dict]:
        if not self.is_ready or not documents:
            return documents[:top_k]
        try:
            scored_docs = []
            for doc in documents:
                content = doc.get(content_key, "")
                score = self.compute_score(query, content)
                scored_docs.append({**doc, "rerank_score": score})
            scored_docs.sort(key=lambda x: x.get("rerank_score", 0), reverse=True)
            return scored_docs[:top_k]
        except Exception as exc:
            logger.error("Rerank failed: %s", exc)
            return documents[:top_k]

    def rerank_batch(
        self,
        query: str,
        documents: List[Dict],
        top_k: int = 5,
        content_key: str = "content",
        batch_size: int = 16,
    ) -> List[Dict]:
        if not self.is_ready or not documents:
            return documents[:top_k]
        try:
            all_scores: List[float] = []
            for i in range(0, len(documents), batch_size):
                batch = documents[i : i + batch_size]
                pairs = [[query, doc.get(content_key, "")] for doc in batch]
                inputs = self.tokenizer(
                    pairs,
                    padding=True,
                    truncation=True,
                    max_length=512,
                    return_tensors="pt",
                ).to(self.device)

                with self._torch.no_grad():
                    scores = self.model(**inputs).logits.squeeze(-1)
                    scores = self._torch.sigmoid(scores).cpu().tolist()
                    if isinstance(scores, float):
                        scores = [scores]
                    all_scores.extend(scores)

            scored_docs = [
                {**doc, "rerank_score": score}
                for doc, score in zip(documents, all_scores)
            ]
            scored_docs.sort(key=lambda x: x.get("rerank_score", 0), reverse=True)
            return scored_docs[:top_k]
        except Exception as exc:
            logger.error("Batch rerank failed: %s", exc)
            return documents[:top_k]


reranker = Reranker(model_name=settings.reranker_model)


def init_reranker() -> bool:
    if settings.enable_reranker:
        return reranker.load_model()
    logger.info("Reranker disabled by config")
    return False
