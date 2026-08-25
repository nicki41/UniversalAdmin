# Anonyme Nutzungsstatistik (Telemetry)

UniversalAdmin kann in regelmäßigen Abständen eine sehr kleine, anonyme
Nachricht ("Heartbeat") senden. Dieses Dokument beschreibt **vollständig**,
was dabei übertragen wird, was ausdrücklich nicht übertragen wird, wie oft das
passiert, und wie man es abschaltet.

Die Regel dahinter: **Es wird nichts erhoben, was hier nicht dokumentiert
ist.** Ein neues Feld im Heartbeat und eine Änderung an diesem Dokument sind
derselbe Change - der Test `TelemetryPayloadTest#sendsNoFieldBeyondTheSixDocumentedOnes`
schlägt fehl, wenn jemand ein Feld hinzufügt.

## Aktueller Status: es wird nichts gesendet

**Stand heute sendet UniversalAdmin nichts.** Es gibt noch keinen offiziellen
Statistik-Endpunkt, und es ist keiner in der Software hinterlegt -
`telemetry.endpoint` ist leer voreingestellt, und es gibt keinen eingebauten
Fallback-Host. Solange kein Endpunkt konfiguriert ist:

- wird kein Request irgendwo hin gemacht,
- wird keine Installation-ID erzeugt,
- wird keine Datei dafür angelegt,
- läuft kein Hintergrund-Timer.

Beim Start steht das auch im Log:

```
Anonymous usage statistics are enabled but no endpoint is configured
(telemetry.endpoint is empty), so nothing is sent.
```

Der Rest dieses Dokuments beschreibt, was passiert, sobald es einen offiziellen
Endpunkt gibt (oder jemand einen eigenen einträgt).

## Zweck

Drei Fragen sollen beantwortbar werden - und nur die:

1. **Wie viele Installationen sind aktiv?** Also: lohnt sich die
   Weiterentwicklung, und wie schnell verbreitet sich ein Release.
2. **Wie viele Spieler sind insgesamt auf diesen Servern online?** Eine
   aggregierte Zahl über alle Installationen.
3. **Wie verteilen sich Versionen?** UniversalAdmin-, Minecraft- und
   Java-Version - die Grundlage dafür, zu entscheiden, was noch unterstützt
   werden muss.

Nicht Zweck: einzelne Server identifizieren, vergleichen, öffentlich
auflisten oder ranken. Es gibt keine Server-Liste und keine Möglichkeit, aus
den Daten eine zu bauen (siehe unten - es wird keine Adresse und kein Name
übertragen).

## Was genau übertragen wird

Ein Heartbeat ist ein HTTP-POST mit genau diesem JSON-Body - sechs Felder,
mehr nicht:

```json
{
  "installationId": "0123456789abcdef0123456789abcdef",
  "universalAdminVersion": "0.1.0-alpha",
  "minecraftVersion": "1.21.4",
  "javaMajorVersion": 25,
  "onlinePlayers": 17,
  "maxPlayers": 100
}
```

| Feld | Bedeutung | Wofür |
|---|---|---|
| `installationId` | 128 Bit Zufall, siehe unten | Zwei Heartbeats derselben Installation zusammenführen, damit "aktive Installationen" nicht einfach "empfangene Requests" bedeutet |
| `universalAdminVersion` | Plugin-Version | Versionsverteilung |
| `minecraftVersion` | z. B. `1.21.4` | Welche Minecraft-Versionen weiter unterstützt werden müssen |
| `javaMajorVersion` | z. B. `25` | Ob eine Anhebung der Java-Anforderung Installationen abhängen würde |
| `onlinePlayers` | Anzahl - nur die Zahl | Aggregierte "Players Online"-Summe |
| `maxPlayers` | Slot-Anzahl des Servers | Einordnung der Größenordnung |

Technisch außerdem unvermeidbar, wie bei jedem HTTP-Request: die IP-Adresse
des sendenden Servers ist dem empfangenden Endpunkt auf Transportebene
bekannt. Sie ist **nicht Teil des Payloads**, wird nicht als Identifier
verwendet, und wie ein künftiges Backend damit umgeht (nicht loggen bzw.
sofort verwerfen) gehört in dessen eigene Datenschutzerklärung - siehe
"Offene Punkte" unten.

### Zwei Felder, die bewusst *nicht* gesendet werden

Beide standen zur Diskussion und wurden nach dem Minimalprinzip gestrichen:

