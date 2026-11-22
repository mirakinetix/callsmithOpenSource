# RC2 redistribution readiness register

**External package sharing is blocked**, including a closed pilot. The
documented items below are preparation evidence. The release owner needs
written clearance for the exact candidate and sharing method.

| Gate | Status | Evidence and required next record |
| --- | --- | --- |
| Package license and notice inventory | Verified for RC2 | `app/licenses` contains the Apache 2.0 license, project notice, branding, Lucide/Feather terms, OpenJFX terms, Jackson and SQLite JDBC notices and the Windows corresponding-source manifest. `runtime/legal` contains runtime notices. Retain these files and confirm final delivery preserves them. |
| Callsmith artwork | Owner approval required | The approved icon ZIP matches the SHA-256 in `BRANDING.md`. The 44 packaged PNGs and source ICO match the supplied pack. The existing package check confirms all seven ICO frames in the launcher. The pack has no standalone license. Record authority and signed scope for the binary and matching source. |
| OpenJFX Windows x64 | Blocked | All four RC2 Windows JAR checksums match Maven Central. The Base JAR reports `25+29`, and `25-ga` and `25+29` resolve to `1718dc767543023a5466304c09fb2d39711cb1a8`. The archive includes Windows and Maven build rules, native sources, and bundled JPEG source and notices. `buildSrc/win.gradle` identifies the CRT files and can select a separate `MSVC_REDIST_VER`. The exact publisher invocation, toolchain and SDK selection and patch status remain unverified. Obtain and match that release build record before clearing the gate. |
| Temurin Windows x64 | Blocked | The source signature and staged SHA-256 values were rechecked. Windows metadata pins devkit `vs2022_redist_14.40.33807_10.0.26100.1742` and the generated certificate source directory. The pinned archive includes `security/certdata.txt`, certificate generation scripts and `docs/CreateWindowsReDistDevKit.md`. RC2's trust store matches the verified Temurin input. The reviewer must record complete coverage of required vendor source, patches, and install/build scripts. Record any separately licensed compiler, SDK, or devkit prerequisites separately and verify the basis for excluding them from the source delivery. |
| Microsoft runtime | Blocked | There are separate Microsoft runtime files in the Temurin image and inside the JavaFX Graphics JAR. Both sets require provenance and redistribution review using the exact binaries and intended private ZIP distribution. |
| Delivery of matching source | Blocked | The staged application snapshot and upstream archive hashes match. Have the owner or legal reviewer confirm the GPLv2 Section 3 delivery route for these recipients. For the same-location route, stage the approved complete matching sources, applicable build/install scripts, notices and checksums where authorized binary recipients have equivalent download access. Test using a pilot recipient account before approval. |

The owner should retain the approval record, inputs and hashes reviewed,
the authority for each decision, and date of sign-off. Do not infer
external redistribution permission from internal verification or access
restrictions.

## Microsoft runtime inventory and terms

| Location inside RC2 | Files | Versions |
| --- | --- | --- |
| `runtime/bin` | `msvcp140.dll`, `vcruntime140.dll`, `vcruntime140_1.dll` | `14.40.33810.0` |
| `runtime/bin` | `ucrtbase.dll` | `10.0.26100.1742` |
| `app/lib/javafx-graphics-25-win.jar` | `msvcp140.dll`, `msvcp140_1.dll`, `msvcp140_2.dll`, `vcruntime140.dll`, `vcruntime140_1.dll` | `14.42.34438.0` |
| `app/lib/javafx-graphics-25-win.jar` | `ucrtbase.dll` and 41 `api-ms-win-*.dll` forwarders | `10.0.22621.3233` |

Microsoft's Visual Studio 2022 REDIST list permits eligible licensees to
distribute unmodified files from the licensed `VC\redist` tree with their
program, subject to the software terms and exclusions for debug files.
Microsoft documents app-local deployment using retail Windows SDK
`Redist\ucrt\DLLs` files. Windows 10 and 11 normally load the system UCRT.
Confirm the source REDIST directory, unmodified bytes, applicable grant to
Mirakinetix or documented onward rights for each set, and any required
distribution conditions. A file name, version match, or presence in the
upstream JAR does not establish that grant.

[Visual Studio 2022 REDIST list](https://learn.microsoft.com/en-us/visualstudio/releases/2022/redistribution#visual-c-runtime-files)
· [Visual Studio license directory](https://visualstudio.microsoft.com/license-terms/)
· [Windows SDK runtime location](https://learn.microsoft.com/en-us/cpp/porting/upgrade-your-code-to-the-universal-crt)

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
   binary. Confirm an unapproved account cannot download, and record the
   access removal date and private feedback channel. Keep access to source
   consistent with the chosen license route before expiring the pilot links.
   Public publication requires its own approval and access check.

[Branding](../../BRANDING.md) · [Project license](../../LICENSE) · [Notice](../../NOTICE)
· [Exact artwork inventory](ARTWORK-INVENTORY.sha256)
· [Third-party inventory](../../LICENSES/THIRD-PARTY-NOTICES.md)
· [Pinned Windows sources](../../LICENSES/WINDOWS-CORRESPONDING-SOURCES.md)
· [GPLv2 Section 3](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html#section3)
· [Microsoft Visual C++ redistribution](https://learn.microsoft.com/en-us/cpp/windows/redistributing-visual-cpp-files)
· [Microsoft Universal CRT deployment](https://learn.microsoft.com/en-us/cpp/windows/universal-crt-deployment)
