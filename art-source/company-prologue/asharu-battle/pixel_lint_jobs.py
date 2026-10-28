"""Asharu pre-finals pixel lint (coordinator, 2026-10-28): the stage-only link sweep never renders, so
the pixel lints (the white-blob hold above all) first ran in the finals, where S5's Harpoon launch
stopped two parts. This writes, for each shot, a few short runs of consecutive frames where an effect
flashes or glows, rendered at a finals template's own settings with the lints on.

    python pixel_lint_jobs.py --template <finals template.json> --sweep <stage-only sweep job log> \\
        --commit <commit> --out <dir> [--runs 5]
    python pixel_lint_jobs.py --report <Drive folder the jobs wrote>

The runs are RUN_F frames (blob_lint flags a region held past HOLD_24 frames, so the third frame of
a hold is the first it can see), at most --runs a shot, in this order:
  launch    each Harpoon salvo, from IGNITE_F frames after it leaves the tubes until its last motor
            has lit (one tube every TUBE_F frames), plus RUN_F: the motors light beside the launcher,
            where they are biggest (S5 final, 2026-10-28: holds at f01135 and f01170)
  flash     each ledger hit that flashes (asharu_damage.LEDGER, kinds FLASH), from the frame before it
  arrival   each salvo's last warhead arriving
Launches and arrivals are the kit's own rows in a stage-only sweep's log (scene_fx: 'missiles a -> b:
n Harpoons, launched L, arriving [...]', scene frames counted from the key frame, 1 at the key).

One job per shot, `machine: any`, review lane, ASHARU_AUDIT_SOFT=1 so a run logs every held region
rather than stopping at the first. Each frame's blob record goes to the Drive folder with its frame;
--report chains them per shot (blob_lint.chain) and lists every hold. Submit each job with
tools/render_queue.py submit <job.json> --commit <commit>.
"""
import argparse
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

FPS = 24
RUN_F = 5          # frames in a run: a hold past HOLD_24 (2) shows on its third, with margin either side
IGNITE_F = 10      # frames from a salvo leaving the tubes to its first motor lighting (cold eject)
TUBE_F = 3         # frames between a salvo's tubes (scene_fx.missiles)
FLASH = ('he', 'breach', 'kill')
MISSILES = re.compile(r'ASHARU kit (\w+?)_f\d+ missiles \S+ -> \S+: (\d+) Harpoons, launched (-?\d+), arriving \[([^\]]*)\]')


def shots():
    """{kf: (shot label, first film frame, end film frame exclusive, key film frame)}, every lit shot."""
    import battle_blockout as bb
    out = {}
    for kf, s in bb.KEYFRAMES.items():
        path = s.get('camera', {}).get('at') if isinstance(s.get('camera'), dict) else None
        if not s.get('shot') or path is None or '_f' in kf or not hasattr(path, 'keys'):
            continue
        key_t = next((p.t for p in s['ships'].values() if hasattr(p, 'at')), None)
        if key_t is None:
            continue
        out[kf] = (s['shot'], round(path.keys[0]['t'] * FPS), round(path.keys[-1]['t'] * FPS), round(key_t * FPS))
    return out


def runs(sweep_log, per_shot):
    """{kf: [(why, first film frame, frames)]}: each shot's runs, in priority order, overlaps dropped."""
    import asharu_damage
    sh = shots()
    want = {kf: [] for kf in sh}
    for m in MISSILES.finditer(Path(sweep_log).read_text(encoding='utf-8', errors='replace')):
        kf, n, launched = m.group(1), int(m.group(2)), int(m.group(3))
        arriving = [int(a) for a in m.group(4).split(',') if a.strip()]
        if kf not in sh:
            continue
        key = sh[kf][3]
        want[kf].append((0, 'launch', key + launched - 1 + IGNITE_F, TUBE_F * (n - 1) + RUN_F))
        if arriving:
            want[kf].append((2, 'arrival', key + max(arriving) - 1 - 1, RUN_F))
    by_label = {}
    for kf, (label, *_r) in sh.items():
        by_label.setdefault(label, kf)
    for h in asharu_damage.LEDGER:
        if h.kind not in FLASH or not h.src:
            continue
        parts = h.src.split()
        kf = parts[1] if len(parts) > 1 and parts[1] in sh else by_label.get(parts[0])
        if kf is not None:
            want[kf].append((1, f'flash {h.ship}@{h.t}:{h.kind}', round(h.t * FPS) - 1, RUN_F))
    out = {}
    for kf, rows in want.items():
        _label, a, b, _key = sh[kf]
        kept = []
        for _p, why, f, n in sorted(rows):
            f = max(a, min(f, b - n))
            n = min(n, b - f)
            if any(f < g + m and g < f + n for _w, g, m in kept):
                continue
            kept.append((why, f, n))
            if len(kept) == per_shot:
                break
        if kept:
            out[kf] = sorted(kept, key=lambda r: r[1])
    return out


def job(template, kf, label, rows, commit):
    t = json.loads(Path(template).read_text(encoding='utf-8'))
    cmd = list(t['command'])
    i = cmd.index('--span')
    cmd[i + 1] = ','.join(f'{kf}:{f / FPS:.6f}:{(f + n) / FPS:.6f}:{FPS}' for _w, f, n in rows)
    slug = label.lower()
    dest = '{drive}/review/asharu/pixel-lint-%s/%s' % (commit[:9], slug)
    return dict(t, id=f'asharu-pixel-lint-{slug}-{kf}-{commit[:9]}', machine='any', lane='review', after=[],
                command=cmd, env=dict(t.get('env') or {}, ASHARU_AUDIT_SOFT='1'),
                inputs=[dict(x, commit='<filled in by --commit>') if x.get('kind') == 'git' else x for x in t['inputs']],
                frames=dict(t['frames'], glob=f'{kf}_f*.png', count=sum(n for _w, _f, n in rows)),
                outputs=[{'from': t['frames']['dir'], 'glob': f'{kf}_f*.png', 'to': dest},
                         {'from': t['frames']['dir'], 'glob': f'{kf}_blobs_f*.json', 'to': dest}])


def report(folder):
    """Every hold in the jobs' saved blob records, chained per shot across its runs."""
    import blob_lint
    held = 0
    for d in sorted(p for p in Path(folder).iterdir() if p.is_dir()):
        recs = [json.loads(p.read_text()) for p in sorted(d.glob('*_blobs_f*.json'))]
        rows = blob_lint.chain(recs)
        held += len(rows)
        print(f'{d.name}: {len(recs)} frames, {len(rows)} held' + ''.join(f'\n  f{f:05d} {r}' for f, r in rows))
    print('PIXEL LINT ' + ('FAIL' if held else 'clean'))
    return 1 if held else 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--template')
    ap.add_argument('--sweep')
    ap.add_argument('--commit')
    ap.add_argument('--out')
    ap.add_argument('--runs', type=int, default=5)
    ap.add_argument('--report')
    a = ap.parse_args()
    if a.report:
        return report(a.report)
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    sh = shots()
    total = 0
    for kf, rows in runs(a.sweep, a.runs).items():
        j = job(a.template, kf, sh[kf][0], rows, a.commit)
        (out / f'{j["id"]}.json').write_text(json.dumps(j, indent=1), encoding='utf-8')
        total += j['frames']['count']
        print(f'{j["id"]}: ' + ', '.join(f'{why} f{f:05d}+{n}' for why, f, n in rows))
    print(f'{total} frames')
    return 0


if __name__ == '__main__':
    sys.exit(main())
