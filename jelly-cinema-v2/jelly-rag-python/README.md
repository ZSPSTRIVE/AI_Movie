# Jelly RAG Python Service

FastAPI-based RAG service for Jelly Cinema, supporting No-Milvus mode.

## Install

```bash
pip install -r requirements.txt
```

## Run

```bash
uvicorn main:app --host 0.0.0.0 --port 8500 --reload
```

## API

- `POST /rag/search`: Search with query enhancement + BM25/Hybrid recall + optional rerank.
- `POST /rag/sync`: Sync films from MySQL and rebuild BM25 index.
- `POST /rag/kb/reload`: Reload local knowledge bases and rebuild BM25 index.
- `GET /films/{film_id}`: Get film details.
- `GET /health`: Service/component health status.
- `GET /metrics`: Prometheus metrics.

## Knowledge Bases

Local business knowledge base files are stored in `knowledge_bases/*.jsonl`.

- `membership_revenue.jsonl`
- `content_compliance.jsonl`
- `growth_operations.jsonl`
- `customer_support_risk.jsonl`

Each line is one JSON document:

```json
{"title":"...", "content":"...", "tags":["..."]}
```
