"""Blender blockout of the command deck at real scale: the ImageGen layout guide for plate v3.

Run from the repository root (headless, under a minute):

  "C:/Program Files/Blender Foundation/Blender 5.2/blender.exe" --background --factory-startup \
      --python art-source/hq-command-deck/blender/deck_blockout.py

The room is the company's CIC rather than an office. Its plan follows the ship's own
bridge (`ShipBridgeFitting`): a square plotting table on an inset command island, watch
banks wrapping it on every side, data cabinets in the corners. Its mood is a warship's
combat information centre: a hanging monitor cluster over the plot, a raised rear tier
behind a rail, heavy beams under a low ceiling, consoles to every wall and no windows.

The first plates were painted from a text brief and their props did not share one
scale. Here every prop has real dimensions, one camera projects them all, and ImageGen
is told to keep the guide's geometry. Shapes are modelled past boxes (bevels, sloped
keyboard decks, tapered CRTs, swivel chairs, I-beams) so the skin does not come back boxy.

Coordinates in this file are deck metres: x right, y up, z ahead of the camera. They
become Blender's (X, Y, Z) = (x, z, y) in one place, `V`.

Writes beside the script's folder:
  deck-guide-v6.png          grey guide for ImageGen: the room and its seated watch, no named figures
  deck-guide-v6-people.png   the same with stand-ins at their stands, for proportions
  deck-guide-v6.layout.json  camera, room, props and projected markers
  masks/<stand>.occluder.png with `-- --masks`: what stands in front of each person
"""
import json
import math
import sys
from contextlib import contextmanager
from pathlib import Path

import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Matrix, Vector

OUT = Path(__file__).resolve().parent.parent
W, H = 2172, 724
CAM = {"height": 2.7, "focal": 1050.0, "cx": W / 2, "cy": 170.0}
ROOM = {"halfWidth": 8.0, "near": 2.0, "back": 13.0, "ceiling": 4.4}
TIER = {"front": 10.5, "height": 0.45}          # the raised rear tier, full width
TABLE = (-1.9, 1.9, 5.8, 8.2)                   # x0, x1, z0, z1 of the plotting table

# Feet (x, z), height above their own floor, and that floor. A seated stand is the chair's
# centre; seated figures are
# measured to the top of the head.
PEOPLE = {
    "castle": (0.9, 8.45, 1.68, 0.0),
    "commander": (-2.4, 6.9, 1.82, 0.0),
    "comms-left": (-5.62, 11.45, 1.30, TIER["height"]),
    "comms-right": (-3.92, 11.45, 1.30, TIER["height"]),
    "quartermaster": (4.2, 11.95, 1.80, TIER["height"]),
}


def V(x, y, z):
    return (x, z, y)


# ---------------------------------------------------------------- kit

M = {}
LIFT = Vector((0.0, 0.0, 0.0))     # Blender-space offset applied to every solid built


def mat(name, grey):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (grey, grey, grey, 1)
    return m


def materials():
    M.update(
        floor=mat("floor plate", .16), inset=mat("command island", .09), wall=mat("bulkhead", .30),
        panel=mat("wall panel", .24), rib=mat("structural rib", .11), ceiling=mat("ceiling", .12),
        metal=mat("console metal", .20), dark=mat("recess", .05), table=mat("chart table", .17),
        chair=mat("chair", .09), pipe=mat("conduit", .14), board=mat("roster board", .55),
        paper=mat("paper", .65), crate=mat("crate", .22), pane=mat("plotting glass", .42),
        rail=mat("handrail", .5), glow=mat("glow surface", .95), lamp=mat("light", .8),
        person=mat("stand-in", .6), crew=mat("seated crew", .36),
    )
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

