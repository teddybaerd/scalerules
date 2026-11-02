# Copyright (c) 2026 ScaleRules contributors
# SPDX-License-Identifier: BSD-3-Clause
import copy
import importlib.util
import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('monitor', Path(__file__).resolve().parents[1] / 'scripts/security_monitor.py')
m = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(m)


def report():
    return {'schema': 1, 'checked_at': '2026-11-02T06:17:00+00:00', 'source_commit': 'abc',
            'releases': {'Android': {'embedded': '1.104.4-t123-g456', 'tag': '1.104.4-t123-g456', 'url': 'https://github.com/tailscale/tailscale-android/releases/tag/test', 'newer': False}},
            'bulletins': [{'id': 'TS-2026-017', 'url': 'https://tailscale.com/security-bulletins/#ts-2026-017', 'digest': 'abc'}],
            'dependencies': {'go_version': '1.27.3', 'go_toolchain_rev': 'abc', 'package_count': 1, 'packages': [], 'matches': []}}


class InventoryTests(unittest.TestCase):
    def test_go_require_blocks_indirect_and_standard_library(self):
        modules, version = m.read_go_modules('''module example.com/app
 go 1.27.3
 require tailscale.com v1.104.4
 require (
 example.com/indirect v0.0.0-20260101000000-abcdef // indirect
 )
''')
        self.assertEqual(version, '1.27.3')
        self.assertEqual(len(modules), 3)
        self.assertIn({'ecosystem': 'Go', 'name': 'stdlib', 'version': '1.27.3'}, modules)

    def test_replacements_and_incomplete_input_fail_closed(self):
        for text in ['go 1.27.3', 'go 1.27.3\nrequire (\na v1.0.0', 'go 1.27.3\nrequire a v1.0.0\nreplace a => ../a']:
            with self.assertRaises(ValueError):
                m.read_go_modules(text)

    def test_stable_release_numeric_order_and_unstable_filter(self):
        def release(tag, prerelease=False):
            return {'tag_name': tag, 'draft': False, 'prerelease': prerelease, 'html_url': 'https://github.com/example', 'published_at': tag}
        items = [release('v1.104.4'), release('v1.104.10'), release('v1.105.1'), release('v1.106.0', True), release('v1.106.0-rc1')]
        self.assertEqual(m.latest_release(items)['tag'], 'v1.104.10')
        self.assertEqual(m.stable_version('1.104.4-t938213198-gb9d487fe6'), (1, 104, 4))
        with self.assertRaises(ValueError):
            m.latest_release([release('v1.105.1')])

    def test_bulletin_revisions_are_detected_but_whitespace_is_stable(self):
        def feed(text):
            return ('<rss><channel><item><title>TS-2026-017</title><link>https://tailscale.com/security-bulletins/#ts-2026-017</link><description>' + text + '</description></item></channel></rss>').encode()
        a = m.parse_bulletins(feed('first text'))
        self.assertEqual(a, m.parse_bulletins(feed('first  text')))
        self.assertNotEqual(a, m.parse_bulletins(feed('changed text')))
        for raw in [b'<rss><channel/></rss>', b'<!DOCTYPE rss><rss/>', feed('text').replace(b'TS-2026-017', b'unknown')]:
            with self.assertRaises(ValueError):
                m.parse_bulletins(raw)


class OSVTests(unittest.TestCase):
    def test_pagination_preserves_package_mapping(self):
        packages = [{'ecosystem': 'Go', 'name': name, 'version': 'v1.0.0'} for name in ['a', 'b']]
        requests = []
        def fetch(url, **kwargs):
            requests.append(kwargs['data']['queries'])
            if len(requests) == 1:
                return {'results': [{'vulns': [{'id': 'GO-2026-1'}], 'next_page_token': 'more'}, {}]}
            self.assertEqual(kwargs['data']['queries'][0]['page_token'], 'more')
            self.assertEqual(len(kwargs['data']['queries']), 1)
            return {'results': [{'vulns': [{'id': 'GO-2026-2'}]}]}
        result = m.osv_matches(packages, fetch)
        self.assertEqual([x['id'] for x in result], ['GO-2026-1', 'GO-2026-2'])
        self.assertEqual({x['name'] for x in result}, {'a'})

    def test_malformed_truncated_or_looping_pages_fail(self):
        packages = [{'ecosystem': 'Go', 'name': 'a', 'version': 'v1.0.0'}]
        for response in [{'results': []}, {'results': [{'error': 'failed'}]}, {'results': [{'next_page_token': 'same'}]}, {'results': [{'vulns': [{'id': '@unsafe mention'}]}]}]:
            with self.assertRaises(ValueError):
                m.osv_matches(packages, lambda *a, **k: response)

    def test_batches_over_one_hundred_and_deduplication(self):
        packages = [{'ecosystem': 'Maven', 'name': 'g:a' + str(i), 'version': '1.0'} for i in range(101)]
        calls = []
        def fetch(url, **kwargs):
            count = len(kwargs['data']['queries']); calls.append(count)
            return {'results': [{} for _ in range(count)]}
        self.assertEqual(m.osv_matches(packages, fetch), [])
        self.assertEqual(calls, [100, 1])


class PublishTests(unittest.TestCase):
    def test_time_and_commit_do_not_trigger_repeated_alerts(self):
        first = report(); second = copy.deepcopy(first)
        second['checked_at'] = 'tomorrow'; second['source_commit'] = 'new commit'
        self.assertEqual(m.state(first), m.state(second))
        second['bulletins'][0]['digest'] = 'revised'
        self.assertNotEqual(m.state(first), m.state(second))

    def test_create_then_update_then_new_finding_reopens_without_duplicates(self):
        existing = []
        mutations = []
        def api(url, **kwargs):
            method = kwargs.get('method', 'GET')
            if method == 'GET': return copy.deepcopy(existing)
            data = kwargs['data']; mutations.append((method, url, copy.deepcopy(data)))
            if method == 'POST' and url.endswith('/issues'):
                existing.append({'title': data['title'], 'body': data['body'], 'number': 12, 'state': 'open', 'user': {'login': 'github-actions[bot]'}, 'html_url': 'https://github.com/owner/repo/issues/12'})
                return existing[0]
            if method == 'PATCH': existing[0].update(data)
            return {}
        with patch.dict(os.environ, {'GITHUB_REPOSITORY': 'owner/repo', 'GH_TOKEN': 'test-token', 'SCALERULES_MAINTAINER': 'owner'}):
            data = report()
            self.assertTrue(m.publish(data, api).endswith('/12'))
            self.assertEqual(mutations[0][2]['assignees'], ['owner'])
            m.publish(data, api)
            self.assertEqual([x[0] for x in mutations], ['POST', 'PATCH'])
            existing[0]['state'] = 'closed'
            m.publish(data, api)
            self.assertEqual(existing[0]['state'], 'closed')
            data['dependencies']['matches'] = [{'ecosystem': 'Go', 'name': 'example.com/a', 'version': 'v1', 'id': 'GO-2026-1'}]
            m.publish(data, api)
            self.assertEqual(existing[0]['state'], 'open')
            self.assertEqual(sum('/comments' in x[1] for x in mutations), 1)
            m.publish(data, api)
            self.assertEqual(sum('/comments' in x[1] for x in mutations), 1)

    def test_credentials_cannot_be_sent_to_external_source(self):
        with self.assertRaises(ValueError): m.request('https://api.osv.dev/v1/querybatch', token='secret')
        with self.assertRaises(ValueError): m.request('http://api.github.com', token='secret')


if __name__ == '__main__':
    unittest.main()
