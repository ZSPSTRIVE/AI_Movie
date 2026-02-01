"""
Milvus 向量数据库连接与操作
"""
from pymilvus import (
    connections,
    Collection,
    FieldSchema,
    CollectionSchema,
    DataType,
    utility
)
from config import get_settings
from typing import List, Dict, Any
import logging

logger = logging.getLogger(__name__)
settings = get_settings()


def connect_milvus():
    """建立 Milvus 连接"""
    try:
        connections.connect(
            alias="default",
            host=settings.milvus_host,
            port=settings.milvus_port
        )
        logger.info(f"✅ Connected to Milvus at {settings.milvus_host}:{settings.milvus_port}")
    except Exception as e:
        logger.error(f"❌ Failed to connect to Milvus: {e}")
        raise


def get_or_create_collection() -> Collection:
    """
    获取或创建电影向量集合
    
    Schema:
    - film_id: 电影ID (主键)
    - title: 电影标题
    - content: 拼接的文本内容 (标题 + 描述 + 演员等)
    - embedding: 向量
    """
    collection_name = settings.milvus_collection_name
    
    if utility.has_collection(collection_name):
        logger.info(f"📦 Collection '{collection_name}' already exists")
        return Collection(collection_name)
    
    # 定义 Schema
    fields = [
        FieldSchema(name="film_id", dtype=DataType.INT64, is_primary=True, auto_id=False),
        FieldSchema(name="title", dtype=DataType.VARCHAR, max_length=500),
        FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=5000),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=settings.embedding_dim)
    ]
    
    schema = CollectionSchema(
        fields=fields,
        description="Jelly Cinema Film Vector Collection"
    )
    
    collection = Collection(
        name=collection_name,
        schema=schema
    )
    
    # 创建索引
    index_params = {
        "metric_type": "COSINE",
        "index_type": "IVF_FLAT",
        "params": {"nlist": 128}
    }
    collection.create_index(field_name="embedding", index_params=index_params)
    
    logger.info(f"✅ Created collection '{collection_name}' with index")
    return collection


def insert_films(films: List[Dict[str, Any]], embeddings: List[List[float]]) -> int:
    """
    批量插入电影向量
    
    Args:
        films: 电影数据列表，每项包含 film_id, title, content
        embeddings: 对应的向量列表
        
    Returns:
        插入成功的数量
    """
    collection = get_or_create_collection()
    
    # 准备数据
    film_ids = [f["film_id"] for f in films]
    titles = [f["title"][:500] for f in films]  # 截断防止超长
    contents = [f["content"][:5000] for f in films]
    
    # 插入
    entities = [film_ids, titles, contents, embeddings]
    
    try:
        result = collection.insert(entities)
        collection.flush()
        logger.info(f"✅ Inserted {len(film_ids)} films into Milvus")
        return len(result.primary_keys)
    except Exception as e:
        logger.error(f"❌ Failed to insert films: {e}")
        raise


def search_similar(query_embedding: List[float], top_k: int = 5) -> List[Dict[str, Any]]:
    """
    向量相似度搜索
    
    Args:
        query_embedding: 查询向量
        top_k: 返回结果数量
        
    Returns:
        匹配的电影列表，包含 film_id, title, content, score
    """
    collection = get_or_create_collection()
    collection.load()
    
    search_params = {
        "metric_type": "COSINE",
        "params": {"nprobe": 16}
    }
    
    results = collection.search(
        data=[query_embedding],
        anns_field="embedding",
        param=search_params,
        limit=top_k,
        output_fields=["film_id", "title", "content"]
    )
    
    matches = []
    for hits in results:
        for hit in hits:
            matches.append({
                "film_id": hit.entity.get("film_id"),
                "title": hit.entity.get("title"),
                "content": hit.entity.get("content"),
                "score": hit.distance
            })
    
    return matches


def delete_collection():
    """删除集合（慎用）"""
    collection_name = settings.milvus_collection_name
    if utility.has_collection(collection_name):
        utility.drop_collection(collection_name)
        logger.info(f"🗑️ Dropped collection '{collection_name}'")
