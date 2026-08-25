# 0009 - Zentrales Audit-System auf Basis von `ActionExecutor`

## Status

Angenommen

## Kontext

[0008](0008-action-authorization-pipeline.md) gab `ActionExecutor` bereits
einen Audit-*Hook* (`AuditService`-Injektion, Aufruf nach erfolgreicher
Ausführung), aber bewusst ohne das eigentliche Audit-System zu bauen - die
damalige `AuditEvent`-Form (`type`, `actor`, `summary`, `targetId`) war ein
Platzhalter, der gerade genug trug, um den Hook zu demonstrieren. Ohne ein
zentrales, vollständiges Audit-System hätte jedes Modul (Moderation für
Kick/Ban, Whitelist für Änderungen, Settings für Reload, ...) seine eigene
Logging-Lösung gebaut - genau das Muster, das UniversalAdmin für
Business-Logik (0002) und Autorisierung (0008) bereits vermieden hat, hier
nur eine Ebene weiter unten.

## Entscheidung

`AuditEvent` wird auf die volle "Audit Entry"-Form erweitert (Actor, Action-
Typ, Modul, Target, Source, Erfolg, Grund, Alt-/Neuwert, Welt/Position,
Summary, Metadata, Correlation-ID - siehe [docs/user/audit-log.md](../user/audit-log.md)),
und `ActionExecutor` baut diesen Eintrag **automatisch** aus
`ActionDefinition`/`ActionContext`/`ActionResult`, ergänzt um optionale,
action-spezifische `AuditDetails`. Ein Feature-Entwickler befüllt damit nie
mehr als die paar Felder, die für seine Action wirklich gelten (typischerweise
nur Alt-/Neuwert) - siehe [../actions.md#audit-hook](../actions.md#audit-hook).

Persistenz bleibt beim bestehenden `AuditEventRepository`/JDBC-Muster
(0003): eine neue, forward-only `AuditSchemaMigrationV2` erweitert
`audit_log` um die neuen Spalten, statt Version 1 nachträglich zu ändern.
Metadata wird als flaches, hart-kodiertes JSON gespeichert (`audit.jdbc.MetadataJson`) -
keine neue Abhängigkeit (siehe docs/development/architecture-rules.md "Dependencies") und explizit keine
Java-Serialisierung. Abfragen (Filter über Actor/Target/Action/Modul/Source/
Erfolg/Zeitraum, Pagination) laufen über `AuditService#query(AuditQuery)`,
implementiert direkt auf dem bestehenden `AuditService`/`AuditEventRepository`-
Paar statt über eine zusätzliche, praktisch nur delegierende
"Query-Service"-Schicht.

Die GUI (`AuditLogListPage`/`AuditLogDetailPage`) ist bewusst **nicht** über
`AbstractListGuiPage` gebaut - diese Basisklasse versiegelt
`renderContent`, und die Listenseite braucht zusätzlich einen permanenten
Filter-Toggle-Button in der Chrome-Reihe, für den es dort keinen
Erweiterungspunkt gibt. Sie lädt die neuesten 200 Einträge (gefiltert) und
paginiert clientseitig darüber - dieselbe "einmal laden, im Speicher
slicen"-Form wie jede andere Listenseite in diesem Framework, keine echte
serverseitige Pagination einer potenziell großen Tabelle.

Retention (`audit.retention-days`, `0` = unbegrenzt) läuft über einen
stündlichen `BukkitTask` (`AuditLogModule`), der nur `AuditService#cleanupExpired()`
anstößt - die eigentliche `DELETE`-Arbeit läuft wie jeder andere
Repository-Aufruf async über `TaskScheduler`, nicht auf jedem Server-Tick.

## Konsequenzen

- Jede Action, die auditiert werden soll, bekommt das automatisch, sobald
  sie über `ActionDefinition` registriert ist - kein Modul schreibt eigenen
  Audit-Code.
- `AuditEvent` ist jetzt ein deutlich größerer Record als zuvor; die neuen
  Spalten in `audit_log` sind alle nullable/mit Default, sodass Zeilen aus
  Version 1 gültig bleiben, ohne Backfill.
- `action` und `audit` bleiben gegenseitig gekoppelt (Actor lebt in
  `action`, `ActionExecutor` kennt `AuditService`/`AuditEventType`) - das
  war bereits mit 0008 akzeptiert und wird hier nicht neu aufgerollt.
- Die GUI-Filterung ist bewusst auf eine Dimension (Erfolg/Fehlschlag)
  beschränkt ("Filter-Grundlage"); die Query-Schicht trägt bereits mehr,
  eine reichhaltigere Filter-UI ist zurückgestellt.

## Alternativen

- **Eigene Audit-Tabelle/-Service pro Modul:** Näher an "jedes Feature
  loggt sich selbst", aber genau das Muster, das diese Entscheidung
  vermeiden soll - keine einheitliche Abfrage-/GUI-/Retention-Schicht über
  alle Module hinweg.
- **Externe JSON-Bibliothek für Metadata:** Hätte den Codec vereinfacht,
  aber gegen "keine neue Dependency ohne klaren Grund" verstoßen, bei einer
  Anforderung (flaches String/Number/Boolean/null-Objekt), die ein kleiner
  handgeschriebener Codec vollständig abdeckt.
- **Server-seitige Pagination in der GUI:** Korrekter für eine sehr große
  `audit_log`-Tabelle, aber ein Bruch mit dem "einmal laden, clientseitig
  paginieren"-Muster, das jede andere Listenseite im GUI-Framework heute
  nutzt - zurückgestellt, bis das Framework selbst dafür einen
  Erweiterungspunkt bekommt.
