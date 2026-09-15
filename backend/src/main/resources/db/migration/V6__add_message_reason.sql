-- Add reason column to messages table for contact alerts.
-- Matches the existing `content TEXT` convention so `ddl-auto: validate` passes.
ALTER TABLE messages ADD COLUMN reason TEXT NOT NULL DEFAULT 'BLOCKING';

-- Update existing messages with a safe default (they're all from the pre-reason era)
UPDATE messages SET reason = 'BLOCKING' WHERE reason IS NULL;

CREATE INDEX idx_messages_reason ON messages (reason);