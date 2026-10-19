"""Colossus Mk.III grapples: four telescoping booms under the keel that carry off the Tarsus.

The Asharu battle plan ends with the captured Tarsus clamped under the Mk.III (S10). The
Tarsus build (tarsus-geometry/geometry.json 'docking') publishes four dorsal grapple
receivers, 2.6 m plates, at (metres, Tarsus frame) (35.7, 13.77, 4.87), (35.7, -12.75,
4.87), (-15.3, 7.65, 5.64) and (-15.3, -6.63, 5.64), the Tarsus riding under the Mk.III's
keel, dorsal up, same heading. They are copied here (TARSUS_RECEIVERS_M) rather than read
from that package; the driver warns when the Tarsus's file disagrees.

Where the Tarsus hangs (TARSUS_ORIGIN, Mk.III units): on the Mk.III's centreline, her top
(11.2 m above her origin) a metre under the keel skids' soles, and fore-aft where the fore
and aft booms rake out by the same amount. The receivers are 51 m apart fore and aft and
the Mk.III's flat keel only 46 m, so the booms splay: the fore pair from the keel ahead of
the forward cargo hatch, the aft pair beside the aft hatch, each reaching out and down to
its plate like a claw.

Each grapple: a bolted base flange and trunnion block on the keel (fixed), and on the
hinge a knuckle, a sleeve, two telescoping stages and a clamp head (a 2.6 m magnetic pad
to match the receiver plate, four jaws, a hazard ring). Built stowed: booms retracted and
folded flat under the keel. Moving parts hang off empties that carry their own axes as
custom properties:
  'Grapple <i> mount'    fixed at the pivot: local +X the deployed boom, local Y the hinge axis;
  'Grapple <i> hinge'    in the mount, rotation_euler[1] from 'stowed_deg' (rest) to 0;
  'Grapple <i> stage 2'  translates along its parent's local +X by 0 .. 'travel';
  'Grapple <i> stage 3'  the same, riding stage 2.
'Grapple <i> clamp anchor' is the pad face, +X along the boom: at full extension it sits on
receiver <i>. --grapples poses them deployed for review.
"""
import math
import bpy
from mathutils import Matrix, Vector
import c3_hull
from c3_hull import Parts, YC

MPU = .495
TARSUS_MPU = .51
TARSUS_RECEIVERS_M = [(35.7, 13.77, 4.87), (35.7, -12.75, 4.87), (-15.3, 7.65, 5.64), (-15.3, -6.63, 5.64)]
TARSUS_CENTRELINE_M = .51              # the Tarsus's sprite centreline, Y in her frame
TARSUS_TOP_M = 11.2                    # her highest point above her origin
SKID_SOLE = c3_hull.HOLD_KEEL-2.85     # the keel skids' soles (units)
CLEARANCE_M = 1.0
# Base pivots (x, rel y) on the hold's flat keel, clear of hatches, skids and lift wells.
BASES = [(81.2, 15.2), (81.2, -15.2), (0.0, 15.6), (0.0, -15.6)]
PIVOT_DROP = 2.0                       # hinge axis below the keel
STOWED = {}
TRAVEL = {}
ANCHORS = {}


def tarsus_origin():
    """Mk.III-unit position of the Tarsus's origin when she hangs from the grapples."""
    oz = SKID_SOLE*MPU-CLEARANCE_M-TARSUS_TOP_M
    oy = YC*MPU-TARSUS_CENTRELINE_M
    fore = max(b[0] for b in BASES)*MPU
    aft = min(b[0] for b in BASES)*MPU
    fx = max(r[0] for r in TARSUS_RECEIVERS_M)
    ax = min(r[0] for r in TARSUS_RECEIVERS_M)
    ox = (fore+aft-fx-ax)/2                # fore and aft booms rake out by the same amount
    return Vector((ox, oy, oz))/MPU


def receivers():
    o = tarsus_origin()
    return [o+Vector(r)/MPU for r in TARSUS_RECEIVERS_M]


def _parent(objs, parent):
    for o in objs:
        o.parent = parent
        o.matrix_parent_inverse = Matrix.Identity(4)


