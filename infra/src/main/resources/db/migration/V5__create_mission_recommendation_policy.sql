ALTER TABLE mission_draft_template ADD COLUMN target_code VARCHAR(80) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE mission_draft_template ADD COLUMN eligible_codes VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE mission_draft_template ADD COLUMN excluded_codes VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE mission_draft_template ADD COLUMN target_formula VARCHAR(30) NOT NULL DEFAULT 'FIXED';
ALTER TABLE mission_draft_template ADD COLUMN cooldown_family VARCHAR(80) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE mission_draft_template ADD COLUMN verification_type VARCHAR(40) NOT NULL DEFAULT 'SELF_REPORT';
ALTER TABLE mission_draft_template ADD COLUMN average_savings_per_unit INTEGER NOT NULL DEFAULT 0;
ALTER TABLE mission_draft_template ADD COLUMN savings_estimate_version VARCHAR(40) NOT NULL DEFAULT 'V1';
ALTER TABLE mission_draft_template ADD COLUMN embedding_text VARCHAR(1000) NOT NULL DEFAULT '';

ALTER TABLE mission_draft ADD COLUMN savings_estimate_version VARCHAR(40) NOT NULL DEFAULT 'V1';
ALTER TABLE mission ADD COLUMN savings_estimate_version VARCHAR(40) NOT NULL DEFAULT 'V1';

UPDATE mission_draft_template
SET target_code = 'GENERAL',
    cooldown_family = action_code,
    target_formula = CASE WHEN metric_type = 'CHECK' THEN 'CHECK' ELSE 'FIXED' END,
    average_savings_per_unit = estimated_savings_won,
    embedding_text = title || ' ' || description;

UPDATE mission_draft_template
SET target_code = 'DELIVERY',
    eligible_codes = 'DELIVERY,COOK,PREPARE_MEAL',
    excluded_codes = 'HEALTH_OR_DIET,FIXED_MEAL,NO_COOKING_ENVIRONMENT,NO_REDUCE_FOOD_AMOUNT',
    cooldown_family = 'DELIVERY_REPLACEMENT',
    target_formula = 'REPLACE'
WHERE action_code = 'REPLACE_DELIVERY_WITH_HOME_MEAL';

UPDATE mission_draft_template
SET target_code = 'PAID_BEVERAGE',
    eligible_codes = 'PAID_BEVERAGE,PREPARE_BEVERAGE',
    cooldown_family = 'PAID_BEVERAGE',
    target_formula = 'REDUCE_MAX'
WHERE action_code = 'REDUCE_PAID_BEVERAGE';

UPDATE mission_draft_template
SET target_code = 'TAXI',
    eligible_codes = 'TAXI',
    excluded_codes = 'LATE_NIGHT_DANGER,EXTREME_WEATHER,MOBILITY_CONSTRAINT,LUGGAGE_OR_CARE,NO_TRANSIT',
    cooldown_family = 'TAXI_REPLACEMENT',
    target_formula = 'REPLACE'
WHERE action_code = 'REPLACE_TAXI_WITH_TRANSIT';

UPDATE mission_draft_template
SET target_code = 'SHORT_DISTANCE_PAID_MOVE',
    eligible_codes = 'SHORT_DISTANCE_PAID_MOVE,WALK_OR_BICYCLE',
    excluded_codes = 'EXTREME_WEATHER,MOBILITY_CONSTRAINT,LUGGAGE_OR_CARE',
    cooldown_family = 'SHORT_DISTANCE_MOVE',
    target_formula = 'REPLACE'
WHERE action_code = 'WALK_SHORT_DISTANCE';

UPDATE mission_draft_template
SET target_code = 'GOODS',
    eligible_codes = 'GOODS,WAIT_BEFORE_BUYING',
    excluded_codes = 'DO_NOT_REDUCE,NO_HOBBY_MISSION',
    cooldown_family = 'HOBBY_PURCHASE',
    target_formula = 'CHECK'
WHERE action_code = 'WAIT_BEFORE_HOBBY_PURCHASE';

UPDATE mission_draft_template
SET target_code = 'ONLINE_SHOPPING',
    eligible_codes = 'ONLINE_SHOPPING,WAIT_24_HOURS',
    excluded_codes = 'NO_LIVING_MISSION,EXCLUDE_NECESSARY_COST',
    cooldown_family = 'LIVING_PURCHASE',
    target_formula = 'CHECK'
WHERE action_code = 'WAIT_BEFORE_ONLINE_PURCHASE';

