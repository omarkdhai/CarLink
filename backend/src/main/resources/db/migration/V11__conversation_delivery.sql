-- SMS delivery confirmation. A provider "accepting" an SMS does not mean the
-- phone got it: the operator can still reject it (measured with TunisieSMS:
-- over-long messages were REJECTED a second later while the alert showed SENT).
--   SENT      = accepted by the provider, delivery not confirmed yet
--   DELIVERED = the delivery report confirms the phone received it
--   FAILED    = refused by the provider, or reported undeliverable
ALTER TABLE conversations DROP CONSTRAINT chk_conv_status;
ALTER TABLE conversations ADD CONSTRAINT chk_conv_status
    CHECK (status IN ('PENDING', 'SENT', 'DELIVERED', 'FAILED', 'EXPIRED'));

-- Provider id used to fetch the delivery report, and a short failure code
-- (provider status such as REJECTED or BLACK_LIST, or an API/transport code).
-- Neither holds the phone number or the message.
ALTER TABLE conversations ADD COLUMN provider_message_id VARCHAR(64);
ALTER TABLE conversations ADD COLUMN delivery_error VARCHAR(40);

-- The report poller only looks at SENT alerts that have a provider id.
CREATE INDEX idx_conversations_awaiting_report ON conversations (created_at)
    WHERE status = 'SENT' AND provider_message_id IS NOT NULL;
