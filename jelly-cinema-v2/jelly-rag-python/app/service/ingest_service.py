from __future__ import annotations

import hashlib
import logging

from app.config import Settings
from app.infra.embedding_client import EmbeddingClient
from app.infra.milvus_store import MilvusStore
from app.infra.pg_client import PgClient
from app.infra.redis_client import RedisClient
from app.model.request import IngestRequest
from app.model.response import IngestResponse, SyncResponse
from app.rag.loader import list_supported_files, load_text
from app.rag.splitter import split_text

logger = logging.getLogger(__name__)


class IngestService:
    def __init__(
        self,
        *,
        settings: Settings,
        pg_client: PgClient,
        milvus_store: MilvusStore,
        embedding_client: EmbeddingClient,
        redis_client: RedisClient,
    ):
        self.settings = settings
        self.pg_client = pg_client
        self.milvus_store = milvus_store
        self.embedding_client = embedding_client
        self.redis_client = redis_client

    def ingest(self, request: IngestRequest) -> IngestResponse:
        text, detected_name = load_text(content=request.content, file_path=request.file_path)
        if not text:
            raise ValueError("Document content is empty after parsing")

        title = request.title.strip()
        source_path = request.source_path or request.file_path
        file_name = detected_name or (source_path.rsplit("/", 1)[-1] if source_path else None)
        content_hash = hashlib.sha256(text.encode("utf-8")).hexdigest()
        biz_type = request.biz_type or self.settings.default_biz_type
        old_chunk_ids: list[int] = []
        if request.replace_by_source and source_path:
            old_chunk_ids.extend(self.pg_client.delete_documents_by_source_path(source_path, biz_type))
        document_id = self.pg_client.upsert_document(
            title=title,
            biz_type=biz_type,
            source_type=request.source_type,
            source_path=source_path,
            file_name=file_name,
            content_hash=content_hash,
            created_by=request.created_by,
            status="INDEXING",
        )

        old_chunk_ids.extend(self.pg_client.list_chunk_ids_by_document(document_id))
        new_chunks = split_text(
            text,
            chunk_size=self.settings.chunk_size,
            chunk_overlap=self.settings.chunk_overlap,
        )
        if not new_chunks:
            raise ValueError("No chunks generated from document")

        self.pg_client.delete_chunks_by_document(document_id)
        chunk_rows = self.pg_client.insert_chunks(document_id, new_chunks)
        if old_chunk_ids:
            self.milvus_store.delete_by_chunk_ids(old_chunk_ids)

        # 向量化属于增强能力；即使失败，也保留 PG 元数据和 chunk，确保最小链路可用。
        vector_status = "vector_ready"
        try:
            embeddings = self.embedding_client.embed_texts([row["chunk_text"] for row in chunk_rows])
            self.milvus_store.upsert_chunks(
                [
                    {
                        "document_id": document_id,
                        "chunk_id": row["chunk_id"],
                        "biz_type": biz_type,
                        "chunk_text": row["chunk_text"],
                    }
                    for row in chunk_rows
                ],
                embeddings,
            )
            self.pg_client.update_chunk_milvus_ids([int(row["chunk_id"]) for row in chunk_rows])
        except Exception as exc:  # pragma: no cover
            vector_status = f"vector_skipped:{exc}"
            logger.warning("Vector ingest skipped for document_id=%s: %s", document_id, exc)
        self.pg_client.set_document_status(document_id, "READY")
        self.redis_client.clear_namespace("rag:search:")
        return IngestResponse(
            success=True,
            document_id=document_id,
            chunks=len(chunk_rows),
            message=f"Ingested {len(chunk_rows)} chunks ({vector_status})",
        )

    def rebuild_from_directory(self) -> SyncResponse:
        files = list_supported_files(self.settings.knowledge_base_path)
        count = 0
        for path in files:
            self.ingest(
                IngestRequest(
                    title=path.stem,
                    file_path=str(path),
                    biz_type=self.settings.default_biz_type,
                    source_type="directory_sync",
                    source_path=str(path),
                    replace_by_source=True,
                )
            )
            count += 1
        return SyncResponse(
            success=True,
            count=count,
            message=f"Rebuilt index from {count} files under {self.settings.knowledge_base_dir}",
        )
