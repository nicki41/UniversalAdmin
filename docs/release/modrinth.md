# Modrinth Page

Source of truth for the `universaladmin` Modrinth project (id `wGP5uSse`).
The official extensions have their own projects - see
[modrinth-extensions.md](modrinth-extensions.md); all three are in the
collection [UniversalAdmin](https://modrinth.com/collection/DrMT8zro).
Edit here first, then copy into Modrinth. Versions are uploaded
automatically by `release.yml` - see [releasing.md](releasing.md#modrinth);
everything on this page (texts, gallery, links) is maintained by hand.

## Metadata

| Field | Value |
|---|---|
| Name | UniversalAdmin |
| Slug | `universaladmin` |
| Summary | see below |
| Categories | Management, Utility (additional: Social) |
| Environment | Server-side only |
| Loaders | Paper, Purpur (plugin); Velocity (the `-velocity` versions); BungeeCord, Waterfall (the `-bungee` versions) |
| Game versions | everything in [`supported-versions.txt`](../../supported-versions.txt) - set by `release.yml`, extended by `compatibility.yml` |
| License | GPL-3.0-or-later (from 1.5.0; up to 1.4.0 Apache-2.0) |
| Icon | `assets/icon.png` |
| Source | https://github.com/universaladmin-mc/UniversalAdmin |
| Issues | https://github.com/universaladmin-mc/UniversalAdmin/issues |
| Wiki | https://universaladmin-mc.github.io/UniversalAdmin/ |
| Discord | none - only list one once it exists |

## Summary

> The free all-in-one admin plugin for Paper and Velocity networks: moderation, reports, staff mode, vanish, staff chat, live inventories, worlds, maintenance and a full audit log - in clean menus and commands.

## Description

Paste everything between the lines into Modrinth's description editor.

The statistics badges go through `wsrv.nl` with `maxage=1d` on purpose:
Modrinth sends images from hosts outside its allow-list through `wsrv.nl`
anyway, but then with a one-year cache, so the numbers would never change.
A `wsrv.nl` link of our own is left as it is and refreshes daily.

---

# UniversalAdmin

[![Servers](https://wsrv.nl/?url=https%3A%2F%2Ftelemetry.0nicki.de%2Fv1%2Fbadge%2Fservers.svg%3Fplugin%3Duniversaladmin&maxage=1d)](https://github.com/universaladmin-mc/UniversalAdmin/blob/main/docs/user/telemetry.md)
[![Players](https://wsrv.nl/?url=https%3A%2F%2Ftelemetry.0nicki.de%2Fv1%2Fbadge%2Fplayers.svg%3Fplugin%3Duniversaladmin%26color%3Dblue&maxage=1d)](https://github.com/universaladmin-mc/UniversalAdmin/blob/main/docs/user/telemetry.md)

**The free, all-in-one admin plugin for Paper.** Everything your staff team
needs - moderation, reports, staff mode, player tools, worlds, maintenance,
performance and a complete audit log - in clean in-game menus *and*
commands, on one server or a whole **Velocity/BungeeCord network**. No
dependencies, no premium version, open source (GPL).

Runs on **Paper and Purpur 1.21.7 - 1.21.11 and 26.1.1 - 26.3** - one jar for
all of them, and new Minecraft versions are tested and added automatically.

## ✨ Highlights

- **One menu for everything** - type `/admin` and click through players,
  moderation, reports, chat, server, worlds, whitelist, performance, audit
  log and settings. Where you type something, one form asks for everything
  at once - text fields, sliders, checkboxes.
- **Commands too** - `/ban Steve 7d griefing`, `/mute`, `/warn`, `/history`,
  `/vanish`, `/invsee`, `/heal`, `/gm` ... with tab completion. Already use
  EssentialsX? Turn off any shortcut you want to keep there.
- **Every action is logged** - who banned whom, who changed which gamerule,
  who opened whose inventory. Searchable in-game.
- **Nicks for staff** - `/nick Notch`: play under another name and skin,
  in the chat, the tab list and above your head. The real name stays in
  punishments and the audit log, and staff with the permission see it.
- **Fine-grained permissions** - give helpers kick and mute, but not ban.
  LuckPerms role templates included.
- **Safe** - confirmations for dangerous actions, no double clicks, player
  input can't inject formatting, an invalid config never crashes the server.
- **Network-ready** - behind Velocity or BungeeCord, see every server and
  player in one menu, teleport across servers, and ban once for the whole
  network.
- **No dependencies** - SQLite out of the box, MySQL/MariaDB optional.
- **English & German** - every text editable.

## 🌐 Networks (Velocity / BungeeCord)

Install UniversalAdmin on every server, give them one MySQL/MariaDB database
and set `network.enabled: true`:

- a **Network** menu with every server - also those without UniversalAdmin -
  their players, TPS and version;
- the **player menu** lists every player on every server, filtered or
  sorted by server; a player's profile sends them anywhere or brings them
  to you;
- `/admin find Steve` - "Steve is on survival";
- `/admin tp Steve` takes you to Steve's server, right next to him;
  `/admin tphere`, `/admin send`, `/admin connect`;
- **bans, mutes, warnings, kicks and freezes apply on every server at once**;
- staff chat, staff notifications, report alerts and `/admin alert` across
  all servers, tagged with the server name;
- **maintenance and a whitelist for one server or the whole network**;
- vanished staff stay hidden in every list and count, also when switching
  servers;
- one audit log for the whole network.

Optional: the **UniversalAdmin Velocity** or **BungeeCord** plugin (the
`-velocity` and `-bungee` versions on this page) keeps banned players out at
the proxy, before they reach any server - and closes the proxy during
network maintenance or to players not on the network whitelist. [Network guide](https://github.com/universaladmin-mc/UniversalAdmin/blob/main/docs/user/network.md).

## 🔁 Switching from another plugin?

`/admin import` brings your bans, mutes, warnings and kicks over from
**LiteBans**, **AdvancedBan**, the **vanilla ban lists** and **EssentialsX** -
with reasons, dates and what is still in force. Preview first, nothing is
lost. [Import guide](https://github.com/universaladmin-mc/UniversalAdmin/blob/main/docs/user/import.md).

## 🛡️ Moderation

Kick, ban, temp-ban, IP-ban, mute, temp-mute, warn and freeze - with reason
and duration presets, a punish form, full history (`/history`),
current status (`/checkban`) and **team notifications** for every
punishment. **Punishment templates** escalate on their own: `/punish Steve spam`
warns the first time, mutes for an hour the second time, and so on.
With Simple Voice Chat, a mute silences a player **in the chat, in the voice
chat or both** (`/chatmute`, `/voicemute`).
Optional **alt detection** shows accounts that connect from the same
address - privacy-friendly: only keyed hashes are stored, and only if you
switch it on.
**Staff notes** let your team leave private notes about players.

## 📣 Reports

Players type `/report <player> <reason>`; staff get an instant, clickable
alert. The reports GUI shows open reports - claim, resolve or reject them,
teleport to the player or to where the report was made. Built-in spam
protection.

## 🕵️ Staff tools

Vanish, godmode, no-collision and a **staff mode** with its own toolbar:
player inspector, freeze stick, live inventory & ender chest inspectors,
teleport picker - and your own inventory comes back even after a crash.
**Staff chat** with `/sc` or a `#` in front of your message.

## 👤 Players

Browse online and offline players - in a network every player on every
server - search, open profiles with play time and location, heal, feed,
fly, gamemode, speed (with sliders), effects, teleports - and edit
inventories and ender chests **live**.

## 🌍 Server & worlds

Dashboard, broadcasts (chat, title, action bar), maintenance mode with an
allow-list, shutdown/restart with countdown. Per-world time, weather,
difficulty, spawn, world border and a full gamerule editor. Chat lock, slow
mode and clear chat.

## 📋 Whitelist

Reasons, notes and **temporary access** that expires by itself - also for
players who have never joined. In a network, also one whitelist for all
servers at once.

## 📈 Performance

TPS, MSPT and memory at a glance (`/lag`), per-world stats, an entity
overview and staff alerts. The entity clear never touches players, villagers,
item frames or other protected entities - always with preview and
confirmation.

## ⚙️ Settings in-game

Change any setting from a menu - validated, applied right away, saved to
`config.yml` with your comments intact.

## 🧩 Extensions

Other plugins can add modules to UniversalAdmin: commands, menu pages,
profile buttons, settings, database tables - all audited and permissioned
like the built-ins. The official extensions - all in the
[UniversalAdmin collection](https://modrinth.com/collection/DrMT8zro):

- **UniversalAdmin Discord** - admin actions in Discord, and as a bot:
  two-way staff chat, `/ban`, `/mute`, `/history` from Discord, reports
  with Claim / Resolve / Reject buttons.
- **UniversalAdmin Placeholders** - PlaceholderAPI placeholders such as
  `%universaladmin_online%` (without vanished players),
  `%universaladmin_muted%` and `%universaladmin_open_reports%`.
- **UniversalAdmin Voice Chat** - Simple Voice Chat: chat, voice or full
  mutes, vanished staff stay silent, voice status on the profile and a
  staff voice group.
- **UniversalAdmin TAB** - TAB's tab list and name tags follow nicks, vanish
  and staff mode.

Developers: see the
[extension guide](https://github.com/universaladmin-mc/UniversalAdmin/blob/main/docs/extensions/getting-started.md).

## 📦 Requirements

- Paper or Purpur 1.21.7 - 1.21.11 or 26.1.1 - 26.3, Java 21+ (Paper 26.x
  itself needs Java 25)
- Nothing else. Optional: MySQL/MariaDB (needed for network mode),
  LuckPerms (any permission plugin works).
- Networks: Velocity 3.4+ or BungeeCord/Waterfall; the UniversalAdmin
  Velocity and BungeeCord plugins are optional.

## 🚀 Getting started

1. Put the jar into `plugins/`, restart.
2. Type `/admin`.
3. Give your team permissions:
   [permissions & role templates](https://github.com/universaladmin-mc/UniversalAdmin/blob/main/docs/user/permissions.md).

All commands: [command reference](https://github.com/universaladmin-mc/UniversalAdmin/blob/main/docs/user/commands.md) ·
Full docs: [GitHub](https://github.com/universaladmin-mc/UniversalAdmin/tree/main/docs)

## 📊 Anonymous statistics

UniversalAdmin sends a small anonymous heartbeat every 30 minutes so we know
how many servers use it: a random installation id, the UniversalAdmin,
Minecraft and Java version, and the online/maximum player count. **Never**
sent: IPs, server names, player names or UUIDs, chat, commands, world data,
other plugins or hardware identifiers. The id is random and derived from
nothing. Turn it off with `telemetry.enabled: false` in `config.yml` - then
no request is made at all.
[Exactly what is sent](https://github.com/universaladmin-mc/UniversalAdmin/blob/main/docs/user/telemetry.md).

## 💬 Support

Bugs and ideas: [GitHub issues](https://github.com/universaladmin-mc/UniversalAdmin/issues) -
please include `/admin status`.

---

## Gallery

Images (1920×1080 or 1280×720 PNG, GUI scale 3 or "auto" in a clean world,
resource packs off). Title / description for each:

1. **Main menu** - "Everything in one menu" - `/admin`, an operator with all
   permissions, every module visible.
2. **Punishment wizard** - "Ban, mute or warn in three clicks" - Moderate →
   Temp Ban → reason list (and the confirmation dialog as a second shot).
3. **Punishment history in chat** - "Full history with one command" -
   `/history <player>` with 4-6 mixed entries, plus `/checkban`.
4. **Report alert** - "Reports arrive instantly" - chat showing the
   clickable `[Report #3] ... [View]` line, then the report detail page
   with claim/resolve/teleport buttons.
5. **Staff mode** - "Staff mode with its own toolbar" - hotbar with the
   tools, action bar "Looking at: <player>".
6. **Player profile** - "Every player at a glance" - profile page with the
   Moderate/Notes/Reports buttons.
7. **Live inventory editor** - "Edit inventories live" - `/invsee`.
8. **Server dashboard** - "Your server at a glance" - Server home page.
9. **Performance** - "Find lag fast" - performance dashboard and the
   entity-by-type page.
10. **Gamerule editor** - "Every gamerule, per world" - Worlds → Gamerules.
11. **Audit log** - "Know who did what" - audit list with filters, plus a
    detail page.
12. **Settings** - "Change settings without touching files" - Settings →
    moderation section.
13. **Discord** - "Punishments in your Discord" - a Discord channel with 2-3
    embeds from the Discord extension.
14. **Network** - "Your whole network in one menu" - Network page with 2-3
    servers and their player heads, plus `/admin find` in chat.

Optional: a 20-30 s clip (main menu → player → moderate → ban → audit log)
for the description, uploaded to YouTube and embedded.

## Version uploads

Automatic - every GitHub release uploads three Modrinth versions with the
changelog section as notes:

- `<v>` - `universaladmin-core-<v>.jar`, loaders Paper and Purpur, every
  game version in `supported-versions.txt`;
- `<v>-velocity` - `universaladmin-velocity-<v>.jar`, loader Velocity;
- `<v>-bungee` - `universaladmin-bungee-<v>.jar`, loaders BungeeCord and
  Waterfall.

The type is derived from the version (`1.1.0` -> release, `-beta`/`-rc` ->
beta, `-alpha` -> alpha). When a new Paper version passes the weekly
compatibility check, it is added to the newest release of each of them
(see [releasing.md](releasing.md#compatibility)). The Discord extension is
not uploaded to this project; create a separate Modrinth project for it if
wanted (then add another `mc-publish` step in `release.yml`).
