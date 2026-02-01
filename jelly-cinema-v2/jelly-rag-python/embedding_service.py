"""
Embedding 向量生成服务
使用 sentence-transformers 本地模型
"""
from sentence_transformers import SentenceTransformer
from config import get_settings
from typing import List
import logging

logger = logging.getLogger(__name__)
settings = get_settings()

# 全局模型实例（懒加载）
_model: SentenceTransformer = None


def get_embedding_model() -> SentenceTransformer:
    """获取或初始化 Embedding 模型"""
    global _model
    if _model is None:
        logger.info(f"🔄 Loading embedding model: {settings.embedding_model}")
        _model = SentenceTransformer(settings.embedding_model)
        logger.info(f"✅ Embedding model loaded, dim={_model.get_sentence_embedding_dimension()}")
    return _model


def embed_text(text: str) -> List[float]:
    """
    将单个文本转换为向量
    
    Args:
        text: 输入文本
        
    Returns:
        向量列表
    """
    model = get_embedding_model()
    embedding = model.encode(text, convert_to_numpy=True)
    return embedding.tolist()


def embed_texts(texts: List[str]) -> List[List[float]]:
    """
    批量将文本转换为向量
    
    Args:
        texts: 输入文本列表
        
    Returns:
        向量列表的列表
    """
    model = get_embedding_model()
    embeddings = model.encode(texts, convert_to_numpy=True, show_progress_bar=True)
    return embeddings.tolist()


def build_film_content(film: dict) -> str:
    """
    构建电影的可检索文本内容
    
    将电影的各个字段拼接成一段完整的描述文本，
    用于向量化和检索。
    
    Args:
        film: 电影数据字典
        
    Returns:
        拼接后的文本
    """
    parts = []
    
    # 标题
    if film.get("title"):
        parts.append(f"电影名称：{film['title']}")
    
    # 分类
    if film.get("category_name"):
        parts.append(f"类型：{film['category_name']}")
    
    # 年份
    if film.get("year"):
        parts.append(f"年份：{film['year']}")
    
    # 地区
    if film.get("region"):
        parts.append(f"地区：{film['region']}")
    
    # 导演
    if film.get("director"):
        parts.append(f"导演：{film['director']}")
    
    # 演员
    if film.get("actors"):
        parts.append(f"主演：{film['actors']}")
    
    # 评分
    if film.get("rating"):
        parts.append(f"评分：{film['rating']}")
    
    # 简介
    if film.get("description"):
        parts.append(f"简介：{film['description']}")
    
    return "\n".join(parts)
