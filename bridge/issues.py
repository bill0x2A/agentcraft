"""Read-only issue connectors. Sources are scoped per group; importing never starts work.
Tokens are read from the environment, ignored .env or GitHub CLI, never saved in worlds.
"""
import json
import re
import subprocess
import uuid
import urllib.request
import urllib.error
from groups import identity
from credentials import credential


def source_config(provider, scope):
    scope = scope.strip()
    if provider == 'github':
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9-]*/[A-Za-z0-9_.-]+', scope) or scope.rsplit('/', 1)[-1] in ('.', '..'): raise ValueError('GitHub scope must be owner/repository')
        scope=scope.lower()
    elif provider == 'linear':
        try: scope=str(uuid.UUID(scope))
        except ValueError: raise ValueError('Linear scope must be a project UUID; use workspace projects to find it') from None
    else: raise ValueError('Choose linear or github')
    return {'id': identity('source'), 'provider': provider, 'scope': scope}


def request(provider, suffix='', query=None, variables=None):
    if provider == 'github':
        token = credential('GITHUB_TOKEN') or credential('GH_TOKEN')
        if not token:
            try: token = subprocess.check_output(['gh', 'auth', 'token'], stderr=subprocess.DEVNULL, text=True, timeout=5).strip()
            except (OSError, subprocess.SubprocessError): token = None
        headers = {'Accept': 'application/vnd.github+json', 'User-Agent': 'Agent-Office'}
        if token: headers['Authorization'] = 'Bearer ' + token
        req = urllib.request.Request('https://api.github.com/repos/' + suffix, headers=headers)
    else:
        token = credential('LINEAR_API_KEY')
        if not token: raise ValueError('Set LINEAR_API_KEY in the repository .env or the environment that starts the office bridge')
        req = urllib.request.Request('https://api.linear.app/graphql', data=json.dumps({'query': query, 'variables': variables or {}}).encode(),
            headers={'Content-Type': 'application/json', 'Authorization': token})
    try:
        with urllib.request.urlopen(req, timeout=8) as response: result = json.load(response)
    except urllib.error.HTTPError as e: raise ValueError(f'{provider} returned HTTP {e.code}; check authentication and project access') from None
    except (urllib.error.URLError, TimeoutError): raise ValueError(f'{provider} is unavailable; existing tasks are unchanged') from None
    if isinstance(result, dict) and result.get('errors'): raise ValueError('Linear query failed; check project ID and access')
    return result


def github_issue(source, issue):
    if 'pull_request' in issue: raise ValueError('Choose an issue rather than a pull request')
    return {'key': f"github:{source['scope'].lower()}:{issue['number']}", 'identifier': '#' + str(issue['number']),
            'title': issue['title'], 'description': issue.get('body') or '', 'url': issue['html_url'],
            'provider': 'github', 'source': source['id'], 'externalStatus': issue['state']}


def linear_issue(source, issue):
    if not issue or (issue.get('project') or {}).get('id') != source['scope']: raise ValueError('Issue is outside the connected Linear project')
    return {'key': 'linear:' + issue['id'], 'identifier': issue['identifier'], 'title': issue['title'],
            'description': issue.get('description') or '', 'url': issue['url'], 'provider': 'linear',
            'source': source['id'], 'externalStatus': issue['state']['name']}


FIELDS = 'id identifier title description url state { name } project { id }'


def browse(source, cursor=None):
    if source['provider'] == 'github':
        page = int(cursor or 1)
        if page < 1 or page > 10000: raise ValueError('Invalid page')
        rows = request('github', source['scope'] + f'/issues?state=open&per_page=30&page={page}')
        return {'items': [github_issue(source, i) for i in rows if 'pull_request' not in i], 'cursor': str(page+1) if len(rows) == 30 else ''}
    result = request('linear', query='query($project: ID!, $after: String) { issues(first: 30, after: $after, filter: {project: {id: {eq: $project}}}) { nodes { '+FIELDS+' } pageInfo { hasNextPage endCursor } } }', variables={'project': source['scope'], 'after': cursor or None})['data']['issues']
    return {'items': [linear_issue(source, i) for i in result['nodes']], 'cursor': result['pageInfo']['endCursor'] if result['pageInfo']['hasNextPage'] else ''}


def fetch(source, identifier):
    if source['provider'] == 'github':
        number = identifier.lstrip('#')
        if not number.isdigit(): raise ValueError('Use the GitHub issue number')
        return github_issue(source, request('github', source['scope'] + '/issues/' + number))
    if not re.fullmatch(r'[A-Za-z0-9-]+', identifier): raise ValueError('Use the Linear issue identifier')
    result = request('linear', query='query($id: String!) { issue(id: $id) { '+FIELDS+' } }', variables={'id': identifier})
    return linear_issue(source, result['data']['issue'])


def projects(cursor=None):
    result=request('linear',query='query($after: String) { projects(first: 50, after: $after) { nodes { id name } pageInfo { hasNextPage endCursor } } }',variables={'after':cursor or None})['data']['projects']
    return {'items':result['nodes'],'cursor':result['pageInfo']['endCursor'] if result['pageInfo']['hasNextPage'] else ''}
