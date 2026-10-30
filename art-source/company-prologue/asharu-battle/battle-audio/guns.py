"""The Asharu battle's guns: every round a turret fires, heard from each shot's camera (S5 to S11).

  python art-source/company-prologue/asharu-battle/battle-audio/guns.py collect JOB_LOG ...
  python art-source/company-prologue/asharu-battle/battle-audio/guns.py [--shot S6b ...] [--out DIR]

Each round is its own sound (owner, 2026-10-30: "individual MG like cannon rounds" for the
turrets), on the frame it leaves the muzzle. The frames are the finished kit's: scene_fx logs a
"rounds" row per gun as it plans the shot, and `collect` reads those rows out of render-queue job
logs (the asharu-rounds-* jobs build each shot's kit at its opening, as a final does, and render one
frame) into `gun-rounds.json` beside this script.

A round plays its weapon's own fire sound from the game (GAME_FIRE; `--source generated` plays its
family's ElevenLabs take instead: machine gun, autocannon, the Hellbore, energy bolt), placed
by where its ship is from the camera as engines.py places a drive: panned by her bearing off the
lens, louder and brighter as she nears. A round is heard on its flash, never delayed by its range:
the cut is the picture's. Writes one stereo stem per shot (`<shot>.wav`, `<shot>.mp3`) under
build/asharu-guns/, one constant scaling them all.
"""
import argparse
import ast
import json
import re
import subprocess
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import asharu_preview  # noqa: E402
import battle_blockout as bb  # noqa: E402
import engines  # noqa: E402

SF = engines.SF
ROUNDS = HERE / "gun-rounds.json"
OUT = HERE.parents[3] / "build" / "asharu-guns"
PEAK = 0.89
# A round at full loudness and its sound's full volume peaks here; the summed rounds of a dense duel
# are held under PEAK by a soft limiter (owner, 2026-10-30, S6b: tracers firing with no sound until
# the hits, when one constant scaled every shot against S8-S9's hundreds of overlapping rounds).
ROUND_GAIN = 0.6
# family: (take, seconds of it each round plays, heard as loud as a ship this long would be). A gun
# carries: S6b's Lasher at 530 m and the merchant Wolf at 950 m are heard, not felt.
FAMILY = {'mg': ('round-mg_t01', .35, 120.0), 'ac': ('round-ac_t01', .6, 180.0),
          'heavy': ('round-heavy_t01', 2.0, 300.0), 'energy': ('round-energy_t01', .5, 160.0)}
WEAPONS = {'lightmg': 'mg', 'lightdualmg': 'mg', 'vulcan': 'mg', 'chaingun': 'mg', 'lightneedler': 'mg',
           'lightac': 'ac', 'heavyac': 'ac', 'mark9': 'ac', 'lightag': 'ac', 'hellbore': 'heavy',
           'ioncannon': 'energy', 'heavyblaster': 'energy'}       # scene_fx.SceneFx.FAMILY's grouping
# The game's own fire sound per weapon (owner, 2026-10-30: reuse the base game's sounds): its .wpn
# fireSoundTwo, resolved through data/config/sounds.json to the first variant, at its pitch and
# volume. Read from the install, never copied into the repository.
GAME = Path(r"C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core")
GAME_FIRE = {'lightmg': ('sfx_wpn_guns/light_machinegun_fire_01', 1.0, 0.4),
             'lightdualmg': ('sfx_wpn_guns/light_machinegun_fire_01', 1.0, 0.4),
             'lightac': ('sfx_wpn_guns/autocannon_fire_01', 1.4, 0.7),
             'heavyac': ('sfx_wpn_guns/autocannon_fire_01', 0.95, 0.7),
             'vulcan': ('sfx_wpn_guns/vulcan_cannon_fire_01_01', 1.0, 0.2),
             'hellbore': ('sfx_wpn_guns/hellbore_fire_01', 1.0, 1.0),
             'ioncannon': ('sfx_wpn_energy/ion_cannon_fire_01', 1.01, 0.5),
             'heavyblaster': ('sfx_wpn_energy/heavy_blaster_fire_01', 1.0, 0.5)}
DULL = (300.0, 900.0, 2500.0, 8000.0, None)     # low-pass corners, far to near (None: the take as made)
ROW = re.compile(r'ASHARU kit (\w+?)_f\d+ rounds (\S+) t(\d+) (\w+) -> (\S+): (\[.*\])')


def collect(logs):
    """The rounds rows of the given job logs, by shot key frame: {kf: [[ship, turret, weapon, target, [frames]]]}."""
    rounds = {}
    for log in logs:
        seen = set()
        for line in Path(log).read_text(encoding='utf-8', errors='replace').splitlines():
            m = ROW.search(line)
            if m and m.group(0)[m.group(0).index(' rounds '):] not in seen:      # every chunk logs its kit once
                seen.add(m.group(0)[m.group(0).index(' rounds '):])
                kf, ship, turret, weapon, target, frames = m.groups()
                rounds.setdefault(kf, []).append([ship, int(turret), weapon, target, ast.literal_eval(frames)])
    old = json.loads(ROUNDS.read_text(encoding='utf-8')) if ROUNDS.exists() else {}
    old.update(rounds)
    ROUNDS.write_text(json.dumps(old, indent=1, sort_keys=True) + '\n', encoding='utf-8', newline='\n')
    for kf, rows in sorted(rounds.items()):
        print(kf, ', '.join(f'{s} t{t} {w} x{len(f)}' for s, t, w, _b, f in rows))


