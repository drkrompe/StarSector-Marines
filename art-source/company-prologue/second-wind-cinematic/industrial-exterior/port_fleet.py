"""The public port's ships for C4 and C5: which of our finished hulls stand where, and what flies.

Called from `c4x_scene.add_port_traffic`. Every hull is one of the cinematic models as published
(`ship-model-status.md`), appended once and placed as collection instances, at its own real scale.
Nothing here edits a model; a second operator's livery is a hue shift laid over its materials.

The berths keep their jobs (`c4x_scene.BERTHS`: what the people, forklifts and carriers around each
one are doing, read by port_life, port_people and port_detail); this module only says which hull
does the job:

  berth 0 (-400)  hound      Hound (A), Hegemony auxiliary livery (port_life copies it for the
                             Hound that lands on berth 2 in C5)
  berth 1 (-310)  unloading  Shepherd, Ko Combine livery
  berth 3 (-130)  lifting    Mudskipper in a teal-trimmed operator's livery (port_life keys its climb
                             for a hull that rests at z 11, which the Mudskipper shares with the Hound)
  berth 4 (-40)   fuelling   Shepherd in a second operator's slate-blue
  berth 5 (140)   ferry      Mudskipper, Wayline's own livery
  heavy berth                Colossus, unloading into the staged containers
  lighter pads               Hermes, textured (the untextured geometry if the export is missing)

A freighter pad is 68 m across; the Hound (61 m), Mudskipper (67 m) and Shepherd (67 m) fit it nose
to tail. The Colossus is 108 x 89 m and fits only the heavy berth's 130 x 100 m walled deck.

In the air, both well below the camera and never across its path:
  'Colossus outbound'  a second Colossus lifting out low over the warehouses in C5, lift thrusters
                       lit: the top view's traffic.
  'Shepherd inbound'   a Shepherd letting down northward over the belt in C4's first seconds.

Inputs: build/c1v2/inputs/{hound-skinned,mudskipper-skinned}.blend as before; shepherd.blend,
colossus.blend and hermes.blend from each hull's `*-cinematic/` folder (built there by its
finalize.sh) or copied into build/c1v2/inputs. A missing hull is reported (PORT_FLEET missing) and
its place falls back to a Hound, so the scene still builds.
"""
import math
from pathlib import Path

import bmesh
import bpy
from mathutils import Matrix, Vector

PROLOGUE = Path(__file__).resolve().parents[2]

# name: (blend file, collection, metres per unit, rest z on a 3 m freighter pad, +X is the bow)
HULLS = {
    'hound': ('hound-skinned.blend', 'Collection', 0.88, 11.0, False),
    'mudskipper': ('mudskipper-skinned.blend', 'Collection', 0.85, 11.0, False),
    'shepherd': ('shepherd.blend', 'SHEPHERD', 0.78, 3.0 + 16.76 * 0.78, True),
    'colossus': ('colossus.blend', 'COLOSSUS', 0.50, 52.9 * 0.50, True),
    'hermes': ('hermes.blend', 'HERMES', 0.11, 2.5, True),
}
# (hue, saturation, value) over every base colour: another operator's paint on the same hull. Hue
# 0.5 leaves a colour alone; 0 or 1 turns it half round the wheel.
LIVERIES = {
    'teal': (0.03, 0.9, 0.85),        # the Mudskipper's red trim turns teal, its white a little greyer
    'slate': (1.0, 0.5, 0.95),        # the Shepherd's tan and orange turn a dull slate blue
}
BERTH_HULLS = {'hound': ('hound', None), 'unloading': ('shepherd', None), 'lifting': ('mudskipper', 'teal'),
               'fuelling': ('shepherd', 'slate'), 'ferry': ('mudskipper', None)}
C5_SECONDS = 33.5                     # how far past C4's end anything moving is keyed
HAZE_SKIP = {'Fleet lift glow'}


def find(c4, filename):
    stem = filename.split('.')[0].split('-')[0]
    for p in (c4.INPUTS / filename, PROLOGUE / ('%s-cinematic' % stem) / filename):
        if p.exists():
            return p
    return None