def shell():
    hw, n, b, c = ROOM["halfWidth"], ROOM["near"], ROOM["back"], ROOM["ceiling"]
    t = 0.25
    box("back wall", -hw, hw, 0, c, b, b + t, M["wall"], 0)
    box("left wall", -hw - t, -hw, 0, c, n, b + t, M["wall"], 0)
    box("right wall", hw, hw + t, 0, c, n, b + t, M["wall"], 0)
    box("ceiling", -hw - t, hw + t, c, c + t, n, b + t, M["ceiling"], 0)

    # deck plates on a 1.4 m pitch; the command island is its own darker inset
    pitch, seam = 1.4, 0.025
    x = -hw
    while x < hw - 1e-6:
        z = n
        while z < TIER["front"] - 1e-6:
            box(f"floor plate {x:+.1f} {z:.1f}", x + seam, min(x + pitch, hw) - seam, -0.03, 0.0,
                z + seam, min(z + pitch, TIER["front"]) - seam, M["floor"], 0.006)
            z += pitch
        x += pitch
    box("floor seam bed", -hw, hw, -0.06, -0.03, n, b, M["dark"], 0)
    box("command island", -3.4, 3.4, 0.0, 0.012, 4.4, 9.6, M["inset"], 0.004)
    for i, (x0, x1, z0, z1) in enumerate([(-3.5, 3.5, 4.3, 4.4), (-3.5, 3.5, 9.6, 9.7),
                                          (-3.5, -3.4, 4.4, 9.6), (3.4, 3.5, 4.4, 9.6)]):
        box(f"command island edge {i}", x0, x1, 0.0, 0.02, z0, z1, M["lamp"], 0.0)

    # back wall: ribs, upper panels and conduit; the tier is built separately
    for i, xr in enumerate([-7.9, -5.6, -2.95, 2.95, 5.6, 7.9]):
        prism(f"back rib {i}", xr - 0.12, xr + 0.12,
              [(0, b), (0, b - 0.16), (c - 0.4, b - 0.16), (c, b - 0.45), (c, b)], M["rib"], 0.015)
    for i, (yy, r) in enumerate([(4.08, 0.06), (4.2, 0.045), (4.3, 0.035)]):
        cylinder(f"back conduit {i}", -hw, hw, yy, b - 0.25 - i * 0.03, r, M["pipe"], horizontal="x")

    # side walls: ribs, wainscot, displays above head height, conduit runs
    for side in (-1, 1):
        xw = side * hw
        lo, hi = sorted((xw, xw - side * 0.16))
        for i in range(8):
            z = 3.3 + i * 1.4
            box(f"side rib {side} {i}", lo, hi, 0, c, z - 0.12, z + 0.12, M["rib"], 0.015)
            plo, phi = sorted((xw, xw - side * 0.03))
            box(f"side wainscot {side} {i}", plo, phi, 0.15, 1.1, z + 0.18, z + 1.22, M["panel"], 0.008)
            if 1 <= i <= 6:
                dlo, dhi = sorted((xw - side * 0.02, xw - side * 0.09))
                box(f"side display {side} {i} frame", dlo, dhi, 1.95, 2.85, z + 0.22, z + 1.18, M["dark"], 0.015)
                slo, shi = sorted((xw - side * 0.09, xw - side * 0.1))
                box(f"side display {side} {i} screen", slo, shi, 2.02, 2.78, z + 0.29, z + 1.11, M["glow"], 0)
        for k, yy in enumerate((3.25, 3.4)):
            cylinder(f"side conduit {side} {k}", n, b, yy, xw - side * (0.25 + 0.1 * k), 0.06, M["pipe"],
                     horizontal="z")

    # ceiling: heavy I-beams across, stringers, a lamp line between beams
    for i, zc in enumerate([5.0, 8.4, 11.6]):
        box(f"beam web {i}", -hw, hw, 3.85, c, zc - 0.09, zc + 0.09, M["rib"], 0.01)
        box(f"beam flange {i}", -hw, hw, 3.78, 3.85, zc - 0.24, zc + 0.24, M["rib"], 0.012)
        for xs in range(-7, 8, 2):
            box(f"beam stiffener {i} {xs}", xs - 0.03, xs + 0.03, 3.85, c, zc - 0.22, zc + 0.22, M["rib"], 0.005)
    for xs in (-6.0, -3.0, 3.0, 6.0):
        box(f"stringer {xs:+.1f}", xs - 0.09, xs + 0.09, c - 0.2, c, n, b, M["rib"], 0.008)
    for xs in (-5.0, 5.0):
        for z0, z1 in ((5.4, 8.0), (8.8, 11.2)):
            box(f"ceiling lamp {xs:+.1f} {z0}", xs - 0.2, xs + 0.2, c - 0.08, c, z0, z1, M["lamp"], 0.01)


