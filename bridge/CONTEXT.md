# Bridge

The local Python process that lists and creates sessions and attaches viewers to terminals. It translates between the JSON lines of the mod and the private protocol of Herdr. It uses the shared terms in [../CONTEXT.md](../CONTEXT.md).

## Language

**Bridge socket**:
The Unix socket on which the bridge accepts connections. The default path is `.runtime/terminal.sock`.
_Avoid_: port, endpoint

**Office request**:
The first JSON line of a connection. Its type is `sessions`, `create`, `attach`, `workspace`, or `capabilities`.
_Avoid_: command, handshake

**Control operation**:
An office request of type `sessions` or `create`. The bridge sends one response and closes the connection.
_Avoid_: RPC, query

**Attachment**:
A connection after an `attach` office request. It carries frames to the viewer and input events to Herdr.
_Avoid_: stream, session

**Input event**:
One JSON line from a viewer of type `resize`, `text`, `paste`, `key`, `mouse`, or `scroll`.
_Avoid_: message, keystroke

**Frame**:
One complete cell grid of a terminal: width, height, cells, cursor, and hyperlinks.
_Avoid_: screen, snapshot, update

**Herdr protocol**:
The private binary protocol of Herdr, version 20, on `.runtime/herdr-client.sock`. The bridge rejects all other versions.
_Avoid_: wire format

**Herdr RPC**:
The JSON-line API of Herdr on `.runtime/herdr.sock`. The bridge uses it to list, create, and close.
_Avoid_: control API

**Isolated Herdr server**:
The Herdr server that `bridge/runtime.py` starts with its configuration and state in the runtime directory. It is not the usual Herdr server of the user.
_Avoid_: workspace server, local Herdr

**Herdr workspace**:
The Herdr container that holds panes. A `create` office request makes one Herdr workspace with one root pane.
_Avoid_: workspace, project

**Pane**:
A Herdr pane. A pane with a terminal becomes one session. Herdr can use a pane ID again for a different terminal.
_Avoid_: window, tab

**Default terminal**:
The terminal that `.runtime/pane.json` records. An `attach` office request with no terminal ID uses it.
_Avoid_: prototype pane, first terminal

**Fixture**:
A disposable shell session with synthetic agent data that a test creates and closes.
_Avoid_: mock, test session

**Live test**:
A test that runs against the isolated Herdr server: `test_live.py` or `test_office.py`.
_Avoid_: unit test, verification

## Relationships

- A **Herdr workspace** holds one or more **Panes**. A **Pane** has zero or one terminal.
- The bridge lists each **Pane** that has a terminal as one session.
- The label of a session is the label of its **Herdr workspace**, or its pane ID if there is no label.
- An **Office request** starts either one **Control operation** or one **Attachment**.
- An **Attachment** serves exactly one terminal ID. The bridge refuses a terminal ID that is not in the session list.
- An explicit focused **Attachment** may request takeover. A passive display never requests takeover. Neither closes a **Pane**.
- A **Fixture** closes only if its pane ID still holds its recorded terminal ID.

## Example dialogue

> **Dev:** "The mod sends `attach` with no terminal ID. What does the viewer see?"
> **Domain expert:** "The **Default terminal** from `.runtime/pane.json`."
> **Dev:** "And if the **Attachment** drops in the middle of a **Frame**?"
> **Domain expert:** "The bridge detaches. The **Pane** and its process continue in the **Isolated Herdr server**."

## Flagged ambiguities

- "server" names `bridge/server.py` (the bridge) and the Herdr server. Say "bridge" or **Isolated Herdr server**.
- "client" in `bridge/server.py` is a viewer connection. It is not the Minecraft client, and it is not `herdr-client.sock`.
- "session" in **Herdr RPC** names (`session.snapshot`) is the full Herdr state. It is not a session of the office.
- "verification" belongs to the mod. A bridge test is a **Live test**.

Working groups are persisted in groups.json with a file lock and atomic replacement.
The CLI and workspace office requests call the same group operations. Issue provider
reads happen outside the lock; imports recheck the source before saving.
