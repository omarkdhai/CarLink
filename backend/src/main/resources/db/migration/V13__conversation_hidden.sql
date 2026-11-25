-- An alert whose content the phone operator refused (TunisieSMS report
-- UNAUTHORIZED, "contenu non autorisé") is hidden from the owner: spam the
-- in-app classifier missed must not reach the owner's dashboard either. The row
-- stays for admin moderation and the normal retention.
ALTER TABLE conversations ADD COLUMN hidden_at TIMESTAMP;

-- Alerts the operator already refused for their content are hidden too.
UPDATE conversations c SET hidden_at = CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM conversation_deliveries d
              WHERE d.conversation_id = c.id AND d.status = 'FAILED' AND d.error = 'UNAUTHORIZED');
