#!/usr/bin/env python3
"""Local, same-user terminal bridge. No external dependencies or network port."""
import argparse
import json
import os
from pathlib import Path
import socket
import threading
import protocol

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / '.runtime'


def rpc(method, params=None):
    with socket.socket(socket.AF_UNIX) as s:
        s.settimeout(8)
        s.connect(str(RUNTIME / 'herdr.sock'))
        s.sendall((json.dumps(dict(id='minecraft', method=method, params=params or {}))+'\n').encode())
        response = json.loads(s.makefile('rb').readline(2 * 1024 * 1024))
    if 'error' in response:
        raise RuntimeError(str(response['error']))
    return response['result']


def handle(client, terminal):
    upstream = socket.socket(socket.AF_UNIX)
    reader = None
    lock = threading.Lock()

    def emit(data):
        with lock:
            client.sendall((json.dumps(data, ensure_ascii=False, separators=(',', ':'))+'\n').encode())

    try:
        upstream.settimeout(8)
        upstream.connect(str(RUNTIME / 'herdr-client.sock'))
        protocol.send(upstream, protocol.hello(100, 32))
        welcome = protocol.receive(upstream)
        if not welcome or welcome.get('version') != protocol.PROTOCOL or welcome.get('error'):
            raise RuntimeError(f'Expected Herdr protocol 20: {welcome}')
        upstream.settimeout(None)
        protocol.send(upstream, protocol.attach(terminal))
        emit(dict(type='connected', terminal=terminal))

        def read_frames():
            try:
                while True:
                    event = protocol.receive(upstream)
                    if event:
                        emit(event)
            except (OSError, EOFError, ValueError) as e:
                try:
                    emit(dict(type='error', message=str(e)))
                except OSError:
                    pass
            finally:
                try:
                    client.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass

        worker = threading.Thread(target=read_frames, daemon=True)
        worker.start()
        reader = client.makefile('rb')
        while True:
            line = reader.readline(65537)
            if not line:
                break
            if len(line) > 65536:
                raise ValueError('Input message exceeds 64 KiB')
            protocol.send(upstream, protocol.input_message(json.loads(line)))
    except (OSError, EOFError, ValueError, RuntimeError, KeyError) as e:
        print(f'Client: {e}', flush=True)
        try:
            emit(dict(type='error', message=str(e)))
        except OSError:
            pass
    finally:
        # Detaching this viewer must never close its pane or the Herdr server.
        try:
            protocol.send(upstream, protocol.uint(4))
            upstream.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        upstream.close()
        if reader is not None:
            reader.close()
        client.close()


def dispatch(client, default_terminal):
    """One initial request selects a control operation or an explicit terminal."""
    from office import sessions, create, require_terminal
    try:
        client.settimeout(8)
        # Unbuffered: do not swallow keyboard messages queued after the handshake.
        with client.makefile('rb', buffering=0) as reader:
            line = reader.readline(4097)
        if len(line) > 4096:
            raise ValueError('Office request too large')
        request = json.loads(line)
        kind = request['type']
        if kind == 'attach':
            terminal = require_terminal(request.get('terminal') or default_terminal)
            client.settimeout(None)
            handle(client, terminal)
            return
        if kind == 'sessions':
            response = dict(type='sessions', sessions=sessions())
        elif kind == 'create':
            response = dict(type='session', session=create(request.get('label')))
        else:
            raise ValueError('Unknown office operation')
        client.sendall((json.dumps(response)+'\n').encode())
    except (OSError, ValueError, RuntimeError, KeyError, TypeError) as e:
        try:
            client.sendall((json.dumps(dict(type='error', message=str(e)))+'\n').encode())
        except OSError:
            pass
    finally:
        client.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--terminal', help='Existing terminal ID; defaults to saved prototype pane')
    parser.add_argument('--socket', type=Path, default=RUNTIME/'terminal.sock', help='Local bridge socket path')
    args = parser.parse_args()
    terminal = args.terminal or json.loads((RUNTIME/'pane.json').read_text())['terminal_id']
    RUNTIME.mkdir(mode=0o700, exist_ok=True)
    path = args.socket.resolve()
    if path.exists():
        with socket.socket(socket.AF_UNIX) as probe:
            try:
                probe.connect(str(path))
            except ConnectionRefusedError:
                path.unlink()
            else:
                raise SystemExit('Bridge already running')
    with socket.socket(socket.AF_UNIX) as listener:
        listener.bind(str(path))
        path.chmod(0o600)
        listener.listen(4)
        print(f'Minecraft terminal bridge: {path} -> {terminal}', flush=True)
        try:
            while True:
                client, _ = listener.accept()
                threading.Thread(target=dispatch, args=(client, terminal), daemon=True).start()
        finally:
            path.unlink(missing_ok=True)


if __name__ == '__main__':
    main()
