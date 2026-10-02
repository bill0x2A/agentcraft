#!/usr/bin/env python3
"""Prepare/clean only the named fixtures used by the opt-in Minecraft client test."""
import json
import sys
import time
from server import ROOT, RUNTIME, rpc
from test_live import connect, send, until, text

FILE = RUNTIME / 'office-fixtures.json'


def close(fixture):
    try:
        pane = rpc('pane.get', {'pane_id': fixture['pane']})['pane']
    except RuntimeError:
        return
    # Never close a reused pane ID belonging to a different terminal.
    if pane['terminal_id'] == fixture['terminal']:
        rpc('workspace.close', {'workspace_id': fixture['workspace']})


def main(action):
    if action == 'watch':
        from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
        from threading import Thread
        class Page(BaseHTTPRequestHandler):
            def do_GET(self):
                body=b'<!doctype html><title>Office browser fixture</title><body style="background:#10202c;color:#6de0ba;font:32px sans-serif;padding:60px"><h1>Office browser fixture</h1><p>Local web app loaded inside Minecraft.</p></body>'
                self.send_response(200);self.send_header('Content-Type','text/html');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
            def log_message(self,*args):pass
        http=ThreadingHTTPServer(('127.0.0.1',0),Page)
        Thread(target=http.serve_forever,daemon=True).start()
        (RUNTIME/'browser-fixture.json').write_text(json.dumps({'url':f'http://127.0.0.1:{http.server_port}/'}))
        ended = False
        reported = set()
        report = RUNTIME/'office-verification.txt'
        deadline = time.monotonic()+600
        while time.monotonic() < deadline:
            value = report.read_text() if report.exists() else ''
            if 'FAIL:' in value: raise SystemExit(value)
            if 'ALL PASS' in value:
                print(value, flush=True); return
            for state in ['working', 'blocked', 'idle']:
                marker = 'AGENTS_REQUEST '+state
                if marker in value and state not in reported:
                    for fixture in json.loads(FILE.read_text()):
                        rpc('pane.report_agent', {'pane_id':fixture['pane'], 'source':'minecraft-verification', 'agent':fixture['agent'], 'state':state})
                    reported.add(state)
            if 'AWAIT_END' in value and not ended:
                main('end-second'); ended = True
            time.sleep(.5)
        raise SystemExit('Timed out waiting for live Minecraft verification')
    if action in ('cleanup', 'end-second'):
        fixtures = json.loads(FILE.read_text())
        for fixture in fixtures[1:] if action == 'end-second' else fixtures:
            close(fixture)
        if action == 'cleanup':
            extra = RUNTIME/'office-ui-created.json'
            if extra.exists():
                terminal = json.loads(extra.read_text())['terminal']
                for pane in rpc('session.snapshot')['snapshot']['panes']:
                    if pane.get('terminal_id') == terminal:
                        rpc('workspace.close', {'workspace_id':pane['workspace_id']})
                extra.unlink()
            FILE.unlink()
        return
    if action not in ('prepare','prepare-agents'): raise SystemExit('Usage: office_fixtures.py prepare|prepare-agents|watch|end-second|cleanup')
    if FILE.exists(): raise SystemExit('Clean up the previous office fixtures first')
    (RUNTIME/'office-verification.txt').unlink(missing_ok=True)
    fixtures = []
    try:
        for label in (('Codex', 'Claude') if action=='prepare-agents' else ('Builder', 'Reviewer')):
            result = rpc('workspace.create', {'cwd':str(ROOT), 'label':label, 'focus':False})
            pane = result['root_pane']
            fixture = dict(terminal=pane['terminal_id'], pane=pane['pane_id'], label=label,
                           workspace=result['workspace']['workspace_id'])
            fixtures.append(fixture)
            client, reader = connect(pane['terminal_id'])
            try:
                until(reader, lambda f: 'minecraft-mods' in text(f))
                send(client, type='paste', text='python3 bridge/terminal_fixture.py')
                send(client, type='key', key='Enter')
                frame = until(reader, lambda f: text(f).startswith('HERDR TERMINAL LIVE'))
                fixture['pidline'] = text(frame).splitlines()[1].split('  ')[0]
                if action=='prepare-agents':
                    fixture['agent']=label.lower()
                    rpc('pane.report_agent', {'pane_id':fixture['pane'], 'source':'minecraft-verification', 'agent':fixture['agent'], 'state':'idle'})
            finally:
                reader.close(); client.close()
        FILE.write_text(json.dumps(fixtures, indent=2)+'\n')
        print('Prepared two isolated terminal fixtures')
    except Exception:
        for fixture in fixtures: close(fixture)
        raise


if __name__ == '__main__': main(sys.argv[1])
