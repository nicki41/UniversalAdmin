# 0012 - Escaped Message Arguments and Layered Language Files

## Status

Accepted (1.0.0)

## Context

Messages are MiniMessage templates with `{0}`-style arguments. Many
arguments are typed by people - ban reasons, report texts, staff chat, note
texts, filter input. Substituted verbatim, a reason like
`<click:run_command:/op me>` would become a clickable command in a message
another staff member sees. Separately, `lang/*.yml` is copied to the data
folder once; keys added in later versions were missing there and rendered
as `[missing: ...]` after an upgrade, and a single YAML typo in a language
file aborted plugin startup.

## Decision

- `MessageService#get` MiniMessage-escapes every `String` argument.
  Arguments that are trusted markup must be wrapped in
  `MessageService.markup(...)` explicitly. Numbers and other types are
  passed through.
- Language resolution is layered: the server owner's
  `plugins/UniversalAdmin/lang/<locale>.yml` overrides the copy bundled in
  the jar (plus defaults registered by extensions), then `en_US` in the same
  order, then a visible marker. A broken override file is logged and
  skipped. `/admin reload` re-reads the override files. (Since 1.7.1 the
  override files hold only the owner's texts - [ADR 0026](0026-language-files-hold-only-overrides.md).)

## Consequences

- Player-supplied text can never inject formatting or events, in any module
  or extension, without each call site having to remember it.
- Upgrades never lose texts; server owners only need to keep the keys they
  changed.
- Code that resolves a message and shows it with `Component.text(...)`
  instead of `ComponentMessages.render(...)` would show escape characters -
  render resolved messages as MiniMessage.

## Alternatives

- **Escaping at each call site.** Rejected: one forgotten call is a
  security hole.
- **MiniMessage tag resolvers instead of `MessageFormat`.** Cleaner in
  theory, but would have changed every template and every call site; the
  escaping keeps both and gives the same protection.
