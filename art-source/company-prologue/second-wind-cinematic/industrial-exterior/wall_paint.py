"""Paint the C4/C5 port's walls, and put plant on its roofs.

`ortho_paint.py` paints what is seen from straight above; this paints what the wide lens sees at
the frame's edges: the sides. Each building's walls are given one of a few tileable wall materials
(`walls/<name>.png`, one ImageGen generation each, briefs and provenance beside them), mapped in
metres along each wall so a door bay is a door's width wherever it lands:

    cladding   corrugated steel, eave flashing at the top and splash at the foot, stretched to the
               wall's height (the ribs run vertically, so only the streaks stretch); tinted per
               building, since a warehouse's walls are seldom its roof's colour
    door-bay   a 12 m bay of the same cladding with a roller door and a personnel door, laid along
               a loading side from the ground; above 12 m the plain cladding carries on
    office     two storeys of concrete and ribbon glazing (the arrivals hall)
    concrete   precast panels (blast walls, pads, the gatehouse, the hall's covered walks)

Containers and supply modules take the cladding at their own size, tinted their own colour.
Roofs keep their materials, and so their overhead paint: the walls get their own materials, by
face, and nothing moves. Roof plant (air-conditioning units, turbine vents, ridge vents, hatches
and ladders) is added inside each roof's edge, so every footprint the overhead paint was made
against stays where it was.

    apply()  from `c4x_scene.build()`, after `ortho_paint.apply`; skipped when ORTHO_PAINT_OFF is set,
             so overhead guides are drawn from the same blockout their paint was.
"""
import math
import os
import random
from pathlib import Path

import bmesh
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
WALLS = HERE / 'walls'
DOOR_H = 12.0                 # the door-bay picture is 12 m wide and 12 m tall

# material: picture, bay width in metres (walls hold whole bays), whether it is tinted per face
KINDS = {
    'cladding': ('cladding', 8.0, True),
    'door-bay': ('door-bay', 12.0, True),
    'office': ('office', 12.0, False),
    'concrete': ('concrete', 6.0, False),
    'container': ('cladding', 6.0, True),
    'roller': ('door-bay', 12.0, False),     # a door slab: just the roller door out of the bay picture
}
ROLLER_UV = ((0.25, 0.75), (0.01, 0.47))  # where the roller door sits in `door-bay.png`
WALL_TINTS = [(0.66, 0.66, 0.64), (0.70, 0.66, 0.56), (0.48, 0.54, 0.58), (0.56, 0.30, 0.18),
              (0.30, 0.40, 0.32), (0.76, 0.76, 0.74), (0.42, 0.42, 0.44)]


def wall_material(kind):
    name = 'Wall ' + kind
    m = bpy.data.materials.get(name)
    if m is not None:
        return m
    pic, _, tinted = KINDS[kind]
    img = bpy.data.images.load(str(WALLS / (pic + '.png')), check_existing=True)
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes['Principled BSDF']
    bsdf.inputs['Roughness'].default_value = 0.75
    bsdf.inputs['Metallic'].default_value = 0.25 if pic in ('cladding', 'door-bay') else 0.0
    uv = nt.nodes.new('ShaderNodeUVMap')
    uv.uv_map = 'wall'
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = img
    tex.interpolation = 'Linear'
    nt.links.new(uv.outputs[0], tex.inputs['Vector'])
    colour = tex.outputs['Color']
    if tinted:                                # the paint is neutral grey: tint it, keep its rust
        tint = nt.nodes.new('ShaderNodeVertexColor')
        tint.layer_name = 'wall_tint'
        mul = nt.nodes.new('ShaderNodeMix')
        mul.data_type = 'RGBA'
        mul.blend_type = 'MULTIPLY'
        mul.inputs['Factor'].default_value = 1.0
        nt.links.new(colour, mul.inputs['A'])
        nt.links.new(tint.outputs['Color'], mul.inputs['B'])
        gain = nt.nodes.new('ShaderNodeMix')    # the grey paint is about 0.55: lift it back to the tint
        gain.data_type = 'RGBA'
        gain.blend_type = 'MULTIPLY'
        gain.inputs['Factor'].default_value = 1.0
        gain.inputs['B'].default_value = (1.7, 1.7, 1.7, 1.0)
        nt.links.new(mul.outputs['Result'], gain.inputs['A'])
        keep = nt.nodes.new('ShaderNodeMix')    # a little of the picture's own colour: rust stays rust
        keep.data_type = 'RGBA'
        keep.inputs['Factor'].default_value = 0.2
        nt.links.new(gain.outputs['Result'], keep.inputs['A'])
        nt.links.new(colour, keep.inputs['B'])
        colour = keep.outputs['Result']
    nt.links.new(colour, bsdf.inputs['Base Color'])
    bump = nt.nodes.new('ShaderNodeBump')       # ribs and joints catch the low sun
    bump.inputs['Strength'].default_value = 0.35
    bump.inputs['Distance'].default_value = 0.05
    nt.links.new(tex.outputs['Color'], bump.inputs['Height'])
    nt.links.new(bump.outputs['Normal'], bsdf.inputs['Normal'])
    return m


