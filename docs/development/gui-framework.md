# GUI-Framework

Dieses Dokument beschreibt das wiederverwendbare Ingame-GUI-Framework unter
[`dev.universaladmin.gui`](../../src/main/java/dev/universaladmin/gui) und,
am Ende, wie ein Modul darauf eine eigene Seite baut. Es ersetzt den
"es gibt noch keine Framework-Entscheidung"-Stand aus
[docs/architecture/gui.md](../architecture/gui.md) - dieses Dokument bleibt
die Kurzfassung der Architekturregel (Frontend ruft nur Service/Action auf),
hier steht das *Wie*.

Aktuell existieren zwei konkrete Seiten aus dem Framework selbst: das
Hauptmenü (`MainMenuPage`) und eine Platzhalterseite pro eingebautem Modul
(`PlaceholderGuiPage`) - siehe "Main Menu Skeleton" unten. Kein Modul hat
heute eine echte Feature-GUI; das ist bewusst außerhalb dieses Tasks (siehe
ROADMAP.md Phase 1).

## Die eine Architekturregel bleibt bestehen

```
GUI → Application Service / Action → Domain
```

Ein Klick-Handler (`GuiButton.ClickHandler`) ruft einen Service oder eine
`Action` auf, nie SQL, nie mehrzeilige Berechnungen. Das Framework ändert
daran nichts - es gibt dieser Regel nur eine gemeinsame, getestete
Grundlage statt dass jede Seite ihren eigenen `InventoryClickListener`
mitbringt. Siehe [docs/architecture/decisions/0004-gui-service-separation.md](../architecture/decisions/0004-gui-service-separation.md).

## Bausteine im Überblick

| Baustein | Klasse | Zweck |
|---|---|---|
| Seite | [`GuiPage`](../../src/main/java/dev/universaladmin/gui/GuiPage.java) | Das minimale, seit ADR-0004 bestehende Interface (`id()`, `open(Player)`). Jede Framework-Seite implementiert es über `AbstractGuiPage`. |
| Basisklasse | [`AbstractGuiPage`](../../src/main/java/dev/universaladmin/gui/AbstractGuiPage.java) | Rendert Navigationsleiste (Back/Refresh/Close), Rest ist `renderContent(...)`. |
| Listen-Basisklasse | [`AbstractListGuiPage<T>`](../../src/main/java/dev/universaladmin/gui/AbstractListGuiPage.java) | Async laden + paginieren + Loading/Empty/Error-Zustand, einmal implementiert. |
| Session | [`GuiSession`](../../src/main/java/dev/universaladmin/gui/GuiSession.java) / [`GuiSessionManager`](../../src/main/java/dev/universaladmin/gui/GuiSessionManager.java) | Pro-Spieler-Zustand (Navigationshistorie, Attribute) - siehe "Player Session". |
| Sichtbares Element | [`GuiItem`](../../src/main/java/dev/universaladmin/gui/GuiItem.java) / [`GuiButton`](../../src/main/java/dev/universaladmin/gui/GuiButton.java) | `GuiItem` ist rein optisch, `GuiButton` hat zusätzlich Klick-Handler + optionale Permission. |
| Rendering | [`GuiView`](../../src/main/java/dev/universaladmin/gui/GuiView.java) | Ein gerendertes Inventory für einen Spieler; einziger Ort, der `Bukkit.createInventory` aufruft. |
| Layout | [`GuiLayout`](../../src/main/java/dev/universaladmin/gui/GuiLayout.java) | Die eine Slot-Tabelle - siehe "Slots". |
| Pagination | [`Pagination<T>`](../../src/main/java/dev/universaladmin/gui/Pagination.java) | Reine Logik (kein Bukkit), von `AbstractListGuiPage` genutzt. |
| Klick-Routing | [`GuiListener`](../../src/main/java/dev/universaladmin/gui/GuiListener.java) | Der eine Bukkit-Listener für alle GUIs. |
| Icons | [`IconProvider`](../../src/main/java/dev/universaladmin/gui/IconProvider.java) / [`MaterialIconProvider`](../../src/main/java/dev/universaladmin/gui/MaterialIconProvider.java) | Siehe "Icons". |
| Bestätigung | [`ConfirmationDialog`](../../src/main/java/dev/universaladmin/gui/ConfirmationDialog.java) | Ja/Nein mit Danger-Level. |
| Auswahl | [`SelectionDialog`](../../src/main/java/dev/universaladmin/gui/SelectionDialog.java) | Pick-one aus einer Liste, baut auf `AbstractListGuiPage` auf. |
| Texteingabe | [`GuiTextInput`](../../src/main/java/dev/universaladmin/gui/GuiTextInput.java) | Freitext über die Paper-Dialog-API - siehe "Search". |
| Bündel | [`GuiFramework`](../../src/main/java/dev/universaladmin/gui/GuiFramework.java) | `GuiSessionManager` + `IconProvider`, per Konstruktor an jede Seite gereicht (`UniversalAdmin#guiFramework()`). |

