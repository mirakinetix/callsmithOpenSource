# Windows corresponding-source manifest

Applies to Callsmith 1.0.0 Windows x64. Keep the archives and their verification
records alongside the matching binary for eventual distribution. Clearance is
pending the checks below.

| Staged file | Revision and verification | SHA-256 |
| --- | --- | --- |
| OpenJDK25U-jdk-sources_25.0.5.1_2.tar.gz | [Adoptium release](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.5.1%2B2). Published checksum and detached signature verified against the [published key fingerprint](https://adoptium.net/installation/gpg-verification) `3B04D753C9050D9A5D343F39843C48A565F8F04B`. Source and Windows binary metadata identify the vendor revision `1513e3f76ff3526c24d7d8610b38872fd653ab1f`, tagged `jdk-25.0.5.1+2_adopt`. | `3fb4af3a690f6c0f47462f3f813a8f09dc4a237f0c6ab462a764f15bc15313a7` |
| Temurin-build-8d9fd6f.tar.gz | [Vendor build scripts](https://github.com/adoptium/temurin-build/archive/8d9fd6fa96e5066571a13823b07c2934e5958d8b.tar.gz) named by the Windows binary and source metadata. Includes the Windows build configuration and launch scripts. Locally recorded digest. | `0c9489a96b3c58c463327af7b33c147f2855da79a032ccad7aca2b9f377e5eea` |
| OpenJFX-25-ga-1718dc7.tar.gz | [Pinned OpenJFX source](https://github.com/openjdk/jfx/archive/1718dc767543023a5466304c09fb2d39711cb1a8.tar.gz). Both `25-ga` and `25+29` resolve to this commit. The Windows Base JAR reports `25+29`. The archive includes Gradle and Windows build rules, Java and Windows native code, and bundled IJG JPEG 9f with its README. Locally recorded digest. | `76edad912961f6608711945803f1b5b7bd3e13be5c169c7735a0d16fa27ed135` |

Retain the Adoptium source and Windows build metadata, published checksum, and
signature. The input Windows x64 JDK ZIP matched the vendor's SHA-256
`849d380ab0a64d9098e7d3a2de28beb73841c3b255c8284f019ccd8748c5eb5b`.
The JDK archive contains configure, make and legal files. The exact linked
modules and options are in `packaging/windows/build-app-image.ps1`.

The app includes Callsmith Apache 2.0 terms and notices, artwork provenance,
Lucide and Feather terms, the OpenJFX license and Classpath Exception material,
and Jackson and SQLite JDBC terms and notices under `app/licenses`.
The linked runtime has module-specific material in `runtime/legal`.
SQLite core is public domain. The OpenJFX graphics notice inventory includes
IJG JPEG, Mesa and PipeWire. Retain the IJG README and its change notices with
the vendored source.

## Before public distribution

- Confirm the shipped OpenJFX Windows Maven JARs correspond to the pinned
  source and identify any downstream native patches and the publisher's exact
  build and installation scripts. The matching version and source contents
  establish an upstream baseline. The publisher's native build inputs remain
  unconfirmed.
- Review the Windows Temurin devkit and certificate inputs in the metadata for
  completeness. Confirm terms for the actual Microsoft runtime files
  `msvcp140.dll`, `vcruntime140.dll`, `vcruntime140_1.dll`, and
  `ucrtbase.dll` separately from the JDK source.
- Obtain written owner confirmation for Callsmith artwork in the binary and
  matching project source distribution.
- Provide complete source, applicable changes and build/install scripts, and
  publish checksums beside the corresponding binaries. Verify equivalent
  download access in a signed-out session under the
  [GPLv2 Section 3](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html#section3)
  same-location option. Keep the exact matching Callsmith source revision and
  packaging instructions. Avoid publishing until the owner has reviewed and
  cleared the remaining terms.
