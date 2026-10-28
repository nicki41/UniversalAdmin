# 0026 - Language Files Hold Only the Server Owner's Texts

## Status

Accepted (1.7.1). Refines [ADR 0012](0012-safe-messages.md).

## Context

Since ADR 0012 the data folder's `lang/<locale>.yml` overrides the copy in
the jar, key by key. But on the first start the *whole* bundled file was
copied there and never touched again. Every key was therefore an override:
a text added in a later version showed up (it was not in the copy), but a
text that was fixed or reworded never reached a server that had been
installed before - the old copy kept shadowing it, typos and outdated
instructions included. Servers only got the new texts by deleting their
`lang/` folder, losing their own changes with it.

## Decision

- **`lang/<locale>.yml` holds only the server owner's own texts.** A new
  installation gets a file with an explanation and no keys. Every text not in
  it comes from the plugin and follows its updates.
- **`lang/reference/<locale>.yml`** is every built-in text of the running
  version, rewritten on every start - to look up keys and copy them, not to
  edit. It is not read as an override (only files directly in `lang/` are).
- **Old full copies are cleaned once.** A file that does not start with the
  overrides header is read, and every text that equals a default the key has
  ever had in a release is dropped; what remains are the owner's changes.
  Those defaults are stored as `lang-history/<locale>.txt` in the jar - per
  key, the first 16 hex digits of the SHA-256 of every text it had in any
  commit of the language files - generated once from the repository history.
  The original file is kept as `lang/<locale>.yml.before-1.7.1`. A cleaned
  file starts with the header and is never cleaned again.

## Consequences

- Text fixes reach every server with the next update.
- The history never needs to grow: only copies made by 1.7.0 or older are
  full copies, and their texts are all in it. Servers that skip 1.7.1 and
  update later are cleaned by the same history.
- A server owner who deliberately kept an old default text loses it to the
  new one (it is indistinguishable from an untouched copy); the backup has
  it.
- Extensions are not affected: they never wrote language files.

## Alternatives

- **Write the copy again on every update, merging the owner's changes in.**
  Needs to know which keys the owner changed - the same history problem, and
  a file the plugin rewrites is a file the owner cannot safely edit.
- **Store a version marker and only replace keys that still have the
  previous version's text.** Works only for owners who update every
  version; skipping one leaves the older texts behind.
