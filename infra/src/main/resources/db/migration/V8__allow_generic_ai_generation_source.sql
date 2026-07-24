ALTER TABLE mission_generation_job
    DROP CONSTRAINT ck_mission_generation_source;

ALTER TABLE mission_generation_job
    ADD CONSTRAINT ck_mission_generation_source
        CHECK (
            (status = 'SUCCEEDED' AND generation_source IN ('MOCK', 'AI', 'OPENAI', 'TEMPLATE_FALLBACK'))
            OR
            (status <> 'SUCCEEDED' AND generation_source IS NULL)
        );
