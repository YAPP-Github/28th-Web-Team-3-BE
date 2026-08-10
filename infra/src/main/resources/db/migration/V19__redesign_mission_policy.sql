-- Mission policy redesign after the existing V16-V18 migrations.
ALTER TABLE onboarding_profile ADD COLUMN address VARCHAR(20);
UPDATE onboarding_profile SET address = 'SEOUL' WHERE address IS NULL;
ALTER TABLE onboarding_profile ADD CONSTRAINT ck_onboarding_profile_address
    CHECK (address IS NULL OR address IN (
        'SEOUL', 'GYEONGGI', 'INCHEON', 'BUSAN', 'DAEGU', 'DAEJEON', 'SEJONG', 'ULSAN',
        'CHUNGNAM', 'CHUNGBUK', 'GYEONGNAM', 'GYEONGBUK', 'JEONNAM', 'JEONBUK', 'GANGWON', 'JEJU'
    ));

DELETE FROM mission_outcome_event
WHERE (mission_source = 'RECOMMENDED' AND mission_id IN (SELECT id FROM mission WHERE category = 'TRANSPORT'))
   OR (mission_source = 'MANUAL' AND mission_id IN (SELECT id FROM manual_mission WHERE category = 'TRANSPORT'));
DELETE FROM mission WHERE category = 'TRANSPORT';
DELETE FROM manual_mission WHERE category = 'TRANSPORT';
DELETE FROM mission_recommendation_candidate;
DELETE FROM mission_recommendation_snapshot;
DELETE FROM mission_survey_answer;
DELETE FROM mission_survey;

UPDATE mission_draft_template
SET active = FALSE,
    sort_order = sort_order + 100;

ALTER TABLE mission_generation_job ADD COLUMN category VARCHAR(20);
ALTER TABLE mission_generation_job ADD COLUMN item_code VARCHAR(40);
ALTER TABLE mission_generation_job ADD COLUMN baseline_frequency INTEGER;
ALTER TABLE mission_generation_job ADD COLUMN baseline_amount_won INTEGER;
ALTER TABLE mission_generation_job ADD CONSTRAINT ck_mission_generation_input
    CHECK (
        (category IS NULL AND item_code IS NULL AND baseline_frequency IS NULL AND baseline_amount_won IS NULL)
        OR
        (category IN ('MEAL', 'LIVING', 'HOBBY')
            AND item_code IS NOT NULL
            AND baseline_frequency BETWEEN 1 AND 10
            AND baseline_amount_won BETWEEN 1 AND 2000000)
    );

ALTER TABLE mission_draft ADD COLUMN item_code VARCHAR(40);
ALTER TABLE mission_draft ADD COLUMN title_template VARCHAR(120);
ALTER TABLE mission_draft ADD COLUMN priority_order INTEGER;
ALTER TABLE mission_draft DROP CONSTRAINT uk_mission_draft_job_template;
ALTER TABLE mission_draft ADD CONSTRAINT uk_mission_draft_job_priority
    UNIQUE (job_id, priority_order);

ALTER TABLE mission ADD COLUMN item_code VARCHAR(40);
ALTER TABLE mission ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE manual_mission ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE mission DROP CONSTRAINT ck_mission_category;
ALTER TABLE mission ADD CONSTRAINT ck_mission_category
    CHECK (category IN ('MEAL', 'LIVING', 'HOBBY'));
ALTER TABLE manual_mission DROP CONSTRAINT ck_manual_mission_category;
ALTER TABLE manual_mission ADD CONSTRAINT ck_manual_mission_category
    CHECK (category IN ('MEAL', 'LIVING', 'HOBBY'));

CREATE TABLE mission_weekly_completion (
    id              UUID PRIMARY KEY,
    guest_user_id   BIGINT NOT NULL REFERENCES guest_user (id),
    mission_source  VARCHAR(20) NOT NULL,
    mission_id      UUID NOT NULL,
    week_start_date DATE NOT NULL,
    completed_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_mission_weekly_completion_source
        CHECK (mission_source IN ('RECOMMENDED', 'MANUAL')),
    CONSTRAINT uk_mission_weekly_completion
        UNIQUE (mission_source, mission_id, week_start_date)
);
CREATE INDEX idx_mission_weekly_completion_guest_week
    ON mission_weekly_completion (guest_user_id, week_start_date);

