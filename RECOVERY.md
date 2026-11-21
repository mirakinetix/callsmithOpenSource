# Callsmith recovery register

| Priority | Evidence | Status |
| --- | --- | --- |
| P0 request persistence | On the baseline, open a collection and type a request. The editor offers Send but no Save. | Save, Save As, editing, folder destinations and request actions implemented. SQLite lifecycle tests pass. |
| P0 lost drafts | On the baseline, select a saved request after editing another. The editor replaces its fields immediately. | Confirmation added before replacing a draft or closing the window. Full manual UI exercise remains. |
| P0 secret storage | The baseline bound the raw value to SQLite. | Passphrase-protected rows and explicit migration implemented. Legacy copies and interactive platform checks remain release considerations. |
| P0 redirect and response data | The baseline followed automatic redirects and decoded all replies as text. | Same-origin policy, binary preservation, and byte counts verified in Part 2. |
| P0 URL fragments | Add a query field to a URL ending in a fragment. The baseline appends the parameter after the fragment. | Fixed and tested against a loopback server. |
| P1 Postman round trip | Export a nested collection with a raw body and import the exported JSON. | Mixed folder and request order, raw fields, and variable templates verified with a round-trip fixture. Unsupported features produce warnings. |
| P1 API testing | The baseline response inspector had no assertions or collection runner. | Persistent response rules and sequential collection runs implemented, with loopback and JavaFX coverage. |
| P1 HTTP controls | The baseline had one raw body field and a fixed timeout. | Part 2 adds temporary auth, structured bodies, file selection, configurable timeout and JSON formatting. |
| P1 packaging | Both scripts pin dependencies and app version. Windows startup reported missing SQLite module native access. | Both scripts include native access options. Windows x64 smoke checks and platform beta gates are recorded below. |
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

## Part 3 — Desktop workflows and approved icons

- The original approved archive was available as a local Downloads copy.
  Connected Library retrieval was unavailable. The matching filename, archive
  manifest, 186-entry CRC check, and SHA-256 are recorded in BRANDING.md.
- Shipped 38 unchanged UI PNGs and six window PNGs. IconView prefers the
  approved artwork and retains the existing SVG fallback. Both packaging scripts
  use the supplied native assets. No branding layout or workflow was redesigned.
- Collection refresh now preserves selection and expansion, restores the saved
  request after save or move, and avoids reloading an already open request.
  Collection deletion guards the current draft, and toolbar database failures
  surface in owned dialogs. Request field and environment dialogs have owners.
- The opt-in Windows JavaFX tests exercised collection creation and rename,
  nesting, request create/save/send/edit/Save As/duplicate/move/delete, discard
  confirmation, resize, restart, and repeat send against loopback.
- The same suite exercised Secret Storage setup, conversion of a fictional
  legacy row, masked display, locked request rejection, incorrect and correct
  unlock, secret substitution, and persistence after restart. The standard
  SQLite tests also passed the independent version 10 backup and recovery case.
- Send Options was tested for authentication selection and form/timeout
  validation. The editor test exercised cancel and resend, multipart file
  bytes, and binary response display. Native file choosers remain manual.
- Maven verify had 186 passed, zero failures/errors, and five desktop tests
  skipped by default. The opt-in desktop run passed all five.
- Windows x64 rebuilt and started with a new disposable SQLite database and
  zero stderr bytes. The launcher embeds all seven supplied ICO frames byte for
  byte, and the JAR includes all 44 copied PNGs. The accessibility tree exposed
  the correct window title and labeled controls. Packaged desktop click geometry
  was unavailable. Visual taskbar checks at Windows scaling settings and native
  dialog interaction remain open. The macOS script passed bash syntax checking.
  macOS Intel and Apple Silicon packaging and GUI tests remain open.

## Part 4 — Assertions, collection runs, and compatibility

- SQLite migration V14 adds saved-request assertion definitions. Existing request
  and secret tables retain their schemas. Definitions follow the same request
  ID when moved and are removed on deletion. Encrypted environment variables can
  be resolved for comparisons while unlocked. Reports omit comparison values.
