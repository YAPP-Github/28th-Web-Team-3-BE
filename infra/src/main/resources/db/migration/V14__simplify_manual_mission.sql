ALTER TABLE manual_mission ALTER COLUMN mission_text TYPE VARCHAR(30);

ALTER TABLE manual_mission DROP CONSTRAINT ck_manual_mission_target;
ALTER TABLE manual_mission DROP COLUMN target_count;
ALTER TABLE manual_mission DROP COLUMN target_unit;
ALTER TABLE manual_mission DROP COLUMN structured_tags;
