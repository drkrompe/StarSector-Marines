"""Blender blockout of the command deck at real scale: the ImageGen layout guide for plate v3.

Run from the repository root (headless, a few seconds):

  "C:/Program Files/Blender Foundation/Blender 5.2/blender.exe" --background --factory-startup \
      --python art-source/hq-command-deck/blender/deck_blockout.py

The PIL blockout (`blockout.py`) fixed the proportions with axis-aligned boxes, and
a painter skinning boxes paints boxes. This rebuilds the same room, camera and stands
with the shapes a painter should keep: a bevelled chart table on a recessed plinth,
CRTs with tapered housings on sloped keyboard decks, swivel chairs, I-beam ceiling
ribs, panelled and ribbed walls with conduit, a mullioned viewport and a wheel hatch.

Coordinates in this file are the blockout's: x right, y up, z = metres in front of
the camera. They become Blender's (X, Y, Z) = (x, z, y) in one place, `V`.

Writes beside the script's folder:
  deck-guide-v4.png          grey guide for ImageGen (no people)
  deck-guide-v4-people.png   the same with stand-ins at their stands, for proportions
  deck-guide-v4.layout.json  camera, room, props and projected markers
"""
import json
import math
from pathlib import Path

import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Matrix, Vector

OUT = Path(__file__).resolve().parent.parent
W, H = 2172, 724
CAM = {"height": 2.7, "focal": 1050.0, "cx": W / 2, "cy": 170.0}
ROOM = {"halfWidth": 7.0, "near": 2.0, "back": 11.0, "ceiling": 4.2}
VIEWPORT = (-2.6, 2.6, 1.45, 3.3)

# Feet (x, z) and height. Seated figures are measured to the top of the head.
PEOPLE = {
    "castle": (0.7, 7.55, 1.68),
    "commander": (-2.1, 6.0, 1.82),
    "comms-left": (-5.62, 9.62, 1.30),
    "comms-right": (-3.92, 9.62, 1.30),
    "quartermaster": (4.2, 9.75, 1.80),
}


def V(x, y, z):
    return (x, z, y)


# ---------------------------------------------------------------- kit

def mat(name, grey, glow=False):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (grey, grey, grey, 1) if not glow else (grey, grey, grey * 0.97, 1)
    return m


M = {}


def materials():
    M.update(
        floor=mat("floor plate", .16), wall=mat("bulkhead", .30), panel=mat("wall panel", .24),
        rib=mat("structural rib", .11), ceiling=mat("ceiling", .12), metal=mat("console metal", .20),
        dark=mat("recess", .05), table=mat("chart table", .17), chair=mat("chair", .09),
        pipe=mat("conduit", .14), board=mat("roster board", .55), paper=mat("paper", .65),
        glass=mat("viewport glass", .015), crate=mat("crate", .22),
        glow=mat("glow surface", .95, True), lamp=mat("light", .8, True),
        person=mat("stand-in", .6),
    )
    M["person"].diffuse_color = (.8, .12, .1, 1)


def finish(ob, m, bevel, segments=2, collection=None):
    ob.data.materials.append(m)
    (collection or bpy.context.scene.collection).objects.link(ob)
    if bevel:
        b = ob.modifiers.new("edge radius", "BEVEL")
        b.width = bevel
        b.segments = segments
        b.limit_method = "ANGLE"
    return ob


def solid(name, verts, faces, m, bevel=0.0, collection=None):
    me = bpy.data.meshes.new(name)
    me.from_pydata([V(*p) for p in verts], [], faces)
    me.update()
    return finish(bpy.data.objects.new(name, me), m, bevel, collection=collection)


BOX_FACES = [(0, 1, 2, 3), (4, 7, 6, 5), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]


def box(name, x0, x1, y0, y1, z0, z1, m, bevel=0.012, rot=0.0):
    """Axis box in deck coordinates, optionally turned about its vertical axis."""
    cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
    pts = []
    for y in (y0, y1):
        for x, z in ((x0, z0), (x1, z0), (x1, z1), (x0, z1)):
            dx, dz = x - cx, z - cz
            c, s = math.cos(rot), math.sin(rot)
            pts.append((cx + dx * c - dz * s, y, cz + dx * s + dz * c))
    # bottom ring 0-3, top ring 4-7
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
    """Vertical cylinder at (x, z), or horizontal along 'x' / 'z' when given: then (x, z) are
    replaced by start/end along that axis, with y0 as the centre height and y1 the cross position."""
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


