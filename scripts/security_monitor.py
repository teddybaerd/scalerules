#!/usr/bin/env python3
"""Read-only source/dependency monitoring; optional isolated GitHub issue publication.

No third-party Python packages, downloaded executable code or signing credentials.
OSV matches package versions, not Android reachability or exploitability.
"""
# Copyright (c) 2026 ScaleRules contributors
# SPDX-License-Identifier: BSD-3-Clause
import argparse
import datetime as dt
import hashlib
import html
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FEED = 'https://tailscale.com/security-bulletins/index.xml'
API = 'https://api.github.com'
OSV = 'https://api.osv.dev/v1/querybatch'
MARKER = '<!-- scalerules-security-monitor:v1 -->'
TITLE = 'ScaleRules: Sicherheits- und Upstream-Monitor'
ID = re.compile(r'[A-Za-z0-9][A-Za-z0-9._-]{0,100}')


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise RuntimeError('Unexpected HTTP redirect; check the configured source URL')


def request(url, data=None, token=None, method=None):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != 'https' or parsed.hostname not in {
        'api.github.com', 'tailscale.com', 'api.osv.dev'
    } or parsed.username or parsed.password:
        raise ValueError('Unapproved endpoint')
    headers = {'User-Agent': 'ScaleRules-security-monitor', 'Accept': 'application/json'}
    if token:
        if parsed.hostname != 'api.github.com':
            raise ValueError('GitHub credentials must never leave api.github.com')
        headers['Authorization'] = 'Bearer ' + token
    payload = None if data is None else json.dumps(data).encode()
    if payload is not None:
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(url, payload, headers, method=method)
    with urllib.request.build_opener(NoRedirect()).open(req, timeout=40) as response:
        raw = response.read(8 * 1024 * 1024 + 1)
    if len(raw) > 8 * 1024 * 1024:
        raise ValueError('Source response exceeds limit')
    return raw


def get_json(url, **kwargs):
    return json.loads(request(url, **kwargs))


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def stable_version(tag):
    match = re.fullmatch(r'v?(\d+)\.(\d+)\.(\d+)(?:-t[0-9a-f]+-g[0-9a-f]+)?', tag)
    if not match:
        return None
    version = tuple(int(x) for x in match.groups())
    # Tailscale uses odd minor numbers for unstable builds.
    return version if version[1] % 2 == 0 else None


def latest_release(releases):
    candidates = [r for r in releases if not r.get('draft') and not r.get('prerelease')
                  and stable_version(r.get('tag_name', '')) is not None]
    if not candidates:
        raise ValueError('No stable upstream release found')
    result = max(candidates, key=lambda r: (stable_version(r['tag_name']), r.get('published_at', '')))
    return {'tag': result['tag_name'], 'url': result['html_url']}


def read_go_modules(text):
    """Parse the checked-in require set; replacements must be explicitly supported first."""
    modules = []
    in_require = False
    go_version = None
    for original in text.splitlines():
        line = original.split('//', 1)[0].strip()
        if not line:
            continue
        if re.match(r'replace(?:\s|\()', line):
            raise ValueError('go.mod replacements require explicit inventory support')
        if line.startswith('go '):
            go_version = line.split()[1]
        if line == 'require (':
            in_require = True
            continue
        if in_require and line == ')':
            in_require = False
            continue
        if line.startswith('require '):
            line = line[len('require '):]
        elif not in_require:
            continue
        parts = line.split()
        if len(parts) != 2 or not parts[1].startswith('v'):
            raise ValueError('Unsupported Go require declaration')
        modules.append({'ecosystem': 'Go', 'name': parts[0], 'version': parts[1]})
    if not go_version or not modules or in_require:
        raise ValueError('Incomplete Go inventory')
    modules.append({'ecosystem': 'Go', 'name': 'stdlib', 'version': go_version})
    return modules, go_version