- The runner walks saved requests in displayed order, uses the selected
  environment, and retains earlier results after a request fails or the user
  cancels. It applies the existing HTTP redirect and transport boundaries.
  Limits are 100 requests, 64 folder levels, 30 seconds per request and five
  minutes of execution. Imported requests require the same explicit Start Run
  step as any other collection.
- Fictional fixtures cover Postman round-trip order and fields, Insomnia legacy
  resource order, invalid input, and import rollback. OpenAPI support remains
  limited to JSON. Complex authentication, scripts and unsupported body formats
  are reported rather than executed or emulated.
- Maven targets Java 25. The packaging scripts and artifact names match Maven's
  1.0.0 version. JavaFX 25, Jackson 2.21.5 and SQLite JDBC 3.53.4.0 are
  shared pins. Review the package scripts whenever Maven dependency versions
  change. The project retains Apache 2.0 licensing and its Lucide attribution.
  Review the notices for the bundled JDK and other redistributable dependencies
  when preparing release artifacts.
- Headless Maven verification runs on Windows and Linux in the local CI
  configuration. Packaging workflows are separately triggered. Before merging
  to main, review the branch rules with the repository owner and require the
  headless checks and a maintainer-reviewed pull request.

### Verification results

- Maven verify passed 196 tests with zero failures and errors. Six JavaFX
  tests were skipped by default and passed in a separate opt-in run.
- The Windows x64 application image created a new disposable SQLite database,
  exposed the branded main window and its Run and Assertions controls, and
  shut down through a normal window-close request. stderr remained empty.
  All seven approved ICO image payloads were present in the launcher, and all
  44 PNG files matched the packaged copies. V14 was present in the app JAR.
- The desktop input provider reported unavailable coordinate geometry for
  packaged clicks. Source-based JavaFX tests covered collection editing, the
  assertion dialog and reports, and Secret Storage. Native chooser and
  taskbar scaling checks remain open. The macOS script passed a Bash syntax
  check. Neither macOS architecture was built or launched here.

### Beta verification still open

1. On a clean Windows x64 machine, launch the package with a disposable
   workspace. Repeat the collection, assertion, runner and encrypted-storage
   workflows. Open the file upload and response-save choosers. Confirm file
   selection, save and cancel behavior. Check the taskbar icon at standard
   and scaled display settings, then close and restart Callsmith normally.
2. Build and launch natively on Intel and Apple Silicon Macs. Verify the icon,
   window and native dialogs, protect and unlock a fictional secret, export
   a collection, then close and restart the app on each architecture.
3. Confirm review and required checks on main, review bundled dependency
   notices and version pins, and choose the beta version before preparing
   public artifacts. Do not present syntax checks as macOS runtime validation.

## GitHub synchronization and review

