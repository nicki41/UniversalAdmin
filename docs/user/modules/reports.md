# Reports

Players report rule-breakers with **`/report <player> <reason>`**; staff get
a clickable notification right away and handle reports in a GUI. No
external plugin needed.

## For players

```
/report Griefer123 broke my house near spawn
```

- Everyone may report (`universaladmin.reports.create`, granted to all by
  default - revoke it to disable reporting for a group).
- You cannot report yourself, and reports are rate-limited: one every
  `reports.cooldown` (default 60s) and at most `reports.max-open-per-player`
  (default 3) open at a time. Staff with `universaladmin.reports.bypass-limits`
  are exempt.
- The reason needs at least `reports.min-reason-length` characters (default 3).
- When staff close your report you get a short thank-you message
  (`reports.notify-reporter`).

UniversalAdmin stores where the reporter stood, so staff can teleport to the
scene later.

## For staff

- **Notification:** everyone with `universaladmin.reports.view` sees
  `[Report #12] Alice reported Bob: ... [View]` plus a short sound
  (`reports.notify-sound`). Click **[View]** to open the report.
- **On join:** staff are reminded how many reports are open
  (`reports.remind-staff-on-join`).
- **GUI:** `/admin` → **Reports** - open reports by default, the hopper in
  the top row switches to all recent ones. A report shows reason, reporter,
  time, status, location and staff note.
- **Handling** (`universaladmin.reports.manage`):
  - **Claim** - "I'll handle this" (others see who is on it),
  - **Resolve** / **Reject** - with an optional note,
  - **Teleport to the reported player** (if online) or **to the report location**,
  - every profile button for the reported player (Moderate, Staff notes, ...)
    is right on the report page.
- **Profile:** every player profile has a **Reports** button listing all
  reports about that player.
- **Chat/console:** `/admin reports [open|all|view <id>|claim <id>|resolve <id> [note]|reject <id> [note]]`.

Every report and every status change is audited, so the audit log shows who
handled what.

## Settings

| Setting | Default | Meaning |
|---|---|---|
| `reports.cooldown` | `60s` | Time between two reports of the same player (`0s` = off) |
| `reports.max-open-per-player` | `3` | Open reports per reporter (`0` = unlimited) |
| `reports.min-reason-length` | `3` | Minimum reason length |
| `reports.notify-sound` | `true` | Sound for staff on a new report |
| `reports.remind-staff-on-join` | `true` | Open-report reminder on join |
| `reports.notify-reporter` | `true` | Tell the reporter when the report was closed |
| `modules.reports` | `true` | Switch the whole module off (restart) |

## Permissions

| Node | Default | Meaning |
|---|---|---|
| `universaladmin.reports.create` | everyone | `/report` |
| `universaladmin.reports.view` | op | See reports, notifications, reminder |
| `universaladmin.reports.manage` | op | Claim/resolve/reject, teleports |
| `universaladmin.reports.bypass-limits` | op | No cooldown/limit |

## Database

`ua_reports` (migration 1009): reporter, target, reason, status
(`OPEN`/`CLAIMED`/`RESOLVED`/`REJECTED`), handler, note, location,
timestamps. The cooldown itself is kept in memory and resets on restart.
