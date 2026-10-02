#!/usr/bin/env python3
"""Generate original flat-colour UV palettes for our code-defined cuboid models."""
from pathlib import Path
import struct
import zlib

DEST = Path(__file__).resolve().parents[1]/'src/main/resources/assets/modding_workspace/textures/entity'
DEST.mkdir(parents=True, exist_ok=True)


def chunk(kind, data):
    return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)


for character in ('codex','claude'):
    for state in ('resting','working','attention','offline','unknown'):
        eyes={'resting':0x506C75,'working':0x62F2B2,'attention':0xFFD05A,'offline':0x38454F,'unknown':0xA4B5BC}[state]
        colors=([0xE6EFED,0x62C8B4,0x182A34,eyes,0xFFFFFF,0x5A747D,0x8BABB2,0xFFD05A] if character=='codex' else
                [0xD97757,0xF3A37A,0x512D29,eyes,0xFFF0D9,0x9B5743,0xE8B28A,0xFFD05A])
        if state=='offline':
            colors=[((c>>16&255)//2)<<16 | ((c>>8&255)//2)<<8 | (c&255)//2 for c in colors]
        data=bytearray()
        for y in range(128):
            data.append(0)
            for x in range(256):
                c=colors[(y//64)*4+x//64]
                data.extend((c>>16&255,c>>8&255,c&255,255))
        png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',256,128,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(data,9))+chunk(b'IEND',b'')
        (DEST/f'{character}_{state}.png').write_bytes(png)
print('Generated 10 agent texture palettes')

# Solid monitor bezel material, shared by its front/back geometry.
(DEST/'browser-frame.png').write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',1,1,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b'\0\xff\xff\xff\xff'))+chunk(b'IEND',b''))
