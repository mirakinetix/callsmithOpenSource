# Windows x64 RC2 acceptance

**22 November 2026** · Windows 11 x64 build 26300 · disposable profiles
ZIP SHA-256 `aff15af34c5b6e5ffb443e9a06f8b5bb0f4b1326a32600b8c7f13095816e1825`.

The current checks and retained observations below used the same RC2 bytes.
The fixture bound to `127.0.0.1:8765` and used fictional data. Two clean
shutdowns during release preparation produced zero stderr bytes.

| Check | Result | Evidence |
| --- | --- | --- |
| Startup and normal shutdown | PASS | Fresh extracted image opened, initialized the new SQLite workspace and closed normally. The same profile also reopened and closed normally. |
| Create a collection and start a new request | PASS | Earlier RC2 session created a collection. This session opened a new GET draft and entered a loopback URL. |
| Save a brand-new request and Save As | BLOCKED | The Save destination opened, then the JavaFX name field could not accept desktop input. The full new-save and Save As workflows require owner checks. |
| Edit and save an existing request | PASS | Changed the imported Health URL to `/health?edited=1`, saved through the app and reopened it after restart. A read-only SQLite check confirmed the stored URL. |
| GET and POST execution | PASS | Reopened GET returned 200 and `health-ok`. A saved JSON POST with `Content-Type: application/json` reached the fixture and returned 200. The fixture validated the raw JSON body. |
| HTTP timeout | PASS | A one-second setting stopped a single POST to `/slow`. The app displayed **The request timed out** and restored Send. The fixture counted one POST. |
| HTTP cancellation | PASS | Earlier RC2 session cancelled a dispatched POST. The app displayed **Cancelled** and restored Send. The server could still finish the received request. |
| Native upload chooser | BLOCKED | The modal drop-down did not reliably select Multipart form using the available desktop input. Upload picker cancellation, file selection and dispatch remain open. |
| Native response-save chooser | PASS | Earlier RC2 session cancelled once and saved a fresh 14-byte response. The bytes matched the fixture exactly. |
| Authentication using saved headers | PASS | The local runner sent fictional Basic, Bearer and API-key headers. Each fixture endpoint validated the corresponding value and returned 200. |
| Send Options authentication helpers | NOT TESTED | The saved-header check verified dispatch. Basic, Bearer and API-key helper configuration remain manual. |
| Protected Secret Storage | NOT TESTED | Setup, wrong and correct passphrase, lock/unlock, locked dispatch prevention, and restart lock need owner testing in RC2. |
| Response assertion evaluation | PASS | Earlier RC2 session evaluated seeded status, header and JSON assertions. All three passed. |
| Assert and save rules from the editor | NOT TESTED | The seeded assertion test verified evaluation. Owner should create a new rule and verify persistence after restart. |
| Sequential collection runner | PASS | Current RC2 runner finished four requests with four passes. Earlier RC2 session also verified order and retained completed checks after cancellation. |
| RUN-01 concurrency | PASS | Earlier RC2 session observed the in-progress explanation when Run was clicked during Send. Fixture counts showed zero extra POSTs. Existing automated tests cover the reverse direction and repeated clicks. |
| Postman import and export | PASS | Earlier RC2 session exported and re-imported a two-request collection through the native pickers. Current RC2 imported and ran another Postman v2.1 fixture. |
| Legacy Insomnia import | PASS | RC2 imported the generated legacy JSON and displayed its saved Health request. |
| OpenAPI import | PASS | RC2 imported the JSON fixture. Disposable SQLite stored the three expected URLs using document, path and operation server precedence. |
| Safe export overwrite | PASS | Earlier RC2 session reached the app's own replacement prompt, declined and verified the existing export SHA-256 stayed unchanged. |
| Approved export replacement and response overwrite refusal | NOT TESTED | The release-owner test must verify both existing-file paths on local NTFS. |
| Restart persistence | PASS | The same temporary profile displayed the imported collections and reopened the edited Health request. GET succeeded after restart. |
| Windows taskbar icon and scaling at 100% and 150% | NOT TESTED | The app's window icon was visible. Check the taskbar and dialog layout at both scales on an approved Windows test machine. |

## Remaining owner checks

Use the [PowerShell launch and fixture instructions](../windows-beta-acceptance.md).
Record the candidate SHA-256, Windows build and the result for each row.

1. Save a new named request, use Save As, and verify both survive restart.
   Create and save an assertion from the editor, then reopen it.
2. Exercise Basic, Bearer and API-key helpers. Set up Secret Storage with a
   fictional passphrase, lock it, verify zero dispatches for a protected
   request, reject a wrong passphrase, unlock with the correct one, and
   verify the next launch starts locked.
3. Cancel the native upload picker once. Reopen it, choose the sample file,
   and confirm HTTP 200. Verify deliberate export replacement and refusal
   to overwrite a saved response file.
4. Check the window layout, native dialogs and taskbar icon at 100% and 150%.

The build and package checks carry forward for the identical ZIP.
The [post-merge checks](https://github.com/mirakinetix/callsmithOpenSource/actions/runs/44991005147)
passed 223 tests per job with 10 opt-in skips and zero failures or errors.
The separately run Windows JavaFX suite previously passed all ten.