def rear_tier():
    """The raised rear platform: fascia, steps at the centre, a rail along its edge."""
    f, th, hw, b = TIER["front"], TIER["height"], ROOM["halfWidth"], ROOM["back"]
    box("tier deck", -hw, hw, 0.0, th, f, b, M["floor"], 0.0)
    box("tier fascia", -hw, hw, 0.05, th - 0.04, f - 0.03, f, M["panel"], 0.01)
    box("tier nosing", -hw, hw, th - 0.04, th, f - 0.06, f + 0.05, M["rail"], 0.01)
    box("tier kick light", -hw, hw, 0.02, 0.05, f - 0.035, f - 0.02, M["lamp"], 0.0)
    for k in range(2):
        rise = th * (k + 1) / 3
        box(f"tier step {k}", -1.3, 1.3, 0.0, rise, f - 0.32 * (2 - k), f, M["metal"], 0.01)
    # rail on the tier edge, broken for the steps
    for x0, x1 in ((-hw + 0.2, -1.45), (1.45, hw - 0.2)):
        cylinder(f"tier rail {x0:+.1f}", x0, x1, th + 1.0, f + 0.08, 0.03, M["rail"], 16, horizontal="x")
        cylinder(f"tier rail mid {x0:+.1f}", x0, x1, th + 0.55, f + 0.08, 0.018, M["rail"], 12, horizontal="x")
        xp = x0
        while xp <= x1 + 1e-6:
            cylinder(f"tier post {xp:+.2f}", xp, f + 0.08, th, th + 1.0, 0.025, M["rail"], 12)
            xp += (x1 - x0) / max(1, round((x1 - x0) / 1.2))


def main_display():
    """Three big boards on the back wall above the tier: the room's situation display."""
    b = ROOM["back"]
    box("main display housing", -2.75, 2.75, 1.2, 3.2, b - 0.22, b, M["metal"], 0.04)
    for i, (x0, x1) in enumerate([(-2.6, -0.95), (-0.82, 0.82), (0.95, 2.6)]):
        box(f"main display {i} bezel", x0, x1, 1.32, 3.07, b - 0.25, b - 0.22, M["dark"], 0.015)
        box(f"main display {i} screen", x0 + 0.06, x1 - 0.06, 1.4, 2.99, b - 0.26, b - 0.25, M["glow"], 0)
    box("main display lintel", -2.9, 2.9, 3.2, 3.35, b - 0.3, b, M["rib"], 0.02)
    for xl in (-2.4, -0.8, 0.8, 2.4):
        box(f"main display lamp {xl:+.1f}", xl - 0.12, xl + 0.12, 3.15, 3.2, b - 0.3, b - 0.24, M["lamp"], 0)


def hatch(b):
    x0, x1, h = 6.0, 7.0, 2.1
    box("hatch frame left", x0 - 0.16, x0, 0, h + 0.16, b - 0.12, b, M["metal"], 0.03)
    box("hatch frame right", x1, x1 + 0.16, 0, h + 0.16, b - 0.12, b, M["metal"], 0.03)
    box("hatch frame top", x0, x1, h, h + 0.16, b - 0.12, b, M["metal"], 0.03)
    box("hatch door", x0 + 0.02, x1 - 0.02, 0.02, h - 0.02, b - 0.06, b, M["panel"], 0.06)
    box("hatch door plate", x0 + 0.14, x1 - 0.14, 0.3, h - 0.3, b - 0.08, b - 0.06, M["metal"], 0.02)
    xc = (x0 + x1) / 2
    torus("hatch wheel", xc, 1.15, b - 0.15, 0.2, 0.025, M["rib"])
    cylinder("hatch wheel hub", b - 0.17, b - 0.08, 1.15, xc, 0.05, M["rib"], horizontal="z")
    for a in range(3):
        spoke = box(f"hatch wheel spoke {a}", xc - 0.2, xc + 0.2, 1.135, 1.165, b - 0.16, b - 0.14, M["rib"], 0.004)
        spin([spoke], (xc, 1.15, b - 0.15), a * math.pi / 3, axis="depth")
    box("hatch light", x0 + 0.3, x1 - 0.3, h + 0.22, h + 0.3, b - 0.08, b, M["lamp"], 0.01)


# ---------------------------------------------------------------- the command island

