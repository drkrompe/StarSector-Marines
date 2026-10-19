"""Write groups/<group>.py: the Legion's part tables, cut into box tiles.

  blender --background build/geom/legion-geometry.blend --python make_groups.py

It first cuts the geometry build along the tile lines and saves that as the
surfacing build (surface_hull.BLEND, build/geom/legion-surface.blend); every
later step opens that one.

The Legion is 292 m long and built from many plate meshes, so a part is a
box tile of the hull seen square-on from one side (parts.py box members),
sized so a tile holds about 6-9 texels per hull unit; a face longer than its
box (by more than PAD) stays on its class swatch. Port tiles are painted;
each starboard tile reuses its port twin mirrored (`skin_from`, `mirror_u`),
and the centreline faces go to the port tile (claimed first). The members of
a tile are the objects a grid of rays from its view direction hits inside its
box on a classed base material, so the tables follow the geometry when it is
rebuilt: rerun this, then render_parts.py.
"""
import sys
from pathlib import Path
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import surface_hull as SH  # noqa: E402

BIG = 1e4
# (group, view, part id, (x0, x1), (y0, y1), (z0, z1), what). Port side only;
# 'top'/'belly' tiles cut X and Y, 'side' tiles X and Z, 'ends' tiles Y and Z.
TILES = [
    ('top', 'top', 'top-bow', (112, 158), (0, 80), None, 'bow deck, chevron plates and port bow blade from above'),
    ('top', 'top', 'top-fore', (67, 112), (0, 80), None, 'forward torso deck and dorsal spine from above'),
    ('top', 'top', 'top-dome', (22, 67), (0, 80), None, 'torso deck beside the command dome, from above'),
    ('top', 'top', 'top-waist', (-23, 22), (0, 80), None, 'torso deck and inner belt at the waist, from above'),
    ('top', 'top', 'top-aft', (-68, -23), (0, 80), None, 'aft torso deck, machinery bed and tail spine, from above'),
    ('top', 'top', 'top-bed', (-104, -68), (0, 80), None, 'aft machinery bed and tail spine, from above'),
    ('top', 'top', 'top-stern', (-140, -104), (0, 80), None, 'engine block and tail spine end, from above'),
    ('top', 'top', 'wing-1', (64, 105), (80, 148), None, 'port wing arm tip and sponson, from above'),
    ('top', 'top', 'wing-2', (24, 64), (80, 148), None, 'port wing arm, forward middle, from above'),
    ('top', 'top', 'wing-3', (-16, 24), (80, 148), None, 'port wing arm, middle, from above'),
    ('top', 'top', 'wing-4', (-56, -16), (80, 148), None, 'port wing arm, aft middle, from above'),
    ('top', 'top', 'wing-5', (-97, -56), (80, 148), None, 'port wing arm root aft, from above'),
    ('side', 'side', 'side-fore-hi', (62, 158), None, (-22, 32), 'port flank forward, upper: wing tip, bow and belt, seen from port'),
    ('side', 'side', 'side-mid-hi', (-34, 62), None, (-22, 32), 'port flank middle, upper: wing armour and hangar, seen from port'),
    ('side', 'side', 'side-aft-hi', (-130, -34), None, (-22, 32), 'port flank aft, upper: wing root and engines, seen from port'),
    ('side', 'side', 'side-fore-lo', (62, 158), None, (-60, -22), 'port flank forward, lower: hangar and keel, seen from port'),
    ('side', 'side', 'side-mid-lo', (-34, 62), None, (-60, -22), 'port flank middle, lower: hangar and keel, seen from port'),
    ('side', 'side', 'side-aft-lo', (-130, -34), None, (-60, -22), 'port flank aft, lower: keel and radiators, seen from port'),
    ('side', 'bow', 'bow-hi', None, (0, 80), (-10, 32), 'port half of the bow, upper, from directly ahead'),
    ('side', 'bow', 'bow-lo', None, (0, 80), (-60, -10), 'port half of the bow, lower, from directly ahead'),
    ('side', 'stern', 'stern-in-hi', None, (0, 74), (-14, 32), 'stern, inboard upper: engine block and tail, from astern'),
    ('side', 'stern', 'stern-in-lo', None, (0, 74), (-60, -14), 'stern, inboard lower: keel and drive bells, from astern'),
    ('side', 'stern', 'stern-out-hi', None, (74, 148), (-14, 32), 'stern, outboard upper: port wing root, from astern'),
    ('side', 'stern', 'stern-out-lo', None, (74, 148), (-60, -14), 'stern, outboard lower: port wing underside, from astern'),
    ('belly', 'belly', 'belly-bow', (110, 158), (0, 74), None, 'port belly at the bow, from below'),
    ('belly', 'belly', 'belly-fore-in', (65, 110), (0, 74), None, 'port belly, fore, inboard, from below'),
    ('belly', 'belly', 'belly-fore-out', (65, 110), (74, 148), None, 'port belly, fore, outboard, from below'),
    ('belly', 'belly', 'belly-mid-fore-in', (20, 65), (0, 74), None, 'port belly, mid-fore, inboard, from below'),
    ('belly', 'belly', 'belly-mid-fore-out', (20, 65), (74, 148), None, 'port belly, mid-fore, outboard, from below'),
    ('belly', 'belly', 'belly-mid-in', (-25, 20), (0, 74), None, 'port belly, mid, inboard, from below'),
    ('belly', 'belly', 'belly-mid-out', (-25, 20), (74, 148), None, 'port belly, mid, outboard, from below'),
    ('belly', 'belly', 'belly-mid-aft-in', (-70, -25), (0, 74), None, 'port belly, mid-aft, inboard, from below'),
    ('belly', 'belly', 'belly-mid-aft-out', (-70, -25), (74, 148), None, 'port belly, mid-aft, outboard, from below'),
    ('belly', 'belly', 'belly-stern-in', (-140, -70), (0, 74), None, 'port belly, stern, inboard, from below'),
    ('belly', 'belly', 'belly-stern-out', (-140, -70), (74, 148), None, 'port belly, stern, outboard, from below'),
]
SHEETS = {
    'top': {'top-a': ['top-bow', 'top-fore', 'top-dome', 'top-waist'],
            'top-b': ['top-aft', 'top-bed', 'top-stern', 'wing-1'],
            'top-c': ['wing-2', 'wing-3', 'wing-4', 'wing-5']},
    'side': {'side-a': ['side-fore-hi', 'side-mid-hi', 'side-aft-hi', 'bow-hi'],
             'side-b': ['side-fore-lo', 'side-mid-lo', 'side-aft-lo', 'bow-lo'],
             'side-c': ['stern-in-hi', 'stern-in-lo', 'stern-out-hi', 'stern-out-lo']},
    'belly': {'belly-a': ['belly-bow', 'belly-fore-in', 'belly-fore-out'],
              'belly-b': ['belly-mid-fore-in', 'belly-mid-fore-out', 'belly-mid-in', 'belly-mid-out'],
              'belly-c': ['belly-mid-aft-in', 'belly-mid-aft-out', 'belly-stern-in', 'belly-stern-out']},
}
# view: (dir name, up name, ray direction into the ship, which two world axes the grid walks)
VIEWS = {'top': ('UP_Z', 'DOWN_X', (0, 0, -1)), 'belly': ('DOWN_Z', 'DOWN_X', (0, 0, 1)),
         'side': ('PORT', 'UP_Z', (0, -1, 0)), 'bow': ('BOW', 'UP_Z', (-1, 0, 0)),
         'stern': ('STERN', 'UP_Z', (1, 0, 0))}
