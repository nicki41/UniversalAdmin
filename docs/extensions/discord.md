# UniversalAdmin Discord

The official Discord extension, in two modes:

| | Webhook mode (default) | Bot mode |
|---|---|---|
| Admin actions as embeds in a channel | yes | yes |
| Staff chat in Discord, **both ways** | - | yes |
| `/ban`, `/mute`, `/kick`, `/warn`, `/unban`, `/unmute`, `/history`, `/check`, `/find` and `/ua <any admin command>` **from Discord** | - | yes |
| New reports with **Claim / Resolve / Reject** buttons | - | yes |
| Setup | a webhook URL | a bot token, a few ids |

Download `universaladmin-discord-<version>.jar` from the
[GitHub release](https://github.com/universaladmin-mc/UniversalAdmin/releases) (same
version as UniversalAdmin) into `plugins/` and restart. On the first start
Paper downloads the Discord library (JDA, about 20 MB) once into the
server's `libraries` folder - the server needs internet access for that.

## Webhook mode

1. In Discord: **Server Settings → Integrations → Webhooks → New Webhook**,
   choose a channel (a staff-only one is a good idea), **Copy Webhook URL**.
2. Paste it into `plugins/UniversalAdminDiscord/config.yml` as `webhook-url`.
3. `/admin discord reload`, then `/admin discord test` - a green "connected"
   message should appear in the channel.

## Bot mode

1. **Create the bot:** [Discord Developer Portal](https://discord.com/developers/applications)
   → **New Application** (name it e.g. "UniversalAdmin") → **Bot** →
   **Reset Token**, copy the token. On the same page switch on
   **Message Content Intent** (needed to read the staff chat channel).
2. **Invite it:** **OAuth2 → URL Generator**, scopes `bot` and
   `applications.commands`, bot permissions *View Channels*, *Send
   Messages*, *Embed Links*, *Read Message History*, *Add Reactions*. Open
   the generated URL and add the bot to your Discord server.
3. **Collect the ids:** in Discord, **User Settings → Advanced → Developer
   Mode** on. Then right-click your server icon → *Copy Server ID*, and
   right-click the channels and roles you want → *Copy ... ID*.
4. **Configure** `plugins/UniversalAdminDiscord/config.yml`:

   ```yaml
   mode: bot
   bot:
     token: "the token from step 1"
     guild-id: "123456789012345678"
     channels:
       log: "234567890123456789"         # admin actions
       staff-chat: "345678901234567890"  # two-way staff chat
       reports: "456789012345678901"     # reports with buttons
     roles:
       "567890123456789012":             # Admin
         - "universaladmin.*"
       "678901234567890123":             # Moderator
         - "universaladmin.moderation.*"
         - "universaladmin.reports.*"
         - "universaladmin.chat.staff"
     activity: "{online} players online"
   ```

   Leave a channel empty to switch that feature off.
5. `/admin discord reload` → `/admin discord status` should say *online as
   ... on ...*, and `/admin discord test` posts a test message. The slash
   commands appear in your Discord server right away.

### Who may do what

Discord members act through their **roles**: `bot.roles` maps a role id to
UniversalAdmin permission nodes (`*` = everything, `universaladmin.moderation.*`
= a node and everything below it). Every slash command, button and staff
chat message is checked against those nodes exactly like in game - a
moderator role with `universaladmin.moderation.mute` but not
`universaladmin.moderation.ban` can `/mute` but not `/ban`. Members without
a listed role can do nothing. In the audit log and in game they appear as
`Discord:<name>`.

### Slash commands

| Command | Runs | Needs |
|---|---|---|
| `/ban player [duration] [reason]` | `/admin ban` - with a duration a temp-ban | `moderation.ban` / `moderation.tempban` |
| `/mute player [duration] [reason]` | `/admin mute` | `moderation.mute` / `moderation.tempmute` |
| `/kick player [reason]` | `/admin kick` | `moderation.kick` |
| `/warn player reason` | `/admin warn` | `moderation.warn` |
| `/punish player template [note]` | `/admin punish` - the next step of a [punishment template](../user/modules/moderation.md#punishment-templates); templates are completed from the server | `moderation.punish` + the step's own node |
| `/unban player`, `/unmute player` | `/admin unban`, `/admin unmute` | `moderation.unban`, `moderation.unmute` |
| `/history player`, `/check player` | punishment history, current status | `moderation.view` |
| `/find player` | which server a player is on (network mode) | `network.view` |
| `/ua command` | any `/admin` command, e.g. `notes Steve add griefer alt` | that command's permission |

Player names are completed from the server. Answers are only visible to
the member who asked. Commands that need a player in game (teleports, the
menus) answer that they are for players only.

### Staff chat

What staff write in game (`/sc`, `#message`) appears in the staff chat
channel; what members with `universaladmin.chat.staff` write in that
channel appears in game as `[Staff] [Discord] Nick: ...`. Messages from
members without that permission get a 🚫 reaction and are not forwarded.
In network mode, staff chat from every server reaches Discord, tagged with
the server.

### Reports

Every new report appears in the reports channel with **Claim**,
**Resolve** and **Reject** buttons (needs `universaladmin.reports.manage`);
Resolve and Reject ask for an optional note. The card shows who is handling
the report and is closed when the report is - also when that happens in
game.

### Networks

Run the bot on **one** server of the network (one bot token, one
connection). Staff chat and reports from all servers reach it; commands run
on that server and take effect network-wide like any other punishment. Admin
actions of the other servers can go to the same log channel through webhook
mode.

## What gets posted

`events` (patterns, `*` = anything) minus `exclude` - for the webhook and
for the bot's log channel:

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

- **No pings:** mentions are always disabled, so a reason or staff chat
  message containing `@everyone` can never ping anybody. Text from the game
  is escaped, so it cannot format Discord messages either.
- **Secrets stay secret:** the webhook URL and the bot token are never
  logged or shown in game; only `https://discord.com/api/webhooks/...` URLs
  are accepted.
- **Everything is checked and audited:** the bot has no powers of its own -
  it runs commands as the Discord member, with the permissions their roles
  grant.
- **No lag:** Discord traffic runs on background threads. If Discord is
  down, messages are dropped with a warning in the console - the server is
  never affected.

## Commands

| Command | Permission | |
|---|---|---|
| `/admin discord status` | `universaladmin.discord.manage` | Webhook: configured, which events. Bot: connected? |
| `/admin discord test` | same | Send a test message |
| `/admin discord reload` | same | Re-read `config.yml` (the bot reconnects) |