ALTER TABLE mission_draft_template ADD CONSTRAINT ck_mission_template_target_formula
    CHECK (target_formula IN ('REDUCE_MAX', 'REPLACE', 'FIXED', 'CHECK', 'RECORD'));
ALTER TABLE mission_draft_template ADD CONSTRAINT ck_mission_template_average_savings
    CHECK (average_savings_per_unit >= 0);

INSERT INTO mission_draft_template
    (category, title, description, action_code, metric_type, target_count, target_unit,
     estimated_savings_won, sort_order, active, target_code, eligible_codes, excluded_codes,
     target_formula, cooldown_family, verification_type, average_savings_per_unit,
     savings_estimate_version, embedding_text)
VALUES
    ('MEAL', '식사 소비 기록하기', '이번 주 식사 관련 소비를 기록하고 지출 습관을 점검해 보세요.',
     'RECORD_MEAL_SPENDING', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 5, TRUE, 'GENERAL', '', '',
     'RECORD', 'MEAL_RECORD', 'SELF_REPORT', 0, 'V1', '식사 소비 횟수와 금액을 기록하고 점검'),
    ('MEAL', '음식 앱 알림 끄기', '충동 주문을 줄일 수 있도록 음식 관련 앱 알림을 점검해 보세요.',
     'DISABLE_FOOD_APP_NOTIFICATIONS', 'CHECK', 1, 'TIMES_PER_WEEK', 0, 6, TRUE, 'GENERAL',
     'DISCOUNT_OR_NOTIFICATION', '', 'CHECK', 'FOOD_NOTIFICATION', 'SELF_REPORT', 0, 'V1',
     '배달 음식 앱 할인 쿠폰 알림을 끄고 충동 주문 방지'),
    ('TRANSPORT', '교통비 사용 기록하기', '이번 주 유료 이동 횟수를 기록하고 이동 습관을 점검해 보세요.',
     'RECORD_TRANSPORT_SPENDING', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 5, TRUE, 'GENERAL', '', '',
     'RECORD', 'TRANSPORT_RECORD', 'SELF_REPORT', 0, 'V1', '택시 대중교통 자가용 교통비 횟수 기록'),
    ('TRANSPORT', '교통 할인 가능성 확인하기', '정기권이나 할인 적용 가능 여부를 한 번 확인해 보세요.',
     'CHECK_TRANSPORT_DISCOUNT', 'CHECK', 1, 'TIMES_PER_WEEK', 0, 6, TRUE, 'GENERAL', '', '',
     'CHECK', 'TRANSPORT_DISCOUNT', 'SELF_REPORT', 0, 'V1', '대중교통 정기권 할인 가능 여부 확인'),
    ('HOBBY', '취미 구매 횟수 기록하기', '이번 주 취미 관련 구매 횟수를 기록하고 점검해 보세요.',
     'RECORD_HOBBY_PURCHASES', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 5, TRUE, 'GENERAL', '',
     'DO_NOT_REDUCE,NO_HOBBY_MISSION', 'RECORD', 'HOBBY_RECORD', 'SELF_REPORT', 0, 'V1',
     '취미 상품 콘텐츠 결제 구매 횟수 기록'),
    ('HOBBY', '무료 취미 대안 찾아보기', '현재 취미를 유지하면서 무료 또는 저렴한 대안을 찾아보세요.',
     'FIND_CHEAPER_HOBBY_ALTERNATIVE', 'CHECK', 1, 'TIMES_PER_WEEK', 0, 6, TRUE, 'GENERAL',
     'USE_CHEAPER_ALTERNATIVE,KEEP_TIME_REDUCE_COST', 'NO_HOBBY_MISSION', 'CHECK',
     'HOBBY_ALTERNATIVE', 'SELF_REPORT', 0, 'V1', '취미 시간은 유지하고 무료 저렴한 대안 탐색'),
    ('LIVING', '생활 소비 기록하기', '이번 주 생활 관련 소비 횟수를 기록하고 점검해 보세요.',
     'RECORD_LIVING_SPENDING', 'COUNT', 1, 'TIMES_PER_WEEK', 0, 5, TRUE, 'GENERAL', '',
     'NO_LIVING_MISSION', 'RECORD', 'LIVING_RECORD', 'SELF_REPORT', 0, 'V1',
     '생활용품 의류 쇼핑 소비 횟수 기록'),
    ('LIVING', '장바구니 정리하기', '필요하지 않은 상품을 장바구니에서 정리해 보세요.',
     'CLEAN_SHOPPING_CART', 'CHECK', 1, 'TIMES_PER_WEEK', 0, 6, TRUE, 'ONLINE_SHOPPING',
     'ONLINE_SHOPPING', 'NO_LIVING_MISSION', 'CHECK', 'SHOPPING_CART', 'SELF_REPORT', 0, 'V1',
     '온라인 쇼핑 장바구니 상품 필요 여부 점검 정리');

