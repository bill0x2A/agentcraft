Verified on Linux x86_64 / Intel Iris Xe, 2 October 2026.

Office live test: custom Codex/Claude entities and Herdr status animations;
actual terminal input and PID preservation; home anchoring; legacy villager and
version-1 save migration; unavailable sessions; Create + link; mob removal;
Chromium world texture, mouse/keyboard input, live page retention, local HTTP
navigation, saved monitor URL restoration, monitor removal.

Browser focused test: real Minecraft wheel input scrolls Chromium; the external
scripts/browser CDP helper changes the same browser DOM and visible heading.
Endpoint checked with ss: only 127.0.0.1:9222 (explicit -PbrowserControl).
The normal browser path needs no TCP control endpoint.

Bridge regressions: test_live.py 7 PASS, test_office.py 4 PASS.
Final Gradle build: successful. Screenshots are original runtime captures.

Tests used disposable shell sessions with synthetic pane.report_agent metadata,
not new AI agents. The original user Codex pane w1:p1 remained the sole Herdr
workspace after cleanup, with the original terminal ID and native agent session.

MCEF emits native VAAPI/shared-memory and AWT diagnostics on this machine;
page rendering, click/type/scroll, navigation, DOM queries and close/reopen were
nevertheless verified. Video acceleration/codecs and arbitrary sites were not
validated. Wheel input uses CEF's in-process Input.dispatchMouseEvent because
the upstream AWT wheel path did not scroll here. No desktop Chrome mirroring.
