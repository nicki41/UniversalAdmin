# UniversalAdmin Discord

The official Discord extension posts what your staff does - bans, mutes,
warnings, reports, restarts, maintenance, whitelist changes - into a Discord
channel, as tidy embeds. No bot account needed, just a webhook.

## Install

1. Download `universaladmin-discord-<version>.jar` from the
   [GitHub release](https://github.com/nicki41/UniversalAdmin/releases)
   (same version as UniversalAdmin) into `plugins/` and restart.
2. In Discord: **Server Settings → Integrations → Webhooks → New Webhook**,
   choose a channel (a staff-only one is a good idea), **Copy Webhook URL**.
3. Paste it into `plugins/UniversalAdminDiscord/config.yml` as `webhook-url`.
4. `/admin discord reload`, then `/admin discord test` - a green "connected"
   message should appear in the channel.

## What gets posted

`events` (patterns, `*` = anything) minus `exclude`:

```yaml
events:
  - "core:moderation.*"
  - "core:reports.*"
  - "core:server.shutdown"
  - "core:server.restart"
  - "core:server.maintenance.*"
  - "core:whitelist.*"
  - "core:chat.lock"
  - "core:chat.unlock"
  - "core:chat.clear"
exclude:
  - "core:moderation.notes.*"
  - "core:moderation.vanish"
  - "core:moderation.godmode"
  - "core:moderation.no-collision"
  - "core:moderation.staffmode.*"
  - "core:moderation.freeze.disconnect-notice"
```

Event types are the audit log's types - `/admin audit` shows them. Extension
events work the same way (e.g. `"tickets:*"`). Failed/denied attempts are
skipped unless `include-failures: true`.

Each message shows the action, the summary, the staff member, the player,
the reason and the server name (`server-name`) - useful when several servers
share one channel.

## Safety

- **No pings:** mentions are always disabled, so a reason containing
  `@everyone` can never ping anybody.
- **The webhook URL is a secret** - it is never logged or shown in-game;
  only `https://discord.com/api/webhooks/...` URLs are accepted.
- **No lag:** messages are sent one by one on a background thread, inside
  Discord's rate limit. If Discord is down, messages are dropped with a
  warning in the console - the server is never affected.

## Commands

| Command | Permission | |
|---|---|---|
| `/admin discord status` | `universaladmin.discord.manage` | Configured? Which events? |
| `/admin discord test` | same | Send a test message |
| `/admin discord reload` | same | Re-read `config.yml` |
