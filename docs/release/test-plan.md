# Manual Test Plan

What the automated tests cannot cover: a real Paper server. Run this before
every minor/major release; for a patch release, run the sections the change
touches plus "Smoke test".

## Setup

- Paper 26.2, Java 25, a fresh `plugins/` folder.
- Two accounts: **Admin** (op) and **Player** (no op, no permissions). A
  third account (**Helper**) for permission tests is ideal; otherwise
  de-op the second account and grant nodes with LuckPerms.
- LuckPerms installed for the permission tests; EssentialsX for the
  shortcut-clash test (separate run).
- Build: `./gradlew clean build`, then copy
  `build/libs/universaladmin-core-1.0.0.jar` and
  `extensions/discord/build/libs/universaladmin-discord-1.0.0.jar`.

Legend: **[A]** as Admin, **[P]** as Player, **[H]** as Helper, **[C]** in
the console.

## 1. Smoke test

- [ ] Server starts without a stack trace; the startup banner lists 10 modules.
- [ ] `plugins/UniversalAdmin/` contains `config.yml` (config-version 2),
      `lang/en_US.yml`, `lang/de_DE.yml`, `data.db`.
- [ ] [C] `/admin` prints the status report; `/admin modules` lists all
      modules as enabled.
- [ ] [A] `/admin` opens the main menu with 10 buttons in this order:
      Players, Moderation, Reports, Server, Chat, Worlds, Whitelist,
      Performance, Audit Log, Settings.
- [ ] [P] `/admin` → "no permission" (or the help with nothing in it).
- [ ] Restart the server - no errors, nothing duplicated.

## 2. Upgrade from 0.1.0-alpha.5

On a copy of a server that ran the alpha (or: run alpha.5, change a few
settings, issue some bans, then upgrade):

- [ ] Starts cleanly; log shows "Applying config migration 2".
- [ ] `config.yml`: `vanish:`, `freeze:`, `reasons:`, `countdown:` moved
      under `moderation:` / `server:`; your changed values survived;
      `commands:` and `modules.reports/chat` were added; comments intact.
- [ ] Old bans/mutes/warnings still in `/history`; still enforced on join/chat.
- [ ] The old `lang/*.yml` files are kept and new texts (e.g. `/admin help`)
      still show properly - no `[missing: ...]` anywhere.

## 3. Commands and shortcuts

- [ ] [A] `/admin help` lists commands, clickable (puts the command into the
      chat line), page 2 works.
- [ ] [H] with only kick+mute rights: `/admin help` shows only those.
- [ ] Tab completion: `/admin <tab>`, `/admin ban <tab>` (player names,
      vanished players hidden for non-staff), `/ban Steve <tab>` (duration and
      reason presets), `/admin world <tab>`, `/admin settings set <tab>`.
- [ ] Shortcuts work: `/kick`, `/ban`, `/vanish`, `/v`, `/heal`, `/gm c`,
      `/history`, `/report`, `/sc`, `/lag`, `/wl`.
- [ ] Tab completion on shortcuts (`/ban <tab>`, `/ban Steve <tab>`).
- [ ] `/universaladmin:ban` works.
- [ ] `commands.shortcuts.disabled: [ban]` + restart → `/ban` is no longer
      ours (vanilla/Essentials), `/admin ban` still works.
- [ ] `commands.shortcuts.enabled: false` + restart → only `/report` and
      `/staffchat`/`/sc` remain as shortcuts.
- [ ] **With EssentialsX installed:** which `/ban`, `/kick`, `/heal` wins?
      Note it for the docs; disabling our shortcuts must hand them to
      Essentials.
- [ ] Unknown subcommand → helpful message; a console-only command run by a
      player (and vice versa) → clear message.

## 4. Moderation

- [ ] [A] `/kick Player test` → Player sees the kick screen with the reason.
- [ ] `/ban Player 2m griefing` → temp-ban; Player cannot join, message shows
      reason and expiry; after 2 minutes can join again.
- [ ] `/ban Player` (no reason) → "No reason given" is used.
- [ ] `/ipban Player spam` → another account from the same IP is refused.
- [ ] `/mute Player 1m` → chat blocked with message; commands still work;
      unmuted automatically.
