ALTER TABLE mission_survey_answer
    ADD COLUMN text_value VARCHAR(50);

ALTER TABLE mission_survey_answer
    DROP CONSTRAINT ck_mission_survey_answer_shape;

ALTER TABLE mission_survey_answer
    ADD CONSTRAINT ck_mission_survey_answer_shape
        CHECK (
            (
                value_type = 'OPTION'
                AND numeric_value IS NULL
                AND unit_code IS NULL
            )
            OR
            (
                value_type = 'NUMBER'
                AND numeric_value IS NOT NULL
                AND numeric_value >= 0
                AND text_value IS NULL
                AND unit_code IS NOT NULL
            )
        );

UPDATE mission_survey
SET schema_version = 'V2'
WHERE schema_version = 'V1';