## Slots

Keine Magic Numbers pro Feature - jede Seite rendert in dasselbe 6-reihige
Layout, definiert einmal in `GuiLayout`:

```
Reihe 0: Back (Slot 0) · Refresh (Slot 4) · Close (Slot 8)
Reihe 1-4: Content-Bereich, 36 Slots - GuiLayout.contentSlot(0..35)
Reihe 5: Previous (48) · Seitenanzeige (49) · Next (50)
```

Eine Seite, die eigenen Content rendert, nutzt ausschließlich
`GuiLayout.contentSlot(index)` statt einer Zahl - so bleibt "wo ist der
Zurück-Button" plattformweit eine einzige Antwort.

## Navigation

Der gesamte Navigationsmechanismus ist genau ein Konzept: **eine Seite
öffnen ist immer `GuiPage#open(Player)`.**

- **Vorwärts** (`GuiClickContext#open(GuiPage)`): merkt sich, wie die
  aktuelle Seite neu gezeichnet wird (`() -> currentPage.open(viewer)`),
  legt das auf `GuiSession`s Historie, öffnet dann die nächste Seite.
- **Zurück** (`GuiClickContext#back()`): holt den obersten Historie-Eintrag
  und führt ihn aus - das ruft schlicht wieder `open(...)` auf der
  vorherigen Seite auf. Ohne Historie schließt `back()` das Inventar.
- **Refresh**: der Refresh-Button in `AbstractGuiPage` ruft `this.open(viewer)`
  erneut auf derselben Seite auf - kein Sonderfall, keine zweite Methode.

Kein separater Stack von `GuiPageId`s, der von Hand synchron gehalten
werden müsste - "wie komme ich hierher zurück" ist immer ausführbarer Code,
kein Datum.

## Pagination

`Pagination<T>` ist reine, ungetestete-Bukkit-freie Logik: Slice-Berechnung,
Clamping bei leerer/zu kurzer Liste, `hasPrevious()`/`hasNext()`. Ein
Seitenwechsel ändert nur die Seitenzahl (in der `GuiSession` unter
`<pageId>.page` abgelegt) und ruft `open(viewer)` erneut auf - dieselbe
Refresh-Mechanik wie oben.

## Async Data

`AbstractListGuiPage<T>` ist die vollständige Referenz für den geforderten
Ablauf:

```
open() → Loading-Platzhalter rendern (sofort)
       → loadItems(viewer) auf dem TaskScheduler
       → whenComplete(...) → scheduler.runOnMainThread(...)
       → GuiView erneut befüllen
```

