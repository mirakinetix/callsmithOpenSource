# Closed beta owner publication checklist

**Decision on record: HOLD.** Complete the evidence and sign-off fields
before sharing a binary with the manager or any developer. Public GitHub publication requires separate, explicit approval.

| Gate | Current state | Decision or evidence to record |
| --- | --- | --- |
| Internal Windows acceptance | Open | Tester and date, Windows version, 100%/150% results, exact RC2 hash and outcomes for the remaining rows. Attach any exception and decision. |
| Artwork rights | Unapproved | Owner name, date, basis of rights, and permission for the exact artwork in official Windows pilot binaries and matching source. Use the statement below. |
| OpenJFX and Temurin corresponding source | Unapproved | Reviewer, date, verified build and patch coverage, approved source-delivery method, and recipient-access test. |
| Microsoft runtime redistribution | Unapproved | Reviewer, date, exact DLLs, grant or terms, REDIST coverage, and permitted delivery method. |
| Candidate identity | Hash verified, owner approval pending | Approve the full RC2 ZIP hash in the [manifest](PACKAGE-MANIFEST.md). Record any new candidate separately. |
| Organizational permission and participants | Pending | Coordinating manager, written permission from the partner organization, authorized Windows x64 machines, and 3–5 named participants or approved tester IDs. |
| Private delivery | Pending | Owner-controlled location, named access list, expiration or removal date, and equally accessible approved source files. |
| Feedback and support | Pending | Private intake channel, Mirakinetix primary and backup contact, working hours, response target and P0 escalation route. |
| Authorization to start | Pending | Owner signature, date, exact ZIP hash, approved sharing method, pilot dates, and explicit go decision. |

## Artwork statement for owner signature

I confirm that Mirakinetix holds or has obtained the rights needed to
reproduce and redistribute the selected Callsmith artwork identified in
the [Windows artwork inventory](ARTWORK-INVENTORY.sha256) and recorded in
`BRANDING.md`. I authorize Mirakinetix to include those unmodified PNG
and ICO assets in the approved official Callsmith Windows beta packages
and their matching source distributions, with Mirakinetix attribution.
This authorization covers the recorded distribution scope. Existing
third-party license terms and branding rules continue to apply.

Name ____________________  Date ____________________

Basis of rights and approved scope ____________________

## Final decision

- Decision `HOLD / GO` ____________________
- Exact ZIP SHA-256 ____________________
- Effective date and pilot dates ____________________
- Release owner and signature ____________________
- Public release separately authorized, if applicable ____________________

[Windows acceptance evidence](WINDOWS-ACCEPTANCE.md)
· [Redistribution register](REDISTRIBUTION.md)
