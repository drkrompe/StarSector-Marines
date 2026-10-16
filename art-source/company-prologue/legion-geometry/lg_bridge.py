"""The bone-white command dome (sprite X 4-66, Y +-22) over the spine.

The Legion's command-control, sufficient to field four fighter wings, sits in
the dome at the head of the spine, inside the inner belt's collar.

Pass 2 detail, read off legion_xiv.png:
- the dome is an armoured shell of bone-white plates over a gunmetal frame:
  transverse bands with real seam gaps, the crown split by the two dark
  longitudinal lines the sprite shows at Y +-4.5 (X 20-47), the shoulders split
  along their length, the walls carrying a slit-viewport row under a brow;
- the dark-and-light checker at X 60-64 is the command glazing band, set back
  between the nose cap and the first plate band; few panes are lit;
- the dark sensor eye sits on the crown at X 52.5, Y 0;
- the white neck aft of the dome (X 7 to -15, Y +-7) is a raised armoured spine
  housing in segments over the tail spine.
'Bridge command dome' is still the dome solid (it counts in the pressure-hull
volume) with its pass-1 stations; it now carries the gunmetal frame material
seen in the seams. The plates are thin shells on its own facets.
"""
import bpy
from lg_kit import loft_x, octagon, prism, section, ccw, clip_rect, material

# Command dome: (x, half-width, crown z) stations, nose to tail.
DOME = [(67, 1.5, 19.0), (64, 8, 24.5), (58, 14, 28.0), (48, 19, 30.0), (36, 21.5, 30.5),
        (24, 20, 29.5), (12, 15, 27.5), (5, 11, 25.0), (2, 6, 21.0)]
DOME_BASE = 12.0

SEAM = .35           # half the gap between neighbouring plates along a facet
PLATE_OUT, PLATE_IN = .55, .4
# Octagon facets as (start, end) vertex indices; the octagon runs CCW in YZ.
FACETS = {'port wall': (3, 4), 'port shoulder': (4, 5), 'crown': (5, 6),
          'starboard shoulder': (6, 7), 'starboard wall': (7, 0)}
# Transverse plate bands (x0, x1): the gaps between them are the band seams.
BANDS = [(47.4, 59.2), (34.4, 47.0), (21.4, 34.0), (9.4, 21.0), (2.6, 9.0)]
CROWN_SPLIT = {(34.4, 47.0): 4.5, (21.4, 34.0): 4.5}   # band -> |Y| of the crown's two seams
GLAZING = (59.7, 63.8)
NOSE = (64.3, 66.4)
SLIT_Z = (17.0, 17.9)          # wall viewport slit, between the lower and upper wall plates
SLIT_BAND = (34.4, 47.0)
EYE = (52.5, 0.0)
# Neck: port half-plan of the raised spine housing over the tail spine.
NECK_PORT = [(7, 0), (7, 7), (-4, 7), (-10, 6), (-14, 4), (-15.5, 0)]
NECK_SEGMENTS = [(3.5, -2.4), (-3.2, -8.4), (-9.2, -14.0)]


def dome_at(x):
    """(half-width, crown z) of the dome at station x, linear between DOME stations."""
    for (xa, wa, za), (xb, wb, zb) in zip(DOME, DOME[1:]):
        if xb <= x <= xa:
            t = (xa-x)/(xa-xb)
            return wa+(wb-wa)*t, za+(zb-za)*t
    return (DOME[0][1], DOME[0][2]) if x > DOME[0][0] else (DOME[-1][1], DOME[-1][2])


def dome_section(x):
    w, z = dome_at(x)
    return octagon(-w, w, DOME_BASE, z, ct=w*.5, cb=min(1.0, w*.2))


