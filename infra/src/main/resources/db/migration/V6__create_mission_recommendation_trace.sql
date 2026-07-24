CREATE TABLE mission_recommendation_snapshot (
    id                UUID PRIMARY KEY,
    guest_user_id     BIGINT NOT NULL REFERENCES guest_user (id),
    job_id            UUID UNIQUE REFERENCES mission_generation_job (id),
    algorithm_version VARCHAR(40) NOT NULL,
    semantic_provider VARCHAR(40) NOT NULL,
    semantic_model_version VARCHAR(80) NOT NULL,
    eligible_candidate_ids VARCHAR(4000) NOT NULL,
    retrieved_candidate_ids VARCHAR(4000) NOT NULL,
    weekly_context_snapshot VARCHAR(4000) NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE mission_recommendation_candidate (
    id                  UUID PRIMARY KEY,
    snapshot_id         UUID NOT NULL REFERENCES mission_recommendation_snapshot (id) ON DELETE CASCADE,
    template_id         BIGINT NOT NULL REFERENCES mission_draft_template (id),
    rank_position       INTEGER NOT NULL,
    raw_score           DOUBLE PRECISION NOT NULL,
    adjusted_score      DOUBLE PRECISION NOT NULL,
    retrieved           BOOLEAN NOT NULL,
    exploration_applied BOOLEAN NOT NULL,
    applied_penalties   VARCHAR(500) NOT NULL,
    selection_probability DOUBLE PRECISION,
    shown               BOOLEAN NOT NULL,
    CONSTRAINT uk_mission_recommendation_candidate UNIQUE (snapshot_id, template_id)
);

CREATE INDEX idx_mission_recommendation_guest_created
    ON mission_recommendation_snapshot (guest_user_id, created_at);
CREATE INDEX idx_mission_recommendation_candidate_snapshot
    ON mission_recommendation_candidate (snapshot_id, rank_position);