MIRROR_DIR = {'PORT': 'STARBOARD'}
STEP = 1.5
PAD = 10   # parts.py box 'pad': a face must also lie within the box grown by this

CUT_BLEND = HERE / SH.BLEND   # the surfacing build: the geometry build cut along the tile lines


def tile_planes():
    """World planes (axis, value) along every tile edge, mirrored to starboard."""
    out = set()
    for _g, _v, _p, xs, ys, zs, _w in TILES:
        for k, rng in enumerate((xs, ys, zs)):
            for v in rng or ():
                out.add((k, float(v)))
                if k == 1:
                    out.add((1, -float(v)))
    return sorted(out)


def cut_at_tile_lines():
    """Split every classed mesh along the tile lines, so a long plate becomes
    pieces that each fall inside one tile and get painted, instead of keeping
    its swatch. The cuts lie in flat faces and the bevels are angle-limited,
    so the shape and its shading do not change."""
    import bmesh
    planes = tile_planes()
    cut = 0
    for ob in bpy.data.objects:
        if ob.type != 'MESH' or ob.data.users > 1:
            continue
        if not any(m and m.name.split('.')[0] in SH.MATERIAL_CLASS for m in ob.data.materials):
            continue
        mw = ob.matrix_world
        corners = [mw @ Vector(c) for c in ob.bound_box]
        lo = [min(c[k] for c in corners) for k in range(3)]
        hi = [max(c[k] for c in corners) for k in range(3)]
        hits = [(k, v) for k, v in planes if lo[k] + 0.01 < v < hi[k] - 0.01]
        if not hits:
            continue
        bm = bmesh.new()
        bm.from_mesh(ob.data)
        inv, n3 = mw.inverted(), mw.to_3x3().transposed()
        before = len(bm.faces)
        for k, v in hits:
            co = [0.0, 0.0, 0.0]
            co[k] = v
            no = [0.0, 0.0, 0.0]
            no[k] = 1.0
            geom = bm.verts[:] + bm.edges[:] + bm.faces[:]
            bmesh.ops.bisect_plane(bm, geom=geom, plane_co=inv @ Vector(co), plane_no=(n3 @ Vector(no)).normalized(), dist=1e-4)
        bm.to_mesh(ob.data)
        bm.free()
        cut += len(ob.data.polygons) - before
    return cut


