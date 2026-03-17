from __future__ import annotations

import json
import logging
from typing import Any

from pymilvus import Collection, CollectionSchema, DataType, FieldSchema, connections, utility

from app.config import Settings

logger = logging.getLogger(__name__)


class MilvusStore:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._connected = False

    def health(self) -> dict:
        try:
            self._connect()
            collections = utility.list_collections()
            return {"status": "connected", "detail": ",".join(collections) or "no collections"}
        except Exception as exc:
            return {"status": "unavailable", "detail": str(exc)}

    def upsert_chunks(self, rows: list[dict[str, Any]], embeddings: list[list[float]]) -> int:
        if not rows:
            return 0
        collection = self._ensure_collection(len(embeddings[0]))
        entities = [
            [int(row["chunk_id"]) for row in rows],
            [int(row["document_id"]) for row in rows],
            [int(row["chunk_id"]) for row in rows],
            [str(row["biz_type"])[:64] for row in rows],
            [str(row["chunk_text"])[:65535] for row in rows],
            embeddings,
        ]
        collection.insert(entities)
        collection.flush()
        return len(rows)

    def search(self, query_embedding: list[float], top_k: int, biz_type: str | None = None) -> list[dict[str, Any]]:
        try:
            if not utility.has_collection(self.settings.milvus_collection_name):
                return []

            collection = self._get_collection()
            collection.load()
            expr = None
            if biz_type:
                expr = f"biz_type == {json.dumps(biz_type, ensure_ascii=False)}"

            search_params = {
                "metric_type": self.settings.milvus_metric_type,
                "params": {"nprobe": 16},
            }
            results = collection.search(
                data=[query_embedding],
                anns_field="embedding",
                param=search_params,
                limit=top_k,
                expr=expr,
                output_fields=["document_id", "chunk_id", "biz_type"],
            )

            hits: list[dict[str, Any]] = []
            for batch in results:
                for hit in batch:
                    hits.append(
                        {
                            "id": int(hit.id),
                            "document_id": int(hit.entity.get("document_id")),
                            "chunk_id": int(hit.entity.get("chunk_id")),
                            "biz_type": hit.entity.get("biz_type"),
                            "score": float(hit.distance),
                        }
                    )
            return hits
        except Exception as exc:
            logger.warning("Milvus search unavailable: %s", exc)
            return []

    def delete_by_chunk_ids(self, chunk_ids: list[int]) -> None:
        try:
            if not chunk_ids or not utility.has_collection(self.settings.milvus_collection_name):
                return
            collection = self._get_collection()
            chunk_list = ",".join(str(int(chunk_id)) for chunk_id in chunk_ids)
            collection.delete(expr=f"id in [{chunk_list}]")
            collection.flush()
        except Exception as exc:
            logger.warning("Milvus delete skipped: %s", exc)

    def _connect(self) -> None:
        if self._connected:
            return
        connections.connect(
            alias="default",
            host=self.settings.milvus_host,
            port=str(self.settings.milvus_port),
            timeout=3.0,
        )
        self._connected = True

    def _get_collection(self) -> Collection:
        self._connect()
        return Collection(self.settings.milvus_collection_name)

    def _ensure_collection(self, dimension: int) -> Collection:
        self._connect()
        name = self.settings.milvus_collection_name
        if utility.has_collection(name):
            collection = Collection(name)
            current_dim = next(field.params["dim"] for field in collection.schema.fields if field.name == "embedding")
            if current_dim != dimension:
                raise ValueError(
                    f"Milvus collection dim mismatch: current={current_dim}, incoming={dimension}. "
                    "Drop the collection before switching embedding models."
                )
            return collection

        schema = CollectionSchema(
            fields=[
                FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=False),
                FieldSchema(name="document_id", dtype=DataType.INT64),
                FieldSchema(name="chunk_id", dtype=DataType.INT64),
                FieldSchema(name="biz_type", dtype=DataType.VARCHAR, max_length=64),
                FieldSchema(name="chunk_text", dtype=DataType.VARCHAR, max_length=65535),
                FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=dimension),
            ],
            description="Minimal RAG chunk store",
        )
        collection = Collection(name=name, schema=schema)
        collection.create_index(
            field_name="embedding",
            index_params={
                "metric_type": self.settings.milvus_metric_type,
                "index_type": "IVF_FLAT",
                "params": {"nlist": self.settings.milvus_index_nlist},
            },
        )
        logger.info("Created Milvus collection %s", name)
        return collection
