INSERT INTO goal (
    guest_user_id,
    target_amount_manwon,
    period_months,
    monthly_target_manwon,
    base_amount_manwon,
    started_at,
    created_at,
    updated_at,
    version
)
SELECT
    onboarding_goal.guest_user_id,
    onboarding_goal.target_amount_manwon,
    onboarding_goal.period_months,
    onboarding_goal.monthly_saving_manwon,
    COALESCE(onboarding_profile.net_worth_manwon, 0),
    onboarding_goal.created_at,
    onboarding_goal.created_at,
    onboarding_goal.created_at,
    0
FROM onboarding_goal
JOIN onboarding_profile
    ON onboarding_profile.guest_user_id = onboarding_goal.guest_user_id
LEFT JOIN goal
    ON goal.guest_user_id = onboarding_goal.guest_user_id
WHERE onboarding_profile.status = 'COMPLETED'
    AND goal.id IS NULL;
