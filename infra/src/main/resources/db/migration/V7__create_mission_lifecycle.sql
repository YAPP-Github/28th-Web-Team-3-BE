ALTER TABLE mission DROP CONSTRAINT ck_mission_status;
ALTER TABLE mission ADD COLUMN week_ends_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE mission ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;
UPDATE mission
SET week_ends_at =
    CAST(
        CAST(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul' AS DATE) +
        CAST(
            8 - EXTRACT(
                ISODOW FROM CAST(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul' AS DATE)
            ) AS INTEGER
        )
        AS TIMESTAMP
    ) AT TIME ZONE 'Asia/Seoul';
ALTER TABLE mission ALTER COLUMN week_ends_at SET NOT NULL;
ALTER TABLE mission ADD CONSTRAINT ck_mission_status
    CHECK (status IN ('ACTIVE', 'COMPLETED', 'INCOMPLETE'));

CREATE TABLE manual_mission (
    id              UUID PRIMARY KEY,
    guest_user_id   BIGINT NOT NULL REFERENCES guest_user (id),
    category        VARCHAR(20) NOT NULL,
    mission_text    VARCHAR(500) NOT NULL,
    structured_tags VARCHAR(500) NOT NULL,
    target_count    INTEGER NOT NULL,
    target_unit     VARCHAR(40) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    week_ends_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_manual_mission_category
        CHECK (category IN ('MEAL', 'TRANSPORT', 'HOBBY', 'LIVING')),
    CONSTRAINT ck_manual_mission_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'INCOMPLETE')),
    CONSTRAINT ck_manual_mission_target CHECK (target_count > 0)
);

CREATE INDEX idx_manual_mission_guest_status
    ON manual_mission (guest_user_id, status, created_at);
CREATE INDEX idx_mission_weekly_status
    ON mission (status, week_ends_at);

CREATE TABLE mission_outcome_event (
    id             UUID PRIMARY KEY,
    guest_user_id  BIGINT NOT NULL REFERENCES guest_user (id),
    mission_source VARCHAR(20) NOT NULL,
    mission_id     UUID NOT NULL,
    final_status   VARCHAR(20) NOT NULL,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_mission_outcome_source CHECK (mission_source IN ('RECOMMENDED', 'MANUAL')),
    CONSTRAINT ck_mission_outcome_status CHECK (final_status IN ('COMPLETED', 'INCOMPLETE')),
    CONSTRAINT uk_mission_outcome_terminal UNIQUE (mission_source, mission_id)
);

CREATE INDEX idx_mission_outcome_guest_time
    ON mission_outcome_event (guest_user_id, occurred_at);
