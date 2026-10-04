# Mod

The Fabric mod (`modding_workspace`) that shows sessions as agent mobs, renders terminals, and places browser displays in a world. It uses the shared terms in [../CONTEXT.md](../CONTEXT.md).

## Language

### Office

**Private single-player world**:
A single-player world that is not open to LAN. Office controls work only there (`OfficeClient.localWorld`).
_Avoid_: local world, offline world

**Agent mob**:
The custom entity (`AgentMob`) that stands for one session in a world.
_Avoid_: villager, NPC, creature, pet

**Character**:
The look of an agent mob: `claude` (orange crab) or `codex` (white-and-teal robot). The detected agent sets it; without an agent, the choice of the player sets it.
_Avoid_: skin, model, mob type

**Binding**:
The saved record that ties one agent mob to one terminal ID. It also holds the label, the dimension, the home, the character, the optional desk position, and whether the agent is filed away.
_Avoid_: link (as a noun), mapping

**Link**:
The player action that makes a binding for an existing session. **Create + link** makes a shell session first.
_Avoid_: bind, attach, spawn

**Home**:
The saved position and orientation of an agent mob. It leaves home to seek attention from a nearby player and returns when attention clears.
_Avoid_: spawn point, anchor

**Activity**:
The pose state of an agent mob (`AgentActivity`): `Resting`, `Working`, `Needs attention`, `Unavailable`, or `Unknown`. It comes from the reported status, never from terminal output.
_Avoid_: status, animation state

**Unavailable**:
The activity of an agent mob when the bridge is offline or when its terminal ID is no longer in the session list.
_Avoid_: dead, ended, offline

**Office save**:
The file `data/agent-office.json` in a world. It holds every binding of that world.
_Avoid_: config, database

**Office screen**:
The GUI screen that F7 opens. It lists sessions and holds the create, link, and place controls.
_Avoid_: session picker, menu

### Furniture

**Agent desk**:
A block assigned to one session. Its agent's home is the work spot two blocks in
front of the desk. A desk opens that session's terminal.

**Office chair**:
A block at an agent's work spot. The agent sits at home and stands to seek attention.

**Project board**:
A block with a saved project name and list of terminal IDs. Its screen shows live
statuses, opens terminals, and locates agents. Sneak-click accesses the legacy world-local grouping. Normal use links a shared working group by ID; the bridge owns that membership.

**Monitor**:
A compact browser display attached to a furniture block. It shares the browser
surface and controls used by the large browser display.

**Filed agent**:
A binding whose mob has been put away through furniture controls. The terminal
continues running. Any filing cabinet can restore that binding to an empty desk.
_Avoid_: killed session, archived process

### Terminal

**Terminal screen**:
The floating GUI window that draws the cell grid of one terminal and sends input. Its title bar moves it, its lower-right corner resizes it, and closing it detaches the viewer.
_Avoid_: console, terminal view

**Default terminal**:
The terminal that F8 opens when no agent mob is selected. The bridge chooses it.

### Browser

**Browser display**:
The saved entity (`BrowserDisplay`) that shows a web page on a 4 × 2.25 block face in a world.
_Avoid_: monitor, browser screen, world screen

**Browser surface**:
The one live Chromium browser of a browser display (`BrowserSurface`). The in-world face and the focused view share it.
_Avoid_: tab, page, texture

**Focused view**:
The GUI screen that opens on a right-click of a browser display. It gives mouse input, keyboard input, and an address bar.
_Avoid_: browser controls, browser screen

**Standalone preview**:
The GUI screen that F9 opens with no browser display. Its browser closes with it.
_Avoid_: test browser

**Saved address**:
The address that a browser display stores in the world and loads again later. **Go** sets it.
_Avoid_: URL, current page

**Office home page**:
The built-in demo page with the address `office:home`. It is the first saved address of each browser display.
_Avoid_: home

**Browser control endpoint**:
The opt-in Chromium DevTools Protocol endpoint at `127.0.0.1:9222`. The `-PbrowserControl` flag enables it.
_Avoid_: debug port, remote control

**MCEF**:
MCEF Modern, the separate mod that embeds Chromium. The browser features need it; the office works without it.

### Development

**Development client**:
The game that `./scripts/mc client` starts in the `run/` game directory.
_Avoid_: test client, dev game

**Verification**:
An opt-in live test that runs inside the development client in its own game directory. There is an office verification and a browser verification.
_Avoid_: test, check

## Relationships

- A **Binding** ties exactly one **Agent mob** to exactly one terminal ID.
- A session has zero or one **Binding** in each world.
- The **Office save** owns the **Bindings**. Herdr owns the processes.
- When the player removes an **Agent mob**, the session continues and the **Binding** stays.
- The reported status sets the **Activity**:
  - `working` gives `Working`.
  - `blocked` gives `Needs attention`.
  - `idle`, `done`, and each shell session give `Resting`.
  - A missing session or an offline bridge gives `Unavailable`.
  - All other values give `Unknown`.
- A **Browser display** has one **Browser surface** while its chunk is loaded.
- A **Browser display** keeps its **Saved address**. It does not keep page state.

## Example dialogue

> **Dev:** "The player breaks the crab. Do we end the Claude session?"
> **Domain expert:** "No. The **Agent mob** is gone, but the **Binding** and the session stay."
> **Dev:** "The nameplate shows `Unavailable`. Is the bridge down?"
> **Domain expert:** "Possibly. **Unavailable** also occurs when the terminal ID of the **Binding** left the session list."
> **Dev:** "And the **Character**? The session was a shell session at the **Link**."
> **Domain expert:** "The player chose it then. When Herdr detects an agent, the agent sets the **Character**."

## Flagged ambiguities

- "screen" must mean a Minecraft GUI screen only. The button text `Place browser screen` and the README use it for a **Browser display**.
- "monitor" used to mean the large **Browser display** in comments. The **Monitor** furniture block now hosts a compact browser display.
- "home" has two uses: the **Home** of an agent mob, and the **Home** button that loads the **Office home page**.
- "mob" alone is not sufficient. `BrowserDisplay` also extends `Mob`. Say **Agent mob**.
- `AgentActivity.OFFLINE` has the label `Unavailable`. The nameplate shows `Bridge offline` only when the bridge does not answer.
- **Character** is the look. **Agent** is the detected tool. A shell session has a **Character** and no **Agent**.

F6 opens shared working groups. AgentCraft task-wall blocks and project boards link
to group IDs. The starter studio has its own dimension and never rebuilds on each visit.
