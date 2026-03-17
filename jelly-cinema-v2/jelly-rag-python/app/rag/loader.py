from __future__ import annotations

import json
from pathlib import Path

from docx import Document as DocxDocument
from PyPDF2 import PdfReader


def load_text(*, content: str | None, file_path: str | None) -> tuple[str, str | None]:
    if content and content.strip():
        return clean_text(content), None

    if not file_path:
        raise ValueError("Either content or file_path is required")

    path = Path(file_path)
    if not path.exists():
        raise FileNotFoundError(f"File not found: {path}")

    suffix = path.suffix.lower()
    if suffix == ".pdf":
        text = _load_pdf(path)
    elif suffix == ".docx":
        text = _load_docx(path)
    elif suffix == ".jsonl":
        text = _load_jsonl(path)
    else:
        text = _load_plain_text(path)
    return clean_text(text), path.name


def list_supported_files(base_dir: Path) -> list[Path]:
    if not base_dir.exists():
        raise FileNotFoundError(f"Knowledge base directory not found: {base_dir}")
    supported = {".txt", ".md", ".markdown", ".pdf", ".docx", ".jsonl"}
    return sorted(path for path in base_dir.iterdir() if path.is_file() and path.suffix.lower() in supported)


def clean_text(text: str) -> str:
    lines = [line.strip() for line in text.replace("\u0000", " ").splitlines()]
    return "\n".join(line for line in lines if line)


def _load_pdf(path: Path) -> str:
    reader = PdfReader(str(path))
    return "\n".join(page.extract_text() or "" for page in reader.pages)


def _load_docx(path: Path) -> str:
    document = DocxDocument(str(path))
    return "\n".join(paragraph.text for paragraph in document.paragraphs if paragraph.text.strip())


def _load_jsonl(path: Path) -> str:
    parts: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        raw = line.strip()
        if not raw:
            continue
        item = json.loads(raw)
        title = item.get("title") or ""
        content = item.get("content") or ""
        tags = ", ".join(item.get("tags") or [])
        merged = "\n".join(part for part in [title, content, tags] if part)
        if merged:
            parts.append(merged)
    return "\n\n".join(parts)


def _load_plain_text(path: Path) -> str:
    for encoding in ("utf-8", "utf-8-sig", "gbk"):
        try:
            return path.read_text(encoding=encoding)
        except UnicodeDecodeError:
            continue
    return path.read_text(encoding="utf-8", errors="ignore")
