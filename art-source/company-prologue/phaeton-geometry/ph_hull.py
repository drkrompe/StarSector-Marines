"""Phaeton hull: the long red tank hull, its three spherical cargo tanks, the
port gallery block, the turret sponsons and the aft machinery spine.

Read off tanker_medium.png (bow right, port up in model XY):
- the red hull runs X -34..128, Y -29..+30, its bow rounded in plan;
- three spherical tanks sit on the centreline at X 0, 44 and 88, each about
  30 units across: a Moss-type layout, the spheres standing half proud of
  the deck in their skirts (the dark ring round each sphere is the skirt top);
- the raised port block X 55..82 reaching Y +37 carries an amber-lit strip
  along its crown: the crew gallery's skylight, with a louvred vent at
  X 63..68 Y +28 on its roof;
- the round grey housings at the turret slots stand outboard of the hull on
  sponsons (WS 002 port, WS 003 starboard, WS 004 on the port bow);
- aft of the hull, a narrow machinery spine (X -64..-30) carries the
  silvery reactor dome at X -50 (ringed, with ribs either side of it) and
  joins the hull to the drive block;
- pass 2 detail: three dark louvre slots at X 17..20 between tanks 1 and 2
  (a valve box), a dark box with two yellow lights at X 64..70 Y +10..+18
  between tanks 2 and 3 (a pump house), a round cross-barred hatch at
  (57, -16), dark vent grilles at X 13..22 Y -17, X 51..58 Y -26 and on the
  bow at X 104..110 Y +22, pale raised plates either side of tank 1 at
  X -6..+4 (bolted service covers), and rows of small yellow lights along the
  grey strakes' outer edges (Y +40 and -37 aft, -36 at X 56..62, the
  starboard bow strake at X 112..118).
Heights are proposed art: keel -20, deck +6, tank centres on the deck.
Plate seams are cut into the solids as dark grooves (the cutter's dark
material lands on the groove faces), so every existing object keeps its
name and stays one piece for the destroyed state.
"""
import math
import bpy
from ph_kit import prism, section, plane_cut, sphere, inset, boolean, material

KEEL, DECK = -20.0, 6.0
# (name, x, radius): tank centres sit on the deck line.
TANKS = [('Tank 1 aft sphere', 0.0, 15.0), ('Tank 2 mid sphere', 44.0, 14.5), ('Tank 3 fore sphere', 88.0, 15.5)]
PLAN = [(-36, -22), (-28, -30), (100, -30), (112, -31), (124, -31), (130, -26), (132, -18), (129.5, -6), (129, 2),
        (126, 10), (125, 20), (121, 28), (114, 34), (104, 35), (96, 33), (86, 30), (56, 30), (52, 31), (-28, 31), (-36, 23)]
HULL_SECTION = section(KEEL, DECK, 10, 3)
# Grey machinery strakes along both flanks, below the red deck: pump rooms and
# pipe galleries out to the sprite's edge (Y +-38), stepping the section.
STRAKES = {
    'port': [(-38, 24), (-30, 26), (100, 26), (104, 31), (100, 36), (86, 38.5), (56, 38.5), (52, 39), (22, 39),
             (16, 40.5), (-10, 40.5), (-14, 38.5), (-26, 38.5), (-38, 32)],
    'starboard': [(-38, -32), (-28, -37.5), (-8, -37.5), (-4, -35.5), (26, -35.5), (56, -36.5), (60, -37), (98, -37), (104, -42),
                  (118, -42), (124, -36), (118, -28), (-30, -26), (-38, -24)],
}
STRAKE_TOP, STRAKE_BOTTOM = -1.0, -13.0
SPONSONS = {  # slot id: (centre x, y, radius, inboard reach)
    'WS 002': (38, 38, 11.0), 'WS 003': (36, -34, 10.0), 'WS 004': (126, 13, 10.5),
}
GALLERY = [(54, 22), (84, 22), (84, 33), (80, 37), (58, 37), (54, 33)]
GALLERY_TOP = DECK+6

