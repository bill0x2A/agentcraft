#!/usr/bin/env python3
"""Start/reuse the workspace-owned Herdr server and its first terminal."""
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time
from server import ROOT, RUNTIME, rpc


def environment():
    env = os.environ.copy()
    # Herdr's state paths are XDG-based. These overrides affect this child only.
    env['XDG_CONFIG_HOME'] = str(RUNTIME / 'config')
    env['XDG_STATE_HOME'] = str(RUNTIME / 'state')
    env['HERDR_SOCKET_PATH'] = str(RUNTIME / 'herdr.sock')
    env.pop('HERDR_SESSION', None)
    return env


def start():
    executable = shutil.which('herdr')
    if not executable:
        raise SystemExit('Install Herdr 0.8.2 first (https://herdr.dev).')
    version = subprocess.check_output([executable, '--version'], text=True).strip()
    if version != 'herdr 0.8.2':
        raise SystemExit(f'This prototype supports herdr 0.8.2; found {version}.')
    RUNTIME.mkdir(mode=0o700, exist_ok=True)
    try:
        rpc('ping')
    except (OSError, RuntimeError):
        log = open(RUNTIME/'herdr.log', 'ab', buffering=0)
        process = subprocess.Popen([executable, 'server'], cwd=ROOT, env=environment(),
                                   stdin=subprocess.DEVNULL, stdout=log, stderr=log,
                                   start_new_session=True)
        for _ in range(100):
            if process.poll() is not None:
                raise SystemExit('Herdr failed to start; see .runtime/herdr.log')
            try:
                rpc('ping')
                break
            except (OSError, RuntimeError):
                time.sleep(.1)
        else:
            raise SystemExit('Herdr not ready; see .runtime/herdr.log')
    saved = RUNTIME/'pane.json'
    if saved.exists():
        pane = json.loads(saved.read_text())
        try:
            current = rpc('pane.get', {'pane_id':pane['pane_id']})['pane']
            if current['terminal_id'] == pane['terminal_id']:
                print(f"Reusing terminal {pane['terminal_id']}")
                return pane
        except (RuntimeError, KeyError):
            pass
    result = rpc('workspace.create', {'cwd':str(ROOT), 'label':'Minecraft terminal', 'focus':False})
    pane = result['root_pane']
    saved.write_text(json.dumps(pane, indent=2)+'\n')
    print(f"Created terminal {pane['terminal_id']} in {pane['pane_id']}")
    return pane


if __name__ == '__main__':
    if sys.argv[1:] == ['stop']:
        rpc('server.stop')
        print('Stopped workspace Herdr server and its terminal processes.')
    else:
        start()
