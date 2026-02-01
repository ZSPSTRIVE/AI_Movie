"""
查询增强服务
HyDE + 查询改写 + 查询扩展

Author: Jelly Cinema Team
Version: 2.0.0
"""
import logging
from typing import List, Optional, Dict
import re
from pathlib import Path

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer

from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


class QueryEnhancer:
    """
    查询增强器
    
    提供多种查询优化策略:
    1. HyDE (Hypothetical Document Embeddings) - 用 LLM 生成假设答案
    2. 查询扩展 - 添加同义词/相关词
    3. 查询清洗 - 去除噪声词
    """
    
    # 电影领域停用词
    STOP_WORDS = {
        "的", "了", "是", "有", "在", "和", "我", "你", "他",
        "想", "要", "看", "找", "帮", "推荐", "给", "一下", "一部",
        "吗", "呢", "啊", "吧", "请", "能", "可以", "什么",
    }
    
    # 电影类型同义词
    GENRE_SYNONYMS = {
        "科幻": ["科幻片", "科幻电影", "sci-fi", "太空", "未来"],
        "动作": ["动作片", "打斗", "格斗", "武打", "功夫"],
        "喜剧": ["喜剧片", "搞笑", "幽默", "轻松"],
        "爱情": ["爱情片", "浪漫", "恋爱", "情感"],
        "恐怖": ["恐怖片", "惊悚", "吓人", "鬼片"],
        "悬疑": ["悬疑片", "推理", "烧脑", "解密"],
        "动画": ["动画片", "动漫", "卡通"],
        "战争": ["战争片", "军事", "二战", "抗战"],
        "犯罪": ["犯罪片", "黑帮", "警匪"],
    }
    
    # 质量/评分相关词
    QUALITY_KEYWORDS = {
        "好看": "高分",
        "经典": "经典",
        "热门": "热门",
        "新片": "最新",
        "高分": "高分",
        "豆瓣": "高分",
    }
    
    def __init__(self):
        self.llm_client = None  # 可选的 LLM 客户端
        self.llm_model = None
        self.llm_tokenizer = None
        self.llm_device = None
        self._llm_loaded = False

    def _load_local_llm(self) -> bool:
        if self._llm_loaded:
            return True
        if not settings.llm_model_path:
            logger.warning("HyDE enabled but llm_model_path not set")
            return False

        try:
            device = settings.llm_device or ("cuda" if torch.cuda.is_available() else "cpu")
            model_path = self._resolve_model_path(settings.llm_model_path)

            logger.info(f"🧠 Loading local LLM for HyDE: {model_path}")
            self.llm_tokenizer = AutoTokenizer.from_pretrained(
                model_path,
                local_files_only=settings.llm_local_only
            )
            if self.llm_tokenizer.pad_token is None and self.llm_tokenizer.eos_token is not None:
                self.llm_tokenizer.pad_token = self.llm_tokenizer.eos_token

            self.llm_model = AutoModelForCausalLM.from_pretrained(
                model_path,
                local_files_only=settings.llm_local_only
            )
            self.llm_model.to(device)
            self.llm_model.eval()

            self.llm_device = device
            self._llm_loaded = True
            logger.info(f"✅ Local LLM ready on {device}")
            return True
        except Exception as e:
            logger.error(f"❌ Failed to load local LLM: {e}")
            self._llm_loaded = False
            return False

    def _resolve_model_path(self, model_path: str) -> str:
        path = Path(model_path)
        if not path.exists() or not path.is_dir():
            return model_path

        snapshots_dir = path / "snapshots"
        if snapshots_dir.is_dir():
            snapshot_dirs = [p for p in snapshots_dir.iterdir() if p.is_dir()]
            if snapshot_dirs:
                latest_snapshot = max(snapshot_dirs, key=lambda p: p.stat().st_mtime)
                logger.info(f"🔍 Using local snapshot: {latest_snapshot}")
                return str(latest_snapshot)

        return model_path
        
    def clean_query(self, query: str) -> str:
        """
        清洗查询
        
        移除停用词和特殊字符
        """
        # 去除特殊字符
        cleaned = re.sub(r'[^\w\s\u4e00-\u9fff]', ' ', query)
        
        # 分词并移除停用词
        words = cleaned.split()
        filtered = [w for w in words if w not in self.STOP_WORDS and len(w) > 0]
        
        result = ' '.join(filtered) if filtered else query
        return result.strip()
    
    def expand_query(self, query: str) -> str:
        """
        查询扩展
        
        添加同义词和相关词
        """
        expanded_terms = [query]
        
        # 类型扩展
        for genre, synonyms in self.GENRE_SYNONYMS.items():
            if genre in query:
                # 添加一个核心同义词
                expanded_terms.append(synonyms[0])
                break
        
        # 质量关键词
        for keyword, expansion in self.QUALITY_KEYWORDS.items():
            if keyword in query:
                expanded_terms.append(expansion)
        
        # 去重并合并
        unique_terms = []
        seen = set()
        for term in expanded_terms:
            if term not in seen:
                unique_terms.append(term)
                seen.add(term)
        
        result = ' '.join(unique_terms)
        if result != query:
            logger.debug(f"🔍 Query expanded: '{query}' → '{result}'")
        
        return result
    
    def generate_hyde_query(self, query: str) -> Optional[str]:
        """
        HyDE (Hypothetical Document Embeddings)
        
        生成假设的回答文档，用于提升检索效果。
        需要 LLM 支持。
        """
        if not settings.enable_hyde:
            return None
        if not self._load_local_llm() and not self.llm_client:
            return None
        
        # HyDE prompt
        prompt = f"""请为以下用户问题生成一个假设的电影推荐回答（只需要写电影相关内容，不要写推荐理由）：

用户问题: {query}

假设回答:"""
        
        try:
            if self.llm_client:
                # 预留第三方 LLM 客户端
                return None

            inputs = self.llm_tokenizer(
                prompt,
                return_tensors="pt"
            ).to(self.llm_device)

            with torch.no_grad():
                outputs = self.llm_model.generate(
                    **inputs,
                    max_new_tokens=settings.llm_max_new_tokens,
                    do_sample=True,
                    temperature=settings.llm_temperature,
                    top_p=settings.llm_top_p,
                    pad_token_id=self.llm_tokenizer.eos_token_id
                )

            generated = self.llm_tokenizer.decode(outputs[0], skip_special_tokens=True)
            if prompt in generated:
                generated = generated.split(prompt, 1)[1]
            return generated.strip() or None
        except Exception as e:
            logger.warning(f"HyDE generation failed: {e}")
            return None
    
    def extract_entities(self, query: str) -> Dict[str, List[str]]:
        """
        提取查询中的实体
        
        Returns:
            {
                "actors": ["刘德华", "周星驰"],
                "directors": ["王家卫"],
                "genres": ["动作"],
                "years": ["2024"],
            }
        """
        entities = {
            "actors": [],
            "directors": [],
            "genres": [],
            "years": [],
        }
        
        # 提取年份
        year_pattern = r'(19|20)\d{2}'
        years = re.findall(year_pattern, query)
        entities["years"] = [f"{y}年" for y in years]
        
        # 提取类型
        for genre in self.GENRE_SYNONYMS.keys():
            if genre in query:
                entities["genres"].append(genre)
        
        # 演员/导演需要更复杂的 NER，这里简化处理
        # 实际生产环境可接入 NER 服务
        
        return entities
    
    def enhance(self, query: str) -> str:
        """
        综合查询增强
        
        Args:
            query: 原始查询
            
        Returns:
            增强后的查询
        """
        # 1. 清洗
        cleaned = self.clean_query(query)
        
        # 2. 扩展
        expanded = self.expand_query(cleaned)
        
        # 3. HyDE (如果可用)
        hyde_query = self.generate_hyde_query(query)
        if hyde_query:
            expanded = f"{expanded} {hyde_query}"
        
        return expanded
    
    def get_search_queries(self, query: str) -> List[str]:
        """
        生成多个搜索查询变体
        
        用于多路召回
        """
        queries = [query]
        
        # 添加清洗后的版本
        cleaned = self.clean_query(query)
        if cleaned != query and cleaned:
            queries.append(cleaned)
        
        # 添加扩展版本
        expanded = self.expand_query(query)
        if expanded != query:
            queries.append(expanded)
        
        return list(set(queries))[:3]  # 最多 3 个


# 全局实例
query_enhancer = QueryEnhancer()


def enhance_query(query: str) -> str:
    """增强查询入口"""
    return query_enhancer.enhance(query)


def get_multi_queries(query: str) -> List[str]:
    """获取多查询变体"""
    return query_enhancer.get_search_queries(query)