# Plate seams: transverse frames that wrap deck, sides and bottom, two
# longitudinal deck strakes a side, and the side strata.
FRAME_STATIONS = [-24, -10, 8, 22, 36, 50, 64, 78, 92, 106]
DECK_SEAMS_Y = [-21.0, -11.5, 11.5, 21.0]
SEAM_WIDTH, SEAM_DEPTH = .4, .35

# Crown access trunk on each dome: the deck module runs its pipes and
# catwalk to this flat top. Height above the sphere's crown, and radius.
TRUNK_RISE, TRUNK_R = 2.9, 2.4
TANK_TRUNK_TOP = {name: DECK+r+TRUNK_RISE for name, _, r in TANKS}

# Strake panel bays on the outboard walls (x0, x1, kind); kept clear of the
# sponson webs. 'door' is an EVA hatch, 'vent' a louvred pump-room vent.
STRAKE_BAYS = {
    'port': [(-24, -16, 'vent'), (-8, -2, 'panel'), (-1, 5, 'door'), (6, 14, 'vent'),
             (57, 64, 'panel'), (65, 70, 'door'), (71, 78, 'vent'), (79, 85, 'panel')],
    'starboard': [(-26, -18, 'vent'), (-16, -10, 'door'), (-2, 6, 'panel'), (7, 13, 'door'), (14, 22, 'vent'),
                  (48, 55, 'panel'), (61, 68, 'vent'), (69, 75, 'door'), (76, 84, 'panel'), (85, 92, 'vent'),
                  (106, 112, 'panel'), (113, 117, 'door')],
}
STRAKE_TOP_SEAMS = {'port': [-20, -4, 10, 18, 60, 70, 80, 92], 'starboard': [-18, -4, 12, 52, 66, 80, 94, 110]}
# Yellow lamp boxes where the sprite shows yellow dots along the strake edges.
STRAKE_LAMPS = {
    'port': [(-14, 37.6), (-9, 39.6), (0, 39.6), (4, 39.6), (9, 39.6), (16, 39.4)],
    'starboard': [(-14, -36.6), (-10, -36.6), (-6, -36.6), (56, -35.6), (59, -36.0), (62, -36.0),
                  (112, -40.6), (115, -39.8), (118, -38.6)],
}
# Deck vent grilles (centre x, y, length along X, width, slats).
VENTS = [(17.5, -17.0, 9.0, 3.4, 7), (54.5, -24.0, 7.0, 3.0, 5), (65.5, 28.5, 5.0, 3.0, 4), (107.0, 22.0, 5.0, 4.0, 4)]
# Bolted service covers (centre x, y, length, width, pale): the pale ones are
# the light plates either side of tank 1.
COVERS = [(-4.5, 22.5, 3.5, 8.0, True), (1.5, 22.5, 3.5, 8.0, True), (-4.5, -21.5, 3.5, 8.0, True),
          (1.5, -21.5, 3.5, 8.0, True), (30.0, -22.0, 6.0, 4.0, False), (24.0, 21.5, 6.0, 4.0, False),
          (78.0, -21.0, 5.0, 5.0, False), (120.0, -2.0, 4.0, 6.0, False)]
ROUND_HATCH = (57.0, -16.0, 4.0)
VALVE_BOX = (17.5, 10.0, 4.0, 6.0, 3.0)      # aft valve box between tanks 1 and 2, port of the deck trunk: x, y, dx, dy, height
PUMP_HOUSE = (66.0, 14.0, 7.0, 8.0, 4.0)     # fore pump house between tanks 2 and 3
SPINE = [(-66, -16), (-28, -16), (-28, 16), (-66, 16)]
SPINE_SECTION = section(-14, DECK+2, 4, 2.5)
REACTOR = (-50.0, 0.0, DECK+1, 8.5)          # dome centre x, y, z and radius


