"""
Cross-Encoder 重排序服务
提升检索精度的二阶段排序

Author: Jelly Cinema Team
Version: 2.0.0
"""
import logging
from typing import List, Dict, Tuple, Optional
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


class Reranker:
    """
    Cross-Encoder 重排序器
    
    使用 Cross-Encoder 模型对粗排结果进行精排，
    显著提升 Top-K 结果的相关性。
    
    工作流程:
    粗排 Top-50 → Cross-Encoder 打分 → 精排 Top-5
    """
    
    # 支持的模型
    MODELS = {
        "bge-reranker-base": "BAAI/bge-reranker-base",
        "bge-reranker-large": "BAAI/bge-reranker-large",
        "ms-marco": "cross-encoder/ms-marco-MiniLM-L-6-v2",
    }
    
    def __init__(self, model_name: str = "bge-reranker-base"):
        self.model = None
        self.tokenizer = None
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.model_name = model_name
        self._loaded = False
        
    def load_model(self) -> bool:
        """加载模型"""
        try:
            model_path = self.MODELS.get(self.model_name, self.model_name)
            logger.info(f"📦 Loading reranker model: {model_path}")
            
            self.tokenizer = AutoTokenizer.from_pretrained(model_path)
            self.model = AutoModelForSequenceClassification.from_pretrained(model_path)
            self.model.to(self.device)
            self.model.eval()
            
            self._loaded = True
            logger.info(f"✅ Reranker loaded on {self.device}")
            return True
            
        except Exception as e:
            logger.error(f"❌ Failed to load reranker: {e}")
            self._loaded = False
            return False
    
    @property
    def is_ready(self) -> bool:
        return self._loaded and self.model is not None
    
    def compute_score(self, query: str, document: str) -> float:
        """
        计算 query-document 相关性分数
        
        Args:
            query: 用户查询
            document: 文档内容
            
        Returns:
            相关性分数 (0-1)
        """
        if not self.is_ready:
            return 0.5
        
        try:
            inputs = self.tokenizer(
                [[query, document]],
                padding=True,
                truncation=True,
                max_length=512,
                return_tensors="pt"
            ).to(self.device)
            
            with torch.no_grad():
                scores = self.model(**inputs).logits.squeeze()
                # Sigmoid 归一化到 0-1
                if len(scores.shape) == 0:
                    score = torch.sigmoid(scores).item()
                else:
                    score = torch.sigmoid(scores[0]).item()
                return score
                
        except Exception as e:
            logger.warning(f"Rerank score error: {e}")
            return 0.5
    
    def rerank(
        self, 
        query: str, 
        documents: List[Dict], 
        top_k: int = 5,
        content_key: str = "content"
    ) -> List[Dict]:
        """
        对文档列表进行重排序
        
        Args:
            query: 用户查询
            documents: 文档列表，每个文档是 dict
            top_k: 返回 top-k 结果
            content_key: 文档内容的 key
            
        Returns:
            重排序后的文档列表
        """
        if not self.is_ready or not documents:
            return documents[:top_k]
        
        try:
            # 批量计算分数
            scored_docs = []
            for doc in documents:
                content = doc.get(content_key, "")
                score = self.compute_score(query, content)
                scored_docs.append({
                    **doc,
                    "rerank_score": score
                })
            
            # 按 rerank_score 降序排序
            scored_docs.sort(key=lambda x: x.get("rerank_score", 0), reverse=True)
            
            logger.info(f"🔄 Reranked {len(documents)} docs, returning top-{top_k}")
            return scored_docs[:top_k]
            
        except Exception as e:
            logger.error(f"❌ Rerank failed: {e}")
            return documents[:top_k]
    
    def rerank_batch(
        self,
        query: str,
        documents: List[Dict],
        top_k: int = 5,
        content_key: str = "content",
        batch_size: int = 16
    ) -> List[Dict]:
        """
        批量重排序 (更高效)
        
        Args:
            query: 用户查询
            documents: 文档列表
            top_k: 返回 top-k
            content_key: 内容字段名
            batch_size: 批处理大小
            
        Returns:
            重排序后的文档列表
        """
        if not self.is_ready or not documents:
            return documents[:top_k]
        
        try:
            all_scores = []
            
            # 分批处理
            for i in range(0, len(documents), batch_size):
                batch = documents[i:i + batch_size]
                pairs = [[query, doc.get(content_key, "")] for doc in batch]
                
                inputs = self.tokenizer(
                    pairs,
                    padding=True,
                    truncation=True,
                    max_length=512,
                    return_tensors="pt"
                ).to(self.device)
                
                with torch.no_grad():
                    scores = self.model(**inputs).logits.squeeze(-1)
                    scores = torch.sigmoid(scores).cpu().tolist()
                    
                    # 处理单元素情况
                    if isinstance(scores, float):
                        scores = [scores]
                    
                    all_scores.extend(scores)
            
            # 组合结果
            scored_docs = [
                {**doc, "rerank_score": score}
                for doc, score in zip(documents, all_scores)
            ]
            
            # 排序
            scored_docs.sort(key=lambda x: x.get("rerank_score", 0), reverse=True)
            
            logger.info(f"🔄 Batch reranked {len(documents)} docs")
            return scored_docs[:top_k]
            
        except Exception as e:
            logger.error(f"❌ Batch rerank failed: {e}")
            return documents[:top_k]


# 全局单例
reranker = Reranker(model_name=settings.reranker_model)


def init_reranker() -> bool:
    """初始化重排序器"""
    if settings.enable_reranker:
        return reranker.load_model()
    logger.info("⏭️ Reranker disabled by config")
    return False
