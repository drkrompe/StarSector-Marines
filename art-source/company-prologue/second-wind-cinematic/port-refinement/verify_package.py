"""Check authoring source syntax, raw image provenance and review references."""
import ast
import hashlib
import json
from pathlib import Path
import re

from PIL import Image

HERE = Path(__file__).resolve().parent


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    checks = {}
    scripts = sorted(HERE.glob('*.py'))
    for path in scripts:
        ast.parse(path.read_text(encoding='utf-8'), filename=str(path))
    checks['authoring_python_syntax'] = True
    json_files = [p for p in HERE.rglob('*.json') if 'build' not in p.relative_to(HERE).parts]
    for path in json_files:
        json.loads(path.read_text(encoding='utf-8'))
    checks['json_utf8_parse'] = True
    assets = []
    for name in ('generation.json', 'secondary-generation.json', 'card-generation.json', 'architecture-generation.json'):
        manifest = json.loads((HERE / name).read_text(encoding='utf-8'))
        rows = manifest.get('assets', [manifest]) + manifest.get('references', [])
        for row in rows:
            path = HERE / row['file']
            passed = path.exists() and digest(path) == row['sha256']
            if 'dimensions' in row:
                with Image.open(path) as image:
                    passed &= list(image.size) == row['dimensions'] and image.mode == row['mode']
            if 'prompt' in row:
                passed &= (HERE / row['prompt']).is_file()
            assets.append({'file': row['file'], 'passed': bool(passed)})
    checks['raw_assets_and_prompt_provenance'] = all(row['passed'] for row in assets)
    expected_sources = {
        'forest-reveal-realism/review/jangala-forest-reveal-realism.mp4': '5dd48e721ea17dcf1e9ec9e1a15aac64151c294658a8147a51be118550135fcc',
        'managed-land-realism/review/jangala-managed-land-realism.mp4': '692e4374db2acb7320af1fb277c6fba719af55b897f20be6cb5ce6660a066f64',
        'forest-reveal-realism/forest-reveal-realism.blend': '7a48fc520d7ce38edfe86b82aeaccb0d443b10b5ced3170c764e439ccfbfefec',
        'managed-land-realism/managed-land-realism.blend': '9c9ec437f12c9721df8c863b462ebbfc98045789b7f8964137ec05824bb49636',
        'port-approach-realism/port-approach-realism.blend': '0b737777f455809e3127a3895ff201faeb93d397d5f6b66c4de27bc1c20ae8e3',
    }
    preserved = [{'file': name, 'passed': digest(HERE.parent / name) == expected}
                 for name, expected in expected_sources.items()]
    checks['accepted_source_scenes_and_movies_unchanged'] = all(row['passed'] for row in preserved)
    links = re.findall(r'\]\(([^)]+)\)', (HERE / 'README.md').read_text(encoding='utf-8'))
    missing = [link for link in links if not link.startswith(('http:', 'https:', '#'))
               and not (HERE / link.split('#')[0]).exists()]
    checks['readme_local_links_exist'] = not missing
    scene_hash = digest(HERE / 'port-refinement.blend')
    for name in ('scene-validation', 'ground-regressions', 'traffic-visibility', 'media-validation'):
        report = json.loads((HERE / 'review' / (name + '.json')).read_text(encoding='utf-8'))
        checks[name + '_current_scene'] = report['scene_sha256'] == scene_hash
    report = {'passed': all(checks.values()), 'checks': checks, 'python_files': len(scripts),
              'json_files': len(json_files), 'assets': assets, 'preserved_sources': preserved,
              'missing_links': missing, 'scene_sha256': scene_hash,
              'limits': 'Package integrity and syntax only; Blender and motion review are separate evidence.'}
    (HERE / 'review/package-validation.json').write_text(
        json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))
    if not report['passed']:
        raise RuntimeError('Authoring package validation failed')


if __name__ == '__main__':
    main()
