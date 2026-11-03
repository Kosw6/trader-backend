-- Run explicitly in the application's schema. No existing user is auto-consented.
CREATE TABLE policy_consent (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    age14_confirmed BOOLEAN NOT NULL CHECK(age14_confirmed),
    version VARCHAR(64) NOT NULL,
    terms_sha256 VARCHAR(64) NOT NULL CHECK(terms_sha256 ~ '^[a-f0-9]{64}$'),
    privacy_sha256 VARCHAR(64) NOT NULL CHECK(privacy_sha256 ~ '^[a-f0-9]{64}$'),
    channel VARCHAR(24) NOT NULL CHECK(channel IN ('LOCAL','ACCOUNT','OAUTH_GOOGLE','OAUTH_KAKAO','OAUTH_GITHUB')),
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(user_id,version)
);
COMMENT ON TABLE policy_consent IS 'Explicit terms and personal data collection consent; no marketing or AI transfer consent implied. Deleted with account.';