print('SURF cut at tile lines:', cut_at_tile_lines(), 'faces added')
CUT_BLEND.parent.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.save_as_mainfile(filepath=str(CUT_BLEND), copy=True)
dg = bpy.context.evaluated_depsgraph_get()
scene = bpy.context.scene


def members(view, xs, ys, zs):
    """Objects whose classed faces a ray grid from the view hits inside the box."""
    d = Vector(VIEWS[view][2])
    lo = [(xs or (-BIG, BIG))[0], (ys or (-BIG, BIG))[0], (zs or (-BIG, BIG))[0]]
    hi = [(xs or (-BIG, BIG))[1], (ys or (-BIG, BIG))[1], (zs or (-BIG, BIG))[1]]
    axis = [i for i in range(3) if d[i] == 0]
    found = {}
    a = lo[axis[0]] + STEP / 2
    while a < hi[axis[0]]:
        b = lo[axis[1]] + STEP / 2
        while b < hi[axis[1]]:
            o = [0.0, 0.0, 0.0]
            o[axis[0]], o[axis[1]] = a, b
            k = [i for i in range(3) if i not in axis][0]
            o[k] = -d[k] * 400
            hit, loc, nrm, idx, ob, _ = scene.ray_cast(dg, Vector(o), d)
            # Only faces inside the part's cone (parts.py selects no others).
            if hit and ob.type == 'MESH' and abs(nrm.dot(d)) >= 0.62:
                ev = ob.evaluated_get(dg)
                slot = ev.material_slots[ev.data.polygons[idx].material_index] if ev.material_slots else None
                if slot and slot.material and slot.material.name in SH.MATERIAL_CLASS:
                    found[ob.name] = found.get(ob.name, 0) + 1
            b += STEP
        a += STEP
    # Ignore objects that only graze the tile (a few rays): they keep their swatch.
    return sorted(n for n, c in found.items() if c >= 6), (lo, hi)


def fmt_box(lo, hi):
    f = lambda v: 'BIG' if v >= BIG else '-BIG' if v <= -BIG else f'{v:g}'
    return f"{{'box': (({f(lo[0])}, {f(lo[1])}, {f(lo[2])}), ({f(hi[0])}, {f(hi[1])}, {f(hi[2])})), 'pad': {PAD}}}"


def mirror_name(n):
    return n.replace('port', '\0').replace('starboard', 'port').replace('\0', 'starboard').replace(
        'Port', '\0').replace('Starboard', 'Port').replace('\0', 'Starboard')


names = set(o.name for o in bpy.data.objects)
for group in SHEETS:
    lines = [f'"""{group}: box tiles of the Legion seen square-on (generated by ../make_groups.py; rerun it, do not edit)."""',
             'from parts_common import PORT, STARBOARD, UP_Z, DOWN_Z, BOW, STERN, DOWN_X',
             '', f'BIG = {BIG:g}', '', 'PARTS = {']
    for g, view, pid, xs, ys, zs, what in TILES:
        if g != group:
            continue
        objs, (lo, hi) = members(view, xs, ys, zs)
        dirn, up, _ = VIEWS[view]
        lines.append(f"    '{pid}': dict(members=[{', '.join(f'({n!r}, {fmt_box(lo, hi)})' for n in objs)}],")
        lines.append(f"                 dir={dirn}, up={up}, cone=.6, what={what!r}),")
        # Starboard twin: the same box mirrored in Y, its objects renamed, the port tile mirrored.
        if view in ('top', 'belly', 'side', 'bow', 'stern'):
            mlo, mhi = [lo[0], -hi[1], lo[2]], [hi[0], -lo[1], hi[2]]
            if view == 'side':
                mlo[1], mhi[1] = -BIG, BIG
            mobjs = [mirror_name(n) for n in objs]
            mobjs = [n for n in mobjs if n in names]
            mdir = MIRROR_DIR.get(dirn, dirn)
            lines.append(f"    '{pid}-stbd': dict(members=[{', '.join(f'({n!r}, {fmt_box(mlo, mhi)})' for n in mobjs)}],")
            lines.append(f"                 dir={mdir}, up={up}, cone=.6, skin_from='{pid}', mirror_u=True,")
            lines.append(f"                 what={(what + ' (starboard mirror)')!r}),")
        print('SURF', group, pid, len(objs), 'objects')
    lines += ['}', '', 'SHEETS = {'] + [f"    '{s}': {ps!r}," for s, ps in SHEETS[group].items()] + ['}', '']
    (HERE / 'groups' / f'{group}.py').write_text('\n'.join(lines), encoding='utf-8')
