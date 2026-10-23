"""Bench tests for the heavy guns (owner, 2026-10-23: "Cannons and hellbores need work to make them
heavier pulsed firing and their in flight projectile needs to convey that").

Each shot of a Hellbore or an autocannon is an event: a hard short blast with a pressure ring,
lingering smoke, the barrel kicking back, a light pulse; and the round is a short hot slug with an
ember trail rather than a long thin streak. Close views watch the muzzle and the recoil, flight
views the slug. Ships are placed with own_hull=True: recoil keys the barrel, a hull object, so a
shared hull would kick on every copy (as aim does).

  manticore-p  t1 Hellbore (modelled), and a Mark IX on the same mount (the variant's main gun)
"""
from bench_guns import muzzle


def _v(*c):
    from mathutils import Vector
    return Vector(c)


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


def hellbore_flight(bench):
    """The Hellbore's slug in flight, side-on over its first 250 m, and the second shot 3 s on."""
    p = _manticore(bench, 'hellbore', 3.2)
    bench.camera(tuple(p + _v(110, -230, 50)), tuple(p + _v(110, 0, 0)), 30)


def mark9_close(bench):
    """The Mark IX's four-round slam at the muzzle: four kicks, four blasts."""
    p = _manticore(bench, 'mark9', .5)
    bench.camera(tuple(p + _v(4, -40, 14)), tuple(p + _v(6, 0, 0)), 35)


def mark9_flight(bench):
    """The Mark IX's shells in flight, side-on."""
    p = _manticore(bench, 'mark9', .5)
    bench.camera(tuple(p + _v(110, -230, 50)), tuple(p + _v(110, 0, 0)), 30)


TESTS = {
    'hellbore-close': dict(builds=['manticore-p'], frames=[2, 3, 6, 14], setup=hellbore_close,
                           note='Hellbore at the muzzle: blast (2), ring and kick (3), recoil and smoke (6, 14).'),
    'hellbore-flight': dict(builds=['manticore-p'], frames=[12, 24, 40, 76], setup=hellbore_flight,
                            note='Hellbore slug in flight side-on (12-40), second shot 3 s on (76).'),
    'mark9-close': dict(builds=['manticore-p'], frames=[2, 3, 7, 10], setup=mark9_close,
                        note='Mark IX (stand-in on the Hellbore mount) at the muzzle: the four-round slam.'),
    'mark9-flight': dict(builds=['manticore-p'], frames=[8, 14, 22, 34], setup=mark9_flight,
                         note='Mark IX shells in flight side-on.'),
}
