-- Meta's WhatsApp message id (wamid) is Base64 that embeds the recipient's
-- phone number. Keep only its SHA-256 (lowercase hex), as the application now
-- does for every new send; the webhook fingerprints incoming ids the same way.
UPDATE conversation_deliveries
SET provider_message_id = encode(sha256(convert_to(provider_message_id, 'UTF8')), 'hex')
WHERE provider = 'meta'
  AND provider_message_id IS NOT NULL
  AND provider_message_id !~ '^[0-9a-f]{64}$';
