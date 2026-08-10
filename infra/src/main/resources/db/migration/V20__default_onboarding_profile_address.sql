UPDATE onboarding_profile
SET address = 'SEOUL'
WHERE address IS NULL;

ALTER TABLE onboarding_profile
    ALTER COLUMN address SET DEFAULT 'SEOUL';
