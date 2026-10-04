"""Read connector credentials from the environment or the repository's ignored .env.

Values are parsed as literal strings, never evaluated as shell commands, and are
not exported into agent processes or persisted in group/world data.
"""
import os
from pathlib import Path
import shlex

ENV_FILE = Path(__file__).resolve().parents[1] / '.env'
KEYS = {'LINEAR_API_KEY', 'GITHUB_TOKEN', 'GH_TOKEN'}


def credential(name):
    if name not in KEYS:
        raise ValueError('Unsupported connector credential')
    if os.environ.get(name):
        return os.environ[name]
    if not ENV_FILE.exists():
        return None
    value = None
    for line in ENV_FILE.read_text().splitlines():
        line = line.strip()
        if line.startswith('export '):
            line = line[7:].lstrip()
        key, separator, raw = line.partition('=')
        if not separator or key.strip() != name:
            continue
        try:
            parts = shlex.split(raw, comments=True, posix=True)
        except ValueError:
            raise ValueError(f'Invalid quoting for {name} in .env') from None
        if len(parts) > 1:
            raise ValueError(f'Quote the value of {name} in .env')
        value = parts[0] if parts else None
    return value or None
