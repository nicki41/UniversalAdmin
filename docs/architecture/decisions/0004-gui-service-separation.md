# 0004 - Strikte Trennung von GUI/Command-Frontend und Application Service

## Status

Angenommen

## Kontext

GUI-Klick-Handler und Command-Executors sind der Ort, an dem Business-
Logik in Admin-Plugins erfahrungsgemäß landet, weil es der kürzeste Weg
zum sichtbaren Ergebnis ist. Das macht die Logik weder testbar (sie hängt
an einem Bukkit-`InventoryClickEvent`) noch wiederverwendbar (eine
künftige Web-App oder ein Command für dieselbe Aktion müsste sie
duplizieren).

## Entscheidung

- `GuiPage` und Command-`Executor`-Implementierungen dürfen ausschließlich
  Services oder `Action`s aufrufen. Keine Berechnung, keine Persistenz,
  keine eigenständige Berechtigungslogik (Permission-*Checks* ja,
  Permission-*Entscheidungen mit eigener Logik* nein) direkt im Handler.
- Eine `GuiPage`-Implementierung bekommt ihre Abhängigkeiten (Services/
  Actions) über den Konstruktor, nicht über Laufzeitzugriff auf ein
  globales `UniversalAdmin`-Objekt beim Öffnen der Seite. Das erzwingt,
  dass eine Seite ihre tatsächlichen Abhängigkeiten explizit deklariert
  und macht sie ohne laufenden Server testbar (Service faken, Klick-
  Handler-Methode direkt aufrufen).
- Details und Code-Beispiel: [../gui.md](../gui.md).

## Konsequenzen

- Jede neue GUI-Funktion braucht (mindestens) einen Service/eine Action
  darunter, auch wenn "es wäre einfacher, das hier direkt reinzuschreiben"
  stimmt. Das ist der bewusste Trade-off.
- Reviews können diese Regel mechanisch prüfen: taucht `Connection`,
  `PreparedStatement` oder eine mehrzeilige Berechnung in einer
  `GuiPage`-/Command-Klasse auf, ist das ein Regelverstoß.
- Die Web-App (siehe [../web-future.md](../web-future.md)) kann später
  dieselben Services/Actions nutzen, weil GUI/Commands sie nie mit
  Logik "verunreinigt" haben.

## Alternativen

- **Logik direkt im Click-Handler, "wird schon refactored, wenn nötig":**
  Ist der Ausgangszustand, den dieses Projekt explizit vermeiden soll
  (siehe Projektphilosophie). Refactoring nach der Tatsache passiert in
  der Praxis selten, solange das Plugin funktioniert.
