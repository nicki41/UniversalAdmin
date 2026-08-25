# Threading

## The Rule

Database access and other blocking IO **never** run on the Paper main
thread. Anything touching the Bukkit API (inventories, entities, world
access) runs **only** on the main thread. The crossover between the two
goes exclusively through
[`TaskScheduler`](../../src/main/java/dev/universaladmin/scheduler/TaskScheduler.java):

```java
public interface TaskScheduler {
    <T> CompletableFuture<T> supplyAsync(Supplier<T> task);
    CompletableFuture<Void> runAsync(Runnable task);
    void runOnMainThread(Runnable task);
    void close();
}
```

Repositories and services never call `Bukkit.getScheduler()` directly -
always through `TaskScheduler`.

## Why Virtual Threads

`PaperTaskScheduler` (the default implementation) uses
`Executors.newVirtualThreadPerTaskExecutor()` (virtual threads, stable
since Java 21; the project runs on Java 25) for the background path.
Blocking JDBC calls are the textbook example virtual threads were built
for: every database call gets its own cheap thread instead of sharing a
small fixed pool - without any repository code having to do anything
async-specific beyond calling a blocking JDBC method inside a lambda.

## The Way Back to the Main Thread

`runOnMainThread` uses Bukkit's own scheduler
(`Bukkit.getScheduler().runTask`). Every GUI update, every inventory
access happening from inside an async callback (e.g. "database result is
in, now set the item in the inventory") has to go back to the main thread
this way.

## The Documented Exception

`UniversalAdminPlugin#onEnable` calls `storage.migrations().runPending()`
synchronously on the main thread - twice, not once, because migrations get
registered from two different places:

1. Once in `bootstrapCore`, right after the two core audit migrations are
   registered - before `ModuleManager.loadAll()`/`enableAll()`.
2. A second time in `onEnable`, right after `moduleManager.enableAll()` -
   because every module only registers its own `Migration`(s) *during* its
   own `onEnable` (see
   [adding-module.md](../development/adding-module.md) step 3), so
   necessarily *after* the first call. Without this second call, every
   module-owned table (`player_profiles`, `punishments`,
   `server_maintenance_state`, `whitelist_entries`, `vanish_state`, ...)
   would never get created - that was a real bug until this second call
   was added. `MigrationRunner.runPending()` is idempotent (see
   `MigrationRunnerTest`), so the second call only applies whatever was
   newly registered since the first, never anything twice.

Both calls run before players can join. That's a deliberate exception:

- Paper offers no asynchronous `onEnable` lifecycle hook.
- Migrations run at startup, not during live operation.
- No module may rely on a database whose schema isn't current yet - so
  every migration has to be finished before a player can join (not
  necessarily before *every single* module's `onEnable`, since a module
  only registers its own migration there in the first place).

This is **not a template** for other code. Any other blocking operation
belongs behind `TaskScheduler`.

## No `Bukkit.reload()`

UniversalAdmin never triggers a global Bukkit reload and doesn't direct
users to use one to reset plugin state. A global reload bypasses the
plugin lifecycle in ways UniversalAdmin (and other plugins on the same
server) can't control - state in `UniversalAdmin`/`ModuleManager` would
become inconsistent with what Bukkit believes is loaded afterward. A
"reload" feature, if wanted, is its own explicitly implemented
`onDisable`/`onEnable` pair for UniversalAdmin itself, not a server-wide
reload.
