ALTER TABLE mission_survey_answer
    ADD COLUMN range_code VARCHAR(60);

DELETE FROM mission_survey
WHERE id IN (
    SELECT mission_survey_id
    FROM mission_survey_answer
    WHERE question_code IN ('MEAL_FREQUENCY', 'TRANSPORT_FREQUENCY')
      AND value_type = 'NUMBER'
      AND numeric_value = 0
);

UPDATE mission_survey_answer
SET range_code = CASE
        WHEN numeric_value BETWEEN 1 AND 2 THEN 'ONE_TO_TWO'
        WHEN numeric_value BETWEEN 3 AND 4 THEN 'THREE_TO_FOUR'
        WHEN numeric_value BETWEEN 5 AND 6 THEN 'FIVE_TO_SIX'
        ELSE 'SEVEN_OR_MORE'
    END,
    numeric_value = CASE
        WHEN numeric_value BETWEEN 1 AND 2 THEN 1
        WHEN numeric_value BETWEEN 3 AND 4 THEN 3
        WHEN numeric_value BETWEEN 5 AND 6 THEN 5
        ELSE 7
    END
WHERE question_code IN ('MEAL_FREQUENCY', 'TRANSPORT_FREQUENCY')
  AND value_type = 'NUMBER';

UPDATE mission_survey
SET schema_version = 'V3';
