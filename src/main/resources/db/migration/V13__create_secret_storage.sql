CREATE TABLE secret_storage (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    format_version INTEGER NOT NULL,
    salt BLOB NOT NULL,
    verifier TEXT NOT NULL
)
