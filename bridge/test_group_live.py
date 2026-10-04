"""Check group launch/context against real Herdr using one disposable shell, never an AI agent."""
import json
import shlex
import tempfile
import time
from pathlib import Path

import groups
from office_fixtures import close
from server import rpc


def main():
    with tempfile.TemporaryDirectory(prefix='office-group-live-') as folder:
        root = Path(folder)
        state = root / 'groups.json'
        report = root / 'result.json'
        group = groups.dispatch('create', {'name': 'Live group fixture', 'cwd': folder}, state)['group']
        launched = groups.dispatch('launch', {'group': group['id'], 'name': 'Group launch verification'}, state)
        pane = next(p for p in rpc('session.snapshot')['snapshot']['panes']
                    if p.get('terminal_id') == launched['terminal'])
        fixture = {'terminal': launched['terminal'], 'pane': pane['pane_id'], 'workspace': pane['workspace_id']}
        try:
            # Point only this fixture's context read at its temporary state.
            script = root / 'inspect.py'
            script.write_text('''import json, os, shutil, subprocess
from pathlib import Path
context=json.loads(subprocess.check_output(['workspace','--json','context'],text=True))
Path('result.json').write_text(json.dumps({'cwd':os.getcwd(),'group':os.environ.get('OFFICE_GROUP'),
    'command':os.environ.get('OFFICE_COMMAND'),'workspace':shutil.which('workspace'),'context':context}))
''')
            command = 'OFFICE_GROUPS_FILE=' + shlex.quote(str(state)) + ' python3 ' + shlex.quote(str(script))
            rpc('pane.send_text', {'pane_id': pane['pane_id'], 'text': command})
            rpc('pane.send_keys', {'pane_id': pane['pane_id'], 'keys': ['Enter']})
            deadline = time.monotonic() + 15
            while not report.exists() and time.monotonic() < deadline:
                time.sleep(.1)
            assert report.exists(), 'Launched shell did not run workspace context'
            result = json.loads(report.read_text())
            assert result['cwd'] == folder
            assert result['group'] == group['id']
            assert result['workspace'] == str(groups.ROOT / 'scripts/workspace')
            assert result['command'] == result['workspace']
            assert result['context']['group']['members'][0]['terminal'] == launched['terminal']
            assert not result['context']['group']['sources']
            print('PASS real group shell launch: working directory, CLI on PATH, group identity, shared context, no tracker')
        finally:
            close(fixture)


if __name__ == '__main__':
    main()
