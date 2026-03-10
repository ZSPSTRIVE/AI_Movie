"""
MySQL 数据同步模块
从 jelly_cinema 数据库同步电影数据到 Milvus
"""
from sqlalchemy import create_engine, text
from config import get_settings
from typing import List, Dict, Any
import logging

logger = logging.getLogger(__name__)
settings = get_settings()


def get_mysql_engine():
    """获取 MySQL 连接引擎"""
    url = f"mysql+pymysql://{settings.mysql_user}:{settings.mysql_password}@{settings.mysql_host}:{settings.mysql_port}/{settings.mysql_database}?charset=utf8mb4"
    return create_engine(url, pool_pre_ping=True)


def fetch_all_films() -> List[Dict[str, Any]]:
    """
    从 MySQL 获取所有电影数据
    
    Returns:
        电影数据列表
    """
    engine = get_mysql_engine()
    
    query = text("""
        SELECT 
            f.id as film_id,
            f.title,
            f.description,
            f.year,
            f.director,
            f.actors,
            f.region,
            f.rating,
            c.name as category_name
        FROM t_film f
        LEFT JOIN t_category c ON f.category_id = c.id
        WHERE f.deleted = 0 AND f.status = 0
        ORDER BY f.id
    """)
    
    with engine.connect() as conn:
        result = conn.execute(query)
        films = [dict(row._mapping) for row in result]
        logger.info(f"📊 Fetched {len(films)} films from MySQL")
        return films


def fetch_films_by_ids(film_ids: List[int]) -> List[Dict[str, Any]]:
    """
    根据 ID 列表获取电影数据
    
    Args:
        film_ids: 电影 ID 列表
        
    Returns:
        电影数据列表
    """
    if not film_ids:
        return []

    # Input validation: ensure IDs are integers and limit batch size
    MAX_BATCH_SIZE = 500
    validated_ids = []
    for fid in film_ids[:MAX_BATCH_SIZE]:
        if isinstance(fid, int) and fid > 0:
            validated_ids.append(fid)
    if not validated_ids:
        return []
    
    engine = get_mysql_engine()
    
    placeholders = ",".join([":id" + str(i) for i in range(len(validated_ids))])
    query = text(f"""
        SELECT 
            f.id as film_id,
            f.title,
            f.description,
            f.cover_url,
            f.video_url,
            f.year,
            f.director,
            f.actors,
            f.region,
            f.rating,
            f.play_count,
            c.name as category_name
        FROM t_film f
        LEFT JOIN t_category c ON f.category_id = c.id
        WHERE f.id IN ({placeholders})
          AND f.deleted = 0
    """)
    
    params = {f"id{i}": validated_ids[i] for i in range(len(validated_ids))}
    
    with engine.connect() as conn:
        result = conn.execute(query, params)
        return [dict(row._mapping) for row in result]