def spin(ob, pivot, axis, angle):
    """Turn an object about a deck-space pivot; axis is Blender's (0 x, 1 depth, 2 up)."""
    ob.data.transform(Matrix.Translation(-Vector(V(*pivot))))
    ob.rotation_euler[axis] = angle
    ob.location = Vector(V(*pivot))


def torus(name, x, y, z, major, minor, m, facing="z"):
    bpy.ops.mesh.primitive_torus_add(major_radius=major, minor_radius=minor, major_segments=40,
                                     minor_segments=12, location=V(x, y, z))
    ob = bpy.context.object
    if facing == "z":
        ob.rotation_euler[0] = math.pi / 2
    ob.name = name
    ob.data.materials.append(m)
    bpy.ops.object.shade_smooth()
    return ob


# ---------------------------------------------------------------- room

def shell():
    hw, n, b, c = ROOM["halfWidth"], ROOM["near"], ROOM["back"], ROOM["ceiling"]
    t = 0.25
    vx0, vx1, vy0, vy1 = VIEWPORT
    # back wall built around the viewport opening
    box("back wall low", -hw, hw, 0, vy0, b, b + t, M["wall"], 0)
    box("back wall high", -hw, hw, vy1, c, b, b + t, M["wall"], 0)
    box("back wall left", -hw, vx0, vy0, vy1, b, b + t, M["wall"], 0)
    box("back wall right", vx1, hw, vy0, vy1, b, b + t, M["wall"], 0)
    box("viewport glass", vx0, vx1, vy0, vy1, b + 0.2, b + 0.22, M["glass"], 0)
    box("left wall", -hw - t, -hw, 0, c, n, b + t, M["wall"], 0)
    box("right wall", hw, hw + t, 0, c, n, b + t, M["wall"], 0)
    box("ceiling", -hw - t, hw + t, c, c + t, n, b + t, M["ceiling"], 0)

    # floor: plates on a 1.4 m pitch with a seam, a cable trunk to the comms bank
    pitch, seam = 1.4, 0.025
    x = -hw
    while x < hw - 1e-6:
        z = n
        while z < b - 1e-6:
            box(f"floor plate {x:+.1f} {z:.1f}", x + seam, min(x + pitch, hw) - seam, -0.03, 0.0,
                z + seam, min(z + pitch, b) - seam, M["floor"], 0.006)
            z += pitch
        x += pitch
    box("floor seam bed", -hw, hw, -0.06, -0.03, n, b, M["dark"], 0)
    box("cable trunk", -5.2, -1.45, 0.0, 0.05, 6.25, 6.55, M["metal"], 0.015)
    box("cable trunk riser", -5.2, -4.9, 0.0, 0.05, 6.55, 10.2, M["metal"], 0.015)
    # deck kick strip along the back wall
    box("back kick strip", -hw, hw, 0, 0.12, b - 0.04, b, M["dark"], 0.004)

    # back wall: wainscot panels, ribs, conduit, lights
    for i, (x0, x1) in enumerate([(-6.9, -5.4), (-5.3, -3.8), (-3.7, -2.95), (-2.6, -0.9), (-0.8, 0.8),
                                  (0.9, 2.6), (2.95, 3.9), (4.0, 5.5)]):
        box(f"back wainscot {i}", x0, x1, 0.15, 1.05 if x1 <= -2.6 or x0 >= 2.6 else vy0 - 0.12,
            b - 0.03, b, M["panel"], 0.008)
    for i, (x0, x1) in enumerate([(-6.9, -4.95), (-4.75, -2.95), (2.95, 5.45)]):
        box(f"back upper panel {i}", x0, x1, 2.55, 3.35, b - 0.03, b, M["panel"], 0.008)
    for i, xr in enumerate([-6.95, -4.85, -2.85, 2.85, 5.6, 6.95]):
        y0 = 2.3 if xr == -4.85 else 0.0
        prism(f"back rib {i}", xr - 0.1, xr + 0.1,
              [(y0, b), (y0, b - 0.14), (c - 0.35, b - 0.14), (c, b - 0.4), (c, b)], M["rib"], 0.015)
    for i, (yy, r) in enumerate([(3.55, 0.055), (3.7, 0.045), (3.82, 0.03)]):
        cylinder(f"back conduit {i}", -hw, hw, yy, b - 0.22 - i * 0.03, r, M["pipe"], horizontal="x")
    for xp in (-6.45, 6.35):
        cylinder(f"conduit drop {xp:+.1f}", xp, b - 0.12, 2.3 if xp < 0 else 2.4, c, 0.05, M["pipe"])
    for i in range(14):
        xb = -6.7 + i * 1.0
        if abs(xb) < 0.2:
            continue
        box(f"conduit clamp {i}", xb - 0.03, xb + 0.03, 3.45, 3.9, b - 0.3, b - 0.16, M["rib"], 0.005)
    for xl in (-3.9, 4.25):
        box(f"wall lamp {xl:+.1f}", xl - 0.3, xl + 0.3, 3.38, 3.46, b - 0.12, b - 0.02, M["lamp"], 0.01)

    # side walls: ribs on a 1.4 m pitch, wainscot, a conduit run
    for side in (-1, 1):
        xw = side * hw
        for i in range(6):
            z = 3.6 + i * 1.4
            x0, x1 = (xw, xw - side * 0.14) if side > 0 else (xw, xw + 0.14)
            box(f"side rib {side} {i}", min(x0, x1), max(x0, x1), 0, c, z - 0.1, z + 0.1, M["rib"], 0.015)
            box(f"side panel {side} {i}", min(xw, xw - side * 0.03), max(xw, xw - side * 0.03), 0.15, 1.1,
                z + 0.15, z + 1.25, M["panel"], 0.008)
        cylinder(f"side conduit {side}", n, b, 3.4, xw - side * 0.22, 0.06, M["pipe"], horizontal="z")

    # ceiling: two I-beams across, longitudinal stringers, light strips
    for i, zc in enumerate([6.18, 8.98]):
        box(f"beam web {i}", -hw, hw, 3.78, c, zc - 0.07, zc + 0.07, M["rib"], 0.01)
        box(f"beam flange {i}", -hw, hw, 3.72, 3.78, zc - 0.2, zc + 0.2, M["rib"], 0.01)
        for xs in range(-6, 7, 2):
            box(f"beam stiffener {i} {xs}", xs - 0.03, xs + 0.03, 3.78, c, zc - 0.19, zc + 0.19, M["rib"], 0.005)
    for xs in (-4.5, -1.5, 1.5, 4.5):
        box(f"stringer {xs:+.1f}", xs - 0.08, xs + 0.08, c - 0.18, c, n, b, M["rib"], 0.008)
    for xs in (-3.0, 3.0):
        box(f"ceiling light {xs:+.1f}", xs - 0.25, xs + 0.25, c - 0.06, c, 6.6, 8.6, M["lamp"], 0.01)
        box(f"ceiling light rear {xs:+.1f}", xs - 0.25, xs + 0.25, c - 0.06, c, 9.4, 10.8, M["lamp"], 0.01)


