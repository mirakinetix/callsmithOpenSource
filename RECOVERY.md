# Callsmith recovery register

| Priority | Evidence | Status |
| --- | --- | --- |
| P0 request persistence | On the baseline, open a collection and type a request. The editor offers Send but no Save. | Save, Save As, editing, folder destinations and request actions implemented. SQLite lifecycle tests pass. |
| P0 lost drafts | On the baseline, select a saved request after editing another. The editor replaces its fields immediately. | Confirmation added before replacing a draft or closing the window. Full manual UI exercise remains. |
| P0 secret storage | SqliteEnvironmentVariableRepository binds the raw value to SQLite. Masking in the UI leaves disk data readable. | Open. Record diagnostics omit values. A storage design must handle macOS and Windows keys, backup recovery and migration without destroying existing data. |
| P0 redirect and response data | The HTTP transport follows normal redirects and presents text responses. Credential forwarding, binary handling and decoded versus wire byte counts need dedicated tests. | Open. |
| P0 URL fragments | Add a query field to a URL ending in a fragment. The baseline appends the parameter after the fragment. | Fixed and tested against a loopback server. |
| P1 Postman round trip | Export a nested collection with a raw body and import the exported JSON. | Basic round trip covered. Wider format compatibility and explicit warnings remain. |
| P1 API testing | The response inspector displays responses but has no assertions or collection runner. | Open. |
| P1 HTTP controls | The request editor has one raw body field and the transport uses a fixed timeout. | Cancellation added. Structured form bodies, uploads, configurable timeout, auth helpers and JSON formatting remain. |
| P1 packaging | Both scripts pin dependencies and app version. Windows startup reported missing SQLite module native access. | Added native access option to both scripts. macOS builds and workflow checks remain. |
| P2 parity | Broader protocol support, scripting and CLI integration. | Deferred. |
