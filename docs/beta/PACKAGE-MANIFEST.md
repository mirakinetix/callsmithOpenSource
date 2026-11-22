# Closed beta private package manifest

**Owner copy. External sharing on hold.**

| Field | Value |
| --- | --- |
| Original archive | `Callsmith-1.0.0-windows-x64-private-rc2.zip` |
| Owner storage | `%LOCALAPPDATA%\Mirakinetix\Callsmith-Private-Beta` |
| SHA-256 | `aff15af34c5b6e5ffb443e9a06f8b5bb0f4b1326a32600b8c7f13095816e1825` |
| Size and inventory | 59,152,029 bytes, 215 archived files |
| Private handoff | `%LOCALAPPDATA%\Mirakinetix\Callsmith-Private-Beta\v1.0.0-beta.1-preparation` |
| Application version | `1.0.0` |
| Proposed pilot label | `v1.0.0-beta.1` after approval |
| Verified main baseline | `f82a0a5f015f32375d60861f8e278cf77776e8e4`. Its tree matches the source commit below. |
| Source commit | `a62a610dd864e4dac614c313ec604a664852809c` |
| Entrypoint | `Callsmith\Callsmith.exe` |
| Signature | Unsigned Windows launcher |
| Bundled runtime | Temurin `25.0.5.1+2-LTS`, OpenJFX `25+29` Windows x64 |
| Other libraries | Jackson Annotations `2.21`, Core and Databind `2.21.8`, SQLite JDBC `3.53.4.0` with SQLite `3.53.4` |

RC2 is a portable Windows application image. It includes the Java runtime.
Release notes for the pilot cover saved collections, protected environment
variables, response assertions, sequential collection runs, import/export,
and the RUN-01 fix that serializes Send and Run. The pilot evaluates these
workflows with non-production APIs. The private handoff uses a byte-identical
copy of RC2 with the original filename. [Release notes](RELEASE-NOTES.md) [Known limitations](KNOWN-LIMITATIONS.md)

After the [owner decision](OWNER-CHECKLIST.md) records
all approvals, use one company-approved, access-controlled location for
the exact ZIP and its checksum, the guide, the test scenarios, the feedback
form, and the approved corresponding-source downloads. Share the
[Windows acceptance checklist](../windows-beta-acceptance.md) with these
documents so its PowerShell setup and loopback fixture remain accessible.
Verify that a pilot recipient can access the approved source materials from
the same location under the selected license-compliance route. Leave the
ZIP filename and contents intact for matching hashes.

Installed license and notice files are under `Callsmith/app/licenses`.
Runtime legal files are under `Callsmith/runtime/legal`. The owner copy of
the exact source package, upstream archives, and checksums is staged under
`sources` in the private folder. [Source manifest](../../LICENSES/WINDOWS-CORRESPONDING-SOURCES.md)
· [Release gates](REDISTRIBUTION.md)

The manager should supply the authorized feedback contact, private channel,
and support hours when issuing the approved guide. [Developer setup](DEVELOPER-GUIDE.md)
· [Feedback instructions](FEEDBACK-TEMPLATE.md)

The private handoff has the original RC2 filename, its checksum, staged
source files, an inventory of the bundled licenses, and copies of this
documentation. Verify `SHA256SUMS.txt` after any copy. The logo artwork
inventory identifies the exact Windows PNG and ICO scope. The external
distribution gate remains blocked. [Artwork inventory](ARTWORK-INVENTORY.sha256)

## Beta identity mapping

Maven and the Windows application image use numeric `1.0.0`. The packager
passes the same numeric version to `jpackage`. About currently shows the
product name and no version. For RC2, identify the beta on the manifest,
release notes, and download label. An eventual approved asset named
`Callsmith-1.0.0-beta.1-windows-x64.zip` may use the same bytes. If so,
record that it is RC2 under a delivery name and use the same SHA-256.

For a future build where About needs the beta identifier, populate it from
explicit build metadata through a separate PR. Keep the numeric Windows
version until an upgrade scheme is approved. Rebuild and record a new
hash for any changed image.
