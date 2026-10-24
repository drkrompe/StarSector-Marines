"""The motion audit (owner, 2026-10-23: "make sure the Asharu session looks at all tracers,
explosions, and damage and makes sure they are flying right across the screen for all of the
scenes"). Run on a shot's kit once it is built for the whole shot (--kit-once): every effect the kit
made is read off its own animation, frame by frame through the shot, in the world the camera sees,
and checked against how that kind of thing has to move.

  rounds    the round bodies (corona, core, tracer): first shown at a muzzle on their fire frame
            (or on the shot's first frame, if fired before the cut); shown on every frame until gone;
            advancing every frame at a steady speed on a straight line; ending on a hull, out of
            frame, at the end of the shot, or burnt out at the end of their flight.
  missiles  bodies and motors: leave their own ship, advance every frame, no pop and no blink.
  flares    thrown by their ship: advance every frame, no pop and no blink.
  decoys    a seeker's burst on the flare it took, away from any hull: shown over unbroken frames,
            animated while shown.
  blasts    explosions, flashes, fireballs, shock rings, sparks: shown over unbroken frames, born on
            or at a hull, animated while shown (no held picture).
  debris    shards and debris: shown over unbroken frames, moving every frame.
  damage    fire, smoke, vents, scorch, breach marks: carried by the hull they are on (in her copy,
            so they ride her), shown over unbroken frames.

A world position is the object's own keyed location carried through its ship's copy space at that
frame (the copy is drawn at the ship's matrix x mpu), so a round re-keyed into the world by
world_rounds, a strike left on a target's plating and a flare thrown off a flank all read as the
camera will see them. Parented pieces (a turret's flash, a hull's fire on its frame) move with what
they hang on by construction, and are checked for continuity only.

The rows go to the job log; audit() returns (rows, failures). battle_blockout raises on failures
unless ASHARU_AUDIT_SOFT is set (the audit jobs set it so the frames still render for review).
"""
import math
import re

from mathutils import Vector

KINDS = [
    ('decoys', re.compile(r' missile decoy ')),     # a seeker's burst on the flare it took: in open space
    ('rounds', re.compile(r' (round(?! halo| light| trail)|tracer) .* f-?\d{3,}$')),
    ('missiles', re.compile(r'SWFX missile .*\b(body|motor|missile)\b')),
    ('flares', re.compile(r'\bflare\b(?! light)')),
    ('debris', re.compile(r'\b(debris|shard|shards|shrapnel)\b')),
    ('blasts', re.compile(r'\b(flash(?! light)|fireball|shock ring|shock|sparks?|embers|hit|burst)\b(?!.*\blight\b)')),
    ('damage', re.compile(r'\b(flame|smoke|vent|scorch|char|pock|fire|breach|stream)\b')),
]
BURNOUT_SLACK = 2          # frames short of the full flight that still count as burnt out
STEP_BAND = (.6, 1.6)      # a frame's step against the round's median step
STRAIGHT = .01             # deviation from the chord, as a share of its length
MUZZLE_TOL = (8.0, .15)    # metres: max(floor, share of the shooter's length); a turret swings its muzzle about


def kind_of(name):
    for kind, rx in KINDS:
        if rx.search(name):
            return kind
    return None


def _fc(core, o, path, i):
    fc = core.find_fcurve(o, path, i)
    return fc if fc is not None and len(fc.keyframe_points) else None


def shown_frames(core, o, lo, hi):
    vis = core.find_fcurve(o, 'hide_render')
    if vis is None or not len(vis.keyframe_points):
        return None if o.hide_render else list(range(lo, hi + 1))
    return [f for f in range(lo, hi + 1) if vis.evaluate(f) < .5]


def animated(core, o):
    """Anything about it that changes while it shows: keys or drivers on it, its data, its materials
    or their node trees (Blender 5 layered actions, through swfx_guns._fcurves), or a geometry-nodes
    modifier that reads the scene time."""
    import swfx_guns

    def live(idb):
        ad = getattr(idb, 'animation_data', None)
        if not ad:
            return False
        if len(ad.drivers):
            return True
        return any(fc.data_path not in ('hide_render', 'hide_viewport') for fc in swfx_guns._fcurves(idb))
    if live(o):
        return True
    mats = [s.material for s in getattr(o, 'material_slots', ()) if s.material is not None]
    ids = [getattr(o, 'data', None)] + mats + [m.node_tree for m in mats if getattr(m, 'node_tree', None)]
    if any(live(d) for d in ids if d is not None):
        return True
    for md in getattr(o, 'modifiers', ()):
        ng = getattr(md, 'node_group', None)
        if ng is not None and (live(ng) or any(n.bl_idname == 'GeometryNodeInputSceneTime' for n in ng.nodes)):
            return True
    return False


