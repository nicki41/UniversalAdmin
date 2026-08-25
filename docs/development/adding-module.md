# Ein neues Modul hinzufügen (bzw. ein Skelett ausbauen)

Sieben der acht eingebauten Module (alle außer `players`) sind aktuell
Skelette: sie implementieren `Module`, registrieren eine Beispiel-
Permission und sonst nichts. Diese Anleitung nutzt
[`dev.universaladmin.modules.players`](../../src/main/java/dev/universaladmin/modules/players)
als vollständiges Vorbild - dieselben Schritte gelten für ein komplett
neues Modul.

## 1. Domain-Modell

Ein unveränderliches `record` pro Entität.

```java
public record PlayerProfile(UUID id, String lastKnownName, Instant firstJoin, Instant lastSeen) {
    public PlayerProfile withLastSeen(String name, Instant seenAt) { ... }
}
```

## 2. Repository-Interface

Erweitert `dev.universaladmin.storage.Repository<T, ID>`, ggf. mit
modulspezifischen Zusatzmethoden.

```java
public interface PlayerProfileRepository extends Repository<PlayerProfile, UUID> {}
```

## 3. Migration

Erstellt die Tabelle. Version ab 1000 aufwärts (siehe
[storage.md](../architecture/storage.md) für die Versionsbereiche) -
prüfe die höchste bereits vergebene Version im Zielmodul, bevor du eine
neue Zahl wählst. Dialektunterschiede (SQLite vs. MySQL/MariaDB) über
`connection.getMetaData().getDatabaseProductName()` behandeln, siehe
`PlayerProfileMigration`/`AuditSchemaMigration` als Beispiel.

## 4. JDBC-Repository-Implementierung

In einem `jdbc`-Subpackage, async über den vom Modul erhaltenen
`TaskScheduler`, `PreparedStatement` mit gebundenen Parametern (nie
String-Concatenation). Eigene unchecked Exception für SQL-Fehler
(`PlayerStorageException`-Muster).

## 5. Application Service

Die eigentliche Business-Logik. Kennt nur das Repository-*Interface*.

```java
public final class PlayerService {
    private final PlayerProfileRepository repository;
    public PlayerService(PlayerProfileRepository repository) { this.repository = repository; }
    public CompletableFuture<PlayerProfile> getOrCreateProfile(UUID playerId, String currentName) { ... }
}
```

## 6. (Optional) Action

Nur wenn die Operation von mehreren Frontends aufrufbar oder
auditiert/berechtigt werden soll (siehe [actions.md](../architecture/actions.md)).

```java
public final class GetPlayerProfileAction implements Action<UUID, PlayerProfile> {
    public static final ActionId ID = ActionId.core("players.get-profile");
    // ...
}
```

## 7. Module-Klasse

