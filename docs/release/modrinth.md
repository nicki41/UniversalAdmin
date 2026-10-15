# Modrinth Page

Source of truth for the `universaladmin` Modrinth project (id `wGP5uSse`).
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
| Loaders | Paper |
| Game versions | 26.2 |
| License | Apache-2.0 |
| Icon | `assets/icon.png` |
| Source | https://github.com/nicki41/UniversalAdmin |
| Issues | https://github.com/nicki41/UniversalAdmin/issues |
| Wiki | https://github.com/nicki41/UniversalAdmin/tree/main/docs |
| Discord | none - only list one once it exists |

## Summary

> The free all-in-one admin plugin for Paper: moderation, reports, staff mode, vanish, staff chat, inventories, worlds, whitelist, maintenance and a full audit log - in clean menus and commands, no dependencies.

## Description

Paste everything between the lines into Modrinth's description editor.

---

# UniversalAdmin

**The free, all-in-one admin plugin for Paper.** Everything your staff team
needs - moderation, reports, staff mode, player tools, worlds, maintenance,
performance and a complete audit log - in clean in-game menus *and*
commands. No dependencies, no premium version, open source.

## ✨ Highlights

- **One menu for everything** - type `/admin` and click through players,
  moderation, reports, chat, server, worlds, whitelist, performance, audit
  log and settings.
- **Commands too** - `/ban Steve 7d griefing`, `/mute`, `/warn`, `/history`,
  `/vanish`, `/invsee`, `/heal`, `/gm` ... with tab completion. Already use
  EssentialsX? Turn off any shortcut you want to keep there.
- **Every action is logged** - who banned whom, who changed which gamerule,
  who opened whose inventory. Searchable in-game.
- **Fine-grained permissions** - give helpers kick and mute, but not ban.
  LuckPerms role templates included.
- **Safe** - confirmations for dangerous actions, no double clicks, player
  input can't inject formatting, an invalid config never crashes the server.
- **No dependencies** - SQLite out of the box, MySQL/MariaDB optional.
- **English & German** - every text editable.

## 🛡️ Moderation

Kick, ban, temp-ban, IP-ban, mute, temp-mute, warn and freeze - with reason
and duration presets, a step-by-step GUI wizard, full history (`/history`),
current status (`/checkban`) and **team notifications** for every
punishment. **Staff notes** let your team leave private notes about players.

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

Browse online and offline players, search, open profiles with play time and
location, heal, feed, fly, gamemode, speed, effects, teleports - and edit
inventories and ender chests **live**.

## 🌍 Server & worlds

Dashboard, broadcasts (chat, title, action bar), maintenance mode with an
allow-list, shutdown/restart with countdown. Per-world time, weather,
difficulty, spawn, world border and a full gamerule editor. Chat lock, slow
mode and clear chat.

## 📋 Whitelist

Reasons, notes and **temporary access** that expires by itself - also for
players who have never joined.

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
like the built-ins. **Official: UniversalAdmin Discord** posts bans, mutes,
reports and restarts to your Discord channel (download from the GitHub
release). Developers: see the
[extension guide](https://github.com/nicki41/UniversalAdmin/blob/main/docs/extensions/getting-started.md).

## 📦 Requirements

- Paper 26.2, Java 25
- Nothing else. Optional: MySQL/MariaDB, LuckPerms (any permission plugin works).

## 🚀 Getting started

1. Put the jar into `plugins/`, restart.
2. Type `/admin`.
3. Give your team permissions:
   [permissions & role templates](https://github.com/nicki41/UniversalAdmin/blob/main/docs/user/permissions.md).

All commands: [command reference](https://github.com/nicki41/UniversalAdmin/blob/main/docs/user/commands.md) ·
Full docs: [GitHub](https://github.com/nicki41/UniversalAdmin/tree/main/docs)

## 📊 Anonymous statistics

UniversalAdmin sends a small anonymous heartbeat every 30 minutes so we know
how many servers use it: a random installation id, the UniversalAdmin,
Minecraft and Java version, and the online/maximum player count. **Never**
sent: IPs, server names, player names or UUIDs, chat, commands, world data,
other plugins or hardware identifiers. The id is random and derived from
nothing. Turn it off with `telemetry.enabled: false` in `config.yml` - then
no request is made at all.
[Exactly what is sent](https://github.com/nicki41/UniversalAdmin/blob/main/docs/user/telemetry.md).

## 💬 Support

Bugs and ideas: [GitHub issues](https://github.com/nicki41/UniversalAdmin/issues) -
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

Optional: a 20-30 s clip (main menu → player → moderate → ban → audit log)
for the description, uploaded to YouTube and embedded.

## Version uploads

Automatic - every GitHub release uploads `universaladmin-core-<v>.jar` as a
Modrinth version with the changelog section as notes; the type is derived
from the version (`1.0.0` → release, `-beta`/`-rc` → beta, `-alpha` →
alpha). The Discord extension is not uploaded to this project; create a
separate Modrinth project for it if wanted (then add a second
`mc-publish` step in `release.yml`).