def parse_bulletins(raw):
    if b'<!DOCTYPE' in raw.upper():
        raise ValueError('Unexpected XML document type')
    root = ET.fromstring(raw)
    result = []
    seen = set()
    for item in root.findall('./channel/item'):
        title = item.findtext('title', '').strip()
        link = item.findtext('link', '').strip()
        parsed = urllib.parse.urlsplit(link)
        if not re.fullmatch(r'TS-\d{4}-\d{3}', title) or parsed.scheme != 'https' or parsed.netloc != 'tailscale.com':
            raise ValueError('Unexpected bulletin identity or link')
        if title in seen:
            raise ValueError('Duplicate bulletin identity')
        seen.add(title)
        description = html.unescape(re.sub('<[^>]+>', ' ', item.findtext('description', '')))
        description = ' '.join(description.split())
        result.append({'id': title, 'url': link, 'digest': fingerprint(description)})
    if not result:
        raise ValueError('No security bulletins found; source format may have changed')
    return sorted(result, key=lambda entry: entry['id'], reverse=True)


def osv_matches(packages, fetch=get_json):
    """Follow every batch page. Any truncated/malformed response fails the whole scan."""
    findings = {}
    for start in range(0, len(packages), 100):
        batch = packages[start:start + 100]
        pending = [(p, None) for p in batch]
        seen_tokens = set()
        for _ in range(100):
            queries = []
            for package, token in pending:
                query = {'package': {'ecosystem': package['ecosystem'], 'name': package['name']},
                         'version': package['version']}
                if token:
                    query['page_token'] = token
                queries.append(query)
            response = fetch(OSV, data={'queries': queries})
            results = response.get('results')
            if not isinstance(results, list) or len(results) != len(pending):
                raise ValueError('Incomplete OSV batch response')
            next_pending = []
            for (package, _), result in zip(pending, results):
                if not isinstance(result, dict) or result.get('error'):
                    raise ValueError('OSV query failed')
                for vuln in result.get('vulns', []):
                    identifier = vuln.get('id', '')
                    if not ID.fullmatch(identifier):
                        raise ValueError('Invalid OSV identifier')
                    key = (package['ecosystem'], package['name'], package['version'], identifier)
                    findings[key] = {**package, 'id': identifier, 'modified': vuln.get('modified', '')}
                token = result.get('next_page_token')
                if token:
                    key = (package['ecosystem'], package['name'], package['version'], token)
                    if key in seen_tokens:
                        raise ValueError('Repeated OSV page token')
                    seen_tokens.add(key)
                    next_pending.append((package, token))
            if not next_pending:
                break
            pending = next_pending
        else:
            raise ValueError('OSV pagination limit exceeded')
    return sorted(findings.values(), key=lambda f: (f['ecosystem'], f['name'], f['version'], f['id']))


def collect(inventory_path):
    inventory = json.loads(inventory_path.read_text())
    maven = inventory.get('packages')
    if inventory.get('schema') != 1 or not isinstance(maven, list) or not maven:
        raise ValueError('Missing resolved Android dependency inventory')
    for package in maven:
        if package.get('ecosystem') != 'Maven' or not re.fullmatch(r'[^\s:]+:[^\s:]+', package.get('name', '')) or not package.get('version'):
            raise ValueError('Invalid Maven package entry')
    modules, go_version = read_go_modules((ROOT / 'go.mod').read_text())
    core = next(p['version'] for p in modules if p['name'] == 'tailscale.com')
    provenance = (ROOT / 'docs/UPSTREAM.md').read_text()
    base = re.search(r'^Release: (.+)$', provenance, re.M)
    if not base or stable_version(base[1]) is None:
        raise ValueError('Missing Android upstream baseline')
    token = os.environ.get('GH_READ_TOKEN')
    releases = {}
    for name, repo, embedded in [
        ('Android', 'tailscale/tailscale-android', base[1]),
        ('VPN-Kern', 'tailscale/tailscale', core),
    ]:
        items = get_json(f'{API}/repos/{repo}/releases?per_page=100', token=token)
        if not isinstance(items, list):
            raise ValueError('Invalid release list')
        latest = latest_release(items)
        releases[name] = {**latest, 'embedded': embedded,
                          'newer': (stable_version(latest['tag']) > stable_version(embedded) or
                                    (stable_version(latest['tag']) == stable_version(embedded) and
                                     latest['tag'] != embedded))}
    package_map = {}
    for package in modules + maven:
        key = (package['ecosystem'], package['name'], package['version'])
        if package['ecosystem'] == 'Go':
            scopes = ['go-stdlib' if package['name'] == 'stdlib' else 'go-requirement']
        else:
            scopes = package.get('scopes', ['unknown'])
        if key not in package_map:
            package_map[key] = {**package, 'scopes': []}
        package_map[key]['scopes'] = sorted(set(package_map[key]['scopes'] + scopes))
    packages = [package_map[key] for key in sorted(package_map)]
    return {
        'schema': 1,
        'checked_at': dt.datetime.now(dt.timezone.utc).isoformat(timespec='seconds'),
        'source_commit': os.environ.get('GITHUB_SHA', 'local'),
        'releases': releases,
        'bulletins': parse_bulletins(request(FEED)),
        'dependencies': {'go_version': go_version,
                         'go_toolchain_rev': (ROOT / 'go.toolchain.rev').read_text().strip(),
                         'package_count': len(packages), 'packages': packages,
                         'matches': osv_matches(packages)},
    }


