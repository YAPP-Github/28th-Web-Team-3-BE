CREATE INDEX idx_mission_guest_created_deleted
    ON mission (guest_user_id, created_at, deleted_at);

CREATE INDEX idx_manual_mission_guest_created_deleted
    ON manual_mission (guest_user_id, created_at, deleted_at);
