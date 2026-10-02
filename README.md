# Minecraft modding workspace

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

The terminal opens from the title screen or a world. Run your usual CLI inside
it, such as `codex`. CLI login and permissions remain that tool's responsibility.
**F8 closes the terminal view without killing the session.** Closing Minecraft
or restarting the bridge also leaves Herdr and its processes running.

| Control | Action |
| --- | --- |
| F7 (in a private single-player world) | Open the office session picker |
| Right-click a linked mob | Open its specific session |
| F8 | Open/close terminal view |
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

The orange crab and white-and-teal robot have original cuboid models and textures.
They stay at saved homes beside your builds. Herdr's state updates their pose,
animated limbs, eyes and nameplate roughly every two seconds:

| Herdr state | Mob behavior |
| --- | --- |
| Shell, idle, done | Resting: gentle motion, relaxed limbs, dim eyes |
| working | Working: animated hands/claws; robot types at a small keyboard |
| blocked | Needs attention: amber eyes, raised/waving limbs, visible `!` |
| unknown | Neutral pose and Unknown label |
| Bridge offline / terminal ended | Slumped, darkened mob with explicit offline/unavailable label |

“Needs attention” does not distinguish a prompt from an approval. “Resting” is
reported activity, not an independent assessment of task completion. Old villager
bindings migrate in place to custom mobs with the same UUID and terminal link.

Ordinary damage and wandering are disabled. Removing a mob does not kill its
process. Removed mobs retain their binding; automatic replacement/reassignment
and custom furniture remain future work.

Mob UUIDs, stable terminal IDs, labels, dimensions, and home positions are saved
atomically in each world's `data/agent-office.json`. The map survives world
reloads. Terminal processes are owned by Herdr, so leaving the view/world or
Minecraft does not end them. If Herdr restarts and gives a terminal a new ID, the
old link remains unavailable; it never silently targets another session.

Minecraft owns entities and interaction. The local bridge lists/creates sessions
and routes each attachment to its explicit terminal ID. Herdr owns the processes
and terminal emulation. Office controls are disabled on remote or LAN-published
worlds. Multiplayer and custom furniture remain separate milestones.

When upgrading from the original terminal-only prototype, close the old game and
restart the foreground bridge before launching this build. Restarting the bridge
preserves Herdr sessions; `scripts/terminal stop` would end them.

## Browsers in the world

The development client includes **MCEF Modern 0.3.3+mc26.3.jcef146.0.10** directly.
WebDisplays is not required. Chromium natives download automatically on first
browser use; progress appears in the focused browser view. This was exercised on
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