def slot(ob, mat):
    me = ob.data
    for i, m in enumerate(me.materials):
        if m == mat:
            return i
    me.materials.append(mat)
    return len(me.materials) - 1


def islands(bm):
    """Each box of a merged mesh, as its faces."""
    seen, out = set(), []
    for f in bm.faces:
        if f.index in seen:
            continue
        stack, group = [f], []
        seen.add(f.index)
        while stack:
            g = stack.pop()
            group.append(g)
            for e in g.edges:
                for h in e.link_faces:
                    if h.index not in seen:
                        seen.add(h.index)
                        stack.append(h)
        out.append(group)
    return out


def paint_object(ob, rule, rng):
    """Give `ob`'s walls wall materials. `rule(centre, size, normal, face_height)` names the kind of
    a wall face (or None to leave it), for a box of the given centre and size; a kind of
    'door-bay' is laid from the ground to DOOR_H and the cladding above it."""
    me = ob.data
    bm = bmesh.new()
    bm.from_mesh(me)
    bm.faces.ensure_lookup_table()
    bm.faces.index_update()
    groups = islands(bm)
    plans = []
    door_split = False
    for group in groups:
        vs = {v for f in group for v in f.verts}
        lo = Vector((min(v.co.x for v in vs), min(v.co.y for v in vs), min(v.co.z for v in vs)))
        hi = Vector((max(v.co.x for v in vs), max(v.co.y for v in vs), max(v.co.z for v in vs)))
        tint = rule.tint(ob, lo, hi, rng)
        for f in group:
            n = f.normal
            if abs(n.z) > 0.5:
                continue
            kind = rule.kind(ob, lo, hi, n)
            if kind == 'door-bay' and hi.z > DOOR_H + 0.5:
                door_split = True
            plans.append((f, kind, lo, hi, tint))
    if door_split:                                   # cut the tall loading walls where the bays end
        faces = {p[0] for p in plans if p[1] == 'door-bay'}
        geom = list({e for f in faces for e in f.edges}) + list(faces)
        bmesh.ops.bisect_plane(bm, geom=geom, plane_co=(0, 0, DOOR_H), plane_no=(0, 0, 1))
        bm.faces.ensure_lookup_table()
        boxes = [(p[2], p[3], p[4], p[1]) for p in plans]
        plans = []
        for f in bm.faces:
            n = f.normal
            if abs(n.z) > 0.5:
                continue
            c = f.calc_center_median()
            for lo, hi, tint, _ in boxes:
                if all(lo[i] - 0.01 <= c[i] <= hi[i] + 0.01 for i in range(3)):
                    break
            kind = rule.kind(ob, lo, hi, n)
            if kind == 'door-bay' and c.z > DOOR_H:
                kind = 'cladding'
            plans.append((f, kind, lo, hi, tint))
    uv = bm.loops.layers.uv.get('wall') or bm.loops.layers.uv.new('wall')
    col = bm.loops.layers.color.get('wall_tint') or bm.loops.layers.color.new('wall_tint')
    for lp in (l for f in bm.faces for l in f.loops):
        lp[col] = (1, 1, 1, 1)
    slots = {}
    for f, kind, lo, hi, tint in plans:
        if kind is None:
            continue
        if kind not in slots:
            slots[kind] = slot(ob, wall_material(kind))
        f.material_index = slots[kind]
        n = f.normal
        t = Vector((-n.y, n.x, 0)).normalized()
        corners = [Vector((x, y, 0)) for x in (lo.x, hi.x) for y in (lo.y, hi.y)]
        a0 = min(c.dot(t) for c in corners)
        length = max(c.dot(t) for c in corners) - a0
        bay = KINDS[kind][1]
        bay = length / max(1, round(length / bay))     # whole bays along the wall
        height = max(hi.z - lo.z, 0.5)
        for lp in f.loops:
            p = lp.vert.co
            u = (p.dot(t) - a0) / bay
            if kind == 'roller':
                (u0, u1), (v0, v1) = ROLLER_UV
                u = u0 + (u1 - u0) * (p.dot(t) - a0) / max(length, 0.1)
                v = v0 + (v1 - v0) * (p.z - lo.z) / height
            elif kind == 'door-bay':
                v = (p.z - lo.z) / DOOR_H
            elif kind == 'office':
                v = (p.z - lo.z) / 10.0
            elif kind == 'concrete':
                v = (p.z - lo.z) / bay
            else:                                      # eave at the top, splash at the foot
                v = (p.z - lo.z) / height
            lp[uv].uv = (u, v)
            lp[col] = (*tint, 1.0)
    bm.to_mesh(me)
    bm.free()


