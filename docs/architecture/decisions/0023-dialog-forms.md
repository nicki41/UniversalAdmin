# 0023 - Dialog Forms Instead of Prompt Chains

## Status

Accepted (1.5.0)

## Context

Every text input in the menus is a Paper dialog with a single text field
(`GuiTextInput`). Flows that need several values ask for them one after
another: adding to the whitelist asks four times, a title broadcast twice,
the punish wizard walks through up to five screens, and values like a
border center or a teleport target are typed as one string (`"x z"`,
`"x y z world"`) and parsed. The dialog API that `GuiTextInput` already
uses can do more: several inputs in one dialog, multi-line text, sliders,
checkboxes and option lists - on every supported Paper version (1.21.7+).

## Decision

- **`GuiForm` in the API** (`dev.universaladmin.gui`): a builder for one
  dialog with any number of inputs - `text` (single or multi-line, with an
  explicit maximum length), `number` (a slider with range and step),
  `toggle` and `choice` - plus a submit and a cancel button. The submit
  callback gets a `GuiForm.Response` that reads each value by its key. The
  rules of `GuiTextInput` carry over: callbacks are single-use, expire after
  a few minutes, and run on the main thread.
- **Forms collect, actions decide.** A form only gathers values and hands
  them to the same actions as before; validation, permissions and audit
  stay where they are. A form that cannot be parsed (an empty required
  field, a bad duration) reports the error and reopens the page, as the
  prompts did.
- **Inventories stay the navigation.** Menus, lists, confirmations
  (`ConfirmationDialog`) and pickers (`SelectionDialog`) stay inventories;
  forms replace only the chains of prompts:
  - punishing: reason (preset or own text), duration (preset or own text)
    and, with a voice chat connected, the mute kind - in one form
  - adding to the whitelist: reason, notes (multi-line) and expiry
  - title broadcasts: title and subtitle; chat broadcasts: multi-line text
  - enabling maintenance: reason and whether to kick players right away
  - world border: center as two fields, size with a transition time,
    damage and warning as their two values each
  - player values: health, food, XP and speeds as sliders; teleporting to
    coordinates as x, y, z and a world list
- **Sliders only for small, bounded ranges** (health, food, XP percent,
  speeds). Coordinates, border sizes and levels stay text fields - a slider
  across thirty million blocks is not usable.
- **`GuiTextInput` gets an explicit maximum length.** The dialog default is
  32 characters, which silently cut longer reasons short.

## Consequences

- Extensions get the same forms (`GuiForm` is API, `@since 1.4`).
- Fewer round trips: most flows need one form and one confirmation.
- Forms need a client that renders dialogs - every Java client of a
  supported version does, as for the text prompts until now.

## Alternatives

- **Everything as dialogs**, menus included. Dialogs have no item icons,
  tooltips or click types, and staff know the inventory menus - rejected.
- **Keeping the prompt chains.** Works, but is the slowest part of the
  menus today.
