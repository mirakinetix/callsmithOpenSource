# RC2 redistribution readiness register

**External package sharing is blocked**, including a closed pilot. The
documented items below are preparation evidence. The release owner needs
written clearance for the exact candidate and sharing method.

| Gate | Status | Evidence and required next record |
| --- | --- | --- |
| Package license and notice inventory | Verified for RC2 | `app/licenses` contains the Apache 2.0 license, project notice, branding, Lucide/Feather terms, OpenJFX terms, Jackson and SQLite JDBC notices and the Windows corresponding-source manifest. `runtime/legal` contains runtime notices. Retain these files and confirm final delivery preserves them. |
| Callsmith artwork | Owner approval required | The approved icon ZIP matches the SHA-256 in `BRANDING.md`. The 44 packaged PNGs and source ICO match the supplied pack. The existing package check confirms all seven ICO frames in the launcher. The pack has no standalone license. Record authority and signed scope for the binary and matching source. |
| OpenJFX Windows x64 | Blocked | The four extracted Windows JARs matched the Maven Central artifact checksums. The Base JAR reports `25+29` and both upstream tags resolve to the staged commit. The archive includes Windows build rules, native source and bundled JPEG materials. Have the release owner or reviewer obtain the publisher's exact Windows build and installation recipe and a list of downstream patches or confirmation that none apply. Check those inputs against the staged archive. |
| Temurin Windows x64 | Blocked | Signed source, pinned vendor scripts, Windows build metadata and the freshly checked Windows SBOM are staged. The metadata identifies devkit `vs2022_redist_14.40.33807_10.0.26100.1742` and the generated certificate source directory. The pinned archive includes `security/certdata.txt`, certificate generation scripts and `docs/CreateWindowsReDistDevKit.md`. RC2's trust store matches the verified Temurin input. Have the reviewer confirm that this source covers the build, identify any missing patch or install inputs, and document separately licensed devkit prerequisites. |
| Microsoft runtime | Blocked | The ZIP contains `msvcp140.dll`, `vcruntime140.dll`, `vcruntime140_1.dll` and `ucrtbase.dll`. The first three report version `14.40.33810.0`. The UCRT reports `10.0.26100.1742`. Record the applicable Visual Studio redistribution grant, REDIST-list coverage, and UCRT terms for these exact files and delivery method. |
| Delivery of matching source | Blocked | The staged application snapshot and upstream archive hashes match. Have the owner or legal reviewer confirm the GPLv2 Section 3 delivery route for these recipients. For the same-location route, stage the approved complete matching sources, applicable build/install scripts, notices and checksums where authorized binary recipients have equivalent download access. Test using a pilot recipient account before approval. |

The owner should retain the approval record, inputs and hashes reviewed,
the authority for each decision, and date of sign-off. Do not infer
external redistribution permission from internal verification or access
restrictions.

## Delivery and responsibility

1. Mirakinetix verifies the exact source components and retains the matching
   Callsmith archive, build scripts, upstream release evidence, license
   notices, and checksums. The staged Callsmith commit is the build input.
   Its tree matches the verified merge on `main`.
2. The release owner or legal reviewer documents the OpenJFX and Temurin
   source decisions and the grant for the actual Microsoft runtime DLLs.
   Record any necessary notices or restrictions and verify that the intended
   source access complies with the relevant licenses.
3. After clearance, the owner places the approved binary and complete matching
   source at the same access-controlled location under the chosen GPLv2
   Section 3 route. The owner and coordinating manager confirm equivalent
   access and hashes using an authorized recipient account before sending the
   binary. Public publication requires its own approval and access check.

[Branding](../../BRANDING.md) · [Project license](../../LICENSE) · [Notice](../../NOTICE)
· [Exact artwork inventory](ARTWORK-INVENTORY.sha256)
· [Third-party inventory](../../LICENSES/THIRD-PARTY-NOTICES.md)
· [Pinned Windows sources](../../LICENSES/WINDOWS-CORRESPONDING-SOURCES.md)
· [GPLv2 Section 3](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html#section3)
· [Microsoft Visual C++ redistribution](https://learn.microsoft.com/en-us/cpp/windows/redistributing-visual-cpp-files)
· [Microsoft Universal CRT deployment](https://learn.microsoft.com/en-us/cpp/windows/universal-crt-deployment)
