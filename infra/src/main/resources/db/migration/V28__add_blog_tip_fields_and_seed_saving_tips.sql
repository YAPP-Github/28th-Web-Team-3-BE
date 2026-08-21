-- 절약 팁: blog_tip에 선택항목(subcategory)·원문URL(source_url) 컬럼 추가 후 기획 제공 데이터 시드.
ALTER TABLE blog_tip ADD COLUMN subcategory VARCHAR(50);
ALTER TABLE blog_tip ADD COLUMN source_url  VARCHAR(1000);

INSERT INTO blog_tip (title, description, category, subcategory, source_url, created_at, updated_at) VALUES
('집밥 레시피 활용팁', '배달 메뉴 대신 집에서 직접 만드는 레시피 찾아보기', '식비', '배달음식', 'https://www.youtube.com/watch?v=nZw2A76aZaw', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('밀프렙 식단관리팁', '밀프랩으로 식단 미리 만들어 두기', '식비', '외식', 'https://www.youtube.com/watch?v=ke5muhJxFWo', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('저렴한 장보기 스킬', '저렴하게 장보기 스킬로 식비 아끼기', '식비', '외식', 'https://www.youtube.com/watch?v=H0vlG-mHk7g', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('커피 기프티콘 할인구매팁', '카페별 할인된 기프티콘 미리 챙기기', '식비', '카페', 'https://blog.naver.com/namakemori/224231608416', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('GS25 편의점 8월 행사 및 혜택', 'GS25 편의점 8월 행사 카카오페이 토스 할인 혜택 챙기기', '식비', '편의점', 'https://blog.naver.com/damoahousing/224366685453', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('8월 네이버페이 편의점 페이백 이벤트 혜택 총정리', '편의점 페이백 이벤트 혜택 챙기기', '식비', '편의점', 'https://blog.naver.com/y0ng00322/224372833847', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('CU·GS25·세븐일레븐 할인 카드 총정리 (2026)', '편의점 할인 카드 혜택으로 절약하기', '식비', '편의점', 'https://blog.naver.com/playplanet-/224367726782', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('GS25 및 CU 편의점 통신사 멤버십 중복 및 마감 할인 앱 활용법', 'GS25 및 CU 편의점 앱 구독 서비스 이용하기

GS25(우리 동네 GS 클럽)

한 끼 구독(월 3,990원) : 도시락, 김밥, 햄버거, 빵, 컵라면 등 간편식 20% 할인(1일 5회, 한 달 최대 15회 이용 한도)

카페 구독(월 2,500원) : 원두커피 (CAFE25) 전 품목 최대 25% 할인(1일 최대 10잔)

CU(포켓 CU 구독)

품목별 구독(월 1~4000원대) : 도시락, GET 커피, 삼각김밥, 컵라면 등 자주 먹는 카테고리를 지정 후 20~30% 할인(매일 1~5회 한도)', '식비', '편의점', 'https://blog.naver.com/greenbook23/224367450635', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('당근마켓 중고활용팁', '당근마켓서 감가 큰 전자기기 중고구매, 안쓰는 옷은 판매해 현금화', '생활', '의류', 'https://onsehub2026.blogspot.com/2026/03/14.html', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('가격대별 중고처분팁', '고가브랜드는 위탁판매(차란), 중가는 당근마켓, 저가는 리클로 일괄처분', '생활', '의류', 'https://livedailycare.com/minimalist-closet-20260503/', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('무신사 쿠폰중복 활용팁', '무신사 등급쿠폰, 시즌세일, 아울렛·중고(유즈드) 함께 활용', '생활', '의류', 'https://echeveau.net/musinsa-complete-guide-hub/', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('다이소 화장품 꿀템 모음 2026', '다이소 기초템 활용하기 토너 및 앰플', '생활', '화장품', 'https://www.youtube.com/watch?v=kLn8Kg9Fx0g', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('올리브영 제품 페이백 받는 리워딩스 활용팁', '리워딩스로 올리브영 리뷰 체험단 참여하기', '생활', '화장품', 'https://blog.naver.com/by_lami_/224323780483', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('화장품 공병 수거 및 재활용 포인트 적립 꿀팁 3가지', '아모레퍼시픽 (이니스프리, 에뛰드, 아리따움, 헤라 등): 다 쓴 공병을 매장으로 가져가면 ''뷰티포인트''로 적립
LG생활건강 (더페이스샵, 빌리프 등): 특정 브랜드 매장에서 공병을 가져가면 포인트를 적립해 주거나, 할인 쿠폰을 발행
러쉬(LUSH) 블랙 팟 5개 모으면 ''프레쉬 마스크'' 증정', '생활', '화장품', 'https://blog.naver.com/whirlpoolbath/224204100148', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('다이소 화장품 추천 2026 품절템', '다이소 화장품 이용하기 PDRN, 리들샷, 화장 전 베이스', '생활', '화장품', 'https://byuppo.com/2026-daiso-beauty-top5/', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('다이소 손해품목 피하기', '다이소 ''사면 손해'' 품목 피하기, 택배비 못채우면 정기배송·매장픽업', '생활', '생활용품', 'https://dasaja.co.kr/saja_guide/15', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('다이소 수납정리 꿀팁', '다이소템 활용 수납정리 꿀팁으로 별도 수납용품 구매 없이 정돈', '생활', '생활용품', 'https://www.youtube.com/watch?v=QAm3iCpih2s', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('폴센트 최저가 추적팁', '폴센트 앱으로 쿠팡 가격변동 추적, 최저가 타이밍에 구매해 절약', '생활', '생활용품', 'https://fallcent.com/', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('거지대학생의 꾸밈비 절약 꿀팁 대공개!! (속눈썹펌 모델/헤어 모델/네일 모델/모델나라/미몽/블로그 체험단 후기)', '속눈썹펌 모델/헤어 모델/네일 모델/모델나라/미몽/블로그 체험단 활용하기', '생활', '미용', 'https://blog.naver.com/godjoo27/224354310092', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('헤어모델 협찬 할인팁', '헤어모델 협찬 활용하기', '생활', '미용', 'https://www.hankyung.com/article/2025012230647', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('셀프미용 절약팁', '앞머리 셀프컷·다이소 염색도구로 셀프미용 도전하기', '생활', '미용', 'https://v.daum.net/v/20260914070111595', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('체험단 무료뷰티 혜택', '레뷰 등 체험단 플랫폼서 뷰티·미용 시술 무료체험 신청하기', '생활', '미용', 'https://www.revu.net/', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('주민센터 숨은 복지 혜택 활용 꿀팁', '주민자치센터 문화•교양 강좌 활용하기', '취미', '수업&클래스', 'https://blog.naver.com/coolmasinser/224353887451', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('여가생활 할인카드 추천 순위 비교 (feat. 최대 할인 받는 법)', '영화•전시회•공연 자주 다니는 사람을 위한 여가 생활 할인 카드 이용하기', '취미', '공연&전시&티켓', 'https://blog.naver.com/2hmii/224372836055', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('9월부터 공연 관람료 1만 원 할인 받는팁', '문체부 비수도권 공연 관람료 할인권 활용하기', '취미', '공연&전시&티켓', 'https://blog.naver.com/mcstkorea/224469832643', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('청년문화예술패스 혜택', '청년문화예술패스로 만19세 청년 연 15만원 공연·전시 관람 지원', '취미', '공연&전시&티켓', 'https://youthculturepass.or.kr/', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('예술의전당 당일할인팁', '예술의전당 당일예매시 특정 연령대 당일할인티켓 제공(환불불가)', '취미', '공연&전시&티켓', 'https://www.sac.or.kr/site/main/show/show_list_ticket', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('문화가있는날 중첩할인팁', '둘째·마지막 수요일 문화가있는날(1만원)+정부 6천원 할인권 중첩시 4천원 관람 활용하기', '취미', '공연&전시&티켓', 'https://dasaja.co.kr/saja_guide/197', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('영화 조조+멤버십 할인팁', '영화 조조할인+통신사멤버십(SKT주1회, KT·LGU+월1회 3천원) 중복 활용', '취미', '공연&전시&티켓', 'https://www.ajd.co.kr/contents/basic-tip/detail/%EC%98%81%ED%99%94_%EC%A1%B0%EC%A1%B0%ED%95%A0%EC%9D%B8_%EC%8B%9C%EA%B0%84_%EB%AF%B8%EB%A6%AC_%EC%B0%BE%EC%95%84%EB%B3%B4%EA%B8%B0%EB%A9%94%EA%B0%80%EB%B0%95%EC%8A%A4,_cgv,_%EB%A1%AF%EB%8D%B0%EC%8B%9C%EB%84%A4%EB%A7%88-70007', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