class Fleet:
    """Appends each hull (and each livery of it) once; `place` makes an instance of it."""

    def __init__(self, c4):
        self.c4 = c4
        self.colls = {}

    def coll(self, hull, livery=None):
        key = (hull, livery)
        if key in self.colls:
            return self.colls[key]
        fname, cname, mpu, rest, bow = HULLS[hull]
        path = find(self.c4, fname)
        if path is None:
            print('PORT_FLEET missing', hull, fname, flush=True)
            if hull == 'hermes':
                path, cname, rest = PROLOGUE / 'hermes-geometry' / 'hermes-geometry.blend', 'Collection', 2.5
            else:
                return self.coll('hound', livery)
        lights = {o.name for o in bpy.data.objects if o.type == 'LIGHT'}
        mats = set(bpy.data.materials)
        coll = self.c4.append_collection(path, cname)
        bpy.context.view_layer.layer_collection.children[coll.name].exclude = True
        # The hull files' own lamps blow out from 700 m in daylight.
        for o in [o for o in bpy.data.objects if o.type == 'LIGHT' and o.name not in lights]:
            bpy.data.objects.remove(o)
        if livery:
            tint(set(bpy.data.materials) - mats, *LIVERIES[livery])
        self.colls[key] = (coll, mpu, rest, bow)
        return self.colls[key]

    def place(self, label, hull, livery=None):
        coll, mpu, rest, bow = self.coll(hull, livery)
        e = self.c4.link(bpy.data.objects.new(label, None))
        e.instance_type = 'COLLECTION'
        e.instance_collection = coll
        e.scale = (mpu,) * 3
        e['port_fleet_hull'] = hull
        return e, rest, bow


