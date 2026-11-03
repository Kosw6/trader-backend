CREATE TABLE ai_transfer_consent (
 id BIGSERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 version VARCHAR(64) NOT NULL,
 notice_sha256 VARCHAR(64) NOT NULL CHECK(notice_sha256 ~ '^[a-f0-9]{64}$'),
 accepted BOOLEAN NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX ai_transfer_consent_user_latest ON ai_transfer_consent(user_id,id DESC);