- **Paper-Build-String** (`git-Paper-123 (MC: 1.21.4)`): beantwortet keine der
  drei Fragen oben, die `minecraftVersion` nicht schon beantwortet, wäre aber
  ein feineres Unterscheidungsmerkmal.
- **Client-Zeitstempel**: der Empfangszeitpunkt beim Backend ist ohnehin die
  maßgebliche Zeit (siehe "Was 'aktiver Server' bedeutet"), und einer Uhr auf
  einem fremden Server kann man nicht trauen. Ein Feld, das nichts
  Verlässliches hinzufügt, wird nicht gesendet.

### Was ausdrücklich nie übertragen wird

- Server-IP, Hostname, Domain, Port, MOTD, Server-Name
- Spielernamen, Spieler-UUIDs, Spieler-IP-Adressen
- Chat-Nachrichten, ausgeführte Commands
- Weltnamen, Koordinaten, Weltgrößen
- andere installierte Plugins
- Dateiinhalte, Datenbankinhalte, Audit-Log-Einträge, Konfigurationswerte
- Hardware-Merkmale, MAC-Adressen, Seriennummern, Maschinen-Fingerprints
- Betriebssystem-Benutzername, absolute Dateipfade

Die Anzahl der Spieler ist ausschließlich eine Zahl. Es gibt keinen Weg,
daraus eine Spieleridentität zu rekonstruieren.

## Die Installation-ID

- Wird **einmal** erzeugt - beim ersten Start, an dem Telemetrie tatsächlich
  senden könnte (aktiviert **und** Endpunkt konfiguriert).
- 128 Bit aus `SecureRandom`, dargestellt als 32 Hex-Zeichen. **Nicht**
  abgeleitet aus IP, MAC-Adresse, Hardware, Hostname, Serveradresse,
  Dateipfad oder Spielerdaten - aus gar nichts. Reiner Zufall.
- Bewusst kein UUID-String-Format, damit sie in keinem Log und keiner
  Datenbank mit einer Spieler-UUID verwechselt werden kann.
- Liegt in `plugins/UniversalAdmin/installation-id.yml` und bleibt über
  Neustarts hinweg gleich.
- Wird die Datei gelöscht, entsteht beim nächsten Start eine neue ID. Die
  alte Installation ist dann nicht mehr zuordenbar; ein Backend würde sie nach
  Ablauf des Aktivitätsfensters (siehe unten) einfach nicht mehr zählen.
- Wer die ID nicht auf einen neuen Server mitnehmen will, löscht die Datei
  beim Kopieren des Plugin-Ordners. Sie liegt genau deshalb nicht in
  `config.yml`.

## Intervall

- Der **erste** Heartbeat kommt frühestens rund 5 Minuten nach einem
  erfolgreichen Start (plus Zufallsanteil) - nie während des Startvorgangs.
- Danach: alle `telemetry.interval` (Standard 30 Minuten, Minimum 5 Minuten,
  Maximum 24 Stunden), **plus ein Zufallsanteil von bis zu der Hälfte dieses
  Werts**. Standardkonfiguration heißt also ungefähr alle 30-45 Minuten.
- Der Zufallsanteil (Jitter) wird für jedes Intervall neu gezogen, damit viele
  Server nicht synchron senden - etwa nach einer gemeinsamen Downtime.

## Verhalten bei Fehlern

Telemetrie ist das Unwichtigste, was dieses Plugin tut, und verhält sich auch
so:

- Läuft nie auf dem Paper-Main-Thread. Die Spielerzahlen werden auf dem
  Main-Thread gelesen (das ist Main-Thread-Zustand), der Request selbst läuft
  auf einem Hintergrund-Thread - siehe
  [docs/architecture/threading.md](../architecture/threading.md).
- Kurze Timeouts (5 s Verbindungsaufbau, 10 s gesamt).
- Kein Retry, keine Warteschlange, kein Zwischenspeichern. Ein verlorener
  Heartbeat ist verloren.
- Ein Ausfall des Endpunkts hat **keine** Auswirkung auf den Server oder auf
  irgendeine Plugin-Funktion.
- Kein Log-Spam: Der erste Fehlschlag pro Serverlauf ist eine einzelne
  Warnung, alles danach steht nur noch auf `FINE`.
- Antworten des Endpunkts werden verworfen, nicht geparst. Über diesen Kanal
  kann ein Backend dem Server nichts mitteilen und nichts anweisen.
