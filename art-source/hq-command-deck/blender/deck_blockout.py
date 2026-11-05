"""Blender blockout of the command deck at real scale: the ImageGen layout guide.

Run through the render queue (`render-queue` skill); the command it runs is:

  blender -b --factory-startup --python art-source/hq-command-deck/blender/deck_blockout.py

The room is the working bridge of a ship, seen across it from the starboard side: the
bow is off the frame's left and the stern off its right, so the picture reads as the
ship does. Its look is the moulded late-90s starship bridge (owner's reference: the
Rodger Young in Starship Troopers): big raked windows between leaning pillars capped with
lights, smooth faceted overhead panels with light troughs and a monitor cowl hung from them,
shell seats with low raked consoles, and crew in the foreground. Forward (left) the flight
dais with its helm and navigator; far wall the comms watch under the glass; the plot
table in the middle; aft (right) the deck narrows past a heavy hull frame into the working end,
with the ledger desk, roster board, pressure hatch and engineering watch, the one
place services still show. Nothing mirrors. The whole ship is turned a few degrees to the camera (`YAW`)
so the far wall recedes towards the bow instead of standing square to the frame.

Everything aboard has a job, and nothing is here to fill a gap: helm and navigator fly her; the two near
stations are the fire-control and sensor watches; comms talks to the fleet and the company; the plot and its
cowl monitor are where the officers read the situation; the quartermaster runs the company's money and roster;
the engineering watch and the systems board mind the ship; the frame carries the damage-control point. Its
hardware is the Rodger Young's too: colour televisions let into panels, boards of physical buttons.

Every prop has real dimensions and one camera projects them all, and ImageGen is told to
keep the guide's geometry. The room is built in ship coordinates, deck metres with x
towards the stern, y up and z towards the port side, and `turn_ship` then bakes the yaw,
after which those are the camera's axes. They become Blender's (X, Y, Z) = (x, z, y) in one
place, `V`.

Writes beside the script's folder:
  deck-guide-v10.png          grey guide for ImageGen: the room and its seated watch, no named figures
  deck-guide-v10-people.png   the same with stand-ins at their stands, for proportions
  deck-guide-v10.layout.json  camera, room, props and projected markers
  deck-guide-v10-ids.png     one flat colour per object, read with deck-guide-v10.ids.json: which object
                             owns every pixel, and so what stands in front of each screen
  masks/<stand>.occluder.png with `-- --masks`: what stands in front of each person
  <stand>-pose-guide.png     with `-- --pose <stand>`: that stand's mannequin alone, grey on
                             transparency at three times the frame, the pose authority for its card
"""
import json
import math
import re
import sys
from contextlib import contextmanager
from pathlib import Path

import bmesh
import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Matrix, Vector

OUT = Path(__file__).resolve().parent.parent
W, H = 2172, 724
CAM = {"height": 2.2, "focal": 1050.0, "cx": W / 2, "cy": 300.0}
ROOM = {"ceiling": 3.2, "port": 11.0, "aftPort": 9.8, "frame": 3.0, "aft": 8.0}
TABLE = (-1.4, 2.0, 5.9, 8.1)                   # x0, x1, z0, z1 of the plotting table
RAKE = 1.15                                     # how far the glass leans in: the roof is this much smaller than the floor
YAW = math.radians(-9.0)                        # the ship's turn to the camera
PIVOT = ((TABLE[0] + TABLE[1]) / 2, (TABLE[2] + TABLE[3]) / 2)   # about the table, which stays where it is drawn

# Feet (x, z) in ship coordinates, height above their own floor, and that floor. A seated stand
# is the chair's centre; seated figures are measured to the top of the head, and so is a leaning
# one: Morgan leans on the plot table's aft rim, so her head is lower than her standing height.
PEOPLE = {
    "castle": (-2.75, 6.2, 1.68, 0.0),
    "comms-left": (-2.8, 9.21, 1.30, 0.0),
    "comms-right": (-1.8, 9.21, 1.30, 0.0),
    "quartermaster": (5.42, 9.05, 1.80, 0.0),
    "morgan": (2.48, 6.85, 1.66, 0.0),
}
# Stand-ins posed rather than drawn as a plain column: their builder, keyed by stand.
POSED = {"morgan": "leaning"}
# How far in front of a posed stand's feet its matte starts: the reach of its arms toward the camera.
POSED_DEPTH = 0.4


def V(x, y, z):
    return (x, z, y)


# ---------------------------------------------------------------- kit

M = {}
LIFT = Vector((0.0, 0.0, 0.0))     # Blender-space offset applied to every solid built


def mat(name, grey):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (grey, grey, grey, 1)
    m.specular_intensity = 0.0   # a flat face square to the camera would otherwise wash out, the black glass worst
    m.roughness = 1.0
    return m


def materials():
    M.update(
        floor=mat("floor plate", .16), inset=mat("command island", .09), wall=mat("bulkhead", .30),
        panel=mat("wall panel", .24), rib=mat("structural rib", .11), ceiling=mat("ceiling", .12),
        metal=mat("console metal", .20), dark=mat("recess", .05), table=mat("chart table", .17),
        chair=mat("chair", .09), pipe=mat("conduit", .14), board=mat("roster board", .55),
        paper=mat("paper", .65), crate=mat("crate", .22), pane=mat("plotting glass", .42),
        rail=mat("handrail", .5), lamp=mat("light", .8), plot=mat("plotting surface", .7),
        glow=mat("screen key", 1.0), 
        person=mat("stand-in", .6), crew=mat("seated crew", .36), space=mat("window key", 1.0),
    )
    # Keys, not pictures: screens and windows are holes in the plate, cut where the painting kept these
    # colours, so the game's readouts and the space outside draw behind whatever was painted in front.
    M["glow"].diffuse_color = (1, 0, 1, 1)
    M["space"].diffuse_color = (0, 1, 0, 1)
    # The boards of buttons and indicators the game draws: the paint leaves each as one plain patch of this colour.
    M["tell"] = mat("button light key", 1.0)
    M["tell"].diffuse_color = (0, 1, 1, 1)
    M["person"].diffuse_color = (.8, .12, .1, 1)


@contextmanager
def lifted(dx=0.0, dy=0.0, dz=0.0):
    """Build everything inside shifted by a deck-space offset (the rear tier, a moved station)."""
    global LIFT
    old = LIFT.copy()
    LIFT = LIFT + Vector(V(dx, dy, dz))
    try:
        yield
    finally:
        LIFT = old


def finish(ob, m, bevel, collection=None):
    ob.data.materials.append(m)
    (collection or bpy.context.scene.collection).objects.link(ob)
    if bevel:
        b = ob.modifiers.new("edge radius", "BEVEL")
        b.width = bevel
        b.segments = 2
        b.limit_method = "ANGLE"
    return ob


def solid(name, verts, faces, m, bevel=0.0, collection=None):
    me = bpy.data.meshes.new(name)
    me.from_pydata([Vector(V(*p)) + LIFT for p in verts], [], faces)
    me.update()
    return finish(bpy.data.objects.new(name, me), m, bevel, collection)


def box(name, x0, x1, y0, y1, z0, z1, m, bevel=0.012, rot=0.0):
    """Axis box in deck coordinates, optionally turned about its own vertical axis."""
    cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
    c, s = math.cos(rot), math.sin(rot)
    # An edge radius past half the thinnest side turns a thin part inside out.
    bevel = min(bevel, 0.4 * min(abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)))
    pts = []
    for y in (y0, y1):
        for x, z in ((x0, z0), (x1, z0), (x1, z1), (x0, z1)):
            dx, dz = x - cx, z - cz
            pts.append((cx + dx * c - dz * s, y, cz + dx * s + dz * c))
    return solid(name, pts, [(3, 2, 1, 0), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)],
                 m, bevel)


def prism(name, x0, x1, profile, m, bevel=0.01):
    """Profile of (y, z) points, counter-clockwise seen from +x, extruded from x0 to x1."""
    n = len(profile)
    area = sum(profile[i][0] * profile[(i + 1) % n][1] - profile[(i + 1) % n][0] * profile[i][1] for i in range(n))
    if area < 0:
        profile = profile[::-1]
    verts = [(x0, y, z) for y, z in profile] + [(x1, y, z) for y, z in profile]
    faces = [tuple(reversed(range(n))), tuple(range(n, 2 * n))]
    faces += [(i, (i + 1) % n, n + (i + 1) % n, n + i) for i in range(n)]
    return solid(name, verts, faces, m, bevel)


def frustum(name, front, back, m, bevel=0.01):
    """front/back = (x0, x1, y0, y1, z): a tapered housing such as a CRT."""
    pts = []
    for x0, x1, y0, y1, z in (front, back):
        pts += [(x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z)]
    return solid(name, pts, [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)],
                 m, bevel)


def cylinder(name, x, z, y0, y1, r, m, verts=24, bevel=0.0, horizontal=None, collection=None):
    """Vertical cylinder at (x, z) from y0 to y1. Horizontal along 'x' or 'z': then x..z is the
    span along that axis, y0 the centre height and y1 the cross-axis position."""
    ring = [(math.cos(2 * math.pi * i / verts) * r, math.sin(2 * math.pi * i / verts) * r) for i in range(verts)]
    if horizontal == "x":
        pts = [(x, y0 + b, y1 + a) for a, b in ring] + [(z, y0 + b, y1 + a) for a, b in ring]
    elif horizontal == "z":
        pts = [(y1 + a, y0 + b, x) for a, b in ring] + [(y1 + a, y0 + b, z) for a, b in ring]
    else:
        pts = [(x + a, y0, z + b) for a, b in ring] + [(x + a, y1, z + b) for a, b in ring]
    faces = [tuple(reversed(range(verts))), tuple(range(verts, 2 * verts))]
    faces += [(i, (i + 1) % verts, verts + (i + 1) % verts, verts + i) for i in range(verts)]
    ob = solid(name, pts, faces, m, bevel, collection)
    for p in ob.data.polygons:
        p.use_smooth = len(p.vertices) == 4
    return ob


def torus(name, x, y, z, major, minor, m, facing="z"):
    """A ring: facing 'z' stands it on the wall plane, facing 'y' lays it flat."""
    bpy.ops.mesh.primitive_torus_add(major_radius=major, minor_radius=minor, major_segments=48,
                                     minor_segments=12, location=Vector(V(x, y, z)) + LIFT)
    ob = bpy.context.object
    if facing == "z":
        ob.rotation_euler[0] = math.pi / 2
    ob.name = name
    ob.data.materials.append(m)
    bpy.ops.object.shade_smooth()
    return ob


def made(fn, *args, **kwargs):
    """The objects a builder call created, so a whole assembly can be turned at once."""
    before = set(bpy.data.objects)
    fn(*args, **kwargs)
    return [o for o in bpy.data.objects if o not in before]


def spin(objects, pivot, angle, axis="up"):
    """Turn objects about a deck-space pivot, baking the turn into their meshes.
    'up' turns about the vertical; 'depth' about the camera's line of sight."""
    p = Vector(V(*pivot)) + LIFT
    turn = Matrix.Translation(p) @ Matrix.Rotation(angle, 4, "Z" if axis == "up" else "Y") @ Matrix.Translation(-p)
    for ob in objects:
        ob.data.transform(turn @ ob.matrix_basis)
        ob.matrix_basis = Matrix.Identity(4)


# ---------------------------------------------------------------- room

def ibeam_z(name, x, z0, z1, top, deep, flange, web=0.05, stiffen=0.7, holes=0.0):
    """An I-section frame running along z under the deck head: web, top and bottom flanges, stiffeners,
    and lightening holes in the web when `holes` gives their radius."""
    box(f"{name} web", x - web / 2, x + web / 2, top - deep, top, z0, z1, M["rib"], 0)
    box(f"{name} flange", x - flange / 2, x + flange / 2, top - deep - 0.035, top - deep, z0, z1, M["rib"], 0.008)
    box(f"{name} top flange", x - flange / 2, x + flange / 2, top - 0.025, top, z0, z1, M["rib"], 0)
    z = z0 + stiffen / 2
    i = 0
    while z < z1:
        box(f"{name} stiffener {i}", x - flange / 2, x + flange / 2, top - deep, top, z - 0.012, z + 0.012, M["metal"], 0.003)
        if holes:
            cylinder(f"{name} hole {i}", x - web / 2 - 0.003, x + web / 2 + 0.003, top - deep / 2, z + stiffen / 2, holes, M["dark"],
                     20, horizontal="x")
        z += stiffen
        i += 1


def ibeam_x(name, x0, x1, z, top, deep, flange, web=0.04, stiffen=0.9):
    """The same section running along x: the longitudinal stringers between frames."""
    box(f"{name} web", x0, x1, top - deep, top, z - web / 2, z + web / 2, M["rib"], 0)
    box(f"{name} flange", x0, x1, top - deep - 0.025, top - deep, z - flange / 2, z + flange / 2, M["rib"], 0.006)
    x = x0 + stiffen / 2
    i = 0
    while x < x1:
        box(f"{name} stiffener {i}", x - 0.01, x + 0.01, top - deep, top, z - flange / 2, z + flange / 2, M["metal"], 0.002)
        x += stiffen
        i += 1


