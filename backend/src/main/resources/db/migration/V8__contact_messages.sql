CREATE TABLE contact_messages (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    mobile VARCHAR(30),
    reason VARCHAR(50) NOT NULL,
    note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL
);
