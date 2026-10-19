"""Pass 2: the detail the sprite implies, sized for S10's look over the shoulder.

S10 sees the Falcon from about 500 m behind, above and to port, so the dorsal
decks, the port flank and the stern carry most of it; S4-S6 at 3-5 km read
only the larger forms. Read off falcon_p.png (the pirate refit) and
falcon_base.png (the shapes under the paint), at 8x:

- Prong plating (`Hull <side> prong deck plating`, `... glacis plating`,
  `... belt plating`): armour plates laid on the loft's own facets with dark
  seam gaps between them (the gap shows the hull beneath; nothing is painted
  on). The pirate livery is carried by the plates: each deck and glacis
  plate takes the paint the sprite shows under it (khaki, pirate red or
  sand, the majority of falcon_p.png's pixels under the plate; LIVERY), so
  the red slashes become repainted plates and the texture pass keeps each
  plate its own colour. Plates are cut from the facets exactly: the loft is linear between its
  stations, so a plate's corners are interpolated straight from the section
  points. The turrets stand on bare aprons.
- Pirate patch plates (`Hull <side> prong patch plates`): heavier slabs bolted
  over the aft decks where the sprite shows the red and orange refit panels.
- Ridge cap (`Hull <side> prong ridge cap`): the raised armour strip along the
  turret line.
- Inner-wall viewports (`Hull <side> prong viewports`): two decks of lit
  1.0 x 0.7 m ports on each prong's inner wall, facing the gap between the
  prongs, where crew decks would look out.
- Prong fittings: the round sensor housings the sprite shows at (80, +-25),
  vent grilles, deck hatches.
- Bridge (`Bridge dome bands`, `Bridge crown mast`): plating bands round the
  dome, a crown hatch and a sensor mast with a dish.
- Dorsal spine machinery (`Hull spine machinery ...`): the sprite's dark
  ribbed block aft of the dome: a housing, transverse radiator fins and pipe
  runs either side.
- Waist (`Hull <side> waist tank bands`, `... saddles`, `... pipework`): the
  bare tanks' banding (the sprite's ladder marks) on saddles, and the pirates'
  pipe runs from the tanks to the drive blocks.
- Drive blocks (`Hull <side> drive block ...`): the patchwork the skin's
  description promises: the block cut back so the drive cans stand proud in
  their hazard bands, a trough over the inboard engine exposing its bare core
  in a cradle, radiator fins and bolted patch plates on top, cable bundles.
- Outrigger pods and bow forks: pod plating and struts; antenna probes at the
  fork tips (the sprite's thin rods past the hardpoints).
- Stern: an aft airlock on the central body between the drive blocks.

Person-scale fittings are true metres through kit.mpu.
"""
import math
import sys
from pathlib import Path
import bpy
import bmesh
from mathutils import Matrix, Vector

import fc_hull
from fc_kit import boolean

sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'turret-kit'))
import turret_kit as tk

SPRITE = 'graphics/ships/falcon/falcon_p.png'


class Livery:
    """falcon_p.png's paint, classified per pixel: 0 khaki, 1 red, 2 sand.
    Thresholds from the prong pixels' hue, saturation and value."""

    def __init__(self):
        sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
        from starsector_path import GAME_CORE
        im = bpy.data.images.load(str(GAME_CORE/SPRITE), check_existing=True)
        self.w, self.h = im.size
        px = list(im.pixels)
        self.cls = {}
        for r in range(self.h):        # Blender rows run bottom-up
            for c in range(self.w):
                i = ((self.h-1-r)*self.w+c)*4
                rr, gg, bb, aa = px[i:i+4]
                if aa < .5:
                    continue
                import colorsys
                hh, ss, vv = colorsys.rgb_to_hsv(rr, gg, bb)
                hh *= 360
                self.cls[(c, r)] = 1 if (ss > .45 and (hh < 18 or hh > 340)) else                     2 if (ss > .3 and 18 <= hh < 45 and vv > .6) else 0
        bpy.data.images.remove(im)

    def at(self, x, y):
        return self.cls.get((int(math.floor(81-y)), int(math.floor(134-x))))

    def plate(self, corners):
        """Majority class under a plate (a 7 x 7 sample across its corners)."""
        votes = [0, 0, 0]
        a, b, c, d = corners
        for i in range(7):
            for j in range(7):
                u, v = (i+.5)/7, (j+.5)/7
                p = a.lerp(b, u).lerp(d.lerp(c, u), v)
                k = self.at(p.x, p.y)
                if k is not None:
                    votes[k] += 1
        return max(range(3), key=lambda k: votes[k]) if sum(votes) else 0


