#!/usr/bin/env python3
"""Integration check against a temporary pane in the isolated Herdr server."""
import json
import socket
import threading
import time
from server import ROOT, rpc, handle


def connect(terminal):
    client, bridge = socket.socketpair()
    client.settimeout(10)
    threading.Thread(target=handle, args=(bridge, terminal), daemon=True).start()
    return client, client.makefile('rb')


def send(client, **event):
    client.sendall((json.dumps(event)+'\n').encode())


def text(frame):
    if frame.get('type') != 'frame':
        return ''
    cells, width = frame['cells'], frame['width']
    return '\n'.join(''.join(c[0] for c in cells[y:y+width]) for y in range(0,len(cells),width))


def until(reader, predicate):
    deadline = time.monotonic()+12
    while time.monotonic() < deadline:
        try:
            line = reader.readline()
        except TimeoutError:
            print('LAST FRAME:', text(event) if 'event' in locals() else 'none', flush=True)
            raise
        if not line:
            raise AssertionError('Bridge closed unexpectedly')
        event = json.loads(line)
        if event['type'] == 'error':
            raise AssertionError(event)
        if event['type'] == 'frame' and predicate(event):
            return event
    raise AssertionError('Expected frame not received')


def main():
    created = rpc('workspace.create', {'cwd':str(ROOT),'label':'Terminal verification','focus':False})
    pane = created['root_pane']
    client, reader = connect(pane['terminal_id'])
    try:
        until(reader, lambda f: 'minecraft-mods' in text(f))
        send(client,type='paste',text='python3 bridge/terminal_fixture.py')
        send(client,type='key',key='Enter')
        frame = until(reader, lambda f: text(f).startswith('HERDR TERMINAL LIVE'))
        assert any(c[0]=='C' and c[1] in (3,0x01000002) for c in frame['cells']), 'Green cells lost'
        assert 'Unicode: λ café ┌─┐' in text(frame)
        print('PASS real alternate screen, colour, Unicode, cursor', flush=True)
        pidline = text(frame).splitlines()[1].split('  ')[0]
        send(client,type='text',text='hello')
        until(reader, lambda f: 'INPUT=hello' in text(f))
        send(client,type='key',key='Up')
        until(reader, lambda f: r'INPUT=\x1b[A' in text(f))
        print('PASS text and arrow-key input', flush=True)
        send(client,type='paste',text='hello\nworld')
        until(reader, lambda f: r'\x1b[200~hello\nworld\x1b[201~' in text(f))
        print('PASS bracketed multiline paste', flush=True)
        send(client,type='mouse',action='down',button=0,col=5,row=5)
        until(reader, lambda f: r'INPUT=\x1b[<0;6;6M' in text(f))
        print('PASS terminal mouse reporting', flush=True)
        send(client,type='resize',cols=92,rows=26)
        until(reader, lambda f: f['width']==92 and f['height']==26 and 'SIZE=92x26' in text(f))
        print('PASS live PTY resize', flush=True)
        reader.close(); client.close()
        time.sleep(.2)
        client, reader = connect(pane['terminal_id'])
        frame = until(reader, lambda f: text(f).startswith('HERDR TERMINAL LIVE'))
        assert pidline in text(frame), 'Process changed during reconnect'
        print('PASS detach/reconnect preserves running process and alternate screen', flush=True)
        send(client,type='key',key='c',modifiers=2)
        until(reader, lambda f: not text(f).startswith('HERDR TERMINAL LIVE'))
        print('PASS Ctrl+C and alternate-screen restoration', flush=True)
    finally:
        reader.close(); client.close()
        rpc('workspace.close', {'workspace_id':created['workspace']['workspace_id']})


if __name__ == '__main__':
    main()