def pipe_run(name, x0, x1, y, z, r, top):
    """A pipe along x with collars at its joints and clamps hanging it from the deck head."""
    cylinder(name, x0, x1, y, z, r, M["pipe"], 16, horizontal="x")
    x = x0 + 0.6
    i = 0
    while x < x1:
        cylinder(f"{name} collar {i}", x - 0.03, x + 0.03, y, z, r * 1.35, M["metal"], 16, horizontal="x")
        box(f"{name} hanger {i}", x + 0.4 - 0.008, x + 0.4 + 0.008, y, top, z - 0.008, z + 0.008, M["metal"], 0)
        box(f"{name} clamp {i}", x + 0.4 - 0.025, x + 0.4 + 0.025, y - r * 1.25, y + r * 1.25, z - r * 1.25, z + r * 1.25, M["dark"], 0.006)
        x += 1.6
        i += 1


def tray_x(name, x0, x1, y, z0, z1, top):
    """A ladder cable tray along x: two side rails, rungs, a bundle of cables, drop rods."""
    for k, z in enumerate((z0, z1)):
        box(f"{name} rail {k}", x0, x1, y, y + 0.07, z - 0.012, z + 0.012, M["metal"], 0.003)
    x = x0 + 0.15
    i = 0
    while x < x1:
        box(f"{name} rung {i}", x - 0.012, x + 0.012, y, y + 0.015, z0, z1, M["metal"], 0.002)
        if i % 4 == 0:
            for z in (z0, z1):
                box(f"{name} rod {i} {z:.1f}", x - 0.007, x + 0.007, y, top, z - 0.007, z + 0.007, M["metal"], 0)
        x += 0.3
        i += 1
    for k in range(5):
        zc = z0 + (z1 - z0) * (k + 0.5) / 5
        cylinder(f"{name} cable {k}", x0, x1, y + 0.04 + 0.012 * (k % 2), zc, 0.024 if k % 3 else 0.035, M["pipe"], 10, horizontal="x")


def duct_x(name, x0, x1, y0, y1, z0, z1, top):
    """A ventilation duct along x: flanged sections, a slatted grille under every other section."""
    box(name, x0, x1, y0, y1, z0, z1, M["panel"], 0.012)
    x = x0
    i = 0
    while x < x1:
        box(f"{name} flange {i}", x - 0.018, x + 0.018, y0 - 0.018, y1 + 0.018, z0 - 0.018, z1 + 0.018, M["metal"], 0.004)
        box(f"{name} hanger {i}", x - 0.01, x + 0.01, y1, top, z0 - 0.012, z1 + 0.012, M["metal"], 0)
        if i % 2 == 0 and x + 1.0 < x1:
            box(f"{name} grille {i}", x + 0.3, x + 0.9, y0 - 0.02, y0, z0 + 0.04, z1 - 0.04, M["dark"], 0.002)
            for k in range(6):
                box(f"{name} slat {i} {k}", x + 0.34 + k * 0.1, x + 0.36 + k * 0.1, y0 - 0.03, y0 - 0.02, z0 + 0.04, z1 - 0.04, M["metal"], 0)
        x += 1.2
        i += 1


def lamp_fitting(name, x0, x1, z0, z1, y):
    """A strip light hung on drop rods: a housing, a diffuser under it, a wire guard across it."""
    c = ROOM["ceiling"]
    box(f"ceiling lamp {name} housing", x0, x1, y, y + 0.07, z0, z1, M["metal"], 0.01)
    box(f"ceiling lamp {name} diffuser", x0 + 0.03, x1 - 0.03, y - 0.015, y, z0 + 0.03, z1 - 0.03, M["lamp"], 0)
    for k, x in enumerate((x0 + 0.12, x1 - 0.12)):
        box(f"ceiling lamp {name} rod {k}", x - 0.008, x + 0.008, y + 0.07, c, (z0 + z1) / 2 - 0.008, (z0 + z1) / 2 + 0.008, M["metal"], 0)
    n = max(2, int((x1 - x0) / 0.25))
    for k in range(n + 1):
        x = x0 + 0.03 + (x1 - x0 - 0.06) * k / n
        box(f"ceiling lamp {name} guard {k}", x - 0.005, x + 0.005, y - 0.03, y - 0.02, z0, z1, M["rail"], 0)


def deck_head(bays, near, far):
    """The deck head between frames: stringers and cassette panels, with panels left off here and there
    where the crew have been at what is behind them."""
    c = ROOM["ceiling"]
    pitch = 1.5
    for b, (x0, x1) in enumerate(bays):
        z = near
        k = 0
        while z < far:
            z1 = min(z + pitch, far)
            if (b * 7 + k * 3) % 11 not in (2, 8):
                box(f"ceiling panel {b} {k}", x0 + 0.03, x1 - 0.03, c - 0.03, c, z + 0.04, z1 - 0.04, M["panel"], 0.006)
                box(f"ceiling panel {b} {k} inset", x0 + 0.1, x1 - 0.1, c - 0.04, c - 0.03, z + 0.12, z1 - 0.12, M["ceiling"], 0.002)
            z = z1
            k += 1
    z = near + pitch
    while z < far:
        ibeam_x(f"ceiling stringer {z:.1f}", bays[0][0], bays[-1][1], z, c, 0.09, 0.09)
        z += pitch


def face(a, b, t0, t1, lift0, lift1, normal):
    """A thin quad on the sloped face from a to b, as a prism profile of (y, z) points."""
    p = (a[0] + (b[0] - a[0]) * t0, a[1] + (b[1] - a[1]) * t0)
    q = (a[0] + (b[0] - a[0]) * t1, a[1] + (b[1] - a[1]) * t1)
    return [(p[0] + normal[0] * lift0, p[1] + normal[1] * lift0), (q[0] + normal[0] * lift0, q[1] + normal[1] * lift0),
            (q[0] + normal[0] * lift1, q[1] + normal[1] * lift1), (p[0] + normal[0] * lift1, p[1] + normal[1] * lift1)]


def raked_glazing(name, x0, x1, zw, sill, head, panes, skip=()):
    """A run of ship's side from x0 to x1 at z = zw: moulded lower wall with a sloped instrument deck, big
    panes of black glass leaning in by RAKE to the roof's edge, heavy pillars leaning with them, each with
    a lit cap at top and bottom. The roof's edge is RAKE inboard of the floor's, which is what the lean
    means. `skip` lists pillars a corner post replaces."""
    c = ROOM["ceiling"]
    rake = RAKE
    gs, gh = (sill, zw + 0.1), (head, zw + 0.1 - rake)
    box(f"{name} wall low", x0, x1, 0, sill, zw, zw + 0.25, M["wall"], 0)
    prism(f"{name} glass", x0, x1, [gs, gh, (head, zw + 0.14 - rake), (sill, zw + 0.14)], M["space"], 0)
    # the roof's edge beam, into which the glass and the pillars land
    prism(f"{name} crown", x0, x1, [(head - 0.04, zw + 0.24 - rake), (head - 0.04, zw + 0.02 - rake), (c, zw - 0.12 - rake), (c, zw + 0.24 - rake)], M["panel"], 0.02)
    # the instrument deck under the glass, sloped up to the sill
    prism(f"{name} deck", x0, x1, [(sill - 0.42, zw - 0.5), (sill - 0.3, zw - 0.52), (sill, zw - 0.02), (sill, zw + 0.1), (sill - 0.42, zw + 0.1)], M["metal"], 0.02)
    prism(f"{name} deck strip", x0, x1, face((sill - 0.3, zw - 0.52), (sill, zw - 0.02), 0.45, 0.52, 0.0, 0.006, (0.86, -0.5)), M["lamp"], 0)
    width = (x1 - x0) / panes
    slope = rake / (head - sill)
    for i in range(panes + 1):
        if i in skip:
            continue
        xp = x0 + width * i
        w = 0.21
        # below the sill into the deck, and up into the crown beam
        lo, hi = sill - 0.3, head + 0.02
        back_lo, back_hi = gs[1] + 0.3 * slope, gs[1] - (hi - sill) * slope
        prism(f"{name} pillar {i}", xp - w, xp + w, [(lo, back_lo), (hi, back_hi), (hi, back_hi - 0.2), (lo, back_lo - 0.24)], M["panel"], 0.03)
        foot, top = (lo, back_lo - 0.24), (hi, back_hi - 0.2)
        n = (-0.55, -0.83)
        prism(f"{name} pillar {i} face", xp - w * 0.62, xp + w * 0.62, face(foot, top, 0.14, 0.97, 0.0, 0.014, n), M["metal"], 0.006)
        for k, (t0, t1) in enumerate(((0.26, 0.29), (0.86, 0.89))):
            prism(f"{name} pillar {i} cap {k}", xp - w - 0.012, xp + w + 0.012, face(foot, top, t0, t1, 0.0, 0.03, n), M["lamp"], 0)
    # the wall under the deck: long moulded panels with a vent in some of them
    n = max(1, round((x1 - x0) / 1.7))
    for i in range(n):
        xa, xb = x0 + (x1 - x0) * i / n + 0.03, x0 + (x1 - x0) * (i + 1) / n - 0.03
        box(f"{name} panel {i}", xa, xb, 0.14, sill - 0.48, zw - 0.025, zw, M["panel"], 0.008)
        if i % 2:
            box(f"{name} vent {i}", xa + 0.2, xa + 0.8, 0.24, 0.42, zw - 0.03, zw - 0.025, M["dark"], 0)
            for k in range(4):
                box(f"{name} vent {i} slat {k}", xa + 0.2, xa + 0.8, 0.265 + k * 0.04, 0.28 + k * 0.04, zw - 0.035, zw - 0.03, M["metal"], 0)


def moulded_beam(name, x0, x1, z0, z1, y, trough=True):
    """A smooth overhead beam running along z: a chamfered box dropping from the deck head to y,
    with a panel seam down its belly and a light trough along one side."""
    c = ROOM["ceiling"]
    w = x1 - x0
    box(f"{name} body", x0 + 0.09, x1 - 0.09, y, c, z0, z1, M["panel"], 0.01)
    for k, (a, b, m) in enumerate(((x0, x0 + 0.09, M["metal"]), (x1 - 0.09, x1, M["metal"]))):
        box(f"{name} shoulder {k}", a, b, y + 0.09, c, z0, z1, m, 0.006)
    box(f"{name} seam", x0 + w * 0.5 - 0.008, x0 + w * 0.5 + 0.008, y - 0.004, y, z0, z1, M["dark"], 0)
    pitch = 1.6
    z = z0 + pitch
    i = 0
    while z < z1:
        box(f"{name} joint {i}", x0 + 0.09, x1 - 0.09, y - 0.004, y, z - 0.01, z + 0.01, M["dark"], 0)
        z += pitch
        i += 1
    if trough:
        box(f"{name} trough", x1 + 0.0, x1 + 0.03, y + 0.1, y + 0.14, z0, z1, M["lamp"], 0)


def overhead_panels(bays, near, far):
    """The deck head between the beams: flush moulded panels with a shallow inset and an access plate here
    and there, none of them left off, because this end of the ship is finished."""
    c = ROOM["ceiling"]
    pitch = 1.6
    for b, (x0, x1) in enumerate(bays):
        z = near
        k = 0
        while z < far:
            z1 = min(z + pitch, far)
            box(f"ceiling panel {b} {k}", x0 + 0.02, x1 - 0.02, c - 0.03, c, z + 0.02, z1 - 0.02, M["panel"], 0.008)
            box(f"ceiling panel {b} {k} inset", x0 + 0.12, x1 - 0.12, c - 0.038, c - 0.03, z + 0.12, z1 - 0.12, M["ceiling"], 0.002)
            if (b + k) % 4 == 1:
                xc, zc = (x0 + x1) / 2, (z + z1) / 2
                cylinder(f"ceiling vent {b} {k}", xc, zc, c - 0.06, c - 0.038, 0.14, M["metal"], 20)
                cylinder(f"ceiling vent {b} {k} throat", xc, zc, c - 0.066, c - 0.06, 0.1, M["dark"], 20)
            z = z1
            k += 1


def plan_slab(name, pts, y0, y1, m, bevel=0.0):
    """A slab of any four-sided plan, given as (x, z) points."""
    verts = [(x, y0, z) for x, z in pts] + [(x, y1, z) for x, z in pts]
    area = sum(pts[i][0] * pts[(i + 1) % 4][1] - pts[(i + 1) % 4][0] * pts[i][1] for i in range(4))
    if area > 0:
        verts = [(x, y0, z) for x, z in pts[::-1]] + [(x, y1, z) for x, z in pts[::-1]]
    return solid(name, verts, [(3, 2, 1, 0), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)], m, bevel)


