-- 탈퇴 시 mission_generation_job 삭제가 mission_knowledge_retrieval_trace의 FK에 막히던 문제 해결.
-- job 삭제 시 연결된 retrieval trace가 함께 삭제되도록 job_id FK에 ON DELETE CASCADE를 부여한다.
-- 인라인 생성된 FK라 DB별 자동 이름이 달라(Postgres: ..._job_id_fkey), IF EXISTS로 안전하게 교체한다.
ALTER TABLE mission_knowledge_retrieval_trace
    DROP CONSTRAINT IF EXISTS mission_knowledge_retrieval_trace_job_id_fkey;

ALTER TABLE mission_knowledge_retrieval_trace
    ADD CONSTRAINT mission_knowledge_retrieval_trace_job_id_fkey
        FOREIGN KEY (job_id) REFERENCES mission_generation_job (id) ON DELETE CASCADE;