class Geo:
    """Accumulates boxes, rings and tori into one mesh, so a cutter or a
    run of small fittings is one object."""
    def __init__(self):
        self.verts, self.faces = [], []

    def box(self, c, d):
        (cx, cy, cz), (dx, dy, dz) = c, d
        o = len(self.verts)
        self.verts += [(cx+sx*dx/2, cy+sy*dy/2, cz+sz*dz/2) for sx in (-1, 1) for sy in (-1, 1) for sz in (-1, 1)]
        self.faces += [(o, o+1, o+3, o+2), (o+4, o+6, o+7, o+5), (o, o+4, o+5, o+1),
                       (o+2, o+3, o+7, o+6), (o, o+2, o+6, o+4), (o+1, o+5, o+7, o+3)]

    def ring(self, outer, inner, a0, a1, axis='Z'):
        """Annular prism between two equal-count polygons, extruded a0..a1
        along Z (polygons in XY) or along X (polygons in YZ)."""
        n = len(outer)
        pt = (lambda p, a: (p[0], p[1], a)) if axis == 'Z' else (lambda p, a: (a, p[0], p[1]))
        o = len(self.verts)
        for poly in (outer, inner):
            for a in (a0, a1):
                self.verts += [pt(p, a) for p in poly]
        O0, O1, I0, I1 = o, o+n, o+2*n, o+3*n
        for i in range(n):
            j = (i+1) % n
            self.faces += [(O0+i, O0+j, O1+j, O1+i), (I0+j, I0+i, I1+i, I1+j),
                           (O1+i, O1+j, I1+j, I1+i), (O0+j, O0+i, I0+i, I0+j)]

    def torus(self, R, r, xf, n=40, m=6):
        o = len(self.verts)
        for i in range(n):
            a = 2*math.pi*i/n
            for j in range(m):
                b = 2*math.pi*j/m
                self.verts.append(xf(((R+r*math.cos(b))*math.cos(a), (R+r*math.cos(b))*math.sin(a), r*math.sin(b))))
        for i in range(n):
            for j in range(m):
                i2, j2 = (i+1) % n, (j+1) % m
                self.faces.append((o+i*m+j, o+i2*m+j, o+i2*m+j2, o+i*m+j2))

    def emit(self, kit, name, mat, bevel=0):
        return kit.mesh(name, self.verts, self.faces, mat, bevel)


def circle(r, n=32, cx=0.0, cy=0.0):
    return [(cx+r*math.cos(2*math.pi*i/n), cy+r*math.sin(2*math.pi*i/n)) for i in range(n)]


def ccw(poly):
    area = sum(a[0]*b[1]-b[0]*a[1] for a, b in zip(poly, poly[1:]+poly[:1]))
    return poly if area > 0 else list(reversed(poly))


def edge_y(plan, x, side):
    """Outermost plan Y at station x on the port (+1) or starboard (-1) side."""
    ys = []
    for (ax, ay), (bx, by) in zip(plan, plan[1:]+plan[:1]):
        if ax != bx and min(ax, bx) <= x <= max(ax, bx):
            ys.append(ay+(x-ax)/(bx-ax)*(by-ay))
    return max(ys) if side > 0 else min(ys)


def cross_section(plan, profile, x):
    """YZ outline of a planform prism at station x (straight-sided stretches)."""
    ys, yp = edge_y(plan, x, -1), edge_y(plan, x, 1)
    return ccw([(ys+d, z) for d, z in profile]+[(yp-d, z) for d, z in reversed(profile)])


def cut(kit, target, geo, name):
    """Groove the target with a cutter built from geo; the grooves take the dark material."""
    boolean(target, geo.emit(kit, 'Temporary '+name+' cutter', kit.dark))