def plotting_table():
    """The ship's plot, at deck scale: a square-cornered table with a radar scope inset."""
    x0, x1, z0, z1 = TABLE
    cz = (z0 + z1) / 2
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
    box("chart top glow", x0 + 0.22, x1 - 0.22, 0.965, 0.978, z0 + 0.22, z1 - 0.22, M["glow"], 0.0)
    torus("chart scope ring", 0.0, 0.98, cz, 0.85, 0.008, M["dark"], facing="y")
    box("chart scope cross x", x0 + 0.3, x1 - 0.3, 0.978, 0.982, cz - 0.006, cz + 0.006, M["dark"], 0)
    box("chart scope cross z", -0.006, 0.006, 0.978, 0.982, z0 + 0.3, z1 - 0.3, M["dark"], 0)
    # control strips on all four rims, keys and a few lamps
    for i, (zf, zb) in enumerate([(z0 + 0.02, z0 + 0.13), (z1 - 0.13, z1 - 0.02)]):
        box(f"chart control strip {i}", -1.2, 1.2, 0.97, 1.0, zf, zb, M["metal"], 0.006)
        for k in range(10):
            xk = -1.08 + k * 0.24
            box(f"chart key {i} {k}", xk - 0.07, xk + 0.07, 1.0, 1.012, zf + 0.025, zb - 0.025,
                M["lamp"] if k % 3 == 0 else M["rib"], 0.003)
    for side in (-1, 1):
        xs = x0 + 0.02 if side < 0 else x1 - 0.13
        box(f"chart end strip {side}", xs, xs + 0.11, 0.97, 1.0, z0 + 0.5, z1 - 0.5, M["metal"], 0.006)
    for xs in (x0 + 0.08, x1 - 0.08):
        for zs in (z0 + 0.08, z1 - 0.08):
            cylinder(f"holo emitter {xs:+.1f} {zs:.1f}", xs, zs, 0.97, 1.04, 0.045, M["metal"], 16, 0.006)
            cylinder(f"holo emitter lens {xs:+.1f} {zs:.1f}", xs, zs, 1.04, 1.05, 0.03, M["lamp"], 16)


def overhead_cluster():
    """The monitor cluster hung over the plot, four screens facing out, with its lamp ring."""
    cz = (TABLE[2] + TABLE[3]) / 2
    c = ROOM["ceiling"]
    y0, y1 = 2.95, 3.5
    for ox, oz in ((-0.9, -0.5), (0.9, -0.5), (-0.9, 0.5), (0.9, 0.5)):
        cylinder(f"cluster hanger {ox:+.1f} {oz:+.1f}", ox, cz + oz, y1, c - 0.4, 0.025, M["rib"], 10)
    box("cluster spine", -0.85, 0.85, y0 + 0.05, y1, cz - 0.42, cz + 0.42, M["metal"], 0.03)
    # front and back screens face along the camera axis, end screens face sideways
    for k, zf in enumerate((cz - 0.45, cz + 0.45)):
        sign = -1 if k == 0 else 1
        box(f"cluster screen {k} housing", -0.95, 0.95, y0, y1 + 0.05, min(zf, zf + sign * 0.1),
            max(zf, zf + sign * 0.1), M["metal"], 0.025)
        box(f"cluster screen {k}", -0.86, 0.86, y0 + 0.07, y1 - 0.02, min(zf + sign * 0.1, zf + sign * 0.11),
            max(zf + sign * 0.1, zf + sign * 0.11), M["glow"], 0)
    for side in (-1, 1):
        xf = side * 0.9
        lo, hi = sorted((xf, xf + side * 0.1))
        box(f"cluster end {side} housing", lo, hi, y0, y1 + 0.05, cz - 0.4, cz + 0.4, M["metal"], 0.025)
        slo, shi = sorted((xf + side * 0.1, xf + side * 0.11))
        box(f"cluster end {side} screen", slo, shi, y0 + 0.07, y1 - 0.02, cz - 0.32, cz + 0.32, M["glow"], 0)
    torus("cluster lamp ring", 0.0, y0 - 0.06, cz, 1.6, 0.035, M["lamp"], facing="y")
    for a in range(4):
        ang = math.pi / 4 + a * math.pi / 2
        cylinder(f"cluster ring strut {a}", 1.1 * math.cos(ang), cz + 1.1 * math.sin(ang), y0 - 0.06, y0 + 0.2,
                 0.015, M["rib"], 8)


def island_rails():
    """Handrails on the island's flanks, so the plot reads as a place apart."""
    for side in (-1, 1):
        xr = side * 3.45
        cylinder(f"island rail {side}", 6.4, 9.2, 1.0, xr, 0.03, M["rail"], 16, horizontal="z")
        for zp in (6.4, 7.8, 9.2):
            cylinder(f"island post {side} {zp}", xr, zp, 0.0, 1.0, 0.03, M["rail"], 12)


# ---------------------------------------------------------------- stations

