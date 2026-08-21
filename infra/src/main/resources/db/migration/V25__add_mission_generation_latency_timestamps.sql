ALTER TABLE mission_generation_job ADD COLUMN worker_started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE mission_generation_job ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;
