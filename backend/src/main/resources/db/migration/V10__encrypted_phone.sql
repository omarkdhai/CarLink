-- Security plan S-17: users.phone now holds AES-256-GCM ciphertext
-- ("v1:" + Base64(IV + ciphertext + tag), about 60 characters for an E.164
-- number). Existing plaintext rows are encrypted at startup by
-- PhoneEncryptionBackfill, which has the key.
ALTER TABLE users ALTER COLUMN phone TYPE VARCHAR(255);
