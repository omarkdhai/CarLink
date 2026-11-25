-- One row per channel an alert was sent on (SMS, WhatsApp). The conversation's
-- status becomes the aggregate: DELIVERED if any channel delivered, SENT while
-- one is still waiting, FAILED when every channel failed.
CREATE TABLE conversation_deliveries (
    id                  UUID        PRIMARY KEY,
    conversation_id     UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    channel             VARCHAR(20) NOT NULL,
    provider            VARCHAR(20) NOT NULL,
    provider_message_id VARCHAR(128),
    status              VARCHAR(20) NOT NULL,
    error               VARCHAR(40),
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_delivery_channel CHECK (channel IN ('SMS', 'WHATSAPP')),
    CONSTRAINT chk_delivery_status CHECK (status IN ('SENT', 'DELIVERED', 'READ', 'FAILED'))
);

CREATE INDEX idx_deliveries_conversation ON conversation_deliveries (conversation_id);
CREATE UNIQUE INDEX uq_deliveries_provider_message
    ON conversation_deliveries (provider, provider_message_id)
    WHERE provider_message_id IS NOT NULL;
CREATE INDEX idx_deliveries_awaiting_report ON conversation_deliveries (created_at)
    WHERE status = 'SENT' AND provider_message_id IS NOT NULL;

-- Existing alerts were SMS-only (TunisieSMS): keep their outcome per channel.
INSERT INTO conversation_deliveries
    (id, conversation_id, channel, provider, provider_message_id, status, error, created_at, updated_at)
SELECT gen_random_uuid(), c.id, 'SMS',
       CASE WHEN c.provider_message_id IS NOT NULL THEN 'tunisiesms' ELSE 'none' END,
       c.provider_message_id,
       CASE c.status WHEN 'DELIVERED' THEN 'DELIVERED' WHEN 'FAILED' THEN 'FAILED' ELSE 'SENT' END,
       c.delivery_error, c.created_at, c.created_at
FROM conversations c
WHERE c.provider_message_id IS NOT NULL OR c.delivery_error IS NOT NULL;

DROP INDEX idx_conversations_awaiting_report;
ALTER TABLE conversations DROP COLUMN provider_message_id;
ALTER TABLE conversations DROP COLUMN delivery_error;

-- WhatsApp alerts are on for every owner with a phone; the profile can turn them off.
ALTER TABLE users ADD COLUMN whatsapp_alerts BOOLEAN NOT NULL DEFAULT TRUE;
