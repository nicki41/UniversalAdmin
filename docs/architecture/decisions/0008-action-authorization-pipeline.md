# 0008 - Zentrale Autorisierung/Validierung über `ActionExecutor`

## Status

Angenommen

## Kontext

[0002](0002-action-system.md) legt `Action<I, R>` als einzigen Ort für
Business-Logik fest, aber nicht, *wer* eine Action ausführen darf oder wie
Eingaben/Targets vor dem eigentlichen Aufruf geprüft werden. In der Praxis
landete das dort, wo es historisch am bequemsten war:
`UniversalAdminCommand#handleReload` prüfte `sender.hasPermission("universaladmin.reload")`
als rohen String-Literal, komplett getrennt von der über `PermissionRegistry`
registrierten `PermissionDefinition` mit demselben Node - und
`GetPlayerProfileAction` hatte trotz registrierter
`universaladmin.players.view`-Permission überhaupt keine Berechtigungsprüfung.
Mit mehr Frontends (Web-API, Extensions) hätte sich dieses Muster
vervielfacht, mit divergierenden Prüfungen pro Frontend - exakt das
Problem, das 0002 für die Business-Logik selbst schon gelöst hatte, nur
eine Ebene höher.

## Entscheidung

Ein `ActionExecutor` ist die einzige Stelle, an der ein Frontend eine
`Action` ausführt - nie `Action.execute(...)` direkt. Module registrieren
nicht mehr die rohe `Action`, sondern eine `ActionDefinition<I, R>`
(Permission, Validator, Self-Target-Policy, Feature-enabled-Check,
Audit-Konfiguration). Der Executor prüft Permission → Feature-enabled →
Self-Target → Input-Validierung, bevor die Action überhaupt läuft, ruft bei
Erfolg `AuditService.record(...)` auf, und feuert `ActionEvent`s
(`Executing`/`Executed`/`Failed`) für alles, was die Pipeline beobachten
will (künftige Extension-API/WebSocket).

Autorisierung selbst läuft über einen `Actor`-getragenen
`PermissionEvaluator` statt über verstreute `Permissible.hasPermission(...)`-
Aufrufe - ein Bukkit-`Permissible` wird über
`dev.universaladmin.permission.bukkit.PermissiblePermissionEvaluator`
adaptiert (analog zum `storage`/`storage.jdbc`-Muster), sodass `permission`
und `action` selbst frei von Paper-Imports bleiben. Details und
Code-Beispiele: [../actions.md](../actions.md).

## Konsequenzen

- Jede Autorisierungs-/Validierungslogik für eine Action lebt an genau
  einer Stelle (`ActionDefinition`), nicht dupliziert je Frontend.
  `UniversalAdminCommand#handleReload` wurde entsprechend umgebaut: der
  Command prüft keine Permission mehr selbst, sondern rendert nur noch das
  vom Executor zurückgegebene `ActionResult`.
- Ein Frontend, das `Action.execute(...)` direkt aufruft statt über
  `ActionExecutor`, umgeht Autorisierung/Validierung/Audit vollständig -
  das ist die eine Sache, die docs/architecture/actions.md explizit als
  "folgt dem Muster nicht" markiert.
- `ActionResult` bekommt `messageKey`/`messageArgs`/`metadata` zusätzlich
  zum bisherigen `message`, damit Fehler aus der Pipeline (Permission
  fehlt, Feature deaktiviert, Self-Target) genauso lokalisiert gerendert
  werden können wie Fehler aus der Action selbst.
- Undo-Vorbereitung (`ReversibleAction`) und Audit-Hook sind bewusst nur
  der Vertrag/Anschlusspunkt, nicht das vollständige Undo- bzw.
  Audit-System - siehe [0009](0009-audit-system.md) für Letzteres.

## Alternativen

- **Permission-Check weiter pro Frontend, nur Business-Logik zentralisiert
  (Status quo vor dieser Entscheidung):** Genau das Muster, das schon beim
  `/admin reload`-Command zu einem rohen String-Permission-Literal geführt
  hat, das an der registrierten `PermissionDefinition` vorbeilief - kein
  struktureller Schutz dagegen für künftige Actions/Frontends.
- **Autorisierung als Teil von `Action#execute` selbst (jede Action prüft
  ihre eigene Permission):** Würde `ActionDefinition` sparen, aber jede
  Action müsste ihre Permission-Prüfung selbst schreiben statt sie
  deklarativ zu registrieren - und ein Frontend könnte eine Action nicht
  mehr generisch (ohne sie zu kennen) auf "brauche ich hierfür eine
  Permission" abfragen, was einer künftigen dynamischen Web-UI die
  Möglichkeit nähme, Buttons/Aktionen vorab auszublenden.
