
## Local storage

The Secret flag masks environment values in the interface. Environment values are
currently persisted in the local SQLite database as plaintext. Treat the database and
its backups as sensitive. Use placeholders in shareable collections and review exported
JSON for credentials before sharing it. Avoid persistent production credentials in
workspaces until protected storage is available.
