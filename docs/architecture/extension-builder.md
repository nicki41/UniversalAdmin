# Extension Builder and Sharing (Planned)

A **planned**, not-yet-built capability. This page records the goal and the
constraints it has to meet, so that work on the extension API keeps it
possible. Nothing here is implemented.

## Goal

Anyone can build a UniversalAdmin extension in the browser - by drag and
drop, without writing Java - and the result can still grow into a good,
complex extension instead of hitting a ceiling.

- **Visual GUI designer:** lay out menu pages on the chest grid (items,
  names, lore, paging, navigation), wire buttons to actions, preview the
  page as it looks in game.
- **Commands:** define `/admin` subcommands and shortcuts with typed
  arguments (player, number, duration, text, choice) and tab completion.
- **Logic as blocks:** "when this happens" (an event, a command, a button, a
  schedule, an audit entry) → conditions → actions (built-in actions such
  as ban, teleport, broadcast, plus the extension's own). Variables, lists
  and loops for the cases that need them.
- **Everything an extension has today:** settings (shown in the settings
  GUI), permissions, translations (`en_US`, `de_DE`, ...), database tables,
  profile buttons, main-menu entries, audit summaries.
- **Output is a real extension:** the builder produces an ordinary
  extension project against `universaladmin-api` (Gradle project with
  sources) plus the built jar. Nothing runs through an interpreter on the
  server, so a built extension is as fast and as capable as a hand-written
  one.
- **Sharing on the same site:** extensions can be published and found
  there - built ones and hand-written ones. A page per extension with
  description, screenshots, versions, supported UniversalAdmin versions and
  download; search and categories; "open in builder" to use a shared
  builder project as a starting point (a remix) instead of starting from
  zero. This is the extension registry the roadmap mentions, not a second
  one.

## Not hitting a ceiling

The builder is only worth it if a growing extension never has to be thrown
away. Requirements for that:

1. **Every block maps 1:1 to an API concept** (`Module`, `ActionDefinition`,
   `AbstractGuiPage`, `AdminCommand`, `SettingDefinition`, `Migration`, ...).
   No builder-only runtime, no hidden magic - the generated code reads like
   the [example extension](../../examples/example-extension).
2. **Code blocks:** a block can contain hand-written Java for the one part
   the visual blocks cannot express, and stays a block in the builder.
3. **Eject:** export the project and continue in an IDE at any time. The
   generated code follows the [development rules](../development/architecture-rules.md)
   (logic in actions, no blocking on the main thread, no hard-coded texts),
   so it is a good starting point, not a dead end.
4. **Round trip:** the builder stores its own model (a JSON file in the
   project) next to the sources, so a project can be reopened in the
   builder; code blocks and ejected projects are respected, not
   overwritten.
5. **Versioned against the API:** a builder project names the
   `universaladmin-api` version it targets; generated code only uses public
   API, so semantic versioning of the API protects builder extensions the
   same way it protects hand-written ones.

## Open questions

- **Where it runs:** a standalone web app that needs no server at all
  (builds in the browser or in a hosted build service), or part of the
  planned [web panel](web-future.md) with a live preview against a running
  server. The standalone variant could come well before the web panel.
- **Building the jar:** compile in a hosted build service, in the browser,
  or hand the user a Gradle project to build.
- **Sharing details:** accounts (own logins or GitHub/Modrinth sign-in),
  moderation of published extensions, licences for shared builder projects
  and remixes, and whether downloads are mirrored to Modrinth.
- **Safety:** a generated extension is a normal plugin with full server
  access, like any plugin. Shared extensions must therefore be reviewable
  before someone installs them: sources visible on the page (always for
  builder projects), no obfuscation, a clear list of what the extension
  registers (commands, permissions, tables), and a way to report abuse.
- **Which API gaps** the builder exposes (e.g. a declarative way to describe
  GUI layouts) - those become API additions first, usable by hand-written
  extensions as well.

Decisions on these questions get an ADR when the work starts.
