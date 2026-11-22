CREATE TABLE request_assertions (
    request_id TEXT PRIMARY KEY NOT NULL REFERENCES saved_requests(id) ON DELETE CASCADE,
    definitions TEXT NOT NULL
)