- [ ] `/warn Player be nice` → Player sees the warning.
- [ ] `/history Player` → all entries with ids, active/expired/lifted tags.
- [ ] `/unwarn <id>`, `/unban Player`, `/unmute Player` work; history shows
      "lifted by".
- [ ] `/checkban Player` matches reality.
- [ ] `/freeze Player reason` → can't move/interact/use commands (per
      `moderation.freeze.*`); `/unfreeze`. Disconnect while frozen → staff
      notice.
- [ ] Reason injection: `/ban Player <red><bold>x <click:run_command:/op Player>y`
      → all staff messages and the kick screen show the text literally, no
      colour, no click.
- [ ] GUI wizard: Player profile → Moderate → Temp Ban → reason → duration →
      confirm. **Cancel** in the confirmation returns to the previous page
      (not two pages back). Double-clicking confirm bans only once.
- [ ] Self-punish (`/ban <yourself>`) is refused.
- [ ] Team notification: [H] online, [A] bans Player → [H] sees
      "[Staff] Admin banned Player (...)"; [A] does not get it twice.
      `moderation.notify-staff: false` → silent.

## 5. Staff tools

- [ ] `/vanish` → Player can't see Admin, tab list hides Admin, join/quit
      messages hidden (settings), items not picked up; `/vanish` again.
- [ ] Reconnect while vanished → still vanished (`restore-on-reconnect`).
- [ ] `/god`, `/nocollision` toggle with on/off message.
- [ ] `/staff` → staff mode toolbar; each tool works (inspector, freeze,
      inventory/ender inspectors, teleport picker, vanish, exit). `/staff`
      again restores the original inventory.
- [ ] Kill the server (`kill -9`/close the window) while in staff mode →
      on rejoin the inventory is restored with a notice. `/admin staff
      recover <player>` works too.

## 6. Staff notes

- [ ] Profile → Staff notes → Add → text appears; right-click → confirm →
      deleted.
- [ ] `/admin notes Player add suspicious`, `/admin notes Player`,
      `/admin notes remove <id>`.
- [ ] Player with notes rejoins → staff get the clickable hint; click shows
      the notes.

## 7. Reports

- [ ] [P] `/report Admin he is mean` → confirmation; [A] gets the clickable
      `[Report #1]` line and a sound.
- [ ] [P] second report within 60s → cooldown message; after 3 open reports
      → limit message; reason "a" → too short; `/report <self>` → refused.
- [ ] [A] click **[View]** → detail page: Claim → status "In progress";
      teleport to player / to location; Resolve with a note → [P] gets the
      thank-you; Reject works the same.
- [ ] Report list: hopper toggles open/all; closed reports show their note.
- [ ] Profile → Reports lists reports about that player.
- [ ] Admin rejoins with open reports → reminder.
- [ ] `/admin reports open|all|view 1|claim 1|resolve 1 done` from the console.

## 8. Chat

- [ ] [A] `/sc hello` → staff and console see it, Player doesn't.
- [ ] `#hello` → staff chat; `/sc` toggles mode (everything goes to staff
      chat) and back.
- [ ] `/admin chat lock` → Player can't chat (message), Admin can; unlock.
- [ ] `/admin chat slow 10s` → Player can send one message per 10s; `slow off`.
- [ ] `/admin chat clear` → Player's chat is cleared, Admin's not; everyone
      sees who cleared it.
- [ ] Chat GUI buttons do the same.
- [ ] Muted player: muted message; muted staff can still use staff chat.
- [ ] With a chat-format plugin (e.g. LPC/ChatControl) installed: staff chat
      and lock still work, nothing is double-sent.

## 9. Players

- [ ] Player browser: online/offline/recently seen/search, pagination.
- [ ] Profile: fields, IP only with `players.ip`; buttons Moderate, Staff
      notes, Reports appear (permission-dependent).
- [ ] `/admin info Player` online and offline.
- [ ] `/heal`, `/feed`, `/fly`, `/gm 1 Player`, `/speed fly 5`,
      `/tpto Player`, `/admin tp Player Admin`, `/tphere Player`.
- [ ] `/invsee Player`: changes appear live for Player and vice versa;
      armor/offhand placeholders not duplicated into the real inventory.
      Without `.edit` permission: view only.
