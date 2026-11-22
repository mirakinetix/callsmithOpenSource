# Windows beta release readiness

**BETA PREPARATION COMPLETE — RELEASE BLOCKERS REMAIN**

The private RC2 copy can be used for continued internal acceptance. External
distribution and public release both require explicit owner approval after all
mandatory gates are closed. The planned pilot targets 3–5 developers on Windows
x64 for ten working days. The partner manager must first secure organizational
permission.

| Gate | Status | Evidence or remaining action |
| --- | --- | --- |
| A. Main branch protected | PASSED | The active ruleset targets only `main`. It requires pull requests, resolved discussions and Windows/Ubuntu CI, and blocks force pushes and deletion. |
| B. Source merged and post-merge CI passed | PASSED | PR #2 preserved the 13 recovery commits. Main `f82a0a5f015f32375d60861f8e278cf77776e8e4` has the tested recovery tree. Both Maven checks passed. |
| C. Windows beta candidate verified | PASSED | The x64 launcher and runtime, dependency versions, license and notice files, approved artwork and unchanged ZIP hash were verified. Focused scanning found no credential patterns. Startup/shutdown and absence of bundled test databases were checked. |
| D. Windows interactive acceptance passed | PENDING | Complete new-save, Save As, assertion editing, Secret Storage, auth helpers, native upload, file replacement and DPI checks. |
| E. Artwork redistribution approved | REQUIRES OWNER APPROVAL | Confirm scope and authority for the reviewed Windows PNGs and ICO in binaries and matching source. Sign the owner declaration. |
| F. Corresponding-source package complete | BLOCKED | The verified source materials are staged. Confirm completeness of exact Windows OpenJFX publisher inputs and required Temurin build and installation materials. |
| G. Third-party redistribution conditions cleared | BLOCKED | Confirm applicable Microsoft runtime and UCRT rights and approve the equivalent source-access method before sending the binary. |
| H. Installation and tester documents complete | PASSED | Developer guide, local fixture, test scenarios, limitations, feedback form, pilot plan and owner checklist are prepared. |
| I. Beta package and checksum finalized | PASSED | Private handoff staged around the verified RC2 bytes and original filename. Proposed delivery identity is `v1.0.0-beta.1`. |
| J. External distribution explicitly approved | REQUIRES OWNER APPROVAL | Record the partner's permission, recipients, approved delivery method, exact archive SHA-256, and release owner's decision. |

[Windows acceptance](WINDOWS-ACCEPTANCE.md) · [Owner publication checklist](OWNER-CHECKLIST.md)
· [Candidate manifest](PACKAGE-MANIFEST.md) · [Redistribution register](REDISTRIBUTION.md)
· [Main ruleset](https://github.com/mirakinetix/callsmithOpenSource/rules/27827106)
· [Post-merge CI](https://github.com/mirakinetix/callsmithOpenSource/actions/runs/44991005147)
