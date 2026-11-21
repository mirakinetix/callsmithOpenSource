# Callsmith recovery register

| Priority | Evidence | Status |
| --- | --- | --- |
| P0 request persistence | On the baseline, open a collection and type a request. The editor offers Send but no Save. | Save, Save As, editing, folder destinations and request actions implemented. SQLite lifecycle tests pass. |
| P0 lost drafts | On the baseline, select a saved request after editing another. The editor replaces its fields immediately. | Confirmation added before replacing a draft or closing the window. Full manual UI exercise remains. |
| P0 secret storage | The baseline bound the raw value to SQLite. | Passphrase-protected rows and explicit migration implemented. Legacy copies and interactive platform checks remain release considerations. |
| P0 redirect and response data | The HTTP transport follows normal redirects and presents text responses. Credential forwarding, binary handling and decoded versus wire byte counts need dedicated tests. | Open. |
| P0 URL fragments | Add a query field to a URL ending in a fragment. The baseline appends the parameter after the fragment. | Fixed and tested against a loopback server. |
| P1 Postman round trip | Export a nested collection with a raw body and import the exported JSON. | Basic round trip covered. Wider format compatibility and explicit warnings remain. |
| P1 API testing | The response inspector displays responses but has no assertions or collection runner. | Open. |
| P1 HTTP controls | The request editor has one raw body field and the transport uses a fixed timeout. | Cancellation added. Structured form bodies, uploads, configurable timeout, auth helpers and JSON formatting remain. |
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
