#!/usr/bin/env python3
"""Interactive VT fixture for testing a real terminal's screen and input paths."""
import os
import select
import signal
import sys
import termios
import tty

original = termios.tcgetattr(0)
last = 'ready'
count = 0


def draw(*_):
    cols, rows = os.get_terminal_size()
    sys.stdout.write('\033[H\033[2J\033[1;36mHERDR TERMINAL LIVE\033[0m\r\n'
                     f'PID={os.getpid()}  SIZE={cols}x{rows}  EVENTS={count}\r\n'
                     '\033[32mCOLOUR_GREEN\033[0m  \033[41;97m BACKGROUND_RED \033[0m\r\n'
                     'Unicode: λ café ┌─┐\r\n'
                     f'INPUT={last}\r\n'
                     'Press keys, click, scroll or resize. Ctrl+C exits.\r\n')
    sys.stdout.flush()


try:
    tty.setraw(0)
    sys.stdout.write('\033[?1049h\033[?1000h\033[?1006h\033[?2004h')
    signal.signal(signal.SIGWINCH, draw)
    draw()
    while True:
        data = os.read(0, 4096)
        if not data or b'\x03' in data:
            break
        count += 1
        last = repr(data)[2:-1]
        draw()
finally:
    sys.stdout.write('\033[?2004l\033[?1006l\033[?1000l\033[?1049l')
    sys.stdout.flush()
    termios.tcsetattr(0, termios.TCSADRAIN, original)
