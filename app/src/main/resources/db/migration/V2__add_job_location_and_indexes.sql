ALTER TABLE job ADD COLUMN location TEXT;
CREATE UNIQUE INDEX idx_job_dedup_key ON job(dedup_key);
CREATE INDEX idx_job_embedding_hnsw ON job USING hnsw (embedding_vector vector_cosine_ops);