class Rule:
    def __init__(self, kind, tint=None):
        self._kind, self._tint = kind, tint

    def kind(self, ob, lo, hi, n):
        return self._kind(ob, lo, hi, n) if callable(self._kind) else self._kind

    def tint(self, ob, lo, hi, rng):
        if callable(self._tint):
            return self._tint(ob, lo, hi, rng)
        return self._tint or (1.0, 1.0, 1.0)


def base_rgb(ob):
    m = ob.data.materials[0]
    bsdf = next(n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
    return tuple(bsdf.inputs['Base Color'].default_value[:3])


def warehouse_kind(ob, lo, hi, n):
    """Loading doors on the side a row is worked from: the freight road's side for the first row,
    the container yard's for the second; plain cladding on the ends."""
    if abs(n.y) < 0.9 or hi.x - lo.x < 20:
        return 'cladding'
    first_row = (lo.y + hi.y) / 2 < 100
    return 'door-bay' if (n.y < 0) == first_row else 'cladding'


def warehouse_tint(ob, lo, hi, rng):
    if rng.random() < 0.35:
        r, g, b = base_rgb(ob)
        return (min(1, r * 1.3), min(1, g * 1.3), min(1, b * 1.3))
    return rng.choice(WALL_TINTS)


def rules():
    clad_pale = Rule('cladding', (0.70, 0.70, 0.68))
    return [
        (lambda o: o.name.startswith('Warehouses '), Rule(warehouse_kind, warehouse_tint)),
        (lambda o: o.name == 'Terminal shed', Rule('cladding', (0.62, 0.64, 0.66))),
        (lambda o: o.name == 'Fabrication hall', Rule('cladding', (0.52, 0.30, 0.20))),
        (lambda o: o.name == 'Nessa hangar',
         Rule(lambda o, lo, hi, n: 'door-bay' if n.x > 0.9 else 'cladding', (0.58, 0.36, 0.24))),
        (lambda o: o.name == 'Nessa workshop',
         Rule(lambda o, lo, hi, n: 'door-bay' if n.y > 0.9 and hi.x - lo.x > 20 else 'cladding', clad_pale._tint)),
        (lambda o: o.name == 'Arrivals hall',
         Rule(lambda o, lo, hi, n: 'office' if hi.z - lo.z > 6 else 'concrete')),
        (lambda o: o.name in ('Terminal doors', 'Fabrication hall doors'), Rule('roller')),
        (lambda o: o.name in ('Gatehouse', 'Heavy berth walls', 'Plant', 'Pads'), Rule('concrete')),
        (lambda o: o.name.startswith('Containers ') or (o.name.startswith('Module ') and o.name.endswith(' stacks')),
         Rule('container', lambda o, lo, hi, rng: base_rgb(o))),
    ]


# ---------------------------------------------------------------- roof plant

ROOFS = {   # name: (centre x, y, width, depth, roof height, what is already up there to keep clear of)
    'Arrivals hall': (240.0, 415.0, 40.0, 34.0, 10.0, [(240.0, 415.0, 13.0)]),
    'Terminal shed': (220.0, 670.0, 80.0, 50.0, 12.0, []),
    'Nessa hangar': (330.0, 540.0, 34.0, 42.0, 18.0, []),
    'Nessa workshop': (334.0, 400.0, 28.0, 18.0, 7.0, []),
    'Fabrication hall': (352.0, 625.0, 72.0, 34.0, 14.0, [(352.0, 625.0, 0.0)]),
}


def roof_plant(warehouses):
    """AC units with their fans, turbine vents, a ridge vent, a hatch and a ladder down one end."""
    r = random.Random(77)
    mats = {
        'unit': mat('Roof AC units', (0.50, 0.51, 0.50), 0.6, 0.2),
        'fan': mat('Roof AC fans', (0.06, 0.06, 0.07), 0.6, 0.4),
        'vent': mat('Roof vents', (0.36, 0.37, 0.38), 0.45, 0.7),
        'duct': mat('Roof ducts', (0.30, 0.31, 0.32), 0.6, 0.5),
        'ladder': mat('Roof ladders', (0.80, 0.62, 0.12), 0.5, 0.4),
    }
    bms = {k: bmesh.new() for k in mats}
    buildings = [(x, y, w, d, h, [], True) for x, y, w, d, h in warehouses]
    buildings += [(x, y, w, d, h, keep, False) for x, y, w, d, h, keep in ROOFS.values()]
    for x, y, w, d, h, keep, ridge in buildings:
        def free(px, py, rad):
            if abs(px - x) > w / 2 - rad - 1.0 or abs(py - y) > d / 2 - rad - 1.0:
                return False
            if any(math.hypot(px - kx, py - ky) < kr + rad for kx, ky, kr in keep):
                return False
            if any(kr == 0.0 for _, _, kr in keep) and abs(px - x) < 33 and any(
                    abs(py - ly) < 1.3 + rad for ly in (613, 621, 629, 637)):
                return False                                 # the fabrication hall's roof lights
            return True
        placed = []

        def put(rad):
            for _ in range(20):
                px, py = x + r.uniform(-w / 2, w / 2), y + r.uniform(-d / 2, d / 2)
                if free(px, py, rad) and all(math.hypot(px - qx, py - qy) > rad + qr + 0.8 for qx, qy, qr in placed):
                    placed.append((px, py, rad))
                    return px, py
            return None
        area = w * d
        for _ in range(max(1, int(area / 900)) + r.randrange(0, 3)):      # AC units, one or two fans each
            spot = put(2.6)
            if spot is None:
                continue
            px, py = spot
            along = r.random() < 0.5
            uw, ud = (4.2, 2.0) if along else (2.0, 4.2)
            box(bms['unit'], (uw, ud, 1.4), (px, py, h + 0.7))
            for k in (-1, 1):
                fx, fy = (px + k * 1.0, py) if along else (px, py + k * 1.0)
                disc(bms['fan'], 0.75, 0.06, (fx, fy, h + 1.4))
        for _ in range(max(2, int(area / 500))):                         # turbine vents
            spot = put(0.6)
            if spot is None:
                continue
            disc(bms['vent'], 0.45, 0.9, (*spot, h), segs=10, top=0.55)
        if r.random() < 0.8:                                             # a duct run between two units
            spot = put(1.0)
            if spot is not None:
                px, py = spot
                run = min(w / 2 - 2, 10.0)
                box(bms['duct'], (run, 0.9, 0.9), (px, py, h + 0.45))
        spot = put(0.8)                                                  # roof hatch
        if spot is not None:
            box(bms['duct'], (1.2, 1.2, 0.5), (*spot, h + 0.25))
        if ridge and w > 24 and r.random() < 0.6:                        # ridge vent along the long axis
            box(bms['vent'], (w - 10, 1.4, 0.7), (x, y, h + 0.35))
        # ladder up the east end: two rails and rungs, standing just off the wall
        lx, ly = x + w / 2 + 0.25, y + r.uniform(-d / 4, d / 4)
        if not (ROOFS.get('Nessa workshop')[:2] == (x, y)):             # keep the families' way clear
            for k in (-0.25, 0.25):
                box(bms['ladder'], (0.08, 0.08, h + 1.1), (lx, ly + k, (h + 1.1) / 2))
            for z in range(1, int(h)):
                box(bms['ladder'], (0.06, 0.5, 0.06), (lx, ly, float(z)))
    for k, bm in bms.items():
        mesh(mats[k].name, bm, mats[k])


def mat(name, rgb, rough, metal):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    p = m.node_tree.nodes['Principled BSDF']
    p.inputs['Base Color'].default_value = (*rgb, 1)
    p.inputs['Roughness'].default_value = rough
    p.inputs['Metallic'].default_value = metal
    return m


def box(bm, size, centre):
    verts = bmesh.ops.create_cube(bm, size=1.0)['verts']
    for v in verts:
        v.co = Vector((v.co.x * size[0], v.co.y * size[1], v.co.z * size[2])) + Vector(centre)


def disc(bm, radius, height, base, segs=12, top=None):
    res = bmesh.ops.create_cone(bm, cap_ends=True, segments=segs, radius1=radius,
                                radius2=radius if top is None else top, depth=height)
    bmesh.ops.translate(bm, vec=Vector(base) + Vector((0, 0, height / 2)), verts=res['verts'])


def mesh(name, bm, m):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    me.materials.append(m)
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def apply(warehouses=()):
    if os.environ.get('ORTHO_PAINT_OFF'):
        return 0
    rng = random.Random(41)
    done = 0
    for ob in list(bpy.context.scene.objects):
        if ob.type != 'MESH' or not ob.data.materials:
            continue
        for match, rule in rules():
            if match(ob):
                paint_object(ob, rule, rng)
                done += 1
                break
    roof_plant(warehouses)
    return done
