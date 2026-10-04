"""The small subset of Herdr 0.8.2 / protocol 20 used by terminal clients.

Wire reference: herdrdev/herdr, v0.8.2, src/protocol/wire.rs.
Bincode 2 standard: little endian, unsigned variable integers, UTF-8 strings.
This adapter deliberately rejects other protocol versions.
"""
import struct

PROTOCOL = 20
MAX_FRAME = 2 * 1024 * 1024


def uint(n):
    if n < 0:
        raise ValueError('Negative unsigned integer')
    if n < 251:
        return bytes([n])
    if n <= 65535:
        return b'\xfb' + struct.pack('<H', n)
    if n <= 0xffffffff:
        return b'\xfc' + struct.pack('<I', n)
    return b'\xfd' + struct.pack('<Q', n)


def string(s):
    b = s.encode('utf-8')
    return uint(len(b)) + b


class Reader:
    def __init__(self, data):
        self.data, self.pos = data, 0

    def take(self, size):
        if size < 0 or self.pos + size > len(self.data):
            raise ValueError('Truncated Herdr frame')
        b = self.data[self.pos:self.pos + size]
        self.pos += size
        return b

    def u8(self):
        return self.take(1)[0]

    def uint(self):
        n = self.u8()
        if n < 251:
            return n
        fmt = {251: '<H', 252: '<I', 253: '<Q'}.get(n)
        if not fmt:
            raise ValueError('Unsupported Bincode integer')
        return struct.unpack(fmt, self.take(struct.calcsize(fmt)))[0]

    def string(self):
        return self.take(self.uint()).decode('utf-8')

    def optional(self, read):
        tag = self.u8()
        if tag not in (0, 1):
            raise ValueError('Invalid option')
        return read() if tag else None


def decode(data):
    r = Reader(data)
    kind = r.uint()
    if kind == 0:
        result = dict(type='welcome', version=r.uint(), encoding=r.uint(), error=r.optional(r.string))
    elif kind == 1:
        count = r.uint()
        if count > 40000:
            raise ValueError('Terminal grid too large')
        cells = []
        for _ in range(count):
            cells.append([r.string(), r.uint(), r.uint(), r.uint(), r.u8(), r.optional(r.uint)])
        width, height = r.uint(), r.uint()
        if width * height != count:
            raise ValueError('Invalid terminal dimensions')
        cursor = r.optional(lambda: [r.uint(), r.uint(), r.u8(), r.u8()])
        links = [r.string() for _ in range(r.uint())]
        graphics = r.take(r.uint())
        result = dict(type='frame', width=width, height=height, cells=cells, cursor=cursor,
                      hyperlinks=links, unsupportedGraphics=bool(graphics))
    elif kind == 4:
        result = dict(type='error', message=r.optional(r.string) or 'Herdr stopped')
    elif kind == 5:
        result = dict(type='notice', kind=r.uint(), message=r.string(), body=r.optional(r.string))
    else:
        # Clipboard, bells, outer-window controls and Kitty graphics don't mutate
        # our cell grid. They are intentionally not executed by the bridge.
        return None
    if r.pos != len(data):
        raise ValueError('Unexpected fields: Herdr protocol mismatch')
    return result


def hello(cols, rows):
    # Hello: protocol, cols, rows, cell pixel size, SemanticFrame, Server, TerminalAttach.
    return b''.join(uint(x) for x in [0, PROTOCOL, cols, rows, 0, 0, 0, 0, 2])


def attach(terminal, takeover=False):
    return uint(5) + string(terminal) + bytes([bool(takeover)])  # Only a focused, explicit open may take control.