- Weiterleitungen (HTTP-Redirects) werden nicht verfolgt: ein umgezogener
  Endpunkt gehört neu konfiguriert, nicht automatisch zu einem anderen Host
  verfolgt.

## Abschalten (Opt-out)

In `plugins/UniversalAdmin/config.yml`:

```yaml
telemetry:
  enabled: false
```

Danach `/admin reload` (oder Serverneustart). Bei `enabled: false` gilt:

- kein Request irgendwelcher Art, auch kein "notwendiger" oder "essenzieller",
- kein Payload wird überhaupt gebaut,
- keine Installation-ID wird erzeugt oder gelesen,
- kein Timer läuft.

`telemetry.enabled` wird bei **jedem** Heartbeat neu aus der Konfiguration
gelesen. Ein `/admin reload` mit `enabled: false` stoppt die Statistik also
sofort, ohne Neustart.

Die vollständige Einstellungsübersicht steht in
[configuration.md](configuration.md).

## Was "aktiver Server" bedeutet

Für ein künftiges Backend ist die Semantik hier festgehalten, damit später
keine irreführenden Zahlen entstehen:

- **Aktive Installation** = eine eindeutige `installationId`, von der
  innerhalb der **letzten 24 Stunden** (gerechnet ab Empfangszeitpunkt
  serverseitig) mindestens ein gültiger Heartbeat empfangen wurde.
- **Nicht** die Gesamtzahl jemals gesehener Installation-IDs. Eine
  Lifetime-Zahl als "aktive Server" darzustellen wäre schlicht falsch.
- **Players Online** = Summe von `onlinePlayers` über den jeweils **neuesten**
  gültigen Heartbeat jeder aktiven Installation. Nicht die Summe aller
  Heartbeats eines Zeitraums (das würde denselben Spieler dutzendfach zählen).

Damit lassen sich später Angaben wie diese darstellen:

```
UniversalAdmin Network
Active Servers: 1,284
Players Online: 18,492
```

Einzelne Server werden nicht öffentlich aufgelistet - es gibt dafür auch keine
Daten.

## Implementierung

| Klasse | Aufgabe |
|---|---|
| `InstallationIdentity` / `InstallationIdentityStore` | Erzeugen und Persistieren der ID |
| `TelemetryPayload` | Der Heartbeat, exakt wie er über die Leitung geht |
| `TelemetryEnvironment` / `PlayerCounts` | Die Eingangsdaten des Payloads |
| `TelemetryClient` | Schnittstelle; `HttpTelemetryClient` (JDK-HTTP-Client) und `NoOpTelemetryClient` (Standard) |
| `TelemetryService` | Baut und sendet einen Heartbeat; setzt die Garantien oben durch |
| `TelemetryScheduler` | Intervall, Jitter, Lifecycle |
| `TelemetryBootstrap` | Verdrahtung beim Start, drei Ausgänge (aus / kein Endpunkt / aktiv) |

Alles unter `dev.universaladmin.telemetry`, keine neue Abhängigkeit (der
HTTP-Client und der JSON-Encoder kommen aus dem JDK bzw. sind sechs Zeilen).
Die Tests unter `src/test/java/dev/universaladmin/telemetry` machen keinen
einzigen echten Netzwerk-Request.

## Offene Punkte

Ehrlich benannt, statt Compliance zu behaupten:

- **Es gibt noch keine Datenschutzerklärung.** Bevor ein echter Endpunkt in
  Betrieb geht, muss eine separate Prüfung stattfinden (auch zur Frage, wie
  mit der Transport-IP umgegangen wird). Dieses Dokument beschreibt die
  technische Umsetzung; es ist keine rechtliche Zusicherung und keine Aussage
  über DSGVO-Konformität.
- **Aufbewahrungsdauer (Retention)** ist eine Backend-Entscheidung und noch
  offen. Anzustreben ist: Rohdaten kurz, danach nur noch Aggregate.
- **Opt-in statt Opt-out** ist bewusst nicht gewählt (Standard ist
  `enabled: true`), aber solange kein Endpunkt existiert, ist die praktische
  Wirkung identisch: es wird nichts gesendet. Vor dem Livegang eines Endpunkts
  gehört diese Entscheidung noch einmal bewusst getroffen und im Release
  angekündigt.
- **Modrinth** verlangt je nach geltenden Regeln eine Offenlegung von
  Telemetrie in der Projektbeschreibung - siehe
  [../release/modrinth.md](../release/modrinth.md).
