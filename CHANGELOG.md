# Changelog

## Unreleased

- Prevented individual Send and collection Run from overlapping. Cancellation
  keeps the execution slot occupied until the worker finishes cleanup.
- Included a Windows corresponding-source manifest and set the launcher vendor
  metadata to Mirakinetix.

- Updated Jackson Core and Databind to 2.21.8 and pinned the packaging and CI JDK.
- Staged native packages from Maven's resolved libraries and included installed license and notice files.
- Verified native package contents in the manual workflows and limited public CI uploads to build reports.
- Checked hard-link support before moving an existing collection export for confirmed replacement.

- Fixed response-header equality assertions to use received values while retaining display redaction.
- Rejected invalid Insomnia resource graphs before saving and reported unsupported records.
- Added final destination confirmation and overwrite protection to Postman export.
- Moved collection import to background workers and bounded file size, resource count, and nesting.

- Blocked unresolved request variables before individual and collection sends,
  and added explicit syntax for sending literal placeholder text.
- Applied OpenAPI operation, path and document server precedence and blocked
  invalid effective servers from falling back to a different destination.

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
- Added persistent response assertions for HTTP status, headers, text, JSON and duration.
- Added deliberate sequential collection runs with environment selection, progress,
  cancellation, and pass/fail reports that omit request and response data.
- Preserved mixed folder and request order on Postman import and export and on
  supported Insomnia import. Added warnings for unsupported collection features.
- Added JavaFX regression coverage for assertion editing and collection reports,
  and a Windows and Linux headless verification workflow.