def roof_corner(d):
    """Where the port and forward roof lines meet, d inboard of the floor's corner at (-5, port)."""
    return (-5.0 + d * (math.sqrt(2) - 1), ROOM["port"] - d)


def roof_forward(d, x):
    """The forward roof line, d inboard of the floor's, at this x."""
    return (x, x + 16.0 - d * math.sqrt(2))


def perimeter_soffit(aft_end):
    """The roof's edge: two stepped soffits, along the port side and round the forward side, mitred where
    they meet and stopping at the crown beam the leaning glass lands on, so the roof is smaller than the
    floor by the lean. A light trough runs in the inner step of both."""
    c, port = ROOM["ceiling"], ROOM["port"]
    far = -14.0
    low = 2.86
    d0, d1, d2 = RAKE - 0.1, RAKE + 1.0, RAKE + 1.3
    for tag, y0, a, b in (("low", low, d0, d1), ("high", low + 0.17, d1, d2)):
        plan_slab(f"overhead soffit port {tag}", [roof_corner(a), (aft_end, port - a), (aft_end, port - b), roof_corner(b)], y0, c, M["panel"], 0.012)
        plan_slab(f"overhead soffit fwd {tag}", [roof_corner(a), roof_corner(b), roof_forward(b, far), roof_forward(a, far)], y0, c, M["panel"], 0.012)
    t0, t1 = d1 - 0.1, d1 - 0.03
    plan_slab("overhead trough port", [roof_corner(t0), (aft_end, port - t0), (aft_end, port - t1), roof_corner(t1)], low - 0.012, low, M["lamp"])
    plan_slab("overhead trough fwd", [roof_corner(t0), roof_corner(t1), roof_forward(t1, far), roof_forward(t0, far)], low - 0.012, low, M["lamp"])
    # seams square to each run, an access plate now and then
    x = roof_corner(d0)[0] + 1.3
    i = 0
    while x < aft_end:
        box(f"overhead soffit seam {i}", x - 0.007, x + 0.007, low - 0.004, low, port - d1 + 0.02, port - d0, M["dark"], 0)
        if i % 2 == 0:
            box(f"overhead soffit plate {i}", x + 0.25, x + 0.95, low - 0.012, low, port - d1 + 0.3, port - d0 - 0.2, M["metal"], 0.004)
        x += 1.5
        i += 1
    for j in range(5):
        xa = roof_corner(d0)[0] - 1.2 - j * 1.5
        a, b = roof_forward(d0, xa), roof_forward(d1 - 0.12, xa + (d1 - 0.12 - d0))
        run = 0.012
        plan_slab(f"overhead soffit fwd seam {j}", [(a[0] - run, a[1] + run), (a[0] + run, a[1] - run), (b[0] + run, b[1] - run), (b[0] - run, b[1] + run)],
                  low - 0.004, low, M["dark"])


def unturned(x, z):
    """A camera-axes (x, z) back in ship coordinates: the inverse of `turned`."""
    dx, dz = x - PIVOT[0], z - PIVOT[1]
    c, s = math.cos(YAW), math.sin(YAW)
    return PIVOT[0] + dx * c + dz * s, PIVOT[1] - dx * s + dz * c


def corner_post(sill, head):
    """The post in the room's corner. It takes the roof's mitre to the floor's corner, and it faces the
    camera: both of its ends lie on one line of sight's plane, so it stands upright in the picture, and
    it shows one flat face with chamfered shoulders, where the posts along each run show their edges."""
    top = turned(*roof_corner(RAKE))
    foot_z = turned(-5.0, ROOM["port"])[1]
    ratio = top[0] / top[1]
    foot = (ratio * foot_z, foot_z)
    norm = math.hypot(ratio, 1.0)
    right = (1.0 / norm, -ratio / norm)
    toward = (-ratio / norm, -1.0 / norm)

    def at(y, across, near):
        t = (y - sill) / (head - sill)
        x = foot[0] + (top[0] - foot[0]) * t + right[0] * across + toward[0] * near
        z = foot[1] + (top[1] - foot[1]) * t + right[1] * across + toward[1] * near
        sx, sz = unturned(x, z)
        return (sx, y, sz)

    def band(name, y0, y1, section, m, bevel=0.0):
        n = len(section)
        verts = [at(y, a, b) for y in (y0, y1) for a, b in section]
        faces = [tuple(range(n - 1, -1, -1)), tuple(range(n, 2 * n))] + [(k, (k + 1) % n, n + (k + 1) % n, n + k) for k in range(n)]
        return solid(name, verts, faces, m, bevel)

    lo, hi = sill - 0.3, head + 0.02
    w = 0.21
    band("forward glazing corner post", lo, hi, [(-w, 0.1), (w, 0.1), (w + 0.08, 0.0), (w + 0.08, -0.32), (-w - 0.08, -0.32), (-w - 0.08, 0.0)], M["panel"], 0.02)
    span = hi - lo
    band("forward glazing corner post face", lo + span * 0.14, lo + span * 0.97, [(-w * 0.62, 0.114), (w * 0.62, 0.114), (w * 0.62, 0.09), (-w * 0.62, 0.09)], M["metal"], 0.006)
    for k, t in enumerate((0.26, 0.86)):
        band(f"forward glazing corner post cap {k}", lo + span * t, lo + span * (t + 0.03),
             [(-w - 0.012, 0.13), (w + 0.012, 0.13), (w + 0.09, 0.015), (w + 0.09, -0.32), (-w - 0.09, -0.32), (-w - 0.09, 0.015)], M["lamp"])


def monitor_cowl(x0, x1, z, low):
    """A monolithic faceted cowl hung from the deck head, its raked face holding one big monitor in a
    deep bezel, a stepped wing running aft from it with a light strip."""
    c = ROOM["ceiling"]
    prism("overhead cowl", x0, x1, [(c, z + 0.9), (low, z + 0.35), (low, z - 0.05), (low + 0.82, z - 0.42), (c, z - 0.5)], M["panel"], 0.03)
    a, b = (low, z - 0.05), (low + 0.82, z - 0.42)
    n = (-0.41, -0.91)
    xc = (x0 + x1) / 2
    prism("overhead cowl recess", xc - 0.62, xc + 0.62, face(a, b, 0.07, 0.93, 0.0, 0.03, n), M["metal"], 0.006)
    # one big television in the cowl, with its own banks of push buttons on either hand
    along = Vector((0.0, b[0] - a[0], b[1] - a[1])).normalized()
    out = Vector((0.0, n[0], n[1]))
    mid = (xc, a[0] + (b[0] - a[0]) * 0.51, a[1] + (b[1] - a[1]) * 0.51)
    plate_box("status board bezel", mid, (1, 0, 0), along, out, 0.46, 0.355, 0.03, 0.05, M["panel"], 0.012)
    tube_face("status board well", mid, (1, 0, 0), along, out, 0.415, 0.315, 0.06, 0.051, M["dark"])
    tube_face("status board screen", mid, (1, 0, 0), along, out, 0.4, 0.3, 0.05, 0.052, M["glow"])
    for side in (-1, 1):
        for r in range(6):
            for k in range(2):
                plate_box(f"status board button {side} {r} {k}", Vector(mid) + Vector((side * (0.5 + k * 0.05), 0, 0)) + along * (0.24 - r * 0.096), (1, 0, 0), along, out,
                          0.018, 0.03, 0.03, 0.04, M["tell"])
    prism("overhead cowl tell", xc - 0.14, xc + 0.14, face(a, b, 0.03, 0.06, 0.0, 0.015, n), M["lamp"], 0)
    for side, xs in ((-1, x0), (1, x1)):
        prism(f"overhead cowl cheek {side}", xs - 0.02, xs + 0.02, [(c, z + 0.7), (low + 0.1, z + 0.3), (low + 0.1, z - 0.02), (low + 0.8, z - 0.36), (c, z - 0.44)], M["metal"], 0.006)


def shell():
    c, port, aftp, fr, aft = ROOM["ceiling"], ROOM["port"], ROOM["aftPort"], ROOM["frame"], ROOM["aft"]
    near = 1.0
    t = 0.25
    # deck plates on a 1.4 m pitch, with the plot's working area as its own darker inset
    pitch, seam = 1.4, 0.025
    x = -12.6
    while x < aft:
        z = near
        while z < port:
            box(f"floor plate {x:+.1f} {z:.1f}", x + seam, min(x + pitch, aft) - seam, -0.03, 0.0,
                z + seam, min(z + pitch, port) - seam, M["floor"], 0.006)
            z += pitch
        x += pitch
    box("floor seam bed", -13, aft, -0.06, -0.03, near, port, M["dark"], 0)
    box("command island", -2.6, 3.4, 0.0, 0.012, 4.6, 8.9, M["inset"], 0.004)
    box("ceiling", -14, aft + t, c, c + t, near, port + t, M["ceiling"], 0)
    box("aft wall", aft, aft + t, 0, c, near, aftp, M["wall"], 0)

    # the far side: big raked panes running to the hull frame, which is the run's last pillar
    raked_glazing("port glazing", -5.0, fr - 0.34, port, 1.22, 2.78, 3, skip=(0, 3))
    # the forward glazing: the same run, turned to face back along the deck
    forward_a, forward_b = (-5.0, port), (-12.0, port - 7.0)
    mid = ((forward_a[0] + forward_b[0]) / 2, (forward_a[1] + forward_b[1]) / 2)
    run = math.hypot(forward_a[0] - forward_b[0], forward_a[1] - forward_b[1])
    parts = made(raked_glazing, "forward glazing", mid[0] - run / 2, mid[0] + run / 2, mid[1], 1.15, 2.78, 4, (4,))
    spin(parts, (mid[0], 0.0, mid[1]), math.radians(45))
    corner_post(1.22, 2.78)

    # the heavy hull frame, finished forward: the last and largest of the window pillars. It stands upright
    # to the sill, then leans in at the glass's own lean, in one piece, up into the overhead's beam
    box("aft port wall", fr, aft + t, 0, c, aftp, aftp + t, M["wall"], 0)
    leg_z = aftp - 0.15
    slope = RAKE / (2.78 - 1.22)
    waist, top = (1.22, leg_z), (c - 0.5, leg_z - (c - 0.5 - 1.22) * slope)
    prism("hull frame leg", fr - 0.34, fr + 0.34, [(0.0, leg_z), waist, top, (c, top[1]), (c, port), (0.0, port)], M["panel"], 0.03)
    n = (-0.593, -0.805)
    prism("hull frame leg upper face", fr - 0.24, fr + 0.24, face(waist, top, 0.2, 0.82, 0.0, 0.02, n), M["metal"], 0.008)
    # lit bands level with the pillars' caps, and one level with the sill's light strip
    for k, tt in enumerate((0.128, 0.892)):
        prism(f"hull frame leg cap {k}", fr - 0.35, fr + 0.35, face(waist, top, tt, tt + 0.02, 0.0, 0.032, n), M["lamp"], 0)
    box("hull frame leg sill band", fr - 0.35, fr + 0.35, 1.06, 1.09, leg_z - 0.03, leg_z + 0.1, M["lamp"], 0)
    box("hull frame leg face", fr - 0.24, fr + 0.24, 0.1, 1.02, leg_z - 0.025, leg_z, M["metal"], 0.008)
    # the damage-control point: a flush locker for breathing sets, and an extinguisher in its bracket
    box("damage locker door", fr - 0.21, fr + 0.03, 0.16, 0.98, leg_z - 0.04, leg_z - 0.025, M["panel"], 0.005)
    box("damage locker handle", fr - 0.03, fr + 0.01, 0.5, 0.64, leg_z - 0.055, leg_z - 0.04, M["rail"], 0.004)
    box("damage locker tell", fr - 0.17, fr - 0.01, 0.9, 0.935, leg_z - 0.046, leg_z - 0.04, M["lamp"], 0)
    box("damage extinguisher bracket", fr + 0.06, fr + 0.22, 0.62, 0.66, leg_z - 0.1, leg_z - 0.025, M["rib"], 0.005)
    cylinder("damage extinguisher", fr + 0.14, leg_z - 0.1, 0.16, 0.76, 0.06, M["rail"], 16, 0.01)
    moulded_beam("overhead hull frame", fr - 0.34, fr + 0.34, near, leg_z, c - 0.5)

    # the overhead forward of it: beams, flush panels, the soffit over the windows, the cowl
    beams = [(-5.9, -5.5), (-3.65, -3.25), (-1.2, -0.8), (2.0, 2.3)]
    for i, (x0, x1) in enumerate(beams):
        moulded_beam(f"overhead frame {i}", x0, x1, near, 9.0, c - 0.22, trough=i in (1, 3))
    edges = [-14.0, -5.9, -5.5, -3.65, -3.25, -1.2, -0.8, 2.0, 2.3, fr - 0.34]
    bays = [(edges[i], edges[i + 1]) for i in range(0, len(edges) - 1, 2)] + [(edges[-1], fr)]
    overhead_panels([b for b in bays if b[1] - b[0] > 0.5], near, 9.0)
    perimeter_soffit(fr - 0.34)
    monitor_cowl(0.95, 2.4, 8.0, 2.14)
    # the plot's own coffer, a rectangle of lit reveal let into the panels over it
    for k, (x0, x1, z0, z1) in enumerate(((-1.0, 2.0, 5.2, 5.26), (-1.0, 2.0, 7.9, 7.96), (-1.0, -0.94, 5.2, 7.96))):
        box(f"ceiling coffer light {k}", x0, x1, c - 0.05, c - 0.035, z0, z1, M["lamp"], 0)

    # aft of the frame it is the working end, and the services show
    for i, xr in enumerate([4.7, 6.3]):
        ibeam_z(f"overhead frame aft {i}", xr, near, aftp, c, 0.27, 0.2)
    deck_head([(fr + 0.34, 4.7), (4.7, 6.3), (6.3, aft)], near, aftp)
    box("aft overhead trunk", fr + 0.34, aft, 2.5, c, 8.65, aftp, M["metal"], 0.03)
    for k in range(4):
        xa = fr + 0.34 + k * (aft - fr - 0.34) / 4
        box(f"aft overhead trunk panel {k}", xa + 0.05, xa + (aft - fr - 0.34) / 4 - 0.05, 2.56, 3.05, 8.63, 8.65, M["panel"], 0.006)
    pipe_run("overhead aft pipe 0", fr + 0.34, aft, 2.42, 9.5, 0.06, 2.5)
    pipe_run("overhead aft pipe 1", fr + 0.34, aft, 2.3, 9.62, 0.045, 2.5)
    tray_x("overhead tray aft", fr + 0.34, aft, 2.78, 6.9, 7.35, c)
    lamp_fitting("aft", 4.85, 6.1, 8.15, 8.45, 2.34)


