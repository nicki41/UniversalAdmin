# Web App (Future)

Auch dieses Dokument beschreibt eine **geplante**, noch nicht gebaute
Fähigkeit - siehe
[decisions/0006-optional-web-architecture.md](decisions/0006-optional-web-architecture.md)
für die Begründung, warum dafür heute kein Gradle-Modul existiert.

## Kernanforderung: der Core läuft vollständig ohne Web

Die Web-App ist optional und obendrauf, nicht Voraussetzung. Ein Server-
Betreiber, der nur die Ingame-GUI/Commands nutzt, installiert nie einen
Webserver-Teil. Das ist bereits strukturell erzwungen: nichts in
`dev.universaladmin.core`, `.module`, `.action`, `.storage` etc. hat eine
Abhängigkeit in Richtung "Web" - die Abhängigkeit zeigt nur in die andere
Richtung (eine künftige Web-Schicht hängt vom Core ab, nicht umgekehrt).

## Dieselben Services, dieselben Actions

Die Web-App soll dieselben Application-Services und `Action`s aufrufen wie
GUI und Commands - kein separater "Web-Businesslogik"-Pfad. Das ist der
Grund, warum `Actor`/`ActorType` bereits einen `WEB`-Fall kennt (siehe
[actions.md](actions.md)) und warum GUI-Click-Handler keine Logik enthalten
dürfen (siehe [gui.md](gui.md)): jede Logik, die nur im Click-Handler
steckt, müsste für die Web-App neu geschrieben werden.

## Geplante Bausteine (nicht gebaut)

- **`universaladmin-web`-Gradle-Modul** - separater Prozess oder embedded
  Server (Entscheidung offen), hängt von `universaladmin-api` ab (siehe
  extensions-future.md), nicht vom Core-Internals direkt.
- **REST-API** über dieselben Actions/Services.
- **WebSockets/Live-Updates** für Dashboard-Widgets und Live-Ansichten
  (z. B. Online-Spieler, Performance-Graphen).
- **Web-Authentifizierung**, getrennt vom Minecraft-Account (ein
  Server-Admin hat nicht zwingend einen Minecraft-Account für den
  Web-Zugriff) - Mechanismus offen.
- **Dashboard Widgets** als eigener Erweiterungspunkt (siehe
  extensions-future.md), damit Extensions eigene Web-Ansichten beisteuern
  können, ohne den Core-Web-Code zu ändern.

## Was das für heutigen Code bedeutet

- Kein Service/keine Action darf etwas annehmen, das nur in einem
  Ingame-Kontext existiert (z. B. direkt einen `org.bukkit.entity.Player`
  als Parameter verlangen, wo eine `UUID`/ein `Actor` reichen würde) - das
  würde die Web-App später zwingen, einen Fake-`Player` zu bauen. Siehe
  `Action`/`Actor` in [actions.md](actions.md) für das bestehende Muster.
- `MessageService`/`MessageKey` existieren bereits getrennt von der GUI,
  damit dieselben Übersetzungen später von einer Web-Ansicht wiederverwendet
  werden können. Konkret: `MessageService.get(key, args...)` liefert einen
  reinen, parametersubstituierten `String` (kann noch MiniMessage-Markup
  wie `<red>` enthalten, aber keine Adventure-`Component`-Instanz). Die
  Ingame-Schicht wandelt das über `dev.universaladmin.localization.ComponentMessages`
  in eine `Component` um; eine Web-Ansicht würde denselben String
  stattdessen in HTML/CSS übersetzen (MiniMessage-Tags → CSS-Klassen o. Ä.)
  - ein eigener, noch nicht existierender Renderer, kein Teil von
  `MessageService` selbst. Siehe
  [docs/development/settings.md](../development/settings.md#localization).
