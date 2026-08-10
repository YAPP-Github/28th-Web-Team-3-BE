-- 지역 기반 혜택 필터용. 온통청년 지역코드(zipCd)의 시도를 거주지역(ResidentialArea)으로 정규화해
-- `,SEOUL,BUSAN,`처럼 구분자 포함 문자열로 적재 시점에 저장한다. 전국 정책은 16개 지역을 모두 포함한다.
ALTER TABLE youth_policy ADD COLUMN region_codes VARCHAR(200);
