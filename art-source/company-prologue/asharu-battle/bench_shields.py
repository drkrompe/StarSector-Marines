"""Bench tests for W1 (asharu-effects-plan.md): shields as a film, in tech colours by rule.

Every ship runs the same drill on a real 24 fps timeline through swfx_shield's own helpers,
built onto that ship's copy inside ship.kit() (no build carries the shield group yet):

    f1   raise (the unfold, about a second)            render f12: half open, the edge leading
    f30  up and idle: a faint rim, the face barely there           render f30
    f33  volley 1, four hits, flux 0 -> .25: flashes and ripple rings   render f39
    f52  volley 2, flux -> .6: thin patches, wider rings             render f58
    f70  volley 3, flux -> .92: banding, leaking hits                render f76
    f80  overload from the last hit: the tear runs, shards, arcs, lamps stutter
                                                                  render f85 (tear) and f95 (shards)
    f130 vent: radiators glow and shed vapour, flux bleeds off       render f140

The camera stands 300 m off on the port bow, where the hits land.
"""
import importlib
import math

FPS = 24
DRILL = [12, 30, 39, 58, 76, 85, 95, 140]
OVERLOAD = [83, 87, 92, 100]   # the tear running, then the pieces burning away
VOLLEYS = [  # (first frame, flux at its end, hits as (azimuth, elevation) on the camera's side)
    (33, .25, [(30, 10), (12, 4), (44, 18), (24, -6)]),
    (52, .6, [(18, 14), (40, 2), (6, -4), (34, 22)]),
    (70, .92, [(26, 8), (48, 12), (14, 18), (36, -2)]),
]
OVERLOAD_AT, VENT_AT = 80, 130
CAMERA_AZ, CAMERA_EL = 35.0, 20.0


def drill(ship, facing=0.0, spin=0.0):
    """Build the film shield on this ship and key the drill. spin turns the hits (degrees)."""
    with ship.kit() as (ctx, kit):
        shield = importlib.reload(importlib.import_module('swfx_shield'))
        rec = shield.build(ctx, film=True, hull_collection=ship.hull)
        if rec.get('bubble') is None:
            raise SystemExit(f'{ship.key}: no shield built: {rec}')
        ctx.set('shield_facing', facing)
        shield.raise_shield(ctx, 1, fps=FPS)
        shield.flux(ctx, VOLLEYS[0][0] - 1, 0.0)
        last = None
        for start, flux, hits in VOLLEYS:
            for k, (az, el) in enumerate(hits, 1):
                f = start + 2 * (k - 1)
                shield.hit(ctx, k, f, az + spin, el, strength=1.0, fps=FPS)
                last = (az + spin, el)
            shield.flux(ctx, start + 2 * (len(hits) - 1), flux)
        shield.overload(ctx, OVERLOAD_AT, *last, fps=FPS)
        shield.vent(ctx, VENT_AT, duration_s=3.0, fps=FPS)
    print(f"BENCH shield {ship.key}: {rec['type']} {rec['arc']:g} deg, scheme {rec['scheme']}, "
          f"mean radius {rec['mean_radius']} u, vents {rec.get('vents')}, missing {rec['missing']}", flush=True)
    return rec


def _camera(bench, ship, distance, lens, target=(0, 0, 0)):
    a, e = math.radians(CAMERA_AZ), math.radians(CAMERA_EL)
    t = bench.at(ship, target)
    d = (math.cos(a) * math.cos(e), math.sin(a) * math.cos(e), math.sin(e))
    bench.camera(tuple(t[i] + d[i] * distance for i in range(3)), tuple(t), lens)


def lasher_front(bench):
    s = bench.ship('lasher', 'lasher', throttle=.4)
    drill(s)
    _camera(bench, s, 300, 70, target=(8, 0, 0))


def kite_omni(bench):
    s = bench.ship('kite', 'kite-p', throttle=.4)
    drill(s, facing=30.0)
    _camera(bench, s, 300, 105)


def lasher_overload(bench):
    s = bench.ship('lasher', 'lasher', throttle=.4)
    drill(s)
    _camera(bench, s, 160, 70, target=(8, 0, 0))


def kite_overload(bench):
    s = bench.ship('kite', 'kite-p', throttle=.4)
    drill(s, facing=30.0)
    _camera(bench, s, 300, 105)


def tech_row(bench):
    ships = [bench.ship('lasher', 'lasher', at=(0, 150, 0), throttle=.4),
             bench.ship('kite', 'kite-p', at=(0, 0, 0), throttle=.4),
             bench.ship('wolf-p', 'wolf-p', at=(0, -140, 0), throttle=.4)]
    for s, facing in zip(ships, (0.0, 30.0, 0.0)):
        drill(s, facing=facing)
    _camera(bench, ships[1], 520, 45)


TESTS = {
    'lasher-front': dict(
        builds=['lasher'], frames=DRILL,
        note='Lasher FRONT 210, low tech (red): raise, idle, three volleys as flux climbs, overload tear, '
             'shards and arcs, vent. 300 m.',
        setup=lasher_front),
    'kite-omni': dict(
        builds=['kite-p'], frames=DRILL,
        note='Kite (P) OMNI 150 turned 30 deg to the threat, midline (blue, rough): the same drill. 300 m.',
        setup=kite_omni),
    'lasher-overload': dict(
        builds=['lasher'], frames=OVERLOAD,
        note='Lasher overload close (160 m): the tear running, then the torn plasma pieces glowing at their rims '
             'and burning away from within as they drift off.',
        setup=lasher_overload),
    'kite-overload': dict(
        builds=['kite-p'], frames=OVERLOAD,
        note='Kite (P) overload at 300 m: the same beats on an omni shield in midline blue.',
        setup=kite_overload),
    'tech-row': dict(
        builds=['lasher', 'kite-p', 'wolf-p'], frames=[30, 39, 76, 95],
        note='Colour by tech, not faction: Lasher low tech, Kite (P) midline, Wolf (P) high tech (smooth). '
             'Idle, volley 1, flux .9, overload.',
        setup=tech_row),
}
