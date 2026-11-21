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
- JUnit 5
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
mvn clean test
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

To run the Windows JavaFX regression tests on a desktop, use this PowerShell
command from the checkout:

```powershell
mvn -B '-Dcallsmith.desktop.tests=true' '-Dtest=WorkspaceDesktopTest,RequestDesktopTest,IconDesktopTest' test
```

The tests create their own SQLite databases and loopback endpoints. The platform
file pickers, Windows taskbar scaling, and macOS native builds need separate
checks. See [BRANDING.md](BRANDING.md) for the shipped icon formats and provenance.
