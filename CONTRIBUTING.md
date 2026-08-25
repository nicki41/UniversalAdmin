# Contributing

Danke für dein Interesse an UniversalAdmin. Dieses Dokument beschreibt, was
du zum Mitarbeiten brauchst und woran ein Pull Request gemessen wird.

Die kurze Version: lies
[docs/development/architecture-rules.md](docs/development/architecture-rules.md)
und [ARCHITECTURE.md](ARCHITECTURE.md), bevor du etwas baust. Die
Architekturregeln sind verbindlich, nicht empfehlend - sie sind der Grund,
warum dieselbe Logik später von GUI, Command und Web-API genutzt werden kann.

## Setup

Du brauchst:

- **Java 25.** Der Gradle-Wrapper holt sich per
  `foojay-resolver-convention` automatisch eine passende Toolchain, wenn
  lokal keine installiert ist - ein anderes lokales JDK reicht also, um den
  Build zu starten.
- **Kein Gradle-Installationszwang** - benutze den mitgelieferten Wrapper
  (`./gradlew`, unter Windows `gradlew.bat`).
- Eine IDE mit Gradle-Import (IntelliJ IDEA, Eclipse, VS Code) - optional.

```bash
git clone https://github.com/nicki41/UniversalAdmin.git
cd UniversalAdmin
./gradlew build
```

`build` kompiliert, führt alle Tests aus, baut die shaded jar unter
`build/libs/` und prüft zusätzlich über `verifyShadedJarDrivers`, dass die
gebündelten Datenbanktreiber in der fertigen jar wirklich funktionieren.
Ausführlicher: [docs/development/setup.md](docs/development/setup.md).

## Architektur

Alles Verbindliche steht in
[docs/development/architecture-rules.md](docs/development/architecture-rules.md).
Die Punkte, an denen Pull Requests am häufigsten scheitern:

- **Business-Logik lebt in Services und Actions**, nicht in GUI-Click-Handlern
  oder Command-Executors. Frontends rufen auf, sie entscheiden nicht.
- **Kein SQL außerhalb einer `*Repository`- oder `Migration`-Implementierung.**
  Services kennen nur das `Repository`-Interface, nie `Connection` oder
  `DataSource`.
- **Mutierende Operationen laufen über `ActionExecutor`**, nie über einen
  direkten `Action.execute(...)`-Aufruf - sonst fehlen Permission-Prüfung und
  Audit-Eintrag.
- **Keine blockierenden Datenbankaufrufe auf dem Paper-Main-Thread.** Alles
  IO läuft über `TaskScheduler.supplyAsync`/`runAsync`, alles Bukkit-API über
  `runOnMainThread`. Siehe
  [docs/architecture/threading.md](docs/architecture/threading.md).
- **Keine sichtbaren Texte im Code.** Jeder Nutzertext ist ein `MessageKey`,
  aufgelöst über `MessageService` aus `lang/<locale>.yml` - und wird in
  **beiden** mitgelieferten Sprachen ergänzt.
- **Kein `config.getString(...)`.** Jeder Konfigurationswert ist ein
  typisiertes, validiertes `SettingDefinition` - siehe
  [docs/development/settings.md](docs/development/settings.md).
- **Keine neue Dependency ohne Begründung** im PR-Text. Insbesondere keine
  Pflicht-Abhängigkeit auf Vault, LuckPerms, PlaceholderAPI oder ProtocolLib.

Ein Umbau der Architektur ist ein eigenes Gespräch und bekommt eine eigene
ADR unter [docs/architecture/decisions/](docs/architecture/decisions/) - kein
Nebeneffekt eines Feature-PRs.

## Neues Modul hinzufügen

Schritt-für-Schritt-Anleitung mit dem `players`-Modul als Vorlage:
[docs/development/adding-module.md](docs/development/adding-module.md).

## Testing

- Jede neue Business-Logik (Service, Action, Migration mit nicht-trivialer
  Logik) braucht einen Unit-Test, der **ohne** laufenden Paper-Server läuft.
- Repositories werden gegen ein In-Memory-Fake des Interfaces getestet, nicht
  gemockt, wo ein Fake einfacher ist.
- Migrationen werden gegen eine echte, temporäre SQLite-Datenbank getestet.
- Keine Tests, die echte Netzwerk-Requests machen.
- Keine Getter-Tests.

```bash
./gradlew test
```

Konventionen im Detail:
[docs/development/testing.md](docs/development/testing.md).

## Code-Stil

- Java 25, 4 Leerzeichen Einrückung, UTF-8, keine Tabs.
- Interfaces ohne Präfix/Suffix (`Repository`, `Module`, `Action`), konkrete
  Implementierungen mit sprechendem Präfix (`JdbcPlayerProfileRepository`).
- Domain-Modelle sind `record`s, keine Klassen mit Settern.
- Typisierte IDs statt roher Strings (`ModuleId`, `ActionId`, `GuiPageId`).
- Kommentare erklären das *Warum*, nicht das *Was* - besonders da, wo eine
  offensichtlichere Lösung bewusst verworfen wurde.

Vollständig: [docs/development/conventions.md](docs/development/conventions.md).

## Dokumentation

Ändert dein PR eine Architekturentscheidung, ein Modul-Verhalten, eine
Permission oder eine Konfigurationsoption, gehört die passende Datei unter
`docs/` (bzw. `README.md`, `ROADMAP.md`, `CHANGELOG.md`) **im selben PR**
aktualisiert - nicht "später".

Betrifft eine Änderung die Telemetrie, gehört sie zusätzlich in
[docs/user/telemetry.md](docs/user/telemetry.md). Es wird nichts erhoben, was
dort nicht dokumentiert ist.

## Pull-Request-Ablauf

1. Fork erstellen, Branch von `main` abzweigen.
2. Änderung umsetzen, Tests ergänzen, Doku im selben Commit-Satz mitziehen.
3. `./gradlew build` muss lokal grün sein.
4. PR gegen `main` öffnen und die Checkliste in der PR-Vorlage abarbeiten.
5. CI (Build + Tests) muss grün sein, bevor gemerged wird.

**Commit-Messages** sind kurz und auf den Punkt und erklären das *Warum*, nicht
nur das *Was* - der Diff zeigt das *Was* bereits. Ein Präfix im Stil von
`feat:`/`fix:`/`docs:`/`chore:` ist willkommen, aber nicht erzwungen.

## Lizenz der Beiträge

Mit einem Pull Request stellst du deinen Beitrag unter die
[Apache License 2.0](LICENSE) (§5 des Lizenztexts). Es gibt kein zusätzliches
CLA.
