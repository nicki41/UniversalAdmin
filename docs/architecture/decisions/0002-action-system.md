# 0002 - Ein Action-System als einziger Ort für Business-Logik

## Status

Angenommen

## Kontext

Dieselbe Operation ("Spieler kicken", "Whitelist setzen") soll langfristig
von mehreren Frontends auslösbar sein: Ingame-GUI, Commands, später eine
REST-API. Ohne eine explizite Abstraktion dafür landet Logik erfahrungsgemäß
direkt im GUI-Click-Handler oder im Command-Executor, wird dort bei Bedarf
kopiert, und divergiert über Zeit (der GUI-Kick prüft eine andere
Bedingung als der Command-Kick).

## Entscheidung

Ein `Action<I, R>`-Interface mit `ActionId`-Identifikation, async
(`CompletableFuture<ActionResult<R>>`), Fehler als sealed `ActionResult`
(`Success`/`Failure` mit `FailureReason`) statt Exceptions. Wer eine Action
ausführt, wird über `Actor`/`ActorType` beschrieben (`PLAYER`, `CONSOLE`,
`WEB`, `SYSTEM`) statt über einen Bukkit-`CommandSender`, damit `action`
nicht von Paper-Typen abhängt.

Frontends (GUI, Commands, später Web) rufen Actions über `ActionRegistry`
per `ActionId` auf, nicht die konkrete Action-Klasse direkt - das hält die
Kopplung lose genug, dass eine Extension später eine eigene Action
registrieren kann, die ein Frontend genauso aufruft wie eine eingebaute.

Details und Code-Beispiel: [../actions.md](../actions.md).

## Konsequenzen

- Jede Operation, die von mehr als einem Frontend ausgelöst werden soll
  oder auditiert/berechtigt werden muss, bekommt eine `Action`. Reine
  interne Lesezugriffe innerhalb eines Moduls müssen keine sein.
- `ActionResult` zwingt jeden Aufrufer, den Fehlerfall zu behandeln - es
  gibt keinen impliziten "wird schon nicht fehlschlagen"-Pfad.
- Mehr Typen (Input-Record, `ActionId`-Konstante, Action-Klasse) pro
  Operation als ein einzeiliger Methodenaufruf. Akzeptiert als Preis für
  Wiederverwendbarkeit über Frontends hinweg.

## Alternativen

- **Command-Pattern ohne Registry**, Actions direkt injiziert: Würde die
  Registry-Indirektion sparen, aber jedes Frontend müsste zur Compile-Zeit
  jede Action kennen, die es aufrufen will - inkompatibel mit dem Ziel,
  dass Extensions später eigene Actions beisteuern, die ein generisches
  Frontend (z. B. eine dynamische Web-UI) aufrufen kann, ohne sie zu
  kennen.
- **Business-Logik direkt in Services, keine separate Action-Schicht:**
  Für reine Ingame-only-Logik ausreichend, verliert aber den einheitlichen
  Einstiegspunkt für Auditierung/Berechtigung/Web-Aufruf, den `Action`
  bietet.
