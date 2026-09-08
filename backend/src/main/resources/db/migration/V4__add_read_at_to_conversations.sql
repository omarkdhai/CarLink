-- Phase 7: read/unread tracking for owner dashboard.
ALTER TABLE conversations ADD COLUMN read_at TIMESTAMP;

-- Accelerate owner-scoped dashboard queries (list all, filter unread).
CREATE INDEX idx_conversations_vehicle_read
    ON conversations (vehicle_id, read_at);
