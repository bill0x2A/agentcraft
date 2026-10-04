# Minecraft modding workspace

## Working groups and the AgentCraft studio

Launch with `mc-office` (or `./scripts/office game`), join a private world, and press
**F6**. **Enter studio** generates AgentCraft's starter building in a separate
studio dimension. Your existing world is preserved. `/office studio` enters it;
`/office overworld` returns to the overworld spawn. The studio generates once, so
subsequent visits preserve your edits. Build additional offices with the supplied
blocks and link their boards to different groups.

**New group** needs a name and a working directory. It needs neither an issue
tracker nor tasks. Add existing Herdr sessions, or explicitly start a shell,
Claude or Codex session. Open a new agent's terminal to handle its normal login
or directory-trust prompts. **Place** puts our crab or robot near you. Existing
sessions keep their current directories and conversations.

Groups support named roles, an optional leader, subgroups, instructions and a
worktree preference. The preference is context for the agents; the office does
not force worktrees or run a fixed lead/worker scheduler. Agents receive a
startup instruction to read their group context and use the same CLI you use to
coordinate. They wait for your work instructions. No AI processes are started by
opening a group, importing an issue or running the tests.

Right-click a **Project board** or the studio's large task wall to link a group.
Membership and tasks live outside Minecraft in `.runtime/groups.json`; the block
stores only the group ID. Multiple boards can show the same group. Sneak-click a
Project board to access its older world-local session grouping controls.

### Normal terminal use

`herdr-office` opens the same Herdr server. The group CLI works with Minecraft
closed; `./scripts/office start` starts/reuses Herdr and the bridge without the game.
New group sessions have `workspace` on PATH and `OFFICE_GROUP` set.

```sh
./scripts/workspace create "Investigate builds" --cwd /path/to/project
./scripts/workspace launch "Investigate builds" --agent shell --name Research
./scripts/workspace join "Investigate builds" --terminal term_ID --role reviewer
./scripts/workspace leader "Investigate builds" --terminal term_ID
./scripts/workspace create "Backend" --parent "Investigate builds" --cwd /path/to/project
./scripts/workspace task "Investigate builds" --title "Find the slow build step"
./scripts/workspace list
./scripts/workspace context "Investigate builds"
./scripts/workspace attach term_ID
```

Use `--agent claude` or `--agent codex` only when you want to launch that tool.
`--json` before a subcommand provides structured output for agents. `send` sends
an explicit message to a detected agent; it refuses shell sessions and agents
whose reported state is blocked, so open their terminal to address attention
prompts first. Removing a member never kills its session.

A focused Minecraft terminal or `workspace attach` explicitly takes control.
Passive world displays do not steal control. Contents and the running process
survive handoffs; close and reopen a terminal window to reclaim it. The normal
Herdr app remains available through `herdr-office`.

### Optional Linear and GitHub issues

In a group's **Issues** tab, connect a Linear project by name or a GitHub
`owner/repository`, browse issues, and import only the ones you want. Connect
multiple sources when needed. Imports retain their source link and are
idempotent within a group. The same issue can be visible in another group without
sharing local assignments. Disconnecting a source preserves already imported work.

Set `LINEAR_API_KEY` in the repository's ignored `.env` file or in the environment
that starts the bridge. The CLI reads the same file. GitHub uses `GH_TOKEN` /
`GITHUB_TOKEN` from those locations, then your existing `gh` login; public
repositories also work without authentication. Environment values take precedence
over the corresponding `.env` entry. File values are read literally and refreshed
on each connector request; they are never evaluated as shell commands or exported
into agents. Tokens are never saved in group or world files. After changing the
bridge's environment, run `./scripts/office restart-bridge`; this preserves Herdr
and its sessions. Editing `.env` needs no restart.

```sh
./scripts/workspace projects
./scripts/workspace projects --cursor CURSOR_FROM_PREVIOUS_PAGE
./scripts/workspace source "Backend" --provider linear --scope PROJECT_UUID --name "Product API"
./scripts/workspace source "Backend" --provider github --scope owner/repository
./scripts/workspace issues "Backend" --source source_ID
./scripts/workspace import "Backend" --source source_ID --issue ENG-123
```