def crt(name, xc, z_front, y0, w=0.55, h=0.42, depth=0.36):
    frustum(f"{name} housing", (xc - w / 2, xc + w / 2, y0 + 0.02, y0 + h + 0.02, z_front),
            (xc - w * 0.32, xc + w * 0.32, y0 + 0.07, y0 + h * 0.72, z_front + depth), M["metal"], 0.025)
    box(f"{name} bezel", xc - w / 2 + 0.04, xc + w / 2 - 0.04, y0 + 0.06, y0 + h - 0.02,
        z_front - 0.012, z_front, M["dark"], 0.01)
    box(f"{name} screen", xc - w / 2 + 0.07, xc + w / 2 - 0.07, y0 + 0.09, y0 + h - 0.05,
        z_front - 0.02, z_front - 0.012, M["glow"], 0.012)
    box(f"{name} foot", xc - w * 0.25, xc + w * 0.25, y0, y0 + 0.03, z_front + 0.05, z_front + 0.3, M["dark"], 0.01)


def swivel_chair(name, xc, zc):
    """A desk chair facing +z (towards its console); its back is to the camera. Office-chair
    proportions: cushion top 0.47 m, backrest top 0.95 m, armrests 0.65 m."""
    for k in range(5):
        a = k * 2 * math.pi / 5 + 0.3
        leg = box(f"{name} leg {k}", xc - 0.02, xc + 0.02, 0.05, 0.09, zc, zc + 0.32, M["chair"], 0.008)
        spin([leg], (xc, 0.0, zc), a)
        cylinder(f"{name} caster {k}", xc - 0.32 * math.sin(a), zc + 0.32 * math.cos(a), 0.0, 0.05, 0.03,
                 M["dark"], 12)
    cylinder(f"{name} column", xc, zc, 0.07, 0.39, 0.035, M["metal"], 16)
    box(f"{name} seat pan", xc - 0.2, xc + 0.2, 0.38, 0.41, zc - 0.2, zc + 0.2, M["metal"], 0.01)
    box(f"{name} cushion", xc - 0.24, xc + 0.24, 0.41, 0.47, zc - 0.24, zc + 0.23, M["chair"], 0.035)
    zb = zc - 0.27
    prism(f"{name} back", xc - 0.23, xc + 0.23,
          [(0.55, zb), (0.55, zb + 0.06), (0.95, zb + 0.02), (0.95, zb - 0.05)][::-1], M["chair"], 0.03)
    box(f"{name} back strut", xc - 0.03, xc + 0.03, 0.44, 0.58, zb - 0.02, zb + 0.04, M["metal"], 0.008)
    for s in (-1, 1):
        box(f"{name} arm {s}", xc + s * 0.27 - 0.03, xc + s * 0.27 + 0.03, 0.45, 0.62, zc - 0.05, zc - 0.01,
            M["metal"], 0.008)
        box(f"{name} armrest {s}", xc + s * 0.27 - 0.04, xc + s * 0.27 + 0.04, 0.62, 0.65, zc - 0.2, zc + 0.12,
            M["chair"], 0.015)


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
    forearms on the keyboard deck. A painter's mannequin, not a figure card: the plate paints it."""
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
        limb(f"{name} upper arm {s}", (xc + s * 0.21, 1.03, zc + 0.0), (xc + s * 0.22, 0.8, zc + 0.14), 0.05, m)
        limb(f"{name} forearm {s}", (xc + s * 0.22, 0.8, zc + 0.14), (xc + s * 0.12, 0.84, zc + 0.56), 0.04, m)


