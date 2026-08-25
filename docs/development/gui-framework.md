# GUI Framework

This document describes the reusable in-game GUI framework under
[`dev.universaladmin.gui`](../../src/main/java/dev/universaladmin/gui) and,
at the end, how a module builds its own page on top of it. It replaces the
"no framework decision yet" state from
[docs/architecture/gui.md](../architecture/gui.md) - that document remains
the short version of the architecture rule (a frontend only calls a
service/action), this one covers the *how*.

Currently two concrete pages from the framework itself exist: the main
menu (`MainMenuPage`) and a placeholder page per built-in module
(`PlaceholderGuiPage`) - see "Main Menu Skeleton" below. No module has a
real feature GUI today; that's deliberately out of scope for this task
(see ROADMAP.md Phase 1).

## The One Architecture Rule Still Holds

```
GUI → application service / action → domain
```

A click handler (`GuiButton.ClickHandler`) calls a service or an `Action`,
never SQL, never multi-line computation. The framework doesn't change
that - it just gives this rule a shared, tested foundation instead of
every page bringing its own `InventoryClickListener`. See
[docs/architecture/decisions/0004-gui-service-separation.md](../architecture/decisions/0004-gui-service-separation.md).

## Building Blocks at a Glance

| Building block | Class | Purpose |
|---|---|---|
| Page | [`GuiPage`](../../src/main/java/dev/universaladmin/gui/GuiPage.java) | The minimal interface from ADR-0004 (`id()`, `open(Player)`). Every framework page implements it via `AbstractGuiPage`. |
| Base class | [`AbstractGuiPage`](../../src/main/java/dev/universaladmin/gui/AbstractGuiPage.java) | Renders the navigation bar (back/refresh/close), the rest is `renderContent(...)`. |
| List base class | [`AbstractListGuiPage<T>`](../../src/main/java/dev/universaladmin/gui/AbstractListGuiPage.java) | Async loading + pagination + loading/empty/error state, implemented once. |
| Session | [`GuiSession`](../../src/main/java/dev/universaladmin/gui/GuiSession.java) / [`GuiSessionManager`](../../src/main/java/dev/universaladmin/gui/GuiSessionManager.java) | Per-player state (navigation history, attributes) - see "Player Session". |
| Visible element | [`GuiItem`](../../src/main/java/dev/universaladmin/gui/GuiItem.java) / [`GuiButton`](../../src/main/java/dev/universaladmin/gui/GuiButton.java) | `GuiItem` is purely visual, `GuiButton` additionally has a click handler + optional permission. |
| Rendering | [`GuiView`](../../src/main/java/dev/universaladmin/gui/GuiView.java) | A rendered inventory for one player; the only place that calls `Bukkit.createInventory`. |
| Layout | [`GuiLayout`](../../src/main/java/dev/universaladmin/gui/GuiLayout.java) | The one slot table - see "Slots". |
| Pagination | [`Pagination<T>`](../../src/main/java/dev/universaladmin/gui/Pagination.java) | Pure logic (no Bukkit), used by `AbstractListGuiPage`. |
| Click routing | [`GuiListener`](../../src/main/java/dev/universaladmin/gui/GuiListener.java) | The one Bukkit listener for every GUI. |
| Icons | [`IconProvider`](../../src/main/java/dev/universaladmin/gui/IconProvider.java) / [`MaterialIconProvider`](../../src/main/java/dev/universaladmin/gui/MaterialIconProvider.java) | See "Icons". |
| Confirmation | [`ConfirmationDialog`](../../src/main/java/dev/universaladmin/gui/ConfirmationDialog.java) | Yes/no with a danger level. |
| Selection | [`SelectionDialog`](../../src/main/java/dev/universaladmin/gui/SelectionDialog.java) | Pick one from a list, built on `AbstractListGuiPage`. |
| Text input | [`GuiTextInput`](../../src/main/java/dev/universaladmin/gui/GuiTextInput.java) | Free text via the Paper dialog API - see "Search". |
| Bundle | [`GuiFramework`](../../src/main/java/dev/universaladmin/gui/GuiFramework.java) | `GuiSessionManager` + `IconProvider`, passed to every page via the constructor (`UniversalAdmin#guiFramework()`). |

## Slots

No magic numbers per feature - every page renders into the same six-row
layout, defined once in `GuiLayout`:

```
Row 0: Back (slot 0) · Refresh (slot 4) · Close (slot 8)
Rows 1-4: content area, 36 slots - GuiLayout.contentSlot(0..35)
Row 5: Previous (48) · page indicator (49) · Next (50)
```

