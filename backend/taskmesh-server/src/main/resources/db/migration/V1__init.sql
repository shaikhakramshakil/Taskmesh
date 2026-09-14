CREATE TABLE jobs (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    priority INT NOT NULL,
    cpu INT NOT NULL,
    memory_mb BIGINT NOT NULL,
    deadline TIMESTAMP WITH TIME ZONE NULL,
    metadata TEXT NULL,
    status VARCHAR(16) NOT NULL,
    worker_id VARCHAR(36) NULL,
    attempts INT NOT NULL,
    max_attempts INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NULL,
    finished_at TIMESTAMP WITH TIME ZONE NULL,
    error VARCHAR(1024) NULL,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_jobs_status ON jobs (status);

CREATE TABLE workers (
    id VARCHAR(128) PRIMARY KEY,
    address VARCHAR(512) NOT NULL,
    total_cpu INT NOT NULL,
    total_memory_mb BIGINT NOT NULL,
    available_cpu INT NOT NULL,
    available_memory_mb BIGINT NOT NULL,
    running_jobs INT NOT NULL,
    health VARCHAR(16) NOT NULL,
    last_heartbeat TIMESTAMP WITH TIME ZONE NOT NULL,
    missed_beats INT NOT NULL DEFAULT 0
);
