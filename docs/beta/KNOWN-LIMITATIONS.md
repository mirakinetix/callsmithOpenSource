# Known limitations for the Callsmith beta

- **Scope.** This evaluation covers the Windows x64 RC2 application image.
  Keep your existing tools in use for business-critical API work.
- **Installation and trust.** RC2 is unsigned. Windows SmartScreen,
  endpoint protection, or organization policy may warn or prevent launch.
  The coordinating manager must obtain company permission before anyone
  installs it. Follow IT instructions and stop if launch is blocked.
- **Compatibility.** Callsmith imports supported Postman Collection v2 JSON,
  OpenAPI 3.x JSON, and legacy Insomnia JSON with a `resources` array. It
  exports Postman Collection v2 JSON. Postman scripts do not run or migrate
  as executable behavior. OpenAPI YAML and newer Insomnia layouts are outside
  supported formats. Read warnings and inspect requests before sending.
- **Exports.** Use a writable local NTFS folder. Safe publication uses hard
  links, and confirmed replacement also uses atomic moves. Some removable
  drives and network shares lack those capabilities. JSON exports omit rules,
  environment data, and resolved secrets. Inline saved fields can still
  contain sensitive values.
- **HTTP and auth.** OAuth and saved auth presets are outside the current
  workflow. Send Options and the single multipart file selection are
  temporary. The runner uses saved request fields and the selected
  environment. Redirects stay within the same scheme, host, and port.
  Incoming response bodies and decompression are limited to 8 MiB.
  Response byte saves require a new destination.
- **Local data.** Protected Secret Storage covers environment variables
  marked Secret. Saved URLs, headers, bodies, response files and databases
  can still contain sensitive data. Keep the pilot workspace disposable.
- **Support.** Report problems through the manager's approved private
  channel. Route a suspected credential exposure, unexpected request, or
  data loss directly to the manager and Mirakinetix pilot contact.