def take(path, seconds=None, pitch=1.0, volume=1.0):
    """A sound file's first `seconds` (all of it when None), mono, played at `pitch` and scaled to
    `volume` of full, faded out over its last fifth, in each DULL variant."""
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(round(SF.SR / pitch)),
                          "-f", "f32le", "-"], check=True, capture_output=True).stdout
    x = np.frombuffer(raw, np.float32).astype(np.float64)       # read at SR / pitch, played at SR
    x = x[:int(seconds * SF.SR)] if seconds else x
    x = x * np.minimum(1.0, (len(x) - np.arange(len(x))) / (0.2 * len(x)))
    out = []
    for corner in DULL:
        y = x if corner is None else SF.one_pole(SF.one_pole(x, corner), corner)
        out.append(volume * y / max(1e-9, np.abs(x).max()))
    return out


def takes(source):
    """weapon -> its DULL variants: the game's fire sound, or the generated take of its family."""
    if source == 'game':
        return {w: take(GAME / 'sounds' / (f + '.ogg'), None, pitch, volume) for w, (f, pitch, volume) in GAME_FIRE.items()}
    fam = {f: take(HERE / 'sfx-takes' / (name + '.mp3'), seconds) for f, (name, seconds, _size) in FAMILY.items()}
    return {w: fam[f] for w, f in WEAPONS.items()}


def where(spec, ship, t):
    """Her position (right, ahead, up) from the shot's camera at t."""
    loc, r, f, u = engines.camera_frame(spec['camera']['at'](t))
    d = np.array(spec['ships'][ship].at(t)[:3], float) - loc
    return d @ r, d @ f, d @ u


def shot_stem(seg, rows, takes):
    spec = bb.SHOTS[seg['kf']]
    key_t = bb.SHOTS[bb.add_span(f"{seg['kf']}:{seg['start']}:{seg['start'] + 1 / 24}:24")[0]]['key_t']
    n = int(round((seg['end'] - seg['start']) * SF.SR))
    mix, count = np.zeros((n, 2)), {}
    for ship, turret, weapon, _target, frames in rows:
        fam = WEAPONS.get(weapon, 'ac')
        size = FAMILY[fam][2]
        for f in frames:
            t = key_t + (f - 1) / 24              # frame 1 is the key time (battle_blockout's anchor)
            if not seg['start'] <= t < seg['end'] or ship not in spec['ships']:
                continue
            x, y, z = where(spec, ship, t)
            r = max(1.0, float(np.sqrt(x * x + y * y + z * z)))
            near = min(1.0, engines.FULL_AT * size / r) ** engines.LOUDNESS_POWER
            clip = takes.get(weapon, takes['lightac'])[min(len(DULL) - 1, int(near * len(DULL)))]
            pan = np.clip(np.sin(np.arctan2(x, y)) * SF.HOUND['pan_width'], -1, 1)
            i = int(round((t - seg['start']) * SF.SR))
            m = min(n, i + len(clip)) - i
            mix[i:i + m, 0] += clip[:m] * near * np.cos((pan + 1) * np.pi / 4)
            mix[i:i + m, 1] += clip[:m] * near * np.sin((pan + 1) * np.pi / 4)
            count[f'{ship} {weapon}'] = count.get(f'{ship} {weapon}', 0) + 1
    print(seg['first'], ', '.join(f'{k} x{v}' for k, v in sorted(count.items())) or 'no rounds')
    return mix


def main():
    if len(sys.argv) > 1 and sys.argv[1] == 'collect':
        return collect(sys.argv[2:])
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--shot", action="append", help="shot-list id (S6b, S8-S9); all with rounds when omitted")
    parser.add_argument("--source", choices=('game', 'generated'), default='game',
                        help="the game's own fire sounds (default) or the generated family takes")
    parser.add_argument("--out", default=str(OUT))
    args = parser.parse_args()
    out = Path(args.out) / args.source
    out.mkdir(parents=True, exist_ok=True)
    rounds = json.loads(ROUNDS.read_text(encoding='utf-8'))
    clips = takes(args.source)
    segs = [s for s in asharu_preview.segments(bb.KEYFRAMES) if s['kf'] in rounds
            and (not args.shot or s['first'] in args.shot or s['id'].split()[0] in args.shot)]
    stems = {s['id'].split()[0]: shot_stem(s, rounds[s['kf']], clips) for s in segs}
    stems = {k: v for k, v in stems.items() if np.abs(v).max() > 0}
    for name, stem in stems.items():
        wav = out / (name + ".wav")
        stem = PEAK * np.tanh(ROUND_GAIN * stem / PEAK)
        SF.write(stem, wav)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(wav), "-b:a", "160k", str(wav.with_suffix(".mp3"))],
                       check=True)
        print("%-6s peak %5.1f dB  %s" % (name, 20 * np.log10(max(1e-9, np.abs(stem).max())), wav))


if __name__ == "__main__":
    main()
