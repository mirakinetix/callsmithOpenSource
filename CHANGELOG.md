# Changelog

## Unreleased

- Added passphrase protection for environment variables marked Secret, with
  explicit migration of existing values and workspace lock and unlock.
- Variable lists show masked summaries without reading protected values.
- Removed expanded request details from diagnostics and hid response URLs after
  environment substitution changes the URL or query.
- Documented workspace backup, recovery, and remaining exposure boundaries.
