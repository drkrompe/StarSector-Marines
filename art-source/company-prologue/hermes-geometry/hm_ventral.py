"""Hermes underside: the troop bay, the troop ramp, the landing gear and the
belly.

The Hermes lands marines, so the design needs all three. None is in the
sprite (a top view cannot show them); they are inferred from the boat's
job and placed where the planform leaves room.

Troop bay. The spine is the only pressure hull deep enough for standing
headroom, so the bay is a lined void inside it over X -3..20: 1 u walls,
1 u roof, a 1.5 u floor structure over the keel. Four armoured seats face
each other across the aisle in two benches of two (7 u pitch, 4.3 u seat
depth), kit lockers and a stowage shelf stand at the bay head either side of
the hatch forward to the flight deck, a casualty litter is stowed flat
against the roof and a lamp strip runs down the middle. The wing body
passes through the spine there, so the bay void is cut out of it too.

Troop ramp. The wing body and drive block cover the spine's flanks and
stern, so the way out is down. The ramp is the bay's aisle floor, hinged on
the opening's AFT edge, and lowers its FORWARD end: marines walk down and
forward. Every ramp part is parented to 'Hull ventral ramp hinge'. Rotating
that empty about its local Y opens it: positive angles lower the toe. The
custom properties on the empty give the axis, sign and the ground-contact
angle. The rams are split into a hull-anchored cylinder and a rod on the
ramp, each aimed at the other with a damped-track constraint, so they
telescope at any opening angle without being driven.

Landing gear. Tricycle: a nose leg under the neck (folds aft) and two main
legs under the wing body (fold forward). Each leg's parts are parented to
'Gear <leg> hinge'; the model is built with the gear extended (rotation 0)
and the empty's 'retract_angle_deg' about 'retract_axis' folds it into its
well. Each well has two doors on their own pivots ('Gear <leg> port door
hinge' / 'starboard door hinge'), built open. A Blender driver ties each
door's angle to its leg's: open with the leg down, closed flush with the
keel at the leg's retract angle. The retraction actuators telescope the
same way as the ramp rams.
"""
import math
import bmesh
import bpy
from mathutils import Matrix, Vector
from hm_kit import bx, blocks, pivot, parent_to, material
from hm_hull import KEEL, WING_KEEL, AFT_KEEL

GROUND = KEEL - 8.0                      # pad soles, gear extended
RAMP_OPENING = (-1.0, 19.0, 5.0)         # x0, x1, half width
RAMP_WELL_TOP = -8.5                     # the bay floor the opening breaks into
RAMP_HINGE = (-1.0, 0.0, KEEL)
RAMP_HALF = 4.85
RAMP_TOP = -8.4                          # closed ramp's walking surface
BAY_X = (-3.0, 20.0)
BAY_FLOOR, BAY_ROOF, BAY_WALL = -8.5, 9.0, 10.0
# Spine section (y, z) offset 1 u inward: floor, keel chamfer, wall, shoulder.
BAY_SECTION = [(8.09, BAY_FLOOR), (BAY_WALL, -6.59), (BAY_WALL, 3.76), (7.38, BAY_ROOF)]
SEAT_X = (4.5, 11.5)                     # seat centres, 7 u pitch, both sides
DOOR_T = .25                             # gear door plate thickness
WELL_HALF = 1.8
# Legs: name, hinge (x, y, z), retract angle about Y (deg).
GEAR = [('nose', (32.0, 0.0, KEEL + 2.1), 90.0),
        ('port main', (-12.0, 16.0, WING_KEEL + 2.0), -90.0),
        ('starboard main', (-12.0, -16.0, WING_KEEL + 2.0), -90.0)]


def ramp_contact_angle():
    """Opening angle at which the closed ramp's lower toe edge meets GROUND."""
    x0, x1, _ = RAMP_OPENING
    return math.degrees(math.asin(min(1.0, (RAMP_HINGE[2] - GROUND)/(x1 - .15 - x0))))


# ---------------------------------------------------------------- helpers

def cut(obj, cutter):
    """Boolean difference that keeps the cutter's material on the cut faces."""
    bpy.context.view_layer.objects.active = obj
    mod = obj.modifiers.new('Ventral cut', 'BOOLEAN')
    mod.operation = 'DIFFERENCE'
    mod.object = cutter
    mod.solver = 'MANIFOLD'
    try:
        mod.material_mode = 'TRANSFER'
    except (AttributeError, TypeError):
        pass
    bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)
    bpy.ops.object.modifier_apply(modifier=mod.name)