def frame_seams(kit, target, plan, profile, stations, name):
    g = Geo()
    for x in stations:
        sec = cross_section(plan, profile, x)
        g.ring(inset(sec, -1.5), inset(sec, SEAM_DEPTH), x-SEAM_WIDTH/2, x+SEAM_WIDTH/2, 'X')
    cut(kit, target, g, name)


def strata(kit, target, plan, bands, name):
    """Horizontal seam rings: bands of (z, plan inset of the surface there)."""
    g = Geo()
    for z, d in bands:
        g.ring(inset(plan, d-1.5), inset(plan, d+SEAM_DEPTH*1.4), z-SEAM_WIDTH/2, z+SEAM_WIDTH/2)
    cut(kit, target, g, name)


def seat(kit, x, y):
    hit = kit.surface(x, y)
    if hit is None:
        raise RuntimeError(f'nothing to seat on at ({x}, {y})')
    return hit[0].z


def bolts(g, x, y, z, lx, ly, step=1.6, size=.3):
    """A bolt row round a rectangular cover's edge."""
    nx, ny = max(2, int(lx/step)+1), max(2, int(ly/step)+1)
    pts = set()
    for i in range(nx):
        px = x-lx/2+.4+(lx-.8)*i/(nx-1)
        pts |= {(round(px, 3), round(y-ly/2+.4, 3)), (round(px, 3), round(y+ly/2-.4, 3))}
    for j in range(ny):
        py = y-ly/2+.4+(ly-.8)*j/(ny-1)
        pts |= {(round(x-lx/2+.4, 3), round(py, 3)), (round(x+lx/2-.4, 3), round(py, 3))}
    for px, py in sorted(pts):
        g.box((px, py, z+size/2), (size, size, size))


def build_body(kit):
    body = prism(kit, 'Hull main body', PLAN, HULL_SECTION, kit.red)
    # Plate seams before the bow facets, so the facets stay clean plate.
    frame_seams(kit, body, PLAN, HULL_SECTION, FRAME_STATIONS, 'hull frame seams')
    g = Geo()
    for y in DECK_SEAMS_Y:
        g.box((46, y, DECK), (152, SEAM_WIDTH, SEAM_DEPTH*2))
    cut(kit, body, g, 'deck strake seams')
    # Side strata: one in the red band above the strakes, one on the keel chamfer.
    strata(kit, body, PLAN, [(1.0, 0.0), (-15.0, 5.0)], 'hull strata')
    # Bow rake: a raked forefoot under the nose and a shoulder facet over it.
    plane_cut(body, 'bow forefoot', (124, 0, -8), (.5, 0, -.87))
    plane_cut(body, 'bow shoulder', (129, 0, 3), (.6, 0, .8))
    kit.hull.append(body)

    # Raised deck plating edge: a low coaming just inside the shoulder chamfer.
    g = Geo()
    g.ring(inset(PLAN, 3.2), inset(PLAN, 4.4), DECK-.2, DECK+.7)
    coaming = g.emit(kit, 'Hull deck edge coaming', kit.red, .08)
    plane_cut(coaming, 'coaming bow', (126.5, 0, 3), (.6, 0, .8))

    # Keel and underside: a keel strake on the centreline, ventral plates
    # either side of it, four docking hardpoints and a towing padeye on the stem.
    keel = kit.box('Hull keel strake', (38, 0, KEEL-.8), (128, 5, 1.6), kit.steel, .2)
    plane_cut(keel, 'keel forefoot', (124, 0, -8), (.5, 0, -.87))
    g = Geo()
    for x0 in range(-20, 100, 12):
        for yc in (-11.0, 11.0):
            g.box((x0+5.6, yc, KEEL-.15), (11.0, 13.0, .3))
    plates = g.emit(kit, 'Hull ventral plates', kit.red, .05)
    plane_cut(plates, 'ventral forefoot', (124, 0, -8), (.5, 0, -.87))
    g, s = Geo(), Geo()
    for x in (30, 78):
        for y in (-12, 12):
            g.box((x, y, KEEL-.9), (4.5, 4.5, 1.4))
            s.box((x, y, KEEL-1.62), (2.2, 2.2, .1))
    g.emit(kit, 'Hull ventral docking hardpoints', kit.steel, .15)
    s.emit(kit, 'Hull ventral docking sockets', kit.dark)
    a = math.radians(-30)  # the forefoot facet falls 30 degrees from the keel line
    n = (.5, 0, -.87)
    z = -8+.5*(116-124)/.87
    eye = kit.box('Hull bow towing padeye', (116+n[0]*.5, 0, z+n[2]*.5), (3, 2.2, 1.4), kit.steel, .15)
    eye.rotation_euler[1] = a


