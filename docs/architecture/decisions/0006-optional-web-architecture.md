# 0006 - Ein Gradle-Modul heute, dokumentierter Split für api/sdk/web später

## Status

Angenommen

## Kontext

Die Zielarchitektur sieht perspektivisch `universaladmin-core`,
`universaladmin-api`, `universaladmin-sdk` und `universaladmin-web` als
separate Artefakte vor. Aktuell existiert aber keine externe Extension und
keine Web-App, die gegen `-api`/`-sdk` kompilieren würde, und kein
Web-Prozess, der `-web` bräuchte.

## Entscheidung

Ein einziges Gradle-Projekt (`universaladmin-core`, Wurzel des Repos) für
diesen Schritt. Kein Multi-Project-Gerüst mit leeren Modulen. Die künftige
Modulgrenze wird stattdessen im Code markiert:

- Die Interfaces, die eine Extension später braucht (`Module`, `Action`,
  `GuiPage`, `PermissionRegistry`, `AuditService`, ...), leben bereits in
  eigenen, fokussierten Packages statt verstreut - der spätere Schnitt
  "diese Packages wandern nach `universaladmin-api`" ist damit ein
  mechanischer Schritt, keine Neuentwicklung.
- Storage/Threading/Config sind bereits so geschnitten, dass eine Web-
  Schicht dieselben Services aufrufen könnte, ohne den Core zu ändern
  (siehe [../web-future.md](../web-future.md)).

Wann der tatsächliche Multi-Project-Split passiert, steht in
[ROADMAP.md](../../../ROADMAP.md) (Phase 4 für `-api`/`-sdk`, Phase 6 für
`-web`) und wird zu dem Zeitpunkt entschieden, an dem ein Modul auch echten
Inhalt hat.

## Konsequenzen

- Weniger Gradle-Overhead und -Konfiguration jetzt.
- Der Split ist nicht "kostenlos", wenn er kommt - er bringt dann echte
  Arbeit (Versionierung, Publishing, ggf. getrennte Repos für `-sdk`-
  Beispiele). Das wird bewusst in Kauf genommen, statt diese Arbeit heute
  ohne Nutzer vorwegzunehmen.
- Bis zum Split gilt weiterhin
  [0005-extension-ready-design.md](0005-extension-ready-design.md): der
  Code muss so geschrieben sein, dass der Split machbar bleibt.

## Alternativen

- **Multi-Project von Anfang an** (leere `api`/`sdk`/`web`-Module):
  Verworfen - reine Struktur ohne Inhalt macht das Repository komplexer zu
  navigieren, ohne dass heute irgendetwas sie nutzt. Kann bei Bedarf jederzeit
  nachgezogen werden, sobald ein Modul echten Code bekommt.
