"""Aim the Paragon's weapons the way the game allows: inside the slot's arc,
inside the turret's elevation limits, and only along a line clear of the ship.

A shot or a review imports this beside the appended asset:

    import aim
    shots = aim.engage(man, target)          # trains every turret that can bear
    for b in man['beams']: ...               # fire the ids in shots['firing']

`man` is this folder's manifest.json. Every rule here is game data or
geometry, never a per-shot exception:
- arc: vanilla's slot arc (`angle_deg` +/- `arc_deg`/2, in the hull's plane,
  measured from the slot), so an aft turret never swings forward;
- elevation: the turret kit's `elevation_min_deg`/`elevation_max_deg` on the
  elevation empty;
- clear line: a ray from the muzzle to the target meets no part of the ship
  but the weapon's own (`swfx_beams.clear_line`). A beam never cuts the hull;
- role: point defence (the PD lasers, vanilla's PD hint) engages missiles and
  fighters inside its range (`point_defense`), never the main target
  (`engage`).
"""
import math
import sys
from pathlib import Path

import bpy
from mathutils import Matrix, Vector

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'second-wind-vfx'))
import swfx_beams  # noqa: E402

AIM_TOLERANCE_DEG = 1.0     # a trained turret must be on the target to this
HARDPOINT_TOLERANCE_DEG = 6.0
THREAT_RADIUS = 6.0          # a missile or fighter's own body, not an obstruction


def upd():
    bpy.context.view_layer.update()


def signed(d, w, k):
    """Angle turning d onto w about axis k (both projected off k)."""
    d, w = d - k * d.dot(k), w - k * w.dot(k)
    if d.length < 1e-6 or w.length < 1e-6:
        return 0.0
    return math.atan2(k.dot(d.cross(w)), d.dot(w))


def hull_local(man, point):
    root = bpy.data.objects[man['root']]
    return root.matrix_world.inverted() @ Vector(point)


def in_arc(man, slot, target):
    """Is the target inside the slot's arc (hull plane, from the slot)?"""
    t = hull_local(man, target)
    az = math.degrees(math.atan2(t.y - slot['location'][1], t.x - slot['location'][0]))
    off = (az - slot['angle_deg'] + 180) % 360 - 180
    return abs(off) <= slot['arc_deg'] / 2 + 1e-6, round(off, 1)


def train(slot, target):
    """Point a turret's first muzzle at target: yaw the pivot about its local
    Z, then pitch the elevation about its local Y within its limits."""
    piv = bpy.data.objects[slot['pivot']]
    ele = bpy.data.objects[slot['elevation']]
    muz = bpy.data.objects[slot['muzzles'][0]]
    lo = math.radians(ele.get('elevation_min_deg', -10))
    hi = math.radians(ele.get('elevation_max_deg', 60))
    pitch = 0.0
    rest = ele.matrix_basis.copy()
    for _ in range(4):
        upd()
        d = muz.matrix_world.to_3x3() @ Vector((1, 0, 0))
        w = target - muz.matrix_world.translation
        k = (piv.matrix_world.to_3x3() @ Vector((0, 0, 1))).normalized()
        piv.matrix_basis = piv.matrix_basis @ Matrix.Rotation(signed(d, w, k), 4, 'Z')
        upd()
        d = muz.matrix_world.to_3x3() @ Vector((1, 0, 0))
        w = target - muz.matrix_world.translation
        k = (ele.matrix_world.to_3x3() @ Vector((0, 1, 0))).normalized()
        # Negative rotation about local Y lifts, so pitch counts upward as -angle.
        pitch = min(hi, max(lo, pitch - signed(d, w, k)))
        ele.matrix_basis = rest @ Matrix.Rotation(-pitch, 4, 'Y')
    upd()


def _can_fire(man, s, b, muz, target, short=0.0):
    """(ok, reason) for a beam whose turret is already trained."""
    fire = (muz.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized()
    to = target - muz.matrix_world.translation
    ok, off = in_arc(man, s, target)
    if not ok:
        return False, f'outside arc ({off:+.0f} deg of {s["angle_deg"]} +/- {s["arc_deg"] / 2:.0f})'
    tol = AIM_TOLERANCE_DEG if b['mount'] == 'TURRET' else HARDPOINT_TOLERANCE_DEG
    miss = math.degrees(fire.angle(to))
    if miss > tol:
        return False, f'cannot lay on target ({miss:.1f} deg off: elevation limit)'
    clear, blocker = swfx_beams.clear_line(muz.matrix_world.translation, target, ignore=(f"Weapon {b['slot']}",),
                                           short=short)
    if not clear:
        return False, f'line blocked by {blocker}'
    return True, None


def point_defense(man, threats):
    """Each PD beam takes the nearest threat (incoming missile or fighter, world
    points) inside its range, its arc and a clear line, trains on it and fires.
    Returns {'firing': {beam id: threat index}, 'held': {id: reason},
    'length': {id: hull units}}. A PD laser with no threat in reach holds."""
    by_id = {s['id']: s for s in man['weapon_slots']}
    threats = [Vector(t) for t in threats]
    out = {'firing': {}, 'held': {}, 'length': {}}
    for b in man['beams']:
        if not b.get('pd'):
            continue
        s, muz = by_id[b['slot']], bpy.data.objects[b['muzzle']]
        rest = (bpy.data.objects[s['pivot']].matrix_basis.copy(), bpy.data.objects[s['elevation']].matrix_basis.copy())
        reasons = []
        for k in sorted(range(len(threats)), key=lambda k: (threats[k] - muz.matrix_world.translation).length):
            t = threats[k]
            if (t - muz.matrix_world.translation).length > b['range']:
                break
            if not in_arc(man, s, t)[0]:
                reasons.append(f'threat {k + 1} outside its arc')
                continue
            train(s, t)
            ok, why = _can_fire(man, s, b, muz, t, short=THREAT_RADIUS)
            if ok:
                out['firing'][b['id']] = k
                out['length'][b['id']] = (t - muz.matrix_world.translation).length
                break
            reasons.append(f'threat {k + 1}: {why}')
            bpy.data.objects[s['pivot']].matrix_basis, bpy.data.objects[s['elevation']].matrix_basis = rest
            upd()
        if b['id'] not in out['firing']:
            out['held'][b['id']] = '; '.join(reasons) or 'no threat in range'
    return out


def engage(man, target, slots=None):
    """Train every turret (or those in `slots`) whose arc holds the target, and
    decide which beams can fire. Returns {'firing': [beam ids], 'held': {id:
    reason}, 'length': {id: hull units to the target}}. Turrets outside their
    arc keep their rest pose."""
    target = Vector(target)
    by_id = {s['id']: s for s in man['weapon_slots']}
    for s in man['weapon_slots']:
        if s['mount'] == 'TURRET' and (slots is None or s['id'] in slots) and in_arc(man, s, target)[0] \
                and not all(b.get('pd') for b in man['beams'] if b['slot'] == s['id']):
            train(s, target)
    upd()
    out = {'firing': [], 'held': {}, 'length': {}}
    for b in man['beams']:
        if slots is not None and b['slot'] not in slots:
            continue
        if b.get('pd'):
            out['held'][b['id']] = 'point defence: missiles and fighters only'
            continue
        s = by_id[b['slot']]
        muz = bpy.data.objects[b['muzzle']]
        fire = (muz.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized()
        out['length'][b['id']] = max(50.0, (target - muz.matrix_world.translation).dot(fire))
        ok, why = _can_fire(man, s, b, muz, target)
        if not ok:
            out['held'][b['id']] = why
            continue
        out['firing'].append(b['id'])
    return out
