# Developer testing scenarios

Use the [isolated profile and loopback fixture](../windows-beta-acceptance.md#setup)
or an approved mock or sandbox. For each assigned scenario, record **PASS**,
**FAIL**, **BLOCKED**, or **NOT TESTED**. For a failure, include the shortest
fictional reproduction. The scenarios marked Core are the default completion
measure. Keep existing tools for ongoing work.

| ID | Exercise | Expected result |
| --- | --- | --- |
| Core 01 | Send GET `/health`. Then send GET `/inspect` with query parameter `page=2` and header `X-Pilot: fixture`. | HTTP 200 for both. The response pane shows status, headers and body. The fixture rejects incorrect query or header values. |
| Core 02 | Send POST `/json` with body `{"beta":true}` and **Send Options → Body format → JSON**. | One POST reaches the fixture, which returns 200 and `post-ok`. |
| Core 03 | Use Basic with username `beta-user` and password `fictional-basic` on GET `/auth-basic`. Use Bearer with `beta-fictional-token` on POST `/auth`. Use **API key in header** with name `X-Pilot-Key` and value `fictional-pilot-key` on GET `/auth-key`. Clear each helper before the next case. | Each configured request returns 200. A deliberately incorrect fictional value is rejected by the fixture. |
| Core 04 | Activate an environment with ordinary variable `baseUrl` set to `http://127.0.0.1:8765`. Protect the disposable workspace and add secret `pilotToken` with value `beta-fictional-token`. Save POST `{{baseUrl}}/auth` with header `Authorization: Bearer {{pilotToken}}`. Lock, send, try a wrong passphrase, then unlock using the correct one. Restart. | Unlocked send returns 200. The locked attempt sends zero POSTs. Wrong passphrase is rejected. The stored variable stays masked and starts locked after restart. |
| Core 05 | Save a GET in a new collection. Edit and save it, create a folder, and use the request context menu to move and duplicate it. Restart the same disposable profile. | The edit stays on the original request, the duplicate is separate, and the tree and saved requests reopen. |
| Core 06 | Save GET `/assertions`. Add **Status equals** `200`, **Header equals** `X-Pilot` / `fixture`, and **JSON property equals** `/ready` / `true`. Send, then change one expected value and send again. | Three passes first. The changed value produces one failure. Use a Boolean `true` rather than a quoted string. |
| Core 07 | Run a collection with GET `/assertions` then POST `/slow`. Check the result, then repeat and cancel during the POST. Also start a single POST `/slow` and try **Run** while Send is still active. | Runs dispatch sequentially and report totals. Cancellation retains completed checks. The overlap attempt displays an explanation and adds zero POSTs. |
| Core 08 | Export a fictional collection to local NTFS and import it as Postman JSON. Load the generated `openapi.json` and `insomnia.json`. | Saved raw fields and tree survive the supported Postman round trip. OpenAPI and legacy Insomnia requests appear with the expected URL and method. Review warnings before sending. |
| Extra 09 | Use an unused loopback port for a connection failure. Use a one-second timeout on POST `/slow`, then repeat with the default timeout and cancel after dispatch. Send GET `/health` afterward. | The connection failure, timeout, and cancellation are distinct. Send becomes available again, and the late reply cannot replace the new response. |
| Extra 10 | Try POST with an undefined `{{missingPilotValue}}`. Import a disposable file containing malformed JSON. | The POST sends nothing, and the invalid import leaves the collection tree unchanged. |
| Extra 11 | Attempt to replace an export on local NTFS. Decline the app's replacement confirmation and compare hashes, then explicitly approve. Separately try saving response bytes to an existing file. | Cancelled export leaves original bytes intact. Approved export produces valid JSON. Response-byte save preserves the existing file. |
| Extra 12 | On POST `/upload`, open **Send Options → Multipart form → Choose File…**. Cancel once, then select fixture `upload.txt`, apply, and send. | Cancelling leaves no selection. The complete upload returns 200. |

The fixture does not need production or corporate access. If a company
sandbox is unavailable, use the loopback fixture and record any scenario
that needs a different environment as blocked.
