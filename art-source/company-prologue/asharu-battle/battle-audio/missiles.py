"""The Asharu battle's Harpoons: every missile's launch and motor, heard from each shot's camera (S5, S6).

  python art-source/company-prologue/asharu-battle/battle-audio/missiles.py [--shot S5 ...] [--out DIR]

Writes one stereo stem per shot (`<shot>.wav` and a page-ready `<shot>.mp3`) under
build/asharu-missiles/, for the Battle Cut page's Sound effects tab (one "Harpoons" cue per shot,
on the full bus).

Each Harpoon is its own source (owner, 2026-10-30: "individual missile launch sounds ... their own
burning thrust sounds individually"). It leaves its tube with the launch take (a thunk, then the
motor lighting), and its motor (the looped motor take) burns from ignition to its hit, flown by
engines.py's machinery (synth_flyby): panned by its bearing off the lens, louder as it nears, with
a Doppler shift from its closing speed. Only the salvos a shot draws are heard in it: S5 draws
Lasher A's and the Phaeton's leaving, S6 those two arriving and the lead Lasher's.

The times are the finished renders' (the 'missiles' rows their logs print: launch and arrival
frames per salvo, one tube every 3 frames), and the flight is the Harpoon profile's: a cold eject
at 18 m/s, lit 0.35 s later, accelerating at 650 m/s2 to 950 m/s (swfx_missile_paths.PROFILES).
The path runs straight from the shooter as she stood at launch to the aim on her target as she is
at the hit; the drawn seeker swings off it by tens of metres at kilometres' range, inaudibly.
"""
import argparse
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
SF.TAKES = HERE / "sfx-takes"            # the motor take is this battle's, not the Jangala arrival's
OUT = HERE.parents[3] / "build" / "asharu-missiles"
LAUNCH_TAKE, MOTOR_TAKE = "harpoon-launch-one_t01", "harpoon-motor_t01"
EJECT_MPS, IGNITE_S, ACCEL, VMAX = 18.0, 0.35, 650.0, 950.0
SIZE = 40.0              # heard as loud as a ship this long would be (engines.FULL_AT): a motor is fierce
PEAK = 0.89
# (shot, target, first launch frame, arrival frames), the frames on each shot's key time (S5 47 s,
# S6 56 s) less a frame: from the logs of asharu-final-n1-s5-m5-d44024e85 and asharu-final-n1-s6-k3-735d44ffd
SALVOS = (('S5', 'lasher-a', -7, (141, 144, 149, 150)), ('S5', 'phaeton', 24, (161, 163, 166, 170)),
          ('S6', 'lasher-a', -225, (-74, -71, -69, -65)), ('S6', 'phaeton', -191, (-53, -51, -49, -47)),
          ('S6', 'lasher-lead', -157, (-6, -2, -1, 0)))
FRAME0 = {'S5': bb.M5_T - 1 / 24, 'S6': 56.0 - 1 / 24}


def harpoons(shot):
    """(launch, hit, target) seconds for each Harpoon the shot draws: one tube every 3 frames."""
    return [(FRAME0[shot] + (first + 3 * i) / 24, FRAME0[shot] + hit / 24, target)
            for s, target, first, hits in SALVOS if s == shot for i, hit in enumerate(hits)]


def travelled(t):
    """Metres from the tube t seconds after launch."""
    t = np.maximum(t, 0.0)
    lit = np.maximum(t - IGNITE_S, 0.0)
    ramp = (VMAX - EJECT_MPS) / ACCEL                  # seconds to full speed once lit
    fast = np.minimum(lit, ramp)
    return EJECT_MPS * np.minimum(t, IGNITE_S) + EJECT_MPS * fast + ACCEL * fast ** 2 / 2 + VMAX * (lit - fast)


def flight(spec, launch, hit, target, ts):
    """Its positions (right, ahead, up) from the shot's camera at times ts."""
    ships = spec['ships']
    a = np.array(ships['manticore-p'].at(launch)[:3], float)
    b = np.array(ships[target].at(hit)[:3], float)
    u = np.clip(travelled(ts - launch) / max(1e-6, travelled(np.array([hit - launch]))[0]), 0, 1)
    rel = []
    for t, k in zip(ts, u):
        loc, r, f, up = engines.camera_frame(spec['camera']['at'](t))
        d = a + (b - a) * k - loc
        rel.append((d @ r, d @ f, d @ up))
    return np.array(rel)


