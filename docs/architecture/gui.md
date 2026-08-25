# GUI

## The Frame

`GuiPage` ([`src/main/java/dev/universaladmin/gui/GuiPage.java`](../../src/main/java/dev/universaladmin/gui/GuiPage.java))
is still the only *interface* a page is opened through:

```java
public interface GuiPage {
    GuiPageId id();
    void open(Player viewer);
}
```

`GuiRegistry` manages registered pages under their `GuiPageId`. Only the
GUI framework's own classes (`GuiView`, `GuiListener`, `AbstractGuiPage`)
work directly with Bukkit inventory APIs/click events today - a feature
page no longer implements `GuiPage` by hand, it extends
`AbstractGuiPage`/`AbstractListGuiPage` and gets navigation, permission
filtering, pagination, and async loading for free. The complete framework
(building blocks, an example, design decisions like choosing the Paper
dialog API for text input) is in
[docs/development/gui-framework.md](../development/gui-framework.md).

## The One Rule

A click handler in a `GuiPage` implementation calls a service or an
`Action` - it contains no logic itself. Concretely that means: no direct
database access, no computation, no authorization check with its own logic
(that belongs in the service/action, not duplicated in the GUI). Violating
this is the most common way an admin plugin turns into an unmaintainable
pile of click handlers - see the
[development rules](../development/architecture-rules.md).

## Dependency Injection Instead of a Global Context

A page gets the services/actions it needs through its constructor - not by
accessing `UniversalAdmin` at open time. That keeps a page testable (the
services can be faked) and makes a page's actual dependencies explicit
instead of hiding them behind a "has access to everything" object.
`GuiFramework` (sessions/icons, see gui-framework.md) is the one accepted
exception - a narrow, GUI-framework-scoped bundle, not access to the whole
platform.

```java
public final class PlayerListPage extends AbstractListGuiPage<PlayerProfile> {
    private final PlayerService playerService; // not: UniversalAdmin platform

    public PlayerListPage(
            GuiFramework framework, MessageService messages, TaskScheduler scheduler, PlayerService playerService) {
        super(GuiPageId.core("players.home"), framework, messages, scheduler);
        this.playerService = playerService;
    }
    // ...
}
```

A complete, runnable example (including rendering, navigation,
registration in the module) is in
[docs/development/gui-framework.md](../development/gui-framework.md#a-module-builds-a-page-example).

## Current State

The framework exists (its own, minimal menu system - no external inventory
GUI library, see
[gui-framework.md](../development/gui-framework.md)) and is used by the
main menu (`MainMenuPage`), a placeholder page per not-yet-built-out
module, and the `players` feature GUI (player browser, profile, actions,
editable inventory/ender chest - see
[docs/user/modules/players.md](../user/modules/players.md)). The remaining
seven modules are [ROADMAP.md](../../ROADMAP.md) Phase 1/2.

## Relationship to the Web App

A `GuiPage` and a future web page for the same feature (see
[web-future.md](web-future.md)) ideally call the same service/action and
only differ in presentation. That's the reason GUI click handlers may not
contain logic: any logic in the GUI is logic the web app can't reuse.
