# Chat

Chat moderation tools that work next to any chat-formatting plugin -
UniversalAdmin only reroutes or blocks messages, it never reformats public
chat.

## Staff chat

- `/staffchat <message>` or `/sc <message>` - sends to everyone with
  `universaladmin.chat.staff` (and the console, `chat.staff-chat.log-to-console`).
- `/sc` without a message toggles **staff chat mode**: while it is on,
  everything you type goes to staff chat.
- Or start a normal chat message with **`#`** (`chat.staff-chat.prefix`;
  set it to `""` to disable).

Staff chat is conversation, not an admin action, so it is not audited.
Muted staff can still use staff chat.

## Chat lock, slow mode, clear

| Command | What it does |
|---|---|
| `/admin chat lock` / `unlock` | While locked, only players with `universaladmin.chat.bypass` can talk |
| `/admin chat slow 5s` / `slow off` | Each player may send one message per interval (max 1h) |
| `/admin chat clear` | Pushes the chat out of view for everyone without bypass (`chat.clear-lines`) |
| `/admin chat status` | Current lock and slow-mode state |

The same is available in `/admin` → **Chat** (left-click cycles slow-mode
presets, right-click sets a custom value). Everyone is told when the chat is
locked, unlocked, slowed or cleared - and by whom. All of it is audited.

Lock and slow mode are deliberately not saved - a restart lifts them.

## Settings

| Setting | Default | Meaning |
|---|---|---|
| `chat.staff-chat.prefix` | `#` | Message prefix that routes to staff chat (max 3 characters, empty = off) |
| `chat.staff-chat.log-to-console` | `true` | Copy staff chat to the console/log |
| `chat.clear-lines` | `100` | Empty lines sent by a clear |
| `modules.chat` | `true` | Switch the whole module off (restart) |

## Permissions

| Node | Default | Meaning |
|---|---|---|
| `universaladmin.chat.staff` | op | Read and write staff chat |
| `universaladmin.chat.manage` | op | Lock, slow mode, clear |
| `universaladmin.chat.bypass` | op | Not affected by lock/slow mode/clear |
