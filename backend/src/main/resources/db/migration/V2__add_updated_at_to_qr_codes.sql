-- ============================================
-- CarLink — V2: align qr_codes with BaseEntity
-- ============================================
-- The initial schema created qr_codes without updated_at while every other
-- entity maps the BaseEntity audit columns. Add it to match JPA mapping.
ALTER TABLE qr_codes ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;