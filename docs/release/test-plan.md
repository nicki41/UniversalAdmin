# Manual Test Plan

What the automated tests cannot cover: a real Paper server. Run this before
every minor/major release; for a patch release, run the sections the change
touches plus "Smoke test".

## Setup

- Paper 26.3 (newest) and once more on Paper 1.21.7 (oldest), a fresh
  `plugins/` folder. `scripts/paper-smoke-test.sh <version>` covers the
  console part of section 1 on any version automatically.
- Two accounts: **Admin** (op) and **Player** (no op, no permissions). A
  third account (**Helper**) for permission tests is ideal; otherwise
  de-op the second account and grant nodes with LuckPerms.
- LuckPerms installed for the permission tests; EssentialsX for the
  shortcut-clash test (separate run).
- Build: `./gradlew clean build`, then copy
  `build/libs/universaladmin-core-1.1.0.jar` and
  `extensions/discord/build/libs/universaladmin-discord-1.1.0.jar`.

Legend: **[A]** as Admin, **[P]** as Player, **[H]** as Helper, **[C]** in
the console.

## 1. Smoke test

- [ ] Server starts without a stack trace; the startup banner lists 10 modules.
- [ ] `plugins/UniversalAdmin/` contains `config.yml` (config-version 3),
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
- [ ] Alts (`moderation.alts.enabled: true`): Player and a second account
      join from the same machine; ban the first → when the second joins, staff
      get "[Alts] ... shares an address with banned ..."; `/admin alts <second>`
      lists the banned one; Profile → Possible Alts shows it in red; the audit
      log shows "Viewed possible alts". `refuse-banned-alts: true` → the
      second account is refused, with `bypass.alt-check` let in.
      `/admin alts <second> forget` → list empty.
- [ ] Templates: `/punish Player` opens the moderation menu, Punish with
      Template shows "Next: Warning"; from the console `/admin punish Player`
      lists the templates;
      `/punish Player spam` warns, the second time mutes 1h (step 2 of 4);
      `/unwarn` the first warning → the next `/punish Player spam` is step 2
      again. A moderator with `moderation.punish` but without
      `moderation.tempmute` is refused at the mute step with the template's
      name. GUI: Moderate → Punish with Template shows the next step per
      template; `/admin reload` after editing the file picks it up.
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
- [ ] Network (section 24 setup): `/wl network add Admin`, `/wl network on` →
      Player is refused on lobby and survival (and at the proxy); Admin gets
      in; `/wl network off`. A temporary entry on survival expires on
      survival only.

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

- [ ] Install `examples/example-extension/build/libs/example-extension-1.1.0.jar`.
- [ ] Main menu has a "Greetings" button; profiles have "Greet"; `/admin greet`
      and `/greet` work; `example.greeting` appears in Settings; greeting
      shows in the audit log; German texts with `de_DE`.

## 19. MySQL/MariaDB

- [ ] Fresh MariaDB database, `database.type: mysql` → starts, all tables
      created (including `ua_reports`, `ua_player_notes`), sections 4-7
      spot-checked.

## 20. Update check

- [ ] On 1.1.0 with no newer release: no update notice.
- [ ] `/admin update` → "already the latest version".

## 21. Import

Copy the data folder of the old plugin (or install it with test data).

- [ ] [C] `/admin import` lists the sources found, each with a location.
- [ ] [C] `/admin import advancedban` (and `litebans`, `vanilla`,
      `essentials`) - preview with plausible counts; nothing in
      `/admin history` yet.
- [ ] [C] `/admin import <source> confirm` - progress, summary; `/history
      <player>` shows the imported entries with reason, date and issuer; an
      imported active ban keeps the player out, an imported mute blocks
      chat.
- [ ] Running the same import again: "already imported" equals the first
      run's count, nothing duplicated.
- [ ] `/admin unban <name>` of an imported player who never joined works.
- [ ] Vanilla: after the import `banned-players.json` no longer lists the
      moved players; `/admin unban` lets them in.
- [ ] EssentialsX: a jailed player has a staff note about the jail.
- [ ] H2/HSQLDB without the old plugin's jar: clear message about the
      missing driver.

## 22. PlaceholderAPI extension

- [ ] With PlaceholderAPI and `universaladmin-placeholderapi-<v>.jar`:
      `/admin modules` lists "Placeholders".
- [ ] `/papi parse me %universaladmin_online%` - vanish yourself, the
      number drops; `%universaladmin_vanished%` yes/no.
- [ ] Mute a player: `%universaladmin_muted%`, `_mute_reason`,
      `_mute_remaining` for that player (`/papi parse <player> ...`).
- [ ] `%universaladmin_open_reports%` changes after `/report`.
- [ ] `%universaladmin_maintenance%` after `/admin maintenance on`.

## 23. Discord bot

