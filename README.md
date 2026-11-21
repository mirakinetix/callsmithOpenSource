# Callsmith

**Callsmith** is an open-source, local-first API development and testing platform developed by **Mirakinetix**.

> Build. Automate. Scale.

## Project

Callsmith is designed as a developer-first desktop application for building, testing, organizing, and working with APIs locally.

## Technology

- Java 25 LTS
- JavaFX
- Maven
- JDK HTTP Client
- SQLite
- JDBC
- Jackson
- JUnit 6
- Mockito

## Open Source

Callsmith source code is licensed under the **Apache License, Version 2.0**.

See [LICENSE](LICENSE) for the complete license.

Third-party licenses are documented under `LICENSES/`.

## Attribution

Callsmith is developed and maintained by **Mirakinetix**.

Official website:

https://mirakinetix.com

For branding and attribution guidance, see [BRANDING.md](BRANDING.md).

## Development

Clone the repository and build with Maven:

```text
mvn -B verify
```

## Saved request workflow

Create a collection from the sidebar. Use **New Request** in the editor, enter a URL, and
choose **Save** to name the request and select a collection or folder. After reopening a
saved request, **Save** updates that request. **Save As** creates a separate copy.
Right-click collections or folders to create nested folders. Right-click saved requests
to rename, duplicate, move, or delete them. A folder must be empty before it can be deleted.
While sending, use **Cancel** to interrupt the local request.

To check the desktop workflow, use a disposable workspace and fictional data. Create a
collection and nested folder, save an example request, reopen and edit it, and confirm
that saving twice leaves one entry. Move it between folders, restart the app, then export
and re-import the collection. Use a local test endpoint for send and cancel checks.

## HTTP requests and responses

Callsmith supports GET, POST, PUT, PATCH, DELETE, HEAD and OPTIONS. Add headers and
query parameters in the request editor. To send repeated query names, put them in
the URL itself. Existing URL query values are preserved, and fragments stay local.

Open **Send Options** to change authentication, body format, or request timeout.

- Choose Basic, Bearer, or API key in a header or query parameter. Use a protected
  environment variable such as {{apiToken}} in the credential field. Basic uses
  UTF-8 credentials. Unlock Secret Storage before sending referenced secrets.
  An authentication helper rejects conflicting manually set credentials.
- Use the main **Body** field for raw JSON, text, or XML. These formats default to
  UTF-8. An explicit charset on a manual Content-Type is honored. For URL-encoded
  or multipart forms, clear the raw Body and enter one name=value per line in Send
  Options. Repeated fields are allowed. Remove any manually entered Content-Type
  when using structured forms so Callsmith can generate it.
- For multipart uploads, choose one local file using **Choose File**. Files are
  streamed and limited to 128 MiB. Reselect a missing or changed file. Callsmith
  keeps the selection in memory and never saves its path or contents in SQLite.
- Send Options apply to the current editor. Saved requests retain their existing
  raw URL, headers, parameters and body. Helpers, literal helper credentials,
  body-format selections, form fields, uploads, and timeout overrides must be
  configured again when reopening the request.
- The default is 30 seconds, configurable from 1 to 300 seconds. The connection
  timeout is 10 seconds. The request deadline also covers redirects and body
  download. **Cancel** asks the JDK to stop the exchange and prevents a late reply
  from replacing a newer one. The server may already have started processing it.

Callsmith follows up to five redirects within the same scheme, host and effective
port. It blocks cross-origin redirects, HTTPS downgrades, and loops. Status 4xx
and 5xx replies are shown as HTTP responses. Final URL display omits queries and
redirect paths. URL or query substitution can hide it completely.

The response pane shows raw text or formatted JSON. It can decode textual gzip
responses and honor response charset declarations. Binary data and unknown
encodings are not rendered as text. **Save Response Bytes** saves the received
body before any decompression or formatting to a new file. Size is measured from
those bytes, excluding HTTP framing. Incoming bodies and gzip expansion are
limited to 8 MiB. The text preview is limited to 256 Ki characters.

OAuth, saved authorization presets, multiple file parts, and downloads larger
than the response limit are outside the current workflow.

## Response assertions and collection runs

Save a request, select it, then open **Assertions**. Add or update rules and choose
**Save Assertions**. Rules stay with that request after a restart or move. **Save As**
and **Duplicate Request** create separate requests with their own rules. Select
**Send** to see each rule's pass or fail result in the response pane.

- HTTP status can equal one code or fall within an inclusive range.
- Header rules check existence or exact value using a case-insensitive header name.
- Text rules check whether the decoded body contains or excludes a substring.
- JSON rules check a property or compare a typed JSON value. Use a JSON Pointer
  such as /items/0/id. Escape a slash within a property name as ~1 and a tilde as
  ~0. Quote JSON string values. An existing null property counts as present.
- A duration rule requires the request to finish below a threshold in milliseconds.
- Use up to 32 rules per request. Binary responses fail text and JSON rules.
  Use non-secret literals or protected environment references for comparisons.
  Results omit headers, response text, selectors and comparison values.

Select a collection, choose **Run**, select an environment, and click **Start Run**
to execute its saved requests in tree order, including nested folders. Review
imported requests before starting. Runs can send state-changing methods and require
this confirmation every time. Runs use the saved method, URL, headers, query and
raw body. Configure any required credentials as protected environment references
in saved request fields. The runner uses the same redirects and response limits
as Send. Temporary **Send Options** are available for individual requests.

The runner allows one active collection run, 100 requests, 64 folder levels,
30 seconds per request and five minutes for execution. HTTP 2xx passes when every
assertion passes. A deliberate assertion can also accept a non-2xx status. A
network, HTTP or assertion failure is recorded and execution continues. Reports
show progress, request numbers, failures and totals. **Cancel Run** interrupts
the active request where possible and preserves completed results. The server may
already have processed a request. Run reports are kept in the open dialog.

## Collection import and export

| Format | Supported workflow |
| --- | --- |
| Postman Collection v2 JSON | Import and export nested folders, mixed folder and request order, methods, URLs, headers, query values and raw bodies. Callsmith exports can be imported again. |
| OpenAPI 3.x JSON | Import supported HTTP operations using their URLs, methods, supported parameters and a representative request body where available. |
| Insomnia legacy JSON with a resources array | Import supported requests and nested folders in resource order, including headers, query values and text bodies. |

Literal Callsmith-style variable references such as {{apiToken}} can be preserved
in supported request fields. Configure their values separately in the workspace.
Read the import warnings for unsupported auth settings, scripts, request bodies,
or environment and schema information. Imports never execute scripts or requests.
The editor stores one value per header or query name. Postman export includes
raw saved fields and omits assertion rules, scripts, environment data, and
resolved protected secrets. Check the exported fields for inline credentials
before sharing. YAML OpenAPI, newer Insomnia layouts, and full Postman
compatibility are outside the current import workflow.

## Platform verification

The Windows x64 app image is built with the Java 25 packaging script.
Native macOS builds target Intel and Apple Silicon and require verification on
each architecture before distributing those artifacts. See [RECOVERY.md](RECOVERY.md)
for the beta verification checklist.

To run the Windows JavaFX regression tests on a desktop, use this PowerShell
command from the checkout:

```powershell
mvn -B '-Dcallsmith.desktop.tests=true' '-Dtest=WorkspaceDesktopTest,RequestDesktopTest,IconDesktopTest' test
```

The tests create their own SQLite databases and loopback endpoints. The platform
file pickers, Windows taskbar scaling, and macOS native builds need separate
checks. See [BRANDING.md](BRANDING.md) for the shipped icon formats and provenance.
