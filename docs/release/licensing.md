# Lizenzierung

**Kein Rechtsrat.** Dieses Dokument hält fest, welche Lizenz für welchen Teil
von UniversalAdmin gilt bzw. vorgesehen ist, und warum. Es ist keine
juristische Beratung. Für verbindliche Aussagen - insbesondere zu
Kompatibilität mit der Paper-API, zu Marken-/Namensfragen und zu allem, was
später kommerziell vertrieben werden soll - gehört ein Anwalt hinzugezogen,
bevor darauf aufgebaut wird.

## Entschieden: Core unter Apache-2.0

Der Core (dieses Repository) steht unter der **Apache License 2.0**. Der
vollständige, unveränderte Lizenztext liegt als [LICENSE](../../LICENSE) im
Repository-Root.

Gründe für Apache-2.0 statt einer der Alternativen:

- **Permissiv, wie MIT** - maximale Adoption, niedrige Einstiegshürde für
  Serverbetreiber und Contributor. Kommerzielle Nutzung ist ausdrücklich
  erlaubt.
- **Expliziter Patent-Grant**, den MIT nicht hat. Sobald Dritte Extensions
  und Integrationen beisteuern, ist das der praktisch relevante Unterschied.
- **Keine Copyleft-Frage im Extension-Ökosystem.** Bei GPL/AGPL müssten
  Extension-Autoren erst klären, ob ihre Extension eine "abgeleitete Arbeit"
  des Cores ist - eine Frage, die bei Plugin-Architekturen regelmäßig
  strittig ist und kommerzielle Extension-Autoren abschreckt.
- **Im Java-/Server-Ökosystem der Normalfall**, entsprechend vertraut.

Bewusst in Kauf genommen: Eine permissive Lizenz erlaubt es Dritten, den Core
zu forken und proprietär weiterzuverwenden. Das ist der Preis dafür, dass ein
Open-Core-Modell mit später möglichen proprietären Extensions überhaupt sauber
funktionieren kann.

## Was das für die einzelnen Teile bedeutet

| Teil | Lizenz | Status |
|---|---|---|
| **Core** (dieses Repository) | Apache-2.0 | gilt jetzt |
| **Öffentliche Extension-API** (`universaladmin-api`, [ROADMAP.md](../../ROADMAP.md) Phase 4) | Apache-2.0 geplant | noch nicht implementiert |
| **SDK / Beispiel-Extensions** | Apache-2.0 geplant | noch nicht implementiert |
| **Community-Extensions** | frei wählbar | Sache der jeweiligen Autoren |
| **Künftige offizielle Premium-Extensions** | können separat proprietär lizenziert werden | keine existieren |
| **Marketplace-/Web-Backend** | kann separat lizenziert werden | keine Implementierung, siehe [web-future.md](../architecture/web-future.md) |

Im Detail:

- **Community-Extensions** dürfen jede Lizenz verwenden, die mit Apache-2.0
  vereinbar ist - inklusive proprietärer Lizenzen. Apache-2.0 verlangt vom
  Core-Nutzer keine bestimmte Lizenz für eigenen, darauf aufbauenden Code.
- **Offizielle Premium-Extensions** sind heute nicht geplant und existieren
  nicht. Die Lizenzwahl hält die Tür offen: eine separat entwickelte,
  separat vertriebene Extension kann proprietär lizenziert werden, ohne dass
  daraus eine Pflicht folgt, den Core zu schließen. Der Core bleibt
  Apache-2.0.
- **Ein Marketplace- oder Web-Backend** wäre ein eigenes Projekt mit eigener
  Lizenz. Nichts an der Core-Lizenz zwingt dazu, ein gehostetes Backend
  offenzulegen.
- **Beiträge** zu diesem Repository werden unter Apache-2.0 eingebracht (§5
  des Lizenztexts: Beiträge stehen mangels anderslautender Vereinbarung unter
  denselben Bedingungen). Es gibt aktuell **kein** zusätzliches CLA.

## Offene Punkte

- **Copyright-Zeile.** `LICENSE` enthält den unveränderten offiziellen
  Apache-2.0-Text inklusive des `Copyright [yyyy] [name of copyright owner]`-
  Platzhalters im Anhang. Wer als Rechteinhaber eingetragen wird (Einzelperson,
  später eventuell eine Organisation), ist bewusst noch nicht ausgefüllt - das
  ist eine Entscheidung des Projektinhabers, keine technische.
- **NOTICE-Datei.** Apache-2.0 verlangt keine, und es gibt derzeit keinen
  Inhalt dafür. Sollten später fremde Apache-2.0-Quellen in den Core kopiert
  werden, ist an dem Punkt eine `NOTICE` zu prüfen.
- **Paper-API-Kompatibilität.** `compileOnly("io.papermc.paper:paper-api:...")`
  wird nicht mitausgeliefert, sondern vom Server zur Laufzeit gestellt - der
  übliche Bukkit-Plugin-Mechanismus. Mit einer permissiven Lizenz ist das
  unkritischer als mit Copyleft, aber ebenfalls nichts, das hier verbindlich
  beurteilt wird.
- **Gebündelte Abhängigkeiten.** Die Shaded jar enthält `sqlite-jdbc`,
  `mariadb-java-client` und `HikariCP` (siehe `build.gradle.kts`). Deren
  eigene Lizenzbedingungen gelten für die jeweils enthaltenen Klassen
  unabhängig von der Lizenz des Cores; vor dem ersten Distributionskanal
  (Modrinth) prüfen, ob deren Lizenzhinweise mit ausgeliefert werden müssen.