def viewport():
    vx0, vx1, vy0, vy1 = VIEWPORT
    b, f, d = ROOM["back"], 0.22, 0.16
    box("viewport bezel bottom", vx0 - f, vx1 + f, vy0 - f, vy0, b - d, b, M["metal"], 0.03)
    box("viewport bezel top", vx0 - f, vx1 + f, vy1, vy1 + f, b - d, b, M["metal"], 0.03)
    box("viewport bezel left", vx0 - f, vx0, vy0, vy1, b - d, b, M["metal"], 0.03)
    box("viewport bezel right", vx1, vx1 + f, vy0, vy1, b - d, b, M["metal"], 0.03)
    box("viewport sill", vx0 - 0.1, vx1 + 0.1, vy0 - 0.08, vy0, b - 0.32, b - d, M["metal"], 0.02)
    for i, xm in enumerate((-0.87, 0.87)):
        box(f"viewport mullion {i}", xm - 0.05, xm + 0.05, vy0, vy1, b + 0.05, b + 0.18, M["rib"], 0.012)
    box("viewport transom", vx0, vx1, 2.33, 2.42, b + 0.05, b + 0.18, M["rib"], 0.012)
    for xs in (vx0 - 0.11, vx1 + 0.11):
        for ys in (vy0 - 0.11, vy1 + 0.11):
            box(f"bezel bolt {xs:+.2f} {ys:.2f}", xs - 0.04, xs + 0.04, ys - 0.04, ys + 0.04,
                b - d - 0.02, b - d, M["rib"], 0.01)


