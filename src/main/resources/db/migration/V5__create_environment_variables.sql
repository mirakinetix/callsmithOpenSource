CREATE TABLE environment_variables (
    id TEXT PRIMARY KEY,
    environment_id TEXT NOT NULL,
    name TEXT NOT NULL,
    value TEXT NOT NULL,
    is_secret INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,

    FOREIGN KEY (environment_id)
        REFERENCES environments(id)
        ON DELETE CASCADE,

    UNIQUE (environment_id, name)
)