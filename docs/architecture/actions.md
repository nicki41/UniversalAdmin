# Actions

## Zweck

Ein `Action<I, R>` ([`src/main/java/dev/universaladmin/action/Action.java`](../../src/main/java/dev/universaladmin/action/Action.java))
ist eine einzelne Business-Operation - "Spieler kicken", "Whitelist-Eintrag
setzen", "Backup erstellen" - die von jedem Frontend gleich aufgerufen
werden kann: einem GUI-Button, einem Command, später einem REST-Endpunkt.

```java
public interface Action<I, R> {
    ActionId id();
    CompletableFuture<ActionResult<R>> execute(ActionContext context, I input);
}
```

- `I` ist die Eingabe, typischerweise ein kleiner Record.
- `R` ist das Erfolgsergebnis.
- `ActionContext` trägt `Actor` (wer) und `Source` (wie/wovon aus - siehe
  unten). Wird später mehr gebraucht (Locale, Request-ID für Web-Tracing),
  kommt das hierher, nicht in die `execute`-Signatur.

`Action` selbst enthält **keine** Autorisierung, Validierung oder Audit-Logik -
das ist bewusst so. Diese Belange leben zentral in `ActionExecutor` (siehe
unten), nicht dupliziert in jeder Action-Implementierung.

## Die Pipeline: `ActionExecutor`

**Kein Frontend ruft `Action.execute(...)` direkt auf.** GUI, Commands, die
künftige Web-API und Extensions rufen ausschließlich `ActionExecutor.execute(...)`
auf - das ist die eine Stelle, an der Autorisierung, Validierung, das
Action selbst und Auditierung zusammenlaufen:

```
Frontend (GUI / Command / Web / Extension)
                ↓
        ActionExecutor.execute(ActionRequest)
                ↓
   Permission-Check → Feature-enabled-Check → Self-Target-Check → Validation
                ↓
           Action.execute(...)
                ↓
        AuditService.record(...)  (bei Erfolg, falls audited)
                ↓
           ActionResult
```

