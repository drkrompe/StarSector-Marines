"""Bench tests for the Borers' mining laser (swfx_mining, S8-S9): the beam close up, where the
battle's wide camera shows it a few pixels long.

A Borer skims under a Colossus Mk.III's belly, her cut raking back and forth along the plating
behind her, as K4_SKIM flies it. The laser is built afresh for each frame from that frame's time
alone (a frame-change handler), as battle_blockout builds it for each frame of a shot.
"""
import math

RAKE_M, PERIOD_S, LAG_S, TRAIL_S = 25.0, 3.0, .35, .35
BELOW_M = 40.0


def _rake(bench, steady):
    import sys

    import bpy
    from mathutils import Vector
    vfx = str(bench_dir() / 'second-wind-vfx')
    if vfx not in sys.path:
        sys.path.insert(0, vfx)
    import swfx_mining
    import zlib
    mk3 = bench.ship('colossus3', 'colossus-mk3', at=(0, 0, 0), heading=0)
    x_of = lambda t: RAKE_M * math.sin(2 * math.pi * t / PERIOD_S)
    borer = bench.ship('borer-a', 'borer', at=(x_of(1 / 24 + LAG_S), 0, -BELOW_M), heading=0, pitch=80)
    bench.camera((70, -95, -75), (0, 0, -6), 35)
    scene = bench.scene
    coll = bpy.data.collections.new('Mining bench')
    scene.collection.children.link(coll)
    names = {o.name for o in mk3.hull.all_objects}

    def cut(t):
        """The cut on the belly under x_of(t): straight up onto her plating."""
        dg = bpy.context.evaluated_depsgraph_get()
        start, ray = Vector((x_of(t), 0, -BELOW_M - 5)), Vector((0, 0, 1))
        for _ in range(16):
            hit, loc, nrm, _i, ob, _m = scene.ray_cast(dg, start, ray, distance=200.0)
            if not hit:
                return Vector((x_of(t), 0, -8)), Vector((0, 0, -1))
            if ob.name in names:
                return loc, nrm
            start = loc + ray * .05
        return Vector((x_of(t), 0, -8)), Vector((0, 0, -1))

    def build(scene_, *_):
        for o in list(coll.objects):
            bpy.data.objects.remove(o)
        t = scene_.frame_current / 24
        d = Vector((x_of(t + LAG_S), 0, -BELOW_M))
        for e in bpy.data.objects:      # the Borer flies: her placement follows her skim
            if e.name in ('Ship | borer-a', 'Ship fx | borer-a'):
                e.location = d
        loc, nrm = cut(t) if not steady else cut(1 / 24)
        emit = d + (loc - d).normalized() * 3.6 * .55
        seed = zlib.crc32(b'borer-a') % 997
        if steady:
            swfx_mining.laser('Mining borer-a', emit, loc, nrm, coll, lights=coll, seed=seed, width_m=.3)
        else:
            trail = [cut(t - TRAIL_S + TRAIL_S * i / 11)[0] for i in range(12)]
            swfx_mining.laser('Mining borer-a', emit, loc, nrm, coll, lights=coll, seed=seed, width_m=.12,
                              halo=2.5, cut_m=.3, pulse=dict(t=t), trail=trail)
        print(f'BENCH mining {"steady" if steady else "rake"} f{scene_.frame_current}: '
              f'{(loc - emit).length:.1f} m to the cut at {tuple(round(c, 1) for c in loc)}', flush=True)

    bpy.app.handlers.frame_change_pre.clear()
    bpy.app.handlers.frame_change_pre.append(build)


def bench_dir():
    from pathlib import Path
    return Path(__file__).resolve().parent.parent


TESTS = {
    'steady': dict(
        builds=['colossus-mk3', 'borer'], frames=[1, 2, 3, 4],
        note='Before: the steady beam (core .3 m, halo 4x) from a Borer under the Mk.III\'s belly, on one point.',
        setup=lambda bench: _rake(bench, True)),
    'rake': dict(
        builds=['colossus-mk3', 'borer'], frames=[1, 2, 3, 4],
        note='After: the thin pulsed beam (core .12 m, halo 2.5x), packets running Borer to hull, the cut '
             'raking along the belly with a cooling seam behind it. Four consecutive frames: the packets move.',
        setup=lambda bench: _rake(bench, False)),
}
