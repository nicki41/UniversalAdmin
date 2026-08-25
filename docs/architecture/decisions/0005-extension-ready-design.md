# 0005 - Built-in-Module nutzen dieselben Abstraktionen wie künftige Extensions

## Status

Angenommen

## Kontext

UniversalAdmin soll später Community- und offizielle Extensions zulassen,
die Module, GUI-Pages, Actions, Permissions, Migrationen usw. registrieren
können (vollständige Liste in [../extensions-future.md](../extensions-future.md)).
Eine öffentliche, versionierte Extension-API wird explizit *nicht* in
diesem Schritt gebaut. Das Risiko: wenn Built-in-Module intern anders
funktionieren als eine spätere Extension funktionieren müsste, erzwingt
der API-Cut später einen Rewrite der Built-ins.

## Entscheidung

Built-in-Module implementieren exakt das `Module`-Interface, das später
auch eine externe Extension implementieren würde - kein interner
"Fast-Path" für Built-ins, der an Zustand hängt, den nur der Core sehen
kann. Alle Registries (`ActionRegistry`, `GuiRegistry`, `PermissionRegistry`,
`ServiceRegistry`, `MigrationRunner`) sind bereits so geschnitten, dass
Herkunft (eingebaut vs. extern) keine Rolle für die Registrierung spielt.

Namespacing (`Key`, siehe `dev.universaladmin.core.id.Key`) ist von Anfang
an Teil jeder Registry-ID (`ModuleId`, `ActionId`, `GuiPageId`,
`AuditEventType`), damit eine künftige Extension mit eigenem Namespace nie
mit einem Core-Namespace (`core:*`) kollidieren kann.

## Konsequenzen

- Jede neue Fähigkeit, die "nur für Built-ins" gebaut wird, ist ein
  Regelverstoß gegen diese ADR, sofern sie nicht explizit als vorübergehende
  Einschränkung dokumentiert wird (z. B. "es gibt noch keinen Extension-
  Loader" ist okay, "Built-ins dürfen Dinge, die eine Extension technisch
  nicht könnte" ist nicht okay).
- Der spätere Schritt "API extrahieren" (siehe
  [0006-optional-web-architecture.md](0006-optional-web-architecture.md))
  wird dadurch zu einem Modul-Split mit dünner Versionierungsschicht
  obendrauf, nicht zu einer Neuentwicklung der Extension-Punkte.
- Es gibt aktuell trotzdem keine echte Abwärtskompatibilitätsgarantie -
  interne Interfaces können sich bis zum API-Cut noch ändern. Diese ADR
  regelt die *Form* der Abstraktionen, nicht deren *Stabilität*.

## Alternativen

- **Erst intern schnell bauen, API-Schicht separat nachziehen:** Ist
  genau das Risiko, das diese ADR vermeiden soll - "separat nachziehen"
  wird in der Praxis zum Rewrite, sobald Built-ins erst mal an internem
  Zustand statt an registrierten Interfaces hängen.