A page rendering its own content exclusively uses
`GuiLayout.contentSlot(index)` instead of a raw number - so "where is the
back button" stays a single, platform-wide answer.

## Navigation

The entire navigation mechanism is exactly one concept: **opening a page
is always `GuiPage#open(Player)`.**

- **Forward** (`GuiClickContext#open(GuiPage)`): remembers how the current
  page redraws itself (`() -> currentPage.open(viewer)`), pushes that onto
  `GuiSession`'s history, then opens the next page.
- **Back** (`GuiClickContext#back()`): pops the top history entry and runs
  it - which simply calls `open(...)` on the previous page again. With no
  history, `back()` closes the inventory.
- **Refresh**: the refresh button in `AbstractGuiPage` calls
  `this.open(viewer)` again on the same page - no special case, no second
  method.

No separate stack of `GuiPageId`s that would need to be kept in sync by
hand - "how do I get back here" is always executable code, never a piece
of data.

## Pagination

`Pagination<T>` is pure, Bukkit-free logic: slice calculation, clamping for
an empty/short list, `hasPrevious()`/`hasNext()`. Changing pages only
changes the page number (stored in `GuiSession` under `<pageId>.page`) and
calls `open(viewer)` again - the same refresh mechanism as above.

## Async Data

`AbstractListGuiPage<T>` is the complete reference for the required flow:

```
open() → render a loading placeholder (immediately)
       → loadItems(viewer) on the TaskScheduler
       → whenComplete(...) → scheduler.runOnMainThread(...)
       → repopulate the GuiView
```

**No inventory mutation off the main thread** - see
[docs/architecture/threading.md](../architecture/threading.md). A result
that arrives after the player has already left the page is detected
(`viewer.getOpenInventory().getTopInventory().getHolder() == view`) and
discarded, instead of mutating a closed/foreign inventory.

An error in the loaded `CompletableFuture` renders the error state
(`gui.error`, refresh retries); an empty list renders the empty state
(`gui.empty`); while loading, the loading state (`gui.loading`) - all
three via `IconProvider`/`MessageService`, never hardcoded.

## Permissions

A `GuiButton` optionally carries a `PermissionNode`. The rule is
mechanically enforced, not just documented: `GuiView#place(int, GuiButton, Player)`
only places the button if `viewer.hasPermission(...)` is true - otherwise
the slot stays empty ("hide by default", no grayed-out disabled state).
`GuiButton#handle` checks again on click as a safety net (defense against
a click racing a permission change).

**GUI permission is display only.** The service/action a button ultimately
calls must re-authorize itself - see
[docs/architecture/decisions/0004-gui-service-separation.md](../architecture/decisions/0004-gui-service-separation.md)
and [SECURITY.md](../../SECURITY.md). An optional "disabled but visible"
state is deliberately not a framework feature: a page that wants that
renders its own `GuiItem` (with no click handler) instead of a
`GuiButton` when `viewer.hasPermission(...)` fails.

## Icons

No `Material` scattered through feature code. `IconProvider#resolve(GuiIcon)`
is the only place that turns a [`GuiIcon`](../../src/main/java/dev/universaladmin/module/GuiIcon.java)
(materialKey + label, see `ModuleDescriptor.icon()`) into a `Material`;
`MaterialIconProvider` is the default implementation (unknown key →
`PAPER` + a one-time warning, the same fallback principle as
`YamlSettingsService` for an invalid config value). The small fixed set of
framework icons (back/close/refresh/pagination/loading/empty/error/
confirm/cancel) are default methods on `IconProvider` - here too, no page
picks its own `Material`.

## Player Session

`GuiSession` (navigation history + attribute bag) and `GuiSessionManager`
(`Map<UUID, GuiSession>`) **never** hold a `Player` reference, only the
`UUID` - holding a `Player` object long-term is a classic way to
accumulate stale/dead references once the player leaves the server and a
new `Player` object is created for the same session. Every framework
class always fetches the live `Player` fresh from the triggering Bukkit
event.

A registered `AbstractGuiPage` is a singleton shared by every player (like
any other service in this codebase) - so it may hold **no** per-player
instance state itself (no `private int currentPage`!). That's exactly why
`GuiSession` exists: state that only applies to one player lives there,
keyed by its own `GuiPageId` (e.g. `core:players.list.page`), never as an
instance field of the page.

