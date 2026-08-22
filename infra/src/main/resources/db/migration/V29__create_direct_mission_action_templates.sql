CREATE TABLE mission_action_template (
    id BIGSERIAL PRIMARY KEY,
    item_code VARCHAR(40) NOT NULL,
    title_template VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_mission_action_template_item CHECK (item_code IN (
        'DELIVERY_FOOD', 'DINING_OUT', 'CAFE', 'CONVENIENCE_STORE',
        'CLOTHING', 'COSMETICS', 'HOUSEHOLD_GOODS', 'BEAUTY',
        'CLASS', 'PERFORMANCE_TICKET'
    )),
    CONSTRAINT ck_mission_action_template_title CHECK (char_length(title_template) <= 120)
);

CREATE INDEX idx_mission_action_template_lookup
    ON mission_action_template (item_code, active, id);

INSERT INTO mission_action_template (item_code, title_template) VALUES
    ('DELIVERY_FOOD', '서울 땡겨요나 경기 배달특급 앱을 {count}회 활용하기'),
    ('DELIVERY_FOOD', '배달 앱 대신 유튜브 집밥 레시피를 {count}회 도전하기'),
    ('DELIVERY_FOOD', '밀프랩으로 평일 배달음식 지출 {count}번 줄이기'),
    ('DELIVERY_FOOD', '배달앱 랜덤 할인 쿠폰으로 {count}회 이용하기'),
    ('DELIVERY_FOOD', '배달 대신 냉동 간편식을 {count}번 구매해 먹기'),

    ('DINING_OUT', '프랜차이즈 이용 시 제휴 신용카드로 {count}회 할인받기'),
    ('DINING_OUT', '마트 마감 세일 이용해서 {count}번 장보기'),
    ('DINING_OUT', '카드 앱에서 식당 전용 맞춤 쿠폰을 찾아 {count}번 적용하기'),
    ('DINING_OUT', '주변 구내식당을 {count}번 이용해보기'),
    ('DINING_OUT', '거지맵으로 {count}번 저렴한 식당 찾아보기'),

    ('CAFE', '카페에서 개인 텀블러 이용해서 할인받기 {count}번 도전'),
    ('CAFE', '스타벅스 리워드 앱으로 음료 및 푸드 쿠폰 {count}번 사용하기'),
    ('CAFE', '카카오톡 선물하기에서 기프티콘을 할인가 {count}회 챙기기'),
    ('CAFE', '통신사 멤버십 등급별 혜택을 이용해 카페 {count}번 할인받기'),
    ('CAFE', '편의점 원두커피로 저렴하게 {count}번 대체하기'),
    ('CAFE', '편의점의 카페 정기구독 서비스로 {count}번 할인받기'),
    ('CAFE', '커피 브랜드 구분 없이 10% 적립되는 체크카드로 {count}회 카페 비용 환급받기'),

    ('CONVENIENCE_STORE', 'GS25나 CU 앱에서 구독 서비스에 가입해 자주 먹는 도시락 {count}번 사먹기'),
    ('CONVENIENCE_STORE', 'CU에서 SKT, GS25에서 KT 할인 혜택 {count}회 이용하기'),
    ('CONVENIENCE_STORE', 'GS25, CU, 세븐일레븐의 1+1 행사 생필품을 {count}번 구매하기'),
    ('CONVENIENCE_STORE', '포켓CU나 우리동네GS 앱 출석체크 이벤트에 {count}번 참여해 포인트 얻기'),
    ('CONVENIENCE_STORE', '네이버플러스 멤버십과 GS25 POP 멤버십을 연동 할인 {count}번 받기'),
    ('CONVENIENCE_STORE', 'GS25, CU, 세븐일레븐 결제 10% 청구할인 카드 {count}번 이용하기'),
    ('CONVENIENCE_STORE', '포켓CU나 우리동네GS 앱의 나눔 및 키핑 기능으로 1+1 상품 {count}회 보관하기'),
    ('CONVENIENCE_STORE', '편의점 소액결제 시에도 멤버십 포인트 {count}번 적립하기'),

    ('CLOTHING', '당근마켓으로 안 입는 옷을 {count}번 팔아 돈 벌기'),
    ('CLOTHING', '차란 등 위탁판매 전문 플랫폼을 이용해 고가 브랜드 의류를 {count}회 처분하기'),
    ('CLOTHING', '중고 의류로 {count}회 구매해 사용하기'),
    ('CLOTHING', '무신사 앱의 등급 쿠폰 {count}회 이용해 최저가로 구매하기'),
    ('CLOTHING', '번개장터에서 안입는 옷  {count}회 처분하기'),
    ('CLOTHING', '리클로 등 의류 수거 서비스로 철 지난 옷 {count}회 정리하기'),
    ('CLOTHING', '더페어 무료 수거 서비스 {count}회 이용해서 비용 아끼기'),

    ('COSMETICS', '다이소 기초 화장품으로 브랜드 제품 {count}번 대체하기'),
    ('COSMETICS', '올리브영 뷰티사이클에 공병 반납하고 포인트 {count}번 적립하기'),
    ('COSMETICS', '아모레퍼시픽에 공병 반납하고 브랜드 포인트 {count}번 적립하기'),
    ('COSMETICS', '러쉬에 공병 반납하고 브랜드 포인트 {count}번 적립하기'),
    ('COSMETICS', '화장품 체험단 앱으로 화장품 본품 {count}번 무료로 받기'),
    ('COSMETICS', '올리브영 영수증 인증샷으로 페이백 앱에서 {count}번 환급받기'),
    ('COSMETICS', '다이소에서 PDRN·리들샷 등 고기능성 대체 화장품 {count}번 구매하기'),
    ('COSMETICS', '가격 추적 앱으로 뷰티 제품 최저가 알림 {count}번 설정하기'),
    ('COSMETICS', '대용량 본품 대신 샘플·미니 사이즈 {count}번 먼저 구매하기'),
    ('COSMETICS', '정기구독 박스 해지하고 필요한 스킨케어만 단품으로 {count}번 구매하기'),

    ('HOUSEHOLD_GOODS', '폴센트 서비스에서 최저가 확인하고 생활용품 {count}회 구매하기'),
    ('HOUSEHOLD_GOODS', '다이소 매장에서 수납함, 청소용품 구매 {count}회 이용하기'),
    ('HOUSEHOLD_GOODS', ' 다나와 가격비교 서비스로 최저가 구매 {count}회 이용하기'),
    ('HOUSEHOLD_GOODS', 'GS25나 CU의 반값택배 서비스 {count}회 이용하기'),

    ('BEAUTY', '뷰티 체험단 플랫폼에서 메이크업 무료 시술 체험 {count}회 이용하기'),
    ('BEAUTY', '다이소 셀프 미용 도구 {count}회 이용하기'),
    ('BEAUTY', '블로그 체험단으로 미용실 {count}회 이용하기'),
    ('BEAUTY', '네이버 예약에서 오프피크 타임 특가 {count}회 이용하기'),

    ('CLASS', '주민센터나 자치회관의 원데이 문화강좌를 {count}회 활용하기'),
    ('CLASS', '지자체 청년 스터디 소모임 지원사업에 {count}회 이용하기'),
    ('CLASS', '공공 스포츠센터에서 운영하는 헬스 및 수영 강좌 {count}회 이용하기'),
    ('CLASS', 'K-MOOC에서 온라인 클래스로 {count}회 이용하기'),

    ('PERFORMANCE_TICKET', '청년문화예술패스로 공연 전시 관람 지원받기 {count}회 이용하기'),
    ('PERFORMANCE_TICKET', '매달 마지막 수요일 문화가 있는 날 {count}회 활용하기'),
    ('PERFORMANCE_TICKET', '문체부에서 지역 전용 공연 관람 {count}회 이용하기'),
    ('PERFORMANCE_TICKET', '지자체 공식 문화패스 {count}회 이용하기'),
    ('PERFORMANCE_TICKET', '국립 예술단체의 청년 전용 관람 할인 {count}회 챙기기'),
    ('PERFORMANCE_TICKET', '티켓링크에서 할인 특가 {count}회 챙기기');
