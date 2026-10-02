CREATE TABLE collection_folders (
    id TEXT PRIMARY KEY,
    collection_id TEXT NOT NULL,
    parent_folder_id TEXT,
    name TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,

    FOREIGN KEY (collection_id)
        REFERENCES collections(id)
        ON DELETE CASCADE,

    FOREIGN KEY (parent_folder_id)
        REFERENCES collection_folders(id)
        ON DELETE CASCADE
)