INSERT INTO mission_draft_template
    (category, title, description, action_code, metric_type, target_count, target_unit,
     estimated_savings_won, sort_order, active, target_code, eligible_codes, excluded_codes,
     target_formula, cooldown_family, verification_type, average_savings_per_unit,
     savings_estimate_version, embedding_text)
VALUES
    ('MEAL', '배달 횟수 제한하기', '평소 배달 횟수에서 계획한 만큼 줄인 주간 상한을 지켜보세요.',
     'LIMIT_DELIVERY_COUNT', 'COUNT', 1, 'TIMES_PER_WEEK', 12000, 7, TRUE, 'DELIVERY', 'DELIVERY',
     'HEALTH_OR_DIET,FIXED_MEAL,UNAVOIDABLE_SCHEDULE,NO_REDUCE_FOOD_AMOUNT', 'REDUCE_MAX',
     'DELIVERY_COUNT', 'SELF_REPORT', 12000, 'V1', '배달 주문 횟수 주간 상한 감축'),
    ('MEAL', '배달 한 끼를 포장으로 바꾸기', '배달 한 번을 직접 포장 주문으로 바꿔보세요.',
     'REPLACE_DELIVERY_WITH_PICKUP', 'COUNT', 1, 'TIMES_PER_WEEK', 4000, 8, TRUE, 'DELIVERY',
     'DELIVERY,PICKUP', 'UNAVOIDABLE_SCHEDULE', 'REPLACE', 'DELIVERY_REPLACEMENT', 'SELF_REPORT',
     4000, 'V1', '배달 대신 포장 주문으로 대체'),
    ('MEAL', '외식 횟수 제한하기', '평소 외식 횟수에서 계획한 만큼 줄인 주간 상한을 지켜보세요.',
     'LIMIT_DINING_OUT', 'COUNT', 1, 'TIMES_PER_WEEK', 15000, 9, TRUE, 'DINING_OUT', 'DINING_OUT',
     'FIXED_MEAL,UNAVOIDABLE_SCHEDULE,NO_REDUCE_FOOD_AMOUNT', 'REDUCE_MAX', 'DINING_OUT',
     'SELF_REPORT', 15000, 'V1', '외식 횟수 주간 상한 감축'),
    ('MEAL', '도시락이나 간편식 준비하기', '외부 식사 한 번을 준비한 도시락이나 간편식으로 바꿔보세요.',
     'PREPARE_PORTABLE_MEAL', 'COUNT', 1, 'TIMES_PER_WEEK', 10000, 10, TRUE, 'DINING_OUT',
     'DINING_OUT,PREPARE_MEAL', 'NO_COOKING_ENVIRONMENT', 'REPLACE', 'PREPARED_MEAL',
     'SELF_REPORT', 10000, 'V1', '도시락 간편식 준비 외식 대체'),
    ('MEAL', '술자리 횟수 제한하기', '피하기 어려운 일정을 제외하고 술자리 횟수를 줄여보세요.',
     'LIMIT_DRINKING_GATHERING', 'COUNT', 1, 'TIMES_PER_WEEK', 20000, 11, TRUE,
     'DRINKING_GATHERING', 'DRINKING_GATHERING', 'UNAVOIDABLE_SCHEDULE', 'REDUCE_MAX',
     'DRINKING_GATHERING', 'SELF_REPORT', 20000, 'V1', '술자리 회식 횟수 감축'),
    ('MEAL', '계획한 식재료만 구매하기', '식재료 목록을 만들고 계획에 없는 추가 구매를 피하세요.',
     'BUY_PLANNED_INGREDIENTS', 'CHECK', 1, 'TIMES_PER_WEEK', 5000, 12, TRUE, 'GENERAL',
     'BUY_PLANNED_INGREDIENTS', '', 'CHECK', 'PLANNED_INGREDIENTS', 'SELF_REPORT', 5000, 'V1',
     '식재료 구매 목록 계획 충동 구매 방지'),
    ('TRANSPORT', '택시 이용 횟수 제한하기', '안전상 필요한 이동을 제외하고 택시 주간 상한을 지켜보세요.',
     'LIMIT_TAXI_COUNT', 'COUNT', 1, 'TIMES_PER_WEEK', 12000, 7, TRUE, 'TAXI', 'TAXI',
     'LATE_NIGHT_DANGER,EXTREME_WEATHER,MOBILITY_CONSTRAINT,LUGGAGE_OR_CARE,NO_TRANSIT',
     'REDUCE_MAX', 'TAXI_COUNT', 'SELF_REPORT', 12000, 'V1', '택시 이용 횟수 주간 상한 감축'),
    ('TRANSPORT', '조금 일찍 출발하기', '급한 유료 이동을 피할 수 있도록 한 번 일찍 출발해 보세요.',
     'LEAVE_EARLY', 'COUNT', 1, 'TIMES_PER_WEEK', 8000, 8, TRUE, 'RUSH_COST',
     'RUSH_COST,TIME_PRESSURE', 'UNAVOIDABLE_SCHEDULE', 'REPLACE', 'RUSH_TRAVEL',
     'SELF_REPORT', 8000, 'V1', '시간 부족 지각 택시 방지 일찍 출발'),
    ('TRANSPORT', '자가용 없는 날 만들기', '필수 이동을 제외하고 자가용을 사용하지 않는 날을 만들어 보세요.',
     'CAR_FREE_DAY', 'COUNT', 1, 'DAYS_PER_WEEK', 8000, 9, TRUE, 'CAR_DRIVING',
     'CAR_DRIVING', 'EXTREME_WEATHER,MOBILITY_CONSTRAINT,LUGGAGE_OR_CARE,NO_TRANSIT',
     'REPLACE', 'CAR_DRIVING', 'SELF_REPORT', 8000, 'V1', '자가용 대신 대중교통 도보 하루'),
    ('TRANSPORT', '무료 주차 이동 방식 확인하기', '유료주차가 필요한 이동 전에 대안을 확인해 보세요.',
     'AVOID_PAID_PARKING', 'CHECK', 1, 'TIMES_PER_WEEK', 5000, 10, TRUE, 'PARKING_OR_TOLL',
     'PARKING_OR_TOLL', 'MOBILITY_CONSTRAINT,LUGGAGE_OR_CARE', 'CHECK', 'PARKING_COST',
     'SELF_REPORT', 5000, 'V1', '유료 주차 통행료 없는 이동 방식 확인'),
    ('HOBBY', '주간 취미 구매 횟수 제한하기', '평소 결제 횟수에서 계획한 만큼 줄인 주간 상한을 정해보세요.',
     'LIMIT_HOBBY_PURCHASES', 'COUNT', 1, 'TIMES_PER_WEEK', 10000, 7, TRUE, 'GOODS',
     'GOODS,SET_WEEKLY_LIMIT', 'DO_NOT_REDUCE,NO_HOBBY_MISSION', 'REDUCE_MAX',
     'HOBBY_PURCHASE', 'SELF_REPORT', 10000, 'V1', '취미 용품 굿즈 구매 횟수 주간 제한'),
    ('HOBBY', '취미비를 쓰지 않는 날 만들기', '취미 시간은 유지하면서 결제하지 않는 날을 만들어 보세요.',
     'HOBBY_NO_SPEND_DAY', 'COUNT', 1, 'DAYS_PER_WEEK', 5000, 8, TRUE, 'GENERAL',
     'KEEP_TIME_REDUCE_COST', 'DO_NOT_REDUCE,NO_HOBBY_MISSION', 'FIXED', 'HOBBY_NO_SPEND',
     'SELF_REPORT', 5000, 'V1', '취미 시간 유지 비용 지출 없는 날'),
    ('HOBBY', '게임·콘텐츠 소액결제 제한하기', '디지털 콘텐츠 소액결제 주간 상한을 지켜보세요.',
     'LIMIT_DIGITAL_CONTENT_PURCHASES', 'COUNT', 1, 'TIMES_PER_WEEK', 5000, 9, TRUE,
     'DIGITAL_CONTENT', 'DIGITAL_CONTENT,SET_WEEKLY_LIMIT', 'DO_NOT_REDUCE,NO_HOBBY_MISSION',
     'REDUCE_MAX', 'DIGITAL_CONTENT', 'SELF_REPORT', 5000, 'V1',
     '게임 디지털 콘텐츠 소액결제 횟수 제한'),
    ('HOBBY', '사용하지 않는 취미 용품 정리하기', '사용하지 않는 취미 용품을 판매하거나 나눔할지 점검해 보세요.',
     'DECLUTTER_HOBBY_ITEMS', 'CHECK', 1, 'TIMES_PER_WEEK', 0, 10, TRUE, 'GOODS',
     'GOODS,USE_OWNED_FIRST', 'NO_HOBBY_MISSION', 'CHECK', 'HOBBY_DECLUTTER',
     'SELF_REPORT', 0, 'V1', '사용하지 않는 취미 용품 판매 나눔 정리'),
    ('LIVING', '온라인 쇼핑 횟수 제한하기', '평소 온라인 쇼핑 횟수에서 계획한 만큼 줄인 상한을 지켜보세요.',
     'LIMIT_ONLINE_SHOPPING', 'COUNT', 1, 'TIMES_PER_FOUR_WEEKS', 12000, 7, TRUE,
     'ONLINE_SHOPPING', 'ONLINE_SHOPPING,LIMIT_FREQUENCY', 'NO_LIVING_MISSION,EXCLUDE_NECESSARY_COST',
     'REDUCE_MAX', 'ONLINE_SHOPPING', 'SELF_REPORT', 12000, 'V1',
     '온라인 쇼핑 구매 횟수 제한'),
    ('LIVING', '생활용품을 사지 않는 날 만들기', '필수 구매를 제외하고 생활용품을 사지 않는 날을 만들어 보세요.',
     'LIVING_NO_SPEND_DAY', 'COUNT', 1, 'DAYS_PER_WEEK', 5000, 8, TRUE, 'GENERAL',
     'LIMIT_FREQUENCY', 'NO_LIVING_MISSION,EXCLUDE_NECESSARY_COST', 'FIXED', 'LIVING_NO_SPEND',
     'SELF_REPORT', 5000, 'V1', '생활용품 의류 지출 없는 날 필수 비용 제외'),
    ('LIVING', '미용·자기관리 이용 횟수 제한하기', '필수 관리가 아닌 이용 횟수의 주간 상한을 정해보세요.',
     'LIMIT_BEAUTY_VISITS', 'COUNT', 1, 'TIMES_PER_FOUR_WEEKS', 20000, 9, TRUE, 'BEAUTY',
     'BEAUTY,LIMIT_FREQUENCY', 'NO_LIVING_MISSION,EXCLUDE_NECESSARY_COST', 'REDUCE_MAX',
     'BEAUTY_VISIT', 'SELF_REPORT', 20000, 'V1', '미용 뷰티 자기관리 이용 횟수 제한'),
    ('LIVING', '집에서 자기관리하기', '외부 자기관리 한 번을 집에서 할 수 있는 활동으로 바꿔보세요.',
     'REPLACE_BEAUTY_WITH_HOME_CARE', 'COUNT', 1, 'TIMES_PER_FOUR_WEEKS', 15000, 10, TRUE,
     'BEAUTY', 'BEAUTY', 'NO_LIVING_MISSION,EXCLUDE_NECESSARY_COST', 'REPLACE',
     'BEAUTY_REPLACEMENT', 'SELF_REPORT', 15000, 'V1', '미용 자기관리 외부 이용 집에서 대체'),
    ('LIVING', '수리·대여·중고 먼저 확인하기', '새 제품을 사기 전에 수리·대여·중고 대안을 확인해 보세요.',
     'CHECK_REPAIR_RENTAL_USED', 'CHECK', 1, 'TIMES_PER_WEEK', 10000, 11, TRUE, 'GENERAL',
     'CONSIDER_REUSE', 'NO_LIVING_MISSION', 'CHECK', 'LIVING_REUSE', 'SELF_REPORT', 10000, 'V1',
     '새 제품 구매 전 수리 대여 중고 재사용 확인'),
    ('LIVING', '쇼핑 목록만 구매하기', '필요한 물건을 목록으로 만들고 목록 밖 구매를 피하세요.',
     'BUY_ONLY_SHOPPING_LIST', 'COUNT', 1, 'TIMES_PER_WEEK', 8000, 12, TRUE, 'GENERAL',
     'USE_SHOPPING_LIST', 'NO_LIVING_MISSION', 'FIXED', 'SHOPPING_LIST', 'SELF_REPORT', 8000, 'V1',
     '쇼핑 목록 필요한 물건만 구매 충동 소비 방지');
