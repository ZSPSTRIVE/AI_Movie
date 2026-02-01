# Jelly RAG Python Service

基于 FastAPI + Milvus + LangChain 的 RAG 检索服务。

## 依赖

```bash
pip install -r requirements.txt
```

## 启动

```bash
uvicorn main:app --host 0.0.0.0 --port 8500 --reload
```

## API

- `POST /rag/search` - 向量检索
- `POST /rag/upload` - 文档上传
- `POST /sync/films` - 同步电影数据到 Milvus
