from app.config import get_settings
from app.infra.embedding_client import EmbeddingClient
from app.infra.milvus_store import MilvusStore
from app.infra.pg_client import PgClient
from app.infra.redis_client import RedisClient
from app.service.ingest_service import IngestService


def main() -> None:
    settings = get_settings()
    service = IngestService(
        settings=settings,
        pg_client=PgClient(settings),
        milvus_store=MilvusStore(settings),
        embedding_client=EmbeddingClient(settings),
        redis_client=RedisClient(settings),
    )
    result = service.rebuild_from_directory()
    print(result.model_dump_json(indent=2))


if __name__ == "__main__":
    main()
