CREATE TABLE users(
    id         SERIAL PRIMARY KEY,
    username   VARCHAR(255)  NOT NULL UNIQUE,
    email      VARCHAR(255)  NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);