CREATE TABLE mission_blog_tip (
    id            UUID PRIMARY KEY,
    guest_user_id BIGINT NOT NULL REFERENCES guest_user (id),
    item_code     VARCHAR(40) NOT NULL,
    title         VARCHAR(300) NOT NULL,
    source        VARCHAR(200) NOT NULL,
    url           VARCHAR(1000) NOT NULL,
    searched_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_mission_blog_tip_user_url UNIQUE (guest_user_id, url)
);

INSERT INTO mission_draft_template (
    category, title, description, action_code, metric_type, target_count, target_unit,
    estimated_savings_won, sort_order, active, target_code, eligible_codes, excluded_codes,
    target_formula, cooldown_family, verification_type, average_savings_per_unit,
    savings_estimate_version, embedding_text
) VALUES
    ('MEAL', '배달음식 {count}회 대체하기', '배달음식을 대신할 수 있는 방법을 실천해 보세요.', 'DELIVERY_FOOD', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 1, TRUE, 'DELIVERY_FOOD', '', '', 'FIXED', 'DELIVERY_FOOD', 'SELF_REPORT', 0, 'V2', '배달음식 절약 대안'),
    ('MEAL', '외식 {count}회 대체하기', '외식비를 줄일 수 있는 방법을 실천해 보세요.', 'DINING_OUT', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 2, TRUE, 'DINING_OUT', '', '', 'FIXED', 'DINING_OUT', 'SELF_REPORT', 0, 'V2', '외식 절약 대안'),
    ('MEAL', '술자리 {count}회 대체하기', '술자리 지출을 줄일 수 있는 방법을 실천해 보세요.', 'DRINKING', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 3, TRUE, 'DRINKING', '', '', 'FIXED', 'DRINKING', 'SELF_REPORT', 0, 'V2', '술자리 절약 대안'),
    ('MEAL', '카페 {count}회 대체하기', '카페 지출을 줄일 수 있는 방법을 실천해 보세요.', 'CAFE', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 4, TRUE, 'CAFE', '', '', 'FIXED', 'CAFE', 'SELF_REPORT', 0, 'V2', '카페 절약 대안'),
    ('MEAL', '간식 {count}회 대체하기', '간식 지출을 줄일 수 있는 방법을 실천해 보세요.', 'SNACK', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 5, TRUE, 'SNACK', '', '', 'FIXED', 'SNACK', 'SELF_REPORT', 0, 'V2', '간식 절약 대안'),
    ('MEAL', '편의점 {count}회 대체하기', '편의점 지출을 줄일 수 있는 방법을 실천해 보세요.', 'CONVENIENCE_STORE', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 6, TRUE, 'CONVENIENCE_STORE', '', '', 'FIXED', 'CONVENIENCE_STORE', 'SELF_REPORT', 0, 'V2', '편의점 절약 대안'),
    ('LIVING', '의류 구매 {count}회 대체하기', '의류 지출을 줄일 수 있는 방법을 실천해 보세요.', 'CLOTHING', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 1, TRUE, 'CLOTHING', '', '', 'FIXED', 'CLOTHING', 'SELF_REPORT', 0, 'V2', '의류 절약 대안'),
    ('LIVING', '화장품 구매 {count}회 대체하기', '화장품 지출을 줄일 수 있는 방법을 실천해 보세요.', 'COSMETICS', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 2, TRUE, 'COSMETICS', '', '', 'FIXED', 'COSMETICS', 'SELF_REPORT', 0, 'V2', '화장품 절약 대안'),
    ('LIVING', '생활용품 구매 {count}회 대체하기', '생활용품 지출을 줄일 수 있는 방법을 실천해 보세요.', 'HOUSEHOLD_GOODS', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 3, TRUE, 'HOUSEHOLD_GOODS', '', '', 'FIXED', 'HOUSEHOLD_GOODS', 'SELF_REPORT', 0, 'V2', '생활용품 절약 대안'),
    ('LIVING', '미용 소비 {count}회 대체하기', '미용 지출을 줄일 수 있는 방법을 실천해 보세요.', 'BEAUTY', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 4, TRUE, 'BEAUTY', '', '', 'FIXED', 'BEAUTY', 'SELF_REPORT', 0, 'V2', '미용 절약 대안'),
    ('LIVING', '자기계발 소비 {count}회 대체하기', '자기계발 지출을 줄일 수 있는 방법을 실천해 보세요.', 'SELF_DEVELOPMENT', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 5, TRUE, 'SELF_DEVELOPMENT', '', '', 'FIXED', 'SELF_DEVELOPMENT', 'SELF_REPORT', 0, 'V2', '자기계발 절약 대안'),
    ('HOBBY', '용품과 굿즈 구매 {count}회 대체하기', '용품과 굿즈 지출을 줄일 수 있는 방법을 실천해 보세요.', 'HOBBY_GOODS', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 1, TRUE, 'HOBBY_GOODS', '', '', 'FIXED', 'HOBBY_GOODS', 'SELF_REPORT', 0, 'V2', '용품 굿즈 절약 대안'),
    ('HOBBY', '게임 소비 {count}회 대체하기', '게임 지출을 줄일 수 있는 방법을 실천해 보세요.', 'GAME', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 2, TRUE, 'GAME', '', '', 'FIXED', 'GAME', 'SELF_REPORT', 0, 'V2', '게임 절약 대안'),
    ('HOBBY', '디지털 콘텐츠 소비 {count}회 대체하기', '디지털 콘텐츠 지출을 줄일 수 있는 방법을 실천해 보세요.', 'DIGITAL_CONTENT', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 3, TRUE, 'DIGITAL_CONTENT', '', '', 'FIXED', 'DIGITAL_CONTENT', 'SELF_REPORT', 0, 'V2', '디지털 콘텐츠 절약 대안'),
    ('HOBBY', '수업과 클래스 {count}회 대체하기', '수업과 클래스 지출을 줄일 수 있는 방법을 실천해 보세요.', 'CLASS', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 4, TRUE, 'CLASS', '', '', 'FIXED', 'CLASS', 'SELF_REPORT', 0, 'V2', '수업 클래스 절약 대안'),
    ('HOBBY', '공연과 전시 소비 {count}회 대체하기', '공연과 전시 지출을 줄일 수 있는 방법을 실천해 보세요.', 'PERFORMANCE_TICKET', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 5, TRUE, 'PERFORMANCE_TICKET', '', '', 'FIXED', 'PERFORMANCE_TICKET', 'SELF_REPORT', 0, 'V2', '공연 전시 티켓 절약 대안'),
    ('HOBBY', '동호회와 모임 {count}회 대체하기', '동호회와 모임 지출을 줄일 수 있는 방법을 실천해 보세요.', 'CLUB_GATHERING', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 6, TRUE, 'CLUB_GATHERING', '', '', 'FIXED', 'CLUB_GATHERING', 'SELF_REPORT', 0, 'V2', '동호회 모임 절약 대안'),
    ('HOBBY', '장비 대여 {count}회 대체하기', '장비 대여 지출을 줄일 수 있는 방법을 실천해 보세요.', 'EQUIPMENT_RENTAL', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 7, TRUE, 'EQUIPMENT_RENTAL', '', '', 'FIXED', 'EQUIPMENT_RENTAL', 'SELF_REPORT', 0, 'V2', '장비 대여 절약 대안'),
    ('HOBBY', '공간 이용 {count}회 대체하기', '공간 이용 지출을 줄일 수 있는 방법을 실천해 보세요.', 'SPACE_USE', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 8, TRUE, 'SPACE_USE', '', '', 'FIXED', 'SPACE_USE', 'SELF_REPORT', 0, 'V2', '공간 이용 절약 대안');
