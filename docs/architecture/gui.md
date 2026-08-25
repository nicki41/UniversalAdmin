# GUI

## Der Rahmen

`GuiPage` ([`src/main/java/dev/universaladmin/gui/GuiPage.java`](../../src/main/java/dev/universaladmin/gui/GuiPage.java))
ist nach wie vor die einzige *Schnittstelle*, über die eine Seite geöffnet
wird:

```java
public interface GuiPage {
    GuiPageId id();
    void open(Player viewer);
}
```

`GuiRegistry` verwaltet registrierte Seiten unter ihrer `GuiPageId`. Direkt
mit Bukkit-Inventory-APIs/Klick-Events arbeiten heute nur noch die Klassen
des GUI-Frameworks selbst (`GuiView`, `GuiListener`, `AbstractGuiPage`) -
eine Feature-Seite implementiert `GuiPage` nicht mehr von Hand, sondern
erweitert `AbstractGuiPage`/`AbstractListGuiPage` und bekommt Navigation,
Permission-Filterung, Pagination und async Laden fertig mit. Das
vollständige Framework (Bausteine, Beispiel, Design-Entscheidungen wie die
Wahl der Paper-Dialog-API für Texteingabe) steht in
[docs/development/gui-framework.md](../development/gui-framework.md).

## Die eine Regel

Ein Klick-Handler in einer `GuiPage`-Implementierung ruft einen Service
oder eine `Action` auf - er enthält selbst keine Logik. Konkret heißt das:
kein direkter Datenbankzugriff, keine Berechnungen, keine
Berechtigungsprüfung mit eigener Logik (die gehört in den Service/die
Action, nicht doppelt in die GUI). Verstöße dagegen sind der häufigste Weg,
wie ein Admin-Plugin zu einer unwartbaren Sammlung von Klick-Handlern wird
- siehe [Entwicklungsregeln](../development/architecture-rules.md).

## Dependency Injection statt globalem Kontext

Eine Seite bekommt die Services/Actions, die sie braucht, über ihren
Konstruktor - nicht durch Zugriff auf `UniversalAdmin` zur Öffnungszeit.
Das hält eine Seite testbar (die Services lassen sich faken) und macht die
tatsächlichen Abhängigkeiten einer Seite explizit sichtbar, statt sie
hinter einem "hat Zugriff auf alles"-Objekt zu verstecken. `GuiFramework`
(Sessions/Icons, siehe gui-framework.md) ist die eine akzeptierte Ausnahme
- ein schmales, GUI-Framework-scoped Bündel, kein Zugriff auf die ganze
Plattform.

```java
public final class PlayerListPage extends AbstractListGuiPage<PlayerProfile> {
    private final PlayerService playerService; // nicht: UniversalAdmin platform

    public PlayerListPage(
            GuiFramework framework, MessageService messages, TaskScheduler scheduler, PlayerService playerService) {
        super(GuiPageId.core("players.home"), framework, messages, scheduler);
        this.playerService = playerService;
    }
    // ...
}
```

Vollständiges, lauffähiges Beispiel (inkl. Rendering, Navigation,
Registrierung im Modul) in
[docs/development/gui-framework.md](../development/gui-framework.md#ein-modul-baut-eine-seite-beispiel).

## Aktueller Stand

Das Framework existiert (eigenes, minimales Menü-System - keine externe
Inventory-GUI-Library, siehe [gui-framework.md](../development/gui-framework.md))
und wird vom Hauptmenü (`MainMenuPage`), einer Platzhalterseite pro noch
nicht ausgebautem Modul, und der `players`-Feature-GUI (Player-Browser,
Profil, Actions, editierbares Inventar/Enderchest - siehe
[docs/user/modules/players.md](../user/modules/players.md)) genutzt. Die
übrigen sieben Module bleiben [ROADMAP.md](../../ROADMAP.md) Phase 1/2.

## Web-App-Bezug

Eine `GuiPage` und eine künftige Web-Seite für dieselbe Funktion (siehe
[web-future.md](web-future.md)) rufen idealerweise denselben Service/dieselbe
Action auf und unterscheiden sich nur in der Darstellung. Das ist der
Grund, warum GUI-Click-Handler keine Logik enthalten dürfen: jede Logik in
der GUI ist Logik, die die Web-App nicht wiederverwenden kann.