def flight_dais():
    """The raised dais under the forward glazing: helm and navigator at low consoles facing out,
    so nothing stands between them and the glass. A rail on its edge, broken for the steps."""
    th = 0.42
    deep = 2.55
    a, b = (-5.0, ROOM["port"]), (-12.0, ROOM["port"] - 7.0)
    # Its edge runs parallel to the glass, built along +x and turned with it.
    mx, mz = (a[0] + b[0]) / 2 + deep * 0.3536, (a[1] + b[1]) / 2 - deep * 0.3536
    run = math.hypot(a[0] - b[0], a[1] - b[1]) + 2.0
    fx0, fx1 = mx - run / 2, mx + run / 2

    def platform():
        box("dais deck", fx0, fx1, 0.0, th, mz - deep / 2, mz + deep / 2, M["floor"], 0)
        box("dais fascia", fx0, fx1, 0.05, th - 0.04, mz - deep / 2 - 0.03, mz - deep / 2, M["panel"], 0.01)
        box("dais nosing", fx0, fx1, th - 0.04, th, mz - deep / 2 - 0.06, mz - deep / 2 + 0.05, M["rail"], 0.01)
        box("dais kick light", fx0, fx1, 0.02, 0.05, mz - deep / 2 - 0.035, mz - deep / 2 - 0.02, M["lamp"], 0)
        gap0, gap1 = mx + 0.2, mx + 1.7
        for k in range(2):
            box(f"dais step {k}", gap0, gap1, 0.0, th * (k + 1) / 3, mz - deep / 2 - 0.3 * (2 - k), mz - deep / 2,
                M["metal"], 0.01)
        ze = mz - deep / 2 + 0.08
        for x0, x1 in ((gap1 + 0.1, fx1),):
            cylinder(f"dais rail {x0:+.1f}", x0, x1, th + 0.95, ze, 0.028, M["rail"], 16, horizontal="x")
            cylinder(f"dais rail mid {x0:+.1f}", x0, x1, th + 0.52, ze, 0.016, M["rail"], 12, horizontal="x")
            n = max(1, round((x1 - x0) / 1.25))
            for i in range(n + 1):
                xp = x0 + (x1 - x0) * i / n
                cylinder(f"dais post {x0:+.1f} {i}", xp, ze, th, th + 0.95, 0.024, M["rail"], 12)

    parts = made(platform)
    spin(parts, (mx, 0.0, mz), math.radians(45))
    for name, xc, zc in (("helm", -5.88, 7.58), ("navigator", -4.33, 9.13)):
        with lifted(dy=th):
            parts = made(flight_console, name, xc, zc)
        spin(parts, (xc, 0.0, zc), math.radians(45))


def plate_box(name, c, u, v, n, hw, hh, d0, d1, m, bevel=0.0):
    """A box on any face: centred on c, hw along u, hh along v, from d0 to d1 along the normal n."""
    c, u, v, n = Vector(c), Vector(u), Vector(v), Vector(n)
    ring = [(-hw, -hh), (hw, -hh), (hw, hh), (-hw, hh)]
    if u.cross(v).dot(n) < 0:
        ring = ring[::-1]
    verts = [c + u * a + v * b + n * d for d in (d0, d1) for a, b in ring]
    bevel = min(bevel, 0.4 * min(2 * hw, 2 * hh, abs(d1 - d0)))
    return solid(name, verts, [(3, 2, 1, 0), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)], m, bevel)


def tube_face(name, c, u, v, n, hw, hh, r, d, m):
    """A picture tube's face, seen as a television's is: a rectangle with well rounded corners."""
    c, u, v, n = Vector(c), Vector(u), Vector(v), Vector(n)
    pts = []
    for sx, sy, a0 in ((1, 1, 0), (-1, 1, 90), (-1, -1, 180), (1, -1, 270)):
        for k in range(7):
            a = math.radians(a0 + k * 15)
            pts.append((sx * (hw - r) + r * math.cos(a), sy * (hh - r) + r * math.sin(a)))
    if u.cross(v).dot(n) < 0:
        pts = pts[::-1]
    return solid(name, [c + u * a + v * b + n * d for a, b in pts], [tuple(range(len(pts)))], m)


def crt_module(name, c, u, v, n, tube_w, tube_h, depth=0.0):
    """A late-century colour television let into a panel: a 4:3 tube with rounded corners sunk in a dark
    well, in a thick plastic bezel with a column of push buttons down its right and a chin of knobs
    under it. With `depth` its housing stands out behind the panel, tapering as a tube's does. The tube is
    named `name`, the bezel `name frame`."""
    c, u, v, n = Vector(c), Vector(u), Vector(v), Vector(n)
    tw, th = tube_w / 2, tube_h / 2
    side, chin, edge = 0.075, 0.06, 0.03
    hw, hh = tw + (edge + side) / 2, th + (edge + chin) / 2
    mid = c + u * ((side - edge) / 2) - v * ((chin - edge) / 2)
    if depth:
        ring = [(-hw, -hh, 0.0), (hw, -hh, 0.0), (hw, hh, 0.0), (-hw, hh, 0.0)]
        verts = [mid + u * a + v * b for a, b, _ in ring] + [mid - n * depth + u * (a * 0.62) + v * (b * 0.62) for a, b, _ in ring]
        solid(f"{name} housing", verts, [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)], M["metal"], 0.012)
    plate_box(f"{name} frame", mid, u, v, n, hw, hh, 0.0, 0.05, M["panel"], 0.014)
    tube_face(f"{name} well", c, u, v, n, tw + 0.016, th + 0.016, 0.05, 0.052, M["dark"])
    tube_face(name, c, u, v, n, tw, th, 0.038, 0.054, M["glow"])
    x = tw + 0.042
    for r in range(5):
        y = th - 0.03 - r * (2 * th - 0.06) / 4
        plate_box(f"{name} button {r}", c + u * x + v * y, u, v, n, 0.019, 0.014, 0.05, 0.058, M["tell"])
    for k in range(4):
        plate_box(f"{name} knob {k}", c + u * (-tw + 0.05 + k * 0.075) - v * (th + 0.034), u, v, n, 0.014, 0.012, 0.05, 0.062, M["rail"])


def button_board(name, x0, x1, y, z0, z1, cols, rows):
    """A flat board of physical push buttons lying on a worktop, some lit, some dark, in no neat order."""
    box(f"{name} board", x0, x1, y, y + 0.018, z0, z1, M["panel"], 0.005)
    dx, dz = (x1 - x0 - 0.02) / cols, (z1 - z0 - 0.02) / rows
    for r in range(rows):
        for k in range(cols):
            xk, zk = x0 + 0.01 + k * dx, z0 + 0.01 + r * dz
            lit = (r * 5 + k * 3) % 7 in (0, 3)
            box(f"{name} key {r} {k}", xk + 0.004, xk + dx - 0.004, y + 0.018, y + 0.027, zk + 0.004, zk + dz - 0.004, M["tell"], 0)


def flight_console(name, xc, zc):
    """A seat at a low desk with an instrument cowl across its back: two screens let into the cowl's
    raked face, and the helm's control column or the navigator's chart lamp on the worktop. Its top
    stays low enough for the crew to see out over it. The seat looks +z."""
    x0, x1 = xc - 0.62, xc + 0.62
    zf = zc - 0.08 + SEAT_BACK
    depth = 0.72
    desk_frame(name, x0, x1, zf, depth)
    box(f"{name} back panel", x0, x1, 0.0, 0.755, zf + depth, zf + depth + 0.04, M["panel"], 0.008)
    rake = lambda t: (0.755 + 0.36 * t, zf + 0.34 + 0.32 * t)   # along the cowl's face, 0 at the worktop
    a, b = rake(0.0), rake(1.0)
    prism(f"{name} cowl", x0, x1, [a, b, (b[0], zf + depth + 0.04), (a[0], zf + depth + 0.04)], M["metal"], 0.012)
    prism(f"{name} cowl brow", x0 - 0.02, x1 + 0.02, [b, (b[0] + 0.03, b[1] - 0.06), (b[0] + 0.05, zf + depth + 0.04),
                                                     (b[0], zf + depth + 0.04)], M["rib"], 0.008)
    along = Vector((0.0, 0.36, 0.32)).normalized()
    out = Vector((0.0, 0.664, -0.747))    # the face's normal, towards the operator and up
    for s in (-1, 1):
        y, z = rake(0.58)
        crt_module(f"{name} screen {s}", (xc + s * 0.31 - 0.02, y, z), (1, 0, 0), along, out, 0.38, 0.285, depth=0.34)
    keyboard(name, xc, zf, 0.755)
    button_board(f"{name} left", xc - 0.59, xc - 0.27, 0.755, zf + 0.02, zf + 0.26, 7, 5)
    if name != "helm":
        button_board(f"{name} right", xc + 0.27, xc + 0.59, 0.755, zf + 0.02, zf + 0.26, 7, 5)
    # the microphone stands in the gap between the screens, so it covers neither
    gooseneck_mic(name, xc, 0.755, zf + 0.3)
    if name == "helm":
        cylinder("helm column", xc, zf + 0.26, 0.755, 0.95, 0.03, M["rail"], 12)
        box("helm yoke", xc - 0.16, xc + 0.16, 0.95, 0.985, zf + 0.24, zf + 0.28, M["rib"], 0.01)
        for k in (-1, 1):
            box(f"helm yoke grip {k}", xc + k * 0.16 - 0.018, xc + k * 0.16 + 0.018, 0.9, 1.03, zf + 0.24, zf + 0.28, M["dark"], 0.008)
        box("helm throttle quadrant", xc + 0.38, xc + 0.56, 0.755, 0.83, zf + 0.04, zf + 0.26, M["panel"], 0.01)
        for k in range(2):
            box(f"helm throttle {k}", xc + 0.42 + k * 0.08, xc + 0.44 + k * 0.08, 0.83, 0.92, zf + 0.13, zf + 0.15, M["rail"], 0.003)
    elif name == "navigator":
        box(f"{name} chart", xc - 0.58, xc - 0.3, 0.755, 0.76, zf + 0.04, zf + 0.3, M["paper"], 0.001)
        cylinder(f"{name} lamp post", xc + 0.5, zf + 0.25, 0.755, 1.02, 0.01, M["metal"], 8)
        frustum(f"{name} lamp shade", (xc + 0.36, xc + 0.56, 0.97, 1.04, zf + 0.12), (xc + 0.42, xc + 0.54, 1.0, 1.04, zf + 0.3), M["metal"], 0.006)
    bridge_seat(f"{name} chair", xc, zc - 0.08)
    seated_crew(f"{name} crew", xc, zc - 0.08)


def near_stations():
    """Two low stations in the foreground, crew at them with their backs to the camera, so the deck has a
    near side and the picture looks over shoulders into the room."""
    flight_console("near port", -4.0, 4.7)
    flight_console("near aft", 4.4, 5.4)