- [ ] `/endersee Player` same.
- [ ] `/clearinv Player` asks for `confirm`; `/clearinv Player confirm` clears.

## 10. Server

- [ ] Dashboard values plausible; `/admin server status` in console.
- [ ] `/broadcast <gold>Hello` (colour works), `/admin server title A | B`,
      `/admin server actionbar Hi`.
- [ ] `/admin maintenance on kick testing` → Player kicked and can't join;
      `allow Player` → can join; `off`.
- [ ] Maintenance survives a restart.
- [ ] `/admin server restart` → countdown messages; `/admin server cancel`
      stops it. (Let one shutdown run through on a test server.)

## 11. Worlds

- [ ] `/admin worlds`; `/admin world info` (current world) and with a name.
- [ ] time day/night/number, weather clear/rain/thunder, difficulty, setspawn,
      border size and `border center here`, `gamerule keepInventory true`.
- [ ] Gamerule GUI shows current values and changes them.

## 12. Whitelist

- [ ] `/wl on`; Player not listed can't join.
- [ ] `/wl add <never-joined name>` (online-mode server) → that account can
      join.
- [ ] `/wl add Player 2m reason` → after 2 minutes removed and kicked/blocked.
- [ ] `/wl remove Player`, `/wl list`, GUI member details.

## 13. Performance

- [ ] `/lag` values plausible; dashboard; world view; entities by type.
- [ ] Spawn 50 chickens; `/admin entities clear chicken` → preview + confirm
      hint; `... confirm` removes them; villagers/armor stands never removed;
      players never.
- [ ] Alert: set `performance.alerts.tps-threshold: 25` in-game → staff get
      an alert (coloured, not raw tags); reset it.

## 14. Audit log

- [ ] Everything above appears in `/admin` → Audit Log with **player names**
      (not UUIDs); filters (actor, module, time, success/failure) work;
      detail page with old/new values.
- [ ] `/admin audit Player`, paging.
- [ ] Failed attempts (e.g. [H] tries to ban) appear as failed where
      configured.

## 15. Settings

- [ ] `/admin` → Settings → each section opens; values shown; sensitive
      `database.password` is masked and not editable.
- [ ] Toggle a boolean, choose an enum (database type → "restart" note),
      edit a number with an invalid value (error) and a valid one,
      edit a list (`moderation.reasons.presets`), right-click reset.
- [ ] `config.yml` after these changes: values written, **comments still
      there**, nothing else changed.
- [ ] `/admin settings get|set|reset|list` from the console.
- [ ] Changing `general.language` to `de_DE` + `/admin reload` → German
      texts everywhere (menu titles, module names, commands).

## 16. Permissions (LuckPerms)

- [ ] `/lp editor` lists `universaladmin.*` nodes including reports/chat.
- [ ] Apply the Helper template from docs/user/permissions.md → [H] can do
      exactly that: kick/mute/warn yes, ban no (also hidden in GUI and help).
- [ ] [P] (default) can `/report`, nothing else.

## 17. Discord extension

- [ ] Server starts with `universaladmin-discord` → warning "no valid
      webhook-url"; `/admin modules` lists `discord:webhook`.
- [ ] Set a webhook URL → `/admin discord reload` → `/admin discord test` →
      embed in Discord.
- [ ] Ban/mute/report → embeds with title, staff, target, reason, colour.
- [ ] Reason containing `@everyone` → no ping.
- [ ] Vanish toggle and notes → not posted (excluded).
- [ ] Ban 10 players quickly → all arrive, no 429 errors in the console.
- [ ] Disable the extension (stop server without it / remove jar) → no
      errors; `/admin discord` gone.

## 18. Example extension (API check)

- [ ] Install `examples/example-extension/build/libs/example-extension-1.0.0.jar`.
- [ ] Main menu has a "Greetings" button; profiles have "Greet"; `/admin greet`
      and `/greet` work; `example.greeting` appears in Settings; greeting
      shows in the audit log; German texts with `de_DE`.

## 19. MySQL/MariaDB

- [ ] Fresh MariaDB database, `database.type: mysql` → starts, all tables
      created (including `ua_reports`, `ua_player_notes`), sections 4-7
      spot-checked.

## 20. Update check

- [ ] On 1.0.0 with no newer release: no update notice.
- [ ] `/admin update` → "already the latest version".
