CREATE TABLE answer_cache (
    id UUID PRIMARY KEY,
    question_text TEXT NOT NULL,
    question_embedding VECTOR(1536) NOT NULL,
    answer_text TEXT NOT NULL,
    scope VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_answer_cache_scope ON answer_cache(scope);
