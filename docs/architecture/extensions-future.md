# Extensions (Future)

Dieses Dokument beschreibt eine **geplante**, noch nicht gebaute Fähigkeit.
Es gibt aktuell keine öffentliche API, keinen Extension-Loader und keine
Trennung zwischen Core und einem `universaladmin-api`-Modul. Was es gibt:
Abstraktionen, die so entworfen sind, dass sie später ohne Rewrite
extern nutzbar werden. Siehe
[decisions/0005-extension-ready-design.md](decisions/0005-extension-ready-design.md).

## Was eine Extension später registrieren soll

Aus der Projektvorgabe, als Checkliste, mit dem Mechanismus, der dafür
schon existiert:

| Erweiterungspunkt | Heutiger Mechanismus |
|---|---|
| Module | `Module`-Interface, `ModuleManager` |
| GUI Pages | `GuiPage`-Interface, `GuiRegistry` |
| Main Menu Entries | noch kein Hauptmenü gebaut (Phase 1) |
| Player Actions | `Action<I, R>`, `ActionRegistry` |
| Player Profile Sections | noch kein Profil-UI gebaut (Phase 1) |
| Admin Actions | `Action<I, R>`, `ActionRegistry` (kein Unterschied zu "Player Actions" im Typsystem - der Unterschied ist, wer sie aufrufen darf, über Permissions) |
| Settings | `SettingRegistry`/`SettingsService` (siehe [docs/development/settings.md](../development/settings.md)) - Namespacing für Core/Modul/künftige Extension existiert bereits, aktuell registriert aber nur `CoreSettings` etwas |
| Permissions | `PermissionNode`/`PermissionDefinition`, `PermissionRegistry` |
| Events | es gibt noch kein UniversalAdmin-eigenes Event-System (aktuell nur Bukkit-Events, die Module intern konsumieren) |
| Audit Events | `AuditEventType`, `AuditService` |
| Storage/Migrations | `Migration`, `MigrationRunner` |
| Dashboard Widgets | existiert erst mit der Web-App, siehe [web-future.md](web-future.md) |
| Web Pages | existiert erst mit der Web-App |
| Web API Hooks | existiert erst mit der Web-App/REST-API |
| Notifications | `NotificationService` (aktuell nur Ingame-Kanal) |

Zeilen ohne heutigen Mechanismus sind kein Widerspruch zur "extension-
ready"-Prämisse - sie existieren schlicht noch nicht als Feature für
irgendjemanden (auch nicht für Built-ins). Sobald sie gebaut werden (siehe
[ROADMAP.md](../../ROADMAP.md)), folgen sie demselben Muster: ein
Interface + eine Registry, kein Built-in-only-Shortcut.

## Was fehlt, bevor Extensions real werden

- **Stabile, versionierte API-Grenze.** Aktuell kann jede interne Klasse
  sich jederzeit ändern. Eine Extension-API braucht Abwärtskompatibilitäts-
  Garantien, die der Core intern nicht braucht.
- **Extension-Loader.** Offen: eigene jars in einem
  `plugins/UniversalAdmin/extensions/`-Ordner (von UniversalAdmin selbst
  geladen) vs. eigenständige Bukkit-Plugins mit `depend: [UniversalAdmin]`
  (von Paper geladen, UniversalAdmin nur als Dependency). Beide Wege sind
  mit dem aktuellen `Module`-Interface kompatibel; die Entscheidung
  beeinflusst nur, *wer* `ModuleManager.enable(...)` für eine Extension
  aufruft.
- **Sandboxing/Vertrauen.** Eine Extension läuft im selben JVM-Prozess wie
  der Core - es gibt keine Isolation. Das ist für ein Server-Plugin normal
  (genau wie bei jedem anderen Bukkit-Plugin), sollte aber in der
  Extension-Dokumentation explizit stehen, wenn sie geschrieben wird.
- **`universaladmin-sdk`.** Beispiel-Extension + Dokumentation, damit
  Dritte nicht raten müssen, wie ein `Module` "richtig" aussieht.

## Was jetzt schon gilt

Baue kein Verhalten, das ein eingebautes Modul kann und eine (hypothetische)
Extension nicht könnte, weil es an internem Zustand statt an einem
registrierten Interface hängt. Das ist die einzige Regel, die *jetzt schon*
durchgesetzt werden muss, damit der spätere API-Cut kein Rewrite ist.
