# Release Readiness

Stand des Projekts im Hinblick auf eine Veröffentlichung. Aktualisiert im
Zuge der Öffentlichmachung des Repositories (Apache-2.0, CI, automatisierte
Releases, anonyme Nutzungsstatistik).

## Überblick

| Punkt | Stand |
|---|---|
| **Current version** | `0.1.0-alpha` (`build.gradle.kts`, einzige Quelle; `./gradlew -q printVersion`) |
| **Build** | grün - `./gradlew clean build` inklusive `verifyShadedJarDrivers` (öffnet eine echte SQLite-Verbindung durch die fertige jar) |
| **Tests** | grün - 245 Tests in 51 Testklassen, 0 Fehler, 0 Fehlschläge, 0 übersprungen |
| **GitHub** | öffentliches Repository, Default-Branch `main`, Issues aktiv, Issue-/PR-Vorlagen vorhanden |
| **License** | Apache-2.0, `LICENSE` mit unverändertem offiziellem Text; Begründung in [docs/release/licensing.md](docs/release/licensing.md) |
| **CI** | `.github/workflows/build.yml` - Build und Tests auf jedem Push/PR gegen `main`, jar als Actions-Artifact, `contents: read` |
| **Automatic releases** | `.github/workflows/release.yml` - Tag `v*` → Tag/Version-Prüfung, Build, Tests, GitHub-Release mit jar und SHA-256, Prerelease-Erkennung, `contents: write`, nur `GITHUB_TOKEN` |
| **Telemetry implementation** | vollständig implementiert und getestet (`dev.universaladmin.telemetry`), dokumentiert in [docs/user/telemetry.md](docs/user/telemetry.md) |
| **Telemetry endpoint** | **keiner** - `telemetry.endpoint` ist leer voreingestellt, es existiert kein offizieller Endpunkt und kein Fallback-Host. Es wird nichts gesendet. |
| **Modrinth** | nicht hochgeladen; Projektseite und Checkliste vorbereitet in [docs/release/modrinth.md](docs/release/modrinth.md) |
| **Extension API** | nicht implementiert - nächster großer Meilenstein ([ROADMAP.md](ROADMAP.md) Phase 4) |
| **Dependabot** | bewusst **nicht** eingerichtet; Abhängigkeiten werden manuell aktualisiert |

## Was in diesem Schritt dazugekommen ist

- **Apache-2.0-Lizenzierung.** `LICENSE` mit dem unveränderten offiziellen
  Lizenztext (kein erfundener Copyright-Inhaber im Anhang-Platzhalter),
  Entscheidung samt Begründung und Auswirkung auf Extension-API,
  Community-Extensions und ein mögliches Marketplace-Backend in
  [docs/release/licensing.md](docs/release/licensing.md).
- **Anonyme Nutzungsstatistik.** Installation-ID (128 Bit aus `SecureRandom`,
  aus nichts abgeleitet), Payload mit genau sechs Feldern, HTTP-Client mit
  kurzen Timeouts, Scheduler mit verzögertem Start und Jitter, vollständiges
  Opt-out ohne Neustart. Kein Feld wird erhoben, das nicht in
  [docs/user/telemetry.md](docs/user/telemetry.md) steht - ein Test hält das
  fest.
- **Automatisierte Releases.** Tag-getrieben, mit einer Prüfung, dass der Tag
  zur Projektversion passt, bevor irgendetwas gebaut oder veröffentlicht wird.
- **Öffentliche Entwicklerdokumentation.** Die verbindlichen Projektregeln
  liegen jetzt als reguläre Doku unter
  [docs/development/architecture-rules.md](docs/development/architecture-rules.md);
  alle Verweise in Quellcode und Doku zeigen dorthin.
- **README/CONTRIBUTING/SECURITY** auf öffentlichen Stand gebracht,
  inklusive Private Vulnerability Reporting und Telemetrie-Offenlegung.

## Telemetrie im Detail

| Frage | Antwort |
|---|---|
| Wird etwas gesendet? | Nein. Kein Endpunkt konfiguriert, kein eingebauter Fallback. |
| Wird eine ID erzeugt? | Nur, wenn Telemetrie aktiv **und** ein Endpunkt konfiguriert ist. Sonst wird nicht einmal eine Datei angelegt. |
| Welche Felder? | `installationId`, `universalAdminVersion`, `minecraftVersion`, `javaMajorVersion`, `onlinePlayers`, `maxPlayers`. Mehr nicht. |
| Spielerbezogene Daten? | Keine. Nur zwei Zahlen. Keine Namen, UUIDs, IPs. |
| Hardware-Fingerprint? | Nein. Reiner Zufall. |
| Opt-out? | `telemetry.enabled: false`, wirksam nach `/admin reload`, ohne Neustart. |
| Auswirkung eines Backend-Ausfalls? | Keine. Kein Retry, keine Queue, eine einzige Warnung pro Serverlauf. |
| Main-Thread? | Nie. Spielerzahlen werden auf dem Main-Thread gelesen, der Request läuft im Hintergrund. |
| Offen | Kein Endpunkt, keine Datenschutzerklärung, keine Retention-Entscheidung, Opt-in-vs-Opt-out vor Livegang erneut zu prüfen. |

