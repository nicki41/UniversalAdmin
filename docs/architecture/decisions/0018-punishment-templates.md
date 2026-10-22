# 0018 - Punishment Templates with Escalation

## Status

Accepted (1.3.0)

## Context

Staff teams want the same offence to be punished the same way, and repeat
offences harder: spam earns a warning first, then an hour's mute, then a
day's. Doing that by hand means looking up the history and agreeing on
durations. The punishment actions (warn, kick, mute, temp-mute, ban,
temp-ban) already carry every rule - permissions, the moderation policy,
enforcement, the network message, staff notifications and the audit
entry.

## Decision

- **Templates live in their own file**, `punishment-templates.yml`, not in
  `config.yml`: a template is a list of steps under a free-chosen name,
  which the typed settings system does not model. The file is written
  once from the jar in the configured language and re-read on
  `/admin reload` (the template service listens for the reload action's
  event). A broken template is skipped with a log line; a file that is not
  YAML at all keeps the templates loaded before.
- **A step is one of the existing punishments** (`warn`, `kick`,
  `mute [duration]`, `ban [duration]`). No IP bans or freezes - an IP ban
  needs the player online here, a freeze is not a punishment that
  escalates.
- **`moderation.punish` delegates**: it works out the step and runs the
  existing action for it through the `ActionExecutor` with the same actor.
  So the step's own permission applies (no template can do what its user
  may not do directly), and enforcement, network, notifications and audit
  are those of the action; the template action itself is not audited.
- **The punishment row remembers its template** - a nullable
  `punishments.template` column (migration 1013) with an index on
  `(target_id, template)`, not the metadata JSON: escalation needs to query
  it, and history should show it.
- **Escalation counts** earlier punishments of the target with the same
  template inside the template's window (default 90 days, `0` = forever).
  Lifted bans and mutes count - they happened; removed warnings do not -
  removing a warning is how a mistake is taken back. After the last step
  the last step repeats.

## Consequences

- One `/punish` command, one GUI button and one Discord slash command;
  everything else about a punishment is unchanged.
- A template whose step the moderator may not issue is refused with the
  template's name and the step, instead of a bare "no permission".
- The file is per server; the punishments that count are shared in a
  network, so servers with different files escalate differently - the
  documentation says to use the same file everywhere.
- Two moderators punishing the same player in the same second may both get
  the same step; accepted.

## Alternatives

- **Templates as a new kind of punishment** with their own enforcement.
  Rejected: a second path for every rule the actions already enforce.
- **Counting all punishments of a type** (every earlier mute) instead of
  the template's own. Rejected by the project owner: offences of a
  different kind should not escalate each other.
- **Templates in `config.yml`** under fixed keys. Rejected: free names and
  step lists do not fit typed settings, and the in-game settings editor
  could not edit them anyway.
