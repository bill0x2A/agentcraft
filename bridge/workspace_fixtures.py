"""Disposable fixtures for the working-group integration test, with no AI processes."""
import json
import sys
from pathlib import Path
import groups
import browser_host
from server import ROOT,RUNTIME,rpc
from office_fixtures import close

FILE=RUNTIME/'workspace-fixture.json'
DATA=RUNTIME/'groups-verification.json'
if sys.argv[1:] == ['cleanup']:
    if FILE.exists():
        for fixture in json.loads(FILE.read_text())['sessions']:close(fixture)
        FILE.unlink()
    DATA.unlink(missing_ok=True)
    raise SystemExit()
if FILE.exists():raise SystemExit('Existing workspace fixtures; clean up before preparing')
DATA.unlink(missing_ok=True)
state=groups.dispatch('create',{'name':'Studio team','cwd':str(ROOT)},DATA)['group']
fixtures=[]
try:
    for kind in ('Claude','Codex'):
        result=rpc('workspace.create',{'cwd':str(ROOT),'label':'Group verification '+kind,'focus':False})
        pane=result['root_pane'];fixture={'terminal':pane['terminal_id'],'pane':pane['pane_id'],'workspace':result['workspace']['workspace_id']};fixtures.append(fixture)
        groups.dispatch('join',{'group':state['id'],'terminal':fixture['terminal'],'role':'researcher' if kind=='Claude' else 'builder'},DATA)
    groups.dispatch('leader',{'group':state['id'],'terminal':fixtures[0]['terminal']},DATA)
    groups.dispatch('task',{'group':state['id'],'title':'Explore the studio','description':'A freeform task. No issue tracker or worktree required.'},DATA)
    groups.dispatch('task',{'group':state['id'],'title':'Try our terminals and group browser'},DATA)
    groups.dispatch('create',{'name':'Research subgroup','parent':state['id']},DATA)
    base=browser_host.ensure()
    fixture={'group':state['id'],'terminal':fixtures[0]['terminal'],'sessions':fixtures,'browser':base+'/view/'+state['id']}
    FILE.write_text(json.dumps(fixture))
    print('Prepared two disposable shells and a tracker-free group')
except Exception:
    for fixture in fixtures:close(fixture)
    raise
