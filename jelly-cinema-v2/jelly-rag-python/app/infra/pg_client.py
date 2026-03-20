from __future__ import annotations

import json
import logging
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Any

from app.config import Settings

logger = logging.getLogger(__name__)

try:  # pragma: no cover
    import psycopg
    from psycopg.rows import dict_row
except ImportError:  # pragma: no cover
    psycopg = None
    dict_row = None


def _pg_literal(value: str | None) -> str:
    if value is None:
        return "NULL"
    tag = "rag"
    while f"${tag}$" in value:
        tag += "_x"
    return f"${tag}${value}${tag}$"


class PgClient:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._schema_ready = False
        self._psql_path = self._detect_psql()

    def health(self) -> dict:
        try:
            row = self._fetch_rows("select current_database() as database, current_user as username")[0]
            backend = "psycopg" if psycopg is not None else "psql"
            return {"status": "connected", "detail": f"{backend}:{row['database']}:{row['username']}"}
        except Exception as exc:
            return {"status": "unavailable", "detail": str(exc)}

    def ensure_schema(self) -> None:
        if self._schema_ready:
            return
        script_path = Path(__file__).resolve().parents[2] / "scripts" / "init_pg.sql"
        self._execute_non_query(script_path.read_text(encoding="utf-8"))
        self._schema_ready = True

    def upsert_document(
        self,
        *,
        title: str,
        biz_type: str,
        source_type: str,
        source_path: str | None,
        file_name: str | None,
        content_hash: str,
        created_by: int | None,
        status: str,
    ) -> int:
        self.ensure_schema()
        sql = f"""
        insert into rag_document (
            biz_type, title, source_type, source_path, file_name, content_hash, status, created_by
        ) values (
            {_pg_literal(biz_type)},
            {_pg_literal(title)},
            {_pg_literal(source_type)},
            {_pg_literal(source_path)},
            {_pg_literal(file_name)},
            {_pg_literal(content_hash)},
            {_pg_literal(status)},
            {created_by if created_by is not None else 'NULL'}
        )
        on conflict (biz_type, content_hash) do update
        set title = excluded.title,
            source_type = excluded.source_type,
            source_path = excluded.source_path,
            file_name = excluded.file_name,
            status = excluded.status,
            updated_at = current_timestamp
        returning id
        """
        return int(self._fetch_scalar(sql))

    def set_document_status(self, document_id: int, status: str) -> None:
        self.ensure_schema()
        sql = f"""
        update rag_document
        set status = {_pg_literal(status)}, updated_at = current_timestamp
        where id = {int(document_id)}
        """
        self._execute_non_query(sql)

    def list_chunk_ids_by_document(self, document_id: int) -> list[int]:
        rows = self._fetch_rows(
            f"select id from rag_chunk where document_id = {int(document_id)} order by chunk_index"
        )
        return [int(row["id"]) for row in rows]

    def delete_chunks_by_document(self, document_id: int) -> None:
        self._execute_non_query(f"delete from rag_chunk where document_id = {int(document_id)}")

    def delete_documents_by_source_path(self, source_path: str, biz_type: str) -> list[int]:
        if not source_path:
            return []
        self.ensure_schema()
        rows = self._fetch_rows(
            f"""
            select c.id as chunk_id
            from rag_chunk c
            join rag_document d on d.id = c.document_id
            where d.source_path = {_pg_literal(source_path)}
              and d.biz_type = {_pg_literal(biz_type)}
            """
        )
        self._execute_non_query(
            f"""
            delete from rag_document
            where source_path = {_pg_literal(source_path)}
              and biz_type = {_pg_literal(biz_type)}
            """
        )
        return [int(row["chunk_id"]) for row in rows]

    def insert_chunks(self, document_id: int, chunks: list[str]) -> list[dict[str, Any]]:
        if not chunks:
            return []
        values = []
        for index, chunk in enumerate(chunks):
            values.append(f"({int(document_id)}, {index}, {_pg_literal(chunk)}, {_pg_literal('')})")
        sql = f"""
        with inserted as (
            insert into rag_chunk (document_id, chunk_index, chunk_text, milvus_id)
            values {",".join(values)}
            returning id as chunk_id, chunk_index, chunk_text
        )
        select coalesce(json_agg(inserted), '[]'::json)::text from inserted
        """
        return self._fetch_json(sql)

    def update_chunk_milvus_ids(self, chunk_ids: list[int]) -> None:
        if not chunk_ids:
            return
        cases = " ".join(f"when {int(chunk_id)} then {_pg_literal(str(int(chunk_id)))}" for chunk_id in chunk_ids)
        chunk_list = ",".join(str(int(chunk_id)) for chunk_id in chunk_ids)
        sql = f"""
        update rag_chunk
        set milvus_id = case id {cases} end
        where id in ({chunk_list})
        """
        self._execute_non_query(sql)

    def fetch_chunks_by_ids(self, chunk_ids: list[int]) -> list[dict[str, Any]]:
        if not chunk_ids:
            return []
        chunk_array = ",".join(str(int(chunk_id)) for chunk_id in chunk_ids)
        sql = f"""
        select
            c.id as chunk_id,
            c.document_id,
            d.title,
            d.biz_type,
            d.source_type,
            d.source_path,
            d.file_name,
            c.chunk_text
        from rag_chunk c
        join rag_document d on d.id = c.document_id
        where c.id = any(array[{chunk_array}]::bigint[])
        order by array_position(array[{chunk_array}]::bigint[], c.id)
        """
        return self._fetch_rows(sql)

    def fetch_recent_chunks(self, limit: int, biz_type: str | None = None) -> list[dict[str, Any]]:
        self.ensure_schema()
        where_clause = ""
        if biz_type:
            where_clause = f"where d.biz_type = {_pg_literal(biz_type)}"
        sql = f"""
        select
            c.id as chunk_id,
            c.document_id,
            d.title,
            d.biz_type,
            d.source_type,
            d.source_path,
            d.file_name,
            c.chunk_text
        from rag_chunk c
        join rag_document d on d.id = c.document_id
        {where_clause}
        order by c.id desc
        limit {int(limit)}
        """
        return self._fetch_rows(sql)

    def _detect_psql(self) -> str | None:
        configured = Path(self.settings.postgres_cli_path)
        if configured.exists():
            return str(configured)
        discovered = shutil.which("psql")
        return discovered

    def _execute_non_query(self, sql: str) -> None:
        if psycopg is not None:
            with psycopg.connect(
                host=self.settings.postgres_host,
                port=self.settings.postgres_port,
                user=self.settings.postgres_user,
                password=self.settings.postgres_password,
                dbname=self.settings.postgres_database,
            ) as connection:
                with connection.cursor() as cursor:
                    cursor.execute(sql)
            return
        self._run_psql(sql)

    def _fetch_scalar(self, sql: str) -> str:
        if psycopg is not None:
            with psycopg.connect(
                host=self.settings.postgres_host,
                port=self.settings.postgres_port,
                user=self.settings.postgres_user,
                password=self.settings.postgres_password,
                dbname=self.settings.postgres_database,
            ) as connection:
                with connection.cursor() as cursor:
                    cursor.execute(sql)
                    row = cursor.fetchone()
                    if row is None:
                        raise RuntimeError("Query returned no rows")
                    return str(row[0]).strip()

        output = self._run_psql(sql)
        if not output:
            raise RuntimeError("Query returned no rows")
        return self._first_data_line(self._strip_command_tags(output))

    def _fetch_rows(self, sql: str) -> list[dict[str, Any]]:
        if psycopg is not None:
            with psycopg.connect(
                host=self.settings.postgres_host,
                port=self.settings.postgres_port,
                user=self.settings.postgres_user,
                password=self.settings.postgres_password,
                dbname=self.settings.postgres_database,
                row_factory=dict_row,
            ) as connection:
                with connection.cursor() as cursor:
                    cursor.execute(sql)
                    return list(cursor.fetchall())

        wrapped = f"select coalesce(json_agg(t), '[]'::json)::text from ({sql}) t"
        output = self._run_psql(wrapped)
        text = self._strip_command_tags(output) if output else "[]"
        return json.loads(text)

    def _fetch_json(self, sql: str) -> list[dict[str, Any]]:
        if psycopg is not None:
            with psycopg.connect(
                host=self.settings.postgres_host,
                port=self.settings.postgres_port,
                user=self.settings.postgres_user,
                password=self.settings.postgres_password,
                dbname=self.settings.postgres_database,
            ) as connection:
                with connection.cursor() as cursor:
                    cursor.execute(sql)
                    row = cursor.fetchone()
                    return json.loads(row[0] if row and row[0] else "[]")

        output = self._run_psql(sql)
        text = self._strip_command_tags(output) if output else "[]"
        return json.loads(text)

    def _run_psql(self, sql: str) -> str:
        if not self._psql_path:
            raise RuntimeError(
                "PostgreSQL client backend unavailable. Install psycopg or configure POSTGRES_CLI_PATH."
            )

        env = os.environ.copy()
        env["PGPASSWORD"] = self.settings.postgres_password
        with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False, encoding="utf-8") as handle:
            handle.write(sql)
            temp_path = handle.name

        command = [
            self._psql_path,
            "-X",
            "-v",
            "ON_ERROR_STOP=1",
            "-P",
            "footer=off",
            "-h",
            self.settings.postgres_host,
            "-p",
            str(self.settings.postgres_port),
            "-U",
            self.settings.postgres_user,
            "-d",
            self.settings.postgres_database,
            "-tA",
            "-f",
            temp_path,
        ]
        try:
            completed = subprocess.run(
                command,
                check=True,
                capture_output=True,
                text=True,
                encoding="utf-8",
                env=env,
            )
            return completed.stdout.strip()
        except subprocess.CalledProcessError as exc:
            stderr = (exc.stderr or exc.stdout or "").strip()
            raise RuntimeError(stderr) from exc
        finally:
            try:
                Path(temp_path).unlink(missing_ok=True)
            except OSError:
                logger.debug("Failed to delete temp SQL file: %s", temp_path)

    def _first_data_line(self, output: str) -> str:
        for line in output.splitlines():
            text = line.strip()
            if text:
                return text
        raise RuntimeError("Query returned no rows")

    def _strip_command_tags(self, output: str) -> str:
        lines = [line.rstrip() for line in output.splitlines() if line.strip()]
        while lines and re.fullmatch(r"[A-Z]+(?: \d+)*", lines[-1]):
            lines.pop()
        return "\n".join(lines).strip()