**Keine Inventory-Mutation außerhalb des Main-Threads** - siehe
[docs/architecture/threading.md](../architecture/threading.md). Ein
Ergebnis, das eintrifft, nachdem der Spieler die Seite verlassen hat, wird
erkannt (`viewer.getOpenInventory().getTopInventory().getHolder() == view`)
und verworfen, statt ein geschlossenes/fremdes Inventory zu mutieren.

Ein Fehler im geladenen `CompletableFuture` rendert den Error-Zustand
(`gui.error`, Refresh versucht es erneut); eine leere Liste rendert den
Empty-Zustand (`gui.empty`); während des Ladens den Loading-Zustand
(`gui.loading`) - alle drei über `IconProvider`/`MessageService`, nie fest
codiert.

## Permissions

Ein `GuiButton` trägt optional einen `PermissionNode`. Die Regel ist
mechanisch erzwungen, nicht nur dokumentiert: `GuiView#place(int, GuiButton, Player)`
platziert den Button nur, wenn `viewer.hasPermission(...)` wahr ist -
andernfalls bleibt der Slot leer ("hide by default", kein disabled-grau).
`GuiButton#handle` prüft beim Klick sicherheitshalber erneut (Verteidigung
gegen einen Klick, der mit einer Rechteänderung wettläuft).

**GUI-Permission ist ausschließlich Darstellung.** Der Service/die Action,
die ein Button am Ende aufruft, muss selbst erneut autorisieren - siehe
[docs/architecture/decisions/0004-gui-service-separation.md](../architecture/decisions/0004-gui-service-separation.md)
und [SECURITY.md](../../SECURITY.md). Ein optionaler "disabled, aber
sichtbar"-Zustand ist bewusst kein Framework-Feature: eine Seite, die das
will, rendert selbst ein `GuiItem` (ohne Klick-Handler) statt eines
`GuiButton`, wenn `viewer.hasPermission(...)` fehlschlägt.

## Icons

Kein `Material` verstreut im Feature-Code. `IconProvider#resolve(GuiIcon)`
ist die einzige Stelle, die aus einem [`GuiIcon`](../../src/main/java/dev/universaladmin/module/GuiIcon.java)
(materialKey + Label, siehe `ModuleDescriptor.icon()`) ein `Material` macht;
`MaterialIconProvider` ist die Standardimplementierung (unbekannter Key →
`PAPER` + einmalige Warnung, dasselbe Fallback-Prinzip wie `YamlSettingsService`
bei einem ungültigen Config-Wert). Die feste Handvoll Framework-Icons
(Back/Close/Refresh/Pagination/Loading/Empty/Error/Confirm/Cancel) sind
Default-Methoden auf `IconProvider` - auch hier wählt keine Seite selbst
ein `Material`.

## Player Session

`GuiSession` (Navigationshistorie + Attribut-Bag) und `GuiSessionManager`
(`Map<UUID, GuiSession>`) halten **niemals** eine `Player`-Referenz, nur
die `UUID` - ein `Player`-Objekt langfristig zu halten ist ein klassischer
Weg, veraltete/tote Referenzen anzusammeln, sobald der Spieler den Server
verlässt und ein neues `Player`-Objekt für dieselbe Session entsteht. Jede
Framework-Klasse holt sich den lebenden `Player` immer frisch aus dem
auslösenden Bukkit-Event.

Eine registrierte `AbstractGuiPage` ist ein Singleton, das von jedem
Spieler geteilt wird (wie jeder andere Service in diesem Codebase) - sie
darf also selbst **keinen** Instanzzustand pro Spieler halten (kein
`private int currentPage`!). Genau dafür existiert `GuiSession`: Zustand,
der nur für einen Spieler gilt, lebt dort, keyed über den eigenen
`GuiPageId` (z. B. `core:players.list.page`), nie als Instanzfeld der Seite.

