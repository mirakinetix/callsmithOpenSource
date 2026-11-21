# Callsmith security

## Workspace Secret Storage

In **Manage Environments → Secret Storage**, choose and confirm a strong, unique
workspace passphrase of at least 16 characters. Store it separately from the
database, for example in a password manager. Every fresh session starts locked.
Unlock here before editing a secret or sending a request that references it. The
same control can lock the workspace again. Ordinary variables remain available.
Secret values stay masked in the variable lists and in the edit dialog. To avoid
accidental disclosure, an existing secret cannot be changed to an ordinary variable.

Callsmith derives a 256-bit AES key using JDK PBKDF2-HMAC-SHA-256 with 600,000
iterations and a random 256-bit workspace salt. It uses AES-GCM with a fresh random
96-bit nonce and a 128-bit tag per encryption. Authenticated data binds the format
and purpose, and binds each secret to its variable ID, environment ID, and name.
SQLite stores the version, salt, authenticated verifier, and ciphertext envelopes.
The passphrase is entered in the application. The derived key is retained in memory
while unlocked. There is no plaintext fallback or passphrase reset.

## Migration from the prior schema

1. Before opening an older workspace with this build, close Callsmith and make a
   consistent backup. Keep it somewhere private. It can contain plaintext secrets.
2. Open a disposable copy first. Schema upgrades add encryption metadata and leave
   legacy values intact. The new app lists legacy secret names but blocks using their values.
3. To migrate, open **Manage Environments → Secret Storage** and select **Set Up and
   Protect** after confirming the backup. This action encrypts legacy secret values.
   Verify that referenced variables resolve and that the same passphrase works after
   a restart before retiring the old backup.
4. Keep old binaries away from the upgraded database. The old value column was
   renamed to block legacy environment variable reads and writes. Older builds can
   still change other workspace data.

Setup checks encryption and verifies the written envelopes before committing. Row
changes and key metadata share a SQLite transaction. Failed setup leaves the legacy
values available for retry from the same workspace or its backup. If setup reports
a disk or file error, verify that the workspace volume is writable and has space,
then retry. An interrupted transaction is recovered by SQLite when reopened. Keep
the passphrase you chose if the application closed during setup, since the commit
may have completed before the UI reported success.

## Backup and restore

Use a trusted SQLite snapshot tool. SQLite's [VACUUM INTO](https://www.sqlite.org/lang_vacuum.html)
creates a consistent, compact snapshot at a new path and excludes SQLite free pages
from the copy. Do not copy a live database file by itself: it may have uncheckpointed
data in its WAL. Verify the snapshot's integrity and unlock a separate restored copy
before relying on it. Never restore directly onto your only working copy.

The snapshot and its original passphrase can restore protected variables in a
compatible build under the same account, under a different account, on another
machine, or across Windows and macOS. There is no OS credential dependency.
Windows was tested. Native macOS builds, both Intel and Apple Silicon, still need
platform verification. Losing the passphrase means losing access to the encrypted
values. Keep the passphrase separately and keep backups from known, trusted sources.

A post-migration snapshot contains encrypted secret values but other workspace data
is still readable. The whole SQLite file is not authenticated. GCM detects damaged
or altered secret envelopes and row swaps, but it cannot prevent data deletion,
rollback to a valid old backup, or changes to ordinary SQLite content.

## Scope and remaining risks

- Database theft and access by another local account are mitigated for protected
  values when the passphrase is strong and kept separately. An attacker can guess
  passphrases offline. Use a long, unique passphrase and restrict access to both
  the database and backups.
- Migration cannot erase legacy backups, filesystem snapshots, old WAL or journal
  bytes, or storage-device remnants. Treat previous copies as sensitive. Consider
  rotating credentials that may have been exposed.
- Unlocking and resolving variables puts secret values in JVM memory. A debugger,
  heap dump, process compromise, or another person using the unlocked app may
  expose them. The app clears temporary buffers where practical, but Java strings
  and UI field contents cannot be reliably erased in memory.
- Callsmith hides expanded URLs in the response inspector when URL or query
  substitution changes the request. Request/response record diagnostics omit
  URLs, headers and bodies. Servers can still echo secrets in responses, proxy
  logs, or redirects. Review responses before sharing them and avoid embedding
  secrets in URLs.
- Saved request headers, URLs, and bodies, imported files, and collection exports
  are outside the protected storage boundary. Use references such as
  `{{apiToken}}` in saved requests. Collection exports include raw request
  fields and can contain inline credentials.

## HTTP redirect and response boundaries

Redirects are followed only within the same scheme, host and port. Redirect
locations are not printed in failures or shown verbatim in response headers.
Credentials for send helpers and selected upload files are session data. Existing
inline credentials in saved request fields and exported collections remain
outside Secret Storage. A server can still return supplied credentials in its
headers or body. Treat response data and saved response files as sensitive.