def loudness(rel):
    return np.minimum(1.0, engines.FULL_AT * SIZE / np.linalg.norm(rel, axis=-1)) ** engines.LOUDNESS_POWER


def take(name):
    """A take as mono samples at the synth's rate."""
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(SF.TAKES / (name + ".mp3")), "-ac", "1", "-ar",
                          str(SF.SR), "-f", "f32le", "-"], check=True, capture_output=True).stdout
    x = np.frombuffer(raw, np.float32).astype(np.float64)
    return x / max(1e-9, np.abs(x).max())


def motor(spec, launch, hit, target, start, end, seed):
    """Its motor from ignition to the hit, heard over [start, end]: (stereo, level) or None."""
    lit = launch + IGNITE_S
    a, b = max(start, lit), min(end, hit)
    if a >= b:
        return None
    ts = np.arange(a - engines.PAD - 3.0, b + engines.PAD + 1.0 / engines.GRID, 1.0 / engines.GRID)
    rel = flight(spec, launch, hit, target, ts)

    def path(te):
        return np.stack([np.interp(te, ts, rel[:, i]) for i in range(3)], axis=1)

    seen = (ts >= a) & (ts <= b)
    grid = np.arange(ts[0], ts[-1], 1 / 400)        # lit over 50 ms, cut dead at the hit
    burn = np.clip((grid - lit) / .05, 0, 1) * (grid <= hit)
    run = dict(SF.HOUND, **SF.SMALL, start=a - engines.PAD, end=b + engines.PAD, path=path,
               sound_speed=engines.SOUND_SPEED, distance_power=engines.LOUDNESS_POWER, seed=seed,
               source=MOTOR_TAKE, thrust=list(zip(grid, burn)), thumps=(), rcs=())
    return SF.render(run), loudness(rel[seen]).max(), a - engines.PAD


def launch_sound(spec, launch, hit, target, clip):
    """Its launch take placed where it left the tube: (stereo, level)."""
    rel = flight(spec, launch, hit, target, np.array([launch]))[0]
    pan = np.clip(np.sin(np.arctan2(rel[0], rel[1])) * SF.HOUND['pan_width'], -1, 1)
    left, right = np.cos((pan + 1) * np.pi / 4), np.sin((pan + 1) * np.pi / 4)
    return np.stack([clip * left, clip * right], axis=1), loudness(rel)


def shot_stem(seg, clip):
    spec = bb.SHOTS[seg['kf']]
    shot = seg['first']
    n = int(round((seg['end'] - seg['start']) * SF.SR))
    mix = np.zeros((n, 2))

    def add(samples, level, at):
        i = int(round((at - seg['start']) * SF.SR))
        lo, hi = max(0, i), min(n, i + len(samples))
        if hi > lo:
            mix[lo:hi] += samples[lo - i:hi - i] / 0.89 * level

    for k, (launch, hit, target) in enumerate(harpoons(shot)):
        if seg['start'] <= launch < seg['end']:
            add(*launch_sound(spec, launch, hit, target, clip), launch)
        heard = motor(spec, launch, hit, target, seg['start'], seg['end'], 301 + k)
        if heard is not None:
            add(*heard)
        print('%s  %-11s launch %6.2f  lit %6.2f  hit %6.2f' % (shot, target, launch, launch + IGNITE_S, hit))
    return mix


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--shot", action="append", help="shot-list id (S5, S6); both when omitted")
    parser.add_argument("--out", default=str(OUT))
    args = parser.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    clip = take(LAUNCH_TAKE)
    segs = [s for s in asharu_preview.segments(bb.KEYFRAMES) if s['first'] in FRAME0 and (not args.shot or s['first'] in args.shot)]
    stems = {s['first']: shot_stem(s, clip) for s in segs}
    scale = PEAK / max(np.abs(v).max() for v in stems.values())
    for name, stem in stems.items():
        wav = out / (name + ".wav")
        SF.write(stem * scale, wav)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(wav), "-b:a", "160k", str(wav.with_suffix(".mp3"))],
                       check=True)
        print("%-6s peak %5.1f dB  %s" % (name, 20 * np.log10(max(1e-9, np.abs(stem * scale).max())), wav))


if __name__ == "__main__":
    main()