def hatch(b):
    x0, x1, h = 6.1, 7.05, 2.0
    box("hatch frame left", x0 - 0.16, x0, 0, h + 0.16, b - 0.12, b, M["metal"], 0.03)
    box("hatch frame right", x1, x1 + 0.16, 0, h + 0.16, b - 0.12, b, M["metal"], 0.03)
    box("hatch frame top", x0, x1, h, h + 0.16, b - 0.12, b, M["metal"], 0.03)
    box("hatch door", x0 + 0.02, x1 - 0.02, 0.02, h - 0.02, b - 0.06, b, M["panel"], 0.06)
    box("hatch door plate", x0 + 0.14, x1 - 0.14, 0.3, h - 0.3, b - 0.08, b - 0.06, M["metal"], 0.02)
    xc = (x0 + x1) / 2
    torus("hatch wheel", xc, 1.1, b - 0.15, 0.2, 0.025, M["rib"])
    cylinder("hatch wheel hub", b - 0.17, b - 0.08, 1.1, xc, 0.05, M["rib"], horizontal="z")
    for a in range(3):
        spoke = box(f"hatch wheel spoke {a}", xc - 0.2, xc + 0.2, 1.085, 1.115, b - 0.16, b - 0.14, M["rib"], 0.004)
        spin([spoke], (xc, 1.1, b - 0.15), a * math.pi / 3, axis="depth")
    box("hatch light", x0 + 0.3, x1 - 0.3, h + 0.2, h + 0.27, b - 0.08, b, M["lamp"], 0.01)


# ---------------------------------------------------------------- the command island

def plotting_table():
    """The ship's plot, at deck scale: a square-cornered table with a radar scope inset."""
    x0, x1, z0, z1 = TABLE
    cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
    box("chart table kick", x0 + 0.3, x1 - 0.3, 0.0, 0.08, z0 + 0.3, z1 - 0.3, M["dark"], 0.01)
    box("chart table plinth", x0 + 0.4, x1 - 0.4, 0.08, 0.74, z0 + 0.4, z1 - 0.4, M["table"], 0.02)
    for i in range(4):
        xp = x0 + 0.55 + i * (x1 - x0 - 1.1) / 3
        box(f"chart plinth panel {i}", xp - 0.28, xp + 0.28, 0.16, 0.66, z0 + 0.38, z0 + 0.4, M["metal"], 0.008)
    for side in (-1, 1):
        xs = x0 + 0.4 if side < 0 else x1 - 0.4
        lo, hi = sorted((xs, xs + side * 0.02))
        for zp in (cz - 0.4, cz + 0.4):
            box(f"chart plinth side panel {side} {zp:.1f}", lo, hi, 0.16, 0.66, zp - 0.3, zp + 0.3, M["metal"], 0.008)
    prism("chart table top", x0, x1,
          [(0.74, z0 + 0.4), (0.84, z0), (0.97, z0), (0.97, z1), (0.84, z1), (0.74, z1 - 0.4)], M["table"], 0.025)
    # the scope: a dark well, the glowing plot, a ring and cross for the radar sweep
    box("chart top well", x0 + 0.15, x1 - 0.15, 0.955, 0.975, z0 + 0.15, z1 - 0.15, M["dark"], 0.004)
    box("chart top glow", x0 + 0.22, x1 - 0.22, 0.965, 0.978, z0 + 0.22, z1 - 0.22, M["plot"], 0.0)
    torus("chart scope ring", cx, 0.98, cz, 0.85, 0.008, M["dark"], facing="y")
    box("chart scope cross x", x0 + 0.3, x1 - 0.3, 0.978, 0.982, cz - 0.006, cz + 0.006, M["dark"], 0)
    box("chart scope cross z", cx - 0.006, cx + 0.006, 0.978, 0.982, z0 + 0.3, z1 - 0.3, M["dark"], 0)
    # control strips on all four rims, keys and a few lamps
    for i, (zf, zb) in enumerate([(z0 + 0.02, z0 + 0.13), (z1 - 0.13, z1 - 0.02)]):
        box(f"chart control strip {i}", cx - 1.2, cx + 1.2, 0.97, 1.0, zf, zb, M["metal"], 0.006)
        for k in range(10):
            xk = cx - 1.08 + k * 0.24
            box(f"chart key {i} {k}", xk - 0.07, xk + 0.07, 1.0, 1.012, zf + 0.025, zb - 0.025,
                M["lamp"] if k % 3 == 0 else M["rib"], 0.003)
    for side in (-1, 1):
        xs = x0 + 0.02 if side < 0 else x1 - 0.13
        box(f"chart end strip {side}", xs, xs + 0.11, 0.97, 1.0, z0 + 0.5, z1 - 0.5, M["metal"], 0.006)
    for xs in (x0 + 0.08, x1 - 0.08):
        for zs in (z0 + 0.08, z1 - 0.08):
            cylinder(f"holo emitter {xs:+.1f} {zs:.1f}", xs, zs, 0.97, 1.04, 0.045, M["metal"], 16, 0.006)
            cylinder(f"holo emitter lens {xs:+.1f} {zs:.1f}", xs, zs, 1.04, 1.05, 0.03, M["lamp"], 16)


# ---------------------------------------------------------------- stations

def crt(name, xc, z_front, y0, w=0.5, h=0.4, depth=0.3):
    """A desk monitor standing on its own base: base plate, neck, tapered housing, a bezel with a
    control chin, and the screen set inside the bezel. Its back stays z_front + depth clear."""
    yb = y0 + 0.08
    box(f"{name} base", xc - w * 0.3, xc + w * 0.3, y0, y0 + 0.02, z_front + 0.03, z_front + 0.24, M["dark"], 0.008)
    box(f"{name} neck", xc - 0.045, xc + 0.045, y0 + 0.02, yb + 0.03, z_front + 0.1, z_front + 0.17, M["metal"], 0.01)
    frustum(f"{name} housing", (xc - w / 2, xc + w / 2, yb, yb + h, z_front),
            (xc - w * 0.33, xc + w * 0.33, yb + 0.04, yb + h * 0.8, z_front + depth), M["metal"], 0.018)
    box(f"{name} bezel", xc - w / 2 + 0.012, xc + w / 2 - 0.012, yb + 0.012, yb + h - 0.012,
        z_front - 0.014, z_front, M["dark"], 0.005)
    box(f"{name} screen", xc - w / 2 + 0.05, xc + w / 2 - 0.05, yb + 0.065, yb + h - 0.04,
        z_front - 0.018, z_front - 0.014, M["glow"], 0)
    for k in range(3):
        cylinder(f"{name} knob {k}", z_front - 0.03, z_front - 0.014, yb + 0.035, xc + w / 2 - 0.06 - k * 0.05, 0.012,
                 M["rail"], 10, horizontal="z")


def arm_monitor(name, xc, z_front, y_mid, mount_z, mount_y, turn=0.0, w=0.54, h=0.42, depth=0.32):
    """A chunky hooded monitor on a jointed arm: a heavy post at the mount, two arm links with
    knuckles, a tapered body, a hood over the screen, a thick bezel with a row of keys down each side
    and the screen let into it. `turn` swings the head about its arm's end, towards the operator."""
    y0, y1 = y_mid - h / 2, y_mid + h / 2
    zb = z_front + depth
    elbow = (xc + 0.16, (mount_y + y_mid) / 2 + 0.03, (mount_z + zb) / 2)
    limb(f"{name} arm low", (xc + 0.16, mount_y, mount_z), elbow, 0.03, M["metal"])
    limb(f"{name} arm high", elbow, (xc, y_mid - 0.02, zb + 0.03), 0.026, M["metal"])
    ball(f"{name} elbow", elbow, 0.042, M["rib"])
    cylinder(f"{name} mount", xc + 0.16, mount_z, mount_y - 0.06, mount_y + 0.02, 0.05, M["rib"], 14)

    def head():
        frustum(f"{name} housing", (xc - w / 2, xc + w / 2, y0, y1, z_front), (xc - w * 0.3, xc + w * 0.3, y0 + 0.05, y1 - 0.08, zb),
                M["metal"], 0.018)
        box(f"{name} bezel", xc - w / 2 - 0.015, xc + w / 2 + 0.015, y0 - 0.015, y1 + 0.015, z_front - 0.04, z_front, M["panel"], 0.008)
        box(f"{name} screen well", xc - w / 2 + 0.045, xc + w / 2 - 0.045, y0 + 0.02, y1 - 0.02, z_front - 0.043, z_front - 0.04, M["dark"], 0)
        tube_face(f"{name} screen", (xc, y_mid, z_front), (1, 0, 0), (0, 1, 0), (0, 0, -1), w / 2 - 0.06, h / 2 - 0.035, 0.035, 0.046, M["glow"])
        for side in (-1, 1):
            for r in range(5):
                yk = y0 + 0.05 + r * (h - 0.1) / 4
                box(f"{name} side {side} key {r}", xc + side * (w / 2 - 0.012) - 0.012, xc + side * (w / 2 - 0.012) + 0.012,
                    yk - 0.012, yk + 0.012, z_front - 0.046, z_front - 0.04, M["tell"], 0)
        # the hood shades the screen: a top and two cheeks standing proud of the bezel
        box(f"{name} hood top", xc - w / 2 - 0.02, xc + w / 2 + 0.02, y1 + 0.015, y1 + 0.04, z_front - 0.11, z_front + 0.02, M["rib"], 0.006)
        for side in (-1, 1):
            x = xc + side * (w / 2 + 0.02)
            prism(f"{name} hood cheek {side}", x - 0.012, x + 0.012,
                  [(y0, z_front + 0.0), (y1 + 0.03, z_front + 0.0), (y1 + 0.03, z_front - 0.11), (y0 + h * 0.5, z_front - 0.04)], M["rib"], 0)
        box(f"{name} yoke", xc - 0.05, xc + 0.05, y_mid - 0.06, y_mid + 0.06, zb, zb + 0.05, M["rib"], 0.008)

    if turn:
        spin(made(head), (xc, 0.0, zb), turn)
    else:
        head()


def gooseneck_mic(name, x, y, z, reach=0.3):
    """A desk microphone on a jointed gooseneck, bending up and towards the operator (-z)."""
    points = [(x, y, z), (x, y + 0.16, z - 0.03), (x - 0.03, y + 0.27, z - reach * 0.5), (x - 0.05, y + 0.31, z - reach)]
    for k in range(3):
        limb(f"{name} gooseneck {k}", points[k], points[k + 1], 0.012, M["rail"], verts=8)
    ball(f"{name} mic", points[-1], 0.03, M["dark"], rings=6, segments=10)
    cylinder(f"{name} mic base", x, z, y, y + 0.025, 0.04, M["rib"], 12)


def keypad_pod(name, xc, y, zf):
    """A control pod on a short stalk beside the keyboard, its face raked to the operator: a lit grid
    of keys under a small status line, as these consoles are thick with."""
    cylinder(f"{name} pod stalk", xc, zf + 0.2, y, y + 0.1, 0.022, M["metal"], 10)
    prism(f"{name} pod", xc - 0.15, xc + 0.15, [(y + 0.08, zf + 0.06), (y + 0.08, zf + 0.26), (y + 0.23, zf + 0.26), (y + 0.26, zf + 0.2)], M["metal"], 0.008)
    def on_face(t):
        return (y + 0.08 + t * 0.18, zf + 0.06 + t * 0.14)
    for r in range(4):
        a, b = on_face(0.14 + r * 0.2), on_face(0.28 + r * 0.2)
        for k in range(5):
            xk = xc - 0.115 + k * 0.0575
            prism(f"{name} pod key {r} {k}", xk - 0.02, xk + 0.02, [a, b, (b[0] + 0.006, b[1] - 0.008), (a[0] + 0.006, a[1] - 0.008)],
                  M["tell"], 0)


def keyboard(name, xc, zf, y0):
    """A keyboard lying on the worktop with its rows of keys on top."""
    box(f"{name} keyboard", xc - 0.22, xc + 0.22, y0, y0 + 0.022, zf, zf + 0.16, M["panel"], 0.006)
    for r in range(3):
        box(f"{name} key row {r}", xc - 0.2, xc + 0.2, y0 + 0.022, y0 + 0.03, zf + 0.018 + r * 0.044, zf + 0.052 + r * 0.044,
            M["dark"], 0.002)


def switch_panel(name, x0, x1, y0, y1, z):
    """A framed panel facing -z with toggle rows, two knobs and a line of lamps."""
    box(f"{name} frame", x0, x1, y0, y1, z - 0.012, z, M["dark"], 0.004)
    box(f"{name} plate", x0 + 0.02, x1 - 0.02, y0 + 0.02, y1 - 0.02, z - 0.018, z - 0.012, M["panel"], 0.002)
    tall = y1 - y0
    columns = max(3, int((x1 - x0) / 0.075))
    for k in range(columns):
        xk = x0 + (x1 - x0) * (k + 0.5) / columns
        box(f"{name} lamp {k}", xk - 0.012, xk + 0.012, y1 - 0.065, y1 - 0.04, z - 0.022, z - 0.018,
            M["tell"], 0)
        if tall > 0.2:
            box(f"{name} toggle {k}", xk - 0.008, xk + 0.008, y0 + 0.04, y0 + 0.08, z - 0.04, z - 0.018, M["rail"], 0.002)
    if tall > 0.28:
        for k, xk in enumerate((x0 + 0.1, x1 - 0.1)):
            cylinder(f"{name} dial {k}", z - 0.04, z - 0.018, y0 + tall * 0.55, xk, 0.035, M["rib"], 14, horizontal="z")


