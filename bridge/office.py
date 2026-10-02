"""Small allow-listed session API for the local Minecraft office."""
from server import ROOT, rpc


def sessions():
    snapshot = rpc('session.snapshot')['snapshot']
    workspaces = {w['workspace_id']: w for w in snapshot['workspaces']}
    result = []
    for pane in snapshot['panes']:
        terminal = pane.get('terminal_id')
        if not terminal:
            continue
        workspace = workspaces.get(pane['workspace_id'], {})
        reported = pane.get('agent_status', 'unknown')
        # Preserve the source state. "Blocked" does not necessarily mean approval.
        status = {'working': 'Working', 'idle': 'Idle', 'done': 'Done',
                  'blocked': 'Needs attention', 'unknown': 'Unknown'}.get(reported, reported)
        if not pane.get('agent'):
            status = 'Shell'
        result.append(dict(terminal=terminal, pane=pane['pane_id'],
                           label=workspace.get('label') or pane['pane_id'],
                           agent=pane.get('agent') or '', status=status,
                           reportedStatus=reported))
    return result


def create(label):
    if not isinstance(label, str) or not label.strip() or len(label) > 48 or any(ord(c) < 32 for c in label):
        raise ValueError('Choose a session name of 1–48 printable characters')
    created = rpc('workspace.create', {'cwd': str(ROOT), 'label': label.strip(), 'focus': False})
    # Creating a session starts only Herdr's normal shell, never an agent command.
    pane = created['root_pane']
    return dict(terminal=pane['terminal_id'], pane=pane['pane_id'], label=label.strip(),
                agent='', status='Shell', reportedStatus='unknown')


def require_terminal(terminal):
    if not isinstance(terminal, str) or not any(s['terminal'] == terminal for s in sessions()):
        raise ValueError('Session unavailable: its original terminal no longer exists')
    return terminal