class Shells:
    """Thin plate solids following the dome's facets, gathered into one mesh."""

    def __init__(self):
        self.verts, self.faces = [], []

    def plate(self, facet, x0, x1, lo=None, hi=None, axis=None, out=PLATE_OUT, inn=PLATE_IN, samples=4):
        """A plate on `facet` from x0 to x1. Without `axis` it spans the facet less a seam
        at each end; with axis 'y'/'z' it spans coordinates lo..hi on that axis, clipped to
        the facet less its seams."""
        a, b = FACETS[facet]
        rows = []
        for k in range(samples):
            x = x0+(x1-x0)*k/(samples-1)
            sec = dome_section(x)
            (ya, za), (yb, zb) = sec[a], sec[b]
            dy, dz = yb-ya, zb-za
            length = (dy*dy+dz*dz)**.5
            if length < 2*SEAM+.3:
                return
            u0, u1 = SEAM/length, 1-SEAM/length
            if axis:
                s, e = (ya, yb) if axis == 'y' else (za, zb)
                if abs(e-s) < 1e-6:
                    return
                v0, v1 = sorted(((lo-s)/(e-s), (hi-s)/(e-s)))
                u0, u1 = max(u0, v0), min(u1, v1)
                if u1-u0 < .3/length:
                    return
            ny, nz = dz/length, -dy/length
            pa = (ya+dy*u0, za+dz*u0)
            pb = (ya+dy*u1, za+dz*u1)
            rows.append([(x, pa[0]+ny*out, pa[1]+nz*out), (x, pb[0]+ny*out, pb[1]+nz*out),
                         (x, pb[0]-ny*inn, pb[1]-nz*inn), (x, pa[0]-ny*inn, pa[1]-nz*inn)])
        base = len(self.verts)
        for r in rows:
            self.verts += r
        n = len(rows)
        self.faces += [(base, base+1, base+2, base+3), (base+4*(n-1), base+4*(n-1)+1, base+4*(n-1)+2, base+4*(n-1)+3)]
        for s in range(n-1):
            o = base+4*s
            self.faces += [(o+i, o+(i+1) % 4, o+4+(i+1) % 4, o+4+i) for i in range(4)]

    def build(self, kit, name, mat):
        return kit.mesh(name, self.verts, self.faces, mat, bevel=0) if self.faces else None


def _material(name, *args):
    return bpy.data.materials.get(name) or material(name, *args)


