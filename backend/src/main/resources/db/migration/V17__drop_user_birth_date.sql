-- The sticker activation form no longer asks for a date of birth, and nothing
-- reads it. Drop the column so no personal data is kept without a purpose.
ALTER TABLE users DROP COLUMN IF EXISTS birth_date;