def flared(name, xc, lo, hi, y0, y1, z0, z1, m, bevel=0.012):
    """A block facing along z whose half-width runs from lo at y0 to hi at y1: a shoulder-wide shell."""
    pts = [(xc - lo, y0, z0), (xc + lo, y0, z0), (xc + hi, y1, z0), (xc - hi, y1, z0),
           (xc - lo, y0, z1), (xc + lo, y0, z1), (xc + hi, y1, z1), (xc - hi, y1, z1)]
    return solid(name, pts, [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)], m, bevel)


def bridge_seat(name, xc, zc):
    """A starship's watch seat, facing +z, its back to the camera: bolted to the deck on one thick
    pedestal, a moulded shell that flares to the shoulders with cheeks wrapping forward, stacked padded
    panels inside it and moulded plates on its back, and an arm pod carrying a keypad. It stops at the
    shoulders, so the head stays clear above it. No casters: a ship's seat does not roll."""
    box(f"{name} deck plate", xc - 0.3, xc + 0.3, 0.0, 0.025, zc - 0.3, zc + 0.3, M["metal"], 0.008)
    for bx in (-1, 1):
        for bz in (-1, 1):
            cylinder(f"{name} bolt {bx} {bz}", xc + bx * 0.24, zc + bz * 0.24, 0.025, 0.04, 0.018, M["rail"], 8)
    cylinder(f"{name} pedestal", xc, zc, 0.025, 0.32, 0.095, M["metal"], 20)
    cylinder(f"{name} pedestal collar", xc, zc, 0.3, 0.37, 0.125, M["rib"], 20)
    box(f"{name} seat frame", xc - 0.24, xc + 0.24, 0.37, 0.42, zc - 0.26, zc + 0.24, M["metal"], 0.015)
    box(f"{name} cushion", xc - 0.23, xc + 0.23, 0.42, 0.49, zc - 0.2, zc + 0.25, M["chair"], 0.03)
    zb = zc - 0.27
    # the shell, seen from behind: its body, and cheeks wrapping forward at the shoulders
    flared(f"{name} shell", xc, 0.24, 0.32, 0.4, 1.08, zb - 0.09, zb + 0.02, M["metal"], 0.018)
    for k in (-1, 1):
        x = xc + k * 0.3
        box(f"{name} cheek {k}", x - 0.03, x + 0.03, 0.78, 1.06, zb - 0.06, zb + 0.15, M["metal"], 0.012)
    # padding inside the shell, in stacked rolls
    for r, (y0, y1) in enumerate(((0.5, 0.68), (0.7, 0.87), (0.89, 1.04))):
        half = 0.2 + 0.08 * (y1 - 0.5) / 0.58
        box(f"{name} pad {r}", xc - half, xc + half, y0, y1, zb + 0.02, zb + 0.07, M["chair"], 0.02)
    # the back the camera sees: a raised lumbar plate, a shoulder plate and a spine between them
    flared(f"{name} back plate low", xc, 0.19, 0.22, 0.48, 0.76, zb - 0.115, zb - 0.09, M["panel"], 0.006)
    flared(f"{name} back plate high", xc, 0.23, 0.28, 0.8, 1.03, zb - 0.115, zb - 0.09, M["panel"], 0.006)
    box(f"{name} spine", xc - 0.035, xc + 0.035, 0.42, 0.78, zb - 0.135, zb - 0.09, M["rib"], 0.008)
    # the seat's station marking: a triangle, point down, high on the shoulder plate
    solid(f"{name} marking", [(xc - 0.075, 1.0, zb - 0.122), (xc + 0.075, 1.0, zb - 0.122), (xc, 0.87, zb - 0.122),
                              (xc - 0.075, 1.0, zb - 0.116), (xc + 0.075, 1.0, zb - 0.116), (xc, 0.87, zb - 0.116)],
          [(0, 1, 2), (5, 4, 3), (0, 3, 4, 1), (1, 4, 5, 2), (2, 5, 3, 0)], M["lamp"])
    for r in range(3):
        box(f"{name} back vent {r}", xc + 0.08, xc + 0.19, 0.53 + r * 0.05, 0.55 + r * 0.05, zb - 0.122, zb - 0.115, M["dark"], 0)
    # arm rests; the right one carries a keypad pod
    for k in (-1, 1):
        box(f"{name} arm strut {k}", xc + k * 0.29 - 0.025, xc + k * 0.29 + 0.025, 0.4, 0.64, zc - 0.12, zc - 0.06, M["metal"], 0.008)
        box(f"{name} arm rest {k}", xc + k * 0.29 - 0.045, xc + k * 0.29 + 0.045, 0.64, 0.68, zc - 0.25, zc + 0.14, M["chair"], 0.015)
    box(f"{name} keypad pod", xc + 0.26, xc + 0.38, 0.68, 0.72, zc - 0.02, zc + 0.2, M["metal"], 0.008)
    for r in range(3):
        box(f"{name} keypad row {r}", xc + 0.28, xc + 0.36, 0.72, 0.726, zc + 0.02 + r * 0.055, zc + 0.05 + r * 0.055, M["lamp"], 0)


def limb(name, p0, p1, r, m, r2=None, verts=14):
    """A tube between two deck points with an elliptical section: r across the body (x), r2 front to back."""
    a, b = Vector(p0), Vector(p1)
    axis = (b - a).normalized()
    side = Vector((1.0, 0.0, 0.0))
    u = (side - axis * side.dot(axis))
    u = u.normalized() if u.length > 1e-6 else Vector((0.0, 0.0, 1.0))
    v = axis.cross(u)
    r2 = r if r2 is None else r2
    ring = [u * (math.cos(2 * math.pi * i / verts) * r) + v * (math.sin(2 * math.pi * i / verts) * r2)
            for i in range(verts)]
    pts = [tuple(a + o) for o in ring] + [tuple(b + o) for o in ring]
    faces = [tuple(reversed(range(verts))), tuple(range(verts, 2 * verts))]
    faces += [(i, (i + 1) % verts, verts + (i + 1) % verts, verts + i) for i in range(verts)]
    ob = solid(name, pts, faces, m)
    for poly in ob.data.polygons:
        poly.use_smooth = len(poly.vertices) == 4
    return ob


def ball(name, centre, r, m, rings=10, segments=16):
    cx, cy, cz = centre
    pts = [(cx, cy - r, cz)]
    for i in range(1, rings):
        t = math.pi * i / rings
        for j in range(segments):
            a = 2 * math.pi * j / segments
            pts.append((cx + r * math.sin(t) * math.cos(a), cy - r * math.cos(t), cz + r * math.sin(t) * math.sin(a)))
    pts.append((cx, cy + r, cz))
    top = len(pts) - 1
    faces = [(0, 1 + (j + 1) % segments, 1 + j) for j in range(segments)]
    for i in range(rings - 2):
        b0, b1 = 1 + i * segments, 1 + (i + 1) * segments
        faces += [(b0 + j, b0 + (j + 1) % segments, b1 + (j + 1) % segments, b1 + j) for j in range(segments)]
    last = 1 + (rings - 2) * segments
    faces += [(last + j, last + (j + 1) % segments, top) for j in range(segments)]
    ob = solid(name, pts, faces, m)
    for poly in ob.data.polygons:
        poly.use_smooth = True
    return ob


def seated_crew(name, xc, zc):
    """A watch-stander sitting at the console in front of the chair (+z): torso a little forward,
    forearms on the keyboard. A painter's mannequin, not a figure card: the plate paints it."""
    m = M["crew"]
    limb(f"{name} hips", (xc, 0.47, zc - 0.07), (xc, 0.62, zc - 0.06), 0.17, m, 0.12)
    limb(f"{name} torso", (xc, 0.6, zc - 0.07), (xc, 1.08, zc + 0.02), 0.19, m, 0.12)
    limb(f"{name} shoulders", (xc - 0.2, 1.04, zc + 0.01), (xc + 0.2, 1.04, zc + 0.01), 0.06, m)
    limb(f"{name} neck", (xc, 1.06, zc + 0.02), (xc, 1.17, zc + 0.04), 0.05, m)
    ball(f"{name} head", (xc, 1.26, zc + 0.05), 0.105, m)
    for s in (-1, 1):
        limb(f"{name} thigh {s}", (xc + s * 0.1, 0.52, zc - 0.04), (xc + s * 0.11, 0.53, zc + 0.38), 0.075, m)
        limb(f"{name} shin {s}", (xc + s * 0.11, 0.51, zc + 0.38), (xc + s * 0.12, 0.08, zc + 0.43), 0.055, m)
        box(f"{name} foot {s}", xc + s * 0.12 - 0.05, xc + s * 0.12 + 0.05, 0.0, 0.08, zc + 0.36, zc + 0.6, m, 0.02)
        limb(f"{name} upper arm {s}", (xc + s * 0.21, 1.03, zc + 0.0), (xc + s * 0.22, 0.82, zc + 0.16), 0.05, m)
        limb(f"{name} forearm {s}", (xc + s * 0.22, 0.82, zc + 0.16), (xc + s * 0.12, 0.81, zc + 0.5), 0.04, m)


SEAT_BACK = 0.42    # from a chair's centre to its desk's front edge: knees under, hands on the keys


def desk_frame(name, x0, x1, zf, depth):
    """A desk you can sit at: end and divider pedestals, a modesty panel at the back of an open
    knee well, a worktop with a nosing."""
    box(f"{name} kick", x0 + 0.02, x1 - 0.02, 0.0, 0.08, zf + depth - 0.14, zf + depth, M["dark"], 0.004)
    box(f"{name} modesty", x0, x1, 0.08, 0.72, zf + depth - 0.1, zf + depth, M["panel"], 0.008)
    seats = max(1, round(x1 - x0))
    for k in range(seats + 1):
        xp = x0 + (x1 - x0) * k / seats
        lo, hi = max(x0, xp - 0.05), min(x1, xp + 0.05)
        box(f"{name} pedestal {k}", lo, hi, 0.0, 0.72, zf + 0.02, zf + depth, M["metal"], 0.008)
    box(f"{name} worktop", x0 - 0.03, x1 + 0.03, 0.72, 0.755, zf - 0.03, zf + depth, M["metal"], 0.01)
    box(f"{name} nosing", x0 - 0.035, x1 + 0.035, 0.705, 0.76, zf - 0.055, zf - 0.025, M["rail"], 0.01)


def watch_bank(name, xc, zf, seats=2, rack=1.95, chairs=True, crew=()):
    """A run of watch consoles looking towards +z with the operator on the -z side, 1.0 m per seat:
    a desk, a monitor and keyboard each, and an equipment rack behind. A rack tall enough carries a
    repeater screen; a low one carries switch panels and a radio head, so nothing passes through it."""
    half = seats * 0.5
    x0, x1 = xc - half, xc + half
    depth = 0.62
    desk_frame(name, x0, x1, zf, depth)
    # the rack stands on the desk's back edge
    box(f"{name} rack", x0, x1, 0.755, rack, zf + depth, zf + depth + 0.18, M["metal"], 0.012)
    box(f"{name} rack base", x0, x1, 0.0, 0.755, zf + depth, zf + depth + 0.18, M["panel"], 0.008)
    tall = rack >= 1.9
    for k in range(seats):
        xs = x0 + 0.5 + k
        keyboard(f"{name} {k}", xs, zf + 0.0, 0.755)
        turn = 0.2 if k % 2 else -0.2
        arm_monitor(f"{name} crt {k}", xs - 0.05, zf + 0.2, 1.1, zf + 0.57, 0.77, turn=turn)
        keypad_pod(f"{name} {k}", xs + 0.34, 0.755, zf + 0.02)
        gooseneck_mic(f"{name} {k}", xs - 0.38, 0.755, zf + 0.34)
        panel_low = 1.34
        if tall:
            box(f"{name} repeater {k} frame", xs - 0.36, xs + 0.36, rack - 0.56, rack - 0.14, zf + depth - 0.016, zf + depth,
                M["dark"], 0.005)
            box(f"{name} repeater {k} screen", xs - 0.32, xs + 0.32, rack - 0.52, rack - 0.18, zf + depth - 0.02,
                zf + depth - 0.016, M["glow"], 0)
            switch_panel(f"{name} panel {k}", xs - 0.45, xs + 0.45, panel_low, rack - 0.6, zf + depth)
        else:
            switch_panel(f"{name} panel {k}", xs - 0.45, xs + 0.45, panel_low, rack - 0.03, zf + depth)
            # the radio head sits on top of the rack, clear of the window behind it
            box(f"{name} radio {k}", xs - 0.3, xs + 0.26, rack, rack + 0.13, zf + depth + 0.02, zf + depth + 0.17, M["panel"], 0.01)
            for j in range(4):
                cylinder(f"{name} radio {k} knob {j}", zf + depth, zf + depth + 0.02, rack + 0.065, xs - 0.22 + j * 0.13, 0.022,
                         M["rail"], 12, horizontal="z")
    if tall:
        prism(f"{name} hood", x0 - 0.03, x1 + 0.03,
              [(rack, zf + depth + 0.2), (rack, zf + depth - 0.1), (rack + 0.08, zf + depth - 0.14), (rack + 0.12, zf + depth + 0.2)],
              M["metal"], 0.012)
        box(f"{name} hood light", x0 + 0.08, x1 - 0.08, rack - 0.015, rack, zf + depth - 0.08, zf + depth - 0.02, M["lamp"], 0)
    else:
        box(f"{name} rack cap", x0 - 0.02, x1 + 0.02, rack - 0.015, rack, zf + depth - 0.01, zf + depth + 0.2, M["rib"], 0.004)
    if chairs:
        for k in range(seats):
            bridge_seat(f"{name} chair {k}", x0 + 0.5 + k, zf - SEAT_BACK)
            if k in crew:
                seated_crew(f"{name} crew {k}", x0 + 0.5 + k, zf - SEAT_BACK)



