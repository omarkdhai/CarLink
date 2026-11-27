-- The buyer picks the sticker design for the whole order: DARK or LIGHT.
-- Existing orders were all printed with the dark design.
ALTER TABLE orders
    ADD COLUMN sticker_style VARCHAR(10) NOT NULL DEFAULT 'DARK',
    ADD CONSTRAINT chk_orders_sticker_style CHECK (sticker_style IN ('DARK', 'LIGHT'));
