# Callsmith closed developer beta pilot plan

**External pilot on hold.** RC2 is for internal acceptance. Start external
testing only after the release owner completes the [decision sheet](OWNER-CHECKLIST.md)
and the coordinating manager confirms organizational permission.

## Ten working days

Invite 3–5 Windows x64 developers. Ask each to use a mock, local, or
approved non-production API and fictional credentials. Keep existing tools
for business-critical API work.

| When | Work | Who |
| --- | --- | --- |
| Day 1 | Verify the ZIP hash, launch in an isolated profile, run the basic GET and POST, and confirm the private feedback channel works. | Manager coordinates. Each developer checks setup. |
| Days 2–5 | Create and save requests, practice auth and protected variables, organize folders, and reopen the workspace. | Developers complete assigned core rows. Manager handles access or environment issues. |
| Days 6–9 | Try assertions, the sequential runner, import/export, and one realistic sandbox task. Record any reproducible issue. | Developers exercise realistic workflows. Mirakinetix triages reports. |
| Day 10 | Submit the short feedback form. Review completion, issues, and willingness to reuse the tool. Decide whether to extend, pause, or close the pilot. | Manager consolidates. Release owner makes the decision. |

The manager assigns the core scenarios before Day 1. Use one shared private
checklist and one final response per developer. File individual bugs only
when they need follow-up. Default support proposal is a next-working-day
acknowledgment, with immediate escalation for P0 once it is reported.
Confirm the channel, backup contact, and working hours before kickoff.

- **Release owner** approves the candidate, redistribution, delivery scope
  and support arrangements. They decide whether to extend or pause the pilot.
- **Coordinating manager** obtains organizational permission, names the
  testers, confirms approved machines and test APIs, and consolidates feedback.
- **Testers** verify the ZIP hash, use approved fictional fixtures or
  sandboxes, record assigned results, and report issues privately.

## Pilot targets

- Zero unresolved critical defects and zero security or data integrity incidents.
- At least 90% of assigned core workflows passed. Calculate passed assigned
  rows divided by assigned rows across all testers. Count a blocked or
  incomplete assignment in the denominator.
- Meaningful feedback from at least three developers.
- Record each developer's willingness to reuse Callsmith and their reasons.
- Hold external use and escalate immediately if a potential P0 occurs.

On Day 10, the owner records GO for a follow-up, HOLD pending fixes, or END. Keep the decision and owners for follow-up items in the private pilot record.

## Materials

- [Windows RC2 acceptance evidence](WINDOWS-ACCEPTANCE.md)
- [Redistribution readiness register](REDISTRIBUTION.md)
- [Private candidate manifest and release notes](PACKAGE-MANIFEST.md)
- [Developer guide](DEVELOPER-GUIDE.md)
- [Developer test scenarios](TEST-SCENARIOS.md)
- [Feedback form and bug reporting](FEEDBACK-TEMPLATE.md)
- [Known limitations](KNOWN-LIMITATIONS.md)
- [Release-owner decision sheet](OWNER-CHECKLIST.md)
- [Release-readiness gates](RELEASE-READINESS.md)
- [Proposed release notes](RELEASE-NOTES.md)