def hatch():
    b = ROOM["back"]
    x0, x1, h = 5.8, 6.8, 2.1
    box("hatch frame left", x0 - 0.16, x0, 0, h + 0.16, b - 0.12, b, M["metal"], 0.03)
    box("hatch frame right", x1, x1 + 0.16, 0, h + 0.16, b - 0.12, b, M["metal"], 0.03)
    box("hatch frame top", x0, x1, h, h + 0.16, b - 0.12, b, M["metal"], 0.03)
    box("hatch door", x0 + 0.02, x1 - 0.02, 0.02, h - 0.02, b - 0.06, b, M["panel"], 0.06)
    box("hatch door plate", x0 + 0.14, x1 - 0.14, 0.3, h - 0.3, b - 0.08, b - 0.06, M["metal"], 0.02)
    torus("hatch wheel", (x0 + x1) / 2, 1.15, b - 0.15, 0.2, 0.025, M["rib"])
    cylinder("hatch wheel hub", b - 0.17, b - 0.08, 1.15, 6.3, 0.05, M["rib"], horizontal="z")
    for a in range(3):
        spoke = box(f"hatch wheel spoke {a}", 6.1, 6.5, 1.135, 1.165, b - 0.16, b - 0.14, M["rib"], 0.004)
        spin(spoke, (6.3, 1.15, b - 0.15), 1, a * math.pi / 3)
    box("hatch light", x0 + 0.3, x1 - 0.3, h + 0.22, h + 0.3, b - 0.08, b, M["lamp"], 0.01)


# ---------------------------------------------------------------- props

def chart_table():
    # recessed plinth with panel lines, a kick plinth, a bevelled top with an inset glowing plate
    box("chart table kick", -1.35, 1.35, 0.0, 0.08, 5.85, 6.95, M["dark"], 0.01)
    box("chart table plinth", -1.25, 1.25, 0.08, 0.74, 5.95, 6.85, M["table"], 0.02)
    for i, xp in enumerate((-0.9, -0.3, 0.3, 0.9)):
        box(f"chart plinth panel {i}", xp - 0.26, xp + 0.26, 0.16, 0.66, 5.93, 5.95, M["metal"], 0.008)
    for i, zp in enumerate((6.15, 6.65)):
        for side in (-1, 1):
            xs = side * 1.25
            box(f"chart plinth side panel {i} {side}", min(xs, xs + side * 0.02), max(xs, xs + side * 0.02),
                0.16, 0.66, zp - 0.18, zp + 0.18, M["metal"], 0.008)
    # cantilevered top: underside chamfered towards the plinth
    prism("chart table top", -1.6, 1.6,
          [(0.74, 5.95), (0.84, 5.6), (0.95, 5.6), (0.95, 7.2), (0.84, 7.2), (0.74, 6.85)], M["table"], 0.02)
    box("chart top inset", -1.45, 1.45, 0.935, 0.955, 5.72, 7.08, M["dark"], 0.004)
    box("chart top glow", -1.4, 1.4, 0.945, 0.958, 5.77, 7.03, M["glow"], 0.0)
    # control strip along the near rim
    prism("chart control strip", -1.0, 1.0, [(0.95, 5.62), (0.95, 5.74), (1.0, 5.74), (0.98, 5.62)],
          M["metal"], 0.006)
    for i in range(8):
        xk = -0.85 + i * 0.24
        box(f"chart key {i}", xk - 0.07, xk + 0.07, 0.99, 1.005, 5.65, 5.71,
            M["lamp"] if i % 3 == 0 else M["rib"], 0.003)
    # holo emitters at the corners
    for xs in (-1.5, 1.5):
        for zs in (5.7, 7.1):
            cylinder(f"holo emitter {xs:+.1f} {zs:.1f}", xs, zs, 0.95, 1.02, 0.045, M["metal"], 16, 0.006)
            cylinder(f"holo emitter lens {xs:+.1f} {zs:.1f}", xs, zs, 1.02, 1.03, 0.03, M["lamp"], 16)


