"""Bench tests for W3, missiles (asharu-effects-plan.md): curved Harpoon flights, contrails left
in place, terminal hits and a kill short.

The rig is the S5 lane at bench scale: the Manticore (P) at the origin fires a pod salvo of four
Harpoons (two per pod, 0.25 s apart in each pod) at a Lasher 3 km down the lane, bow on. The
Lasher's three light MGs screen it (W2, `swfx_pd`), so the same plan carries the kills short.
`harpoon_lane()` builds it, and bench_pd's close-up reuses it.

The guided missiles hunt (the seeker's corrections, `swfx_missile_paths.Seeker`), and the curve
width is the owner's call: `harpoon-wide` (0.28) and `harpoon-narrow` (0.10) fly the same salvo
from the same camera on the same frames as the default's middle four, so the three read side by
side.
"""
import json
import math

LANE_M = 3000.0
CURVE_WIDTH = .18          # the Harpoon guide's peak sideways offset, a fraction of the range
WIDE, NARROW = .28, .10    # the pair the owner picks between
COMPARE_FRAMES = [46, 62, 78, 94]


def harpoon_lane(bench, width=CURVE_WIDTH, kills=2, seed=7):
    """The Manticore's four-Harpoon salvo into a Lasher's PD screen. Returns (manticore,
    lasher, flights, engagements)."""
    import swfx_missile_paths as mp
    import swfx_pd as pd
    m = bench.ship('manticore', 'manticore-p', at=(0, 0, 0), heading=0, throttle=.35)
    l = bench.ship('lasher', 'lasher', at=(LANE_M, 60, 40), heading=180, throttle=.6)
    target = bench.at(l, (14, 0, 4))           # her bow, above the waterline of the deck
    with m.kit() as (ctx, kit):
        starts = [(*mp.tube(m, ctx, t, tag), lab) for t, tag, lab in
                  ((5, 'tube 1', 't5_1'), (6, 'tube 1', 't6_1'), (5, 'tube 2', 't5_2'), (6, 'tube 2', 't6_2'))]
    flights = mp.salvo('harpoon', starts, target, mp.space_of(m), frames=[4, 7, 10, 13], spread_m=4.0,
                       seed=seed, target_name='lasher', width=width)
    with l.kit() as (ctx, kit):
        guns = [pd.gun(l, ctx, t, 'barrel 1', 'mg', range_m=850.0) for t in (1, 2, 3)]
    eng = pd.screen(flights, guns, kills=kills, seed=seed)
    with l.kit() as (ctx, kit):
        for r in pd.key_screen(ctx, eng, mp.space_of(l)):
            print("BENCH pd keyed", json.dumps(r, default=str), flush=True)
    with m.kit() as (ctx, kit):
        kit.guns.launch_flights(ctx, flights)
    report(flights, eng, target)
    return m, l, flights, eng


def report(flights, eng, target=None):
    """Print each flight's plan (the job log carries it), and the arrival cues for W5/W6."""
    import swfx_missile_paths as mp
    for fl in flights:
        peak = fl.lateral_peak(target) if target is not None else None
        print(f"BENCH plan {fl.kind} {fl.label}: launch f{fl.frame} ignite f{fl.ignite} "
              f"end {fl.end['kind']} f{fl.end['frame']} by {fl.end['by']} peak offset {peak}", flush=True)
        sk = getattr(fl, 'seeker', None)
        if sk is not None and sk.updates:
            worst = max(math.hypot(u[1], u[2]) for u in sk.updates)
            print(f"BENCH seeker {fl.label}: {len(sk.updates)} corrections every ~{sk.every}s, "
                  f"worst {worst:.1f} deg, first {sk.updates[:3]}", flush=True)
    for e in eng or ():
        print(f"BENCH pd {e['gun']['label']} -> {e['flight'].label}: open f{e['open']} close f{e['close']} "
              f"{'KILL f%s' % e['K'] if e['kill'] else 'miss'}", flush=True)
    print('BENCH cues ' + json.dumps(mp.arrivals(flights)), flush=True)


def harpoon_salvo(bench):
    """The whole lane from high on the Manticore's starboard quarter: the fan-out, the wide
    swing, the convergence, the Lasher's screen killing two short, two hits on her bow."""
    harpoon_lane(bench)
    bench.camera((1350, -2650, 1500), (1500, 0, 0), 30)


def harpoon_wide(bench):
    """The same salvo and camera with the guide swung out to 0.28 of the range."""
    harpoon_lane(bench, width=WIDE)
    bench.camera((1350, -2650, 1500), (1500, 0, 0), 30)


def harpoon_narrow(bench):
    """The same salvo and camera, the guide held in to 0.10 of the range."""
    harpoon_lane(bench, width=NARROW)
    bench.camera((1350, -2650, 1500), (1500, 0, 0), 30)


def harpoon_launch(bench):
    """Over the Manticore's shoulder: cold eject, ignition clear of the pods, the turn out."""
    harpoon_lane(bench)
    bench.camera((-260, -330, 150), (420, 40, 10), 30)


TESTS = {
    'harpoon-salvo': dict(
        builds=['manticore-p', 'lasher'], frames=[16, 30, 46, 62, 78, 94, 104, 118],
        note='Four Harpoons, 3 km lane, curve width 0.18 of range: fan out, swing wide, hunt and converge; '
             'the Lasher (far right) kills two short with her MGs, two hit her bow.',
        setup=harpoon_salvo),
    'harpoon-wide': dict(
        builds=['manticore-p', 'lasher'], frames=COMPARE_FRAMES,
        note='Curve width 0.28 (wide): the same salvo, camera and frames as harpoon-salvo f46-f94.',
        setup=harpoon_wide),
    'harpoon-narrow': dict(
        builds=['manticore-p', 'lasher'], frames=COMPARE_FRAMES,
        note='Curve width 0.10 (narrow): the same salvo, camera and frames as harpoon-salvo f46-f94.',
        setup=harpoon_narrow),
    'harpoon-launch': dict(
        builds=['manticore-p', 'lasher'], frames=[8, 14, 22, 34],
        note='Over the shoulder: the pods cold-eject (a small gas puff), the motors light clear of the tubes, '
             'and the missiles turn out on their blue contrails.',
        setup=harpoon_launch),
}