Setup as in [docs/extensions/discord.md](../extensions/discord.md#bot-mode),
with a log, a staff chat and a reports channel, an "Admin" role with
`universaladmin.*` and a "Mod" role with `universaladmin.moderation.mute`,
`universaladmin.moderation.view` and `universaladmin.chat.staff`.

- [ ] `/admin discord status` - online as ... on ...; `/admin discord test`
      posts into the log channel.
- [ ] Slash commands are listed in Discord; `/ban player:` completes online
      players.
- [ ] Admin: `/ban player:Steve duration:1h reason:test` - Steve is banned,
      the answer is only visible to the admin, the audit log shows
      `Discord:<name>`, the log channel gets an embed.
- [ ] Mod: `/mute` works, `/ban` answers "no permission"; without any role
      nothing works.
- [ ] `/ua command:notes Steve add test` and `/history player:Steve`.
- [ ] Staff chat: `/sc hi` in game appears in the channel; a Mod message in
      the channel appears in game as `[Staff] [Discord] ...`; a member
      without the role gets a 🚫 reaction; `@everyone` in game pings nobody.
- [ ] `/report` in game - a card with buttons appears; Claim (card shows
      who), Resolve with a note (buttons disabled, report closed in game);
      a report closed in game closes its card.
- [ ] `/admin discord reload` after changing the token - reconnects; a wrong
      token is reported in `/admin discord status`.

## 24. Network (Velocity)

Setup: Velocity with `player-info-forwarding-mode = "modern"`, two Paper
servers `lobby` and `survival` with the forwarding secret, one MariaDB,
`network.enabled: true` and `network.server-name` on both,
`universaladmin-velocity-1.1.0.jar` on the proxy. Admin on lobby, Player on
survival.

- [ ] Both logs: `Network mode: this server is '...'`; `/admin status` shows
      the network line; `/uavelocity` on the proxy: ban check active.
- [ ] [A] `/admin` → Network: both servers with players, TPS, version; a
      third server in `velocity.toml` without UniversalAdmin is listed too.
      Clicking a server lists its players; clicking a player opens their
      network profile.
- [ ] [A] `/admin find Player` → "Player is on survival".
- [ ] [A] `/admin tp Player` → Admin lands on survival right next to Player;
      back on lobby, `/admin tphere Player` → Player lands next to Admin.
- [ ] [A] `/admin send Player lobby`, `/admin connect survival`.
- [ ] [A] on lobby: `/ban Player 10m test` → Player is removed from the
      whole network with the ban screen; reconnecting is refused by the
      proxy (Velocity ban screen) - also with the Velocity plugin removed,
      then by the server on join.
- [ ] [A] on lobby: `/mute Player 10m` → Player on survival cannot chat;
      `/freeze Player` → frozen on survival, still frozen after switching
      servers.
- [ ] Staff chat, staff notifications and a `/report` on survival appear on
      lobby with `[survival]`; `/admin alert hello` appears on both.
- [ ] Audit log on lobby shows survival's entries with the server tag.
- [ ] [A] on survival: `/admin maintenance on kick test` → Player is moved
      to lobby; `/server survival` is refused with the maintenance message;
      lobby stays open - also after restarting lobby.
- [ ] [A] on lobby: `/admin maintenance network on kick update` → Player
      leaves the network; a new login is refused by the proxy; the server
      list shows "Maintenance"; Admin (bypass permission via LuckPerms on
      the proxy, or `/admin maintenance network allow Admin`) still gets in.
      `/admin maintenance status` on survival shows the network's
      maintenance. `network off` → everyone can join again.
- [ ] [A] `/vanish` on survival → within ~5 s Player (without
      `bypass.vanish`) no longer finds Admin with `/admin find`, the network
      list and counts; Admin switches to lobby → still vanished there.
- [ ] Stop survival → after ~20 s it shows as offline in the network list.
- [ ] Same with BungeeCord instead of Velocity and
      `universaladmin-bungee-<v>.jar` on the proxy: `/uabungee` shows the
      checks; a banned player is refused at login; during network maintenance
      and with the network whitelist on, a player without bypass is
      disconnected right after the login, Admin (bypass via LuckPerms on the
      proxy) gets in; the server list shows "Maintenance".
- [ ] [A] on survival: **Players → Online Players** lists Player on lobby
      with the server; the **Server** filter cycles through the servers, the
      sort button switches to "by server". Player's profile shows "Online on
      lobby" with **Teleport to player**, **Bring to me** and **Send to
      server...** - each works; no inventory button.

## 25. Forms

- [ ] Punish: **Moderate → Temp Mute** opens one form (reason choice, own
      reason, duration choice, own duration); a typed reason wins; a bad
      duration reports an error and reopens the menu.
- [ ] Whitelist add: reason, notes (multi-line) and expiry in one form;
      without `whitelist.temporary` no expiry field.
- [ ] Broadcast title: title and subtitle in one form; chat broadcast keeps
      line breaks.
- [ ] Maintenance on: reason, message and kick checkbox in one form; the
      message is shown to a refused player.
- [ ] World border: center with "use my position", size with transition
      slider, damage and warning fields.
- [ ] Player actions: health, food, XP, fly and walk speed as sliders at the
      current value; teleport to coordinates with a world list.

## 26. Voice Chat extension

- [ ] With Simple Voice Chat and `universaladmin-voicechat-<v>.jar`:
      `/admin modules` lists "Voice Chat"; the punish form for a mute asks
      "Mute in".
- [ ] `/voicemute Player 5m loud` → Player is not heard and sees the
      action-bar notice; chat still works. `/chatmute` → the reverse.
      `/mute` → neither. `/unmute` lifts each.
- [ ] Without the extension, `/voicemute` says no voice chat is connected.
- [ ] `/vanish` → Admin is not heard; `/admin voicegroup` → in the staff
      group, another staff member there hears Admin, Player does not.
- [ ] Player's profile: **Voice Chat** shows installed/connected and the
      group; a click takes Player out of the group (audit entry).
- [ ] Network: `/voicemute` on lobby → Player on survival is silenced at
      once.