LIVERY = None
TURRETS = [(14, 38, 9.5), (55, 29, 5.5), (14, -38, 9.5), (55, -29, 5.5)]
GAP = .45   # plate seam, units


# --- the prong's own facets ---------------------------------------------------

def prong_pts(x, side):
    """The loft's section points at x (linear between its stations), port order."""
    st = fc_hull.PRONG  # descending x
    for (xa, *a), (xb, *b) in zip(st, st[1:]):
        if xb <= x <= xa:
            t = (x-xb)/(xa-xb)
            pa = fc_hull.prong_section(xa, *a, 1)
            pb = fc_hull.prong_section(xb, *b, 1)
            pts = [(yb+(ya-yb)*t, zb+(za-zb)*t) for (ya, za), (yb, zb) in zip(pa, pb)]
            return [(y*side, z) for y, z in pts]
    raise ValueError(x)


def facet_point(x, side, k, s):
    pts = prong_pts(x, side)
    (y0, z0), (y1, z1) = pts[k], pts[(k+1) % len(pts)]
    return Vector((x, y0+(y1-y0)*s, z0+(z1-z0)*s))


def facet_width(x, side, k):
    return (facet_point(x, side, k, 1)-facet_point(x, side, k, 0)).length


def centroid(x, side):
    pts = prong_pts(x, side)
    return Vector((x, sum(p[0] for p in pts)/len(pts), sum(p[1] for p in pts)/len(pts)))


def slab(bm, corners, normal, t, sink=.25, mat=0):
    """A plate on four coplanar-ish corners: top `t` proud along normal, sunk below."""
    lo = [bm.verts.new(c-normal*sink) for c in corners]
    hi = [bm.verts.new(c+normal*t) for c in corners]
    n = len(corners)
    faces = [bm.faces.new((lo[i], lo[(i+1) % n], hi[(i+1) % n], hi[i])) for i in range(n)]
    faces += [bm.faces.new(lo[::-1]), bm.faces.new(hi)]
    for f in faces:
        f.material_index = mat


def facet_plate(bm, side, k, x0, x1, s0, s1, t=.35, livery=False):
    c = [facet_point(x0, side, k, s0), facet_point(x1, side, k, s0),
         facet_point(x1, side, k, s1), facet_point(x0, side, k, s1)]
    n = (c[1]-c[0]).cross(c[3]-c[0]).normalized()
    mid = sum(c, Vector())/4
    mat = LIVERY.plate(c) if livery else 0
    if n.dot(mid-centroid((x0+x1)/2, side)) < 0:
        n = -n
        c = c[::-1]
    slab(bm, c, n, t, mat=mat)
    return mid, n


def near_turret(x, y, pad=2.5):
    return any(math.hypot(x-tx, y-ty) < r+pad for tx, ty, r in TURRETS)


def emit(bm, name, mat, kit):
    """One object; `mat` is a material, or a list indexed by the faces' material_index."""
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(ob)
    mats = mat if isinstance(mat, list) else [mat]
    kit.finish(ob, name, mats[0])
    for m in mats[1:]:
        ob.data.materials.append(m)
    return ob


def mesh_obj(m, name, mat, kit):
    ob = m.emit(name, mat, None)
    if ob is None:
        return None
    ob.name = ob.data.name = name
    return ob


# --- prongs ----------------------------------------------------------------

DECK_X = [104-5.5*i for i in range(25)]
GLACIS_X = [104-6.7*i for i in range(21)]
BELT_X = [104, 94, 84, 74, 64, 54, 44, 34, 24, 14, 4, -6, -16, -26, -35]


