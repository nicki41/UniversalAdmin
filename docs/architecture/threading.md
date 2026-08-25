# Threading

## Die Regel

Datenbankzugriffe und andere blockierende IO laufen **niemals** auf dem
Paper-Main-Thread. Alles, was Bukkit-API anfasst (Inventories, Entities,
World-Zugriff), läuft **nur** auf dem Main-Thread. Der Übergang zwischen
beidem läuft ausschließlich über
[`TaskScheduler`](../../src/main/java/dev/universaladmin/scheduler/TaskScheduler.java):

```java
public interface TaskScheduler {
    <T> CompletableFuture<T> supplyAsync(Supplier<T> task);
    CompletableFuture<Void> runAsync(Runnable task);
    void runOnMainThread(Runnable task);
    void close();
}
```

Repositories und Services rufen `Bukkit.getScheduler()` nie direkt auf -
immer über `TaskScheduler`.

## Warum virtuelle Threads

`PaperTaskScheduler` (die Standardimplementierung) nutzt
`Executors.newVirtualThreadPerTaskExecutor()` (virtuelle Threads, seit
Java 21 stabil; das Projekt läuft auf Java 25) für den
Hintergrundpfad. Blockierende JDBC-Aufrufe sind das klassische Beispiel,
für das virtuelle Threads gebaut wurden: jeder Datenbankaufruf bekommt
seinen eigenen billigen Thread, statt sich einen kleinen festen Pool zu
teilen - ohne dass Repository-Code irgendetwas Async-Spezifisches tun
muss außer eine blockierende JDBC-Methode in einer Lambda aufzurufen.

## Der Rückweg zum Main-Thread

`runOnMainThread` nutzt Bukkits eigenen Scheduler
(`Bukkit.getScheduler().runTask`). Jede GUI-Aktualisierung, jeder
Inventory-Zugriff, der aus einem async-Callback heraus passiert (z. B.
"Datenbank-Ergebnis da, jetzt Item im Inventory setzen"), muss über diesen
Weg zurück auf den Main-Thread.

## Die dokumentierte Ausnahme

`UniversalAdminPlugin#onEnable` ruft `storage.migrations().runPending()`
synchron auf dem Main-Thread auf - zweimal, nicht einmal, weil Migrationen
von zwei verschiedenen Stellen registriert werden:

1. Einmal in `bootstrapCore`, direkt nachdem die beiden Core-Audit-
   Migrationen registriert wurden - vor `ModuleManager.loadAll()`/
   `enableAll()`.
2. Ein zweites Mal in `onEnable`, direkt nach `moduleManager.enableAll()` -
   denn jedes Modul registriert seine eigene(n) `Migration`(en) erst
   *während* seines eigenen `onEnable` (siehe
   [adding-module.md](../development/adding-module.md) Schritt 3), also
   zwangsläufig *nach* dem ersten Aufruf. Ohne diesen zweiten Aufruf würde
   jede Modul-eigene Tabelle (`player_profiles`, `punishments`,
   `server_maintenance_state`, `whitelist_entries`, `vanish_state`, ...)
   nie angelegt - genau das war ein realer Bug, bis dieser zweite Aufruf
   ergänzt wurde. `MigrationRunner.runPending()` ist idempotent (siehe
   `MigrationRunnerTest`), der zweite Aufruf wendet also nur an, was seit
   dem ersten neu registriert wurde, nie etwas doppelt.

Beide Aufrufe laufen vor Spieler joinen können. Das ist eine bewusste
Ausnahme:

- Paper bietet keinen asynchronen `onEnable`-Lifecycle-Hook.
- Migrationen laufen beim Start, nicht im laufenden Betrieb.
- Kein Modul darf sich auf eine Datenbank verlassen, deren Schema noch
  nicht auf dem aktuellen Stand ist - jede Migration muss also fertig sein,
  bevor ein Spieler joinen kann (nicht zwingend vor *jedem* einzelnen
  Modul-`onEnable`, da ein Modul seine eigene Migration selbst erst dort
  registriert).

Das ist **kein Vorbild** für sonstigen Code. Jede weitere blockierende
Operation gehört hinter `TaskScheduler`.

## Kein `Bukkit.reload()`

UniversalAdmin löst niemals einen globalen Bukkit-Reload aus und leitet
Nutzer nicht dazu an, einen zu verwenden, um Plugin-Zustand
zurückzusetzen. Ein globaler Reload umgeht den Plugin-Lifecycle auf eine
Weise, die UniversalAdmin (und andere Plugins auf demselben Server) nicht
kontrollieren können - Zustand in `UniversalAdmin`/`ModuleManager` würde
inkonsistent mit dem werden, was Bukkit danach glaubt geladen zu haben.
Ein "Neu laden"-Feature, falls gewünscht, ist ein eigenes, explizit
implementiertes `onDisable`/`onEnable`-Paar für UniversalAdmin selbst, kein
serverweiter Reload.
