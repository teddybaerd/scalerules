#!/usr/bin/env python3
"""Check English/German coverage, duplicate keys and Android format arguments."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / 'android/src/main/res'
FORMAT = re.compile(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]')


def read(folder):
    entries = list(ET.parse(ROOT / folder / 'strings.xml').getroot())
    strings = [node for node in entries if node.tag == 'string']
    names = [node.attrib['name'] for node in strings]
    assert len(names) == len(set(names)), f'Duplicate names in {folder}'
    return {node.attrib['name']: node for node in strings}


def check():
    default = read('values')
    german = read('values-de')
    translated = {key for key, node in default.items() if node.get('translatable') != 'false'}
    assert set(german) == translated, f'Translation key mismatch: {set(german) ^ translated}'
    for key in sorted(translated):
        source = ''.join(default[key].itertext())
        target = ''.join(german[key].itertext())
        assert target.strip(), f'Empty German string: {key}'
        assert sorted(FORMAT.findall(source)) == sorted(FORMAT.findall(target)), f'Format mismatch: {key}'
    locales = ET.parse(ROOT / 'xml/locale_config.xml').getroot()
    ns = '{http://schemas.android.com/apk/res/android}'
    assert {node.get(ns + 'name') for node in locales} == {'en', 'de'}
    print(f'Validated {len(translated)} German translations, format arguments and locale configuration.')


if __name__ == '__main__':
    check()
