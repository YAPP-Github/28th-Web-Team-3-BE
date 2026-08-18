CREATE TABLE mission_knowledge (
    id                  BIGSERIAL PRIMARY KEY,
    category            VARCHAR(20) NOT NULL,
    item_code           VARCHAR(40) NOT NULL,
    content             VARCHAR(1000) NOT NULL,
    subject_key         VARCHAR(100),
    official_source_url VARCHAR(1000),
    source_owner        VARCHAR(200),
    valid_from          DATE,
    valid_until         DATE,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'CURATED',
    verified_at         TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_mission_knowledge_category CHECK (category IN ('MEAL', 'LIVING', 'HOBBY')),
    CONSTRAINT ck_mission_knowledge_verification_status
        CHECK (verification_status IN ('CURATED', 'VERIFIED', 'UNVERIFIED', 'REJECTED')),
    CONSTRAINT ck_mission_knowledge_validity
        CHECK (valid_from IS NULL OR valid_until IS NULL OR valid_from <= valid_until)
);

CREATE INDEX idx_mission_knowledge_lookup
    ON mission_knowledge (item_code, active, valid_from, valid_until);
CREATE INDEX idx_mission_knowledge_subject
    ON mission_knowledge (item_code, subject_key);

CREATE TABLE mission_knowledge_retrieval_trace (
    id                    BIGSERIAL PRIMARY KEY,
    job_id                UUID NOT NULL REFERENCES mission_generation_job (id),
    item_code             VARCHAR(40) NOT NULL,
    candidate_count       INTEGER NOT NULL,
    verified_count        INTEGER NOT NULL,
    selected_knowledge_ids VARCHAR(1000) NOT NULL,
    selection_policy      VARCHAR(30) NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mission_knowledge_trace_job UNIQUE (job_id)
);

INSERT INTO mission_knowledge (category, item_code, content, subject_key) VALUES
    ('MEAL', 'DINING_OUT', '저가 쇼핑몰을 이용해 식비 절약하기. 예: 와이즐리', 'LOW_PRICE_SHOPPING'),
    ('MEAL', 'CAFE', '배달앱 선착순 할인쿠폰 활용하기', 'DELIVERY_APP_COUPON'),
    ('MEAL', 'CAFE', '할인된 기프티콘 미리 사두기', 'DISCOUNTED_GIFTICON'),
    ('MEAL', 'CONVENIENCE_STORE', 'GS25 편의점 8월 행사 카카오페이 할인 혜택 챙기기', 'GS25_KAKAOPAY_AUGUST'),
    ('MEAL', 'CONVENIENCE_STORE', '토스 할인 혜택 챙기기', 'TOSS_DISCOUNT'),
    ('MEAL', 'CONVENIENCE_STORE', '편의점 페이백 이벤트 혜택 활용하기', 'CONVENIENCE_PAYBACK'),
    ('MEAL', 'CONVENIENCE_STORE', '편의점 할인 카드 혜택 활용하기', 'CONVENIENCE_CARD_DISCOUNT'),
    ('MEAL', 'CONVENIENCE_STORE', 'GS25 및 CU 앱 구독 서비스 이용하기', 'CONVENIENCE_APP_SUBSCRIPTION'),
    ('MEAL', 'CONVENIENCE_STORE', '한 끼 구독을 통한 할인 혜택 활용하기', 'MEAL_SUBSCRIPTION'),
    ('MEAL', 'CONVENIENCE_STORE', '카페 구독을 통한 할인 혜택 활용하기', 'CAFE_SUBSCRIPTION'),
    ('MEAL', 'CONVENIENCE_STORE', 'GS25, CU의 앱을 통한 할인 이벤트 확인하기', 'CONVENIENCE_APP_EVENT'),
    ('MEAL', 'CONVENIENCE_STORE', '품목별 구독 활용하기', 'ITEM_SUBSCRIPTION'),
    ('LIVING', 'COSMETICS', '올리브영 리워딩스 참여하기', 'OLIVEYOUNG_REWARDINGS'),
    ('LIVING', 'COSMETICS', '아모레퍼시픽 다 쓴 공병 반납을 통한 뷰티포인트 적립하기', 'AMOREPACIFIC_EMPTY_CONTAINER'),
    ('LIVING', 'COSMETICS', 'LG생활건강 공병 반환으로 포인트 적립 또는 할인 쿠폰 받기', 'LGHNH_EMPTY_CONTAINER'),
    ('LIVING', 'COSMETICS', '러쉬 블랙 팟 모으기', 'LUSH_BLACK_POT'),
    ('LIVING', 'HOUSEHOLD_GOODS', '리필형과 대용량 소분 활용하기', 'REFILL_AND_BULK'),
    ('LIVING', 'HOUSEHOLD_GOODS', '온라인 특가 활용하기', 'ONLINE_SPECIAL_PRICE'),
    ('LIVING', 'HOUSEHOLD_GOODS', '정기배송 서비스 활용하기', 'REGULAR_DELIVERY'),
    ('LIVING', 'HOUSEHOLD_GOODS', '지역사랑상품권과 온누리상품권 이용하기', 'LOCAL_GIFT_CERTIFICATE'),
    ('LIVING', 'HOUSEHOLD_GOODS', '마트 마감 상품 공략하기', 'MART_CLOSING_DISCOUNT'),
    ('LIVING', 'HOUSEHOLD_GOODS', '이월 상품 세일 공략하기', 'CARRYOVER_PRODUCT_SALE'),
    ('LIVING', 'BEAUTY', '헤어 모델로 참여해 미용비 절약하기', 'HAIR_MODEL'),
    ('LIVING', 'BEAUTY', '블로그 체험단 활용하기', 'BLOG_TRIAL_GROUP'),
    ('HOBBY', 'CLASS', '주민자치센터 문화 교양 강좌 활용하기', 'COMMUNITY_CENTER_CLASS'),
    ('HOBBY', 'PERFORMANCE_TICKET', '여가 생활 할인 카드 이용하기', 'LEISURE_DISCOUNT_CARD'),
    ('HOBBY', 'PERFORMANCE_TICKET', '문화체육관광부 비수도권 공연 관람료 할인권 활용하기', 'MCST_NON_CAPITAL_TICKET');

UPDATE mission_draft_template
SET active = FALSE
WHERE target_code IN (
    'SELF_DEVELOPMENT', 'HOBBY_GOODS', 'DIGITAL_CONTENT',
    'CLUB_GATHERING', 'EQUIPMENT_RENTAL', 'SPACE_USE'
);

UPDATE mission
SET deleted_at = COALESCE(deleted_at, CURRENT_TIMESTAMP)
WHERE item_code IN (
    'SELF_DEVELOPMENT', 'HOBBY_GOODS', 'DIGITAL_CONTENT',
    'CLUB_GATHERING', 'EQUIPMENT_RENTAL', 'SPACE_USE'
);
