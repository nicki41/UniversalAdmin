# Settings

Every UniversalAdmin setting - core, every module, and every extension -
can be changed in-game. No more hunting through `config.yml`.

## In-game

`/admin` → **Settings** (`universaladmin.settings.manage`) shows one button
per section (`moderation`, `reports`, `chat`, `telemetry`, ...). Inside a
section every setting shows its current value, default and description:

- **Booleans** toggle on click.
- **Choices** (e.g. the database type) open a list of valid values.
- **Everything else** opens a text prompt pre-filled with the current value
  (lists are comma-separated: `30m, 1h, 1d`).
- **Right-click** resets a setting to its default.

Invalid input is rejected with the reason ("must be between 1 and 100") -
nothing is written then.

## Chat and console

```
/admin settings list                       # every section
/admin settings list moderation            # every setting in a section
/admin settings get reports.cooldown
/admin settings set reports.cooldown 2m
/admin settings reset reports.cooldown
```

Tab completion knows every setting path and, for booleans and choices,
every valid value.

## What happens on a change

- The new value is written to `config.yml` - **comments and every other
  key are kept** - and applied immediately.
- Settings marked **"requires a restart"** (database connection, module
  toggles, timers) are saved right away but only take effect after the next
  restart; the GUI and the command say so.
- **Sensitive** settings (the database password) are never shown and can
  only be changed in the file.
- Every change is recorded in the audit log with the old and new value.

Editing `config.yml` by hand and running `/admin reload` works just as
before.