**No memory leak:** `GuiListener` removes a session as soon as the
associated inventory closes for a "real" reason (`PLAYER`, `DISCONNECT`,
...) - not on `OPEN_NEW` (that's us navigating to the next page ourselves).
`PlayerQuitEvent` additionally removes it defensively, independent of the
close-event ordering. See
[`GuiSessionManager`](../../src/main/java/dev/universaladmin/gui/GuiSessionManager.java)'s
class comment for the full argument.

## Click Handling

A single [`GuiListener`](../../src/main/java/dev/universaladmin/gui/GuiListener.java),
registered once in `UniversalAdminPlugin#bootstrapCore` - no feature
registers its own `InventoryClickEvent` handler. Detection is via
`inventory.getHolder() instanceof GuiView` (not the title string, which
could differ per locale).

Default behavior for every page: the entire click event is cancelled
(`setCancelled(true)`) - nothing can be taken out of, dragged into, or
shift-clicked out of the GUI. A click on a slot with a `GuiButton` fires
its `ClickHandler` with the matching `GuiClickType` (LEFT/SHIFT_LEFT/
RIGHT/SHIFT_RIGHT/MIDDLE/OTHER); a click on an empty/decorative slot is
only cancelled, nothing more.

`GuiView#editable(boolean)` turns off the automatic cancel, allowing free
drag/drop within the (synthetic, `GuiListener`-protected) `GuiView` -
used e.g. by
`dev.universaladmin.modules.players.gui.PlayerInventoryPage`/
`PlayerEnderChestPage` for the inventory/ender chest editor. A slot with a
registered `GuiButton` stays protected regardless (see `GuiListener`) - so
individual slots (e.g. decorative filler between two areas) can still be
locked on an editable page by placing them as a `GuiButton` with a no-op
handler instead of a plain `GuiItem`.

`GuiView#onClose(Consumer<GuiView>)` is the attachment point for an
editable page that wants to skip a separate save button: the callback runs
once the view actually closes (not when navigating to another
UniversalAdmin page - the same `OPEN_NEW` distinction as session cleanup),
by which point every drag/click by the player has already landed in the
`Inventory`. `PlayerInventoryPage`/`PlayerEnderChestPage` read the final
contents there and call `SetPlayerInventoryContentsAction`/
`SetPlayerEnderChestContentsAction` through `ActionExecutor` normally -
"live" here means "no save click needed", not "bypass `ActionExecutor` and
write straight into the real inventory" (see docs/user/modules/players.md
for the full reasoning on why a directly opened `PlayerInventory` of the
target is deliberately *not* the chosen path).

## Confirmations

`ConfirmationDialog.open(...)` renders a small inventory (not the Paper
dialog API - see "Search" below for the trade-off between the two) with a
title, description, confirm/cancel button, and a `DangerLevel`
(`NORMAL`/`WARNING`/`DANGEROUS`) that determines the confirm button's color
via `IconProvider#confirm(DangerLevel)` (lime/yellow/red wool). Ephemeral:
not registered in `GuiRegistry`, a click handler opens a fresh instance
directly with the concrete parameters (e.g. "ban player X?"). Intended for
ban/clear-inventory/restart/shutdown/entity-clear, once the respective
modules need it - not wired up yet.

## Selection

`SelectionDialog.open(...)` is pick-one-from-a-list, implemented as a thin
adapter over `AbstractListGuiPage` - the same pagination/async machinery
as any other list page, just with a one-off callback instead of a
registered `GuiPageId`.

## Search

Minecraft inventories have no text field, and this project rules out
packet hacks/ProtocolLib/NMS (see docs/development/architecture-rules.md).
Options considered:

- **Anvil GUI hack** (a renamable anvil as text input): only works via an
  "empty" recipe plus click interception, in practice a packet-/NMS-
  adjacent behavior depending on the implementation - rejected.