def build_tanks(kit):
    for name, x, r in TANKS:
        dome = sphere(kit, name, (x, 0, DECK), r, kit.tank)
        kit.hull.append(dome)
        base = name.replace(' sphere', '')
        # Moss skirt: the cylindrical collar the sphere stands in, a stiffener
        # band round it and the dark weather seal where the cover meets it.
        top = DECK+3.0
        kit.cylinder(name.replace('sphere', 'skirt'), (x, 0, DECK+1.5), r+1.2, 3.0, kit.red, 'Z', 48, .2)
        kit.cylinder(base+' skirt stiffener band', (x, 0, DECK+1.4), r+1.65, .8, kit.steel, 'Z', 48, .1)
        rz = math.sqrt(r*r-(top-DECK)**2)
        g = Geo()
        g.torus(rz+.1, .32, lambda p, x=x: (p[0]+x, p[1], p[2]+top))
        g.emit(kit, base+' weather seal', kit.dark)
        # Cover plate seams: a latitude seam and two meridians (four lines over the cover).
        g = Geo()
        phi = math.radians(52)
        g.torus(r*math.sin(phi)+.03, .11, lambda p, x=x: (p[0]+x, p[1], p[2]+DECK+r*math.cos(phi)), 64, 5)
        for k in range(2):
            psi = math.pi/4+k*math.pi/2
            g.torus(r+.03, .11, lambda p, x=x, psi=psi: (x+p[0]*math.cos(psi)-p[2]*math.sin(psi),
                                                      p[0]*math.sin(psi)+p[2]*math.cos(psi), DECK+p[1]), 64, 5)
        g.emit(kit, base+' cover seams', kit.dark)
        # Crown access trunk: flange on the sphere, trunk, flat cap at TANK_TRUNK_TOP.
        crown = DECK+r
        kit.cylinder(base+' trunk flange', (x, 0, crown-.3), TRUNK_R+1.1, .6, kit.steel, 'Z', 24, .08)
        kit.cylinder(base+' crown trunk', (x, 0, crown+(TRUNK_RISE-.5)/2-.3), TRUNK_R, TRUNK_RISE-.5+.6,
                     kit.grey, 'Z', 24, .08)
        kit.cylinder(base+' trunk cap', (x, 0, crown+TRUNK_RISE-.25), TRUNK_R+.5, .5, kit.steel, 'Z', 24, .08)
        # A side manhole on the trunk, facing aft along the catwalk.
        kit.box(base+' trunk manhole', (x-TRUNK_R-.1, 0, crown+1.1), (.35, 1.6, 1.6), kit.dark, .05)


