"""Bench tests for W5 impacts and the W6 cook-off tie-in (asharu-effects-plan.md).

Every impact is placed at runtime by swfx_impacts on the ship it hits, through that ship's own
copy (`with ship.kit() as (ctx, kit)`), at the point where the round's line meets the hull.

COOKOFF is the W6 hand-over: what causes the Phaeton's cook-off, as cue data in the Phaeton
wreck's own frame (metres) and in seconds against its break_frame. The bench test below plays
it; battle_blockout's integration owner reads the same table for k3 (see `k3_cue`).
"""

# The Phaeton wreck's strike point (phaeton-cinematic export_ship STRIKE, units x 0.33 m/unit):
# the missiles open tank 2 here and the main blast's anchor sits on it.
PHAETON_STRIKE_M = (14.52, 1.32, 1.32)

# Light MG (the Lashers): 5-round bursts 0.1 s apart, then a 0.4 s pause (asharu-effects.md).
MG_ROUND_S, MG_PAUSE_S, MG_BURST = .1, .4, 5

COOKOFF = {
    # The lead Lasher's light MGs walk up the port side onto tank 2 in two bursts...
    'mg': [dict(kind='stream', hit='kinetic', t_s=-1.25, over_s=.4, every=2.4, seed=3,
                aim=(((-10, 60, 30), (-6, 2, 2)), ((6, 60, 30), (10, 2, 2)))),
           dict(kind='stream', hit='kinetic', t_s=-.6, over_s=.4, every=2.4, seed=4,
                aim=(((8, 60, 30), (11, 2, 2)), ((16, 60, 30), (15, 2, 2))))],
    # ...and the Manticore (P)'s Harpoon salvo (a pod's four, 0.25 s apart) comes in on her: two
    # warheads burst on the plating, the third opens tank 2 at break_frame, the fourth is lost
    # short to point defence (W2's to show). Harpoon explosions are the game's blue.
    'harpoons': [dict(kind='he', t_s=-.5, size_m=3.5, flash_colour=(.39, .65, 1.0),
                      aim=((26, 70, 40), (18, 1, 3))),
                 dict(kind='he', t_s=-.25, size_m=3.5, flash_colour=(.39, .65, 1.0),
                      aim=((4, 70, 40), (11, 1, 3))),
                 dict(kind='he', t_s=0.0, size_m=4.0, flash_colour=(.39, .65, 1.0),
                      aim=((14.5, 70, 40), PHAETON_STRIKE_M))],
}


def cookoff_entries(break_frame):
    """COOKOFF as swfx_impacts.apply_cue entries for a scene whose break_frame is known."""
    out = []
    for group in COOKOFF.values():
        for c in group:
            c = dict(c)
            c['frame'] = break_frame + round(c.pop('t_s') * 24)
            out.append(c)
    return out


def k3_cue(frame_now, break_ago_s=2.0):
    """The k3 still's Phaeton cue (break_frame break_ago_s before frame_now): the impacts' marks
    still glowing on the wreck two seconds on, for `cues={'phaeton': {'impacts': ...}}`."""
    return cookoff_entries(frame_now - round(break_ago_s * 24))


def _imp():
    import importlib
    import swfx_impacts
    return importlib.reload(swfx_impacts)


def framing(bench, ship, recs, label):
    """--check's view of where the hits fall in the frame: (x, y) in 0..1 and the share inside."""
    from bpy_extras.object_utils import world_to_camera_view
    from mathutils import Vector
    bench.bpy.context.view_layer.update()
    pts = [world_to_camera_view(bench.scene, bench.cam, ship.matrix @ Vector(r['at_m'])) for r in recs]
    inside = [p for p in pts if 0 <= p.x <= 1 and 0 <= p.y <= 1 and p.z > 0]
    print(f'BENCH framing {label}: {len(inside)}/{len(pts)} in frame',
          [(round(p.x, 2), round(p.y, 2)) for p in pts], flush=True)


def liveness(bench, ship, frames, label):
    """--check's proof the impacts evaluate: per frame, the impacts' flash-light watts, the live
    spark / spall / debris points, and the breach fire's light watts."""
    import ship_fx
    bpy = bench.bpy
    ship_fx.refresh(bench.scene)
    for f in frames:
        bench.scene.frame_set(f)
        dg = bpy.context.evaluated_depsgraph_get()
        flash = fire = 0.0
        pts = {}
        for o in ship.ids:
            if not isinstance(o, bpy.types.Object):
                continue
            n = ship_fx.base_name(o.name)
            if o.type == 'LIGHT' and n.startswith('SWFX impact flash light'):
                flash += o.evaluated_get(dg).data.energy
            elif o.type == 'LIGHT' and n.startswith('SWFX fire light | SWFX impact'):
                fire += o.evaluated_get(dg).data.energy
            elif o.type == 'MESH' and n.startswith(('SWFX impact kinetic spark', 'SWFX impact he sp',
                                                    'SWFX impact debris')):
                kind = n.split(' | ')[0][len('SWFX impact '):]
                pts[kind] = pts.get(kind, 0) + len(o.evaluated_get(dg).data.vertices) // 8
        print(f'BENCH live {label} f{f}: flash {flash:.0f} fire {fire:.0f} pieces {pts}', flush=True)