def state(report):
    return {key: fingerprint(report[key]) for key in ('releases', 'bulletins', 'dependencies')}


def render(report):
    lines = [MARKER, '# ScaleRules: Sicherheits- und Upstream-Monitor', '',
             'Letzte vollständig erfolgreiche Prüfung (UTC): ' + report['checked_at'], '',
             '## Upstream-Versionen', '']
    for name, release in report['releases'].items():
        status = '**Neuere stabile Version verfügbar – prüfen.**' if release['newer'] else 'Keine neuere stabile Version gefunden.'
        lines += [f"- {name}: eingebunden `{release['embedded']}`, aktuell [{release['tag']}]({release['url']}). {status}"]
    lines += ['', '## Offizielle Sicherheitsmeldungen', '',
              'Feed-Beobachtung, keine automatische Aussage zur Betroffenheit von Android. '
              'Auch Änderungen älterer Meldungen werden erkannt. Aktuelle Meldungen können im eingebundenen Kern bereits behoben sein.', '']
    lines += [f"- [{b['id']}]({b['url']})" for b in report['bulletins']]
    deps = report['dependencies']
    lines += ['', '## Abhängigkeitsprüfung (OSV)', '',
              f"Geprüft: {deps['package_count']} Paketversionen aus Go, Go-Standardbibliothek, Android-Release-Laufzeit und Gradle-Build-Abhängigkeiten.",
              f"Go laut go.mod: `{deps['go_version']}`; Tailscale-Go-Revision: `{deps['go_toolchain_rev']}`.", '',
              '**Treffer sind Prüfkandidaten anhand der Paketversion, kein Nachweis einer ausnutzbaren Android-Lücke.** '
              'Go-Build-Tags, Erreichbarkeit und Fixes des Tailscale-Go-Forks müssen separat bewertet werden.', '']
    if not deps['matches']:
        lines += ['Keine passenden bekannten OSV-Einträge gefunden. Das ist kein Sicherheitsnachweis.']
    for finding in deps['matches']:
        name = f"{finding['ecosystem']} {finding['name']}@{finding['version']}"
        scope_names = {'android-release': 'Android-Release-Laufzeit', 'gradle-build': 'Build-Werkzeuge',
                       'go-stdlib': 'Go-Standardbibliothek', 'go-requirement': 'Go-Anforderung; Android-Erreichbarkeit offen'}
        scope = ', '.join(scope_names.get(s, s) for s in finding.get('scopes', ['nicht zugeordnet']))
        lines += [f"- `{name}` ({scope}): [{finding['id']}](https://osv.dev/vulnerability/{finding['id']})"]
    lines += ['', '## Nächster Schritt', '',
              'Neue Meldungen nach Plattform und Konfiguration bewerten. Native Änderungen über eine geprüfte stabile '
              'Tailscale-Android-Version samt go.mod, go.sum und go.toolchain.rev übernehmen; danach Build, '
              'Gerätetest und signiertes ScaleRules-Release. Keine automatischen Merges oder Installationen.', '',
              'Betrieb und Grenzen: [docs/SECURITY-MONITORING.md](../blob/main/docs/SECURITY-MONITORING.md).']
    body = '\n'.join(lines)
    if len(body) > 57000:
        body = body[:57000] + '\n\nBericht gekürzt; vollständige Daten im Workflow-Artefakt.\n'
    body += '\n\n<!-- scalerules-monitor-state:' + json.dumps(state(report), sort_keys=True) + ' -->\n'
    return body