def build_strakes(kit):
    for side, plan in STRAKES.items():
        s = 1 if side == 'port' else -1
        strake = prism(kit, f'Hull {side} machinery strake', plan, section(STRAKE_BOTTOM, STRAKE_TOP, 3, 1.5), kit.steel)
        cutter, slats, doors, panels = Geo(), Geo(), Geo(), Geo()
        for x0, x1, kind in STRAKE_BAYS[side]:
            xm = (x0+x1)/2
            yo = edge_y(plan, xm, s)
            if kind == 'door':  # an EVA hatch 2.2 x 6.5, in a recessed frame
                cutter.box((xm, yo, -6.2), (3.0, 1.4, 7.2))
                doors.box((xm, yo-s*.45, -6.2), (2.2, .3, 6.5))
                doors.box((xm+.6, yo-s*.25, -6.0), (.3, .2, 1.0))  # dogging handle
            else:
                cutter.box((xm, yo, -6.25), (x1-x0, 1.4, 5.5))
                if kind == 'vent':
                    for z in (-8.2, -7.2, -6.2, -5.2, -4.2):
                        slats.box((xm, yo-s*.4, z), (x1-x0-.3, .5, .3))
                else:
                    panels.box((xm, yo-s*.45, -6.25), (x1-x0-.9, .3, 4.6))
        for x in STRAKE_TOP_SEAMS[side]:
            cutter.box((x, s*36, STRAKE_TOP), (SEAM_WIDTH, 16, SEAM_DEPTH*2))
        cut(kit, strake, cutter, f'{side} strake bays')
        kit.hull.append(strake)
        slats.emit(kit, f'Hull {side} strake vent louvres', kit.grey, .03)
        doors.emit(kit, f'Hull {side} strake EVA hatches', kit.grey, .05)
        panels.emit(kit, f'Hull {side} strake bolted panels', kit.steel, .05)
        # Lamp boxes on the strake edge: a housing with an amber lens on top.
        housings, lenses = Geo(), Geo()
        for x, y in STRAKE_LAMPS[side]:
            y = s*min(abs(y), abs(edge_y(plan, x, s))-1.0)  # on the edge chamfer, never past it
            z = seat(kit, x, y)
            housings.box((x, y, z+.3), (1.2, .9, .7))
            lenses.box((x, y, z+.72), (.7, .55, .18))
        housings.emit(kit, f'Hull {side} strake lamp housings', kit.steel, .05)
        lenses.emit(kit, f'Hull {side} strake lamps', kit.lamp)

    # Aft transverse frame: where the hull meets the spine and the drive housings.
    kit.hull.append(prism(kit, 'Hull aft transverse frame', [(-42, -34), (-30, -34), (-30, 34), (-42, 34)],
                          section(-13, DECK-3, 3, 2), kit.steel))


def build_gallery(kit):
    gal = prism(kit, 'Hull port gallery block', GALLERY, section(-12, GALLERY_TOP, 3, 2.5), kit.red)
    # Plating: one stratum over the window band and two frame seams.
    strata(kit, gal, GALLERY, [(7.2, 0.0)], 'gallery stratum')
    frame_seams(kit, gal, GALLERY, section(-12, GALLERY_TOP, 3, 2.5), [64.5, 73.5], 'gallery frames')
    # Windows: a row of person-scale ports (0.6 x 0.9 m) along the outboard
    # wall, between the strake top and the stratum.
    g, glass = Geo(), Geo()
    for x in (60.0, 63.0, 66.0, 69.0, 72.0, 75.0, 78.0):
        g.box((x, 37.0, 3.6), (1.9, .8, 2.8))
        glass.box((x, 36.75, 3.6), (1.9, .1, 2.8))
    cut(kit, gal, g, 'gallery windows')
    kit.hull.append(gal)
    glass.emit(kit, 'Hull gallery windows', kit.glass)
    kit.box('Hull gallery skylight', (69, 34.6, DECK+6.05), (20, 1.6, .25), kit.lamp, 0)
    g = Geo()
    for i in range(9):
        g.box((59.5+i*2.375, 34.6, GALLERY_TOP+.25), (.3, 2.0, .3))
    for y in (33.5, 35.7):
        g.box((69, y, GALLERY_TOP+.25), (20.4, .3, .3))
    g.emit(kit, 'Hull gallery skylight frame', kit.steel)