def crt(name, xc, z_front, y0, w=0.55, h=0.42, depth=0.36):
    frustum(f"{name} housing", (xc - w / 2, xc + w / 2, y0 + 0.02, y0 + h + 0.02, z_front),
            (xc - w * 0.32, xc + w * 0.32, y0 + 0.07, y0 + h * 0.72, z_front + depth), M["metal"], 0.025)
    box(f"{name} bezel", xc - w / 2 + 0.04, xc + w / 2 - 0.04, y0 + 0.06, y0 + h - 0.02,
        z_front - 0.012, z_front, M["dark"], 0.01)
    box(f"{name} screen", xc - w / 2 + 0.07, xc + w / 2 - 0.07, y0 + 0.09, y0 + h - 0.05,
        z_front - 0.02, z_front - 0.012, M["glow"], 0.012)
    box(f"{name} foot", xc - w * 0.25, xc + w * 0.25, y0, y0 + 0.03, z_front + 0.05, z_front + 0.3, M["dark"], 0.01)


def swivel_chair(name, xc, zc, facing=1):
    """A desk chair whose back faces the camera (facing=1 means the seat looks at +z)."""
    for k in range(5):
        a = k * 2 * math.pi / 5 + 0.3
        leg = box(f"{name} leg {k}", xc - 0.02, xc + 0.02, 0.05, 0.09, zc, zc + 0.32, M["chair"], 0.008)
        spin(leg, (xc, 0.0, zc), 2, a)
        cylinder(f"{name} caster {k}", xc - 0.32 * math.sin(a), zc + 0.32 * math.cos(a), 0.0, 0.05, 0.03,
                 M["dark"], 12)
    cylinder(f"{name} column", xc, zc, 0.07, 0.44, 0.035, M["metal"], 16)
    box(f"{name} seat pan", xc - 0.2, xc + 0.2, 0.42, 0.46, zc - 0.2, zc + 0.2, M["metal"], 0.01)
    box(f"{name} cushion", xc - 0.25, xc + 0.25, 0.46, 0.54, zc - 0.25, zc + 0.24, M["chair"], 0.035)
    zb = zc - 0.27 * facing
    prism(f"{name} back", xc - 0.23, xc + 0.23,
          [(0.6, zb), (0.6, zb + 0.07 * facing), (1.08, zb + 0.02 * facing), (1.08, zb - 0.06 * facing)][::facing],
          M["chair"], 0.03)
    box(f"{name} back strut", xc - 0.03, xc + 0.03, 0.5, 0.66, zb - 0.02, zb + 0.04, M["metal"], 0.008)
    for s in (-1, 1):
        box(f"{name} arm {s}", xc + s * 0.27 - 0.03, xc + s * 0.27 + 0.03, 0.52, 0.72, zc - 0.05, zc - 0.01,
            M["metal"], 0.008)
        box(f"{name} armrest {s}", xc + s * 0.27 - 0.04, xc + s * 0.27 + 0.04, 0.72, 0.75, zc - 0.2, zc + 0.12,
            M["chair"], 0.015)


def comms_bank():
    x0, x1 = -6.8, -2.9
    # desk body: pedestals each end and in the middle, knee recesses for the two seats
    for i, (a, bb) in enumerate([(x0, -6.2), (-5.05, -4.5), (-3.4, x1)]):
        box(f"comms pedestal {i}", a, bb, 0.0, 0.72, 10.25, 11.0, M["metal"], 0.015)
        for k in range(3):
            box(f"comms pedestal {i} drawer {k}", a + 0.04, bb - 0.04, 0.1 + k * 0.2, 0.26 + k * 0.2,
                10.23, 10.25, M["panel"], 0.006)
    box("comms knee back", x0, x1, 0.1, 0.72, 10.85, 11.0, M["dark"], 0.0)
    box("comms top", x0 - 0.05, x1 + 0.05, 0.72, 0.76, 10.15, 11.0, M["metal"], 0.012)
    # sloped keyboard decks
    for i, xc in enumerate((-5.62, -3.92)):
        prism(f"comms keyboard deck {i}", xc - 0.55, xc + 0.55,
              [(0.76, 10.18), (0.76, 10.42), (0.84, 10.42), (0.79, 10.18)], M["panel"], 0.008)
        box(f"comms keyboard {i}", xc - 0.3, xc + 0.3, 0.79, 0.82, 10.24, 10.38, M["dark"], 0.006)
    for i, xc in enumerate((-6.225, -5.475, -4.325, -3.575)):
        crt(f"comms crt {i}", xc, 10.42, 0.8)
    # rack behind, with an overhanging canopy, two flat repeaters and status lights
    box("comms rack", x0, x1, 0.76, 2.3, 10.75, 11.0, M["metal"], 0.015)
    prism("comms canopy", x0 - 0.05, x1 + 0.05,
          [(2.3, 11.0), (2.3, 10.55), (2.42, 10.5), (2.48, 11.0)], M["metal"], 0.015)
    box("comms canopy light", x0 + 0.1, x1 - 0.1, 2.28, 2.3, 10.58, 10.7, M["lamp"], 0.0)
    for i, (a, bb) in enumerate([(-6.25, -5.45), (-4.35, -3.55)]):
        box(f"comms repeater {i} frame", a, bb, 1.48, 1.98, 10.69, 10.75, M["dark"], 0.012)
        box(f"comms repeater {i} screen", a + 0.04, bb - 0.04, 1.52, 1.94, 10.68, 10.69, M["glow"], 0.0)
    for r in range(3):
        for k in range(10):
            xk = -5.25 + k * 0.08
            box(f"comms status {r} {k}", xk - 0.025, xk + 0.025, 1.55 + r * 0.12, 1.6 + r * 0.12,
                10.735, 10.75, M["lamp"] if (r + k) % 3 else M["rib"], 0.0)
    for k in range(6):
        box(f"comms vent {k}", -5.3, -4.5, 1.95 + k * 0.05, 1.97 + k * 0.05, 10.735, 10.75, M["dark"], 0.0)
    for i, xc in enumerate((-5.62, -3.92)):
        swivel_chair(f"comms chair {i}", xc, 9.72, facing=1)