## Known limitations

Unverändert gültige Einschränkungen aus dem vorherigen Readiness-Durchlauf,
plus die neuen:

- **Kein Test auf einem laufenden Paper-Server in diesem Durchlauf.** Der
  Build ist grün und `verifyShadedJarDrivers` prüft die fertige jar gegen eine
  echte Datenbank, aber GUI-Navigation (Back/Close/Pagination/Empty-State/
  Confirmations) und die Moderations-Edge-Cases (abgelaufener Ban/Mute,
  Neustart, Disconnect während Freeze, Staff-Mode-/Vanish-Reconnect,
  Inventar-Wiederherstellung) sind weiterhin nur code-geprüft. Frühere
  Erfahrung in diesem Projekt: ein grüner Build war schon einmal **nicht**
  ausreichend - ein Serverstart deckte zwei Shading-Fehler auf, die keine
  Testsuite sehen konnte (deshalb existiert `verifyShadedJarDrivers`).
- **Auch die Telemetrie wurde nicht gegen einen echten Endpunkt getestet** -
  es gibt keinen. Die Unit-Tests decken Aktivierung/Deaktivierung, Payload,
  Fehlerpfad, Jitter und Cleanup ohne Netzwerk ab; ein echter HTTP-Roundtrip
  ist ungeprüft.
- **Confirmation-Dialog-Historie:** `ConfirmationDialog.open(...)` legt keinen
  eigenen Eintrag auf den Navigationsstack, sodass `confirmCtx.back()` zur
  obersten Redraw-Callback-Seite zurückkehrt, nicht zwingend zu der Seite, von
  der der Dialog geöffnet wurde. Repo-weites, bestehendes Muster; die
  Performance-Dialoge umgehen es mit `this.open(viewer)`. Nicht behoben, um
  keine breite, verhaltensändernde Änderung ohne Live-Test vorzunehmen.
- **Zwei Low-Severity-Security-Punkte als Backlog:** kein
  Path-Traversal-Validator auf `database.file` (Admin-Eingabe, dieselbe
  Vertrauensstufe wie der Rest von `config.yml`) und kein Click-Debounce auf
  GUI-Bestätigungsbuttons.
- **Architektur-Nitpick:** `ModerationPlayerLink` (ein
  Cross-Modul-Erweiterungspunkt) liegt im Package des Moderation-Moduls statt
  an neutraler Stelle; der Lookup läuft korrekt über `ServiceRegistry`.
- **Bekannter, nicht damit zusammenhängender Bug:**
  `InGameNotificationService` rendert die Nachricht einer `Notification` als
  Literaltext statt MiniMessage zu parsen, sodass Tags wie `<yellow>` in
  einzelnen Lang-Keys unrendert im Chat erscheinen.
- **Unterstützter Minecraft-/Paper-Versionsbereich ist nicht explizit
  festgelegt** - nur "die in `build.gradle.kts` gepinnte API-Version". Für
  einen Modrinth-Upload muss das beantwortet werden.
- **Keine Screenshots** für README/Modrinth.
- **Settings-GUI/-Commands fehlen** (der Service existiert), ebenso die
  Command-Frontends für Players/Moderation/Worlds/Whitelist/Performance.

## Empfohlene nächste Schritte

1. **Einen echten Paper-Server** gegen die gebaute jar laufen lassen und die
   GUI-/Moderations-Checkliste oben von Hand durchgehen.
2. **Screenshots** aufnehmen (Liste in
   [docs/release/modrinth.md](docs/release/modrinth.md)).
3. **Versionsbereich festlegen** (welche Paper-/Minecraft-Versionen werden
   unterstützt).
4. **Ersten Alpha-Release taggen** nach
   [docs/release/releasing.md](docs/release/releasing.md) - der Workflow
   erledigt Build, Tests, Release, jar und SHA-256.
5. Danach: **öffentliche Extension-API** (ROADMAP.md Phase 4). Der Core hat
   genug Funktionsumfang; weitere eingebaute Features konkurrieren zunehmend
   mit "die bestehende Oberfläche erweiterbar machen".
6. Unabhängig davon: **Telemetrie-Backend** samt Datenschutzerklärung,
   Retention und einer bewussten Opt-in-/Opt-out-Entscheidung, bevor ein
   Endpunkt live geht.
