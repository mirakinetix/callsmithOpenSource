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
