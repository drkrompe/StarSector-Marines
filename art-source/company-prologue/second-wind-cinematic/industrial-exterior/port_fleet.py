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
  'Colossus outbound'  a second Colossus leaving through C5 (DEPART_KEYS): in low along the south
                       side of Kilo Nine as the rise begins, then climbing away north-west, small
                       and still burning under the title. Its plumes hang off its thruster markers
                       (`after_markers`, called after `thrust_shots.mark_c4`).
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
from mathutils import Matrix

PROLOGUE = Path(__file__).resolve().parents[2]

# name: (blend file, collection, metres per unit, rest z on a 3 m freighter pad, +X is the bow).
# Every hull here is +X bow (each geometry.json says so), so a parked hull is turned to its lane's
# heading and its stern, where the cargo comes out, points back down the lane.
HULLS = {
    'hound': ('hound-skinned.blend', 'Collection', 0.88, 11.0, True),
    'mudskipper': ('mudskipper-skinned.blend', 'Collection', 0.85, 11.0, True),
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
HAZE_SKIP = {'Fleet plume core', 'Fleet plume glow'}
# The departing Colossus (C4+C5 seconds after C4's end): shown from DEPART_SHOW, in the frame as the
# rise starts, climbing away north-west under the title; drives burning the whole time it is shown.
DEPART_SHOW = 10.0
DEPART_KEYS = ((10.0, 700.0, 160.0, 60.0), (22.0, 170.0, 370.0, 160.0), (C5_SECONDS, -280.0, 430.0, 480.0))
DEPART_MAIN, DEPART_LIFT = 0.8, 1.0


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
        e.rotation_euler = (0, 0, c4.ROW_HEADING + math.radians(off) + (0.0 if bow else math.pi))
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
    # A second Colossus leaving: it comes in low along the south side of Kilo Nine, big in the frame as
    # the rise begins, and climbs away north-west, small and still burning under the title. It is
    # airborne before it is shown, so there is no lift dust; until DEPART_SHOW it is hidden (shadow too).
    e, rest, bow = fleet.place('Colossus outbound', 'colossus')
    (t0, x0, y0, _), (t2, x2, y2, _) = DEPART_KEYS[0], DEPART_KEYS[-1]
    hd = math.atan2(y2 - y0, x2 - x0)
    for t, x, y, z in DEPART_KEYS:
        f = round((T + t) * c4.FPS)
        e.location = (x, y, z)
        e.rotation_euler = (0, 0, hd)
        e.keyframe_insert('location', frame=f)
        e.keyframe_insert('rotation_euler', frame=f)
    for f, hidden in ((0, True), (round((T + DEPART_SHOW) * c4.FPS), False)):
        e.hide_render = hidden
        e.keyframe_insert('hide_render', frame=f)
    e.hide_render = True
    # A Shepherd letting down northward over the belt in C4's first seconds, from 420 m to 320 m, left
    # of the camera's track: under it for the opening seconds of the pan, a hull plainly in the air at
    # well under half the camera's height, keeping pace with the pan. It goes on north past the port and
    # out of every later frame.
    e, rest, bow = fleet.place('Shepherd inbound', 'shepherd', 'slate')
    hd = math.atan2(850.0, 60.0)
    # It starts 300 m from where Second Wind opens (c4x_scene.P0) and the two paths only diverge.
    fly(e, [(0.0, -320.0, -230.0, 420.0, hd), (c4.PAN, -260.0, 620.0, 320.0, hd),
            (T, -220.0, 620.0 + 850.0 * (T - c4.PAN) / c4.PAN, 280.0, hd)], F)
    print('PORT_FLEET hulls', sorted('%s%s' % (h, '/' + l if l else '') for h, l in fleet.colls), flush=True)


def after_markers(c4):
    """After `thrust_shots.mark_c4`: the departing Colossus's throttle, and its plumes hung off its
    nozzle markers (`thrust-markers/README.md`). Its mains point aft, so they are what reads from
    above; the lift jets under the hull are lit too, for whatever a lower view or the sound wants."""
    import thrust_markers as tm
    craft = bpy.data.objects.get('Colossus outbound')
    if craft is None:
        return
    T = c4.DURATION
    marks = [o for o in bpy.data.objects if o.parent == craft and o.name.startswith(tm.PREFIX)]
    on = T + DEPART_SHOW
    tm.key(marks, [(0.0, 0.0), (on - 0.1, 0.0), (on, DEPART_MAIN)], c4.FPS, kinds=('main',))
    tm.key(marks, [(0.0, 0.0), (on - 0.1, 0.0), (on, DEPART_LIFT)], c4.FPS, kinds=('lift',))
    core, glow = plume_materials()
    s = craft.scale.x
    n = 0
    for m in marks:
        if m.get('thrust_kind') != 'main':
            continue
        r = float(m.get('radius_m', 1.5))
        # Exaggerated past a bell's true flame so a burn reads from 1.8 km: a hot core and a wider glow.
        for mat, length, width in ((core, 14.0 * r, 0.9 * r), (glow, 26.0 * r, 1.8 * r)):
            p = cone(mat.name + ' | ' + m.name.split(' | ', 1)[1], mat, c4)
            p.parent = m
            p.matrix_parent_inverse = Matrix.Identity(4)
            p.hide_render = True
            p.keyframe_insert('hide_render', frame=0)
            p.hide_render = False
            p.keyframe_insert('hide_render', frame=round(on * c4.FPS))
            p.scale = (length / s, width / s, width / s)
        n += 1
    print('PORT_FLEET departure plumes=%d shown from %.2f s (C5 %.2f s)' % (n, on, DEPART_SHOW), flush=True)


def cone(name, mat, c4):
    """A plume along local +X from the mouth: unit length, unit radius at the mouth."""
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=12, radius1=1.0, radius2=0.05, depth=1.0)
    bmesh.ops.rotate(bm, verts=bm.verts, cent=(0, 0, 0), matrix=Matrix.Rotation(math.pi / 2, 3, 'Y'))
    bmesh.ops.translate(bm, verts=bm.verts, vec=(0.5, 0, 0))
    ob = c4.mesh(name, bm, mat)
    ob.visible_shadow = False
    return ob


def plume_materials():
    out = []
    for name, rgb, strength, alpha in (('Fleet plume core', (1.0, 0.85, 0.6), 60.0, 1.0),
                                       ('Fleet plume glow', (1.0, 0.5, 0.15), 18.0, 0.45)):
        m = bpy.data.materials.get(name)
        if m is None:
            m = bpy.data.materials.new(name)
            m.use_nodes = True
            m.surface_render_method = 'BLENDED'
            p = m.node_tree.nodes['Principled BSDF']
            p.inputs['Base Color'].default_value = rgb + (1,)
            p.inputs['Emission Color'].default_value = rgb + (1,)
            p.inputs['Emission Strength'].default_value = strength
            p.inputs['Alpha'].default_value = alpha
        out.append(m)
    return out
