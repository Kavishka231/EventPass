ALTER TABLE refresh_tokens
    ADD COLUMN family_id UUID,
    ADD COLUMN parent_token_id UUID REFERENCES refresh_tokens(id),
    ADD COLUMN created_at TIMESTAMPTZ,
    ADD COLUMN last_used_at TIMESTAMPTZ,
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD COLUMN revocation_reason VARCHAR(50),
    ADD COLUMN device_info VARCHAR(255);

UPDATE refresh_tokens
SET family_id = id,
    created_at = expires_at - INTERVAL '30 days',
    revoked_at = CASE WHEN revoked THEN CURRENT_TIMESTAMP ELSE NULL END,
    revocation_reason = CASE WHEN revoked THEN 'LEGACY_REVOKED' ELSE NULL END,
    device_info = 'legacy-session';

ALTER TABLE refresh_tokens
    ALTER COLUMN family_id SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN device_info SET NOT NULL;

CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_active_family
    ON refresh_tokens(family_id, expires_at)
    WHERE revoked = FALSE;
