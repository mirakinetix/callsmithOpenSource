# Callsmith v1.0.0-beta.1

**Proposed closed Windows x64 beta.** These notes describe application image
RC2, which reports version `1.0.0` in Windows metadata. The prepared filename
`Callsmith-1.0.0-beta.1-windows-x64.zip` is a byte-identical copy of RC2.
Use fictional data and local or
approved non-production APIs during the evaluation.

## Changes included

- HTTP requests have configurable deadlines, cancellation, raw byte saves,
  and JSON formatting. Redirect handling limits hops and keeps the request
  on the same origin. Missing environment variables stop dispatch.
- Secret Storage protects environment values marked Secret with a workspace
  passphrase. Secret lists stay masked and each fresh session starts locked.
- Saved requests support editing, moving, duplicating, and organizing in
  nested folders. Native Windows artwork and dialog ownership were improved.
- Saved response assertions cover status, headers, text, JSON and timing.
  The collection runner executes saved requests in order and reports results.
  Send and Run share one execution slot through cancellation cleanup.
- Postman Collection v2 JSON import and export preserve supported saved
  requests and order. OpenAPI 3.x JSON and legacy Insomnia JSON have supported
  import paths. Export confirms replacement and uses safe filesystem operations.
- The Windows image includes its Java runtime and third-party notices.

## Limits for this pilot

The unsigned Windows x64 image may be subject to SmartScreen or company
endpoint policy. Follow the approved installation process. Postman scripting,
OAuth, OpenAPI YAML, and newer Insomnia layouts are outside this pilot.
Temporary Send Options do not persist in saved requests or collection runs.
Use a writable local NTFS folder for collection exports. Keep using established
tools for business-critical API work. [Known limitations](KNOWN-LIMITATIONS.md)

Built from recovery commit `a62a610dd864e4dac614c313ec604a664852809c`.
The verified main merge has the same product tree. [Merged recovery history](https://github.com/mirakinetix/callsmithOpenSource/pull/2)
· [Private candidate manifest](PACKAGE-MANIFEST.md) · [Developer guide](DEVELOPER-GUIDE.md)