def build_deck_fittings(kit):
    for i, (x, y, lx, ly, n) in enumerate(VENTS, 1):
        z = seat(kit, x, y)
        kit.box(f'Hull deck vent {i} well', (x, y, z+.15), (lx, ly, .3), kit.dark, 0)
        g = Geo()
        for k in range(n):
            g.box((x-lx/2+lx*(k+.5)/n, y, z+.45), (.4, ly-.2, .3))
        for yy in (y-ly/2+.15, y+ly/2-.15):
            g.box((x, yy, z+.4), (lx, .3, .5))
        g.emit(kit, f'Hull deck vent {i} slats', kit.steel)

    pale = material('Hull pale access plate', (.62, .50, .44), .2, .5)
    for i, (x, y, lx, ly, light) in enumerate(COVERS, 1):
        z = seat(kit, x, y)
        kit.box(f'Hull deck service cover {i}', (x, y, z+.2), (lx, ly, .4), pale if light else kit.red, .06)
        g = Geo()
        bolts(g, x, y, z+.4, lx, ly)
        g.emit(kit, f'Hull deck service cover {i} bolts', kit.steel)

    # The round cross-barred hatch at (57, -16).
    x, y, r = ROUND_HATCH
    z = seat(kit, x, y)
    kit.cylinder('Hull deck round hatch coaming', (x, y, z+.4), r, .8, kit.steel, 'Z', 24, .08)
    kit.cylinder('Hull deck round hatch cover', (x, y, z+.9), r-.6, .3, kit.grey, 'Z', 24, .05)
    g = Geo()
    g.box((x, y, z+1.15), (2*r-1.6, .5, .3))
    g.box((x, y, z+1.15), (.5, 2*r-1.6, .3))
    g.emit(kit, 'Hull deck round hatch dogs', kit.dark)

    # Aft valve box between tanks 1 and 2: three louvre slots on its crown.
    x, y, lx, ly, h = VALVE_BOX
    z = seat(kit, x, y)
    kit.box('Hull aft valve box', (x, y, z+h/2), (lx, ly, h), kit.steel, .2)
    g = Geo()
    for yy in (y-3.4, y, y+3.4):
        g.box((x, yy, z+h+.05), (lx-1.4, 2.0, .2))
    g.emit(kit, 'Hull aft valve box louvres', kit.dark)
    # Fore pump house between tanks 2 and 3: two yellow lamps on its crown, a
    # louvred panel and a low access hatch on its faces.
    x, y, lx, ly, h = PUMP_HOUSE
    z = seat(kit, x, y)
    kit.box('Hull fore pump house', (x, y, z+h/2), (lx, ly, h), kit.steel, .2)
    g = Geo()
    g.box((x, y-ly/2-.05, z+h/2), (lx-2, .2, h-1.6))
    g.box((x+lx/2+.05, y+1, z+h/2-.2), (.2, 2.2, h-1.2))
    g.emit(kit, 'Hull fore pump house recesses', kit.dark)
    housings, lenses = Geo(), Geo()
    for px, py in ((x-1.5, y-1.0), (x+1.5, y+2.5)):
        housings.box((px, py, z+h+.3), (1.1, .9, .6))
        lenses.box((px, py, z+h+.68), (.65, .55, .16))
    housings.emit(kit, 'Hull fore pump house lamp housings', kit.grey)
    lenses.emit(kit, 'Hull fore pump house lamps', kit.lamp)