def watch_bank(name, xc, zf, seats=2, rack=1.95, chairs=True, crew=()):
    """A run of watch consoles, faces at z = zf looking towards -z; 1.0 m per seat.
    Desk, sloped keyboard deck, CRTs, a rack behind with repeaters and status lamps."""
    half = seats * 0.5
    x0, x1 = xc - half, xc + half
    box(f"{name} body", x0, x1, 0.0, 0.72, zf, zf + 0.75, M["metal"], 0.015)
    box(f"{name} kick", x0 + 0.05, x1 - 0.05, 0.0, 0.1, zf - 0.02, zf + 0.05, M["dark"], 0.004)
    for k in range(seats):
        xs = x0 + 0.5 + k
        for d in range(3):
            box(f"{name} door {k} {d}", xs - 0.42, xs + 0.42, 0.14 + d * 0.19, 0.3 + d * 0.19,
                zf - 0.02, zf, M["panel"], 0.006)
    box(f"{name} top", x0 - 0.04, x1 + 0.04, 0.72, 0.76, zf - 0.08, zf + 0.75, M["metal"], 0.012)
    for k in range(seats):
        xs = x0 + 0.5 + k
        prism(f"{name} deck {k}", xs - 0.46, xs + 0.46,
              [(0.76, zf - 0.05), (0.76, zf + 0.22), (0.84, zf + 0.22), (0.79, zf - 0.05)], M["panel"], 0.008)
        box(f"{name} keys {k}", xs - 0.28, xs + 0.28, 0.79, 0.82, zf + 0.01, zf + 0.15, M["dark"], 0.006)
        crt(f"{name} crt {k}", xs, zf + 0.24, 0.8, w=0.5, h=0.4, depth=0.3)
    box(f"{name} rack", x0, x1, 0.76, rack, zf + 0.55, zf + 0.75, M["metal"], 0.015)
    prism(f"{name} hood", x0 - 0.04, x1 + 0.04,
          [(rack, zf + 0.75), (rack, zf + 0.35), (rack + 0.1, zf + 0.3), (rack + 0.16, zf + 0.75)], M["metal"], 0.015)
    box(f"{name} hood light", x0 + 0.08, x1 - 0.08, rack - 0.02, rack, zf + 0.38, zf + 0.5, M["lamp"], 0)
    for k in range(seats):
        xs = x0 + 0.5 + k
        box(f"{name} repeater {k} frame", xs - 0.36, xs + 0.36, 1.33, 1.78, zf + 0.5, zf + 0.55, M["dark"], 0.012)
        box(f"{name} repeater {k} screen", xs - 0.32, xs + 0.32, 1.37, 1.74, zf + 0.49, zf + 0.5, M["glow"], 0)
    for k in range(int(seats * 10)):
        xk = x0 + 0.06 + k * 0.1
        box(f"{name} lamp {k}", xk - 0.02, xk + 0.02, 1.83, 1.87, zf + 0.535, zf + 0.55,
            M["lamp"] if k % 3 else M["rib"], 0)
    if chairs:
        for k in range(seats):
            swivel_chair(f"{name} chair {k}", x0 + 0.5 + k, zf - 0.6)
            if k in crew:
                seated_crew(f"{name} crew {k}", x0 + 0.5 + k, zf - 0.6)


def side_banks():
    """The east and west watch banks along the flanks, operators facing the walls."""
    for side in (-1, 1):
        for j, zc in enumerate((7.0, 9.4)):
            xc = side * 6.6
            # most of the watch is stood: one seat per side left empty
            crew = (0, 1) if (side < 0) == (j == 0) else (1,)
            parts = made(watch_bank, f"{'west' if side < 0 else 'east'} bank {j}", xc, zc - 0.375, seats=2,
                         crew=crew)
            spin(parts, (xc, 0.0, zc), -side * math.pi / 2)


def data_cabinets():
    for i, (xc, zc) in enumerate([(-7.35, 4.6), (7.35, 4.6), (-7.35, 11.6), (7.35, 11.6)]):
        y0 = TIER["height"] if zc > TIER["front"] else 0.0
        with lifted(dy=y0):
            box(f"data cabinet {i}", xc - 0.45, xc + 0.45, 0.0, 2.0, zc - 0.4, zc + 0.4, M["metal"], 0.02)
            face = xc + (0.46 if xc < 0 else -0.46)
            lo, hi = sorted((face, face + (0.02 if xc < 0 else -0.02)))
            for d in range(2):
                box(f"data cabinet {i} drawer {d}", lo, hi, 0.15 + d * 0.92, 0.95 + d * 0.92, zc - 0.33, zc + 0.33,
                    M["panel"], 0.01)
                box(f"data cabinet {i} pull {d}", lo, hi, 0.5 + d * 0.92, 0.55 + d * 0.92, zc - 0.15, zc + 0.15,
                    M["rail"], 0.005)


def foreground_consoles():
    """Low consoles at the near corners, seen from behind, framing the shot."""
    for side in (-1, 1):
        x0, x1 = sorted((side * 3.9, side * 6.9))
        prism(f"near console {side}", x0, x1,
              [(0.0, 3.4), (0.0, 4.2), (1.05, 4.2), (1.18, 3.75), (1.05, 3.4)], M["metal"], 0.02)
        box(f"near console {side} kick", x0 + 0.05, x1 - 0.05, 0.0, 0.1, 3.36, 3.42, M["dark"], 0.004)
        for k in range(3):
            xm = x0 + 0.5 + k
            frustum(f"near console {side} monitor {k} back",
                    (xm - 0.25, xm + 0.25, 1.2, 1.5, 3.85), (xm - 0.38, xm + 0.38, 1.12, 1.65, 4.15), M["metal"], 0.02)
        for k in range(6):
            box(f"near console {side} panel {k}", x0 + 0.1 + k * 0.48, x0 + 0.5 + k * 0.48, 0.2, 0.9, 3.38, 3.4,
                M["panel"], 0.006)


