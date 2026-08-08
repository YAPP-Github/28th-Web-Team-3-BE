-- 혜택 필터용 4분류(금융/주거/복지/교육). 온통청년 중분류를 정규화해 적재 시점에 저장한다.

ALTER TABLE youth_policy ADD COLUMN category VARCHAR(20);

CREATE INDEX idx_youth_policy_category ON youth_policy (category);
