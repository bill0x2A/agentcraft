"""A passive viewer never steals; explicit focus hands the same live terminal back and forth."""
import json
import time
from server import rpc
from test_office import connect
from test_live import until,text,send
from office_fixtures import close

created=rpc('workspace.create',{'cwd':__import__('os').getcwd(),'label':'Handoff verification','focus':False})
pane=created['root_pane'];fixture={'terminal':pane['terminal_id'],'pane':pane['pane_id'],'workspace':created['workspace']['workspace_id']}
links=[]
def attach(takeover=False):
    c,r=connect(dict(type='attach',terminal=fixture['terminal'],takeover=takeover));links.append((c,r));return c,r
try:
    a,ar=attach();until(ar,lambda f:'minecraft-mods' in text(f))
    send(a,type='paste',text='python3 bridge/terminal_fixture.py');send(a,type='key',key='Enter')
    frame=until(ar,lambda f:text(f).startswith('HERDR TERMINAL LIVE'));pid=text(frame).splitlines()[1].split('  ')[0]
    b,br=attach();line=json.loads(br.readline())
    while line.get('type')!='error':line=json.loads(br.readline())
    assert 'already has an attached client' in line['message']
    b2,br2=attach(True);frame=until(br2,lambda f:pid in text(f));send(b2,type='text',text='HANDOFF')
    until(br2,lambda f:'INPUT=HANDOFF' in text(f))
    a2,ar2=attach(True);frame=until(ar2,lambda f:'INPUT=HANDOFF' in text(f))
    assert pid in text(frame)
    print('PASS passive attach refuses takeover; explicit handoffs retain process and terminal contents')
finally:
    for c,r in links:r.close();c.close()
    close(fixture)