def plotting_board():
    """A free-standing glass plotting board, the kind written on from behind."""
    for side in (-1, 1):
        xc, zc = side * 4.6, 10.0
        parts = made(_plotting_board, xc, zc)
        spin(parts, (xc, 0.0, zc), -side * 0.5)


def _plotting_board(xc, zc):
    box("plot board foot", xc - 0.6, xc + 0.6, 0.0, 0.06, zc - 0.25, zc + 0.25, M["metal"], 0.015)
    for s in (-1, 1):
        box(f"plot board post {s}", xc + s * 0.62 - 0.04, xc + s * 0.62 + 0.04, 0.0, 2.25, zc - 0.04, zc + 0.04,
            M["metal"], 0.01)
    box("plot board frame", xc - 0.62, xc + 0.62, 0.75, 2.2, zc - 0.03, zc + 0.03, M["rib"], 0.01)
    box("plot board pane", xc - 0.55, xc + 0.55, 0.82, 2.13, zc - 0.01, zc + 0.01, M["pane"], 0)


def comms_bank():
    """Back-left on the tier: the comms watch, two operators. Its back is the bulkhead."""
    b = ROOM["back"]
    for j, xc in enumerate((-5.62, -3.92)):
        watch_bank(f"comms bank {j}", xc, b - 0.95, seats=1, rack=2.3)


def ledger_station():
    b = ROOM["back"]
    x0, x1, zf = 3.0, 5.4, b - 0.8
    for i, (a, bb) in enumerate([(x0, 3.55), (4.85, x1)]):
        box(f"ledger pedestal {i}", a, bb, 0.0, 0.72, zf + 0.05, b, M["crate"], 0.015)
        for k in range(3):
            box(f"ledger drawer {i} {k}", a + 0.04, bb - 0.04, 0.06 + k * 0.22, 0.24 + k * 0.22,
                zf + 0.03, zf + 0.05, M["panel"], 0.008)
            box(f"ledger handle {i} {k}", (a + bb) / 2 - 0.08, (a + bb) / 2 + 0.08, 0.2 + k * 0.22,
                0.22 + k * 0.22, zf, zf + 0.03, M["rail"], 0.004)
    box("ledger modesty", 3.55, 4.85, 0.25, 0.72, b - 0.15, b - 0.1, M["crate"], 0.01)
    box("ledger top", x0 - 0.05, x1 + 0.05, 0.72, 0.77, zf - 0.05, b, M["crate"], 0.015)
    crt("ledger crt", 3.81, zf + 0.25, 0.77, w=0.46, h=0.4, depth=0.34)
    box("ledger keyboard", 3.6, 4.05, 0.77, 0.8, zf + 0.05, zf + 0.2, M["dark"], 0.006)
    for k in range(5):
        box(f"ledger paper {k}", 4.4, 4.9, 0.77 + k * 0.018, 0.785 + k * 0.018, zf + 0.1, zf + 0.48, M["paper"],
            0.002, rot=0.05 * ((-1) ** k) * (k + 1) / 3)
    box("ledger binder", 4.95, 5.25, 0.77, 1.05, b - 0.35, b - 0.1, M["panel"], 0.01)
    cylinder("ledger lamp base", 5.15, zf + 0.2, 0.77, 0.8, 0.07, M["metal"], 16, 0.005)
    cylinder("ledger lamp arm", 5.15, zf + 0.2, 0.8, 1.18, 0.012, M["metal"], 8)
    frustum("ledger lamp shade", (5.0, 5.2, 1.12, 1.2, zf + 0.16), (5.08, 5.18, 1.15, 1.2, zf + 0.24), M["metal"], 0.006)
    box("supply crate", 2.2, 2.9, 0.0, 0.6, b - 0.9, b - 0.2, M["crate"], 0.02)
    for i, (a, bb) in enumerate([(2.2, 2.26), (2.84, 2.9)]):
        box(f"supply crate band {i}", a - 0.01, bb + 0.01, 0.0, 0.6, b - 0.91, b - 0.19, M["rib"], 0.008)
    box("supply crate lid rim", 2.19, 2.91, 0.56, 0.62, b - 0.91, b - 0.19, M["rib"], 0.01)
    box("supply case", 2.3, 2.8, 0.62, 0.88, b - 0.75, b - 0.3, M["panel"], 0.02, rot=0.12)