def aft_watch():
    """The engineering watch along the aft bulkhead: one bank, one operator on watch."""
    xc, zc = 7.05, 6.3
    parts = made(watch_bank, "engineering bank", xc, zc - 0.375, seats=2, crew=(0,))
    spin(parts, (xc, 0.0, zc), -math.pi / 2)


def systems_board():
    """The ship's systems board in the aft corner, built into the bulkhead: the one place the watch reads
    every circuit at a glance, a mimic panel of lamps over rows of breakers, on a raked moulded plinth."""
    b, x0, x1 = ROOM["aftPort"], 7.2, 7.95
    prism("systems board plinth", x0, x1, [(0.0, b - 0.1), (0.8, b - 0.16), (0.86, b - 0.12), (0.86, b), (0.0, b)], M["panel"], 0.012)
    box("systems board panel", x0, x1, 0.86, 2.1, b - 0.12, b, M["metal"], 0.012)
    box("systems board brow", x0 - 0.02, x1 + 0.02, 2.1, 2.2, b - 0.2, b, M["panel"], 0.012)
    for r in range(9):
        for k in range(6):
            lit = (r * 2 + k * 5) % 6 < 2
            box(f"systems board lamp {r} {k}", x0 + 0.08 + k * 0.1, x0 + 0.14 + k * 0.1, 1.42 + r * 0.07, 1.46 + r * 0.07, b - 0.128, b - 0.12, M["tell"], 0)
    for r in range(4):
        for k in range(8):
            box(f"systems board breaker {r} {k}", x0 + 0.07 + k * 0.078, x0 + 0.115 + k * 0.078, 0.96 + r * 0.1, 1.03 + r * 0.1, b - 0.135, b - 0.12, M["rib"], 0)


def comms_bank():
    """Port side, abaft the dais: the comms watch, two operators, their backs to the plot."""
    b = ROOM["port"]
    for j, xc in enumerate((PEOPLE["comms-left"][0], PEOPLE["comms-right"][0])):
        watch_bank(f"comms bank {j}", xc, b - 1.37, seats=1, rack=1.46)


def ledger_station():
    """The quartermaster's station, built into the aft bulkhead rather than standing against it: a moulded
    console running floor to head height with its screens let into a raked face, keys let into the deck
    below them and no separate furniture, then a plain moulded counter beside it where the books are
    kept, with the roster above. The console is the same family as the watch seats' cowls."""
    b = ROOM["aftPort"]
    x0, xm, x1 = 3.3, 4.6, 5.85
    nose, knee, head = (0.74, b - 0.78), (0.86, b - 0.46), (1.72, b - 0.14)
    box("ledger toe", x0 + 0.03, x1 - 0.03, 0.0, 0.1, b - 0.5, b, M["dark"], 0)
    prism("ledger console body", x0, xm, [(0.1, b - 0.56), nose, knee, (0.86, b), (0.1, b)], M["panel"], 0.015)
    prism("ledger console upper", x0, xm, [knee, head, (1.72, b), (0.86, b)], M["metal"], 0.012)
    prism("ledger console brow", x0 - 0.02, xm + 0.02, [(1.72, b - 0.14), (1.8, b - 0.24), (1.92, b - 0.2), (1.92, b), (1.72, b)], M["panel"], 0.015)
    box("ledger console brow light", x0 + 0.1, xm - 0.1, 1.785, 1.8, b - 0.23, b - 0.17, M["lamp"], 0)
    for xs in (x0, xm):
        prism(f"ledger console cheek {xs}", xs - 0.035, xs + 0.035, [(0.1, b - 0.58), (nose[0] + 0.02, nose[1] - 0.02), (knee[0] + 0.03, knee[1] - 0.03),
                                                                     (head[0] + 0.03, head[1] - 0.03), (1.72, b), (0.1, b)], M["rib"], 0.01)
    # the kick face's moulded panels, and a vent; nothing that opens like an office drawer
    for k, (a, c) in enumerate(((x0 + 0.08, x0 + 0.62), (x0 + 0.68, xm - 0.08))):
        prism(f"ledger console plate {k}", a, c, face((0.14, b - 0.57), (0.7, b - 0.76), 0.06, 0.94, 0.0, 0.012, (-0.3, -0.95)), M["metal"], 0.005)
    for r in range(5):
        prism(f"ledger console vent {r}", x0 + 0.76, xm - 0.16, face((0.14, b - 0.57), (0.7, b - 0.76), 0.1 + r * 0.06, 0.13 + r * 0.06, 0.012, 0.015, (-0.3, -0.95)), M["dark"], 0)

    along = Vector((0.0, head[0] - knee[0], head[1] - knee[1])).normalized()
    out = Vector((0.0, 0.349, -0.937))    # the raked face's normal, up and towards the operator
    n = (0.349, -0.937)
    screens = (x0 + 0.325, xm - 0.325)
    t = 0.6
    y, z = knee[0] + (head[0] - knee[0]) * t, knee[1] + (head[1] - knee[1]) * t
    for k, xs in enumerate(screens):
        crt_module(f"ledger screen {k}", (xs - 0.02, y, z), (1, 0, 0), along, out, 0.45, 0.34)
        # the deck in front of each screen is a board of keys, with a strip of function keys above it
        for r in range(3):
            for c in range(8):
                xk = xs - 0.245 + c * 0.07
                prism(f"ledger deck key {k} {r} {c}", xk - 0.026, xk + 0.026, face(nose, knee, 0.14 + r * 0.27, 0.36 + r * 0.27, 0.0, 0.01, (0.94, -0.35)),
                      M["tell"], 0)
        for c in range(7):
            xk = xs - 0.222 + c * 0.074
            prism(f"ledger strip {k} key {c}", xk - 0.026, xk + 0.026, face(knee, head, 0.1, 0.2, 0.0, 0.012, n), M["tell"], 0)
    # above the screens a row of indicator lamps
    for c in range(14):
        xk = x0 + 0.14 + c * 0.079
        prism(f"ledger indicator {c}", xk - 0.024, xk + 0.024, face(knee, head, 0.9, 0.96, 0.0, 0.012, n), M["tell"], 0)

    # the counter beside it, moulded the same, with what the quartermaster actually works from
    zc = b - 0.56
    box("ledger counter body", xm, x1, 0.1, 0.76, zc + 0.03, b, M["panel"], 0.012)
    box("ledger counter top", xm, x1 + 0.02, 0.76, 0.81, zc - 0.03, b, M["metal"], 0.012)
    for k, (a, c) in enumerate(((xm + 0.1, xm + 0.6), (xm + 0.66, x1 - 0.08))):
        box(f"ledger counter plate {k}", a, c, 0.16, 0.7, zc + 0.012, zc + 0.03, M["metal"], 0.005)
    box("ledger counter edge", x1 - 0.02, x1 + 0.05, 0.0, 0.81, zc - 0.03, b, M["rib"], 0.01)
    t = 0.81
    for k in range(5):
        box(f"ledger paper {k}", xm + 0.72, xm + 1.14, t + k * 0.012, t + 0.01 + k * 0.012, zc + 0.04, zc + 0.36, M["paper"],
            0.002, rot=0.05 * ((-1) ** k) * (k + 1) / 3)
    box("ledger clipboard", xm + 0.26, xm + 0.5, t, t + 0.008, zc + 0.04, zc + 0.3, M["paper"], 0.002, rot=0.2)
    box("ledger binder", xm + 0.06, xm + 0.2, t, t + 0.28, zc + 0.2, zc + 0.5, M["crate"], 0.01)
    box("ledger binder 2", xm + 0.21, xm + 0.33, t, t + 0.26, zc + 0.26, zc + 0.5, M["panel"], 0.01)
    cylinder("ledger mug", xm + 0.58, zc + 0.14, t, t + 0.09, 0.04, M["panel"], 14, 0.004)


def roster_board():
    b = ROOM["aftPort"]
    x0, x1 = 4.72, 5.8
    box("roster frame", x0, x1, 1.08, 2.2, b - 0.06, b, M["rib"], 0.015)
    box("roster board", x0 + 0.06, x1 - 0.06, 1.14, 2.14, b - 0.07, b - 0.06, M["board"], 0.0)
    for r in range(5):
        for k in range(4):
            box(f"roster card {r} {k}", x0 + 0.1 + k * 0.23, x0 + 0.29 + k * 0.23, 1.98 - r * 0.19, 2.1 - r * 0.19,
                b - 0.08, b - 0.07, M["paper"], 0.0)


def leaning_figure(name, xf, zf):
    """Standing at the plot table's right rim, facing it (-x): weight on the back foot, torso
    pitched forward over the rim, both hands planted on its edge, head raised to the space
    above the table where the board is projected. A painter's mannequin for the card's pose."""
    m = M["person"]
    rim, edge = 0.97, TABLE[1] + 0.02
    hip = (xf + 0.08, 0.95, zf)
    chest = (xf - 0.22, 1.38, zf)
    for s in (-1, 1):
        ankle = (xf + (0.06 if s < 0 else -0.06), 0.08, zf + s * 0.14)
        knee = (xf + 0.02, 0.5, zf + s * 0.12)
        limb(f"{name} thigh {s}", (hip[0], hip[1], zf + s * 0.1), knee, 0.075, m)
        limb(f"{name} shin {s}", knee, ankle, 0.055, m)
        box(f"{name} foot {s}", ankle[0] - 0.2, ankle[0] + 0.05, 0.0, 0.08, ankle[2] - 0.05, ankle[2] + 0.05, m, 0.02)
    limb(f"{name} hips", (hip[0], hip[1] - 0.05, zf), hip, 0.17, m, 0.13)
    limb(f"{name} torso", hip, chest, 0.19, m, 0.12)
    limb(f"{name} shoulders", (chest[0], chest[1] - 0.02, zf - 0.21), (chest[0], chest[1] - 0.02, zf + 0.21), 0.06, m)
    neck_top = (chest[0] - 0.09, chest[1] + 0.13, zf)
    limb(f"{name} neck", (chest[0] - 0.02, chest[1], zf), neck_top, 0.05, m)
    ball(f"{name} head", (neck_top[0] - 0.03, neck_top[1] + 0.085, zf), 0.105, m)
    for s in (-1, 1):
        shoulder = (chest[0], chest[1] - 0.03, zf + s * 0.22)
        hand = (edge + 0.04, rim + 0.03, zf + s * 0.3)
        elbow = ((shoulder[0] + hand[0]) / 2 + 0.06, (shoulder[1] + hand[1]) / 2, zf + s * 0.3)
        limb(f"{name} upper arm {s}", shoulder, elbow, 0.05, m)
        limb(f"{name} forearm {s}", elbow, hand, 0.04, m)
        box(f"{name} hand {s}", hand[0] - 0.06, hand[0] + 0.04, rim, rim + 0.04, hand[2] - 0.04, hand[2] + 0.04, m, 0.01)


def stand_ins():
    col = bpy.data.collections.new("stand-ins")
    bpy.context.scene.collection.children.link(col)
    for pid, (x, z, h, floor) in PEOPLE.items():
        if POSED.get(pid) == "leaning":
            for ob in made(leaning_figure, f"stand-in {pid}", x, z):
                for c in ob.users_collection:
                    c.objects.unlink(ob)
                col.objects.link(ob)
            continue
        head = 0.12 * h / 1.75
        cylinder(f"stand-in {pid} body", x, z, floor, floor + h - 2 * head - 0.03, 0.17 * h / 1.75, M["person"], 20,
                 collection=col)
        bpy.ops.mesh.primitive_uv_sphere_add(radius=head, location=V(x, floor + h - head, z), segments=20,
                                             ring_count=10)
        s = bpy.context.object
        s.name = f"stand-in {pid} head"
        s.data.materials.append(M["person"])
        for c in s.users_collection:
            c.objects.unlink(s)
        col.objects.link(s)
    return col


# ---------------------------------------------------------------- the ship's turn to the camera

