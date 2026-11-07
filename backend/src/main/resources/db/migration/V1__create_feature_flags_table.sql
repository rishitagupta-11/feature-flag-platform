CREATE TABLE feature_flags (
                               id UUID PRIMARY KEY,
                               flag_key VARCHAR(100) NOT NULL UNIQUE,
                               name VARCHAR(150) NOT NULL,
                               description VARCHAR(500),
                               enabled BOOLEAN NOT NULL DEFAULT FALSE,
                               rollout_percentage INTEGER NOT NULL DEFAULT 100,
                               created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                               updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                               version BIGINT NOT NULL DEFAULT 0,

                               CONSTRAINT chk_rollout_percentage
                                   CHECK (rollout_percentage BETWEEN 0 AND 100)
);