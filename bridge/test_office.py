#!/usr/bin/env python3
"""Real two-session isolation, control API, reconnection, and ended-session checks."""
import json
import socket
import threading
import time
from server import dispatch, rpc
from test_live import until, text, send


def connect(request):
    client, peer = socket.socketpair()
    client.settimeout(12)
    threading.Thread(target=dispatch, args=(peer, None), daemon=True).start()
    send(client, **request)
    return client, client.makefile('rb')


def control(**request):
    client, reader = connect(request)
    try:
        return json.loads(reader.readline())
    finally:
        reader.close(); client.close()


def main():
    created, connections = [], []
    try:
        for label in ['Office isolation A', 'Office isolation B']:
            response = control(type='create', label=label)
            assert response['type'] == 'session', response
            session = response['session']; created.append(session)
            client, reader = connect(dict(type='attach', terminal=session['terminal']))
            connections.append((client, reader))
            until(reader, lambda f: 'minecraft-mods' in text(f))
            send(client, type='paste', text='python3 bridge/terminal_fixture.py')
            send(client, type='key', key='Enter')
            frame = until(reader, lambda f: text(f).startswith('HERDR TERMINAL LIVE'))
            session['pidline'] = text(frame).splitlines()[1].split('  ')[0]
        assert created[0]['terminal'] != created[1]['terminal']
        assert created[0]['pidline'] != created[1]['pidline']
        catalog = control(type='sessions')['sessions']
        assert all(any(s['terminal'] == c['terminal'] and s['label'] == c['label'] for s in catalog) for c in created)
        print('PASS create/list two independent real shell sessions', flush=True)
        for i, (client, reader) in enumerate(connections):
            marker = f'ISOLATED_{i}'
            send(client, type='text', text=marker)
            frame = until(reader, lambda f: f'INPUT={marker}' in text(f))
            assert created[i]['pidline'] in text(frame)
            assert f'ISOLATED_{1-i}' not in text(frame)
        # Query authoritative terminal buffers too, independent of each viewer.
        for i, session in enumerate(created):
            visible = rpc('pane.read', {'pane_id':session['pane'], 'source':'visible','format':'text'})['read']['text']
            assert f'ISOLATED_{i}' in visible and f'ISOLATED_{1-i}' not in visible
        print('PASS simultaneous input/output stays in the selected terminal', flush=True)
        for client, reader in connections:
            reader.close(); client.close()
        connections.clear(); time.sleep(.2)
        for i, session in enumerate(created):
            client, reader = connect(dict(type='attach', terminal=session['terminal']))
            connections.append((client, reader))
            frame = until(reader, lambda f: f'ISOLATED_{i}' in text(f))
            assert session['pidline'] in text(frame)
        print('PASS both live processes and terminal contents survive reconnect', flush=True)
        session = created.pop()
        pane = rpc('pane.get', {'pane_id':session['pane']})['pane']
        rpc('workspace.close', {'workspace_id':pane['workspace_id']})
        assert all(s['terminal'] != session['terminal'] for s in control(type='sessions')['sessions'])
        rejected = control(type='attach', terminal=session['terminal'])
        assert rejected['type'] == 'error' and 'unavailable' in rejected['message'], rejected
        print('PASS ended terminal disappears and stale attachment is rejected', flush=True)
    finally:
        for client, reader in connections:
            reader.close(); client.close()
        for session in created:
            pane = rpc('pane.get', {'pane_id':session['pane']})['pane']
            rpc('workspace.close', {'workspace_id':pane['workspace_id']})


if __name__ == '__main__':
    main()
