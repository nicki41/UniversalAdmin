# Permissions

UniversalAdmin registriert seine Permission-Nodes zur Laufzeit (nicht
statisch in `plugin.yml`) - siehe
[docs/architecture/decisions/0005-extension-ready-design.md](../architecture/decisions/0005-extension-ready-design.md)
für die Begründung. Funktioniert mit jedem Standard-Permission-Plugin
(LuckPerms und ähnliche), die kein Vault im Core voraussetzen.

## Aktuell registrierte Nodes

| Node | Modul | Standard | Beschreibung |
|---|---|---|---|
| `universaladmin.reload` | Core (kein Modul) | op | Reload UniversalAdmin's configuration (`/admin reload`) |
| `universaladmin.menu.open` | Core (kein Modul) | op | Open the `/admin` main menu (siehe [gui-framework.md](../development/gui-framework.md)) |
| `universaladmin.players.view` | Players | op | View player profiles (browser, profile page) |
| `universaladmin.players.ip` | Players | op | View a player's IP address |
| `universaladmin.players.teleport` | Players | op | Teleport players (admin↔player, world spawn, bed, coordinates) |
| `universaladmin.players.heal` | Players | op | Heal a player |
| `universaladmin.players.feed` | Players | op | Feed a player |
| `universaladmin.players.extinguish` | Players | op | Extinguish a player |
| `universaladmin.players.effects.clear` | Players | op | Clear a player's potion effects |
| `universaladmin.players.effects.add` | Players | op | Add a potion effect to a player |
| `universaladmin.players.effects.remove` | Players | op | Remove one potion effect from a player |
| `universaladmin.players.set.health` | Players | op | Set a player's health |
| `universaladmin.players.set.food` | Players | op | Set a player's food level |
| `universaladmin.players.set.xp` | Players | op | Set a player's experience progress |
| `universaladmin.players.set.level` | Players | op | Set a player's experience level |
| `universaladmin.players.fly.toggle` | Players | op | Toggle whether a player may fly |
| `universaladmin.players.fly.speed` | Players | op | Set a player's fly speed |
| `universaladmin.players.walk-speed` | Players | op | Set a player's walk speed |
| `universaladmin.players.glow` | Players | op | Toggle a player's glowing effect |
| `universaladmin.players.gravity` | Players | op | Toggle whether gravity affects a player |
| `universaladmin.players.collision` | Players | op | Toggle whether a player collides with entities |
| `universaladmin.players.gamemode` | Players | op | Change a player's gamemode |
| `universaladmin.players.inventory.view` | Players | op | View a player's main inventory, armor, and offhand |
| `universaladmin.players.inventory.edit` | Players | op | Edit or clear a player's main inventory, armor, and offhand |
| `universaladmin.players.enderchest.view` | Players | op | View a player's ender chest |
| `universaladmin.players.enderchest.edit` | Players | op | Edit a player's ender chest |
| `universaladmin.moderation.use` | Moderation | op | Open the Moderation GUI |
| `universaladmin.moderation.view` | Moderation | op | View punishment history, warnings, bans, and mutes |
| `universaladmin.moderation.kick` | Moderation | op | Kick a player |
| `universaladmin.moderation.ban` | Moderation | op | Permanently ban a player |
| `universaladmin.moderation.tempban` | Moderation | op | Temporarily ban a player |
| `universaladmin.moderation.ipban` | Moderation | op | Ban a player's IP address |
| `universaladmin.moderation.mute` | Moderation | op | Permanently mute a player |
| `universaladmin.moderation.tempmute` | Moderation | op | Temporarily mute a player |
| `universaladmin.moderation.warn` | Moderation | op | Warn a player |
| `universaladmin.moderation.unban` | Moderation | op | Revoke an active ban |
| `universaladmin.moderation.unmute` | Moderation | op | Revoke an active mute |
| `universaladmin.moderation.removewarn` | Moderation | op | Remove a single warning |
| `universaladmin.moderation.freeze` | Moderation | op | Freeze a player |
| `universaladmin.moderation.unfreeze` | Moderation | op | Unfreeze a player |
| `universaladmin.moderation.vanish` | Moderation | op | Toggle your own vanish status |
| `universaladmin.bypass.vanish` | Moderation | op | See vanished players |
| `universaladmin.moderation.godmode` | Moderation | op | Toggle your own godmode status |
| `universaladmin.moderation.collision` | Moderation | op | Toggle your own no-collision status |
| `universaladmin.moderation.staffmode` | Moderation | op | Enter/exit staff mode |
| `universaladmin.moderation.staffmode.recover` | Moderation | op | Manually recover a pending staff-mode snapshot (`/admin staff recover`) |
| `universaladmin.server.view` | Server | op | Open the server dashboard |
| `universaladmin.server.broadcast` | Server | op | Broadcast messages/titles/actionbars to every online player |
| `universaladmin.server.maintenance` | Server | op | Enable/disable maintenance mode and manage its allow-list |
| `universaladmin.server.restart` | Server | op | Restart the server (with confirmation/countdown) |
| `universaladmin.server.shutdown` | Server | op | Shut down the server (with confirmation/countdown) |
| `universaladmin.bypass.maintenance` | Server | op | Join the server while maintenance mode is enabled |
| `universaladmin.worlds.view` | Worlds | op | Open the world browser and profile pages |
| `universaladmin.worlds.view.seed` | Worlds | op | View a world's seed |
| `universaladmin.worlds.teleport` | Worlds | op | Teleport to a world's spawn |
| `universaladmin.worlds.spawn.set` | Worlds | op | Set a world's spawn point |
| `universaladmin.worlds.time.set` | Worlds | op | Set a world's time |
| `universaladmin.worlds.weather.set` | Worlds | op | Set a world's weather |
| `universaladmin.worlds.difficulty.set` | Worlds | op | Set a world's difficulty |
| `universaladmin.worlds.border.manage` | Worlds | op | Manage a world's border |
| `universaladmin.worlds.gamerule.manage` | Worlds | op | Change a world's gamerules |
| `universaladmin.whitelist.view` | Whitelist | op | View the whitelist status and members |
| `universaladmin.whitelist.toggle` | Whitelist | op | Enable/disable the whitelist |
| `universaladmin.whitelist.add` | Whitelist | op | Add a player to the whitelist |
| `universaladmin.whitelist.remove` | Whitelist | op | Remove a player from the whitelist |
| `universaladmin.whitelist.temporary` | Whitelist | op | Give a whitelist entry an expiration |
| `universaladmin.performance.view` | Performance | op | View performance diagnostics (dashboard, world/entity breakdown); also who receives alert notifications |
| `universaladmin.performance.entity-clear` | Performance | op | Clear non-player entities matching a filter, with preview and confirmation |
| `universaladmin.audit.view` | Audit Log | op | View the audit log (list) |
| `universaladmin.audit.details` | Audit Log | op | View full detail (old/new values, metadata) of an audit entry |
| `universaladmin.settings.manage` | Settings | op | Manage UniversalAdmin settings |

