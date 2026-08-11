-- 혜택 목록 조회수(온통청년 inqCnt) 기반 정렬용. 적재 시점 값으로 저장한다.
ALTER TABLE youth_policy ADD COLUMN view_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_youth_policy_view_count ON youth_policy (view_count DESC);
