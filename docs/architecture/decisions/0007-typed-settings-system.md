# 0007 - Ein typisiertes Settings-System statt verstreuter `config.getString(...)`-Aufrufe

## Status

Angenommen

## Kontext

`config.yml` sollte von Anfang an mehr als die Handvoll Werte tragen, die
`YamlConfigService` bisher direkt aus `FileConfiguration` gelesen hat
(Datenbank, Locale). Mit GUI-, Audit-, Modul-Toggle-, Performance- und
Wartungs-Einstellungen dazu wächst die Zahl der Config-Werte deutlich -
und mit ihr das Risiko, dass jede Stelle im Code ihren eigenen
`config.getString("irgendwas")`-Aufruf mit eigenem Default und eigener
(oder fehlender) Validierung mitbringt. Das ist genau das Muster, das
[Entwicklungsregeln](../../development/architecture-rules.md) explizit vermeiden soll.

Gleichzeitig soll das System später auch von Modulen und Extensions
genutzt werden können (siehe
[decisions/0005-extension-ready-design.md](0005-extension-ready-design.md))
- nicht nur vom Core.

## Entscheidung

- **Ein** Zugriffspfad von `config.yml` zu Anwendungscode: ein
  registriertes `SettingDefinition<T>` (Key, Typ, Default, Beschreibung,
  `requiresRestart`-Flag, Validator), aufgelöst über `SettingsService.get(key)`.
  Kein Code außerhalb von `dev.universaladmin.settings.YamlSettingsService`
  liest `config.yml` direkt.
- `SettingKey<T>` trennt bewusst zwei Strings: den `configPath` (den
  wörtlichen YAML-Pfad, z. B. `gui.page-size` - global eindeutig über alle
  Namespaces hinweg, weil es dieselbe Zeile derselben Datei ist) und den
  `namespace` (wer die Einstellung besitzt - `core`, ein Modul über
  `ModuleDescriptor.settingsNamespace()`, später eine Extension-ID). Siehe
  [../modules.md](../modules.md) für die Verbindung zu `ModuleDescriptor`.
- Parsing (`SettingType<T>`) und Validierung (`SettingValidator<T>`) sind
  getrennte, kombinierbare Bausteine statt einer monolithischen
  Parse-Funktion pro Setting.
- **Ein ungültiger Wert crasht nie den Server.** `YamlSettingsService`
  fällt bei Parse- oder Validierungsfehlern auf den registrierten Default
  zurück und loggt eine klare Warnung - sowohl beim initialen Start als
  auch bei `/admin reload`.
- `config-version` plus `ConfigMigrationRunner` (im schlanker gewordenen
  `config`-Package) versioniert die Datei selbst, analog zu
  `storage.Migration` für das Datenbankschema - eine bestehende
  Nutzer-Config wird bei einem Update nie stillschweigend überschrieben.

Details: [docs/development/settings.md](../../development/settings.md).

## Konsequenzen

- Neue Config-Werte brauchen mehr Ceremony als ein einzeiliger
  `getString(...)`-Aufruf (eine `SettingKey`-Konstante, eine
  `SettingDefinition`-Registrierung). Akzeptiert als Preis für zentrale
  Typsicherheit, Validierung und einen einheitlichen Reload-Mechanismus.
- Jedes Setting deklariert explizit, ob es live änderbar ist
  (`requiresRestart`). Das zwingt zu einer bewussten Entscheidung pro
  Wert statt eines pauschalen "Reload macht schon alles neu" - siehe
  `ReloadConfigAction`.
- `config`- und `settings`-Package sind jetzt klar getrennt: `config`
  kennt nur noch die Datei-Versionierung, `settings` das eigentliche
  Typsystem. Das ist eine sichtbare Verschiebung gegenüber dem ursprünglich
  in `config.ConfigService` zentralisierten Zugriff.

## Alternativen

- **Ein generisches `Map<String, Object>`-basiertes Config-Objekt** ohne
  Registrierung: spart die Definitions-Ceremony, verliert aber die
  zentrale Stelle, an der alle Settings (mit Beschreibung, Default,
  Restart-Flag) für Doku/GUI/Extensions sichtbar sind - genau das, was
  `SettingRegistry` bereitstellt.
- **Ein bestehendes Config-Framework (z. B. Configurate) einbinden:**
  hätte ähnliche Typsicherheit gebracht, aber eine zusätzliche
  Abhängigkeit für ein Problem, das mit der bereits vorhandenen
  Bukkit-`FileConfiguration` und einer schlanken eigenen Schicht lösbar
  ist - siehe "keine neuen Dependencies ohne klaren Grund" in
  [Entwicklungsregeln](../../development/architecture-rules.md).
