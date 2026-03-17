create table if not exists rag_document (
    id bigserial primary key,
    biz_type varchar(32) not null,
    title varchar(255) not null,
    source_type varchar(32) not null,
    source_path text,
    file_name varchar(255),
    content_hash varchar(64) not null,
    status varchar(32) not null default 'READY',
    created_by bigint,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create unique index if not exists uk_rag_document_biz_hash
    on rag_document (biz_type, content_hash);

create table if not exists rag_chunk (
    id bigserial primary key,
    document_id bigint not null references rag_document(id) on delete cascade,
    chunk_index int not null,
    chunk_text text not null,
    milvus_id varchar(128) not null default '',
    created_at timestamp not null default current_timestamp
);

create unique index if not exists uk_rag_chunk_document_index
    on rag_chunk (document_id, chunk_index);

create index if not exists idx_rag_chunk_document_id
    on rag_chunk (document_id);
