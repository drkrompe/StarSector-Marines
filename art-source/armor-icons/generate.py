"""One paid generation per family, through the codex-imagegen wrapper.

  python art-source/armor-icons/generate.py PALATINE [MILITIA ...] [--version v2] [--replace]

Sends prompts/<slug>.prompt.txt with two references: the former tier icon for the family's
weight class (composition, camera, light and finish only) and refs/<slug>-identity.png (the
family's battle layers: the design authority). The untouched master and its provenance land in
raw/<slug>[-<version>].raw.png. Nothing retries; look at each master before building on it.
"""
import argparse
import subprocess
import sys

from briefs import composition_ref
from families import FAMILIES, ROOT, slug

HERE = ROOT / 'art-source/armor-icons'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('families', nargs='+', choices=list(FAMILIES))
    ap.add_argument('--version', default='')
    ap.add_argument('--replace', action='store_true')
    args = ap.parse_args()
    failed = []
    for family in args.families:
        s = slug(family)
        out = HERE / 'raw' / f'{s}{"-" + args.version if args.version else ""}.raw.png'
        out.parent.mkdir(parents=True, exist_ok=True)
        cmd = [sys.executable, str(ROOT / 'tools/codex_imagegen.py'),
               '--prompt', str(HERE / 'prompts' / f'{s}.prompt.txt'),
               '--out', str(out),
               '--ref', f'{ROOT / composition_ref(family)}=Composition, camera, lighting and finish reference only; not the suit design, colours or markings',
               '--ref', f'{HERE / "refs" / f"{s}-identity.png"}=Identity authority: the same suit seen from directly overhead in battle; palette, plate shapes, markings']
        if args.replace:
            cmd.append('--replace')
        print('>>', family, flush=True)
        if subprocess.run(cmd, cwd=ROOT).returncode != 0:
            failed.append(family)
    if failed:
        print('failed:', ' '.join(failed))
        sys.exit(1)


if __name__ == '__main__':
    main()
