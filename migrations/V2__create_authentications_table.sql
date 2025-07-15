CREATE TABLE authentications
(
    token      VARCHAR(512) PRIMARY KEY,
    user_id    SERIAL NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);