Ein `ModuleDescriptor` (statische Metadaten - siehe
[modules.md](../architecture/modules.md#moduledescriptor---static-metadata)),
eine `onLoad`, die **nur** die Migration(en) registriert, und ein `onEnable`,
das den Rest verdrahtet: Repository/Service bauen, Service ggf.
modulübergreifend über `ServiceRegistry` verfügbar machen, Actions/
Permissions registrieren.

**Migration registrieren gehört in `onLoad`, nicht `onEnable`.**
`ModuleManager.loadAll()` ruft `onLoad` für **jedes** Modul auf, bevor
`enableAll()` für irgendein Modul `onEnable` aufruft - und
`UniversalAdminPlugin#onEnable` ruft `storage.migrations().runPending()`
genau dazwischen auf. Registriert ein Modul seine Migration stattdessen in
`onEnable`, ist die Tabelle zu dem Zeitpunkt, an dem dasselbe `onEnable`
schon sein Repository/seinen Service baut, noch nicht angelegt - bei einem
Service, der beim Bauen sofort (auch nur asynchron/fire-and-forget) einen
Datenbank-Read auslöst, ist das ein garantierter `no such table`-Fehler,
kein theoretisches Risiko (siehe
[docs/architecture/threading.md](../architecture/threading.md)).

```java
public final class PlayersModule implements Module {
    public static final ModuleId ID = ModuleId.core("players");

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Players")
            .description("Tracks a profile per player (name history, first/last seen).")
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onLoad(ModuleContext context) {
        context.platform().storage().migrations().register(new PlayerProfileMigration());
    }

    @Override
    public void onEnable(ModuleContext context) {
        var repository = new JdbcPlayerProfileRepository(
                context.platform().storage().dataSource(), context.platform().scheduler());
        var service = new PlayerService(repository);
        context.platform().services().register(PlayerService.class, service);

        context.platform().permissions().register(new PermissionDefinition(
                PermissionNode.core("players.view"), "View player profiles", PermissionDefault.OP));
        context.platform().actions().register(ActionDefinition.builder(new GetPlayerProfileAction(service))
                .permission(PermissionNode.core("players.view"))
                .notAudited()
                .build());
    }
}
```

Nur falls das Modul einen anderen Built-in-Module *zwingend* braucht (nicht
"nutzt ihn, falls vorhanden"): `.dependsOn(OtherModule.ID)` auf dem
Builder. Ohne echten Grund keine Abhängigkeit deklarieren - siehe
[modules.md](../architecture/modules.md#dependencies-and-ordering).

Registriert das Modul in `onEnable` einen Bukkit-Listener, einen
Scheduler-Task, oder einen Registry-Eintrag, der beim Disable wieder
verschwinden soll, geht das über `context.resources()` statt über
manuelles Bookkeeping in `onDisable` - siehe
[modules.md](../architecture/modules.md#resource-cleanup):

```java
context.resources().listener(new MyJoinListener(service));
```

## 8. In `UniversalAdminPlugin#registerBuiltInModules()` registrieren

Das ist die einzige Stelle im Bootstrap, die built-in Module namentlich
kennt. `ModuleManager` bringt sie danach über `loadAll()`/`enableAll()`
selbst in Abhängigkeitsreihenfolge - die Registrierungsreihenfolge hier
muss also nur dann etwas garantieren, wenn eine echte
`ModuleDescriptor.dependsOn(...)`-Abhängigkeit fehlt, auf die man sich
sonst verlassen müsste.

## 9. Tests

Mindestens ein Test für den Service gegen ein In-Memory-Fake des
Repositorys (siehe [testing.md](testing.md) und `PlayerServiceTest`).
Migration gegen eine echte temporäre SQLite-Datenbank testen, falls sie
nicht-triviale SQL enthält. Lifecycle-/Registrierungsverhalten des
Moduls selbst (falscher State-Übergang, Fehlerisolierung, Cleanup) ist
bereits durch [`ModuleManagerTest`](../../src/test/java/dev/universaladmin/module/ModuleManagerTest.java)
generisch abgedeckt - dafür braucht ein neues Modul keinen eigenen Test.

## 10. GUI/Command-Frontend

`players` ist inzwischen selbst das vollständige Vorbild dafür: siehe
[`dev.universaladmin.modules.players.gui`](../../src/main/java/dev/universaladmin/modules/players/gui)
(`PlayerBrowserHomePage` als Einstiegsseite, weitere Seiten ephemer wie
`AuditLogDetailPage`) und [gui-framework.md](gui-framework.md). Kurzfassung:
eine Seite erweitert `AbstractGuiPage`/`AbstractListGuiPage` statt `GuiPage`
von Hand zu implementieren, bekommt Services über den Konstruktor (plus
`GuiFramework`), und registriert sich in `onEnable` unter derselben
`GuiPageId`, die heute noch eine `PlaceholderGuiPage` belegt
(`core:<modul>.home`, siehe `UniversalAdminPlugin#registerMainMenu`) - diese
Platzhalter-Registrierung muss dann für das jeweilige Modul entfernt werden
(siehe die Entfernung der `players.home`-Zeile dort als Beispiel). Die Regel
bleibt: der Klick-Handler/Command-Executor ruft nur den Service/die Action
auf.

## 11. Doku aktualisieren

`docs/architecture/modules.md` (Statuszeile des Moduls),
`ROADMAP.md` (abgehakter Punkt), ggf. `docs/user/permissions.md` (neue
Permission-Node).
