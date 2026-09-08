-- Phase 6: the contact relay records delivery outcome, so a conversation may
-- sit in PENDING while the channel send is in flight, before being flipped to
-- SENT (delivered) or FAILED (relay error). Widen the CHECK to admit it.
ALTER TABLE conversations DROP CONSTRAINT chk_conv_status;
ALTER TABLE conversations ADD CONSTRAINT chk_conv_status
    CHECK (status IN ('SENT', 'FAILED', 'EXPIRED', 'PENDING'));