- **Chat input session** (intercept the player's next chat message):
  works, but collides with `AsyncPlayerChatEvent`/other plugins listening
  to chat, and the player types "somewhere in chat", not in a recognizable
  UI context - no clean state, no timeout without extra work.
- **Sign API**: forces a sign texture/block placement as the input
  surface - functional, but a visual and UX mismatch for a menu that's
  otherwise entirely inventory-based.
- **Paper Dialog API** (`io.papermc.paper.dialog.Dialog`,
  `Player#showDialog`, `TextDialogInput`): a server-defined,
  client-rendered input form - not a packet hack, an official part of the
  target Paper version (confirmed in this project's `paper-api` jar.
  **Chosen.**

[`GuiTextInput.request(...)`](../../src/main/java/dev/universaladmin/gui/GuiTextInput.java)
builds a `Dialog` with a text field plus submit/cancel button; each button
is a `DialogAction.customClick(...)` callback with `uses(1)` and a
two-minute lifetime, so a dialog that's never answered can't be replayed.
The callback runs (like a command or a click event) on the main thread -
a caller that needs blocking IO afterward has to hop off via
`TaskScheduler` itself, same as everywhere else.

A "search" button on a future list page would call, for example:

```java
GuiTextInput.request(
        viewer, title, label, previousQuery, submitLabel, cancelLabel,
        query -> { /* filter results, reopen the page */ },
        () -> { /* do nothing, or go back to the list */ });
```

## Main Menu Skeleton

`MainMenuPage` (`/admin` with no arguments, see below) shows one button per
built-in module, filtered by:

1. **The module is actually `ENABLED`** (`ModuleRegistry#state(...)`) - a
   module disabled via `config.yml` or failed doesn't show up at all,
   never as "disabled".
2. **Permission** - the module's own existing permission node
   (`universaladmin.players.view`, `universaladmin.moderation.use`, ...),
   via `GuiButton`'s hide-by-default rule.

`MainMenuPage` only references each module through its `ModuleId` literal
(`ModuleId.core("players")`, identical to what each module class defines
for itself) - **not** through an import of the concrete
`dev.universaladmin.modules.*` class. That keeps the GUI framework package
generic and not dependent "upward" on individual built-in modules, see
the "Package Rules" section of the
[development rules](architecture-rules.md).

Every button today opens a `PlaceholderGuiPage` ("not built yet") under a
stable `GuiPageId` (`core:<module>.home`, e.g. `core:players.home`). A
module that later gets a real GUI registers its own page under the same
`GuiPageId` - `GuiRegistry` allows exactly one owner per id, so the
placeholder registration in `UniversalAdminPlugin#registerMainMenu` then
has to be removed for that module.

## The `/admin` Command

- `/admin` (no argument): opens `MainMenuPage` for a player (permission
  `universaladmin.menu.open`, otherwise `error.no-permission`). For
  console/command blocks (can't see an inventory), the previous text
  status report remains.
- `/admin reload`: unchanged, entirely separate from the menu - see
  [docs/user/configuration.md#reload](../user/configuration.md#reload).

## Extension Readiness

Like the rest of the platform (see
[docs/architecture/decisions/0005-extension-ready-design.md](../architecture/decisions/0005-extension-ready-design.md)),
this framework is built so a future external extension could use the same
building blocks as a built-in module - `AbstractGuiPage`, `GuiButton`,
`GuiFramework` aren't coupled to `dev.universaladmin.modules.*`. But there
is **no public, versioned API boundary yet** (no `universaladmin-api`
module, see ROADMAP.md Phase 4) - so this package can still change without
a compatibility promise.

## A Module Builds a Page: Example

Suppose `PlayersModule` got a real "player list" page (not yet part of
this task, but the pattern is already usable). The service already exists
(`PlayerService`, see
[docs/development/adding-module.md](adding-module.md)):

```java
package dev.universaladmin.modules.players.gui;

public final class PlayerListPage extends AbstractListGuiPage<PlayerProfile> {

    public static final GuiPageId ID = GuiPageId.core("players.home");

    private final PlayerService playerService;

    public PlayerListPage(
            GuiFramework framework, MessageService messages, TaskScheduler scheduler, PlayerService playerService) {
        super(ID, framework, messages, scheduler);
        this.playerService = playerService;
    }

    @Override
    protected Component title(Player viewer) {
        return text("players.gui.title"); // a new lang key, no hardcoded string
    }

    @Override
    protected CompletableFuture<List<PlayerProfile>> loadItems(Player viewer) {
        return playerService.allProfiles(); // service, no repository/SQL knowledge here
    }

    @Override
    protected GuiItem render(PlayerProfile profile) {
        return GuiItem.of(Material.PLAYER_HEAD, Component.text(profile.lastKnownName()));
    }

    @Override
    protected void onSelect(GuiClickContext ctx, PlayerProfile profile) {
        ctx.open(new PlayerDetailPage(framework, messages, profile)); // forward navigation
    }
}
```

Registration in `PlayersModule#onEnable` simply replaces the previous
`PlaceholderGuiPage` registration for `core:players.home`:

```java
context.platform().guiPages().register(
        new PlayerListPage(context.platform().guiFramework(), context.platform().messages(),
                context.platform().scheduler(), playerService));
```

No `InventoryClickListener`, no pagination code, no manual main-thread
hopping - `AbstractListGuiPage` provides all of that. The click handler
(`onSelect`) only calls navigation and (indirectly, through the service)
domain logic, never SQL or computation directly - see "The One
Architecture Rule Still Holds" above.
