# Callsmith recovery register

| Priority | Evidence | Status |
| --- | --- | --- |
| P0 request persistence | On the baseline, open a collection and type a request. The editor offers Send but no Save. | Save, Save As, editing, folder destinations and request actions implemented. SQLite lifecycle tests pass. |
| P0 lost drafts | On the baseline, select a saved request after editing another. The editor replaces its fields immediately. | Confirmation added before replacing a draft or closing the window. Full manual UI exercise remains. |
| P0 secret storage | The baseline bound the raw value to SQLite. | Passphrase-protected rows and explicit migration implemented. Legacy copies and interactive platform checks remain release considerations. |
| P0 redirect and response data | The baseline followed automatic redirects and decoded all replies as text. | Same-origin policy, binary preservation, and byte counts verified in Part 2. |
| P0 URL fragments | Add a query field to a URL ending in a fragment. The baseline appends the parameter after the fragment. | Fixed and tested against a loopback server. |
| P1 Postman round trip | Export a nested collection with a raw body and import the exported JSON. | Basic round trip covered. Wider format compatibility and explicit warnings remain. |
| P1 API testing | The response inspector displays responses but has no assertions or collection runner. | Open. |
| P1 HTTP controls | The baseline had one raw body field and a fixed timeout. | Part 2 adds temporary auth, structured bodies, file selection, configurable timeout and JSON formatting. |
| P1 packaging | Both scripts pin dependencies and app version. Windows startup reported missing SQLite module native access. | Added native access option to both scripts. macOS builds and workflow checks remain. |
| P2 parity | Broader protocol support, scripting and CLI integration. | Deferred. |

## Part 1 — Secret Storage

- User-controlled passphrase gives a portable recovery path without storing a key
  beside the database. Windows DPAPI would bind access to the account and usually
  the computer. macOS Keychain could avoid prompting each session, but its app
  identity and access behavior need validation for signed Intel and Apple Silicon
  packages. A hybrid would require both native integrations and a separately tested
  recovery artifact. The current milestone uses only standard Java cryptography.
- Schema migrations V11–V13 distinguish raw records and versioned encryption.
  Legacy conversion is explicit. The SQLite integration tests use a version 10
  source and migrate copies. A simulated metadata write failure after row updates
  rolls back the transaction. A failed setup can be retried without new key files.
- Backup and recovery are documented in SECURITY.md. A compacted SQLite snapshot
  restored on Windows with the original passphrase and refused the wrong one.
  Callsmith has no dedicated backup export UI. The snapshot still contains ordinary
  workspace data as plaintext.
- Windows x64: the app image built and launched against a disposable protected workspace. The Variables panel exposed the fictional secret only as a mask in its accessibility tree. Desktop input was unavailable for the interactive dialog check. Neither macOS architecture ran here. Maven verify passed 169 tests with zero failures, errors or skips.
- P0 before beta: verify the packaged Secret Storage dialog and complete the
  interactive workflow on the supported platforms. Test a real power interruption
  and disk-full scenario in a disposable workspace before relying on migration at
  scale. Existing user workspaces must be backed up and explicitly migrated by
  their owner.
- P1: Review cross-origin credential forwarding and server echoes separately.
  Consider passphrase rotation and a guided backup flow. P2: Evaluate native key
  storage and workstation-specific integration after the portable path is stable.

## Part 2 — HTTP requests and responses

| Baseline issue | Part 2 result |
| --- | --- |
| P0 automatic redirects could replay headers or a request body at another origin. | Disabled JDK redirects. Explicit same-origin policy blocks other hosts, ports and schemes, detects loops and permits five hops. Loopback HTTP and TLS destination tests observed no prohibited request. |
| P0 string decoding lost binary data and reported re-encoded text length. | Retain original bytes, cap wire data and gzip expansion at 8 MiB, classify binary data, and decode common text encodings. |
| P0 fixed timeout did not give an editor control or an explicit deadline across redirect hops and body reception. | Per-send timeout shares a deadline across the chain. Cancellation interrupts the worker and cancels the JDK exchange. The editor ignores stale results. |
| P1 auth required manual headers. Structured bodies and uploads were absent. | Added Basic, Bearer, header and query API keys, URL-encoded and multipart forms, MIME defaults, and single-file streaming. Options are temporary and no SQLite schema changed. |
| P1 unformatted body view offered no binary indication or byte export. | Added bounded raw and formatted JSON previews, binary indication, and byte saving to a new file. |

The optional JavaFX test drives editor controls against a loopback server on a
desktop. It checks JSON, text, Bearer authorization, cancellation followed by a
new send, multipart file bytes, and binary display. Native file chooser and
interactive packaged dialog checks remain separate. The default Maven build passed
184 tests with one opt-in desktop test skipped. That Windows desktop test passed
when run separately with the callsmith.desktop.tests property enabled.
The rebuilt Windows x64 image started on a fresh disposable database with no
stderr output. Its accessibility tree exposed Send Options, Format JSON and Save
Response Bytes. The desktop input provider reported unavailable coordinate input
geometry, so manual packaged dialog and native file chooser checks remain.
