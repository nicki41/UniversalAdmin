# Release-Prozess

Wie ein UniversalAdmin-Release entsteht. Kurzfassung: **ein Version-Tag löst
alles Weitere aus.** Es gibt keinen manuellen Upload-Schritt und keinen
"Release-Button" - `.github/workflows/release.yml` baut, testet und
veröffentlicht.

## Versionsschema

[Semantic Versioning](https://semver.org/lang/de/), mit Vorab-Suffixen:

| Version | Tag | GitHub-Release-Typ |
|---|---|---|
| `0.1.0-alpha.1` | `v0.1.0-alpha.1` | Prerelease |
| `0.2.0-beta.1` | `v0.2.0-beta.1` | Prerelease |
| `1.0.0-rc.1` | `v1.0.0-rc.1` | Prerelease |
| `1.0.0` | `v1.0.0` | normaler Release |

Der Workflow erkennt `-alpha`, `-beta` und `-rc` im Versionsstring und
markiert den Release automatisch als Prerelease. Alles andere ist ein
normaler Release.

Die Version steht an genau **einer** Stelle: `version = "..."` in
`build.gradle.kts`. `plugin.yml` bekommt sie beim Build eingesetzt
(`processResources`), und `./gradlew -q printVersion` gibt sie aus - das ist
auch, was die CI liest.

## Ablauf

### 1. Version setzen

In `build.gradle.kts`:

```kotlin
version = "0.1.0-alpha.1"
```

### 2. CHANGELOG aktualisieren

Den Inhalt aus `## [Unreleased]` in einen versionierten Abschnitt überführen:

```markdown
## [0.1.0-alpha.1] - 2026-08-25
```

Wenn ein Abschnitt mit exakt dieser Überschrift (`## [<version>]`) existiert,
übernimmt der Release-Workflow seinen Inhalt zusätzlich in die Release Notes.
Gibt es keinen, werden nur die von GitHub aus Commits/PRs generierten Notes
verwendet - kein Fehler, nur weniger Kontext.

### 3. Lokal bauen und prüfen

```bash
./gradlew clean build
```

Muss grün sein - inklusive Tests und `verifyShadedJarDrivers` (öffnet eine
echte Datenbankverbindung durch die fertige jar). Ein roter lokaler Build wird
in der CI genauso rot; der Release entsteht dann nicht.

Vor einem echten Release zusätzlich das, was kein Build prüfen kann: die jar
auf einem tatsächlichen Paper-Server starten und die betroffenen GUI-Pfade
durchklicken. Siehe [RELEASE_READINESS.md](../../RELEASE_READINESS.md) zu den
bekannten Grenzen der automatisierten Prüfung.

### 4. Committen

```bash
git add -A
git commit -m "chore: release 0.1.0-alpha.1"
```

### 5. Taggen

```bash
git tag v0.1.0-alpha.1
```

Der Tag muss exakt der Version aus `build.gradle.kts` mit vorangestelltem `v`
entsprechen. Weicht er ab, **bricht der Release-Workflow ab**, bevor irgendetwas
veröffentlicht wird - das ist Absicht, damit `v0.2.0` nie eine `0.1.0`-jar
ausliefert.

### 6. Pushen

```bash
git push origin main
git push origin v0.1.0-alpha.1
```

## Was danach automatisch passiert

`.github/workflows/release.yml` läuft und:

1. checkt den Tag aus,
2. richtet Java ein (Gradle läuft auf 21, kompiliert mit der Java-25-Toolchain),
3. **prüft Tag gegen Projektversion** - bei Abweichung: Abbruch, kein Release,
4. führt `./gradlew clean build` inklusive aller Tests aus - schlägt etwas
   fehl, entsteht **kein** Release,
5. sucht `build/libs/universaladmin-core-<version>.jar` (die installierbare,
   shaded jar - keine sources-, javadoc- oder unshaded jar),
6. erzeugt daneben `<jar>.sha256`,
7. legt den GitHub-Release an: Titel `UniversalAdmin <version>`, Notes aus
   GitHub-Generierung (plus CHANGELOG-Abschnitt, falls vorhanden),
   Prerelease-Flag je nach Versionssuffix,
8. hängt jar und SHA-256-Datei an.

Verwendet wird ausschließlich das automatische `GITHUB_TOKEN` mit
`contents: write`. Es gibt keine zusätzlichen Secrets.

## Wenn etwas schiefgeht

- **Tag/Version passen nicht zusammen:** Workflow schlägt fehl, nichts wurde
  veröffentlicht. Version oder Tag korrigieren. Einen bereits gepushten
  falschen Tag löscht man mit `git push origin :refs/tags/v0.2.0` und setzt ihn
  neu - der Commit-Verlauf bleibt unangetastet.
- **Build/Tests rot:** Ursache beheben, normal committen und pushen, danach
  den Tag neu setzen. Kein Force-Push auf `main`.
- **Release entstand mit falschem Inhalt:** neuen Patch-Release
  (`0.1.0-alpha.2`) hinterherschieben statt einen veröffentlichten Release zu
  überschreiben.

## Modrinth

Noch **keine** automatische Modrinth-Veröffentlichung: es gibt weder ein
Modrinth-Projekt noch einen API-Token dafür. Die vorbereitete Projektseite und
die Checkliste stehen in [modrinth.md](modrinth.md).

Der Release-Workflow ist so aufgebaut, dass das später ein zusätzlicher Schritt
am Ende ist - Version, jar-Pfad und Prerelease-Flag stehen bereits als
Step-Outputs zur Verfügung; ergänzt werden müssten nur ein Upload-Schritt und
ein Repository-Secret mit dem Modrinth-Token.

## Was dieser Prozess bewusst nicht tut

- **Keine automatischen Tags.** Ein Release ist eine bewusste Entscheidung; CI
  erzeugt keine Versionen von selbst.
- **Kein Publish aus `build.yml`.** Der Build-Workflow lädt die jar nur als
  Actions-Artifact hoch (nachvollziehbar, temporär), veröffentlicht aber nie.
- **Keine automatischen Dependency-Updates.** Kein Dependabot; Abhängigkeiten
  werden bewusst manuell aktualisiert (siehe
  [SECURITY.md](../../SECURITY.md#abhängigkeiten)).
