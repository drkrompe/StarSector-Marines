"""Bench tests for the heavy guns (owner, 2026-10-23: "Cannons and hellbores need work to make them
heavier pulsed firing and their in flight projectile needs to convey that").

Each shot of a Hellbore or an autocannon is an event: a hard short blast with a pressure ring,
lingering smoke, the barrel kicking back, a light pulse; and the round is a short hot slug with an
ember trail rather than a long thin streak. Close views watch the muzzle and the recoil, flight
views the slug. Ships are placed with own_hull=True: recoil keys the barrel, a hull object, so a
shared hull would kick on every copy (as aim does).

  manticore-p  t1 Hellbore (modelled), and a Mark IX on the same mount (the variant's main gun)
  hound-p      the Hellbore's target in hellbore-hit (swfx_impacts.hellbore: its own jagged HE hit)

Every test ends with swfx_lint.orbs over its frames: a BENCH ORB line is an emissive effect that
would read as a ball of light at the bench camera (owner: no orbs). None is the pass.
"""
from bench_guns import muzzle


def _v(*c):
    from mathutils import Vector
    return Vector(c)


def lint(bench, frames, label):
    """Print every effect that would read as an orb on these frames (swfx_lint.orbs)."""
    import ship_fx
    import swfx_lint
    ship_fx.refresh(bench.scene)
    rows = swfx_lint.orbs(bench.scene, frames=frames, camera=bench.cam)
    for r in rows:
        print(f'BENCH ORB {label} {r}', flush=True)
    print(f'BENCH lint {label}: {len(rows)} orb(s)', flush=True)


def _manticore(bench, weapon, seconds):
    m = bench.ship('manticore', 'manticore-p', throttle=.3, own_hull=True)
    with m.kit() as (ctx, k):
        k.guns.arm(ctx, 1, weapon)
        k.guns.fire_weapon(ctx, 1, 2, seconds)
    p, _ = muzzle(m, 1, 'barrel 1')
    return p


def hellbore_close(bench):
    """The Hellbore's shot at the muzzle: blast, ring, recoil, smoke."""
    p = _manticore(bench, 'hellbore', 3.2)
    bench.camera(tuple(p + _v(4, -40, 14)), tuple(p + _v(6, 0, 0)), 35)
    lint(bench, TESTS['hellbore-close']['frames'], 'hellbore-close')


def hellbore_flight(bench):
    """The Hellbore's slug in flight, side-on over its first 250 m, and the second shot 3 s on."""
    p = _manticore(bench, 'hellbore', 3.2)
    bench.camera(tuple(p + _v(110, -230, 50)), tuple(p + _v(110, 0, 0)), 30)
    lint(bench, TESTS['hellbore-flight']['frames'], 'hellbore-flight')


def mark9_close(bench):
    """The Mark IX's four-round slam at the muzzle: four kicks, four blasts."""
    p = _manticore(bench, 'mark9', .5)
    bench.camera(tuple(p + _v(4, -40, 14)), tuple(p + _v(6, 0, 0)), 35)
    lint(bench, TESTS['mark9-close']['frames'], 'mark9-close')


def mark9_flight(bench):
    """The Mark IX's shells in flight, side-on."""
    p = _manticore(bench, 'mark9', .5)
    bench.camera(tuple(p + _v(110, -230, 50)), tuple(p + _v(110, 0, 0)), 30)
    lint(bench, TESTS['mark9-flight']['frames'], 'mark9-flight')


def hellbore_hit(bench):
    """The Hellbore on a Hound (P) 180 m ahead: the slug in flight, then its hit, a large jagged HE
    burst (swfx_impacts.hellbore) thrown out of her side."""
    import importlib
    import swfx_impacts
    from bench_guns import to_copy
    imp = importlib.reload(swfx_impacts)
    m = bench.ship('manticore', 'manticore-p', throttle=.3, own_hull=True)
    p, d = muzzle(m, 1, 'barrel 1')
    t = bench.ship('target', 'hound-p', at=tuple(p + d * 185 - _v(0, 0, 4)), heading=90, throttle=.3)
    bench.bpy.context.view_layer.update()
    inv = t.matrix.inverted()        # ray_to_hull works in the target's own frame
    got = imp.ray_to_hull(bench.scene, t.matrix, tuple(inv @ (p + d * 30)), tuple(inv @ (p + d * 400)))
    aim = t.matrix @ _v(*got[0]) if got else p + d * 180
    with m.kit() as (ctx, k):
        k.guns.arm(ctx, 1, 'hellbore')
        recs = k.guns.fire_weapon(ctx, 1, 2, .1, target=to_copy(m, aim))
    hit = recs[0].get('hit_frame') if recs else None
    print('BENCH hellbore-hit fired', [(r['frame'], r.get('hit_frame')) for r in recs], 'ray', bool(got), flush=True)
    if got and hit:
        with t.kit() as (ctx, k):
            imp.hellbore(ctx, hit, *got)
    mid = (p + aim) / 2
    bench.camera(tuple(mid + _v(0, -190, 45)), tuple(mid + _v(25, 0, 0)), 35)
    lint(bench, TESTS['hellbore-hit']['frames'], 'hellbore-hit')


def decoy_flares(bench):
    """The Hound (P)'s decoy flares as scene_fx.flares throws them (glare 0.6 m, faint grey trail,
    4 kW light): small bright points in thin smoke that breaks up as it disperses, not grey rods."""
    import swfx_missile_paths as mp
    import swfx_pd as pd
    h = bench.ship('hound', 'hound-p', throttle=.4)
    anchors = pd.flare_anchors(h)
    fl = pd.plan_flares(anchors, 2)
    with h.kit() as (ctx, k):
        pd.key_flares(ctx, fl, mp.space_of(h), h.mpu, glare_m=.6, light_w=4.0e3,
                      trail=dict(trail_glow=0.0, trail_alpha=.3, trail_r_m=.45, trail_spread=1.2,
                                 trail=(.55, .55, .55)))
    print('BENCH decoy-flares', len(anchors), 'anchors,', len(fl), 'flares', flush=True)
    bench.camera(tuple(bench.at(h, (-30, -110, 45))), tuple(bench.at(h, (-25, 0, 0))), 35)
    lint(bench, TESTS['decoy-flares']['frames'], 'decoy-flares')


TESTS = {
    'hellbore-close': dict(builds=['manticore-p'], frames=[2, 3, 6, 14], setup=hellbore_close,
                           note='Hellbore at the muzzle: blast (2), ring and kick (3), recoil and smoke (6, 14).'),
    'hellbore-flight': dict(builds=['manticore-p'], frames=[12, 24, 40, 76], setup=hellbore_flight,
                            note='Hellbore slug in flight side-on (12-40), second shot 3 s on (76).'),
    'mark9-close': dict(builds=['manticore-p'], frames=[2, 3, 7, 10], setup=mark9_close,
                        note='Mark IX (stand-in on the Hellbore mount) at the muzzle: the four-round slam.'),
    'mark9-flight': dict(builds=['manticore-p'], frames=[8, 14, 22, 34], setup=mark9_flight,
                         note='Mark IX shells in flight side-on.'),
    'hellbore-hit': dict(builds=['manticore-p', 'hound-p'], frames=[18, 31, 33, 38], setup=hellbore_hit,
                         note='Hellbore on a Hound (P) 185 m ahead: slug in flight, then its jagged HE hit.'),
    'decoy-flares': dict(builds=['hound-p'], frames=[8, 16, 26, 40], setup=decoy_flares,
                         note="Hound (P) decoy flares at scene_fx's settings: bright points, thin torn smoke."),
}