**Kein Memory Leak:** `GuiListener` entfernt eine Session, sobald das
zugehörige Inventory aus einem "echten" Grund schließt (`PLAYER`,
`DISCONNECT`, ...) - nicht bei `OPEN_NEW` (das sind wir selbst, die zur
nächsten Seite navigieren). `PlayerQuitEvent` entfernt zusätzlich
defensiv, unabhängig von der Close-Event-Reihenfolge. Siehe
[`GuiSessionManager`](../../src/main/java/dev/universaladmin/gui/GuiSessionManager.java)s
Klassenkommentar für das vollständige Argument.

## Click Handling

Ein einziger [`GuiListener`](../../src/main/java/dev/universaladmin/gui/GuiListener.java),
registriert einmal in `UniversalAdminPlugin#bootstrapCore` - kein Feature
registriert einen eigenen `InventoryClickEvent`-Handler. Erkennung über
`inventory.getHolder() instanceof GuiView` (nicht über den Titel-String,
der sich pro Locale unterscheiden könnte).

Standardverhalten für jede Seite: das gesamte Klick-Event wird abgebrochen
(`setCancelled(true)`) - nichts kann aus dem GUI entnommen, hineingezogen
oder per Shift-Klick verschoben werden. Ein Klick auf einen Slot mit
`GuiButton` löst dessen `ClickHandler` mit dem passenden `GuiClickType`
(LEFT/SHIFT_LEFT/RIGHT/SHIFT_RIGHT/MIDDLE/OTHER) aus; ein Klick auf einen
leeren/dekorativen Slot wird nur abgebrochen, nichts weiter.

`GuiView#editable(boolean)` schaltet den automatischen Cancel ab, sodass
freies Ziehen/Ablegen innerhalb der (synthetischen, per `GuiListener`
geschützten) `GuiView` möglich wird - genutzt z. B. von
`dev.universaladmin.modules.players.gui.PlayerInventoryPage`/
`PlayerEnderChestPage` für den Inventar-/Enderchest-Editor. Ein Slot mit
einem registrierten `GuiButton` bleibt dabei immer geschützt (siehe
`GuiListener`) - so lassen sich einzelne Slots (z. B. dekorative Filler
zwischen zwei Bereichen) auch in einer editierbaren Seite sperren, indem man
sie als `GuiButton` mit No-op-Handler statt als reines `GuiItem` platziert.

`GuiView#onClose(Consumer<GuiView>)` ist der Anschlusspunkt für eine
editierbare Seite, die ohne separaten Save-Button auskommen will: der
Callback läuft, sobald die View wirklich schließt (nicht beim Navigieren zu
einer anderen UniversalAdmin-Seite - dieselbe `OPEN_NEW`-Unterscheidung wie
beim Session-Cleanup), zu dem Zeitpunkt ist jeder Drag/Klick der Spielerin
bereits im `Inventory` angekommen. `PlayerInventoryPage`/`PlayerEnderChestPage`
lesen dort den finalen Inhalt aus und rufen ganz normal
`SetPlayerInventoryContentsAction`/`SetPlayerEnderChestContentsAction` über
`ActionExecutor` auf - "live" heißt hier "kein Save-Klick nötig", nicht "am
`ActionExecutor` vorbei direkt ins echte Inventory schreiben" (siehe
docs/user/modules/players.md für die volle Begründung, warum ein direkt
geöffnetes `PlayerInventory` des Ziels bewusst *nicht* der gewählte Weg ist).

## Confirmations