def section_solid(kit, name, x0, x1, half, mat):
    """A port half-section (y, z), mirrored and swept along X from x0 to x1."""
    ring = list(half) + [(-y, z) for y, z in reversed(half)]
    n = len(ring)
    verts = [(x, y, z) for x in (x0, x1) for y, z in ring]
    faces = [tuple(range(n)), tuple(range(n, 2*n))]
    faces += [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
    ob = kit.mesh(name, verts, faces, mat)
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(ob.data)
    bm.free()
    return ob


def rod(kit, name, p0, p1, r, mat, verts=10, bevel=.02):
    """Cylinder from p0 to p1."""
    a, b = Vector(p0), Vector(p1)
    d = b - a
    ob = kit.cylinder(name, (a+b)/2, r, d.length, mat, 'Z', verts, bevel)
    ob.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    return ob


def tracking_tube(kit, name, base, length, r, mat, target, verts=12):
    """A cylinder whose origin is its base end, aimed along local +Z at
    `target` by a damped-track constraint (half of a telescoping ram)."""
    ob = kit.cylinder(name, base, r, length, mat, 'Z', verts, .02)
    ob.data.transform(Matrix.Translation((0, 0, length/2)))
    c = ob.constraints.new('DAMPED_TRACK')
    c.target = target
    c.track_axis = 'TRACK_Z'
    return ob


def empty(name, loc, size=.4):
    ob = bpy.data.objects.new(name, None)
    ob.empty_display_type = 'PLAIN_AXES'
    ob.empty_display_size = size
    ob.location = loc
    bpy.context.collection.objects.link(ob)
    return ob


def telescoping_ram(kit, name, anchor, eye, eye_parent, r, mat_cyl, mat_rod):
    """Cylinder fixed at `anchor`, rod fixed at `eye` (which moves with
    `eye_parent`); each tracks the other, so the ram stays whole as it moves."""
    a, e = Vector(anchor), Vector(eye)
    span = (e - a).length
    a_empty = empty(f'{name} anchor', a)
    e_empty = empty(f'{name} eye', e)
    parent_to([e_empty], eye_parent)
    cyl = tracking_tube(kit, f'{name} cylinder', a, span*.62, r, mat_cyl, e_empty)
    rd = tracking_tube(kit, f'{name} rod', e, span*.62, r*.55, mat_rod, a_empty)
    parent_to([rd], e_empty)
    kit.cylinder(f'{name} anchor clevis', a, r*1.25, r*2.6, mat_cyl, 'Y', 10, .02)
    clevis = kit.cylinder(f'{name} eye clevis', e, r*1.1, r*2.4, mat_cyl, 'Y', 10, .02)
    parent_to([clevis], eye_parent)
    return cyl, rd


def seams(kit, name, strips, z, mat, proud=.04):
    """Dark seam strips on an underside at z: (x0, x1, y0, y1)."""
    return blocks(kit, name, [(a, b, c, d, z - proud, z + .02) for a, b, c, d in strips], mat)


def bolt_row(p0, p1, n, z, s=.3, h=.1):
    out = []
    for i in range(n):
        t = i/(n - 1) if n > 1 else .5
        x = p0[0] + (p1[0] - p0[0])*t
        y = p0[1] + (p1[1] - p0[1])*t
        out.append((x - s/2, x + s/2, y - s/2, y + s/2, z - h, z + .02))
    return out


# ---------------------------------------------------------------- bay

def build_bay(kit, mats):
    x0, x1 = BAY_X
    void = section_solid(kit, 'Temporary troop bay void', x0, x1, BAY_SECTION, mats['liner'])
    cut(kit.hull, void)
    cut(bpy.data.objects['Wing body'], void)
    # Hull fittings sunk into the spine roof (the horseshoe arms' roots) would
    # otherwise stand inside the bay; trim them to the void too.
    for ob in [o for o in bpy.data.objects if o.name.startswith('Hull aft arm') and o.type == 'MESH'
               and 'bolts' not in o.name]:
        cut(ob, void)
    bpy.data.objects.remove(void, do_unlink=True)
    rx0, rx1, h = RAMP_OPENING

    # Deck: plates either side of the opening, the aft landing and the head.
    blocks(kit, 'Hull troop bay deck plates',
           [(x0+.05, rx0-.05, -8.0, 8.0, BAY_FLOOR, BAY_FLOOR+.12),
            (rx1+.05, x1-.05, -8.0, 8.0, BAY_FLOOR, BAY_FLOOR+.12)]
           + [(xa+.05, xb-.05, *sorted((s*(h+.75), s*8.0)), BAY_FLOOR, BAY_FLOOR+.12)
              for s in (1, -1) for xa, xb in ((rx0, 6.0), (6.0, 13.0), (13.0, rx1))], mats['liner'])
    # Opening coaming on the bay floor, inside the deck plates.
    blocks(kit, 'Hull troop bay opening coaming',
           [(rx0, rx1, *sorted((s*h, s*(h+.7))), BAY_FLOOR, BAY_FLOOR+.2) for s in (1, -1)]
           + [(rx0-.6, rx0, -h-.7, h+.7, BAY_FLOOR, BAY_FLOOR+.2),
              (rx1, rx1+.6, -h-.7, h+.7, BAY_FLOOR, BAY_FLOOR+.2)], kit.steel)
    # Tie-down rails along the deck strips.
    blocks(kit, 'Hull troop bay tie-down rails',
           [(rx0+.5, rx1-.5, *sorted((s*7.2, s*7.5)), BAY_FLOOR+.12, BAY_FLOOR+.22) for s in (1, -1)], kit.steel)

    # Frames: transverse ribs on the walls and roof, a liner panel between each.
    ribs = [-2.0, 1.0, 8.0, 15.0, 19.4]
    rib_boxes = []
    for x in ribs:
        for s in (1, -1):
            rib_boxes.append((x-.25, x+.25, *sorted((s*9.45, s*BAY_WALL)), -6.6, 3.8))
        rib_boxes.append((x-.25, x+.25, -7.4, 7.4, BAY_ROOF-.45, BAY_ROOF))
    blocks(kit, 'Hull troop bay frames', rib_boxes, kit.steel)
    panels = []
    for xa, xb in zip(ribs, ribs[1:]):
        for s in (1, -1):
            panels.append((xa+.3, xb-.3, *sorted((s*9.7, s*BAY_WALL)), -6.3, 3.5))
        panels.append((xa+.3, xb-.3, -7.2, 7.2, BAY_ROOF-.2, BAY_ROOF))
    blocks(kit, 'Hull troop bay liner panels', panels, mats['liner'], .03)
    # Aft bulkhead: a liner face with conduit runs.
    blocks(kit, 'Hull troop bay aft bulkhead liner', [(x0, x0+.2, -9.6, 9.6, -6.4, 3.6), (x0, x0+.2, -7.0, 7.0, 3.6, 8.6)], mats['liner'], .03)
    blocks(kit, 'Hull troop bay conduits',
           [(x0+.2, x0+.5, -8.8, 8.8, z-.15, z+.15) for z in (-5.2, -4.4)]
           + [(xa, xb, 8.6, 8.9, 2.8, 3.1) for xa, xb in ((x0+.2, x1-.2),)]
           + [(xa, xb, -8.9, -8.6, 2.8, 3.1) for xa, xb in ((x0+.2, x1-.2),)], kit.steel)

    # Overhead lamp strip and its housing (a faint interior glow only).
    bx(kit, 'Hull troop bay lamp housing', 0.0, 16.0, -.8, .8, BAY_ROOF-.75, BAY_ROOF-.2, kit.steel, .05)
    bx(kit, 'Hull troop bay lamp strip', .3, 15.7, -.55, .55, BAY_ROOF-.85, BAY_ROOF-.74, mats['bay lamp'], 0)
    # The strip's light, so the bay reads through the open ramp. It is
    # inside the pressure hull, so no exterior view or lights layer sees it.
    light = bpy.data.lights.new('Hull troop bay lamp light', 'AREA')
    light.shape = 'RECTANGLE'
    light.size, light.size_y = 15.0, 1.0
    light.energy = 2500
    light.color = (1.0, .93, .82)
    lob = bpy.data.objects.new('Hull troop bay lamp light', light)
    lob.location = (8.0, 0.0, BAY_ROOF - .95)
    bpy.context.collection.objects.link(lob)
    # Casualty litter stowed flat against the roof, port of the lamp strip.
    bx(kit, 'Hull troop bay litter', -1.5, 16.0, 1.6, 6.4, BAY_ROOF-1.05, BAY_ROOF-.75, mats['canvas'], .04)
    blocks(kit, 'Hull troop bay litter poles',
           [(-2.2, 16.7, y-.17, y+.17, BAY_ROOF-1.15, BAY_ROOF-.8) for y in (1.5, 6.5)], kit.steel)
    blocks(kit, 'Hull troop bay litter rack',
           [(x-.15, x+.15, 1.1, 6.9, BAY_ROOF-1.25, BAY_ROOF-1.1) for x in (0.0, 7.5, 14.5)]
           + [(x-.15, x+.15, y-.15, y+.15, BAY_ROOF-1.25, BAY_ROOF-.2) for x in (0.0, 7.5, 14.5) for y in (1.1, 6.9)],
           kit.steel)

    # Benches: two armoured seats a side, facing across the aisle.
    for s, side in ((1, 'port'), (-1, 'starboard')):
        y_wall, y_front = s*9.6, s*5.6
        ya, yb = sorted((y_front, y_wall))
        frame, pads, backs, straps, buckles, legs = [], [], [], [], [], []
        for k, xc in enumerate(SEAT_X):
            frame.append((xc-3.4, xc+3.4, ya, yb, -5.1, -4.75))
            pads.append((xc-3.1, xc+2.9, *sorted((s*5.8, s*9.5)), -4.75, -4.15))
            backs.append((xc-3.0, xc+2.8, *sorted((s*9.0, s*9.55)), -3.9, 2.4))
            backs.append((xc-1.9, xc+1.7, *sorted((s*9.0, s*9.55)), 2.7, 3.6))   # head pad
            for dx in (-1.25, 1.05):
                straps.append((xc+dx-.25, xc+dx+.25, *sorted((s*8.85, s*9.0)), -4.1, 2.3))
            buckles.append((xc-.6, xc+.4, *sorted((s*8.7, s*8.9)), -2.2, -1.4))
            for dx in (-3.1, 3.1):
                legs.append((xc+dx-.2, xc+dx+.2, *sorted((s*6.0, s*6.4)), BAY_FLOOR+.12, -5.1))
        frame.append((SEAT_X[0]+.2-.15, SEAT_X[0]+.2+.15, ya, yb, -5.1, -3.6))  # divider
        frame.append((SEAT_X[0]+3.5-.15, SEAT_X[0]+3.5+.15, *sorted((s*6.2, s*9.5)), -4.75, -3.4))
        blocks(kit, f'Hull troop bay {side} bench frame', frame + legs, kit.steel)
        blocks(kit, f'Hull troop bay {side} seat pads', pads + backs, mats['pad'], .08)
        blocks(kit, f'Hull troop bay {side} harness straps', straps, mats['strap'])
        blocks(kit, f'Hull troop bay {side} harness buckles', buckles, kit.steel)
        # Kit locker at the bay head, and a stowage shelf with kit bags over it.
        la, lb = sorted((s*3.9, s*9.4))
        bx(kit, f'Hull troop bay {side} kit locker', 15.7, 19.8, la, lb, BAY_FLOOR+.12, 3.4, mats['liner'], .06)
        blocks(kit, f'Hull troop bay {side} kit locker doors',
               [(15.55, 15.7, *sorted((s*a, s*b)), BAY_FLOOR+.4, 3.1) for a, b in ((4.15, 6.55), (6.75, 9.15))], kit.steel)
        blocks(kit, f'Hull troop bay {side} kit locker handles',
               [(15.35, 15.55, *sorted((s*a, s*(a+.3))), -1.8, .2) for a in (6.1, 6.95)], kit.dark)
        bx(kit, f'Hull troop bay {side} stowage shelf', 15.2, 19.8, *sorted((s*3.9, s*8.4)), 4.6, 4.85, kit.steel, .03)
        blocks(kit, f'Hull troop bay {side} kit bags',
               [(15.6, 19.4, *sorted((s*4.2, s*6.0)), 4.85, 6.6), (16.2, 19.4, *sorted((s*6.2, s*7.9)), 4.85, 6.0)],
               mats['canvas'], .25)
    # Hatch forward to the flight deck, between the lockers (closed).
    bx(kit, 'Hull troop bay forward hatch frame', 19.6, 19.95, -3.6, 3.6, BAY_FLOOR+.12, 7.6, kit.steel, .06)
    bx(kit, 'Hull troop bay forward hatch', 19.35, 19.6, -2.95, 2.95, BAY_FLOOR+.5, 7.1, mats['liner'], .1)
    blocks(kit, 'Hull troop bay forward hatch dogs',
           [(19.1, 19.35, y-.25, y+.25, z-.6, z+.6) for y in (-2.4, 2.4) for z in (-4.0, 4.0)]
           + [(19.05, 19.35, -1.6, 1.6, -.3, .3)], kit.steel)


# ---------------------------------------------------------------- ramp

def build_ramp(kit, mats):
    x0, x1, h = RAMP_OPENING
    well = bx(kit, 'Temporary ramp well', x0, x1, -h, h, KEEL - 1, BAY_FLOOR + .3, kit.dark, 0)
    cut(kit.hull, well)
    bpy.data.objects.remove(well, do_unlink=True)
    # Jamb liner round the opening (the floor structure's thickness).
    blocks(kit, 'Hull troop bay well liner',
           [(x0+.02, x1-.02, -h+.02, -h+.12, KEEL+.02, BAY_FLOOR), (x0+.02, x1-.02, h-.12, h-.02, KEEL+.02, BAY_FLOOR),
            (x0+.02, x0+.12, -h+.02, h-.02, KEEL+.02, BAY_FLOOR), (x1-.12, x1-.02, -h+.02, h-.02, KEEL+.02, BAY_FLOOR)],
           kit.dark)
    # Frame round the opening on the keel, hazard bars on it, jamb lamps
    # (unlit: the lights layer shows no underside lamps).
    w = .7
    fr = [(x0-w, x1+w, -h-w, -h), (x0-w, x1+w, h, h+w), (x0-w, x0, -h, h), (x1, x1+w, -h, h)]
    blocks(kit, 'Hull ventral ramp frame', [(a, b, c, d, KEEL-.22, KEEL+.05) for a, b, c, d in fr], kit.steel, .03)
    bars = [(x-.25, x+.25, *sorted((s*(h+.1), s*(h+.6))), KEEL-.26, KEEL-.2)
            for s in (1, -1) for x in [x0+1.0+i*1.6 for i in range(12)]]
    bars += [(x1+.1, x1+.6, y-.25, y+.25, KEEL-.26, KEEL-.2) for y in [-h+.8+i*1.4 for i in range(7)]]
    blocks(kit, 'Hull ventral ramp hazard bars', bars, mats['hazard'])
    corners = [(x0-.35, h+.35), (x0-.35, -h-.35), (x1+.35, h+.35), (x1+.35, -h-.35)]
    blocks(kit, 'Hull ventral ramp jamb lamp housings',
           [(x-.32, x+.32, y-.32, y+.32, KEEL-.42, KEEL-.2) for x, y in corners], kit.steel)
    blocks(kit, 'Hull ventral ramp jamb lamp lenses',
           [(x-.2, x+.2, y-.2, y+.2, KEEL-.5, KEEL-.42) for x, y in corners], mats['lens'])

    hx, hy, hz = RAMP_HINGE
    xa, xb = hx + .1, x1 - .15
    parts = [bx(kit, 'Hull ventral ramp', xa, xb, -RAMP_HALF, RAMP_HALF, KEEL + .02, RAMP_TOP - .12, kit.panel, .05),
             bx(kit, 'Hull ventral ramp hinge barrel', hx - .3, hx + .3, -RAMP_HALF + .4, RAMP_HALF - .4, hz - .3, hz + .3, kit.dark, .05)]
    # Walking face: a deck plate with cross treads, curbs along both edges.
    parts.append(bx(kit, 'Hull ventral ramp deck plate', xa+.2, xb-.2, -4.25, 4.25, RAMP_TOP-.12, RAMP_TOP, kit.steel, .03))
    parts.append(blocks(kit, 'Hull ventral ramp treads',
                        [(x-.14, x+.14, -4.05, 4.05, RAMP_TOP, RAMP_TOP+.14) for x in [xa+.9+i*1.5 for i in range(13)]],
                        mats['tread']))
    parts.append(blocks(kit, 'Hull ventral ramp curbs',
                        [(xa, xb, *sorted((s*4.25, s*RAMP_HALF)), RAMP_TOP-.12, RAMP_TOP+.45) for s in (1, -1)], kit.steel, .04))
    parts.append(blocks(kit, 'Hull ventral ramp curb hazard bars',
                        [(x-.3, x+.3, *sorted((s*4.5, s*4.8)), RAMP_TOP+.45, RAMP_TOP+.5)
                         for s in (1, -1) for x in [xa+1.2+i*2.4 for i in range(8)]], mats['hazard']))
    # Toe plate: a chamfered steel lip that bears on the ground.
    parts.append(bx(kit, 'Hull ventral ramp toe plate', xb-1.0, xb, -RAMP_HALF+.15, RAMP_HALF-.15, KEEL+.02, RAMP_TOP-.2, kit.steel, .25))
    # Outer face (the belly when closed): two stiffener seams and a bolt line.
    out = [(xa+.4, xb-1.2, y-.12, y+.12, KEEL-.03, KEEL+.03) for y in (-2.2, 2.2)]
    out += [(x-.12, x+.12, -RAMP_HALF+.3, RAMP_HALF-.3, KEEL-.03, KEEL+.03) for x in (5.5, 12.0)]
    parts.append(blocks(kit, 'Hull ventral ramp outer seams', out, kit.dark))
    bolts = []
    for y in (-4.3, 4.3):
        bolts += bolt_row((xa+.6, y), (xb-1.4, y), 12, KEEL, .28, .08)
    parts.append(blocks(kit, 'Hull ventral ramp outer bolts', bolts, kit.steel))
    hinge = pivot('Hull ventral ramp hinge', RAMP_HINGE,
                  {'open_axis': 'Y', 'open_sign': 1, 'contact_angle_deg': round(ramp_contact_angle(), 1),
                   'note': 'positive rotation about local Y lowers the forward toe'})
    parent_to(parts, hinge)
    # Rams: from brackets on the aft bulkhead to lugs on the curbs.
    for s, side in ((1, 'port'), (-1, 'starboard')):
        y = s*4.55
        bx(kit, f'Hull ventral ramp {side} ram bracket', BAY_X[0], BAY_X[0]+1.0, y-.5, y+.5, -6.6, -5.4, kit.steel, .05)
        telescoping_ram(kit, f'Hull ventral ramp {side} ram', (BAY_X[0]+.6, y, -6.0), (6.0, y, RAMP_TOP+.75),
                        hinge, .32, kit.steel, mats['chrome'])
        lug = bx(kit, f'Hull ventral ramp {side} ram lug', 5.4, 6.6, y-.35, y+.35, RAMP_TOP+.3, RAMP_TOP+.95, kit.steel, .05)
        parent_to([lug], hinge)
    return hinge


# ---------------------------------------------------------------- gear

def add_door_driver(door_pivot, gear_hinge, open_deg, retract_deg):
    """Door angle follows the leg: open_deg with the leg down, 0 when stowed."""
    fc = door_pivot.driver_add('rotation_euler', 0)
    drv = fc.driver
    drv.type = 'SCRIPTED'
    v = drv.variables.new()
    v.name = 'g'
    v.type = 'SINGLE_PROP'
    t = v.targets[0]
    t.id_type = 'OBJECT'
    t.id = gear_hinge
    t.data_path = 'rotation_euler[1]'
    drv.expression = f'{math.radians(open_deg):.6f}*(1-g/{math.radians(retract_deg):.6f})'
    drv.use_self = False


def build_leg(kit, mats, name, x, y, z, retract):
    f = -1 if retract > 0 else 1                 # fold direction along X
    outboard = 1 if y >= 0 else -1                # torque links side (nose: port)
    H = z - GROUND                                # hinge to sole
    host = kit.hull if name == 'nose' else bpy.data.objects['Wing body']
    base = KEEL if name == 'nose' else WING_KEEL
    top = z + 2.45
    wx0, wx1 = sorted((x - f*1.7, x + f*(H + .45)))
    well = bx(kit, 'Temporary gear well', wx0, wx1, y - WELL_HALF, y + WELL_HALF, base - 1, top, kit.dark, 0)
    cut(host, well)
    if name == 'nose':
        cut(bpy.data.objects['Nose collar'], well)
    bpy.data.objects.remove(well, do_unlink=True)
    bx(kit, f'Gear {name} well liner', wx0 + .05, wx1 - .05, y - WELL_HALF + .05, y + WELL_HALF - .05, top - .2, top - .05, kit.dark, 0)
    blocks(kit, f'Gear {name} well ribs',
           [(xr-.15, xr+.15, y-WELL_HALF+.05, y+WELL_HALF-.05, top-.55, top-.2)
            for xr in [wx0 + 2.0 + i*3.0 for i in range(int((wx1 - wx0 - 2.5)/3.0) + 1)]], kit.steel)

    strut_bot = z - .58*H
    sole = GROUND
    ankle = GROUND + 1.25
    parts = [
        kit.cylinder(f'Gear {name} trunnion', (x, y, z), .9, 2.6, kit.steel, 'Y', 16, .05),
        kit.cylinder(f'Gear {name} strut', (x, y, (z + strut_bot)/2), .6, z - strut_bot, kit.steel, 'Z', 16, .05),
        kit.cylinder(f'Gear {name} oleo', (x, y, (ankle + strut_bot + .9)/2), .38, strut_bot + .9 - ankle, mats['chrome'], 'Z', 16, .03),
        kit.cylinder(f'Gear {name} oleo gland', (x, y, strut_bot), .74, .35, kit.steel, 'Z', 16, .03),
        kit.cylinder(f'Gear {name} ankle pivot', (x, y, ankle), .45, 1.5, kit.steel, 'Y', 12, .03),
        bx(kit, f'Gear {name} ankle yoke', x - .5, x + .5, y - .6, y + .6, ankle, ankle + .7, kit.steel, .08),
        bx(kit, f'Gear {name} pad', x - 1.35, x + 1.35, y - 1.2, y + 1.2, sole + .25, sole + .85, kit.steel, .12),
        bx(kit, f'Gear {name} sole plate', x - 1.45, x + 1.45, y - 1.3, y + 1.3, sole, sole + .25, mats['rubber'], .06),
        blocks(kit, f'Gear {name} pad ribs',
               [(x-1.2, x+1.2, y+dy-.1, y+dy+.1, sole+.85, sole+1.2) for dy in (-.75, .75)]
               + [(x+dx-.1, x+dx+.1, y-1.0, y+1.0, sole+.85, sole+1.05) for dx in (-.9, .9)], kit.steel),
    ]
    # Torque links: a scissor on the outboard face between gland and ankle.
    knee = Vector((x, y + outboard*1.45, (strut_bot + ankle)/2 + .2))
    up = Vector((x, y + outboard*.55, strut_bot - .25))
    dn = Vector((x, y + outboard*.5, ankle + .55))
    parts.append(rod(kit, f'Gear {name} upper torque link', up, knee, .17, kit.steel, 6))
    parts.append(rod(kit, f'Gear {name} lower torque link', knee, dn, .17, kit.steel, 6))
    parts.append(kit.cylinder(f'Gear {name} torque link knee', tuple(knee), .22, .4, kit.steel, 'X', 8, .02))
    # Drag brace: a yoke off the trunnion toward the fold, braced to the strut.
    yoke_end = Vector((x + f*1.8, y, z - .3))
    parts.append(rod(kit, f'Gear {name} trunnion yoke', (x, y, z), yoke_end, .26, kit.steel, 8))
    parts.append(rod(kit, f'Gear {name} drag brace', yoke_end, (x, y, z - .45*H), .24, kit.steel, 8))
    parts.append(kit.cylinder(f'Gear {name} drag brace knuckle', tuple(yoke_end), .3, .7, kit.steel, 'Y', 8, .02))
    # Retraction crank on the inboard side of the trunnion.
    inboard = -outboard
    crank_tip = Vector((x - f*.75, y + inboard*1.05, z + 1.05))
    parts.append(bx(kit, f'Gear {name} retraction crank', x - .45, x + .45, y + inboard*.9 - .15, y + inboard*.9 + .15,
                    z - .2, z + 1.25, kit.steel, .05))
    hinge = pivot(f'Gear {name} hinge', (x, y, z),
                  {'retract_axis': 'Y', 'retract_angle_deg': retract, 'extended_angle_deg': 0.0,
                   'note': 'built extended; rotate about local Y by retract_angle_deg to stow'})
    parent_to(parts, hinge)
    # Retraction actuator: hull-anchored in the well roof, rod on the crank.
    anchor = (x + f*6.2, y + inboard*1.05, z + 1.95)
    bx(kit, f'Gear {name} actuator mount', anchor[0] - .45, anchor[0] + .45, anchor[1] - .4, anchor[1] + .4,
       anchor[2], top - .5, kit.steel, .05)
    telescoping_ram(kit, f'Gear {name} actuator', anchor, tuple(crank_tip), hinge, .28, kit.steel, mats['chrome'])

    # Doors: one along each long edge of the well, built open (hanging),
    # closing flush with the keel as the leg stows.
    ha, hb = (wx0 + .15, wx1 - .15)
    for s, side in ((1, 'port'), (-1, 'starboard')):
        ye = y + s*WELL_HALF
        dp = pivot(f'Gear {name} {side} door hinge', (x, ye, base),
                   {'close_axis': 'X', 'open_angle_deg': 90.0*s, 'closed_angle_deg': 0.0,
                    'follows': f'Gear {name} hinge',
                    'note': 'built open; a driver closes it (0 deg) as the leg reaches its retract angle'})
        ya, yb = sorted((ye, ye - s*(WELL_HALF - .06)))
        door = [bx(kit, f'Gear {name} {side} door', ha, hb, ya, yb, base + .01, base + DOOR_T, kit.panel, .05),
                blocks(kit, f'Gear {name} {side} door stiffeners',
                       [(ha+.4, hb-.4, *sorted((ye - s*.5, ye - s*.7)), base + DOOR_T, base + DOOR_T + .2),
                        (ha+.4, hb-.4, *sorted((ye - s*1.2, ye - s*1.4)), base + DOOR_T, base + DOOR_T + .2)], kit.steel),
                blocks(kit, f'Gear {name} {side} door hinge knuckles',
                       [(xk-.4, xk+.4, *sorted((ye, ye - s*.3)), base - .12, base + .2)
                        for xk in (ha + 1.0, (ha + hb)/2, hb - 1.0)], kit.dark)]
        parent_to(door, dp)
        dp.rotation_euler[0] = math.radians(90.0*s)
        add_door_driver(dp, hinge, 90.0*s, retract)
    return hinge


def build_gear(kit, mats):
    return [build_leg(kit, mats, name, x, y, z, retract) for name, (x, y, z), retract in GEAR]


# ---------------------------------------------------------------- belly

def build_belly(kit, mats):
    k = KEEL
    # Keel strakes either side of the ramp, a short centre strake aft of it.
    blocks(kit, 'Hull belly keel strakes',
           [(-22.5, 31.0, *sorted((s*6.6, s*7.3)), k - .55, k + .05) for s in (1, -1)]
           + [(-22.5, -17.0, -.35, .35, k - .45, k + .05)], kit.steel, .06)
    # Panel seams on the spine belly (transverse and the outer longitudinal).
    sp = [(x - .1, x + .1, -7.8, -5.9) for x in (-17.0, -9.7, 24.0, 28.5)]
    sp += [(x - .1, x + .1, 5.9, 7.8) for x in (-17.0, -9.7, 24.0, 28.5)]
    sp += [(x - .1, x + .1, -6.4, 6.4) for x in (-17.0, -9.7)]
    sp += [(-22.5, -3.0, y - .1, y + .1) for y in (-3.4, 3.4)]
    sp += [(22.0, 33.5, y - .1, y + .1) for y in (-3.6, 3.6)]
    seams(kit, 'Hull belly spine seams', sp, k, kit.dark)
    # Service covers with bolt rings on the spine belly aft of the bay.
    covers = [(-15.0, -10.5, -2.8, 2.8), (21.6, 24.0, -6.6, -4.2), (21.6, 24.0, 4.2, 6.6)]
    blocks(kit, 'Hull belly service covers', [(a, b, c, d, k - .14, k + .02) for a, b, c, d in covers], kit.panel, .04)
    bolts = []
    for a, b, c, d in covers:
        for p0, p1 in (((a+.3, c+.3), (b-.3, c+.3)), ((a+.3, d-.3), (b-.3, d-.3))):
            bolts += bolt_row(p0, p1, max(2, int((b - a)/1.0)), k - .14, .26, .08)
    blocks(kit, 'Hull belly service cover bolts', bolts, kit.steel)
    # Drains: three plugs down the centreline.
    for i, x in enumerate((-16.0, -3.6, 26.5), 1):
        kit.cylinder(f'Hull belly drain {i}', (x, 0, k - .12), .35, .3, kit.steel, 'Z', 10, .04)
    # Antenna blades: one under the spine aft, two under the drive block.
    blades = [('spine', (-6.8, 0.0, k)), ('drive block port', (-27.0, 11.0, AFT_KEEL)),
              ('drive block starboard', (-27.0, -11.0, AFT_KEEL))]
    for label, (x, y, z) in blades:
        verts, faces = [], []
        prof = [(x + 1.6, z), (x - 1.6, z), (x - 2.3, z - 2.0), (x - 1.3, z - 2.0)]
        for yy in (y - .12, y + .12):
            verts += [(px, yy, pz) for px, pz in prof]
        faces = [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]
        kit.mesh(f'Hull belly antenna blade {label}', verts, faces, kit.steel, .04)
        bx(kit, f'Hull belly antenna blade {label} base', x - 1.9, x + 1.9, y - .5, y + .5, z - .2, z + .02, kit.panel, .05)

    # Wing body underside: seams, a vent grille and a service cover a side.
    wk = WING_KEEL
    for s, side in ((1, 'port'), (-1, 'starboard')):
        st = [(x - .1, x + .1, *sorted((s*12.0, s*23.5))) for x in (-15.0, 6.0)]
        st += [(x - .1, x + .1, *sorted((s*19.0, s*23.5))) for x in (-4.0,)]
        st += [(-15.0, 20.0, *sorted((s*12.0, s*12.2))), (6.0, 20.0, *sorted((s*23.0, s*23.2)))]
        seams(kit, f'Hull belly wing {side} seams', st, wk, kit.dark)
        ya, yb = sorted((s*19.5, s*23.0))
        bx(kit, f'Hull belly wing {side} vent well', -13.5, -6.5, ya, yb, wk - .12, wk + .02, kit.dark, 0)
        blocks(kit, f'Hull belly wing {side} vent louvres',
               [(xl - .17, xl + .17, ya + .2, yb - .2, wk - .4, wk - .1) for xl in [-13.0 + i*.75 for i in range(9)]], kit.steel)
        ca, cb = sorted((s*19.5, s*23.0))
        bx(kit, f'Hull belly wing {side} service cover', -2.5, 3.0, ca, cb, wk - .14, wk + .02, kit.panel, .04)
        blocks(kit, f'Hull belly wing {side} service cover bolts',
               bolt_row((-2.2, ca + .3), (2.7, ca + .3), 5, wk - .14, .26, .08)
               + bolt_row((-2.2, cb - .3), (2.7, cb - .3), 5, wk - .14, .26, .08), kit.steel)
        # Drive block underside: a vent grille and seams each side.
        ak = AFT_KEEL
        da, db = sorted((s*4.0, s*8.0))
        bx(kit, f'Hull belly drive block {side} vent well', -33.5, -26.5, da, db, ak - .12, ak + .02, kit.dark, 0)
        blocks(kit, f'Hull belly drive block {side} vent louvres',
               [(xl - .17, xl + .17, da + .2, db - .2, ak - .4, ak - .1) for xl in [-33.0 + i*.75 for i in range(9)]], kit.steel)
        seams(kit, f'Hull belly drive block {side} seams',
              [(-35.5, -18.0, *sorted((s*14.5, s*14.7))), (-24.0, -23.8, *sorted((s*1.0, s*19.0)))], ak, kit.dark)


def build(kit):
    mats = {
        'liner': material('Troop bay liner grey', (.30, .32, .33), .3, .6),
        'pad': material('Troop bay seat pad', (.16, .17, .15), 0, .85),
        'strap': material('Troop bay harness webbing', (.32, .26, .12), 0, .8),
        'canvas': material('Troop bay canvas', (.30, .29, .22), 0, .9),
        'bay lamp': material('Troop bay lamp lens', (.75, .74, .68), 0, .3, emission=(1.0, .95, .85), strength=6.0),
        'hazard': material('Ventral hazard yellow', (.62, .48, .08), .3, .5),
        'lens': material('Ventral jamb lamp lens (unlit)', (.55, .42, .2), 0, .25),
        'tread': material('Ventral ramp tread', (.09, .09, .09), .2, .85),
        'chrome': material('Gear oleo chrome', (.6, .62, .64), .9, .25),
        'rubber': material('Gear sole rubber', (.05, .05, .05), 0, .9),
    }
    build_bay(kit, mats)
    build_ramp(kit, mats)
    build_gear(kit, mats)
    build_belly(kit, mats)