def mg_bursts(first, bursts):
    """Frames of a light MG's rounds: `bursts` five-round bursts from `first`."""
    out, f = [], float(first)
    for _ in range(bursts):
        out.append((f, f + (MG_BURST - 1) * MG_ROUND_S * 24))
        f += ((MG_BURST - 1) * MG_ROUND_S + MG_PAUSE_S + MG_ROUND_S) * 24
    return out


def armour_breach(bench):
    """W5's two beats on a Buffalo Mk.II stand-in (the Tarsus, 94 m, no shield): the armour soaks
    a light MG stream (sparks, pocks) and then light assault gun HE (orange flashes, spall,
    craters); then a breach opens where the HE pounded, burns and vents, and plate fragments
    tumble clear."""
    bench.bpy.context.view_layer.update()
    imp = _imp()
    t = bench.ship('target', 'tarsus', at=(0, 0, 0), heading=0, throttle=.4)
    bench.bpy.context.view_layer.update()
    port = lambda x, z: ((x, 80, z + 6), (x, 0, z))        # noqa: E731  a round from the port beam
    recs = []
    with t.kit() as (ctx, kit):
        for i, (a, b) in enumerate(mg_bursts(6, 3)):         # beat 1: the MG stream walks aft to fore
            x0, x1 = -24 + i * 12, -16 + i * 12
            recs += imp.stream(ctx, 'kinetic', a, b, MG_ROUND_S * 24, None, None, None, seed=i, matrix=t.matrix,
                               aim=(port(x0, 2), port(x1, 3)))
        for k, f in enumerate((44, 50, 56)):                  # light assault gun, 2 s apart in game, closer here
            got = imp.ray_to_hull(bench.scene, t.matrix, *port(18 + 4 * k, 1 + k))
            if got:
                recs.append(imp.he(ctx, f, *got))
        got = imp.ray_to_hull(bench.scene, t.matrix, *port(24, 2))   # beat 2: she breaks open
        if got:
            recs.append(imp.breach(ctx, 62, *got, size_m=3.5))
            recs.append(imp.debris(ctx, 62, *got))
    print('BENCH impacts', len(recs), 'placed:',
          {k: sum(1 for r in recs if r['kind'] == k) for k in ('kinetic', 'he', 'debris')},
          'breach retimed', [r['breach']['drivers_retimed'] for r in recs if 'breach' in r], flush=True)
    bench.camera((40, 175, 45), (2, 0, 0), 50)
    framing(bench, t, recs, 'armour-breach')
    liveness(bench, t, TESTS['armour-breach']['frames'], 'armour-breach')


def cookoff_caused(bench):
    """W6: the accepted Phaeton cook-off, caused. The lead Lasher's MG fire walks onto tank 2,
    two Harpoon warheads burst on the plating and the third opens the tank at break_frame,
    which is where the wreck's own main blast already sits."""
    bench.bpy.context.view_layer.update()
    imp = _imp()
    p = bench.ship('phaeton', 'phaeton-wreck', at=(0, 0, 0), heading=0)
    p.set('break_frame', BREAK)
    bench.bpy.context.view_layer.update()
    with p.kit() as (ctx, kit):
        recs = imp.apply_cue(ctx, cookoff_entries(BREAK), matrix=p.matrix)
    print('BENCH cook-off cause', [(r['kind'], r['frame'], r['at_m']) for r in recs], flush=True)
    bench.camera((95, 150, 85), (8, 0, 0), 50)
    # The cook-off fills this framing with volumes; coarser volume steps keep the light job in
    # its 30 minutes (k3, with the wreck 1.3 km off, took ~10 min a frame at full step rate).
    bench.scene.cycles.volume_step_rate = 2.0
    bench.scene.cycles.volume_max_steps = 256
    framing(bench, p, recs, 'cookoff-caused')
    liveness(bench, p, TESTS['cookoff-caused']['frames'], 'cookoff-caused')


BREAK = 30

TESTS = {
    'armour-breach': dict(
        builds=['tarsus'], frames=[8, 14, 30, 45, 51, 63, 72, 90],
        note='Beat 1, armour soaks it: MG sparks and glowing pocks (8-30), HE flashes and craters (45, 51). '
             'Beat 2, she breaks up: breach blast and debris (63), burning and venting (72, 90).',
        setup=armour_breach),
    'cookoff-caused': dict(
        builds=['phaeton-wreck'], frames=[BREAK - 6, BREAK + 1, BREAK + 12],
        note='Cause then effect: MG sparks and the first Harpoon on tank 2 (24); the third Harpoon opens '
             'the tank at break_frame (31); the cook-off under way, the strike marks still glowing (42).',
        setup=cookoff_caused),
}
