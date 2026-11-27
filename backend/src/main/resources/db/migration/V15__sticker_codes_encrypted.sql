-- The raw QR token and printed activation code of each sticker are no longer
-- shown to the buyer at checkout; the admin prints them from the order page.
-- They are kept AES-256-GCM encrypted (same key and format as owner phone
-- numbers); the SHA-256 columns stay the lookup keys. Stickers minted before
-- this migration only have hashes, so their codes stay unavailable (NULL).
ALTER TABLE stickers
    ADD COLUMN token_enc           TEXT,
    ADD COLUMN activation_code_enc TEXT;