def prong_plating(kit, side, label):
    deck, glacis, belt = bmesh.new(), bmesh.new(), bmesh.new()
    for xa, xb in zip(DECK_X, DECK_X[1:]):
        x0, x1 = xb+GAP/2, xa-GAP/2
        w = facet_width((x0+x1)/2, side, 2)
        g = GAP/max(w, 1)
        for s0, s1 in ((.03, 1/3-g/2), (1/3+g/2, 2/3-g/2), (2/3+g/2, .97)):
            mid = facet_point((x0+x1)/2, side, 2, (s0+s1)/2)
            if not near_turret(mid.x, mid.y):
                facet_plate(deck, side, 2, x0, x1, s0, s1, livery=True)
    for xa, xb in zip(GLACIS_X, GLACIS_X[1:]):
        x0, x1 = xb+GAP/2, xa-GAP/2
        mid = facet_point((x0+x1)/2, side, 3, .5)
        if not near_turret(mid.x, mid.y, 1.0):
            g = GAP/max(facet_width((x0+x1)/2, side, 3), 1)
            for s0, s1 in ((.04, .5-g/2), (.5+g/2, .96)):
                facet_plate(glacis, side, 3, x0, x1, s0, s1, livery=True)
    for xa, xb in zip(BELT_X, BELT_X[1:]):
        facet_plate(belt, side, 4, xb+GAP/2, xa-GAP/2, .06, .94, t=.5)
    paint = [kit.armour, kit.red, kit.sand]
    emit(deck, f'Hull {label} prong deck plating', paint, kit)
    emit(glacis, f'Hull {label} prong glacis plating', paint, kit)
    emit(belt, f'Hull {label} prong belt plating', kit.plate, kit)

    # Ridge cap: a raised strip along the turret line, broken at the turrets.
    cap = bmesh.new()
    for xa, xb in ((100, 62.5), (47.5, 26), (2, -26)):
        a, b = facet_point(xa, side, 3, 0), facet_point(xb, side, 3, 0)
        d = (a-b).normalized()
        up = Vector((0, 0, 1))
        lat = d.cross(up).normalized()
        c = [b-lat*.9, a-lat*.9, a+lat*.9, b+lat*.9]
        slab(cap, c, up, .7)
    emit(cap, f'Hull {label} prong ridge cap', kit.plate, kit)

    # Pirate patch plates: bolted slabs over the aft deck and glacis.
    patch = bmesh.new()
    bolts = tk.Mesh()
    for k, x0, x1, s0, s1 in ((2, -24, -11, .15, .85), (3, -26, -8, .1, .7), (2, 23, 34, .1, .55),
                              (3, 66, 80, .2, .8)):
        mid, n = facet_plate(patch, side, k, x0, x1, s0, s1, t=.75)
        for xx in (x0+.8, x1-.8):
            for ss in (s0+.06, s1-.06):
                p = facet_point(xx, side, k, ss)+n*.8
                bolts.cyl(tuple(p), .22, .3, 'Z', n=8)
    emit(patch, f'Hull {label} prong patch plates', kit.red, kit)
    mesh_obj(bolts, f'Hull {label} prong patch bolts', kit.steel, kit)

    # Viewports: two decks of lit ports on the inner wall (facet 0), facing the gap.
    ports = tk.Mesh()
    w, h = 1.0/kit.mpu, .7/kit.mpu
    for x in [22+3.4*i for i in range(22)]:
        for s in ((.38, .7) if x < 60 else (.62,)):
            p = facet_point(x, side, 0, s)
            ports.box((p.x, p.y-.06*side, p.z), (w, .2, h))
    mesh_obj(ports, f'Hull {label} prong viewports', kit.glass, kit)

    # Sensor housing where the sprite shows a ring at (80, +-25): a low drum and dish.
    fit = tk.Mesh()
    hatch = tk.Mesh()
    for x, y, r in ((80, 25, 3.0),):
        hit = kit.surface(x, y*side, top=80)
        z = hit[0].z
        fit.lathe([(r+.6, -.6), (r+.6, .5), (r, .9), (r*.75, 1.3), (r*.3, 1.45), (0, 1.45)], 20,
                  c=(x, y*side, z))
    # Vent grilles: louvred boxes on the deck where the sprite shows dark slots.
    for x, y in ((92, 22), (66, 33), (38, 44), (-4, 50)):
        hit = kit.surface(x, y*side, top=80)
        if not hit:
            continue
        z = hit[0].z
        fit.box((x, y*side, z+.35), (4.2, 2.6, 1.0), bevel=.08)
        for i in range(5):
            hatch.box((x-1.6+.8*i, y*side, z+.9), (.35, 2.2, .25))
    # Deck access hatches, true size (1.2 m), beside the ridge.
    hs = 1.2/kit.mpu
    for x in (98, 72, 44, 30, 6, -14):
        p = facet_point(x, side, 2, .78)
        hatch.box((p.x, p.y, p.z+.3), (hs, hs, .25), bevel=.05)
    mesh_obj(fit, f'Hull {label} prong fittings', kit.plate, kit)
    mesh_obj(hatch, f'Hull {label} prong hatches', kit.steel, kit)