- **Implemented.** The five original recovery commits are preserved under
  `recovery/callsmith`. The branch is open for review in
  [draft PR #2](https://github.com/mirakinetix/callsmithOpenSource/pull/2).
  Local secret scans covered the tracked source and the five original outgoing
  commits and reported no findings.
- **CI verified.** At `8e504bb`, GitHub Actions ran Temurin 25 and
  `mvn -B verify` on Windows and Ubuntu. Each job passed 196 tests with
  zero failures and errors and skipped the six opt-in desktop tests.
  [Verification run](https://github.com/mirakinetix/callsmithOpenSource/actions/runs/44914156605)
  The verification workflow uses read-only repository permissions and produces
  no installer artifacts. Consult the draft PR checks for the current head.
- **Desktop verified.** All six opt-in JavaFX tests passed separately on Windows.
  The Windows x64 app image from `8e504bb` started and shut down normally with a
  disposable workspace. Packaged controls were readable through accessibility,
  and packaged mouse interaction could not be exercised from this session.
- **Manual verification pending.** Check the native Windows upload and response-save
  choosers and taskbar scaling. Build and launch the current recovery version on
  Intel and Apple Silicon Macs. Review bundled dependency notices, select a
  public beta version, and obtain final release approval.
- **Maintainer review pending.** GitHub reported no active protection on `main`
  during synchronization. Before merging, require a maintainer-approved pull
  request, passing Windows and Ubuntu verification, and protection against force
  pushes and deletion.

## P0 request dispatch remediation

- HTTP-01 reproduced with two POSTs received for unresolved header variables,
  one with an environment selected and one without. The shared request
  service now validates and expands variables before handing the request to
  the transport. Missing and locked variables produce a redacted error and
  zero dispatches. Intentional literal text uses `{!{example}}` to send
  `{{example}}`. The collection runner records failed validation and continues.
- IMP-01 reproduced with all three operations received by the root loopback
  server. The importer now applies operation, path, then document server
  precedence and expands declared defaults. A malformed effective server
  fails import. Relative paths and the default `/` require a user-supplied
  absolute URL when importing from a local file.
- After remediation, the root, path and operation fixtures each received
  exactly their intended request, and invalid or unresolved cases sent
  nothing. A runner fixture sent only its second, valid request and reported
  the rejected POST without including the protected value.
- Local Maven verify passed 204 tests with zero failures and errors and six
  opt-in tests skipped. The six opt-in Windows JavaFX tests passed separately.
  These tests used loopback endpoints and disposable files or databases.

## P1 remediation part A

- TEST-01 reproduced with two false failures and a false pass when assertions
  read the display marker rather than the received redirect-related header.
  Comparisons now use the received value with case-insensitive names. The
  response view, diagnostics, and run summaries keep these header values hidden.
- IMP-02 reproduced when an Insomnia collection with an unknown parent saved
  only its reachable request. Import now validates resource IDs, parent
  types, and reachability before persistence. Existing workspaces passed the
  preservation checks. Unsupported resources are named as a count in warnings.
- FILE-01 reproduced when an existing normalized filename was replaced by
  the export path without authorization. The UI confirms the actual destination.
  Publication stages complete JSON and uses a no-replace link. Confirmed
  replacement checks the existing file before using that same publication step.
  Tests cover cancelled confirmation, write failures, existing files, and a
  destination created or changed during staging. A filesystem that cannot
  safely publish through a hard link requires a different destination.
- DESK-01 reproduced when an oversized file reached importer detection. JSON is
  now read once with an 8 MiB input ceiling, bounded parser limits, a 5,000
  resource limit, and 64 folder levels. The desktop reads and saves on separate
  workers. Cancel during reading prevents persistence. Once saving starts,
  the SQLite transaction finishes before another import or close.
- Local Maven verify passed 219 tests, with zero failures and errors and nine
  opt-in tests skipped. All nine opt-in Windows JavaFX tests passed separately.
  The new tests exercise JavaFX dialogs and worker behavior with disposable
  directories and databases. Native chooser checks remain open.

## P1 remediation part B

- DEP-01: Maven and the Windows x64 image resolved Jackson Core and Databind
  2.21.8 with Annotations 2.21. SECURITY.md records the reviewed advisory
  ranges. The compatibility suite also checks bounded oversized properties
  in UTF-8 and UTF-16 collections. OpenJFX remains at 25 and SQLite JDBC
  remains at 3.53.4.0.
- REL-01: The package contains the application, Lucide, artwork provenance,
  OpenJFX, and Maven dependency notices in app/licenses. The linked Java
  runtime retains its legal directory. The Windows package verified 32
  installed application, library, and notice files byte for byte. Legal
  clearance for public binary distribution still requires confirmation of
  artwork rights and provision of corresponding GPLv2 source access.
- Windows x64 built with Temurin 25.0.5.1+2. The launcher contains all seven
  approved ICO frames. It opened a disposable database and shut down
  normally with exit code zero and empty stderr.
- CI-01: All workflows use the same Temurin pin. Packaging is manual and
  uploads reports only because the repository is public. The macOS jobs
  check hardware architecture, staged files, runtime legal files and the
  approved ICNS inside the mounted images.
- Export: NTFS tests passed. A filesystem without links is rejected before
  an existing file is moved. A disposable ZIP filesystem test preserved
  the original bytes and published no partial export. Removable and
  network-mounted destinations require writable hard-link support and
  atomic moves for replacement.
- Maven verify passed 221 tests with zero failures and errors and nine
  opt-in tests skipped. All nine Windows JavaFX tests passed separately.
  The Windows native chooser and taskbar checks, macOS interactive opening
  and Gatekeeper checks, and final beta approval remain open.
