"""Bench tests for the explosion sweep (owner, 2026-10-21: no orb explosions): every burst the kit
keys, side by side in open space, so their shapes and sizes compare at a glance.

Each row is keyed on one host ship's own copy (a Lasher parked out of frame below the row), all
bursts on the same frame, each at its own spot along world +X, thrown up and a little toward the
camera. The bursts are the production calls, not stand-ins: `swfx_impacts.kinetic` / `he` /
`breach`, `swfx_missile_paths.burst` with the game profiles, `swfx_pd`'s flak, and an explosion
anchor through `swfx_fire.build_explosion(style='jagged')`.

    small   left to right: kinetic round, HE shell, breach, Harpoon ignition, Harpoon kill pop
    large   left to right: flak burst, Harpoon hit, Harpoon on a shield, decoyed Swarmer,
            torpedo hit, cook-off blast (fire group, 30 m)
"""
import random

FIRE_AT = 4
FRAMES = [5, 7, 10, 16]
SMALL = ['kinetic', 'he', 'breach', 'ignition', 'pop']
LARGE = ['flak', 'harpoon-hit', 'shield-hit', 'decoy', 'torpedo', 'cookoff']
SMALL_STEP, LARGE_STEP = 14.0, 72.0
THROW = (0.0, -.35, .94)          # world direction the bursts throw along: up, a little toward camera


def _row(bench, kinds, step):
    from mathutils import Vector
    import swfx_core
    import swfx_missile_paths as mp
    host = bench.ship('host', 'lasher', at=(step * (len(kinds) - 1) / 2, 0, -900), heading=0, throttle=0.0)
    space = mp.space_of(host)
    inv = space.inverted()
    local = host.matrix.inverted()
    throw = Vector(THROW).normalized()
    made = {}
    with host.kit() as (ctx, kit):
        import swfx_burst
        import swfx_fire
        import swfx_impacts as imp
        import swfx_pd as pd
        mpu = swfx_core.METRES_PER_UNIT
        coll = ctx.collection('Guns')
        axis_c = (inv.to_3x3() @ throw).normalized()
        normal_m = (local.to_3x3() @ throw).normalized()
        for i, kind in enumerate(kinds):
            world = Vector((step * i, 0, 0))
            here = inv @ world
            at_m = local @ world
            rnd = random.Random(f'bench-burst|{kind}')
            if kind == 'kinetic':
                r = imp.kinetic(ctx, FIRE_AT, at_m, normal_m)
            elif kind == 'he':
                r = imp.he(ctx, FIRE_AT, at_m, normal_m)
            elif kind == 'breach':
                r = imp.breach(ctx, FIRE_AT, at_m, normal_m, vent_m=12.0)
            elif kind == 'ignition':
                r = mp.burst(ctx, coll, mp.PROFILES['harpoon'], 'ignition', 'bench', here, FIRE_AT, mpu, axis_c, rnd)
            elif kind == 'pop':
                r = mp.burst(ctx, coll, mp.PROFILES['harpoon'], 'pop', 'bench', here, FIRE_AT, mpu, axis_c, rnd)
            elif kind == 'flak':
                r = pd.jagged_burst(ctx, coll, 'flak bench', here, FIRE_AT, mpu, rnd, pd.FLAK['flash'], light_w=1.5e4)
            elif kind == 'harpoon-hit':
                r = mp.burst(ctx, coll, mp.PROFILES['harpoon'], 'hit', 'bench', here, FIRE_AT, mpu, axis_c, rnd)
            elif kind == 'shield-hit':
                r = mp.burst(ctx, coll, mp.PROFILES['harpoon'], 'shield', 'bench', here, FIRE_AT, mpu, axis_c, rnd)
            elif kind == 'decoy':
                r = mp.burst(ctx, coll, mp.PROFILES['swarmer'], 'decoy', 'bench', here, FIRE_AT, mpu, None, rnd)
            elif kind == 'torpedo':
                r = mp.burst(ctx, coll, mp.PROFILES['torpedo'], 'hit', 'bench', here, FIRE_AT, mpu, axis_c, rnd)
            elif kind == 'cookoff':
                swfx_fire._knobs(ctx)
                ctx.controls['fir_exp_frame'] = float(FIRE_AT)
                fcoll = ctx.collection(swfx_fire.GROUP)
                a = swfx_core.empty('SWFX bench explosion anchor', fcoll, size=1.0)
                a.location = here
                a['size_m'] = 30.0
                a.hide_render = True
                r = swfx_fire.build_explosion(ctx, fcoll, a, style='jagged')
            else:
                raise ValueError(kind)
            made[kind] = r if isinstance(r, dict) else [o.name for o in r]
    for kind in kinds:
        n = len(made[kind].get('objects', [])) if isinstance(made[kind], dict) else len(made[kind])
        print(f'BENCH burst {kind}: {n} objects', flush=True)
    return host


def small(bench):
    """The small bursts from 75 m: kinetic, HE, breach, Harpoon ignition, Harpoon kill pop."""
    _row(bench, SMALL, SMALL_STEP)
    cx = SMALL_STEP * (len(SMALL) - 1) / 2
    bench.camera((cx, -75, 14), (cx, 0, 6), 35)


def large(bench):
    """The large bursts from 470 m: flak, Harpoon hit, shield hit, decoy, torpedo, cook-off."""
    _row(bench, LARGE, LARGE_STEP)
    cx = LARGE_STEP * (len(LARGE) - 1) / 2
    bench.camera((cx, -470, 60), (cx, 0, 25), 35)


TESTS = {
    'small': dict(
        builds=['lasher'], frames=FRAMES,
        note='No orbs. Left to right: kinetic round, HE shell, breach, Harpoon ignition, Harpoon kill pop.',
        setup=small),
    'large': dict(
        builds=['lasher'], frames=FRAMES,
        note='No orbs. Left to right: flak, Harpoon hit, Harpoon on a shield, decoyed Swarmer, torpedo hit, cook-off blast.',
        setup=large),
}
