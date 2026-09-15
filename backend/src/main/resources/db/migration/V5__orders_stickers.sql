-- ============================================
-- CarLink schema — orders, stickers, user birth_date
-- ============================================

-- ---------- ORDERS (guest COD checkout) ----------
CREATE TABLE orders (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference         VARCHAR(20) NOT NULL,
    customer_name     VARCHAR(100) NOT NULL,
    mobile            VARCHAR(20) NOT NULL,
    email             VARCHAR(255) NOT NULL,
    governorate       VARCHAR(50) NOT NULL,
    delivery_address  VARCHAR(255) NOT NULL,
    delivery_notes    TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'PLACED',
    total_amount      NUMERIC(10, 2) NOT NULL,
    currency          VARCHAR(3)  NOT NULL DEFAULT 'TND',
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_orders_reference UNIQUE (reference),
    CONSTRAINT chk_orders_status CHECK (status IN ('PLACED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT chk_orders_amount CHECK (total_amount >= 0)
);

CREATE INDEX idx_orders_reference ON orders (reference);
CREATE INDEX idx_orders_status ON orders (status);

-- ---------- ORDER ITEMS ----------
CREATE TABLE order_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id          UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    sticker_package   VARCHAR(20) NOT NULL,
    quantity          INTEGER NOT NULL,
    unit_price        NUMERIC(10, 2) NOT NULL,
    stickers_per_pack INTEGER NOT NULL,
    subtotal          NUMERIC(10, 2) NOT NULL,
    CONSTRAINT chk_order_items_package CHECK (sticker_package IN ('SINGLE', 'DOUBLE', 'BUSINESS')),
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_items_spack CHECK (stickers_per_pack > 0)
);

CREATE INDEX idx_order_items_order ON order_items (order_id);

-- ---------- STICKERS (physical QR sticker lifecycle) ----------
CREATE TABLE stickers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- A sticker always comes from an order; legacy backfilled rows have none.
    order_id        UUID REFERENCES orders (id) ON DELETE SET NULL,
    -- UNBOUND/DEACTIVATED stickers have no owner or vehicle.
    owner_id        UUID REFERENCES users (id) ON DELETE SET NULL,
    vehicle_id      UUID REFERENCES vehicles (id) ON DELETE SET NULL,
    -- Hash of the public token (SHA-256, hex). The raw token is returned to the
    -- buyer once, at order creation, and is never persisted.
    token_hash      VARCHAR(128) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'UNBOUND',
    bound_at        TIMESTAMP,
    deactivated_at  TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_stickers_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_stickers_status CHECK (status IN ('UNBOUND', 'BOUND', 'DEACTIVATED'))
);

CREATE INDEX idx_stickers_status ON stickers (status);
CREATE INDEX idx_stickers_owner ON stickers (owner_id);
CREATE INDEX idx_stickers_vehicle ON stickers (vehicle_id);

-- ---------- USERS: birth date (collected at sticker activation) ----------
ALTER TABLE users ADD COLUMN birth_date DATE;

-- ---------- BACKFILL: migrate already-printed active QR codes ----------
-- Every ACTIVE qr_codes row becomes a BOUND sticker so that legacy printed
-- stickers keep resolving after the public lookup moves to the stickers table.
INSERT INTO stickers (id, order_id, owner_id, vehicle_id, token_hash, status,
                      bound_at, created_at, updated_at)
SELECT gen_random_uuid(),
       NULL,
       v.owner_id,
       q.vehicle_id,
       q.token_hash,
       'BOUND',
       COALESCE(q.activated_at, CURRENT_TIMESTAMP),
       q.created_at,
       CURRENT_TIMESTAMP
FROM qr_codes q
JOIN vehicles v ON v.id = q.vehicle_id
WHERE q.is_active = TRUE;