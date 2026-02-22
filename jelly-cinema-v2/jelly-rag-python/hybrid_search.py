"""
Hybrid retrieval service: BM25 + vector + RRF fusion.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple

from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


@dataclass
class SearchResult:
    film_id: int
    title: str
    content: str
    score: float
    source: str
    source_type: str = "film"
    knowledge_base: Optional[str] = None


class HybridSearcher:
    def __init__(self):
        self.bm25_index = None
        self.corpus: List[List[str]] = []
        self.film_data: List[Dict] = []
        self._indexed = False

    def build_bm25_index(self, films: List[Dict]) -> bool:
        try:
            from rank_bm25 import BM25Okapi
            import jieba

            self.film_data = films
            self.corpus = []
            for film in films:
                content = film.get("content", "")
                title = film.get("title", "")
                full_text = f"{title} {content}"
                tokens = list(jieba.cut(full_text))
                self.corpus.append(tokens)

            self.bm25_index = BM25Okapi(self.corpus)
            self._indexed = True
            logger.info("BM25 index built with %s documents", len(films))
            return True
        except ImportError:
            logger.warning("rank_bm25/jieba not installed, BM25 disabled")
            return False
        except Exception as exc:
            logger.error("BM25 index build failed: %s", exc)
            return False

    def bm25_search(self, query: str, top_k: int = 20) -> List[SearchResult]:
        if not self._indexed or not self.bm25_index:
            return []

        try:
            import jieba

            query_tokens = list(jieba.cut(query))
            scores = self.bm25_index.get_scores(query_tokens)
            indexed_scores = [(i, s) for i, s in enumerate(scores)]
            indexed_scores.sort(key=lambda x: x[1], reverse=True)

            results: List[SearchResult] = []
            for idx, score in indexed_scores[:top_k]:
                if score <= 0:
                    continue
                item = self.film_data[idx]
                results.append(
                    SearchResult(
                        film_id=item.get("film_id", 0),
                        title=item.get("title", ""),
                        content=item.get("content", ""),
                        score=float(score),
                        source=item.get("source", "bm25"),
                        source_type=item.get("source_type", "film"),
                        knowledge_base=item.get("knowledge_base"),
                    )
                )
            return results
        except Exception as exc:
            logger.error("BM25 search failed: %s", exc)
            return []

    @staticmethod
    def rrf_fusion(
        results_list: List[List[SearchResult]],
        k: int = 60,
        top_k: int = 10,
    ) -> List[SearchResult]:
        fusion_scores: Dict[int, Tuple[float, SearchResult]] = {}

        for results in results_list:
            for rank, result in enumerate(results):
                score = 1.0 / (k + rank + 1)
                if result.film_id in fusion_scores:
                    old_score, old_result = fusion_scores[result.film_id]
                    fusion_scores[result.film_id] = (old_score + score, old_result)
                else:
                    fusion_scores[result.film_id] = (score, result)

        sorted_items = sorted(fusion_scores.values(), key=lambda x: x[0], reverse=True)

        fused: List[SearchResult] = []
        for score, result in sorted_items[:top_k]:
            fused.append(
                SearchResult(
                    film_id=result.film_id,
                    title=result.title,
                    content=result.content,
                    score=score,
                    source="hybrid",
                    source_type=result.source_type,
                    knowledge_base=result.knowledge_base,
                )
            )
        return fused


hybrid_searcher = HybridSearcher()


def hybrid_search(query: str, vector_results: List[Dict], top_k: int = 5) -> List[Dict]:
    if not hybrid_searcher._indexed:
        return vector_results[:top_k]

    try:
        bm25_results = hybrid_searcher.bm25_search(query, top_k=20)
        vector = [
            SearchResult(
                film_id=r.get("film_id", 0),
                title=r.get("title", ""),
                content=r.get("content", ""),
                score=r.get("score", 0),
                source=r.get("source", "vector"),
                source_type=r.get("source_type", "film"),
                knowledge_base=r.get("knowledge_base"),
            )
            for r in vector_results
        ]

        if bm25_results:
            fused = HybridSearcher.rrf_fusion([vector, bm25_results], top_k=top_k)
            logger.info(
                "Hybrid search: %s vector + %s bm25 -> %s fused",
                len(vector),
                len(bm25_results),
                len(fused),
            )
        else:
            fused = vector[:top_k]

        return [
            {
                "film_id": r.film_id,
                "title": r.title,
                "content": r.content,
                "score": r.score,
                "source": r.source,
                "source_type": r.source_type,
                "knowledge_base": r.knowledge_base,
            }
            for r in fused
        ]
    except Exception as exc:
        logger.error("Hybrid search failed: %s", exc)
        return vector_results[:top_k]
