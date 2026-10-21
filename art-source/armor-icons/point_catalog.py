"""Point every armor pattern's catalog iconPath at its own variant image, else its family's.

  python art-source/armor-icons/point_catalog.py

Rewrites only the iconPath lines of mod/data/marines/marine-armor-catalog.armor.json, keeping the
file's own formatting. A pattern whose family has no exported image keeps its current path.
"""
import json
import re

from families import CATALOG, ROOT, icon_path
from variants import VARIANTS, variant_icon_path

def main():
    patterns = json.load(open(CATALOG))['armor']
    lines = CATALOG.read_text(encoding='utf-8').split('\n')
    targets = {}
    for p in patterns:
        fam = p['battle']['appearanceFamily']
        own = p['id'].removeprefix('armor.')
        if own in VARIANTS and (ROOT / 'mod' / variant_icon_path(own)).exists():
            targets[p['id']] = variant_icon_path(own)
        elif (ROOT / 'mod' / icon_path(fam)).exists():
            targets[p['id']] = icon_path(fam)
    current, changed = None, 0
    for i, line in enumerate(lines):
        m = re.match(r'\s*"id":\s*"(armor\.[^"]+)"', line)
        if m:
            current = m.group(1)
        m = re.match(r'(\s*"iconPath":\s*")([^"]*)(".*)', line)
        if m and current in targets and m.group(2) != targets[current]:
            lines[i] = m.group(1) + targets[current] + m.group(3)
            changed += 1
    CATALOG.write_bytes('\n'.join(lines).encode('utf-8'))
    print(f'{changed} iconPath lines repointed')


if __name__ == '__main__':
    main()
