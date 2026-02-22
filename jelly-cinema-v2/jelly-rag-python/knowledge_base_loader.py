"""
Knowledge base loader for commercial operations.
"""
from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any, Dict, List

logger = logging.getLogger(__name__)


def resolve_knowledge_dir(base_dir: str) -> Path:
    root = Path(base_dir)
    if root.is_absolute():
        return root
    return Path(__file__).resolve().parent / base_dir


def load_knowledge_documents(base_dir: str, max_docs: int = 2000) -> List[Dict[str, Any]]:
    """
    Load docs from `<base_dir>/*.jsonl`.

    Each line should be:
    {
      "title": "...",
      "content": "...",
      "tags": ["..."]
    }
    """
    root = resolve_knowledge_dir(base_dir)
    if not root.exists() or not root.is_dir():
        logger.warning("Knowledge base dir not found: %s", root)
        return []

    docs: List[Dict[str, Any]] = []
    for file in sorted(root.glob("*.jsonl")):
        kb_name = file.stem
        try:
            with file.open("r", encoding="utf-8") as f:
                for line_no, line in enumerate(f, start=1):
                    if len(docs) >= max_docs:
                        logger.warning("Knowledge docs reached max_docs=%s", max_docs)
                        return docs
                    line = line.strip()
                    if not line:
                        continue
                    item = json.loads(line)
                    title = str(item.get("title", "")).strip()
                    content = str(item.get("content", "")).strip()
                    tags = item.get("tags") or []
                    if not content:
                        logger.warning("Skip empty content in %s:%s", file.name, line_no)
                        continue
                    docs.append(
                        {
                            "knowledge_base": kb_name,
                            "title": title or f"{kb_name}#{line_no}",
                            "content": content,
                            "tags": tags if isinstance(tags, list) else [str(tags)],
                            "source_file": file.name,
                        }
                    )
        except Exception as exc:
            logger.error("Failed loading knowledge file %s: %s", file, exc)
    logger.info("Loaded %s knowledge docs from %s", len(docs), root)
    return docs


def build_knowledge_search_items(base_dir: str, max_docs: int = 2000) -> List[Dict[str, Any]]:
    """
    Convert knowledge docs to searchable items compatible with BM25/hybrid pipeline.
    """
    docs = load_knowledge_documents(base_dir=base_dir, max_docs=max_docs)
    items: List[Dict[str, Any]] = []
    for idx, doc in enumerate(docs):
        kb_name = doc["knowledge_base"]
        tags = " ".join(doc.get("tags", []))
        content = "\n".join(
            [
                f"知识库: {kb_name}",
                f"标题: {doc['title']}",
                f"标签: {tags}" if tags else "",
                doc["content"],
            ]
        ).strip()
        items.append(
            {
                # Keep schema compatible with existing retrieval client.
                # Negative ids represent non-film knowledge docs.
                "film_id": -1000000 - idx,
                "title": doc["title"],
                "content": content,
                "source": "kb",
                "source_type": "knowledge_base",
                "knowledge_base": kb_name,
            }
        )
    return items