def ledger_station():
    x0, x1 = 3.0, 5.4
    for i, (a, bb) in enumerate([(x0, 3.55), (4.85, x1)]):
        box(f"ledger pedestal {i}", a, bb, 0.0, 0.72, 10.25, 11.0, M["crate"], 0.015)
        for k in range(3):
            box(f"ledger drawer {i} {k}", a + 0.04, bb - 0.04, 0.06 + k * 0.22, 0.24 + k * 0.22,
                10.23, 10.25, M["panel"], 0.008)
            box(f"ledger handle {i} {k}", (a + bb) / 2 - 0.08, (a + bb) / 2 + 0.08, 0.2 + k * 0.22,
                0.22 + k * 0.22, 10.2, 10.23, M["rib"], 0.004)
    box("ledger modesty", 3.55, 4.85, 0.25, 0.72, 10.85, 10.9, M["crate"], 0.01)
    box("ledger top", x0 - 0.05, x1 + 0.05, 0.72, 0.77, 10.15, 11.0, M["crate"], 0.015)
    crt("ledger crt", 3.81, 10.45, 0.77, w=0.46, h=0.4, depth=0.34)
    box("ledger keyboard", 3.6, 4.05, 0.77, 0.8, 10.25, 10.4, M["dark"], 0.006)
    for k in range(5):
        box(f"ledger paper {k}", 4.4, 4.9, 0.77 + k * 0.018, 0.785 + k * 0.018, 10.3, 10.68, M["paper"], 0.002,
            rot=0.05 * ((-1) ** k) * (k + 1) / 3)
    box("ledger binder", 4.95, 5.25, 0.77, 1.05, 10.65, 10.9, M["panel"], 0.01)
    # desk lamp
    cylinder("ledger lamp base", 5.15, 10.4, 0.77, 0.8, 0.07, M["metal"], 16, 0.005)
    cylinder("ledger lamp arm", 5.15, 10.4, 0.8, 1.18, 0.012, M["metal"], 8)
    frustum("ledger lamp shade", (5.0, 5.2, 1.12, 1.2, 10.36), (5.08, 5.18, 1.15, 1.2, 10.44), M["metal"], 0.006)
    # supply crates
    box("supply crate", 2.2, 2.9, 0.0, 0.6, 10.0, 10.7, M["crate"], 0.02)
    for i, (a, bb) in enumerate([(2.2, 2.26), (2.84, 2.9)]):
        box(f"supply crate band {i}", a - 0.01, bb + 0.01, 0.0, 0.6, 9.99, 10.71, M["rib"], 0.008)
    box("supply crate lid rim", 2.19, 2.91, 0.56, 0.62, 9.99, 10.71, M["rib"], 0.01)
    box("supply case", 2.3, 2.8, 0.62, 0.88, 10.15, 10.6, M["panel"], 0.02, rot=0.12)
    # roster board on the back wall
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
    for pid, (x, z, h) in PEOPLE.items():
        head = 0.12 * h / 1.75
        cylinder(f"stand-in {pid} body", x, z, 0.0, h - 2 * head - 0.03, 0.17 * h / 1.75, M["person"], 20,
                 collection=col)
        bpy.ops.mesh.primitive_uv_sphere_add(radius=head, location=V(x, h - head, z), segments=20, ring_count=10)
        s = bpy.context.object
        s.name = f"stand-in {pid} head"
        s.data.materials.append(M["person"])
        for c in s.users_collection:
            c.objects.unlink(s)
        col.objects.link(s)
    return col


