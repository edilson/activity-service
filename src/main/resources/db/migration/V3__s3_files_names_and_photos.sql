alter table activities add column name varchar(255);
alter table activity_sources add column storage_bucket varchar(255);
alter table activity_sources add column object_key text;
alter table activity_sources add column object_url text;
alter table activity_sources add column file_size bigint;
create table activity_photos (
    id uuid primary key,
    activity_id uuid not null references activities(id) on delete cascade,
    filename varchar(255) not null,
    content_type varchar(255) not null,
    storage_bucket varchar(255) not null,
    object_key text not null,
    object_url text not null,
    file_size bigint not null check (file_size > 0),
    created_at timestamp with time zone not null
);
create index idx_photo_activity on activity_photos(activity_id, created_at);
-- Retain original_file only for safe migration of old imports. New uploads never populate it.