def input_message(event):
    kind = event['type']
    if kind == 'resize':
        cols, rows = int(event['cols']), int(event['rows'])
        if not (20 <= cols <= 240 and 5 <= rows <= 100):
            raise ValueError('Resize outside supported bounds')
        return b''.join(uint(x) for x in [3, cols, rows, 0, 0])
    if kind in ('text', 'paste'):
        data = event['text'].encode('utf-8')
        if kind == 'paste':
            data = b'\x1b[200~' + data + b'\x1b[201~'
        return raw_input(data)
    if kind == 'key':
        key = event['key']
        mods = int(event.get('modifiers', 0)) & 15
        modifier = 1 + (mods & 1) + (2 if mods & 4 else 0) + (4 if mods & 2 else 0)
        if key in ('Up','Down','Right','Left','Home','End'):
            tail = {'Up':'A','Down':'B','Right':'C','Left':'D','Home':'H','End':'F'}[key]
            data = ('\x1b[' + (f'1;{modifier}' if modifier != 1 else '') + tail).encode()
        elif key in ('Insert','Delete','PageUp','PageDown'):
            code = {'Insert':2,'Delete':3,'PageUp':5,'PageDown':6}[key]
            data = (f'\x1b[{code}' + (f';{modifier}' if modifier != 1 else '') + '~').encode()
        elif key.startswith('F') and key[1:].isdigit():
            number = int(key[1:])
            if not 1 <= number <= 12:
                raise ValueError('Only F1–F12 supported')
            if number <= 4:
                data = ('\x1bO' + 'PQRS'[number-1]).encode() if modifier == 1 else f"\x1b[1;{modifier}{'PQRS'[number-1]}".encode()
            else:
                code = [15,17,18,19,20,21,23,24][number-5]
                data = (f'\x1b[{code}' + (f';{modifier}' if modifier != 1 else '') + '~').encode()
        elif key in ('Enter','Backspace','Tab','BackTab','Esc'):
            data = {'Enter':b'\r','Backspace':b'\x7f','Tab':b'\t','BackTab':b'\x1b[Z','Esc':b'\x1b'}[key]
            if key == 'Enter' and modifier != 1:
                data = f'\x1b[13;{modifier}u'.encode()
            elif mods & 4:
                data = b'\x1b' + data
        elif len(key) == 1:
            data = key.encode('utf-8')
            if mods & 2 and 32 <= ord(key) <= 127:
                data = bytes([ord(key) & 31])
            if mods & 4:
                data = b'\x1b' + data
        else:
            raise ValueError('Unsupported key')
        return raw_input(data)
    if kind == 'mouse':
        col, row = int(event['col']), int(event['row'])
        if not (0 <= col < 240 and 0 <= row < 100):
            raise ValueError('Mouse outside grid')
        button = {0:0,1:2,2:1}[int(event['button'])]
        mods = int(event.get('modifiers',0))
        button += (4 if mods & 1 else 0) + (8 if mods & 4 else 0) + (16 if mods & 2 else 0)
        if event['action'] == 'drag': button += 32
        tail = 'm' if event['action'] == 'up' else 'M'
        return raw_input(f'\x1b[<{button};{col+1};{row+1}{tail}'.encode())
    if kind == 'scroll':
        # AttachScroll(Wheel, direction, lines, optional column/row, modifiers).
        return b''.join(uint(x) for x in [6, 0, 0 if event['up'] else 1, 3]) + b'\x00\x00\x00'
    raise ValueError('Unknown input type')


def raw_input(data):
    # Protocol-20 InputEvents routes through global app focus even in direct
    # attach mode. Raw Input is the per-terminal path (headless.rs).
    return uint(1) + uint(len(data)) + data


def send(sock, payload):
    sock.sendall(struct.pack('<I', len(payload)) + payload)


def exact(sock, size):
    data = bytearray()
    while len(data) < size:
        chunk = sock.recv(size - len(data))
        if not chunk:
            raise EOFError('Herdr disconnected')
        data.extend(chunk)
    return bytes(data)


def receive(sock):
    size = struct.unpack('<I', exact(sock, 4))[0]
    if size > MAX_FRAME:
        raise ValueError('Herdr frame exceeds 2 MiB')
    return decode(exact(sock, size))
