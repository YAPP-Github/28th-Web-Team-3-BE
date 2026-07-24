ALTER TABLE mission_draft_template ADD COLUMN reference_expense_label VARCHAR(80);
ALTER TABLE mission_draft_template ADD COLUMN alternative_expense_label VARCHAR(80);
ALTER TABLE mission_draft_template ADD COLUMN reference_expense_won INTEGER;
ALTER TABLE mission_draft_template ADD COLUMN alternative_expense_won INTEGER;
ALTER TABLE mission_draft_template ADD COLUMN expense_unit VARCHAR(20);
ALTER TABLE mission_draft_template ADD COLUMN estimate_basis VARCHAR(40);

ALTER TABLE mission_draft ADD COLUMN reference_expense_label VARCHAR(80);
ALTER TABLE mission_draft ADD COLUMN alternative_expense_label VARCHAR(80);
ALTER TABLE mission_draft ADD COLUMN reference_expense_won INTEGER;
ALTER TABLE mission_draft ADD COLUMN alternative_expense_won INTEGER;
ALTER TABLE mission_draft ADD COLUMN estimated_savings_per_unit_won INTEGER;
ALTER TABLE mission_draft ADD COLUMN expense_unit VARCHAR(20);
ALTER TABLE mission_draft ADD COLUMN estimate_basis VARCHAR(40);
ALTER TABLE mission_draft ADD COLUMN savings_description VARCHAR(300);
ALTER TABLE mission_draft ADD COLUMN savings_copy_source VARCHAR(30);
ALTER TABLE mission_draft ADD COLUMN savings_copy_version VARCHAR(40);

ALTER TABLE mission ADD COLUMN reference_expense_label VARCHAR(80);
ALTER TABLE mission ADD COLUMN alternative_expense_label VARCHAR(80);
ALTER TABLE mission ADD COLUMN reference_expense_won INTEGER;
ALTER TABLE mission ADD COLUMN alternative_expense_won INTEGER;
ALTER TABLE mission ADD COLUMN estimated_savings_per_unit_won INTEGER;
ALTER TABLE mission ADD COLUMN expense_unit VARCHAR(20);
ALTER TABLE mission ADD COLUMN estimate_basis VARCHAR(40);
ALTER TABLE mission ADD COLUMN savings_description VARCHAR(300);
ALTER TABLE mission ADD COLUMN savings_copy_source VARCHAR(30);
ALTER TABLE mission ADD COLUMN savings_copy_version VARCHAR(40);

ALTER TABLE mission_draft_template ADD CONSTRAINT ck_mission_template_expense_policy
    CHECK (
        (reference_expense_won IS NULL AND alternative_expense_won IS NULL
            AND reference_expense_label IS NULL AND alternative_expense_label IS NULL
            AND expense_unit IS NULL AND estimate_basis IS NULL)
        OR
        (reference_expense_won > alternative_expense_won AND alternative_expense_won >= 0
            AND reference_expense_label IS NOT NULL AND alternative_expense_label IS NOT NULL
            AND expense_unit IS NOT NULL AND estimate_basis IS NOT NULL)
    );

UPDATE mission_draft_template
SET reference_expense_label = '배달음식',
    alternative_expense_label = '집밥',
    reference_expense_won = 13000,
    alternative_expense_won = 8000,
    expense_unit = 'ORDER',
    estimate_basis = 'POLICY_REFERENCE_V1',
    average_savings_per_unit = 5000,
    estimated_savings_won = 5000,
    savings_estimate_version = 'POLICY_REFERENCE_V1'
WHERE action_code = 'REPLACE_DELIVERY_WITH_HOME_MEAL';
