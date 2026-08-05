ALTER TABLE mission_generation_job ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE mission_generation_job ADD COLUMN lease_token UUID;
ALTER TABLE mission_generation_job ADD COLUMN lease_expires_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE mission_generation_outbox (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES mission_generation_job (id) ON DELETE CASCADE,
    generation INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    claim_token UUID,
    published_at TIMESTAMP WITH TIME ZONE,
    task_name VARCHAR(180),
    last_error VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_mission_generation_outbox_job_generation UNIQUE (job_id, generation),
    CONSTRAINT uk_mission_generation_outbox_task_name UNIQUE (task_name),
    CONSTRAINT ck_mission_generation_outbox_generation CHECK (generation > 0),
    CONSTRAINT ck_mission_generation_outbox_status CHECK (status IN ('CREATED', 'CLAIMED', 'PUBLISHED'))
);

CREATE INDEX idx_mission_generation_outbox_dispatch
    ON mission_generation_outbox (status, next_attempt_at);

-- Jobs that were pending before the outbox rollout must remain deliverable.
INSERT INTO mission_generation_outbox (
    id, job_id, generation, status, next_attempt_at, created_at, updated_at
)
SELECT gen_random_uuid(), id, 1, 'CREATED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM mission_generation_job
WHERE status = 'PENDING' AND active_generation_key = 'ACTIVE';