`ConfirmationDialog.open(...)` rendert ein kleines Inventory (nicht die
Paper-Dialog-API - siehe "Search" unten für die Abwägung, wann welche
Lösung passt) mit Titel, Beschreibung, Confirm-/Cancel-Button und einem
`DangerLevel` (`NORMAL`/`WARNING`/`DANGEROUS`), der die Confirm-Button-Farbe
über `IconProvider#confirm(DangerLevel)` bestimmt (Lime/Gelb/Rot-Wolle).
Ephemer: nicht in `GuiRegistry` registriert, ein Klick-Handler öffnet
direkt eine neue Instanz mit den konkreten Parametern (z. B. "Spieler X
bannen?"). Vorgesehen für Ban/Clear-Inventory/Restart/Shutdown/Entity-Clear,
sobald die jeweiligen Module das brauchen - noch nicht verdrahtet.

## Selection

`SelectionDialog.open(...)` ist Pick-one-aus-einer-Liste, implementiert als
dünner Adapter über `AbstractListGuiPage` - dieselbe Pagination/Async-
Maschinerie wie jede andere Listenseite, nur mit einem einmaligen Callback
statt einer registrierten `GuiPageId`.

## Search

Minecraft-Inventories haben kein Textfeld, und dieses Projekt schließt
Packet-Hacks/ProtocolLib/NMS aus (siehe docs/development/architecture-rules.md). Geprüfte Optionen:

- **Anvil-GUI-Hack** (umbenennbarer Amboss als Texteingabe): funktioniert
  nur über ein "leeres" Rezept + Klick-Interception, ist in der Praxis ein
  Packet-/NMS-nahes Verhalten je nach Implementierung - verworfen.
- **Chat-Input-Session** (nächste Chat-Nachricht des Spielers abfangen):
  funktioniert, kollidiert aber mit `AsyncPlayerChatEvent`/anderen Plugins,
  die auf Chat hören, und der Spieler tippt "irgendwo im Chat", nicht in
  einem erkennbaren UI-Kontext - kein sauberer Zustand, kein Timeout ohne
  Zusatzaufwand.
- **Sign API**: erzwingt eine Schild-Textur/Blockplatzierung als
  Eingabefläche - funktional, aber visuell und UX-mäßig ein Fremdkörper
  für ein Menü, das sonst komplett Inventory-basiert ist.
- **Paper Dialog API** (`io.papermc.paper.dialog.Dialog`,
  `Player#showDialog`, `TextDialogInput`): serverseitig definierte,
  clientseitig gerenderte Eingabemaske - kein Packet-Hack, offizieller
  Bestandteil der Ziel-Paper-Version (bestätigt im `paper-api`-Jar dieses
  Projekts). **Gewählt.**

[`GuiTextInput.request(...)`](../../src/main/java/dev/universaladmin/gui/GuiTextInput.java)
baut einen `Dialog` mit einem Textfeld plus Submit-/Cancel-Button; jeder
Button ist ein `DialogAction.customClick(...)`-Callback mit `uses(1)` und
zwei Minuten Lebensdauer, damit ein nie beantworteter Dialog nicht
wiederholt/repliziert werden kann. Der Callback läuft (wie ein Command
oder ein Klick-Event) auf dem Main-Thread - ein Aufrufer, der daraufhin
blockierendes IO braucht, muss selbst über `TaskScheduler` abspringen,
genau wie überall sonst.

Ein "Search"-Button einer künftigen Listenseite ruft also z. B.:

```java
GuiTextInput.request(
        viewer, title, label, previousQuery, submitLabel, cancelLabel,
        query -> { /* Ergebnisse filtern, Seite neu öffnen */ },
        () -> { /* nichts tun oder zur Liste zurück */ });
```

## Main Menu Skeleton

`MainMenuPage` (`/admin` ohne Argumente, siehe unten) zeigt einen Button
pro eingebautem Modul, gefiltert nach:

1. **Modul tatsächlich `ENABLED`** (`ModuleRegistry#state(...)`) - ein per
   `config.yml` deaktiviertes oder fehlgeschlagenes Modul taucht gar nicht
   auf, nicht als "disabled" angezeigt.
2. **Berechtigung** - dasselbe bestehende Permission-Node des jeweiligen
   Moduls (`universaladmin.players.view`, `universaladmin.moderation.use`,
   ...), über `GuiButton`s Hide-by-default-Regel.

`MainMenuPage` referenziert jedes Modul nur über sein `ModuleId`-Literal
(`ModuleId.core("players")`, identisch zu dem, was jede Modulklasse selbst
für sich definiert) - **nicht** über einen Import der konkreten
`dev.universaladmin.modules.*`-Klasse. Das GUI-Framework-Package bleibt
damit generisch und hängt nicht "nach oben" von einzelnen Built-in-Modulen
ab, siehe [Entwicklungsregeln](architecture-rules.md)s "Package-Regeln"-Abschnitt.

Jeder Button öffnet heute eine `PlaceholderGuiPage` ("noch nicht gebaut")
unter einer stabilen `GuiPageId` (`core:<modul>.home`, z. B.
`core:players.home`). Ein Modul, das später eine echte GUI bekommt,
registriert seine eigene Seite unter derselben `GuiPageId` - `GuiRegistry`
erlaubt genau einen Eigentümer pro Id, die Platzhalterregistrierung in
`UniversalAdminPlugin#registerMainMenu` muss dann für dieses Modul entfernt
werden.

## `/admin`-Command

- `/admin` (kein Argument): öffnet für einen Spieler `MainMenuPage`
  (Permission `universaladmin.menu.open`, sonst `error.no-permission`).
  Für Konsole/Command-Block (kann kein Inventory sehen) bleibt der
  bisherige Text-Statusbericht erhalten.
- `/admin reload`: unverändert, komplett getrennt vom Menü - siehe
  [docs/user/configuration.md#reload](../user/configuration.md#reload).

## Extension-Fähigkeit

Wie der Rest der Plattform (siehe
[docs/architecture/decisions/0005-extension-ready-design.md](../architecture/decisions/0005-extension-ready-design.md))
ist dieses Framework so gebaut, dass eine künftige externe Extension
dieselben Bausteine nutzen könnte wie ein eingebautes Modul - `AbstractGuiPage`,
`GuiButton`, `GuiFramework` sind nicht an `dev.universaladmin.modules.*`
gekoppelt. Es gibt aber **noch keine öffentliche, versionierte API-Grenze**
(kein `universaladmin-api`-Modul, siehe ROADMAP.md Phase 4) - dieses
Package kann sich also noch ohne Kompatibilitätsversprechen ändern.

## Ein Modul baut eine Seite: Beispiel

Angenommen, `PlayersModule` bekäme eine echte "Spielerliste"-Seite (noch
nicht Teil dieses Tasks, aber das Muster ist bereits nutzbar). Der Service
existiert schon (`PlayerService`, siehe
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
        return text("players.gui.title"); // neuer lang-Key, kein hartcodierter String
    }

    @Override
    protected CompletableFuture<List<PlayerProfile>> loadItems(Player viewer) {
        return playerService.allProfiles(); // Service, keine Repository-/SQL-Kenntnis hier
    }

    @Override
    protected GuiItem render(PlayerProfile profile) {
        return GuiItem.of(Material.PLAYER_HEAD, Component.text(profile.lastKnownName()));
    }

    @Override
    protected void onSelect(GuiClickContext ctx, PlayerProfile profile) {
        ctx.open(new PlayerDetailPage(framework, messages, profile)); // Vorwärtsnavigation
    }
}
```

Registrierung in `PlayersModule#onEnable` ersetzt einfach die bisherige
`PlaceholderGuiPage`-Registrierung für `core:players.home`:

```java
context.platform().guiPages().register(
        new PlayerListPage(context.platform().guiFramework(), context.platform().messages(),
                context.platform().scheduler(), playerService));
```

Kein `InventoryClickListener`, kein Pagination-Code, kein manuelles
Main-Thread-Hopping - alles davon liefert `AbstractListGuiPage`. Der
Klick-Handler (`onSelect`) ruft ausschließlich Navigation und (indirekt,
über den Service) Domain-Logik auf, nie SQL oder Berechnung direkt - siehe
"Die eine Architekturregel bleibt bestehen" oben.
