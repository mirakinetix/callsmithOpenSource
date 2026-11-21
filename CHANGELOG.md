# Changelog

## Unreleased

- Added passphrase protection for environment variables marked Secret, with
  explicit migration of existing values and workspace lock and unlock.
- Variable lists show masked summaries without reading protected values.
- Removed expanded request details from diagnostics and hid response URLs after
  environment substitution changes the URL or query.
- Documented workspace backup, recovery, and remaining exposure boundaries.
- Added same-origin redirect handling, bounded binary-safe downloads and JSON previews.
- Added temporary request authentication, body formats, file uploads and adjustable timeouts.
- Integrated approved Callsmith window icons, native launcher assets, and UI artwork.
- Preserved collection tree selection and expanded folders after edits and owned more dialogs by their parent window.
- Added desktop regression coverage for saved requests, Secret Storage, and Send Options.
