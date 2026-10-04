"""Persistent, tracker-optional work groups shared by Minecraft and terminal clients.

Herdr owns processes. This module owns membership, roles and work, never a scheduler.
All state changes use a process lock and atomic replacement, including CLI operations.
"""
import contextlib
import fcntl
import json
import os
from pathlib import Path
import shlex
import tempfile
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
STATE = Path(os.environ.get('OFFICE_GROUPS_FILE', ROOT / '.runtime' / 'groups.json'))
STATES = ('todo', 'working', 'blocked', 'review', 'done')


def identity(prefix):
    return prefix + '_' + uuid.uuid4().hex[:12]


def text(value, label, limit=200, empty=False):
    if not isinstance(value, str) or len(value.strip()) > limit or '\x00' in value:
        raise ValueError(f'Invalid {label} (maximum {limit} characters)')
    value = value.strip()
    if not empty and not value:
        raise ValueError(f'Enter {label}')
    return value


@contextlib.contextmanager
def transaction(path=None):
    path = Path(path or STATE)
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with path.with_suffix('.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        data = json.loads(path.read_text()) if path.exists() else {'version': 1, 'revision': 0, 'groups': [], 'tasks': []}
        if data.get('version') != 1:
            raise ValueError('Unsupported group data version; refusing to overwrite it')
        before = json.dumps(data, sort_keys=True)
        yield data
        if json.dumps(data, sort_keys=True) != before or not path.exists():
            data['revision'] += 1
            fd, name = tempfile.mkstemp(dir=path.parent, prefix='.groups-', suffix='.tmp')
            try:
                with os.fdopen(fd, 'w') as out:
                    json.dump(data, out, indent=2); out.write('\n'); out.flush(); os.fsync(out.fileno())
                os.replace(name, path)
            finally:
                if os.path.exists(name): os.unlink(name)


def group(data, key):
    found = [g for g in data['groups'] if g['id'] == key or g['name'] == key]
    if len(found) != 1:
        raise ValueError('Group not found or name ambiguous; use its group ID')
    return found[0]


def task(data, key):
    return next((t for t in data['tasks'] if t['id'] == key), None) or (_ for _ in ()).throw(ValueError('Task not found'))


def live_session(terminal):
    from office import sessions
    return next((s for s in sessions() if s['terminal'] == terminal), None) or (_ for _ in ()).throw(ValueError('Session unavailable; link a current Herdr session'))


def directory(value):
    path = Path(text(value, 'working directory', 4096)).expanduser().resolve()
    if not path.is_dir(): raise ValueError('Working directory does not exist')
    return str(path)


def dispatch(op, args, path=None):
    if op == 'projects':
        from issues import projects
        return {'projects': projects(args.get('cursor'))}
    # Provider I/O happens outside the file lock. Import rechecks source membership below.
    if op in ('issues', 'import'):
        from issues import browse, fetch
        with transaction(path) as data:
            g = group(data, args['group'])
            source = next((s.copy() for s in g['sources'] if s['id'] == args['source']), None)
            gid = g['id']
        if source is None: raise ValueError('Source is not connected to this group')
        if op == 'issues': return {'issues': browse(source, args.get('cursor'))}
        issue = fetch(source, text(args['issue'], 'issue identifier', 160))
        with transaction(path) as data:
            g = group(data, gid)
            if source not in g['sources']: raise ValueError('Source changed; select it again')
            existing = next((t for t in data['tasks'] if t['group'] == gid and t.get('issue', {}).get('key') == issue['key']), None)
            if existing: return {'task': existing}
            t = new_task(gid, issue['title'], issue.get('description', ''))
            t['issue'] = issue; data['tasks'].append(t)
            return {'task': t}
    if op == 'browser':
        with transaction(path) as data: gid = group(data, args['group'])['id']
        from browser_host import ensure
        return {'url': ensure() + '/view/' + gid}
    if op == 'launch':
        from server import rpc
        import shutil
        kind = args.get('agent', 'shell')
        if kind not in ('shell', 'claude', 'codex'): raise ValueError('Choose shell, claude or codex')
        if kind != 'shell' and not shutil.which(kind): raise ValueError(f'{kind} is not installed')
        with transaction(path) as data:
            g = group(data, args['group']); gid, cwd = g['id'], directory(g['cwd'])
            name = text(args.get('name') or kind, 'session name', 48)
            role = text(args.get('role', 'member'), 'role', 80)
        pane = rpc('workspace.create', {'cwd': cwd, 'label': name, 'focus': False, 'env': {
            'OFFICE_GROUP': gid, 'OFFICE_COMMAND': str(ROOT / 'scripts/workspace'),
            'PATH': str(ROOT / 'scripts') + os.pathsep + os.environ.get('PATH', '')}})['root_pane']
        # Save the shell before launching so even launch failure leaves it discoverable.
        with transaction(path) as data:
            g = group(data, gid)
            g['members'].append({'terminal': pane['terminal_id'], 'role': role})
        if kind != 'shell':
            rpc('pane.send_text', {'pane_id': pane['pane_id'], 'text': shlex.quote(kind) + ' ' + shlex.quote(f'Read your working group context by running {ROOT / "scripts/workspace"} context {gid}. Use that CLI to coordinate with group members and subgroups when asked. Issue tracking and worktrees are optional. Read the context, then wait for my instructions.')})
            rpc('pane.send_keys', {'pane_id': pane['pane_id'], 'keys': ['Enter']})
        return {'terminal': pane['terminal_id'], 'group': gid}
    if op == 'send':
        terminal = text(args['terminal'], 'terminal ID')
        with transaction(path) as data:
            g = group(data, args['group'])
            if terminal not in [m['terminal'] for m in g['members']]: raise ValueError('Session is not in this group')
            message = text(args['message'], 'message', 16000)
        session = live_session(terminal)
        if session['agent'] not in ('claude', 'codex'):
            raise ValueError('Start Claude or Codex before sending a message; no prompt was sent to the shell')
        if session.get('reportedStatus') == 'blocked':
            raise ValueError('This agent needs attention. Open its terminal before sending another message.')
        from server import rpc
        rpc('pane.send_text', {'pane_id': session['pane'], 'text': message})
        time.sleep(.15)
        rpc('pane.send_keys', {'pane_id': session['pane'], 'keys': ['Enter']})
        return {'sent': True}
    if op == 'join': live_session(args['terminal'])
    with transaction(path) as data:
        if op == 'snapshot': return json.loads(json.dumps(data))
        if op == 'create':
            name = text(args['name'], 'group name', 80)
            if any(g['name'] == name for g in data['groups']): raise ValueError('A group with that name already exists')
            parent = group(data, args['parent']) if args.get('parent') else None
            cwd = directory(args.get('cwd') or (parent['cwd'] if parent else str(ROOT)))
            g = {'id': identity('group'), 'name': name, 'cwd': cwd, 'parent': parent['id'] if parent else '',
                 'leader': '', 'members': [], 'sources': [], 'instructions': '', 'worktree': 'agent-choice'}
            data['groups'].append(g); return {'group': g}
        g = group(data, args['group'])
        if op == 'configure':
            if 'cwd' in args: g['cwd'] = directory(args['cwd'])
            if 'name' in args:
                name = text(args['name'], 'group name', 80)
                if any(other['id'] != g['id'] and other['name'] == name for other in data['groups']): raise ValueError('Group name already exists')
                g['name'] = name
            if 'instructions' in args: g['instructions'] = text(args['instructions'], 'instructions', 16000, True)
            if 'parent' in args:
                parent = group(data, args['parent']) if args['parent'] else None
                ancestor = parent
                while ancestor:
                    if ancestor['id'] == g['id']: raise ValueError('A subgroup cannot contain itself')
                    ancestor = group(data, ancestor['parent']) if ancestor['parent'] else None
                g['parent'] = parent['id'] if parent else ''
            if 'worktree' in args:
                if args['worktree'] not in ('agent-choice', 'shared', 'isolated'): raise ValueError('Choose agent-choice, shared or isolated')
                g['worktree'] = args['worktree']
        elif op == 'join':
            terminal = args['terminal']; role = text(args.get('role', 'member'), 'role', 80)
            m = next((m for m in g['members'] if m['terminal'] == terminal), None)
            if m: m['role'] = role
            else: g['members'].append({'terminal': terminal, 'role': role})
        elif op == 'leave':
            g['members'] = [m for m in g['members'] if m['terminal'] != args['terminal']]
            if g['leader'] == args['terminal']: g['leader'] = ''
            for t in data['tasks']:
                if t['group'] == g['id'] and t['assignee'] == args['terminal']: t['assignee'] = ''
        elif op == 'leader':
            terminal = args.get('terminal', '')
            if terminal and terminal not in [m['terminal'] for m in g['members']]: raise ValueError('Add the leader to this group first')
            g['leader'] = terminal
        elif op == 'task':
            t = new_task(g['id'], text(args['title'], 'task title', 300), text(args.get('description', ''), 'description', 32000, True))
            data['tasks'].append(t); return {'task': t}
        elif op == 'update-task':
            t = task(data, args['task'])
            if t['group'] != g['id']: raise ValueError('Task belongs to another group')
            if 'status' in args:
                if args['status'] not in STATES: raise ValueError('Invalid task status')
                t['status'] = args['status']
            if 'assignee' in args:
                if args['assignee'] and args['assignee'] not in [m['terminal'] for m in g['members']]: raise ValueError('Assignee is not in this group')
                t['assignee'] = args['assignee']
            return {'task': t}
        elif op == 'source':
            from issues import source_config
            source = source_config(args['provider'], args['scope'])
            source['name'] = text(args.get('name', source['scope']), 'source name', 160)
            old = next((s for s in g['sources'] if s['provider'] == source['provider'] and s['scope'] == source['scope']), None)
            if not old: g['sources'].append(source)
            elif 'name' in args: old['name'] = source['name']
        elif op == 'disconnect': g['sources'] = [s for s in g['sources'] if s['id'] != args['source']]
        else: raise ValueError('Unknown group operation')
        return {'group': g}


def new_task(gid, title, description):
    return {'id': identity('task'), 'group': gid, 'title': title, 'description': description,
            'status': 'todo', 'assignee': '', 'created': time.time()}