def tint(mats, hue, sat, val):
    for m in mats:
        if not m.use_nodes:
            continue
        nt = m.node_tree
        for n in [n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED']:
            inp = n.inputs['Base Color']
            hs = nt.nodes.new('ShaderNodeHueSaturation')
            hs.inputs['Hue'].default_value = hue
            hs.inputs['Saturation'].default_value = sat
            hs.inputs['Value'].default_value = val
            if inp.links:
                nt.links.new(inp.links[0].from_socket, hs.inputs['Color'])
            else:
                hs.inputs['Color'].default_value = inp.default_value
            nt.links.new(hs.outputs['Color'], inp)


def add(c4):
    fleet = Fleet(c4)
    F, D = c4.FRAMES, c4.DURATION
    for i, (x, off, who) in enumerate(c4.BERTHS):
        if who == 'empty':
            continue
        hull, livery = BERTH_HULLS[who]
        e, rest, bow = fleet.place('%s at berth %d' % (who.title(), i), hull, livery)
        e.location = (x, c4.BERTH_Y + c4.BERTH_DY[x], rest)
        if who == 'lifting':
            for f, z in ((0, rest), (F - 1, rest + 4.0 * D)):
                e.location.z = z
                e.keyframe_insert('location', frame=f)
        # Bow up the lane, stern (where the cargo comes out) back down it toward the container yard.
        e.rotation_euler = (0, 0, c4.ROW_HEADING + math.radians(off) + (0.0 if bow else c4.STERN_FIRST))
    for i, (x, who) in enumerate(c4.LIGHTERS):
        if who == 'empty':
            continue
        e, rest, bow = fleet.place('Lighter %d %s' % (i, who), 'hermes')
        e.rotation_euler = (0, 0, math.pi / 2 + math.radians((i * 37) % 9 - 4))
        z0, z1 = {'parked': (rest, rest), 'lifting': (rest, rest + 9.0 * D),
                  'landing': (rest + 12.0 * D, rest)}[who]
        for f, z in ((0, z0), (F - 1, z1)):
            e.location = (x, c4.LIGHTER_Y, z)
            e.keyframe_insert('location', frame=f)
    heavy(c4, fleet)
    air(c4, fleet)


def heavy(c4, fleet):
    """The Colossus in the heavy berth: square in the walled bay, bow west, its hold under the
    gantry, the staged containers off its stern quarter."""
    hx0, hx1, hy0, hy1 = c4.HEAVY
    e, rest, bow = fleet.place('Colossus at the heavy berth', 'colossus')
    # Its bounds run -126..91 units along X and -87..90 across (centre -17.5, 1.5); turned bow west,
    # that centre lies (+8.8, -0.75) m from the root, so the root sits that far back of the bay's middle.
    cx, cy = (hx0 + hx1) / 2, (hy0 + hy1) / 2
    e.rotation_euler = (0, 0, math.pi)
    e.location = (cx - 17.535 * 0.5, cy + 1.5 * 0.5, 0.2 + rest)


def lift_glows(coll):
    """The Colossus's ten ventral lift thrusters, in hull units (its export's anchors)."""
    out = []
    for o in coll.all_objects:
        n = o.name.split('.')[0]
        if n.startswith('Engine ') and n.endswith(' exhaust anchor'):
            try:
                k = int(n.split()[1])
            except ValueError:
                continue
            if k >= 7:
                out.append(rest_matrix(o).translation.copy())
    return out


def rest_matrix(o):
    """Where `o` sits in its file's own frame. The hull's collection is excluded from the view
    layer and never evaluated, so matrix_world is stale; compose the parent chain instead."""
    m = o.matrix_parent_inverse @ o.matrix_basis
    return (rest_matrix(o.parent) @ m) if o.parent is not None else m


def glow_material():
    m = bpy.data.materials.get('Fleet lift glow')
    if m is None:
        m = bpy.data.materials.new('Fleet lift glow')
        m.use_nodes = True
        p = m.node_tree.nodes['Principled BSDF']
        p.inputs['Base Color'].default_value = (1.0, 0.6, 0.3, 1)
        p.inputs['Emission Color'].default_value = (1.0, 0.6, 0.3, 1)
        p.inputs['Emission Strength'].default_value = 30.0
    return m


def fly(e, keys, F):
    """Key `e` through (C4+C5 second, x, y, z, heading) at a steady speed between keys."""
    for t, x, y, z, hd in keys:
        f = round(t * 24)
        e.location = (x, y, z)
        e.rotation_euler = (0, 0, hd)
        e.keyframe_insert('location', frame=f)
        e.keyframe_insert('rotation_euler', frame=f)
    import port_life
    port_life.linear(e)


def air(c4, fleet):
    T = c4.DURATION
    END = T + C5_SECONDS
    F = round(END * c4.FPS)
    # A second Colossus lifting out low over the warehouses in C5, 260 m climbing to 420 m, east-north-
    # east: it comes into the top view from the west edge as the rise ends, well clear of the berth
    # Mudskipper climbing straight up off berth 3. It is hidden, shadow and all, until its first key.
    e, rest, bow = fleet.place('Colossus outbound', 'colossus')
    hd = math.atan2(90.0, 700.0)
    fly(e, [(T + 22.0, -800.0, 180.0, 260.0, hd), (END, -100.0, 270.0, 420.0, hd)], F)
    coll = e.instance_collection
    glows = lift_glows(coll)
    for i, p in enumerate(glows):
        bm = bmesh.new()
        bmesh.ops.create_uvsphere(bm, u_segments=10, v_segments=6, radius=1.0)
        g = c4.mesh('Colossus outbound lift glow %d' % i, bm, glow_material())
        g.parent = e
        g.matrix_parent_inverse = Matrix.Identity(4)
        g.location = p + Vector((0, 0, -4.0))
        g.scale = (5.0, 5.0, 7.0)
    for ob in [e] + list(e.children):
        for f, hidden in ((0, True), (round((T + 22.0) * c4.FPS), False)):
            ob.hide_render = ob.hide_viewport = hidden
            ob.keyframe_insert('hide_render', frame=f)
            ob.keyframe_insert('hide_viewport', frame=f)
    # A Shepherd letting down northward over the belt in C4's first seconds, from 380 m to 300 m, left
    # of the camera's track: under it for the opening seconds of the pan, a hull plainly in the air at
    # well under half the camera's height. It goes on north past the port and out of every later frame.
    e, rest, bow = fleet.place('Shepherd inbound', 'shepherd', 'slate')
    hd = math.atan2(1000.0, 80.0)
    fly(e, [(0.0, -200.0, -450.0, 380.0, hd), (8.0, -120.0, 550.0, 300.0, hd), (T, -60.0, 1350.0, 260.0, hd)], F)
    print('PORT_FLEET hulls', sorted('%s%s' % (h, '/' + l if l else '') for h, l in fleet.colls), flush=True)
