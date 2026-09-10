create table activities (
    id uuid primary key,
    owner varchar(255) not null,
    source varchar(255) not null,
    distance_meters double precision not null check (distance_meters > 0),
    duration_seconds double precision not null check (duration_seconds > 0),
    elevation_gain_meters double precision not null check (elevation_gain_meters >= 0),
    polyline text,
    created_at timestamp with time zone not null
);
create index idx_activity_owner on activities(owner, created_at);
create table outbox_events (
    id uuid primary key,
    activity_id uuid not null references activities(id),
    owner varchar(255) not null,
    distance_meters double precision not null,
    created_at timestamp with time zone not null,
    published boolean not null default false,
    attempts integer not null default 0,
    next_attempt_at timestamp with time zone not null
);
create index idx_outbox_pending on outbox_events(published, next_attempt_at, created_at);
create table provider_connections (
    id uuid primary key,
    owner varchar(255) not null,
    provider varchar(255) not null,
    provider_user_id varchar(255) not null,
    encrypted_tokens text not null,
    expires_at timestamp with time zone not null,
    unique(owner, provider)
);
