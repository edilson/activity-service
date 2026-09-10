create table activity_sources (
    activity_id uuid primary key references activities(id) on delete cascade,
    filename varchar(255),
    content_type varchar(255),
    original_file bytea,
    source_url text,
    provider_response jsonb,
    route jsonb not null
);
