"""Start/reuse only our persistent browser host; never touch the user's browser profile."""
import fcntl
import json
from pathlib import Path
import subprocess
import time
import urllib.request
from groups import ROOT


def ensure():
    runtime=ROOT/'.runtime';runtime.mkdir(exist_ok=True)
    with (runtime/'browser-host.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        def ready():
            try:
                config=json.loads((runtime/'browser-host.json').read_text())
                base=f"http://127.0.0.1:{config['port']}/{config['token']}"
                with urllib.request.urlopen(base+'/health',timeout=1) as r:
                    if json.load(r).get('ok'): return base
            except (OSError,ValueError): pass
        base=ready()
        if base:return base
        with (runtime/'browser-host.log').open('ab',buffering=0) as log:
            process=subprocess.Popen(['node',str(ROOT/'browser/host.mjs')],cwd=ROOT,stdout=log,stderr=log,stdin=subprocess.DEVNULL,start_new_session=True)
        for _ in range(80):
            base=ready()
            if base:return base
            if process.poll() is not None:raise ValueError('Browser host failed; see .runtime/browser-host.log')
            time.sleep(.1)
        raise ValueError('Browser host is still starting; try again')
