CREATE TABLE catalog_photo (
    id UUID PRIMARY KEY,
    media_type VARCHAR(20) NOT NULL CHECK (media_type IN ('image/jpeg', 'image/png')),
    content BYTEA NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 2097152)
);
