-- ============================================
-- CarLink schema — sticker activation code
-- ============================================
-- A short, human-enterable code printed under the QR so a buyer can register
-- a sticker without scanning. Like the QR token, only the SHA-256 hash is
-- stored; the raw code is shown exactly once at order creation.

ALTER TABLE stickers
    ADD COLUMN activation_code_hash VARCHAR(128) UNIQUE;

COMMENT ON COLUMN stickers.activation_code_hash IS
    'SHA-256 of the short activation code printed under the QR; NULL for legacy rows';