def build_sponsons(kit):
    for sid, (x, y, r) in SPONSONS.items():
        s = 1 if y > 0 else -1
        ring = [(x+r*math.cos(math.pi/8+i*math.pi/4), y+r*math.sin(math.pi/8+i*math.pi/4)) for i in range(8)]
        ob = prism(kit, f'Hull sponson {sid}', ring, section(-8, DECK+1, 3, 1.2), kit.grey)
        # Plating strata round the drum; the top stays clean for the turret ring.
        g = Geo()
        for z in (-2.5, 2.5):
            g.ring(inset(ring, -1.5), inset(ring, SEAM_DEPTH), z-SEAM_WIDTH/2, z+SEAM_WIDTH/2)
        cut(kit, ob, g, f'sponson {sid} strata')
        kit.hull.append(ob)
        # Underside: a smaller faceted drum under the keel chamfer, with a drain plate.
        under = [(x+(r-3.5)*math.cos(math.pi/8+i*math.pi/4), y+(r-3.5)*math.sin(math.pi/8+i*math.pi/4)) for i in range(8)]
        prism(kit, f'Hull sponson {sid} underside', under, section(-10.5, -7.5, 1.2, 0), kit.steel)
        kit.cylinder(f'Hull sponson {sid} drain plate', (x, y, -10.6), 2.0, .3, kit.dark, 'Z', 16, 0)
        if sid != 'WS 004':
            kit.box(f'Hull sponson {sid} web', (x, y-s*(r+1)/2, -2), (2*r-2, r+2, 12), kit.steel, .3)
            g = Geo()
            for dx in (-r/2, 0, r/2):
                g.box((x+dx, y-s*(r+1)/2, -2), (.6, r+2.6, 10.5))
            g.emit(kit, f'Hull sponson {sid} web stiffeners', kit.grey, .05)


def build_spine(kit):
    spine = prism(kit, 'Hull aft machinery spine', SPINE, SPINE_SECTION, kit.steel)
    frame_seams(kit, spine, SPINE, SPINE_SECTION, [-60, -40, -34], 'spine frames')
    kit.hull.append(spine)
    x, y, z, r = REACTOR
    dome = sphere(kit, 'Hull aft reactor dome', (x, y, z), r, kit.grey)
    kit.hull.append(dome)
    top = DECK+2
    # Service ring round the dome's foot, bolted down.
    g = Geo()
    g.ring(circle(r+1.6, 32, x, y), circle(r-.6, 32, x, y), top-.1, top+1.4)
    g.emit(kit, 'Hull aft reactor service ring', kit.steel, .08)
    g = Geo()
    for i in range(16):
        a = 2*math.pi*(i+.5)/16
        g.box((x+(r+1.75)*math.cos(a), y+(r+1.75)*math.sin(a), top+.6), (.7, .7, 1.4))
    g.emit(kit, 'Hull aft reactor ring cleats', kit.grey)
    # Segmented dome: two latitude seams and three meridians, a crown cap.
    g = Geo()
    for deg in (35, 62):
        phi = math.radians(deg)
        g.torus(r*math.sin(phi)+.03, .13, lambda p, phi=phi: (p[0]+x, p[1]+y, p[2]+z+r*math.cos(phi)), 48, 5)
    for k in range(3):
        psi = k*math.pi/3
        g.torus(r+.03, .13, lambda p, psi=psi: (x+p[0]*math.cos(psi)-p[2]*math.sin(psi),
                                                 y+p[0]*math.sin(psi)+p[2]*math.cos(psi), z+p[1]), 48, 5)
    g.emit(kit, 'Hull aft reactor dome seams', kit.dark)
    kit.cylinder('Hull aft reactor crown cap', (x, y, z+r-.1), 2.2, .9, kit.steel, 'Z', 16, .08)
    # Radiator fin banks either side, forward of the dome, read as the
    # ribbed grey between the dome and the hull.
    for side, s in (('port', 1), ('starboard', -1)):
        g = Geo()
        g.box((-36.5, s*10.5, top+.2), (10.5, 6.8, .4))
        for k in range(6):
            g.box((-36.5, s*(7.75+k*1.1), top+1.5), (10.0, .3, 2.4))
        g.emit(kit, f'Hull aft {side} radiator fins', kit.grey, .03)


def build(kit):
    build_body(kit)
    build_tanks(kit)
    build_strakes(kit)
    build_gallery(kit)
    build_sponsons(kit)
    build_spine(kit)
    build_deck_fittings(kit)