Bis auf Players, Moderation, Server, Worlds, Whitelist und Performance ist
diese Liste bewusst grob (ein Node pro Modul) - der aktuelle Stand der
übrigen Module ist Skelett-Ebene (siehe
[docs/architecture/modules.md](../architecture/modules.md)). Players,
Moderation, Server, Worlds, Whitelist und Performance sind die Module, bei
denen die feingranulareren Nodes tatsächlich angekommen sind (z. B.
getrennte `.inventory.view`/`.inventory.edit` statt eines pauschalen
`.manage`, getrennte `.kick`/`.ban`/`.tempban`/`.ipban`/`.mute`/`.tempmute`/
`.warn`/`.unban`/`.unmute`/`.removewarn` statt eines pauschalen `.use`,
getrennte `.broadcast`/`.maintenance`/`.restart`/`.shutdown` statt eines
pauschalen `.manage`, ein separates `.view.seed` neben `.view`, ein
separates `.temporary` neben `.add`, oder ein separates `.entity-clear`
neben `.view`) - vollständig dokumentiert in
[docs/user/modules/players.md](modules/players.md#permissions),
[docs/user/modules/moderation.md](modules/moderation.md#permissions),
[docs/user/modules/server.md](modules/server.md#permissions),
[docs/user/modules/worlds.md](modules/worlds.md#permissions),
[docs/user/modules/whitelist.md](modules/whitelist.md#permissions) und
[docs/user/modules/performance.md](modules/performance.md#permissions). Mit
zunehmendem Funktionsumfang je Modul werden weitere Module diesem Muster
folgen.

## Standard ("op")

Alle aktuellen Nodes stehen standardmäßig auf `op` - ohne Permission-
Plugin haben nur Server-Operatoren Zugriff. Mit einem Permission-Plugin
lassen sich Nodes gezielt an bestimmte Rollen vergeben, unabhängig vom
Op-Status.

## Wie eine Permission tatsächlich geprüft wird

Jede Action, die eine Permission braucht, deklariert sie über
`ActionDefinition.Builder#permission(...)` bei der Registrierung (siehe
[docs/architecture/actions.md](../architecture/actions.md)) - `ActionExecutor`
prüft sie zentral, bevor die Action läuft. Es gibt bewusst keinen Code-Pfad,
der `player.hasPermission("...")` mit einem rohen String-Literal verstreut
im GUI-/Command-Code aufruft; jeder Check geht über den `PermissionEvaluator`,
den der jeweilige `Actor` trägt.

Wildcards (z. B. eine Rolle mit `universaladmin.*` in LuckPerms) brauchen
keine eigene Logik - `PermissiblePermissionEvaluator` delegiert direkt an
Bukkits `Permissible.hasPermission`, also funktionieren sie automatisch
über jedes installierte Permission-Plugin.

## Neue Permission hinzufügen (für Contributor)

Siehe [docs/development/adding-module.md](../development/adding-module.md)
Schritt 7 - eine `PermissionDefinition` wird im jeweiligen Modul
registriert, nicht in `plugin.yml` eingetragen.
