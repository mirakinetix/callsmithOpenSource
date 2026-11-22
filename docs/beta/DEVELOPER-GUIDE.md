# Callsmith closed beta developer guide

Callsmith is Mirakinetix's local-first desktop tool for building HTTP requests,
organizing collections, and running basic response checks. This pilot evaluates
day-to-day API workflows on Windows x64. The internal smoke test used Windows 11 x64. Use an organization-approved Windows installation and a writable local folder. Keep using your existing tools for
business-critical API work.

## Install and launch

After your manager confirms organizational approval, obtain the full Windows
x64 ZIP through the approved private channel. Compare its SHA-256 with the
approved manifest using `Get-FileHash -Algorithm SHA256 -LiteralPath '<path to ZIP>'`.
Extract the entire archive into an approved local folder. Callsmith includes
its Java runtime. Launch `Callsmith\Callsmith.exe` with the disposable
profile steps in the [Windows acceptance checklist](../windows-beta-acceptance.md).
The same PowerShell session can relaunch that profile. Keep the extracted
files together. If policy, antivirus, or SmartScreen blocks launch, stop
and ask the manager or IT for an approved path.

## Your first collection

1. Start the [local HTTP fixture](../windows-beta-acceptance.md#setup) or use your
   manager's approved sandbox. Use fictional data.
2. In **Collections**, choose **New** and name the collection. Click **New
   Request**, set the method and URL, then add headers, query parameters and
   a body as needed. Send GET `http://127.0.0.1:8765/health` and confirm status
   200 and `health-ok`.
3. Choose **Save**, pick the destination, and name the request. Reopen it from
   the collection tree. Use **Save** to update it and **Save As** to make a
   separate copy. Right-click the tree to create folders, move or duplicate
   items. Review the destination before every send.

## Authentication and environments

- Open **Send Options** for Basic, Bearer, or an API key in a header or query
  parameter. Avoid query credentials if the API supports a header. Do not
  combine an auth helper with a conflicting manual credential.
- In **Environment → Manage**, create and activate a test environment.
  Add an ordinary variable such as `baseUrl` for the sandbox origin. Reference
  variables as `{{baseUrl}}` or `{{pilotToken}}`. A missing value stops
  that request from sending.
- In **Manage → Secret Storage**, choose **Set Up and Protect** on the
  disposable workspace. Use a unique fictional passphrase of at least
  16 characters and keep it separate. Mark a test variable as **Secret**,
  reference it in your request, and use **Unlock** before sending. Reopening
  the app locks the store. A lost passphrase cannot be recovered.
- **Send Options** are temporary. For a saved request or collection run, use
  a protected placeholder in a saved header such as
  `Authorization: Bearer {{pilotToken}}`, or in another supported saved field.
  Avoid placing literal credentials in saved fields, body text, or URLs.

## Verify responses and share collections safely

- Open **Assertions** on a saved request. Add a status, header, or JSON rule,
  then **Save Assertions**. Choose **Send** and inspect the rule results.
  JSON rules use JSON Pointer selectors such as `/ready`.
- Choose a collection and **Run**, select the environment, review all saved
  requests and choose **Start Run**. Requests run in tree order and can
  change server state. **Cancel Run** stops subsequent work where possible
  and keeps completed results. A server may already have processed a request.
- Import Postman Collection v2 JSON, supported OpenAPI 3.x JSON, or older
  Insomnia JSON with a `resources` array. Read import warnings. Export to
  Postman JSON using a writable local NTFS folder. Review exports for inline
  credentials before sharing.

See the [known limitations](KNOWN-LIMITATIONS.md) and
[test scenarios](TEST-SCENARIOS.md). Report issues
through the private channel supplied by your manager. Keep production
credentials, tokens, passphrases, confidential response data and workspace
databases out of bug reports. [Feedback form](FEEDBACK-TEMPLATE.md)
