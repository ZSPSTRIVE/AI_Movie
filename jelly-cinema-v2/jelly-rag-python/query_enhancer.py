"""
Query enhancement service (clean + expand + optional HyDE).
"""
import logging
import re
from pathlib import Path
from typing import Dict, List, Optional

from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


class QueryEnhancer:
    STOP_WORDS = {
        "的",
        "了",
        "是",
        "在",
        "和",
        "我",
        "你",
        "他",
        "她",
        "想",
        "要",
        "看",
        "找",
        "推荐",
        "请",
        "一个",
        "一部",
        "吗",
        "呢",
        "啊",
        "可以",
        "什么",
    }

    GENRE_SYNONYMS = {
        "科幻": ["科幻片", "sci-fi", "太空", "未来"],
        "动作": ["动作片", "打斗", "格斗", "功夫"],
        "喜剧": ["喜剧片", "搞笑", "幽默"],
        "爱情": ["爱情片", "浪漫", "恋爱"],
        "恐怖": ["恐怖片", "惊悚", "鬼片"],
        "悬疑": ["悬疑片", "推理", "烧脑"],
        "动画": ["动画片", "动漫"],
        "战争": ["战争片", "军事"],
        "犯罪": ["犯罪片", "警匪"],
    }

    QUALITY_KEYWORDS = {
        "好看": "高分",
        "经典": "经典",
        "热门": "热门",
        "新片": "最新",
        "豆瓣": "高分",
    }

    def __init__(self):
        self.llm_model = None
        self.llm_tokenizer = None
        self.llm_device = None
        self._llm_loaded = False
        self._torch = None

    def _resolve_model_path(self, model_path: str) -> str:
        path = Path(model_path)
        if not path.exists() or not path.is_dir():
            return model_path
        snapshots_dir = path / "snapshots"
        if snapshots_dir.is_dir():
            snapshot_dirs = [p for p in snapshots_dir.iterdir() if p.is_dir()]
            if snapshot_dirs:
                latest_snapshot = max(snapshot_dirs, key=lambda p: p.stat().st_mtime)
                return str(latest_snapshot)
        return model_path

    def _load_local_llm(self) -> bool:
        if self._llm_loaded:
            return True
        if not settings.llm_model_path:
            logger.warning("HyDE enabled but llm_model_path is empty")
            return False

        try:
            import torch
            from transformers import AutoModelForCausalLM, AutoTokenizer

            self._torch = torch
            model_path = self._resolve_model_path(settings.llm_model_path)
            device = settings.llm_device or ("cuda" if torch.cuda.is_available() else "cpu")

            self.llm_tokenizer = AutoTokenizer.from_pretrained(
                model_path,
                local_files_only=settings.llm_local_only,
            )
            if self.llm_tokenizer.pad_token is None and self.llm_tokenizer.eos_token is not None:
                self.llm_tokenizer.pad_token = self.llm_tokenizer.eos_token

            self.llm_model = AutoModelForCausalLM.from_pretrained(
                model_path,
                local_files_only=settings.llm_local_only,
            )
            self.llm_model.to(device)
            self.llm_model.eval()

            self.llm_device = device
            self._llm_loaded = True
            logger.info("HyDE local LLM loaded on %s", device)
            return True
        except Exception as exc:
            logger.error("Failed to load local LLM: %s", exc)
            self._llm_loaded = False
            return False

    def clean_query(self, query: str) -> str:
        cleaned = re.sub(r"[^\w\s\u4e00-\u9fff]", " ", query)
        words = cleaned.split()
        filtered = [w for w in words if w not in self.STOP_WORDS and w]
        return (" ".join(filtered) if filtered else query).strip()

    def expand_query(self, query: str) -> str:
        expanded_terms = [query]
        for genre, synonyms in self.GENRE_SYNONYMS.items():
            if genre in query:
                expanded_terms.append(synonyms[0])
                break
        for keyword, expansion in self.QUALITY_KEYWORDS.items():
            if keyword in query:
                expanded_terms.append(expansion)
        dedup = list(dict.fromkeys(expanded_terms))
        return " ".join(dedup)

    def _sanitize_for_prompt(self, text: str) -> str:
        """Remove control characters and limit length to prevent prompt injection."""
        sanitized = re.sub(r"[\x00-\x1f\x7f]", "", text)
        sanitized = re.sub(r"(指令|忽略|system|ignore|prompt|instruction)", "", sanitized, flags=re.IGNORECASE)
        return sanitized[:200]

    def generate_hyde_query(self, query: str) -> Optional[str]:
        if not settings.enable_hyde:
            return None
        if not self._load_local_llm():
            return None

        safe_query = self._sanitize_for_prompt(query)
        prompt = (
            "请为以下用户问题生成一个假设的电影推荐回答，"
            "只输出电影相关内容：\n\n"
            f"用户问题: {safe_query}\n\n"
            "假设回答:"
        )

        try:
            inputs = self.llm_tokenizer(prompt, return_tensors="pt").to(self.llm_device)
            with self._torch.no_grad():
                outputs = self.llm_model.generate(
                    **inputs,
                    max_new_tokens=settings.llm_max_new_tokens,
                    do_sample=True,
                    temperature=settings.llm_temperature,
                    top_p=settings.llm_top_p,
                    pad_token_id=self.llm_tokenizer.eos_token_id,
                )
            generated = self.llm_tokenizer.decode(outputs[0], skip_special_tokens=True)
            if prompt in generated:
                generated = generated.split(prompt, 1)[1]
            return generated.strip() or None
        except Exception as exc:
            logger.warning("HyDE generation failed: %s", exc)
            return None

    def extract_entities(self, query: str) -> Dict[str, List[str]]:
        entities = {"actors": [], "directors": [], "genres": [], "years": []}
        years = re.findall(r"(?:19|20)\d{2}", query)
        entities["years"] = [f"{year}年" for year in years]
        for genre in self.GENRE_SYNONYMS:
            if genre in query:
                entities["genres"].append(genre)
        return entities

    def enhance(self, query: str) -> str:
        cleaned = self.clean_query(query)
        expanded = self.expand_query(cleaned)
        hyde = self.generate_hyde_query(query)
        if hyde:
            expanded = f"{expanded} {hyde}"
        return expanded

    def get_search_queries(self, query: str) -> List[str]:
        queries = [query]
        cleaned = self.clean_query(query)
        if cleaned and cleaned != query:
            queries.append(cleaned)
        expanded = self.expand_query(query)
        if expanded != query:
            queries.append(expanded)
        return list(dict.fromkeys(queries))[:3]


query_enhancer = QueryEnhancer()


def enhance_query(query: str) -> str:
    return query_enhancer.enhance(query)


def get_multi_queries(query: str) -> List[str]:
    return query_enhancer.get_search_queries(query)