def audit(sfx, cam_view=None):
    """sfx: the shot's SceneFx, built once for the whole shot. cam_view(f) -> (loc, rotation
    quaternion, lens) of the shot's camera on scene frame f, for the out-of-frame test."""
    import swfx_core as core
    f_lo, f_hi = sfx.span
    fps = sfx.fps
    flight_max = round(sfx.MAX_FLIGHT_S * fps)
    owners = {}
    for key in sfx.placed:
        s = sfx.ship(key)
        if s is None:
            continue
        for coll in (s.fx, s.hull):
            if coll is None:
                continue
            for o in coll.all_objects:
                owners.setdefault(o, key)
    copy = {}

    def copy_at(key, f):
        if (key, f) not in copy:
            from mathutils import Matrix
            copy[(key, f)] = sfx.matrix_at(key, f) @ Matrix.Scale(sfx.placed[key]['mpu'], 4)
        return copy[(key, f)]

    def ships_near(p, f, share=.75):
        for key, pl in sfx.placed.items():
            if (sfx.matrix_at(key, f).translation - p).length < pl['length'] * share:
                return key
        return None

    def in_frame(p, f):
        if cam_view is None:
            return True
        loc, rot, lens = cam_view(f)
        c = rot.inverted() @ (p - loc)
        if c.z >= 0:
            return False
        half_w = 18.0 / lens
        half_h = half_w * 9 / 16
        return abs(c.x / -c.z) <= half_w * 1.02 and abs(c.y / -c.z) <= half_h * 1.02

    muzzles = {}

    def muzzle_points(key):
        if key not in muzzles:
            s = sfx.ship(key)
            pts = []
            for coll in (s.hull, s.fx) if s is not None else ():
                for o in coll.all_objects:
                    if o.type == 'EMPTY' and ' muzzle' in o.name:
                        m, p = o.matrix_basis.copy(), o.parent
                        n = o
                        while p is not None:
                            m = p.matrix_basis @ n.matrix_parent_inverse @ m
                            n, p = p, p.parent
                        pts.append(m.translation.copy())
            muzzles[key] = pts
        return muzzles[key]

    counts, fails = {}, []

    def fail(kind, o, f, why):
        fails.append(f'{kind} f{f} {o.name}: {why}')

    for o in list(sfx_objects(sfx)):
        kind = kind_of(o.name)
        if kind is None or o.type == 'EMPTY':     # a locator (a shield hit's pivot) draws nothing
            continue
        key = owners.get(o)
        lo_k = f_lo - flight_max
        shown = shown_frames(core, o, max(lo_k, f_lo), f_hi)
        if not shown:
            continue
        c = counts.setdefault(kind, [0, 0])
        c[0] += 1
        n_before = len(fails)
        if shown[-1] - shown[0] + 1 != len(shown):
            gap = next(f for a, f in zip(shown, shown[1:]) if f != a + 1)
            fail(kind, o, gap, f'blinks: shown {shown[0]}..{shown[-1]} with gaps')
        fcs = [_fc(core, o, 'location', i) for i in range(3)]
        keyed = all(fcs)
        birth = sfx._birth(o, core)
        if kind in ('rounds', 'missiles', 'flares'):
            if key is None:
                fail(kind, o, shown[0], 'in no ship\'s copy: cannot be placed in the world')
            elif o.parent is None:
                if birth is not None and shown[0] > max(birth, f_lo) + 1:
                    fail(kind, o, shown[0], f'pops in mid-flight (fired f{birth})')
                if len(shown) > 1 and not keyed:
                    fail(kind, o, shown[1], 'shown over several frames with no flight keys')
                if keyed and not o.get('world_space'):
                    fail(kind, o, shown[0], 'rides its ship: not re-keyed into the world')
                if keyed:
                    pts = [copy_at(key, f) @ Vector([fc.evaluate(f) for fc in fcs]) for f in shown]
                    steps = [(b - a).length for a, b in zip(pts, pts[1:])]
                    for f, st in zip(shown[1:], steps):
                        if st < 1e-3:
                            fail(kind, o, f, 'holds still')
                            break
                    if kind == 'rounds' and len(steps) >= 3:
                        med = sorted(steps)[len(steps) // 2]
                        for f, st in zip(shown[1:-1], steps[:-1]):
                            if med > 0 and not STEP_BAND[0] <= st / med <= STEP_BAND[1]:
                                fail(kind, o, f, f'jumps: step {st:.1f} m against {med:.1f} m')
                                break
                        chord = pts[-1] - pts[0]
                        if chord.length > 1e-3:
                            u = chord.normalized()
                            dev = max(((p - pts[0]) - u * (p - pts[0]).dot(u)).length for p in pts)
                            if dev > max(.5, STRAIGHT * chord.length):
                                fail(kind, o, shown[0], f'bends: {dev:.1f} m off a {chord.length:.0f} m line')
                    if kind == 'rounds' and birth is not None and birth >= f_lo:
                        b0 = Vector([fc.evaluate(birth) for fc in fcs])
                        mz = muzzle_points(key)
                        tol = max(MUZZLE_TOL[0], MUZZLE_TOL[1] * sfx.placed[key]['length'])
                        mpu = sfx.placed[key]['mpu']
                        if mz:
                            d = min((b0 - m).length for m in mz) * mpu
                            if d > tol:
                                fail(kind, o, birth, f'born {d:.1f} m from any muzzle (tolerance {tol:.1f} m)')
                    if kind == 'rounds':
                        end, age = shown[-1], shown[-1] - (birth if birth is not None else shown[0])
                        p_end = pts[-1]
                        ok = (end >= f_hi or age >= flight_max - BURNOUT_SLACK
                              or ships_near(p_end, end) is not None
                              or not in_frame(p_end, end) or not in_frame(p_end + (p_end - pts[-2] if len(pts) > 1
                                                                                    else Vector()), end + 1))
                        if not ok:
                            fail(kind, o, end, f'vanishes mid-air, in frame, {age} frames into its flight')
        elif kind == 'decoys':
            if len(shown) > 1 and not animated(core, o):
                fail(kind, o, shown[1], 'a held picture: nothing about it changes while it shows')
        elif kind in ('blasts', 'debris'):
            if key is not None and o.parent is None:
                p = copy_at(key, shown[0]) @ (Vector([fc.evaluate(shown[0]) for fc in fcs]) if keyed
                                              else o.matrix_basis.translation)
                if kind == 'blasts' and ships_near(p, shown[0], 1.2) is None:
                    k_, d_ = min(((k, (sfx.matrix_at(k, shown[0]).translation - p).length)
                                  for k in sfx.placed), key=lambda x: x[1])
                    fail(kind, o, shown[0], f'born away from any hull: nearest {k_} at {d_:.0f} m '
                                            f'(her length {sfx.placed[k_]["length"]:.0f} m)')
            if len(shown) > 1 and not animated(core, o):
                fail(kind, o, shown[1], 'a held picture: nothing about it changes while it shows')
            if kind == 'debris' and keyed and len(shown) > 1:
                for f in shown[1:]:
                    if sum((fc.evaluate(f) - fc.evaluate(f - 1)) ** 2 for fc in fcs) < 1e-6:
                        fail(kind, o, f, 'holds still')
                        break
        elif kind == 'damage':
            if key is None:
                fail(kind, o, shown[0], 'on no hull: cannot ride her')
            elif o.get('world_space'):
                fail(kind, o, shown[0], 'held in the world: it would not ride her hull')
        if len(fails) > n_before:
            c[1] += 1
    rows = [f'motion audit {k}: {n} checked, {bad} failed' for k, (n, bad) in sorted(counts.items())]
    rows += [f'motion audit FAIL {x}' for x in fails[:60]]
    if len(fails) > 60:
        rows.append(f'motion audit ... {len(fails) - 60} more')
    return rows, fails


def sfx_objects(sfx):
    import bpy
    return [o for o in bpy.data.objects if o not in sfx.before and o.name.startswith('SWFX')]