# --- bridge, spine, waist --------------------------------------------------

def bridge(kit):
    m = tk.Mesh()
    for z, r in ((10.8, 12.65), (13.8, 10.85)):
        m.ring((4, 0, z), r-.3, r+.25, .5, 'Z', n=20)
    m.lathe([(2.2, 17.4), (2.2, 18.1), (1.6, 18.4), (0, 18.4)], 16, c=(-1.5, 0, 0))   # crown hatch
    mesh_obj(m, 'Bridge dome bands', kit.steel, kit)
    mast = tk.Mesh()
    mast.cyl((2.5, 0, 21.0), .35, 6.0, 'Z', n=10)
    mast.box((2.5, 0, 22.8), (.4, 5.5, .35))
    mast.lathe([(.0, 0), (1.6, .25), (1.8, .55), (0, .35)], 16, c=(2.5, 0, 24.0))
    mesh_obj(mast, 'Bridge crown mast', kit.steel, kit)


def spine(kit):
    house = tk.Mesh()
    house.box((-27, 0, 10.7), (34, 11, 2.6), bevel=.1)
    fins = tk.Mesh()
    for i in range(16):
        x = -42+2.0*i
        fins.box((x, 0, 12.8), (.45, 9.0, 1.8))
    pipes = tk.Mesh()
    for y in (6.6, -6.6):
        pipes.cyl((-26, y, 10.6), .75, 36, 'X', n=10)
        pipes.cyl((-26, y*1.12, 9.9), .45, 36, 'X', n=8)
        for x in range(-42, -8, 6):
            pipes.box((x, y, 10.4), (.5, 1.9, 1.5))   # pipe clamps
    mesh_obj(house, 'Hull spine machinery housing', kit.steel, kit)
    mesh_obj(fins, 'Hull spine radiator fins', kit.dark, kit)
    mesh_obj(pipes, 'Hull spine pipe runs', kit.bare, kit)
    # Stern airlock on the central body's aft face, between the drive blocks.
    lock = tk.Mesh()
    w, h = 2.2/kit.mpu, 2.6/kit.mpu
    lock.box((-47.0, 0, 0), (.5, w+.8, h+.8), bevel=.08)
    mesh_obj(lock, 'Hull stern airlock frame', kit.steel, kit)
    door = tk.Mesh()
    door.box((-47.3, 0, 0), (.3, w, h))
    mesh_obj(door, 'Hull stern airlock door', kit.dark, kit)


def waist(kit):
    for side, label in ((1, 'port'), (-1, 'starboard')):
        y = 29.0*side
        bands = tk.Mesh()
        for x in (-41, -36, -31, -26, -22.5, -20.5):
            bands.ring((x, y, 6.5), 5.4, 6.0, .7, 'X', n=16)
        bands.cyl((-46.2, y, 6.5), 1.2, 1.4, 'X', n=10)   # end valve
        mesh_obj(bands, f'Hull {label} waist tank bands', kit.steel, kit)
        sad = tk.Mesh()
        for x in (-39, -23):
            sad.box((x, y, 2.4), (2.2, 9.5, 2.4), bevel=.08)
        mesh_obj(sad, f'Hull {label} waist tank saddles', kit.steel, kit)
        pipe = tk.Mesh()
        # Tank to drive block: elbows over the waist deck.
        for dy, z in ((-3.5, 4.2), (3.0, 4.8)):
            a = Vector((-44, (29+dy)*side, z))
            b = Vector((-52, (24+dy)*side, z+2.2))
            pipe.cyl(tuple((a+b)/2), .6, (b-a).length, 'X', n=10,
                     rot=Matrix.Rotation(math.atan2(b.y-a.y, b.x-a.x), 4, 'Z') @
                     Matrix.Rotation(-math.atan2(b.z-a.z, (b.xy-a.xy).length), 4, 'Y'))
        # Tank to central body: the diagonal runs the sprite shows across the waist.
        for x0, x1, y0, y1 in ((-18, -10, 26, 13), (-34, -42, 23, 12)):
            a = Vector((x0, y0*side, 5.2))
            b = Vector((x1, y1*side, 6.8))
            pipe.cyl(tuple((a+b)/2), .5, (b-a).length, 'X', n=8,
                     rot=Matrix.Rotation(math.atan2(b.y-a.y, b.x-a.x), 4, 'Z') @
                     Matrix.Rotation(-math.atan2(b.z-a.z, (b.xy-a.xy).length), 4, 'Y'))
        mesh_obj(pipe, f'Hull {label} waist pipework', kit.bare, kit)