def roster_board():
    b = ROOM["back"]
    box("roster frame", 3.05, 5.35, 1.4, 2.4, b - 0.06, b, M["rib"], 0.015)
    box("roster board", 3.12, 5.28, 1.47, 2.33, b - 0.07, b - 0.06, M["board"], 0.0)
    for r in range(4):
        for k in range(6):
            box(f"roster card {r} {k}", 3.22 + k * 0.34, 3.48 + k * 0.34, 2.12 - r * 0.2, 2.26 - r * 0.2,
                b - 0.08, b - 0.07, M["paper"], 0.0)


def stand_ins():
    col = bpy.data.collections.new("stand-ins")
    bpy.context.scene.collection.children.link(col)
    for pid, (x, z, h, floor) in PEOPLE.items():
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
        "plotting-table": "chart table", "plot-glow": "chart top glow", "overhead-cluster": "cluster",
        "comms-bank": "comms bank", "ledger": "ledger", "roster-board": "roster", "main-display": "main display",
        "hatch": "hatch", "west-bank": "west bank", "east-bank": "east bank", "rear-tier": "tier",
    }
    props = {k: bbox(v) for k, v in stations.items()}
    g = props["plot-glow"]
    gz = (g[4] + g[5]) / 2
    markers = {
        "plot-corners": [project(g[0], g[3], g[4]), project(g[1], g[3], g[4]),
                         project(g[1], g[3], g[5]), project(g[0], g[3], g[5])],
        "plot-centre": project(0, g[3], gz),
        "up-at-plot-centre": project(0, g[3] + 1.0, gz),
        "cluster-front-screen": centre("cluster screen 0"),
        "main-display": {f"screen-{i}": centre(f"main display {i} screen") for i in range(3)},
        "comms-screens": {f"crt-{j}": centre(f"comms bank {j} crt 0 screen") for j in range(2)},
        "comms-repeaters": {f"repeater-{j}": centre(f"comms bank {j} repeater 0 screen") for j in range(2)},
        "west-bank-screens": {f"{j}-{k}": centre(f"west bank {j} crt {k} screen") for j in range(2) for k in range(2)},
        "east-bank-screens": {f"{j}-{k}": centre(f"east bank {j} crt {k} screen") for j in range(2) for k in range(2)},
        "wall-displays": {f"{'west' if s < 0 else 'east'}-{i}": centre(f"side display {s} {i} screen")
                          for s in (-1, 1) for i in range(1, 7)},
        "ledger-screen": centre("ledger crt screen"),
        "roster-board": centre("roster board"),
        "hatch-centre": centre("hatch door"),
    }
    people = {k: {"x": x, "z": z, "heightM": h, "floorM": f, "feetPx": project(x, f, z),
                  "headPx": project(x, f + h, z)} for k, (x, z, h, f) in PEOPLE.items()}
    doc = {"size": [W, H], "units": "metres; x right, y up, z ahead of the camera",
           "camera": CAM, "projection": "u = cx + focal*x/z, v = cy - focal*(y - height)/z",
           "room": ROOM, "tier": TIER, "table": TABLE, "props": props, "markers": markers, "people": people}
    (OUT / "deck-guide-v6.layout.json").write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
    for k, v in people.items():
        print(k, "height px", round(v["feetPx"][1] - v["headPx"][1]))


def main():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    materials()
    shell()
    rear_tier()
    plotting_table()
    overhead_cluster()
    island_rails()
    side_banks()
    data_cabinets()
    foreground_consoles()
    plotting_board()
    with lifted(dy=TIER["height"]):
        main_display()
        comms_bank()
        ledger_station()
        roster_board()
        hatch(ROOM["back"])
    people = stand_ins()
    cam = camera()
    render_settings()
    check_camera(cam)
    sc = bpy.context.scene
    if "--masks" in sys.argv:
        people.hide_render = True
        occluder_masks()
        return
    for hidden, name in ((True, "deck-guide-v6.png"), (False, "deck-guide-v6-people.png")):
        people.hide_render = hidden
        sc.render.filepath = str(OUT / name)
        bpy.ops.render.render(write_still=True)
    layout()


# Room surfaces that never stand between the camera and a person.
SURFACES = ("floor", "command island", "back wall", "left wall", "right wall", "ceiling", "stringer",
            "beam", "side ", "tier deck", "back ")


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
    for pid, (x, z, h, floor) in PEOPLE.items():
        for ob in meshes:
            near = min((ob.matrix_world @ Vector(c)).y for c in ob.bound_box)
            ob.hide_render = near >= z or ob.name.startswith(SURFACES)
        sc.render.filepath = str(out / f"{pid}.occluder.png")
        bpy.ops.render.render(write_still=True)


main()
