# 0001 - Modularer Core statt monolithischer Plugin-Klasse

## Status

Angenommen

## Kontext

UniversalAdmin soll acht eingebaute Fachbereiche (Players, Moderation,
Server, Worlds, Whitelist, Performance, Audit Log, Settings) abdecken und
langfristig externe Extensions zulassen. Ein einzelner
`JavaPlugin`-Listener/Command-Wust pro Feature führt erfahrungsgemäß zu
genau der Art von unwartbarem Admin-Plugin, die dieses Projekt vermeiden
soll (siehe Projektphilosophie in [../overview.md](../overview.md)).

Zusätzlich gibt es keinen Dependency-Injection-Rahmen (bewusst - siehe
Alternativen unten), aber trotzdem den Wunsch nach klar geschnittenen,
unabhängig testbaren Einheiten.

## Entscheidung

- Ein `Module`-Interface, das Fachbereiche als in sich geschlossene
  Einheiten modelliert, die sich bei geteilten Registries anmelden statt
  selbst Zustand zu halten.
- Ein einziger Composition Root (`UniversalAdmin`), von Hand in
  `UniversalAdminPlugin#onEnable` zusammengebaut und per Konstruktor an
  alles weitergereicht, was es braucht. Das ist das einzige bewusst
  erlaubte "God Object" im Projekt.
- Keine DI-Framework-Abhängigkeit (Guice, Spring, etc.). Für die aktuelle
  Größe ist manuelles Wiring einfacher zu lesen und zu debuggen als eine
  Framework-Konfiguration, und es entfällt eine weitere Abhängigkeit.

## Konsequenzen

- Neuer Service = neuer Konstruktor-Parameter an den Stellen, die ihn
  brauchen. Das ist mehr Tipparbeit als ein DI-Framework, aber jede
  Abhängigkeit ist im Code sichtbar, nicht in einer Annotation versteckt.
- `UniversalAdmin` wächst mit jedem neuen Querschnittsservice. Das ist
  akzeptiert, solange es nur *Registries/Services referenzieren*, nicht
  *Business-Logik enthalten* tut - siehe [Entwicklungsregeln](../../development/architecture-rules.md).
- Wenn das manuelle Wiring bei wachsender Modulzahl unhandlich wird, ist
  ein DI-Framework eine spätere, bewusste Entscheidung (neue ADR), kein
  stillschweigender Umbau.

## Alternativen

- **DI-Framework (Guice/Spring):** Mehr Boilerplate-Reduktion bei großer
  Modulzahl, aber eine zusätzliche Kern-Abhängigkeit und eine Indirektion,
  die für ein Projekt dieser Größe (noch) nicht gerechtfertigt ist.
- **Ein Bukkit-Plugin pro Modul:** Würde echte Prozess-/Classloader-
  Isolation geben, aber jedes Modul bräuchte eine eigene Datenbankverbindung
  und ein eigenes Update/Versionsschema - Overhead ohne Nutzen, solange
  alle Module im selben Repository entwickelt werden.