def build(kit):
    stations = [(x, octagon(-w, w, DOME_BASE, z, ct=w*.5, cb=min(1.0, w*.2))) for x, w, z in DOME]
    # The dome solid is the frame the plates are hung on: gunmetal in the seams.
    dome = loft_x(kit, 'Bridge command dome', stations, kit.steel)
    kit.hull.append(dome)

    # --- Armour plates: transverse bands, each facet a plate, seams between. ---
    plates = Shells()
    for x0, x1 in BANDS + [NOSE]:
        split = CROWN_SPLIT.get((x0, x1))
        if split:
            plates.plate('crown', x0, x1, -60, -split-SEAM, axis='y')
            plates.plate('crown', x0, x1, -split+SEAM, split-SEAM, axis='y')
            plates.plate('crown', x0, x1, split+SEAM, 60, axis='y')
        else:
            plates.plate('crown', x0, x1)
        for side in ('port', 'starboard'):
            w, zc = dome_at((x0+x1)/2)
            zmid = zc-w*.25          # shoulders split along their length at mid-slope
            plates.plate(f'{side} shoulder', x0, x1, -10, zmid-SEAM, axis='z')
            plates.plate(f'{side} shoulder', x0, x1, zmid+SEAM, 60, axis='z')
            if (x0, x1) == SLIT_BAND:
                plates.plate(f'{side} wall', x0, x1, -10, SLIT_Z[0], axis='z')
                plates.plate(f'{side} wall', x0, x1, SLIT_Z[1], 60, axis='z')
            else:
                plates.plate(f'{side} wall', x0, x1)
    for side in ('port', 'starboard'):     # the glazing band's walls stay armoured
        plates.plate(f'{side} wall', *GLAZING)
    shell = plates.build(kit, 'Bridge dome armour plates', kit.bone)

    # --- Command glazing band (the sprite's checker at X 60-64), set back under the plates. ---
    unlit = _material('Bridge glazing unlit', (.02, .03, .035), .3, .12)
    lit, dark = Shells(), Shells()
    gx0, gx1 = GLAZING
    w, zc = dome_at((gx0+gx1)/2)
    panes = {'crown': (-(w*.5), w*.5, 'y', 4), 'port shoulder': (zc-w*.5, zc, 'z', 3),
             'starboard shoulder': (zc-w*.5, zc, 'z', 3)}
    for facet, (lo, hi, axis, n) in panes.items():
        step = (hi-lo)/n
        for k in range(n):
            on = (facet == 'crown' and k in (1, 2)) or (facet != 'crown' and k == n-1)
            (lit if on else dark).plate(facet, gx0, gx1, lo+k*step+.3, lo+(k+1)*step-.3, axis=axis,
                                        out=.12, inn=.3, samples=3)
    lit.build(kit, 'Bridge command glazing lit panes', kit.glass)
    dark.build(kit, 'Bridge command glazing panes', unlit)

    # --- Wall viewport slits, recessed between the wall plates, and the brow over them. ---
    for side in ('port', 'starboard'):
        slits_lit, slits = Shells(), Shells()
        sx0, sx1 = SLIT_BAND
        n = 5
        step = (sx1-sx0)/n
        for k in range(n):
            (slits_lit if k == 2 else slits).plate(f'{side} wall', sx0+k*step+.35, sx0+(k+1)*step-.35,
                                                   SLIT_Z[0]+.05, SLIT_Z[1]-.05, axis='z', out=.05, inn=.3, samples=3)
        slits_lit.build(kit, f'Bridge {side} viewport slit lit', kit.glass)
        slits.build(kit, f'Bridge {side} viewport slits', unlit)
        brow = Shells()
        brow.plate(f'{side} wall', sx0, sx1, SLIT_Z[1]+.05, SLIT_Z[1]+.55, axis='z', out=1.4, inn=.3, samples=5)
        brow.build(kit, f'Bridge {side} viewport brow', kit.steel)

    # --- Sensor eye on the crown. ---
    ex, ey = EYE
    kit.hull.append(shell)          # seat the eye on the plates, then leave the seating set as it was
    hit = kit.surface(ex, ey, top=120)
    kit.hull.remove(shell)
    seat = hit[0].z if hit else dome_at(ex)[1]+PLATE_OUT
    kit.cylinder('Bridge sensor eye housing', (ex, ey, seat+.1), 2.8, 1.4, kit.steel, vertices=20, bevel=.12)
    bpy.ops.mesh.primitive_uv_sphere_add(segments=16, ring_count=8, radius=1.8, location=(ex, ey, seat+.8))
    lens = bpy.context.object
    lens.scale = (1, 1, .55)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    kit.finish(lens, 'Bridge sensor eye lens', _material('Bridge sensor lens', (.015, .015, .02), .6, .08))

    # --- Neck: a segmented armoured spine housing running aft over the tail spine. ---
    neck_plan = ccw(NECK_PORT + [(x, -y) for x, y in reversed(NECK_PORT) if y > 0])
    top = kit.surface(-5, 0, top=120)
    base = (top[0].z if top else 14.0)-1.0
    core = prism(kit, 'Bridge neck spine housing', neck_plan, section(base, base+4.3, .3, 1.4), kit.steel)
    for k, (xa, xb) in enumerate(NECK_SEGMENTS, 1):
        wa, wb = _neck_half(xa)+.6, _neck_half(xb)+.6
        band = ccw([(xa, wa), (xb, wb), (xb, -wb), (xa, -wa)])
        prism(kit, f'Bridge neck armour segment {k}', band, section(base+.5, base+5.0, .3, 1.2), kit.bone, bevel=0)


def _neck_half(x):
    """Half-width of the neck plan at station x."""
    pts = NECK_PORT[1:]
    for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
        if xb <= x <= xa:
            return ya+(yb-ya)*(xa-x)/(xa-xb)
    return pts[0][1] if x > pts[0][0] else pts[-1][1]
