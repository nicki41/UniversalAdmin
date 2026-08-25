# Security Policy

## Unterstützte Versionen

UniversalAdmin ist in der Alpha-Phase. Sicherheitsfixes gehen in die jeweils
aktuellste Version; es gibt keine Backports auf ältere Alpha-Releases.

| Version | Unterstützt |
|---|---|
| aktuellstes Release / `main` | ja |
| ältere Alpha-Releases | nein |

## Sicherheitslücken melden

Bitte **nicht** als öffentliches Issue melden.

Bevorzugter Weg: **GitHub Private Vulnerability Reporting** über den Tab
["Security" → "Report a vulnerability"](https://github.com/nicki41/UniversalAdmin/security/advisories/new)
in diesem Repository. Der Report ist dann nur für die Maintainer sichtbar.

Sollte das für dich nicht verfügbar sein, wende dich über einen der auf dem
GitHub-Profil des Repository-Inhabers angegebenen Kontaktwege direkt an die
Maintainer. Es wird hier bewusst keine E-Mail-Adresse erfunden, die es nicht
gibt.

Bitte enthalten:

- Betroffene Version bzw. Commit
- Reproduktionsschritte
- Erwartetes vs. tatsächliches Verhalten
- Mögliche Auswirkung (z. B. Rechteausweitung, Datenverlust, RCE)

**Bitte keine Zugangsdaten, Tokens, Passwörter oder Serverlogs mit
personenbezogenen Daten in den Report kopieren** - eine Beschreibung reicht.

Es gibt kein Bug-Bounty-Programm.

## Sicherheitsrelevante Designentscheidungen

Diese Regeln sind bewusst gesetzt, siehe auch die
[Entwicklungsregeln](docs/development/architecture-rules.md):

- **Keine Secrets im Log.** Datenbank-Passwörter und künftige API-Tokens
  werden nie geloggt, auch nicht auf Debug-Level. `DatabaseConfig#toString()`
  redigiert das Passwort explizit.
- **Keine unsicheren Packet-Hacks im Core.** UniversalAdmin greift nicht in
  das Netzwerkprotokoll ein (kein ProtocolLib, kein rohes Packet-Injection)
  - das reduziert die Angriffsfläche und hält den Core kompatibel mit
    Server-internen Änderungen.
- **Permissions statt harter Op-Checks.** Jede geschützte Aktion hat einen
  eigenen `PermissionNode` (siehe
  [docs/user/permissions.md](docs/user/permissions.md)), sodass
  Serverbetreiber granular vergeben können, statt pauschal Op zu vergeben.
  Geprüft wird zentral im `ActionExecutor`, nicht verstreut im Frontend.
- **SQL-Injection:** Ausschließlich `PreparedStatement` mit gebundenen
  Parametern in Repository-Implementierungen - siehe
  [docs/architecture/storage.md](docs/architecture/storage.md). Kein
  String-Concatenation von Nutzereingaben in SQL.
- **Datenbank-Zugangsdaten** liegen in `config.yml` im Plugin-Ordner
  (Dateisystem-Berechtigungen des Servers sind der Schutz dafür, wie bei
  jedem anderen Paper-Plugin auch). Es gibt aktuell keinen verschlüsselten
  Secret-Store; das ist eine bekannte Grenze, kein Versehen.
- **Verbindungsfehler zur Datenbank loggen nie das Passwort.**
  `StorageService`/`DataSourceFactory` geben `DatabaseConfig.password()`
  nirgends an einen Logger weiter - auch nicht in der Exception, die beim
  Start einen fehlgeschlagenen Verbindungsaufbau meldet. Siehe
  [docs/architecture/storage.md#health](docs/architecture/storage.md#health).
- **Audit-Trail.** Jede mutierende Action erzeugt automatisch einen
  `AuditEvent` über `ActionExecutor` - kein Modul kann das umgehen, ohne die
  Architekturregeln zu brechen. Siehe
  [docs/user/audit-log.md](docs/user/audit-log.md).

## Telemetrie und Datenschutz

UniversalAdmin enthält eine anonyme Nutzungsstatistik. Vollständig
dokumentiert - jedes einzelne Feld, das Intervall, das Opt-out und alles, was
ausdrücklich nicht erhoben wird - in
[docs/user/telemetry.md](docs/user/telemetry.md).

Sicherheitsrelevante Eckpunkte:

- **Standardmäßig wird nichts gesendet.** Es ist kein Endpunkt hinterlegt und
  es gibt keinen eingebauten Fallback-Host. Ohne konfigurierten
  `telemetry.endpoint` wird kein Request ausgeführt, keine Installation-ID
  erzeugt und kein Timer gestartet.
- **Kein Identifier, der auf den Host zurückführt.** Die Installation-ID sind
  128 zufällige Bits aus `SecureRandom` - nicht abgeleitet aus IP,
  MAC-Adresse, Hardware, Hostname, Serveradresse oder Dateipfad.
- **Keine personenbezogenen Spielerdaten.** Übertragen werden ausschließlich
  Anzahlen; nie Namen, UUIDs, IP-Adressen, Chat oder Commands.
- **Der Kanal ist einseitig.** Antworten des Endpunkts werden verworfen, nie
  geparst und nie ausgeführt; Redirects werden nicht verfolgt. Ein
  kompromittierter oder falsch konfigurierter Endpunkt kann dem Server
  dadurch nichts anweisen.
- **Vollständiges Opt-out** über `telemetry.enabled: false`, wirksam ohne
  Neustart nach `/admin reload`.
- **Noch keine Datenschutzerklärung.** Es wird hier ausdrücklich keine
  Aussage über DSGVO- oder sonstige Compliance getroffen. Vor dem Betrieb
  eines echten Endpunkts muss das separat geprüft werden.

## Abhängigkeiten

Der Core hat bewusst eine kleine Abhängigkeitsliste (siehe
`build.gradle.kts`), um die Angriffsfläche durch Third-Party-Code klein zu
halten: zwei JDBC-Treiber und ein Connection-Pool, sonst nichts zur Laufzeit.
Aktualisierungen werden bewusst manuell geprüft und eingespielt; es gibt
absichtlich keine automatischen Dependency-Update-Pull-Requests.