# --- drive blocks ------------------------------------------------------------

def drive_blocks(kit):
    for side, label in ((1, 'port'), (-1, 'starboard')):
        block = bpy.data.objects[f'Hull {label} drive block']
        # Cut back the aft end so the cans stand proud in their hazard bands.
        cut = tk.Mesh()
        cut.box((-80, 24.25*side, 0), (20, 38.5, 20))   # the outboard wall runs on aft as a frame
        c1 = mesh_obj(cut, 'Temporary drive block aft cutter', kit.dark, kit)
        boolean(block, c1)
        # Trough over the inboard engine: its bare core shows from above.
        cut = tk.Mesh()
        cut.box((-58, 16*side, 5.5), (17, 11.6, 7))
        c2 = mesh_obj(cut, 'Temporary drive block trough cutter', kit.dark, kit)
        boolean(block, c2)
        y3 = 16*side
        core = tk.Mesh()
        for x in (-66.5, -63, -59.5, -56, -52.5):
            core.ring((x, y3, 0), 5.3, 6.1, .8, 'X', n=20)   # lagging rings on the bare core
        mesh_obj(core, f'Hull {label} drive block core rings', kit.steel, kit)
        cradle = tk.Mesh()
        for x in (-64, -54):
            cradle.box((x, y3, -1.5), (1.2, 13.0, 3.0), bevel=.08)
        mesh_obj(cradle, f'Hull {label} drive block core cradle', kit.plate, kit)
        # Thrust frame: the block's new aft face, with a collar round each can.
        frame = tk.Mesh()
        for y in (38, 27, 16):
            frame.ring((-69.6, y*side, 0), 5.5, 6.9, 1.0, 'X', n=20)
        mesh_obj(frame, f'Hull {label} drive block thrust collars', kit.plate, kit)
        # Radiator fin bank and bolted patch plates on the block's outboard top.
        top = tk.Mesh()
        for i in range(8):
            top.box((-66+2.0*i, 34*side, 7.4), (.4, 12, 1.6))
        mesh_obj(top, f'Hull {label} drive block radiator fins', kit.dark, kit)
        patch = tk.Mesh()
        patch.box((-46, 30*side, 6.85), (8, 13, .7), bevel=.08,
                  rot=Matrix.Rotation(math.radians(4*side), 4, 'Z'))
        patch.box((-55, 40*side, 6.85), (6, 5, .7), bevel=.08,
                  rot=Matrix.Rotation(math.radians(-7*side), 4, 'Z'))
        mesh_obj(patch, f'Hull {label} drive block patch plates', kit.red, kit)
        cables = tk.Mesh()
        for dy, r in ((-1.2, .45), (0, .55), (1.2, .45)):
            cables.cyl((-55, (24+dy)*side, 7.2), r, 24, 'X', n=8)
        for x in (-64, -56, -48):
            cables.box((x, 24*side, 7.2), (.6, 4.0, 1.6))
        mesh_obj(cables, f'Hull {label} drive block cable bundle', kit.steel, kit)


# --- outriggers and bow forks -------------------------------------------------

def outriggers(kit):
    for side, label in ((1, 'port'), (-1, 'starboard')):
        m = tk.Mesh()
        m.box((-22, 70*side, 4.2), (14, 12, .5), bevel=.06)
        m.box((-14, 62*side, -1.0), (10, 4, 3.0), bevel=.08)   # strut into the prong's flank
        mesh_obj(m, f'Hull {label} outrigger pod plating', kit.plate, kit)


def bow_forks(kit):
    for side, label in ((1, 'port'), (-1, 'starboard')):
        m = tk.Mesh()
        for y in (29.0, 13.0):
            for dz in (1.6, -1.6):
                m.cyl((126.5, y*side, dz), .3, 9, 'X', n=8)
                m.cyl((130.8, y*side, dz), .45, .6, 'X', n=8)
        mesh_obj(m, f'Hull {label} bow fork probes', kit.steel, kit)


def build(kit):
    global LIVERY
    LIVERY = Livery()
    tk._CAP[0] = tk.CHAMFER_M/kit.mpu
    for side, label in ((1, 'port'), (-1, 'starboard')):
        prong_plating(kit, side, label)
    bridge(kit)
    spine(kit)
    waist(kit)
    drive_blocks(kit)
    outriggers(kit)
    bow_forks(kit)
    bpy.context.view_layer.update()