def turned(x, z):
    """A ship (x, z) in camera axes after `turn_ship`."""
    dx, dz = x - PIVOT[0], z - PIVOT[1]
    c, s = math.cos(YAW), math.sin(YAW)
    return PIVOT[0] + dx * c - dz * s, PIVOT[1] + dx * s + dz * c


def turn_ship():
    """Bake the yaw into everything built. From here on geometry is in the camera's axes."""
    spin([o for o in bpy.data.objects if o.type == "MESH"], (PIVOT[0], 0.0, PIVOT[1]), YAW)


def stand_at(pid):
    """A stand in camera axes: x, z, height and floor."""
    x, z, h, floor = PEOPLE[pid]
    return (*turned(x, z), h, floor)


# ---------------------------------------------------------------- camera, render, layout

def camera():
    data = bpy.data.cameras.new("deck camera")
    data.sensor_fit = "HORIZONTAL"
    data.sensor_width = 36.0
    data.lens = CAM["focal"] * 36.0 / W
    data.shift_y = -(H / 2 - CAM["cy"]) / W
    data.clip_start, data.clip_end = 0.1, 100
    ob = bpy.data.objects.new("deck camera", data)
    bpy.context.scene.collection.objects.link(ob)
    ob.location = V(0, CAM["height"], 0)
    ob.rotation_euler = (math.pi / 2, 0, 0)
    bpy.context.scene.camera = ob
    return ob


def render_settings():
    sc = bpy.context.scene
    sc.render.engine = "BLENDER_WORKBENCH"
    sc.render.resolution_x, sc.render.resolution_y = W, H
    sc.render.resolution_percentage = 100
    sc.display.render_aa = "16"
    sh = sc.display.shading
    sh.light = "STUDIO"
    sh.color_type = "MATERIAL"
    sh.show_cavity = True
    sh.cavity_type = "BOTH"
    sh.show_shadows = True
    sh.shadow_intensity = 0.35
    sc.display.light_direction = (0.35, 0.45, 0.82)
    sc.view_settings.view_transform = "Standard"


def project(x, y, z):
    return [round(CAM["cx"] + CAM["focal"] * x / z, 1), round(CAM["cy"] - CAM["focal"] * (y - CAM["height"]) / z, 1)]


def check_camera(cam):
    """The Blender camera must agree with the pinhole the markers are computed from."""
    sc = bpy.context.scene
    bpy.context.view_layer.update()
    for p in [(0.0, CAM["height"], 10.0), (1.9, 0.97, 5.8), (-1.9, 0.97, 8.2)]:
        v = world_to_camera_view(sc, cam, Vector(V(*p)))
        got, want = (v.x * W, (1 - v.y) * H), project(*p)
        assert abs(got[0] - want[0]) < 0.6 and abs(got[1] - want[1]) < 0.6, (p, got, want)


def bbox(prefix):
    """Deck-space extent of every mesh whose name starts with prefix."""
    pts = [ob.matrix_world @ Vector(c) for ob in bpy.data.objects
           if ob.name.startswith(prefix) and ob.type == "MESH" for c in ob.bound_box]
    xs, ys, zs = [p.x for p in pts], [p.z for p in pts], [p.y for p in pts]
    return [round(min(xs), 3), round(max(xs), 3), round(min(ys), 3), round(max(ys), 3),
            round(min(zs), 3), round(max(zs), 3)]


def centre(prefix):
    b = bbox(prefix)
    return project((b[0] + b[1]) / 2, (b[2] + b[3]) / 2, (b[4] + b[5]) / 2)


def layout():
    bpy.context.view_layer.update()
    stations = {
        "plotting-table": "chart table", "plot-glow": "chart top glow", "comms-bank": "comms bank", "ledger": "ledger",
        "roster-board": "roster", "status-board": "status board", "cowl": "overhead cowl", "hatch": "hatch", "engineering-bank": "engineering bank",
        "helm": "helm", "navigator": "navigator",
    }
    props = {k: bbox(v) for k, v in stations.items()}
    x0, x1, z0, z1 = TABLE
    inset = 0.22
    top = 0.978
    corners = [turned(x0 + inset, z0 + inset), turned(x1 - inset, z0 + inset), turned(x1 - inset, z1 - inset),
               turned(x0 + inset, z1 - inset)]
    cx, cz = turned(*PIVOT)
    hw, hd = (x1 - x0) / 2, (z1 - z0) / 2
    markers = {
        "plot-corners": [project(c[0], top, c[1]) for c in corners],
        "plot-centre": project(cx, top, cz),
        "up-at-plot-centre": project(cx, top + 1.0, cz),
        "status-board": centre("status board screen"),
        "comms-screens": {f"crt-{j}": centre(f"comms bank {j} crt 0 screen") for j in range(2)},
        "engineering-screens": {f"crt-{k}": centre(f"engineering bank crt {k} screen") for k in range(2)},
        "helm-screens": {f"{n}-{s}": centre(f"{n} screen {s} frame") for n in ("helm", "navigator") for s in (-1, 1)},
        "ledger-screen": centre("ledger screen 0"),
        "roster-board": centre("roster board"),
        "hatch-centre": centre("hatch door"),
    }
    people = {}
    for k in PEOPLE:
        x, z, h, f = stand_at(k)
        people[k] = {"x": round(x, 3), "z": round(z, 3), "heightM": h, "floorM": f, "feetPx": project(x, f, z),
                     "headPx": project(x, f + h, z)}
    doc = {"size": [W, H], "units": "metres; x right, y up, z ahead of the camera; the ship is turned by yawDegrees",
           "camera": CAM, "projection": "u = cx + focal*x/z, v = cy - focal*(y - height)/z",
           "yawDegrees": round(math.degrees(YAW), 2), "room": ROOM,
           "table": [round(cx - hw, 3), round(cx + hw, 3), round(cz - hd, 3), round(cz + hd, 3)],
           "props": props, "markers": markers, "people": people}
    (OUT / "deck-guide-v10.layout.json").write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
    for k, v in people.items():
        print(k, "height px", round(v["feetPx"][1] - v["headPx"][1]), "at", v["feetPx"])


def main():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    materials()
    shell()
    flight_dais()
    plotting_table()
    aft_watch()
    systems_board()
    near_stations()
    comms_bank()
    ledger_station()
    roster_board()
    hatch(ROOM["aftPort"])
    people = stand_ins()
    turn_ship()
    cam = camera()
    render_settings()
    check_camera(cam)
    button_boards()
    sc = bpy.context.scene
    if "--masks" in sys.argv:
        people.hide_render = True
        occluder_masks()
        return
    if "--pose" in sys.argv:
        pose_guide(sys.argv[sys.argv.index("--pose") + 1])
        return
    for hidden, name in ((True, "deck-guide-v10.png"), (False, "deck-guide-v10-people.png")):
        people.hide_render = hidden
        sc.render.filepath = str(OUT / name)
        bpy.ops.render.render(write_still=True)
    layout()
    people.hide_render = True
    object_ids()


# Room surfaces that never stand between the camera and a person.
SURFACES = ("floor", "command island", "aft wall", "aft port wall", "port ", "ceiling", "overhead", "forward glazing",
            "aft overhead", "dais")


def pose_guide(stand, scale=3, margin=0.15):
    """The stand's mannequin alone, cropped round its own projected box and rendered at `scale`
    times the frame: everything else is hidden, so ImageGen gets the pose and the viewpoint
    without the table or room the card must not paint."""
    sc = bpy.context.scene
    for ob in bpy.data.objects:
        if ob.type == "MESH":
            ob.hide_render = not ob.name.startswith(f"stand-in {stand} ")
    sh = sc.display.shading
    sh.light, sh.color_type, sh.single_color = "STUDIO", "SINGLE", (0.7, 0.7, 0.7)
    sc.render.film_transparent = True
    x, z, h, floor = stand_at(stand)
    feet, head = project(x, floor, z), project(x, floor + h, z)
    tall = feet[1] - head[1]
    left, right = feet[0] - tall * 0.75, feet[0] + tall * 0.45
    top, bottom = head[1] - tall * margin, feet[1] + tall * margin
    sc.render.resolution_percentage = 100 * scale
    sc.render.use_border, sc.render.use_crop_to_border = True, True
    sc.render.border_min_x, sc.render.border_max_x = left / W, right / W
    sc.render.border_min_y, sc.render.border_max_y = 1 - bottom / H, 1 - top / H
    sc.render.filepath = str(OUT / f"{stand}-pose-guide.png")
    bpy.ops.render.render(write_still=True)


BUTTONS = {}


def button_boards():
    """Every button is drawn by the game, not painted: the buttons of one group leave the picture and one
    plain patch covering them stands in for the group, for the paint to keep as a key. Each button's outline
    goes in the id table, so the game knows where to draw it on its board."""
    bpy.context.view_layer.update()
    groups = {}
    for ob in [o for o in bpy.data.objects if o.type == "MESH" and o.data.materials and o.data.materials[0] is M["tell"]]:
        name = re.sub(r"( -?\d+){1,2}$", "", re.sub(r"\.\d+$", "", ob.name))
        groups.setdefault(name, []).append(ob)
    for name, members in groups.items():
        pts = []
        BUTTONS[name] = {}
        for ob in members:
            world = [ob.matrix_world @ v.co for v in ob.data.vertices]
            pts += world
            BUTTONS[name][ob.name] = [project(q.x, q.z, q.y) for q in world]
            ob.hide_render = True
        mid = sum(pts, Vector()) / len(pts)
        me = bpy.data.meshes.new(f"{name} board")
        bm = bmesh.new()
        for q in pts:
            bm.verts.new(mid + (q - mid) * 1.1)
        bmesh.ops.convex_hull(bm, input=bm.verts)
        bm.to_mesh(me)
        bm.free()
        me.materials.append(M["tell"])
        bpy.context.scene.collection.objects.link(bpy.data.objects.new(f"{name} board", me))


def object_ids():
    """One exact flat colour per object, unantialiased, with a table of ids and each screen's and window's
    full outline: compared, they say what stands in front of every screen."""
    sc = bpy.context.scene
    sh = sc.display.shading
    sh.light, sh.color_type = "FLAT", "OBJECT"
    sh.show_cavity = sh.show_shadows = False
    sc.world = sc.world or bpy.data.worlds.new("matte")
    sc.world.color = (0, 0, 0)
    sc.display.render_aa = "OFF"
    sc.view_settings.view_transform = "Raw"
    sc.view_settings.look = "None"
    sc.render.dither_intensity = 0.0
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGB"
    bpy.context.view_layer.update()
    deps = bpy.context.evaluated_depsgraph_get()
    table, outlines = {}, {}
    meshes = [o for o in bpy.data.objects if o.type == "MESH" and not o.hide_render]
    for i, ob in enumerate(meshes, start=1):
        ob.color = ((i >> 16 & 255) / 255, (i >> 8 & 255) / 255, (i & 255) / 255, 1)
        table[i] = ob.name
        if ob.data.materials and ob.data.materials[0] is M["glow"]:
            mesh = ob.evaluated_get(deps).to_mesh()
            pts = [ob.matrix_world @ v.co for v in mesh.vertices]
            outlines[ob.name] = [project(p.x, p.z, p.y) for p in pts]
    sc.render.filepath = str(OUT / "deck-guide-v10-ids.png")
    bpy.ops.render.render(write_still=True)
    (OUT / "deck-guide-v10.ids.json").write_text(json.dumps({"ids": table, "screens": outlines, "buttons": BUTTONS}) + "\n", encoding="utf-8")


def occluder_masks():
    """Per stand, white where geometry nearer than that person covers the frame: the matte a
    figure card is cut with when it is composited into the plate. Run with `-- --masks`."""
    sc = bpy.context.scene
    sh = sc.display.shading
    sh.light, sh.color_type, sh.single_color = "FLAT", "SINGLE", (1, 1, 1)
    sh.show_cavity = sh.show_shadows = False
    sc.world = sc.world or bpy.data.worlds.new("matte")
    sc.world.color = (0, 0, 0)
    sc.display.render_aa = "8"
    out = OUT / "masks"
    out.mkdir(exist_ok=True)
    meshes = [o for o in bpy.data.objects if o.type == "MESH" and not o.name.startswith("stand-in")]
    cam = sc.camera.data
    far = cam.clip_end
    for pid in PEOPLE:
        x, z, h, floor = stand_at(pid)
        if pid in POSED:
            # A posed figure touches furniture, so a whole object is not simply in front of it:
            # her hands rest on the table top. Clip the camera just in front of her instead, so
            # only geometry nearer than her own front renders into the matte.
            cam.clip_end = z - POSED_DEPTH
            for ob in meshes:
                ob.hide_render = ob.name.startswith(SURFACES)
        else:
            cam.clip_end = far
            for ob in meshes:
                near = min((ob.matrix_world @ Vector(c)).y for c in ob.bound_box)
                ob.hide_render = near >= z or ob.name.startswith(SURFACES)
        sc.render.filepath = str(out / f"{pid}.occluder.png")
        bpy.ops.render.render(write_still=True)


main()
