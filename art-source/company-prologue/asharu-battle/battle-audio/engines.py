"""The Asharu battle's engines: every ship's drive, heard from each shot's camera (S1a to S11).

  python art-source/company-prologue/asharu-battle/battle-audio/engines.py [--shot S6 ...] [--out DIR]

Writes one stereo stem per shot (`<shot>.wav` and a page-ready `<shot>.mp3`) under
build/asharu-engines/, for the Battle Cut page's Sound effects tab (one "engines" cue per shot, on
the full bus).

The voice is the Jangala arrival's (second-wind-cinematic/arrival-audio/synth_flyby.py): a rocket
burn, bass and crackling combustion, never air or hum (owner, 2026-10-19). Each ship flies her own
blockout path as seen from the shot's camera, so the sound is placed by the picture:

- pan from her bearing off the lens axis (+X right, +Y ahead: synth_flyby's "c1"), so a ship
  crossing the frame crosses the speakers;
- loudness from her apparent size, (FULL_AT * length / distance) ** LOUDNESS_POWER, capped at
  full, with the muffle opening as she closes and a Doppler shift from her closing speed;
- her drive's size from her length (THRUSTERS), and its swell from the throttle her pose carries.

Levels hold across ships and shots: every stem is scaled by one constant, so a near pass is louder
than a far one. Space carries no sound; this is the reconstruction's, heard as if it did.
"""
import argparse
import importlib.util
import subprocess
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import asharu_preview  # noqa: E402
import battle_blockout as bb  # noqa: E402

OUT = HERE.parents[3] / "build" / "asharu-engines"
PAD = 0.4                # synth_flyby fades each clip's ends over 0.4 s: rendered past the cut and trimmed
GRID = 25.0              # path samples per second, interpolated between
SOUND_SPEED = 1500.0     # m/s: Doppler and delay for km ranges (Jangala's 300 m/s suits close passes only)
FULL_AT = 4.0            # a ship is at full level within this many of her lengths
LOUDNESS_POWER = 0.8
PEAK = 0.89              # the loudest shot's peak; every other shot keeps its level against it
MIN_LENGTH = 20.0        # drones and boats (Borers, Hermes) carry no drive of their own here
SILENT = ('S12',)        # the wreckfall stays silent (asharu-battle-shot.md, Sound)


def synth():
    path = HERE.parents[1] / "second-wind-cinematic" / "arrival-audio" / "synth_flyby.py"
    spec = importlib.util.spec_from_file_location("synth_flyby", path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


SF = synth()
# By length (owner's sizes, synth_flyby): a freighter or cruiser burns a single heavy thruster (the
# generated c4_launch_burn_t02, with the Legion's weight), a destroyer a medium drive, a frigate small.
HEAVY = dict(SF.HEAVY_RANGE, **{k: v for k, v in SF.LEGION_HEAVY.items() if k not in ("thrust", "distance_power")},
             source="c4_launch_burn_t02")
THRUSTERS = ((100.0, HEAVY, 1.0), (65.0, SF.MEDIUM_RANGE, 0.8), (MIN_LENGTH, SF.SMALL, 0.6))


def thruster(length):
    """(the synth_flyby settings, the level) of a ship this long; None for one without a drive."""
    for least, kind, level in THRUSTERS:
        if length >= least:
            return kind, level
    return None


def camera_frame(c):
    """The camera's right, ahead and up, as _in_frame takes them (its roll ignored)."""
    f = np.array([c['target'][i] - c['loc'][i] for i in range(3)], float)
    f /= np.linalg.norm(f)
    r = np.array([f[1], -f[0], 0.0])
    r /= np.linalg.norm(r)
    return np.array(c['loc'], float), r, f, np.cross(r, f)


def track(spec, key, ts):
    """Her positions (right, ahead, up) from the camera at times ts, and her throttle."""
    cam, pose = spec['camera']['at'], spec['ships'][key]
    rel, thr = [], []
    for t in ts:
        loc, r, f, u = camera_frame(cam(t))
        p = pose.at(t) if hasattr(pose, 'at') else pose
        d = np.array(p[:3], float) - loc
        rel.append((d @ r, d @ f, d @ u))
        thr.append(p[6] if len(p) > 6 else 0.5)
    return np.array(rel), np.array(thr)


def ship_stem(spec, key, start, end, seed):
    """Her drive, heard from the shot's camera over [start, end]: (stereo samples, level), or None."""
    length = bb.SHIP_LENGTH.get(key, 50.0)
    kind = thruster(length)
    if kind is None:
        return None
    settings, level = kind
    ts = np.arange(start - PAD - 3.0, end + PAD + 1.0 / GRID, 1.0 / GRID)
    rel, thr = track(spec, key, ts)

    def path(te):
        return np.stack([np.interp(te, ts, rel[:, i]) for i in range(3)], axis=1)

    r = np.linalg.norm(rel, axis=1)
    seen = (ts >= start) & (ts <= end)
    gain = np.minimum(1.0, FULL_AT * length / r) ** LOUDNESS_POWER * (0.2 + 0.8 * thr)
    run = dict(SF.HOUND, **settings, start=start - PAD, end=end + PAD, path=path, sound_speed=SOUND_SPEED,
               distance_power=LOUDNESS_POWER, seed=seed,
               thrust=[(t, 0.2 + 0.8 * g) for t, g in zip(ts, thr)], thumps=(), rcs=())
    return SF.render(run), level * gain[seen].max()


def shot_stem(seg):
    spec = bb.SHOTS[seg['kf']]
    if spec['camera'].get('at') is None:
        return None
    n = int(round((seg['end'] - seg['start'] + 2 * PAD) * SF.SR))
    mix, heard = np.zeros((n, 2)), []
    for i, key in enumerate(sorted(spec.get('ships', {}))):
        stem = ship_stem(spec, key, seg['start'], seg['end'], 101 + i)
        if stem is not None:
            samples, level = stem
            mix[:len(samples)] += samples[:n] / 0.89 * level
            heard.append((level, key))
    print(seg['id'], ' '.join('%s %.0f' % (k, 20 * np.log10(v)) for v, k in sorted(heard, reverse=True)[:5]))
    cut = int(round(PAD * SF.SR))
    return mix[cut:n - cut]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--shot", action="append", help="shot-list id (S6, S8-S9); all when omitted")
    parser.add_argument("--out", default=str(OUT))
    args = parser.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    segs = [s for s in asharu_preview.segments(bb.KEYFRAMES) if s['kf'] and s['first'] not in SILENT and (not args.shot or s['first'] in args.shot
                                                                            or s['id'].split()[0] in args.shot)]
    stems = {s['id'].split()[0]: shot_stem(s) for s in segs}
    stems = {k: v for k, v in stems.items() if v is not None}
    scale = PEAK / max(np.abs(v).max() for v in stems.values())
    for name, stem in stems.items():
        wav = out / (name + ".wav")
        SF.write(stem * scale, wav)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(wav), "-b:a", "160k", str(wav.with_suffix(".mp3"))],
                       check=True)
        print("%-6s peak %5.1f dB  %s" % (name, 20 * np.log10(max(1e-9, np.abs(stem * scale).max())), wav))


if __name__ == "__main__":
    main()
