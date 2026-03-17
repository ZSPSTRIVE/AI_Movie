# Jelly RAG Python

极简版 Python RAG 服务，只保留三条内部能力：

- `POST /rag/ingest`
- `POST /rag/search`
- `GET /health`

兼容保留：

- `POST /rag/sync`：从 `knowledge_bases/` 目录重建索引
- `POST /rag/movie/sync`：从 `tvbox-proxy` 同步电影快照或搜索结果到本地知识库

## 当前实现刻意保持简单

为了方便快速学习、准备实习面试和解释架构，这个版本只保留 4 个核心知识点：

1. 文档/电影内容进入本地知识库
2. 优先向量检索，Milvus 不可用时退化到 PostgreSQL 关键词检索
3. 搜索时按需调用 `tvbox-proxy`，把电影结果增量写回本地 RAG
4. 首页推荐由后台刷新控制，不再依赖随机推荐

如果你准备实习面试，可以直接看 [INTERVIEW_GUIDE.md](INTERVIEW_GUIDE.md)。

## 运行前置

推荐直接使用 `E:\infra` 里的本机底座：

- PostgreSQL: `127.0.0.1:5432`
- Milvus Lite: `127.0.0.1:19530`
- 默认库: `rag_meta`

## 安装

```bash
pip install -r requirements.txt
```

## 启动

```bash
uvicorn main:app --host 0.0.0.0 --port 8500
```

## 初始化 PostgreSQL

```bash
E:\infra\scripts\psql-postgres.bat --db rag_meta -f scripts\init_pg.sql
```

## 接口示例

### 1. 写入文档

```json
POST /rag/ingest
{
  "title": "会员规则说明",
  "content": "积分存在有效期限制，具体规则如下……",
  "biz_type": "policy",
  "source_type": "manual"
}
```

### 2. 检索问答

```json
POST /rag/search
{
  "query": "会员积分会过期吗？",
  "biz_type": "policy",
  "top_k": 5
}
```

当查询像电影标题、导演、演员、影片相关问题时，服务会先尝试从 `tvbox-proxy` 拉取最新影片信息，写入 `knowledge_bases/movie_dynamic_catalog.jsonl`，再进入 RAG 检索链路。

### 3. 手动同步电影知识库

```json
POST /rag/movie/sync
{
  "query": "流浪地球",
  "limit": 12
}
```

如果不传 `query`，会同步 TVBox 推荐池快照：

```json
POST /rag/movie/sync
{
  "limit": 24
}
```
