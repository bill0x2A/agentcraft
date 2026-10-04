# Agent Office

The language that the mod and the bridge share. Agent Office shows Herdr sessions as mobs in a Minecraft world and lets the player use the terminal of each session.

See [CONTEXT-MAP.md](./CONTEXT-MAP.md) for the glossary of each area.

## Language

**Herdr**:
The external program that owns each shell or agent process and emulates its terminal. The repository pins Herdr 0.8.2 and protocol 20.
_Avoid_: multiplexer, terminal server

**Terminal**:
One Herdr terminal emulator together with its process.
_Avoid_: console, PTY, pane

**Terminal ID**:
The stable identifier that Herdr gives a terminal. It is the only identity that the office trusts.
_Avoid_: pane ID, session ID

**Session**:
A terminal as the office lists it: a terminal ID, a label, an agent, and a status.
_Avoid_: workspace, pane, tab

**Shell session**:
A session in which Herdr detects no agent. A new session is always a shell session.
_Avoid_: empty session, plain terminal

**Agent**:
The AI command-line tool that Herdr detects in a session: `claude` or `codex`. Group launch controls can explicitly start Claude or Codex in a new Herdr shell. Creating a group or importing an issue does not launch agents.
_Avoid_: bot, assistant, AI

**Reported status**:
The raw agent state that Herdr supplies for a session: `working`, `idle`, `done`, `blocked`, or `unknown`.
_Avoid_: state, raw status

**Status**:
The display label that the bridge derives from the reported status: `Working`, `Idle`, `Done`, `Needs attention`, `Unknown`, or `Shell`.
_Avoid_: state

**Bridge**:
The local Python process that connects the mod to Herdr through same-user Unix sockets.
_Avoid_: backend, daemon, server

**Viewer**:
One client connection that shows a terminal and sends input to it.
_Avoid_: client, controller

**Attach**:
To connect one viewer to one terminal through the bridge.

**Detach**:
To end a viewer connection. The terminal and its process continue.
_Avoid_: close, kill, disconnect

**Office**:
The set of features that puts sessions and browsers into a world.
_Avoid_: workspace, room

**Runtime directory**:
The ignored `.runtime/` directory that holds the sockets, the isolated Herdr state, and the verification reports.

## Working groups

A **Working group** has a stable ID, name and working directory. Its optional
members, leader, parent group, instructions, worktree preference, tasks and issue
sources are saved by `bridge/groups.py` outside Minecraft. The `scripts/workspace`
CLI and F6 screen use the same operations. A session can belong to multiple groups;
leaders are group-local. Tasks and issue sources are optional. Local task state
never changes an external issue. A table stores only a group ID.

A **Group browser** runs in the separate Chromium host. Minecraft's MCEF browser
renders its local viewer; the real page survives leaving the game. An ordinary
MCEF browser surface still ends with its world/preview.

## Relationships

- **Herdr** owns every **Terminal**. The **Bridge** lists each **Terminal** as one **Session**.
- A **Session** has exactly one **Terminal ID** and zero or one **Agent**.
- A **Reported status** maps to exactly one **Status**. A **Shell session** always has the **Status** `Shell`.
- The mod speaks only to the **Bridge**. The **Bridge** speaks to **Herdr**.
- A **Detach** never ends a **Terminal**.

## Example dialogue

> **Dev:** "The player closes the terminal screen. Does the **Session** end?"
> **Domain expert:** "No. That is a **Detach**. **Herdr** keeps the **Terminal** and its process."
> **Dev:** "And if Herdr restarts and the **Terminal ID** changes?"
> **Domain expert:** "Then the old **Session** is gone. The office never points an old **Terminal ID** at a different **Terminal**."

## Flagged ambiguities

- "workspace" names a Herdr container and also this repository (`Modding Workspace`, `WORKSPACE_ROOT`). Say **Herdr workspace** or "repository".
- "server" names the Herdr server, the bridge (`bridge/server.py`), and the Minecraft integrated server. Always add the owner.
- "session" also appears in Herdr's own API (`session.snapshot`, `HERDR_SESSION`). In this repository **Session** has only the meaning above.
- `blocked` shows as `Needs attention`. It does not tell a question apart from an approval request.
