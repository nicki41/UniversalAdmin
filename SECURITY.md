# Security Policy

## Supported Versions

UniversalAdmin is in its alpha phase. Security fixes go into the latest
version; there are no backports to older alpha releases.

| Version | Supported |
|---|---|
| latest release / `main` | yes |
| older alpha releases | no |

## Reporting a Vulnerability

Please do **not** report it as a public issue.

Preferred path: **GitHub Private Vulnerability Reporting** via the
["Security" → "Report a vulnerability"](https://github.com/nicki41/UniversalAdmin/security/advisories/new)
tab on this repository. The report is then visible only to the maintainers.

If that isn't available to you, reach out to the maintainers through one of
the contact paths listed on the repository owner's GitHub profile. No email
address is invented here that doesn't exist.

Please include:

- Affected version or commit
- Reproduction steps
- Expected vs. actual behavior
- Potential impact (e.g. privilege escalation, data loss, RCE)

**Please don't paste credentials, tokens, passwords, or server logs
containing personal data into the report** - a description is enough.

There is no bug bounty program.

## Security-Relevant Design Decisions

These rules are set deliberately, see also the
[development rules](docs/development/architecture-rules.md):

- **No secrets in logs.** Database passwords and future API tokens are never
  logged, not even at debug level. `DatabaseConfig#toString()` explicitly
  redacts the password.
- **No unsafe packet hacks in the core.** UniversalAdmin doesn't touch the
  network protocol (no ProtocolLib, no raw packet injection) - that reduces
  the attack surface and keeps the core compatible with internal server
  changes.
- **Permissions instead of hard op checks.** Every protected action has its
  own `PermissionNode` (see
  [docs/user/permissions.md](docs/user/permissions.md)), so server operators
  can grant access granularly instead of blanket op. Checked centrally in
  `ActionExecutor`, not scattered across the frontend.
- **SQL injection:** exclusively `PreparedStatement` with bound parameters in
  repository implementations - see
  [docs/architecture/storage.md](docs/architecture/storage.md). No string
  concatenation of user input into SQL.
- **Database credentials** live in `config.yml` in the plugin folder (the
  server's own filesystem permissions are the protection for that, same as
  for any other Paper plugin). There is currently no encrypted secret store;
  that's a known limitation, not an oversight.
- **Database connection failures never log the password.**
  `StorageService`/`DataSourceFactory` never pass `DatabaseConfig.password()`
  to a logger - not even in the exception raised when a connection attempt
  fails at startup. See
  [docs/architecture/storage.md#health](docs/architecture/storage.md#health).
- **Audit trail.** Every mutating action automatically produces an
  `AuditEvent` through `ActionExecutor` - no module can bypass that without
  breaking the architecture rules. See
  [docs/user/audit-log.md](docs/user/audit-log.md).

## Telemetry and Privacy

UniversalAdmin includes anonymous usage statistics. Fully documented - every
single field, the interval, the opt-out, and everything explicitly not
collected - in [docs/user/telemetry.md](docs/user/telemetry.md).

Security-relevant highlights:

- **Nothing is sent by default.** No endpoint is preconfigured and there's no
  built-in fallback host. Without a configured `telemetry.endpoint`, no
  request is made, no installation id is generated, and no timer starts.
- **No identifier that traces back to the host.** The installation id is 128
  random bits from `SecureRandom` - not derived from IP, MAC address,
  hardware, hostname, server address, or file path.
- **No personal player data.** Only counts are transmitted; never names,
  UUIDs, IP addresses, chat, or commands.
- **The channel is one-way.** Endpoint responses are discarded, never
  parsed, never acted on; redirects aren't followed. A compromised or
  misconfigured endpoint can't instruct the server through this channel.
- **Full opt-out** via `telemetry.enabled: false`, effective without a
  restart after `/admin reload`.
- **No privacy policy yet.** No claim of GDPR or other compliance is made
  here. Before a real endpoint goes live, that has to be reviewed separately.

## Dependencies

The core deliberately keeps a small dependency list (see
`build.gradle.kts`) to keep the third-party attack surface small: two JDBC
drivers and a connection pool at runtime, nothing else. Updates are
deliberately reviewed and applied manually; there are intentionally no
automated dependency-update pull requests.