# ---------------------------------------------------------------- camera and render

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
    sc.render.film_transparent = False
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
    sc.world = bpy.data.worlds.new("deck") if sc.world is None else sc.world


def project(x, y, z):
    return [round(CAM["cx"] + CAM["focal"] * x / z, 1), round(CAM["cy"] - CAM["focal"] * (y - CAM["height"]) / z, 1)]


def check_camera(cam):
    sc = bpy.context.scene
    bpy.context.view_layer.update()
    for p in [(0.0, CAM["height"], 10.0), (1.6, 0.95, 5.6), (-1.6, 0.95, 7.2)]:
        v = world_to_camera_view(sc, cam, Vector(V(*p)))
        got = (v.x * W, (1 - v.y) * H)
        want = project(*p)
        assert abs(got[0] - want[0]) < 0.6 and abs(got[1] - want[1]) < 0.6, (p, got, want)


def layout():
    def bbox(prefix):
        pts = [ob.matrix_world @ Vector(c) for ob in bpy.data.objects
               if ob.name.startswith(prefix) and ob.type == "MESH" for c in ob.bound_box]
        xs, ys, zs = [p.x for p in pts], [p.z for p in pts], [p.y for p in pts]
        return [round(min(xs), 3), round(max(xs), 3), round(min(ys), 3), round(max(ys), 3),
                round(min(zs), 3), round(max(zs), 3)]

    stations = {
        "chart-table": "chart table", "chart-glow": "chart top glow", "comms-bank": "comms",
        "comms-rack": "comms rack", "ledger": "ledger", "roster-board": "roster", "hatch": "hatch",
        "viewport": "viewport", "supply-crate": "supply",
    }
    props = {k: bbox(v) for k, v in stations.items()}
    g = props["chart-glow"]
    markers = {
        "chart-top-corners": [project(g[0], g[3], g[4]), project(g[1], g[3], g[4]),
                              project(g[1], g[3], g[5]), project(g[0], g[3], g[5])],
        "chart-top-centre": project(0, g[3], (g[4] + g[5]) / 2),
        "up-at-chart-centre": project(0, g[3] + 1.0, (g[4] + g[5]) / 2),
        "comms-screens": {f"crt-{i}": project(xc, 1.01, 10.40)
                          for i, xc in enumerate((-6.225, -5.475, -4.325, -3.575))},
        "comms-repeaters": {"left": project(-5.85, 1.73, 10.68), "right": project(-3.95, 1.73, 10.68)},
        "ledger-screen": project(3.81, 0.98, 10.43),
        "roster-board": project(4.2, 1.9, ROOM["back"] - 0.08),
        "viewport-centre": project(0, (VIEWPORT[2] + VIEWPORT[3]) / 2, ROOM["back"]),
        "hatch-centre": project(6.3, 1.05, ROOM["back"] - 0.06),
    }
    people = {k: {"x": x, "z": z, "heightM": h, "feetPx": project(x, 0, z), "headPx": project(x, h, z)}
              for k, (x, z, h) in PEOPLE.items()}
    doc = {"size": [W, H], "units": "metres; x right, y up, z ahead of the camera",
           "camera": CAM, "projection": "u = cx + focal*x/z, v = cy - focal*(y - height)/z",
           "room": ROOM, "viewport": VIEWPORT, "props": props, "markers": markers, "people": people}
    (OUT / "deck-guide-v4.layout.json").write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
    for k, v in people.items():
        print(k, "height px", round(v["feetPx"][1] - v["headPx"][1]))


def main():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    materials()
    shell()
    viewport()
    hatch()
    chart_table()
    comms_bank()
    ledger_station()
    people = stand_ins()
    cam = camera()
    render_settings()
    check_camera(cam)
    sc = bpy.context.scene
    for hidden, name in ((True, "deck-guide-v4.png"), (False, "deck-guide-v4-people.png")):
        people.hide_render = hidden
        sc.render.filepath = str(OUT / name)
        bpy.ops.render.render(write_still=True)
    layout()


main()
