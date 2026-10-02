CREATE TABLE saved_requests (
    id TEXT PRIMARY KEY,
    collection_id TEXT NOT NULL,
    folder_id TEXT,
    name TEXT NOT NULL,
    method TEXT NOT NULL,
    url TEXT NOT NULL,
    headers_json TEXT NOT NULL,
    query_parameters_json TEXT NOT NULL,
    body TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,

    FOREIGN KEY (collection_id)
        REFERENCES collections(id)
        ON DELETE CASCADE,

    FOREIGN KEY (folder_id)
        REFERENCES collection_folders(id)
        ON DELETE CASCADE
)