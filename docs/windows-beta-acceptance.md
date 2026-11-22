# Windows beta acceptance

Use the private Windows x64 ZIP and a disposable Windows account or VM. Use
fictional credentials and loopback endpoints. Unpack the entire ZIP and run
the following from PowerShell in the folder containing `Callsmith`.

```powershell
$betaApp = (Resolve-Path '.\Callsmith\Callsmith.exe').Path
$betaRoot = Join-Path $env:TEMP ('callsmith-beta-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $betaRoot | Out-Null
$previousLocalAppData = $env:LOCALAPPDATA

function Start-CallsmithBeta {
    try {
        $env:LOCALAPPDATA = $betaRoot
        Start-Process -FilePath $betaApp
    } finally {
        $env:LOCALAPPDATA = $previousLocalAppData
    }
}

Start-CallsmithBeta
Write-Output $betaRoot
```

Repeat `Start-CallsmithBeta` for a restart against the same disposable
database. In a second PowerShell window, set `$betaRoot` to the printed
directory and start this fixture using Python 3.

```powershell
Set-Location -LiteralPath $betaRoot
@'
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from time import sleep

origin = "http://127.0.0.1:8765"
Path("upload.txt").write_bytes(b"callsmith-upload")
Path("insomnia.json").write_text(json.dumps({
    "_type": "export", "__export_format": 4, "resources": [
        {"_id": "wrk_beta", "_type": "workspace", "name": "Insomnia beta"},
        {"_id": "req_health", "_type": "request", "parentId": "wrk_beta",
         "name": "Health", "method": "GET", "url": origin + "/health"}
    ]
}), encoding="utf-8")
Path("openapi.json").write_text(json.dumps({
    "openapi": "3.0.3", "info": {"title": "OpenAPI beta", "version": "1"},
    "servers": [{"url": origin + "/document"}],
    "paths": {
        "/document-only": {"get": {}},
        "/path-only": {"servers": [{"url": origin + "/path"}], "get": {}},
        "/override": {"servers": [{"url": origin + "/path"}],
                      "post": {"servers": [{"url": origin + "/operation"}]}}
    }
}), encoding="utf-8")

class Fixture(BaseHTTPRequestHandler):
    def do_GET(self):
        self.reply(200, b"health-ok\n")

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        print("received POST", self.path, flush=True)
        if self.path == "/slow":
            sleep(10)
        ok = True
        if self.path == "/json":
            ok = ("application/json" in self.headers.get("Content-Type", "")
                  and body == b'{"beta":true}')
        if self.path == "/auth":
            ok = self.headers.get("Authorization") == "Bearer beta-fictional-token"
        if self.path == "/upload":
            ok = ("multipart/form-data" in self.headers.get("Content-Type", "")
                  and b"callsmith-upload" in body)
        self.reply(200 if ok else 400, b"post-ok\n" if ok else b"invalid fixture\n")

    def reply(self, code, body):
        self.send_response(code)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            pass

ThreadingHTTPServer(("127.0.0.1", 8765), Fixture).serve_forever()
'@ | python.exe -
```

## Checklist

Use `http://127.0.0.1:8765` for each endpoint. Put downloaded bytes and
collection exports in the temporary folder.

| Check | Steps and expected result |
| --- | --- |
| Startup and display | Launch with an empty workspace. Expect one Callsmith window and an empty collection tree. Resize and drag the divider. At 100% and 150% Windows scale, verify the taskbar icon, reachable labels and controls, and dialogs that stay on-screen near their owner. |
| Saved requests | Create collection `Beta`. Create GET `/health`, **Save** as `Health`, and send. Expect 200 with headers and `health-ok`. Edit its URL to `/health?edited=1`, save, **Save As** a copy, and delete the copy. Restart and confirm the edited original persists and the copy is absent. |
| JSON, timeout and cancel | POST `{"beta":true}` to `/json` using **Send Options → Body format → JSON**. Expect 200 and one POST. POST to `/slow` with a one-second timeout. Expect one POST and a timeout before Send becomes available. Repeat at the default timeout and manually cancel. Wait for cancellation, send GET `/health`, and confirm the late response cannot replace it. |
| Native upload | On an unsaved POST to `/upload` with a blank raw body, use **Send Options → Body format → Multipart → Choose File…**. Cancel once and confirm no selection. Reopen, choose `upload.txt`, apply, and send. Expect 200 and one POST. |
| Native response save | Send GET `/health`, open **Save Response Bytes…**, cancel, and check that no file was written. Then save as `response.bin`. It must contain `health-ok` followed by LF. Try the same name. If the picker allows continuing, the app must reject the existing destination. Verify the original with `Get-FileHash`. |
| Assertions and collection run | Save POST `/slow` then GET `/health` in a test collection. On each, select **Assertions → Status equals**, enter 200, choose **Add Rule → Save Assertions**. Run and verify the server sees both in order and the summary reports two passes. Repeat and cancel during the first request, then verify the summary says cancelled and the second request never arrived. |
| RUN-01 | Start a single POST `/slow`, click **Send** repeatedly, then select the test collection and click **Run**. Expect an in-progress explanation and exactly one POST. After Send finishes, start the run and click **Start Run** repeatedly. Expect the main window to remain unavailable during the modal run, with one dispatch per saved request. After cancellation completes, verify Send works again. |
| Protected authentication | In **Environment → Manage → Secret Storage**, choose a fictional passphrase of at least 16 characters and protect the workspace. In an active test environment, create secret `betaToken` with value `beta-fictional-token`. Save a POST to `/auth` with header `Authorization: Bearer {{betaToken}}`. Expect 200 while unlocked and a masked variable display. Lock and send again. Expect failure with zero new POSTs. Try a wrong passphrase, then the correct one, and verify the request works again. |
| Import and export | Export `Beta` to `beta.json`, re-import as Postman JSON, and verify the saved request. Repeat export to the same path. Proceed past the native picker as needed to reach **Replace collection export**, decline, and verify the hash is unchanged. Then explicitly approve and check valid JSON. Import `insomnia.json` and check the GET URL. Import `openapi.json` and check URLs end with `/document/document-only`, `/path/path-only`, and `/operation/override`. |
| Shutdown and restart | Close normally and verify the process exits. Relaunch and confirm startup is quiet, saved requests and assertions persisted, and Secret Storage starts locked. Stop the fixture with Ctrl+C. |

Record pass or fail per row, the ZIP SHA-256, Windows version, display scale,
and any dialog or error text.