def publish(report, call=get_json):
    if report.get('schema') != 1:
        raise ValueError('Invalid report schema')
    repo = os.environ.get('GITHUB_REPOSITORY', '')
    if not re.fullmatch(r'[\w.-]+/[\w.-]+', repo):
        raise ValueError('Invalid GITHUB_REPOSITORY')
    token = os.environ['GH_TOKEN']
    base = f'{API}/repos/{repo}'
    existing = None
    # Include closed issues: acknowledgement must not cause duplicate daily issues.
    for page in range(1, 101):
        items = call(f'{base}/issues?state=all&per_page=100&page={page}', token=token)
        for issue in items:
            if MARKER in (issue.get('body') or '') and issue.get('user', {}).get('login') == 'github-actions[bot]' and not issue.get('pull_request'):
                if existing is not None:
                    raise ValueError('Multiple monitor issues found; resolve duplicates manually')
                existing = issue
        if len(items) < 100:
            break
    else:
        raise ValueError('Issue pagination limit exceeded')
    body = render(report).replace('(../blob/main/', f'(https://github.com/{repo}/blob/main/')
    if existing is None:
        payload = {'title': TITLE, 'body': body}
        maintainer = os.environ.get('SCALERULES_MAINTAINER')
        if maintainer:
            payload['assignees'] = [maintainer]
        issue = call(f'{base}/issues', data=payload, token=token, method='POST')
        return issue['html_url']
    previous = re.search(r'<!-- scalerules-monitor-state:(\{[^\n]+\}) -->', existing['body'])
    old = json.loads(previous[1]) if previous else {}
    new = state(report)
    changed = [key for key in new if old.get(key) != new[key]]
    if changed:
        names = {'releases': 'Upstream-Versionen', 'bulletins': 'Sicherheitsmeldungen', 'dependencies': 'Abhängigkeiten / OSV-Befunde'}
        note = 'Neue Änderungen bei: ' + ', '.join(names[k] for k in changed) + '. Der aktualisierte Bericht steht oben im Issue. Bitte Betroffenheit prüfen.'
        call(f"{base}/issues/{existing['number']}/comments", data={'body': note}, token=token, method='POST')
    payload = {'body': body}
    if changed and existing.get('state') == 'closed':
        payload['state'] = 'open'
    call(f"{base}/issues/{existing['number']}", data=payload, token=token, method='PATCH')
    return existing['html_url']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inventory', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--publish', type=Path)
    args = parser.parse_args()
    if args.publish:
        print(publish(json.loads(args.publish.read_text())))
    else:
        if not args.inventory or not args.output:
            parser.error('--inventory and --output are required for a scan')
        report = collect(args.inventory)
        args.output.mkdir(parents=True, exist_ok=True)
        (args.output / 'report.json').write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
        (args.output / 'report.md').write_text(render(report))
        summary = os.environ.get('GITHUB_STEP_SUMMARY')
        if summary:
            with open(summary, 'a') as output:
                output.write(render(report))
        print(f"Complete scan: {report['dependencies']['package_count']} packages, {len(report['dependencies']['matches'])} OSV matches")


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Do not expose HTTP authentication headers or response bodies.
        print(f'Monitor failed ({type(error).__name__}): {error}', file=sys.stderr)
        sys.exit(1)