Freeform and imported tasks share local status and assignment controls. Assignment
does not start work; **Send to assignee** sends the task when you choose. External
issue status, assignees and comments are read-only in this version. Completing a
local task does not close the external issue.

### Persistent group browsers

A group's **Settings → Browser / Place browser** opens its shared Chromium page.
The page lives in a separate background process and continues running when
Minecraft closes; the Minecraft display streams images and forwards input. The
original direct MCEF browsers remain available through F7/F9. Group browsers
currently stream about six frames per second, intended for monitoring and normal
page interaction rather than video playback.

Requires Node 22+ and Chrome/Chromium; `OFFICE_CHROME` can select its executable.
The helper uses `.runtime/browser-profile`, separate from your normal profile.
Viewers use a private local capability URL. Agents can use the local CDP endpoint
at `127.0.0.1:9333`, or `./scripts/browser --persistent list|eval|navigate`.
`./scripts/workspace browser GROUP` returns the same viewer URL for use outside
the game. Do not share that URL. Restarting Chromium restores saved addresses,
not arbitrary in-memory page state; closing Minecraft preserves the live pages.

```sh
./scripts/browser-host status
./scripts/browser-host start
./scripts/browser-host stop
```

### Source and verification

The studio, materials and supporting block/render code are adapted from
[AgentCraft](https://github.com/blendi-remade/agentcraft) revision
`0be815deb4833a9ce0f070c29737bba882e98759`. Attribution and MIT terms are in
`licenses/AgentCraft-origin.txt` and `licenses/AgentCraft-MIT.txt`. Its fixed
Claude-only Foreman scheduler and named cast are not included. Our character
models, terminals, furniture and browser integration are retained.

Run `PYTHONPATH=bridge python3 -m unittest bridge/test_groups.py` and
`./scripts/mc build`. `bridge/test_handoff.py` needs the running office Herdr
server and creates/cleans only its disposable shell. `bridge/test_group_live.py`
checks a real group shell's working directory, CLI availability and inherited
group context, using temporary group data. The opt-in
`-PworkspaceVerification` client uses `run-workspace-test`, a separate bridge
socket and group data; `bridge/workspace_fixtures.py` prepares its shell fixtures.
See `docs/verification/workspace-live.txt` for the recorded live result.



Fabric development environment and a working Herdr agent-office prototype for
Minecraft Java Edition. Press **F7** in a single-player world to create or link
sessions to custom mobs: an orange Claude crab and a white-and-teal Codex robot.
Right-click a mob to use its real terminal. Place live browser screens from F7.
**F8** also opens the original default terminal from the title screen or a world.
Herdr owns the shell/agent process and terminal emulator; Minecraft renders its
cell grid and forwards input through a small local Python bridge.

## Run the terminal prototype

Requirements: Linux, Python 3, and **Herdr 0.8.2** on `PATH`, plus the Java setup
below. This adapter is deliberately pinned to Herdr's private protocol 20.

Run these in two terminals:

```sh
# Terminal 1: start/reuse the isolated Herdr session and serve the bridge
./scripts/terminal

# Terminal 2: launch Minecraft; join a single-player world and press F7
./scripts/mc client
```

The terminal opens as a floating window over the title screen or world. Drag its
title bar to move it and its lower-right corner to resize it. The terminal
process receives the new grid size, and the window remembers its position and
size for the game session. Run your usual CLI inside
it, such as `codex`. CLI login and permissions remain that tool's responsibility.
**F8 closes the terminal view without killing the session.** Closing Minecraft
or restarting the bridge also leaves Herdr and its processes running.

| Control | Action |
| --- | --- |
| F7 (in a private single-player world) | Open the office session picker |
| Right-click a linked mob | Open its specific session |
| Right-click a linked mob with an office item | Use the item; see [Office items](#office-items) |
| F8 / title-bar × | Open/close terminal window |
| Drag title bar / lower-right corner | Move / resize terminal window |
| Ctrl+= / Ctrl+- | Larger/smaller terminal text |
| Ctrl+0 | Default terminal text size |
| Escape | Send Escape to the terminal application |
| Ctrl+Shift+V | Paste clipboard as bracketed paste |
| Ctrl+Shift+C | Copy the visible terminal screen |
| Mouse wheel | Herdr terminal scroll |
| F6 | Toggle SGR mouse input; off by default |

Enable mouse input only for an application that supports SGR mouse reporting;
protocol 20 does not expose its mouse mode to this adapter. Keyboard input,
colours, Unicode, cursor, alternate screen, and window resize are implemented.
F6/F8 are reserved; F13–F24, inline images, clickable hyperlinks, and text
selection are not implemented. Complex emoji/wide glyphs and application-specific
keyboard modes need further compatibility work. Bracketed paste requires support
in the receiving application.

The terminal has its own text size, independent of Minecraft's GUI scale. It
starts a little smaller than the GUI text. The chosen size lasts until the game
closes; a size too large for the window is reduced to fit.

Runtime state lives under ignored `.runtime/`, with same-user Unix sockets and
no TCP listener. The helper isolates Herdr's configuration/state for this project;
it does not attach to your usual Herdr server. `./scripts/terminal status` shows
the isolated session. Ctrl+C stops the foreground bridge. To deliberately end
the session **and its terminal processes**, use `./scripts/terminal stop`.
Persistence here means surviving viewer disconnects, not surviving a reboot.

## Sessions as mobs

In a private single-player world, stand near solid ground with space two blocks
in front of you and press **F7**. **Create + link** creates a named shell session
and a custom mob; **Link** attaches an existing session. Choose **Codex** or
**Claude** before creating/linking a shell. Once Herdr detects the actual agent,
its identity chooses the model automatically. Creating a shell does not launch
an AI agent: right-click the mob and run your preferred CLI yourself.
Each session can have one linked mob per world.

### Office items

The items are in the creative **Agent Office** tab. Each has a two-ingredient
shapeless recipe, and `/give @s modding_workspace:<id>` also works. No item is
used up. They work in a private single-player world. The **Office Handbook**
describes them in the game.

| Item (`id`) | Recipe | Use |
| --- | --- | --- |
| Office Handbook (`handbook`) | book + paper | Right-click the air to read one page for each item |
| Claude Egg, Codex Egg (`claude_egg`, `codex_egg`) | egg + orange or cyan dye | Use on a block: a new shell session named `Claude 1`, `Codex 1`, … starts and its crab or robot gets its home there. The egg starts no agent |
| Name Badge (`name_badge`) | paper + string | Right-click a mob to rename it. Only the nameplate changes: the session keeps its name in Herdr and in the F7 list |
| Home Flag (`home_flag`) | stick + white wool | Right-click a mob, then a block. The mob walks to its new home and faces where you stood. The home stays in the mob's dimension and replaces a desk assignment |
| Memo (`memo`) | paper + feather | Right-click a mob and type one line. It is pasted into the terminal, followed by Enter unless you turn that off |
| Status Tablet (`status_tablet`) | iron ingot + redstone | While held, lists each mob with its status and distance, attention first. Right-click the air to open the F7 screen |
| Terminal Display (`terminal_display`) | glass pane + redstone | Use on a block: a 4 × 2.25 live view of a terminal, the terminal counterpart of the browser display. A wall face holds it; on the ground it stands and faces you. It then asks which terminal to show. Right-click it to open the terminal window; sneak and right-click it to choose another terminal |
| Dismissal Slip (`dismissal_slip`) | paper + red dye | Right-click a mob to remove it and its binding, so the session can be linked again from F7. Right-click a terminal display to remove it. The session and its process continue |

A terminal display needs no agent mob. Its chooser lists every session, shows
a mob's label for a linked session, and has **Create + show** for a new shell
session with no mob, **Open** for the terminal window, and **Remove**.
**F7 → Place terminal display** places one four blocks ahead, as the browser
button does. As a shortcut, right-click a mob with the item first: the next
display shows that mob's terminal at once. The world save keeps the terminal ID
of each display.

Herdr permits one attached client for each terminal and starts each new attach
at 100 × 32. The terminal window, the terminal displays and the Memo therefore
share one connection per terminal, and a display never sends input or a size:
it shows the grid of the last window size, or 100 × 32 before the window was
opened. A display of an ended session keeps its last frame.

The orange crab and white-and-teal robot have original cuboid models and textures.
They keep saved homes beside your builds. Herdr's state updates their pose,
animated limbs, eyes and nameplate roughly every two seconds:

| Herdr state | Mob behavior |
| --- | --- |
| Shell, idle, done | Resting: gentle motion, relaxed limbs, dim eyes |
| working | Working: animated hands/claws; robot types at a small keyboard |
| blocked | Runs in front of the nearby player and faces them, with amber eyes, waving limbs and a visible `!` |
| unknown | Neutral pose and Unknown label |
| Bridge offline / terminal ended | Slumped, darkened mob with explicit offline/unavailable label |

“Needs attention” does not distinguish a prompt from an approval. “Resting” is
reported activity, not an independent assessment of task completion. Old villager
bindings migrate in place to custom mobs with the same UUID and terminal link.

When attention clears, mobs walk back to their saved position and original facing.
They follow players within 48 blocks in the same dimension, choosing clear ground
in front of the player and navigating around obstacles. If no player is nearby,
they return home. An obstructed return falls back to the saved home after 20 seconds
if that space is clear. Older saves acquire home orientation on first load.

Ordinary damage and wandering are disabled. Removing a mob does not kill its
process. Removed mobs retain their binding; automatic replacement remains future work.

Mob UUIDs, stable terminal IDs, labels, dimensions, and home positions/orientations are saved
atomically in each world's `data/agent-office.json`. The map survives world
reloads. Terminal processes are owned by Herdr, so leaving the view/world or
Minecraft does not end them. If Herdr restarts and gives a terminal a new ID, the
old link remains unavailable; it never silently targets another session.

Minecraft owns entities and interaction. The local bridge lists/creates sessions
and routes each attachment to its explicit terminal ID. Herdr owns the processes
and terminal emulation. Office controls are disabled on remote or LAN-published
worlds. Multiplayer remains a separate milestone.

When upgrading from the original terminal-only prototype, close the old game and
restart the foreground bridge before launching this build. Restarting the bridge
preserves Herdr sessions; `scripts/terminal stop` would end them.

## Office furniture

Five craftable blocks are available in the creative **Functional Blocks** tab:
**Agent Desk**, **Office Chair**, **Project Board**, **Monitor**, and **Filing Cabinet**.
All face you when placed. Sneak while holding a block to place it against furniture.

- **Agent Desk:** right-click an empty desk to assign an existing session, move its
  agent, or restore a filed agent. Its work spot is **two blocks in front of the
  desk**, on solid ground. Right-click an assigned desk to open its terminal;
  sneak-right-click with an empty hand to manage it or file its agent away.
  Create new sessions through F7 first. Each desk holds one agent.
- **Office Chair:** put it on that work spot, facing the desk. Both agent models
  sit while at home, stand when attention is needed, and return to their seat.
  Right-click an occupied chair to open its agent's terminal. Removing the chair
  returns the agent to standing at the same home.
- **Project Board:** name a project and add sessions. Its list shows their live
  statuses, opens individual terminals, and locates agents by home coordinates.
  Names and membership are saved with the board. Removing a board removes that
  grouping; the sessions and agents remain.
- **Monitor:** place it on top of a desk or another surface. Right-click to start
  and control its live browser. It shares the existing address bar, navigation,
  and persistent URL behavior. Breaking the block removes its browser display.
  MCEF Modern is required for browsing.
- **Filing Cabinet:** choose **File away an agent** to remove its mob and free its
  desk while keeping its terminal process running. **Restore to desk** lets you
  select a filed session, then right-click an empty desk to restore the same mob
  UUID and terminal ID. Any cabinet accesses the world's filed sessions, so
  breaking a cabinet never loses them. An active agent must be in a loaded chunk
  before it can be moved or filed away.

The desk has a one-block gap before its work spot to leave room for both the
robot and the wider crab. Keep two blocks of headroom above the work spot/seat.
Desks and filing state live in the office save; older office saves are migrated
when used. Furniture follows the same private-single-player restriction as F7.

| Furniture | Crafting materials |
| --- | --- |
| Agent Desk | 5 planks, 1 iron ingot |
| Office Chair | 4 wool, 1 iron ingot |
| Project Board | 3 copper ingots, 3 paper, 1 stick |
| Monitor | 4 iron ingots, 2 glass panes, 1 redstone dust |
| Filing Cabinet | 6 planks, 2 iron ingots, 1 chest |

Block IDs are `modding_workspace:agent_desk`, `office_chair`, `project_board`,
`monitor`, and `filing_cabinet` (all use the `modding_workspace:` namespace).
For example: `/give @s modding_workspace:agent_desk`.
The original cuboid models use Minecraft's existing textures and can be
regenerated with `python3 scripts/generate-office-furniture.py`.

Run `./scripts/mc client -PfurnitureVerification` for the isolated furniture
checks. It uses synthetic session IDs, creates its own world, and exits after
checking both seated models, attention movement, interaction routing,
filing/restoration, a live Chromium monitor, and world persistence. Results are
written to `.runtime/furniture-verification.txt`, with screenshots under
`run-furniture-test/screenshots/`.

## Browsers in the world

The development client includes **MCEF Modern 0.3.3+mc26.3.jcef146.0.10** directly.
WebDisplays is not required. Development launches share the pinned Chromium
engine in `.tools/browser/`, including verification clients. The existing
installation has been copied there, so this workspace no longer needs another
first-use download. A fresh workspace downloads the engine on first browser use;
the focused view shows its stage, percentage when available, elapsed time and
a notice if progress has not changed for over a minute. This was exercised on
this machine's Linux x86_64 graphics stack.

- **F7 → Place browser screen** creates a 4 × 2.25 block display four blocks ahead.
  Leave a clear 5 × 3 area for its frame. The screen faces you.
- **Right-click the screen** for mouse/keyboard input and an address bar.
- **Go** loads and saves an HTTP(S) address. **Home** restores the local demo.
  **Back** and **Reload** control Chromium.
- **Escape / Close** returns to the world; the same browser keeps running while
  the monitor remains loaded. **Remove** deletes this monitor and closes its page.
- **F9** opens a standalone preview, including from the title screen. Closing
  that preview closes its browser.

A monitor saves its UUID, position, orientation and chosen address with the world.
Leaving the world or unloading its chunk releases the browser. Returning creates
a fresh page at the saved address; unsaved form state is not persisted across
world/chunk reloads. Links/redirects and external browser-control navigation affect
the live page, while **Go** sets the address to restore next time. Browser login
state belongs to Chromium's profile, separate from desktop Chrome. Browser controls
are currently for private single-player worlds; separate monitors have separate
pages but share the embedded Chromium profile.

### Let an agent use the visible browser

Start Minecraft with the opt-in local control endpoint:

```sh
./scripts/mc client -PbrowserControl
# Once a browser screen is open/placed (Node 22+):
./scripts/browser list
./scripts/browser eval TARGET_ID 'document.title'
./scripts/browser navigate TARGET_ID http://localhost:3000
```

The endpoint is **http://127.0.0.1:9222**, Chromium DevTools Protocol (CDP).
An agent can use the helper or a CDP-capable browser tool against the listed page
ID. Browser actions on that page appear on its Minecraft monitor. This endpoint
is disabled by default and grants local processes control of the embedded pages;
keep it on loopback. The integration removes upstream's `--disable-web-security`
flag so normal browser origin checks remain enabled. Wheel events use Chromium's
in-process input API because the pinned MCEF AWT wheel path did not scroll on this runtime.

Herdr still manages the agent's terminal process. Minecraft owns the visible
browser; CDP connects the agent's browser tool to it. An existing Chrome,
Playwright, or Codex-app browser is a separate browser and is not automatically
mirrored. No personal agent configuration is changed and no agents are launched
by enabling this endpoint. For a packaged installation, install the pinned
[MCEF Modern](https://modrinth.com/mod/mcef-modern) alongside this mod and Fabric API.
Video codecs, browser permission prompts, popups and complex sites have not been
comprehensively tested.

## Validation

```sh
./scripts/mc build
./scripts/mc client -PattentionVerification # Disposable world; closes after movement checks
python3 bridge/test_live.py   # Requires the isolated Herdr server to be running
python3 bridge/test_office.py # Two-session control, isolation and ended-session checks
```

The live test creates and removes its own temporary workspace. It checks actual
PTY output, colours/Unicode, keyboard input, bracketed paste, mouse input, resize,
alternate-screen restoration, and detach/reconnect to the same process.
The development client has also rendered a real Codex CLI session and its
`TERMINAL_AGENT_OK` response. `-PterminalPreview` auto-opens the screen and saves
a developer screenshot under `run/screenshots/`.

The live Minecraft verification runs in its own game directory and creates a
fresh flat test world. It checks the entity interaction callback, actual terminal
text input, return to home, a full world save/reload, process identity and terminal
contents after reload, unavailable nameplates, the Create + link button, and
session survival after mob removal. It also exercises original villager migration,
version-1 save migration, crab/robot activity transitions, real browser mouse and
text input, shared in-world/focused page state, local web-app navigation, saved
monitor restoration and removal. Status metadata is synthetic on real shell
fixtures; these checks do not launch Claude or Codex. To reproduce with the isolated Herdr server
already running:

```sh
python3 bridge/office_fixtures.py prepare-agents
python3 bridge/server.py --socket .runtime/office-test.sock  # Keep running
# In another terminal:
python3 bridge/office_fixtures.py watch  # Another terminal: drives fixture states/local web page
./scripts/mc client -PofficeVerification -PagentVerification -PbrowserControl
# Once the report ends with ALL PASS, close the test game and test bridge:
python3 bridge/office_fixtures.py cleanup
```

For the focused wheel/CDP check, run `./scripts/mc client -PbrowserVerification -PbrowserControl`.
Once its home page opens, run `scripts/browser list`, then
`scripts/browser eval TARGET_ID "document.documentElement.dataset.officeControl='verified'"`.
The focused report is `.runtime/browser-verification.txt`.

Results: `.runtime/office-verification.txt`. Screenshots:
`run-office-test/screenshots/`. The fixture scripts close only their recorded
temporary workspaces. Existing agent sessions are not used for these tests.

## Quick start

```sh
./scripts/mc setup       # Install local JDK 25 (safe to rerun)
./scripts/mc doctor      # Check prerequisites
./scripts/mc build       # Build build/libs/minecraft-mods-0.1.0.jar
./scripts/mc client      # Launch a separate development game
```

The helper works from any current directory. The first build needs internet access
to download Gradle, Minecraft, Fabric, and their dependencies. Client launch also
downloads game assets. No global Gradle or Maven installation is needed.

Use `./scripts/mc gradle <task>` for other Gradle tasks and
`./scripts/mc sources` to generate Minecraft sources for IDE navigation.
Prefer the helper over invoking `gradlew` directly: it selects JDK 25 and keeps
Gradle's user cache in `.gradle-user-home/`.

## Pinned versions

| Component | Version |
| --- | --- |
| Minecraft Java | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Fabric Loom | 1.18.2 |
| Gradle wrapper | 9.7.1 |
| Local JDK | Eclipse Temurin 25.0.4.1+1 |
| MCEF Modern | 0.3.3+mc26.3.jcef146.0.10 |
| Embedded Chromium | 146.0.7680.179 |

Minecraft 26.3 was chosen because it is already installed on this machine and
listed as stable by Fabric. The project uses Java 25, matching Minecraft's runtime
requirement. Versions are in `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`,
and `scripts/setup.sh`; upgrades should be deliberate and tested together.

The setup script installs a SHA-256-verified Linux x86_64 JDK in `.tools/jdk-25/`.
It can also use an existing JDK 25 through `JAVA_HOME` or Arch's standard JDK 25
path. System Java and shell startup files are unchanged. Other platforms need an
installed JDK 25 and `JAVA_HOME`; the automatic JDK download is Linux x86_64 only.
The Gradle distribution also has a pinned SHA-256 checksum.

## Development

- Common/server-safe Java: `src/main/java/`.
- Client-only Java: `src/client/java/`.
- Mod metadata and future assets/data: `src/main/resources/`.
- Development client saves and logs: `run/`.
- Development server saves and logs: `run-server/`.
- Built mod: `build/libs/minecraft-mods-0.1.0.jar` (the sources JAR is for reading code).

The development client uses its own game directory. Existing launcher instances,
mods, saves, and account settings are untouched. To test the packaged JAR later,
use a separate Fabric 26.3 launcher instance with the matching Fabric API.

`./scripts/mc server` starts a dedicated development server. Its first launch may
stop to create `run-server/eula.txt`; review the linked Minecraft EULA and accept
it yourself before restarting. The scripts do not accept it automatically.

VS Code is installed. This folder includes Java/Gradle extension recommendations
and build/client/doctor tasks under **Terminal → Run Task**. The extensions are
optional for command-line work. For Java project import, set the IDE's project
JDK and Gradle JVM to `/home/bill/code/minecraft-mods/.tools/jdk-25`.
The same JDK works if using IntelliJ IDEA instead.

## System assessment — 2 October 2026

| Check | Finding |
| --- | --- |
| OS / CPU | Manjaro Linux, x86_64, 20 logical CPUs |
| Memory | 30 GiB RAM; about 15 GiB available at assessment |
| Storage | About 74 GiB free; partition 92% used |
| Existing Java | Full OpenJDK 26; Java 21 headless runtime only |
| Basic tools | Bash, Git, curl, tar, unzip, Python 3 installed |
| Editor | VS Code installed |
| Launchers | Minecraft Launcher and Prism Launcher installed |
| Existing games | Vanilla 26.3; Prism instance on NeoForge 1.21.1 |
| Graphics | Desktop probe verified hardware-accelerated Intel Iris Xe graphics and OpenGL 4.6; NVIDIA packages also installed |

The system has sufficient resources for the starter environment. Gradle is capped
at four workers and a 2 GiB heap. Build caches, JDK, and game assets take additional
disk space and are ignored by Git. Run `doctor` to reassess; a successful build
does not verify that a game window can open from the desktop. Sandboxed shells may
be unable to access the display even when the desktop graphics check passes.

Environment validation completed: JDK installation and repeat setup, prerequisite
checks, shell syntax and JSON parsing, full mod builds, reuse of Gradle's
configuration cache, packaged metadata inspection, and a live development client
with the terminal screen. A dedicated server has not been tested.

## Project reference

The requested [Agent Office README](https://github.com/AgentSystemLabs/agent-office#readme)
is saved unchanged in `references/agent-office/README.upstream.md`, with its
upstream license and source metadata. The saved file was checked against the
Git blob SHA returned by GitHub. Relative links in that reference belong to the
upstream repository. This is reference material for our next design discussion;
Agent Office has not been installed or integrated.

## Upstream references

- [Fabric development setup](https://docs.fabricmc.net/develop/getting-started/setting-up)
- [Fabric 26.3 template](https://github.com/FabricMC/fabric-example-mod/tree/44465cb0eb83932c72ece5934d32ddfc758802ed)
- [Fabric version metadata](https://meta.fabricmc.net/v2/versions/game)
- [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html)
- [Temurin JDK release](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.4.1%2B1)

The Gradle wrapper files come from the pinned Fabric template commit above;
their embedded upstream license notices are retained. The bundled DejaVu Sans Mono
font's license is in `licenses/DejaVu-LICENSE.txt` and included in the mod JAR.
The authored mob textures can be regenerated with `python3 scripts/generate-agent-textures.py`.
[MCEF Modern](https://github.com/DimasKama/mcef-modern) is a separate dependency
with its own upstream license, not bundled into our mod JAR.
Herdr is a separate executable, not bundled. No license for our own mod code has
been chosen yet.
