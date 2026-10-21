"""One paid generation per family, through the codex-imagegen wrapper.

  python art-source/armor-icons/generate.py PALATINE [MILITIA ...] [--version v2] [--replace]
  python art-source/armor-icons/generate.py pathfinder corpsman ...     (variant patterns, variants.py)

Sends prompts/<slug>.prompt.txt with two references: the former tier icon for the family's
weight class (composition, camera, light and finish only) and refs/<slug>-identity.png (the
family's battle layers: the design authority). A variant pattern instead sends its family's accepted
armory image (base design) and the same battle layers. The untouched master and its provenance land in
raw/<slug>[-<version>].raw.png. Nothing retries; look at each master before building on it.
"""
import argparse
import subprocess
import sys

from briefs import composition_ref
from families import FAMILIES, ROOT, slug
from variants import VARIANTS, base_image, identity_ref

HERE = ROOT / 'art-source/armor-icons'


def family_refs(family, s):
    return ['--ref', f'{ROOT / composition_ref(family)}=Composition, camera, lighting and finish reference only; not the suit design, colours or markings',
            '--ref', f'{HERE / "refs" / f"{s}-identity.png"}=Identity authority: the same suit seen from directly overhead in battle; palette, plate shapes, markings']


def variant_refs(pattern):
    return ['--ref', f'{base_image(pattern)}=Base design authority: the finished armory image of the suit this pattern varies; design, palette, composition and finish',
            '--ref', f'{identity_ref(pattern)}=Identity: the same suit seen from directly overhead in battle; palette and silhouette character']


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('families', nargs='+', choices=list(FAMILIES) + list(VARIANTS))
    ap.add_argument('--version', default='')
    ap.add_argument('--replace', action='store_true')
    args = ap.parse_args()
    failed = []
    for family in args.families:
        s = family if family in VARIANTS else slug(family)
        out = HERE / 'raw' / f'{s}{"-" + args.version if args.version else ""}.raw.png'
        out.parent.mkdir(parents=True, exist_ok=True)
        cmd = [sys.executable, str(ROOT / 'tools/codex_imagegen.py'),
               '--prompt', str(HERE / 'prompts' / f'{s}.prompt.txt'),
               '--out', str(out),
               ] + (variant_refs(family) if family in VARIANTS else family_refs(family, s))
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
