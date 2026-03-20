from __future__ import annotations

SEPARATORS = ["\n\n", "\n", "。", "！", "？", ".", "!", "?", "；", ";", "，", ",", " "]


def split_text(text: str, *, chunk_size: int, chunk_overlap: int) -> list[str]:
    cleaned = text.strip()
    if not cleaned:
        return []
    if chunk_size <= 0:
        raise ValueError("chunk_size must be positive")
    if chunk_overlap < 0:
        raise ValueError("chunk_overlap cannot be negative")

    units = _split_recursively(cleaned, SEPARATORS, chunk_size)
    return _merge_units(units, chunk_size=chunk_size, chunk_overlap=min(chunk_overlap, chunk_size - 1))


def _split_recursively(text: str, separators: list[str], chunk_size: int) -> list[str]:
    stripped = text.strip()
    if not stripped:
        return []
    if len(stripped) <= chunk_size:
        return [stripped]

    for index, separator in enumerate(separators):
        if separator not in stripped:
            continue

        parts = _split_keep_separator(stripped, separator)
        if len(parts) <= 1:
            continue

        chunks: list[str] = []
        next_separators = separators[index + 1 :]
        for part in parts:
            candidate = part.strip()
            if not candidate:
                continue
            if len(candidate) <= chunk_size:
                chunks.append(candidate)
                continue
            chunks.extend(_split_recursively(candidate, next_separators, chunk_size))

        if chunks:
            return chunks

    return _split_by_length(stripped, chunk_size)


def _split_keep_separator(text: str, separator: str) -> list[str]:
    parts: list[str] = []
    start = 0
    while True:
        index = text.find(separator, start)
        if index < 0:
            break
        piece = text[start : index + len(separator)]
        if piece.strip():
            parts.append(piece)
        start = index + len(separator)
    if start < len(text):
        tail = text[start:]
        if tail.strip():
            parts.append(tail)
    return parts


def _split_by_length(text: str, chunk_size: int) -> list[str]:
    return [text[index : index + chunk_size].strip() for index in range(0, len(text), chunk_size) if text[index : index + chunk_size].strip()]


def _merge_units(units: list[str], *, chunk_size: int, chunk_overlap: int) -> list[str]:
    chunks: list[str] = []
    start = 0

    while start < len(units):
        current: list[str] = []
        current_length = 0
        end = start

        while end < len(units):
            unit = units[end]
            if current and current_length + len(unit) > chunk_size:
                break
            current.append(unit)
            current_length += len(unit)
            end += 1

        chunk = "".join(current).strip()
        if chunk:
            chunks.append(chunk)

        if end >= len(units):
            break
        if chunk_overlap <= 0:
            start = end
            continue

        # Reuse a suffix worth roughly chunk_overlap characters without looping forever.
        next_start = end
        overlap_length = 0
        probe = end - 1
        while probe > start:
            overlap_length += len(units[probe])
            if overlap_length > chunk_overlap:
                break
            next_start = probe
            probe -= 1
        start = next_start

    return chunks
