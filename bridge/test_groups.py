"""Behavioral checks for shared groups, optional trackers and scoped imports."""
import concurrent.futures
import json
import contextlib
import io
import os
from pathlib import Path
import runpy
import tempfile
import unittest
from unittest.mock import patch
import groups
import issues

class GroupsTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.path=Path(self.temp.name)/'groups.json'
        self.g=self.call('create',name='Team',cwd=self.temp.name)['group']
    def call(self,op,**args):return groups.dispatch(op,args,self.path)
    def test_quick_group_and_restart(self):
        self.assertEqual(self.g['sources'],[]);self.assertEqual(self.g['leader'],'')
        self.call('task',group='Team',title='Investigate the build')
        snapshot=groups.dispatch('snapshot',{},self.path)
        self.assertEqual(snapshot['tasks'][0]['title'],'Investigate the build')
        self.assertEqual(snapshot['groups'][0]['cwd'],self.temp.name)
    def test_subgroups_reject_cycles_and_rollback(self):
        child=self.call('create',name='Subteam',parent='Team')['group']
        self.assertEqual(child['cwd'],self.temp.name)
        with self.assertRaises(ValueError):self.call('configure',group='Team',parent=child['id'],name='Should roll back')
        self.assertEqual(self.call('snapshot')['groups'][0]['name'],'Team')
    def test_membership_multiple_teams_leader_and_task(self):
        self.call('create',name='Other',cwd=self.temp.name)
        with patch.object(groups,'live_session',return_value={}):
            self.call('join',group='Team',terminal='term1',role='reviewer')
            self.call('join',group='Other',terminal='term1')
        self.call('leader',group='Team',terminal='term1')
        t=self.call('task',group='Team',title='Work')['task']
        self.call('update-task',group='Team',task=t['id'],assignee='term1',status='working')
        with self.assertRaises(ValueError):self.call('update-task',group='Other',task=t['id'],status='done')
        self.call('leave',group='Team',terminal='term1')
        state=self.call('snapshot');self.assertEqual(state['groups'][0]['leader'],'');self.assertEqual(state['tasks'][0]['assignee'],'')
        self.assertEqual(state['groups'][1]['members'][0]['terminal'],'term1')
    def test_concurrent_writes_do_not_lose_tasks(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            list(pool.map(lambda n:self.call('task',group='Team',title=f'Task {n}'),range(40)))
        self.assertEqual(len(self.call('snapshot')['tasks']),40)
    def test_import_deduplicates_and_preserves_local_work(self):
        source=self.call('source',group='Team',provider='github',scope='owner/repo')['group']['sources'][0]
        issue={'key':'github:owner/repo:1','title':'Fix it','url':'https://github.com/owner/repo/issues/1','provider':'github'}
        with patch.object(issues,'fetch',return_value=issue):
            t=self.call('import',group='Team',source=source['id'],issue='1')['task']
            self.call('update-task',group='Team',task=t['id'],status='done')
            again=self.call('import',group='Team',source=source['id'],issue='1')['task']
            self.assertEqual(t['id'],again['id']);self.assertEqual(again['status'],'done')
        self.call('disconnect',group='Team',source=source['id'])
        self.assertEqual(len(self.call('snapshot')['tasks']),1)
        with self.assertRaises(ValueError):self.call('issues',group='Team',source=source['id'])
    def test_linear_scope_enforced(self):
        source={'id':'s','scope':'projectA'}
        with self.assertRaises(ValueError):issues.linear_issue(source,{'project':{'id':'projectB'}})
        with self.assertRaises(ValueError):issues.linear_issue(source,None)
    def test_no_prompt_sent_to_shell(self):
        with patch.object(groups,'live_session',return_value={'agent':''}),patch('server.rpc') as rpc:
            self.call('join',group='Team',terminal='shell')
            with self.assertRaises(ValueError):self.call('send',group='Team',terminal='shell',message='Delete everything')
            rpc.assert_not_called()
    def test_blocked_agent_message_does_not_answer_attention_prompt(self):
        with patch.object(groups,'live_session',return_value={'agent':'codex','reportedStatus':'blocked'}),patch('server.rpc') as rpc:
            self.call('join',group='Team',terminal='blocked')
            with self.assertRaises(ValueError):self.call('send',group='Team',terminal='blocked',message='yes')
            rpc.assert_not_called()
    def test_launch_uses_directory_and_shell_only_by_default(self):
        with patch('server.rpc',return_value={'root_pane':{'terminal_id':'new','pane_id':'p1'}}) as rpc:
            result=self.call('launch',group='Team')
            self.assertEqual(result['terminal'],'new');self.assertEqual(rpc.call_count,1)
            self.assertEqual(rpc.call_args.args[1]['cwd'],self.temp.name)
            self.assertIn('OFFICE_GROUP',rpc.call_args.args[1]['env'])
    def test_invalid_launch_never_creates_a_shell(self):
        with patch('server.rpc') as rpc:
            with self.assertRaises(ValueError):self.call('launch',group='Team',role=' ')
            folder=Path(self.temp.name)/'removed-project';folder.mkdir()
            self.call('configure',group='Team',cwd=str(folder));folder.rmdir()
            with self.assertRaises(ValueError):self.call('launch',group='Team')
            rpc.assert_not_called()
        self.assertEqual(self.call('snapshot')['groups'][0]['members'],[])
    def cli(self,*args):
        output=io.StringIO()
        with patch('sys.argv',['workspace','--json',*args]),patch.object(groups,'STATE',self.path),contextlib.redirect_stdout(output):
            runpy.run_path(str(groups.ROOT/'scripts/workspace'),run_name='__main__')
        return json.loads(output.getvalue())
    def test_cli_subgroup_inherits_parent_directory(self):
        child=self.cli('create','Child','--parent','Team')['group']
        self.assertEqual(child['cwd'],self.temp.name)
        root=self.cli('create','Root')['group']
        self.assertEqual(root['cwd'],str(Path.cwd()))
    def test_cli_linear_project_pagination(self):
        with patch.object(issues,'projects',return_value={'items':[],'cursor':''}) as projects:
            self.cli('projects','--cursor','next-project-page')
            projects.assert_called_once_with('next-project-page')
    def test_linear_reads_and_imports_only_selected_project(self):
        project='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
        source=self.call('source',group='Team',provider='linear',scope=project)['group']['sources'][0]
        item={'id':'issue-uuid','identifier':'ENG-123','title':'Fix build','description':'Details',
              'project':{'id':project},'state':{'name':'Todo'},'url':'https://linear.app/example/issue/ENG-123'}
        captured=[]
        def respond(req,**kwargs):
            payload=json.loads(req.data);captured.append(payload)
            query=payload['query']
            self.assertNotIn('mutation',query)
            if 'projects(' in query:
                data={'projects':{'nodes':[{'id':project,'name':'Selected project'}],'pageInfo':{'hasNextPage':True,'endCursor':'page2'}}}
            elif 'issues(' in query:
                self.assertEqual(payload['variables']['project'],project)
                self.assertEqual(payload['variables']['after'],'issue-page2')
                data={'issues':{'nodes':[item],'pageInfo':{'hasNextPage':False,'endCursor':None}}}
            else:
                self.assertEqual(payload['variables']['id'],'ENG-123')
                data={'issue':item}
            return io.BytesIO(json.dumps({'data':data}).encode())
        with patch.dict(os.environ,{'LINEAR_API_KEY':'test-only'}),patch('urllib.request.urlopen',side_effect=respond):
            self.assertEqual(self.call('projects')['projects']['cursor'],'page2')
            listed=self.call('issues',group='Team',source=source['id'],cursor='issue-page2')['issues']
            self.assertEqual(listed['items'][0]['identifier'],'ENG-123')
            imported=self.call('import',group='Team',source=source['id'],issue='ENG-123')['task']
        self.assertEqual(imported['issue']['source'],source['id'])
        self.assertEqual(imported['description'],'Details')
        self.assertEqual(len(captured),3)
    def test_corrupt_or_future_state_not_replaced(self):
        self.path.write_text('{broken')
        with self.assertRaises(ValueError):self.call('snapshot')
        self.assertEqual(self.path.read_text(),'{broken')
        self.path.write_text('{"version":99}')
        with self.assertRaises(ValueError):self.call('snapshot')
        self.assertEqual(self.path.read_text(),'{"version":99}')
    def test_github_pr_excluded_and_identifiers_validated(self):
        source={'provider':'github','scope':'owner/repo','id':'s'}
        with patch.object(issues,'request',return_value=[{'pull_request':{}}]):self.assertEqual(issues.browse(source)['items'],[])
        with self.assertRaises(ValueError):issues.fetch(source,'../secrets')
        with self.assertRaises(ValueError):issues.source_config('github','https://example.com/repo')

if __name__=='__main__':unittest.main()