Vor und nach jedem Schritt feuert der Executor ein [`ActionEvent`](../../src/main/java/dev/universaladmin/action/ActionEvent.java)
(`Executing` → `Executed`/`Failed`) - siehe [Events](#events).

```java
ActionRequest<Void> request = ActionRequest.of(SomeAction.ID, actor, Source.COMMAND, null);
platform.actionExecutor().<Void, SomeResult>execute(request)
        .thenAccept(result -> /* render result */);
```

`UniversalAdminCommand#handleReload` ist das Referenzbeispiel: es baut einen
`Actor` aus dem Bukkit-`CommandSender`, einen `ActionRequest` mit
`Source.COMMAND`, und rendert danach ausschließlich das zurückkommende
`ActionResult` - keine eigene Berechtigungsprüfung mehr im Command selbst.

## `ActionDefinition` - was registriert wird

Module registrieren nicht die rohe `Action`, sondern eine `ActionDefinition<I, R>`,
die die Action mit allem umwickelt, was der Executor für Autorisierung/
Validierung/Audit braucht:

```java
context.platform().actions().register(ActionDefinition.builder(new KickPlayerAction(service))
        .permission(PermissionNode.core("moderation.kick"))
        .target(input -> Optional.of(ActionTarget.player(input.playerId(), input.playerName())))
        .forbidSelfTarget()
        .validator((ctx, input) -> input.reason().isBlank()
                ? Optional.of(ValidationError.of(FailureReason.VALIDATION, MessageKey.of("moderation.kick.reason-required")))
                : Optional.empty())
        .build());
```

Alle Felder außer der Action selbst sind optional mit sinnvollen Defaults
(kein Permission-Zwang, kein Target, `SelfTargetPolicy.ALLOWED`, immer
enabled, immer auditiert). `GetPlayerProfileAction`s Registrierung in
`PlayersModule` ist das minimale Beispiel (nur Permission, `.notAudited()`
weil ein reiner Lesezugriff nicht jedes Mal einen Audit-Eintrag braucht),
`ReloadConfigAction`s Registrierung in `UniversalAdminPlugin` das
Permission-only-Beispiel.

| Feld | Zweck |
|---|---|
| `permission` | `PermissionNode`, das der `Actor` haben muss, oder `null` |
| `validator` | synchrone `ActionValidator<I>` für billige Eingabe-Checks (siehe unten) |
| `targetExtractor` | liefert ein generisches `ActionTarget` (Typ/ID/Anzeigename) aus dem Input, für Self-Target-Check und Audit-`targetId` |
| `selfTargetPolicy` | `ALLOWED` (Default) oder `FORBIDDEN` |
| `enabledCheck` | feingranulares "ist dieses Feature gerade aktiv", unabhängig vom Modul-Enable-Zustand |
| `audited` | ob ein Erfolg einen Audit-Eintrag erzeugt (Default `true`) |
| `auditSummary` | baut den Audit-Summary-Text aus dem Input |

## Validierung

`ActionExecutor` prüft, in dieser Reihenfolge, **bevor** die Action läuft:

1. **Permission** - `Actor.hasPermission(definition.permission())`.
2. **Feature enabled** - `definition.enabledCheck()`.
3. **Self-Target** - nur falls `selfTargetPolicy() == FORBIDDEN`: der aus
   dem Input extrahierte `ActionTarget` darf nicht auf den ausführenden
   `PLAYER`-Actor zeigen.
4. **Input-Validierung** - `definition.validator()`, eine synchrone
   `ActionValidator<I>` für billige, statische Checks (leerer String,
   ungültiges Format).

Alle vier Fehlerfälle werden als strukturiertes `ActionResult.Failure`
zurückgegeben, nie als Exception - siehe [`ActionResult`](#actionresult-statt-exceptions).

**"Target Zustand"** (existiert das Ziel noch, ist der Spieler online, ...)
prüft der Executor bewusst **nicht** generisch - das braucht Domain-Wissen
(einen Repository-Lookup), das nur die Action selbst hat. Solche Checks
laufen in der Action's eigenem `execute(...)` und kommen als ganz normales
`ActionResult.Failure` zurück, genau wie jeder andere Business-Rule-Fehler.

## `ActionResult` statt Exceptions

```java
sealed interface ActionResult<R> {
    record Success<R>(R value, Map<String, Object> metadata) implements ActionResult<R> {}
    record Failure<R>(FailureReason reason, String message, MessageKey messageKey,
                       List<Object> messageArgs, Map<String, Object> metadata) implements ActionResult<R> {}
}
```

`FailureReason` (`VALIDATION`, `NOT_FOUND`, `NOT_PERMITTED`, `CONFLICT`,
`FEATURE_DISABLED`, `INTERNAL_ERROR`) gibt jedem Frontend genug Information,
um eine sinnvolle Fehlermeldung zu rendern, ohne die Action-Internals zu
kennen. Frontends müssen den Failure-Fall behandeln - es gibt keinen
impliziten "wirf einfach eine Exception"-Pfad.

`messageKey`/`messageArgs` lassen einen Failure (oder Success, über
`metadata`) über `MessageService` lokalisiert rendern, statt einen rohen
englischen String hart zu codieren - siehe `GetPlayerProfileAction`, die
`MessageKey.of("players.not-found")` statt eines String-Literals
zurückgibt. `message` bleibt für nicht-lokalisierte Debug-/Log-Zwecke
verfügbar. `metadata` ist eine kleine, action-definierte Bag für alles
Weitere (z. B. eine betroffene Anzahl), ohne für jede Action einen neuen
Result-Typ zu erfinden.

## `Actor` statt Bukkit-`CommandSender`

`Actor`/`ActorType` (`PLAYER`, `CONSOLE`, `WEB`, `SYSTEM`) beschreiben, wer
handelt, ohne dass das `action`-Package von Bukkit-Typen abhängt - absichtlich
mit Blick auf die künftige Web-App: eine Web-Session ist kein
`CommandSender`, soll aber genauso eine `Action` auslösen können.

Jeder `Actor` trägt einen `PermissionEvaluator` ([`dev.universaladmin.permission.PermissionEvaluator`](../../src/main/java/dev/universaladmin/permission/PermissionEvaluator.java)):

```java
public interface PermissionEvaluator {
    boolean has(PermissionNode node);
}
```

Das ist der zentral gekapselte "Permission Resolver" - Code fragt
`actor.hasPermission(node)`, nie `player.hasPermission(...)` verstreut im
GUI-/Command-Code. Für einen echten Bukkit-`Permissible`
(`Player`/`ConsoleCommandSender`) liefert
[`PermissiblePermissionEvaluator`](../../src/main/java/dev/universaladmin/permission/bukkit/PermissiblePermissionEvaluator.java)
(im `.bukkit`-Adapter-Subpackage, damit `permission` selbst frei von
Paper-Imports bleibt) genau das Verhalten, das ein Permission-Plugin
(LuckPerms und ähnliche) sowieso liefert - **Wildcards funktionieren dadurch
automatisch**, ohne eigene Wildcard-Logik: `PermissiblePermissionEvaluator`
delegiert nur an `Permissible.hasPermission`.

- `Actor.player(UUID, String, PermissionEvaluator)` - ein echter Spieler.
- `Actor.console()` / `Actor.system(String)` - immer autorisiert
  (`PermissionEvaluator.allowAll()`), da Konsole und interne Systemaufgaben
  grundsätzlich vertraut sind.
- `Actor.web(String, PermissionEvaluator)` - Platzhalter für eine künftige
  Web-Session (siehe ROADMAP.md Phase 6); es existiert noch keine
  Web-Schicht, aber die Stelle für einen eigenen, session-basierten
  `PermissionEvaluator` ist schon da.

## `Source` - wie die Action ausgelöst wurde

```java
public enum Source { GUI, COMMAND, WEB, API, EXTENSION, SYSTEM }
```

Bewusst getrennt vom `Actor`: derselbe Spieler kann in derselben Sitzung
sowohl über die GUI als auch über `/admin` Actions auslösen - `Source` ist
eine Eigenschaft der einzelnen Anfrage (`ActionContext`), nicht der
Actor-Identität selbst.

## `ActionRequest`

```java
public record ActionRequest<I>(ActionId id, ActionContext context, I input) {
    public static <I> ActionRequest<I> of(ActionId id, Actor actor, Source source, I input) { ... }
}
```

Bündelt eine einzelne Anfrage an `ActionExecutor` - nützlich, wenn ein
Frontend die Anfrage bauen und erst später (z. B. nach einer
Bestätigungs-Dialog-Interaktion) tatsächlich ausführen will.

## Events

`ActionExecutor` feuert `ActionEvent`s (`Executing` vor jeder Prüfung,
danach genau eines von `Executed`/`Failed`) an registrierte
`ActionEventListener`. Kein Bukkit-Event-Typ - bewusst, damit eine künftige
Web-Session oder Extension ohne laufenden Paper-Event-Bus zuhören kann und
`action` frei von Bukkit-Imports bleibt:

```java
platform.actionExecutor().subscribe(event -> switch (event) {
    case ActionEvent.Executing<?> e -> ...;
    case ActionEvent.Executed<?, ?> e -> ...;
    case ActionEvent.Failed<?, ?> e -> ...;
});
```

Ein werfender Listener bricht die Pipeline nicht ab - der Executor fängt
und loggt. Gedacht als Anschlusspunkt für eine spätere Extension-API/
WebSocket-Live-Ansicht, nicht als vollständiges Event-System heute.

## Undo (`ReversibleAction`)

```java
public interface ReversibleAction<I, R> extends Action<I, R> {
    CompletableFuture<ActionResult<Void>> undo(ActionContext context, I input, R result);
}
```

Opt-in: eine Action, die ihren eigenen Effekt rückgängig machen kann,
implementiert zusätzlich dieses Interface. `ActionExecutor.undo(id, context,
input, result)` prüft dieselbe `permission()` wie die Vorwärts-Action und
ruft `undo(...)`, falls die registrierte Action tatsächlich reversibel ist -
sonst `ActionResult.Failure(VALIDATION, "... is not reversible")`.

**Es gibt noch keine Undo-*Historie*** (Stack vergangener Aufrufe, ein
"letzte Aktion rückgängig machen"-GUI-Button) - das ist bewusst
zurückgestellt; dies ist nur der Vertrag, auf dem ein späteres
Undo-System aufbaut.

## Audit-Hook

`ActionExecutor` bekommt einen `AuditService` injiziert und baut nach jedem
Lauf automatisch einen vollständigen `AuditEvent`
([docs/user/audit-log.md](../user/audit-log.md) für die volle Feldliste):
`type` spiegelt die `ActionId` (`namespace:name`), `module`/`target`/
`source` kommen aus `ActionDefinition`/`ActionContext`/`targetExtractor`,
`success` aus dem `ActionResult`. Ein Erfolg wird immer auditiert, außer die
Action hat `.notAudited()` gesetzt (z. B. `GetPlayerProfileAction` - ein
reiner Lesezugriff, bei dem jeder Aufruf sonst nur Log-Rauschen wäre); ein
Fehlschlag (Permission verweigert, ungültige Eingabe, unerwarteter Fehler)
wird nur auditiert, wenn die Action das explizit über
`.auditFailures()` angefordert hat - gedacht für sicherheitsrelevante
Actions, bei denen "jemand hat es versucht und wurde abgelehnt" selbst
protokollierenswert ist.

Für alles, was über die generischen Felder hinausgeht (Grund, Alt-/Neuwert,
Welt/Position, Metadata, Correlation-ID), liefert eine Action optional eine
`AuditDetails`-Instanz über `ActionDefinition.Builder#auditDetails(BiFunction<I, ActionResult<R>, AuditDetails>)` -
so füllt ein Feature-Entwickler nur die Felder, die für seine Action
tatsächlich Sinn ergeben, statt jedes Mal ein vollständiges `AuditEvent` von
Hand zu bauen:

```java
ActionDefinition.builder(new SetGamemodeAction(playerService))
        .permission(PermissionNode.core("players.gamemode"))
        .module("players")
        .target(input -> Optional.of(ActionTarget.player(input.playerId(), input.playerName())))
        .auditDetails((input, result) -> AuditDetails.builder()
                .oldValue(input.previousGamemode().name())
                .newValue(input.gamemode().name())
                .build())
        .build();
```

Fehlgeschlagene Audit-Schreibvorgänge brechen die Pipeline nicht ab (nur
geloggt) - ein Frontend soll nie an einem Audit-Fehler scheitern.

Das ist bewusst der einzige Ort, an dem `action` auf `audit` trifft - siehe
[docs/user/audit-log.md](../user/audit-log.md) für das eigentliche
Audit-System (Repository, Query-Service, GUI), das auf dieser Grundlage
aufbaut.

## Wo Actions registriert werden

`ActionRegistry` ist eine typisierte Registry (`ActionId → ActionDefinition<?,?>`).
Module registrieren ihre Actions in `onEnable`:

```java
context.platform().actions().register(ActionDefinition.builder(new GetPlayerProfileAction(playerService))
        .permission(PermissionNode.core("players.view"))
        .notAudited()
        .build());
```

Ein Frontend baut einen `ActionRequest` mit der `ActionId` und ruft
`platform.actionExecutor().execute(request)` - nie `Action.execute(...)`
und nie eine konkrete Action-Klasse direkt.

## Wann eine Action statt direktem Service-Aufruf?

Nicht jeder Service-Aufruf muss eine `Action` sein. Eine `Action` lohnt
sich, sobald eine Operation von mehr als einer Frontend-Art ausgelöst
werden soll oder auditiert/berechtigt werden muss. Rein interne
Lesezugriffe innerhalb eines Moduls können direkt über den Service laufen.
