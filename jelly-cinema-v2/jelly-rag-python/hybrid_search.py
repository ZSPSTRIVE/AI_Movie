"""
混合检索服务
BM25 + Vector + RRF 融合

Author: Jelly Cinema Team
Version: 2.0.0
"""
import logging
from typing import List, Dict, Optional, Tuple
from dataclasses import dataclass
import math

from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


@dataclass
class SearchResult:
    """检索结果"""
    film_id: int
    title: str
    content: str
    score: float
    source: str  # "bm25" or "vector"


class HybridSearcher:
    """
    混合检索器
    
    融合 BM25 稀疏检索和 Vector 稠密检索:
    1. BM25 - 关键词精确匹配 (适合专有名词、人名等)
    2. Vector - 语义相似度 (适合同义词、概念匹配)
    3. RRF - 融合两种结果，取长补短
    """
    
    def __init__(self):
        self.bm25_index = None
        self.corpus = []
        self.film_data = []
        self._indexed = False
        
    def build_bm25_index(self, films: List[Dict]) -> bool:
        """
        构建 BM25 索引
        
        Args:
            films: 电影列表，包含 film_id, title, content
        """
        try:
            from rank_bm25 import BM25Okapi
            import jieba
            
            self.film_data = films
            self.corpus = []
            
            for film in films:
                content = film.get("content", "")
                title = film.get("title", "")
                full_text = f"{title} {content}"
                # 中文分词
                tokens = list(jieba.cut(full_text))
                self.corpus.append(tokens)
            
            self.bm25_index = BM25Okapi(self.corpus)
            self._indexed = True
            logger.info(f"✅ BM25 index built with {len(films)} documents")
            return True
            
        except ImportError:
            logger.warning("⚠️ rank_bm25 or jieba not installed, BM25 disabled")
            return False
        except Exception as e:
            logger.error(f"❌ BM25 index build failed: {e}")
            return False
    
    def bm25_search(self, query: str, top_k: int = 20) -> List[SearchResult]:
        """
        BM25 稀疏检索
        
        Args:
            query: 用户查询
            top_k: 返回数量
            
        Returns:
            BM25 检索结果
        """
        if not self._indexed or not self.bm25_index:
            return []
        
        try:
            import jieba
            
            query_tokens = list(jieba.cut(query))
            scores = self.bm25_index.get_scores(query_tokens)
            
            # 获取 top-k
            indexed_scores = [(i, s) for i, s in enumerate(scores)]
            indexed_scores.sort(key=lambda x: x[1], reverse=True)
            top_results = indexed_scores[:top_k]
            
            results = []
            for idx, score in top_results:
                if score > 0:  # 过滤零分
                    film = self.film_data[idx]
                    results.append(SearchResult(
                        film_id=film.get("film_id", 0),
                        title=film.get("title", ""),
                        content=film.get("content", ""),
                        score=float(score),
                        source="bm25"
                    ))
            
            return results
            
        except Exception as e:
            logger.error(f"❌ BM25 search failed: {e}")
            return []
    
    @staticmethod
    def rrf_fusion(
        results_list: List[List[SearchResult]],
        k: int = 60,
        top_k: int = 10
    ) -> List[SearchResult]:
        """
        RRF (Reciprocal Rank Fusion) 融合
        
        公式: RRF(d) = Σ 1 / (k + rank(d))
        
        Args:
            results_list: 多个检索器的结果列表
            k: RRF 参数 (通常 60)
            top_k: 返回数量
            
        Returns:
            融合后的结果
        """
        # 按 film_id 聚合分数
        fusion_scores: Dict[int, Tuple[float, SearchResult]] = {}
        
        for results in results_list:
            for rank, result in enumerate(results):
                fid = result.film_id
                rrf_score = 1.0 / (k + rank + 1)
                
                if fid in fusion_scores:
                    old_score, old_result = fusion_scores[fid]
                    fusion_scores[fid] = (old_score + rrf_score, old_result)
                else:
                    fusion_scores[fid] = (rrf_score, result)
        
        # 排序
        sorted_items = sorted(
            fusion_scores.values(),
            key=lambda x: x[0],
            reverse=True
        )
        
        # 构建结果
        final_results = []
        for score, result in sorted_items[:top_k]:
            final_results.append(SearchResult(
                film_id=result.film_id,
                title=result.title,
                content=result.content,
                score=score,
                source="hybrid"
            ))
        
        return final_results


# 全局实例
hybrid_searcher = HybridSearcher()


def hybrid_search(
    query: str,
    vector_results: List[Dict],
    top_k: int = 5
) -> List[Dict]:
    """
    混合检索入口
    
    Args:
        query: 用户查询
        vector_results: 向量检索结果
        top_k: 返回数量
        
    Returns:
        融合后的结果列表
    """
    # 如果 BM25 未就绪，只用向量结果
    if not hybrid_searcher._indexed:
        return vector_results[:top_k]
    
    try:
        # 1. BM25 检索
        bm25_results = hybrid_searcher.bm25_search(query, top_k=20)
        
        # 2. 转换向量结果格式
        vec_results = [
            SearchResult(
                film_id=r.get("film_id", 0),
                title=r.get("title", ""),
                content=r.get("content", ""),
                score=r.get("score", 0),
                source="vector"
            )
            for r in vector_results
        ]
        
        # 3. RRF 融合
        if bm25_results:
            fused = HybridSearcher.rrf_fusion([vec_results, bm25_results], top_k=top_k)
            logger.info(f"🔀 Hybrid search: {len(vec_results)} vec + {len(bm25_results)} bm25 → {len(fused)} fused")
        else:
            fused = vec_results[:top_k]
        
        # 4. 转回 dict 格式
        return [
            {
                "film_id": r.film_id,
                "title": r.title,
                "content": r.content,
                "score": r.score,
                "source": r.source
            }
            for r in fused
        ]
        
    except Exception as e:
        logger.error(f"❌ Hybrid search failed: {e}")
        return vector_results[:top_k]