def grapple(kit, i, base, target):
    x, rel = base
    p = f'Grapple {i}'
    sign = 1 if rel > 0 else -1
    by = YC+rel
    keel = c3_hull.HOLD_KEEL
    piv = Vector((x, by, keel-PIVOT_DROP))
    d = (target-piv)
    D = d.length
    d.normalize()
    # Hinge axis: horizontal, square to the boom's plane; stowed the boom lies flat, pointing
    # back along its own plan direction (toward the middle of the keel).
    plan = Vector((d.x, d.y, 0)).normalized()
    stow = -plan
    axis = d.cross(stow).normalized()
    ang = math.degrees(d.angle(stow))
    ex, ey = d, axis
    ez = ex.cross(ey)
    basis = Matrix((ex, ey, ez)).transposed().to_4x4()

    # --- Fixed: base flange, trunnion block and its cheeks, bolts, hazard ring. ---
    fixed = Parts()
    fixed.cyl((x, by, keel+.05), 3.2, -.7, 'Z', 32)
    fixed.build(kit, f'{p} base flange', kit.steel, .08)
    blk = Parts()
    blk.box(x-2.0, x+2.0, by-2.4, by+2.4, keel-.6, keel-PIVOT_DROP+.6)
    blk.build(kit, f'{p} base block', kit.armour, .1)
    bolts = Parts()
    for k in range(12):
        a = 2*math.pi*(k+.5)/12
        bolts.cyl((x+2.85*math.cos(a), by+2.85*math.sin(a), keel-.65), .2, -.22, 'Z', 6)
    bolts.build(kit, f'{p} base bolts', kit.pale)
    Parts().tube(x, by, keel-.76, keel-.7, 3.75, 3.75, .5, 40).build(kit, f'{p} base hazard ring', kit.orange)

    # --- Hinge and everything on it, built about the origin along +X, then placed. ---
    hinge = kit.empty(f'{p} hinge', (0, 0, 0), 0)
    L0 = max(8.5, (D+1.45)/3+.6)
    Ls = L0-1.2
    head = 2.25
    t = (D-(L0+1.3+head))/2
    TRAVEL[i] = round(t, 3)
    on_hinge = [kit.cylinder(f'{p} knuckle', (0, 0, 0), 2.0, 4.4, kit.steel, axis='Y', vertices=16, bevel=.08),
                kit.cylinder(f'{p} hinge pin', (0, 0, 0), .7, 5.4, kit.pale, axis='Y', vertices=10, bevel=0),
                kit.cylinder(f'{p} sleeve', ((.6+L0)/2, 0, 0), 1.8, L0-.6, kit.steel, axis='X', vertices=14, bevel=.06),
                kit.cylinder(f'{p} sleeve collar', (L0-.3, 0, 0), 2.1, .6, kit.armour, axis='X', vertices=14, bevel=.05),
                kit.cylinder(f'{p} sleeve root collar', (2.4, 0, 0), 2.1, .7, kit.armour, axis='X', vertices=14, bevel=.05)]
    # Hydraulic line along the sleeve's back.
    on_hinge.append(kit.cylinder(f'{p} sleeve hydraulic line', ((2.8+L0-.6)/2, 0, 2.05), .26, L0-3.4, kit.dark,
                                 axis='X', vertices=8, bevel=0))
    _parent(on_hinge, hinge)
    s2 = kit.empty(f'{p} stage 2', (0, 0, 0), 0)
    s2.parent = hinge
    st2 = [kit.cylinder(f'{p} stage 2 tube', (1.5+Ls/2, 0, 0), 1.4, Ls, kit.pale, axis='X', vertices=14, bevel=.04),
           kit.cylinder(f'{p} stage 2 collar', (1.5+Ls-.2, 0, 0), 1.6, .4, kit.steel, axis='X', vertices=14, bevel=.03)]
    _parent(st2, s2)
    s3 = kit.empty(f'{p} stage 3', (0, 0, 0), 0)
    s3.parent = s2
    hx = 2.5+Ls                                       # stage 3's end: the clamp head's neck
    st3 = [kit.cylinder(f'{p} stage 3 tube', (2.5+Ls/2, 0, 0), 1.0, Ls, kit.pale, axis='X', vertices=12, bevel=.03),
           kit.cylinder(f'{p} stage 3 collar', (hx-.2, 0, 0), 1.2, .4, kit.steel, axis='X', vertices=12, bevel=.03),
           kit.cylinder(f'{p} clamp neck', (hx+.6, 0, 0), 1.3, 1.2, kit.steel, axis='X', vertices=14, bevel=.05),
           kit.cylinder(f'{p} clamp pad back', (hx+1.5, 0, 0), 2.4, .6, kit.armour, axis='X', vertices=28, bevel=.08),
           kit.cylinder(f'{p} clamp pad', (hx+1.95, 0, 0), 2.62, .3, kit.dark, axis='X', vertices=28, bevel=.03)]
    ring = Parts().tube(0, 0, -.01, .05, 2.66, 2.66, .35, 40)
    st3.append(ring.build(kit, f'{p} clamp pad hazard ring', kit.orange))
    st3[-1].rotation_euler = (0, math.pi/2, 0)
    st3[-1].location = (hx+1.83, 0, 0)
    jaws = Parts()
    for k in range(4):
        a = math.pi/4+k*math.pi/2
        c, s = math.cos(a), math.sin(a)
        r0, r1 = 2.5, 3.0
        # A hooked finger: out from the pad back, then forward past the pad face and in.
        pts = [(hx+1.2, r0), (hx+1.2, r1), (hx+2.9, r1+.15), (hx+3.3, r1-.35), (hx+2.8, r1-.45), (hx+1.7, r0)]
        w = .35
        quad = [(px, (rr)*c-w*s, (rr)*s+w*c) for px, rr in pts]
        jaws.slab(quad, (0, 2*w*s, -2*w*c))
    st3.append(jaws.build(kit, f'{p} clamp jaws', kit.steel, .05))
    _parent(st3, s3)
    anchor = kit.empty(f'{p} clamp anchor', (hx+2.1, 0, 0), 0)
    anchor.parent = s3
    # Place: a fixed mount at the pivot with +X along the deployed boom; the hinge turns in it
    # about local Y, ang degrees (stowed, the rest pose) to 0 (deployed).
    mount = kit.empty(f'{p} mount', (0, 0, 0), 0)
    bpy.context.view_layer.update()
    mount.matrix_world = Matrix.Translation(piv) @ basis
    hinge.parent = mount
    hinge.matrix_parent_inverse = Matrix.Identity(4)
    hinge.rotation_mode = 'XYZ'
    mount['note'] = 'fixed; local +X is the deployed boom, local Y the hinge axis'
    hinge['axis'] = 'Y'
    hinge['stowed_deg'] = round(ang, 2)
    hinge['deployed_deg'] = 0.0
    hinge['note'] = 'rotation_euler[1] (local Y, radians) from stowed_deg (rest) to deployed_deg'
    for s in (s2, s3):
        s['axis'] = 'X'
        s['travel'] = TRAVEL[i]
        s['note'] = 'location[0] from 0 (retracted, rest) to travel (extended)'
    STOWED[i] = (hinge, s2, s3, ang)
    ANCHORS[i] = {'pivot': [round(v, 3) for v in piv], 'boom_direction': [round(v, 4) for v in d],
                  'reach': round(D, 3), 'stowed_deg': round(ang, 2), 'travel_per_stage': TRAVEL[i],
                  'receiver': [round(v, 3) for v in target]}


def pose(deployed):
    """Rest the grapples stowed (folded, retracted) or deployed (down, fully extended)."""
    for i, (hinge, s2, s3, ang) in STOWED.items():
        hinge.rotation_euler = (0, 0 if deployed else math.radians(ang), 0)
        s2.location = (TRAVEL[i] if deployed else 0, 0, 0)
        s3.location = (TRAVEL[i] if deployed else 0, 0, 0)
    bpy.context.view_layer.update()


def build(kit):
    STOWED.clear()
    TRAVEL.clear()
    ANCHORS.clear()
    for i, (base, target) in enumerate(zip(BASES, receivers()), 1):
        grapple(kit, i, base, target)
    pose(False)
