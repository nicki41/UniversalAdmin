# Development Setup

## Voraussetzungen

- JDK 25 (wird bei Bedarf automatisch über den
  `foojay-resolver-convention`-Gradle-Plugin beschafft, falls lokal keine
  Java-25-Installation gefunden wird - kein manuelles JDK-Management nötig).
- Kein lokal installiertes Gradle nötig, der Wrapper (`gradlew`/`gradlew.bat`)
  reicht.

## Bauen

```bash
./gradlew build
```

Baut, testet und erzeugt `build/libs/universaladmin-core-<version>.jar` -
bereits mit `sqlite-jdbc`, `mariadb-java-client` und `HikariCP` shaded
(relociert unter `dev.universaladmin.libs.*`, siehe `build.gradle.kts`).

Nur kompilieren, ohne Tests/Jar:

```bash
./gradlew compileJava
```

Nur Tests:

```bash
./gradlew test
```

## Lokal gegen einen Paper-Server testen

Es gibt aktuell kein automatisiertes Dev-Server-Setup in diesem Repository.
Manuell:

1. `./gradlew build`
2. Ein aktuelles Paper-Server-Jar (passend zur in `build.gradle.kts`
   referenzierten Paper-API-Version) in einem separaten Ordner aufsetzen.
3. `build/libs/universaladmin-core-<version>.jar` nach `plugins/` kopieren.
4. Server starten, `eula.txt` akzeptieren, `config.yml` unter
   `plugins/UniversalAdmin/` prüfen (siehe
   [docs/user/configuration.md](../user/configuration.md)).

Ein `run/`-Ordner für einen lokalen Testserver ist in `.gitignore`
vorgesehen, falls das später automatisiert wird (z. B. über das
`xyz.jpenilla.run-paper`-Gradle-Plugin) - das ist noch nicht eingerichtet.

## IDE

Jedes IDE mit Gradle-Unterstützung (IntelliJ IDEA, VS Code mit Java-
Extensions) funktioniert - `settings.gradle.kts`/`build.gradle.kts`
importieren lassen, Java-Toolchain-Erkennung übernimmt den Rest.
