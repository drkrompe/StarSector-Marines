"""Pass 2: the detail the sprites imply, laid on the pass 1 structure.

Read off wolf_pirates.png at 20x beside wolf_base.png (same hull, unpainted),
in model units (sprite column c, row r -> X 53-r..54-r, Y 25-c..26-c):
- Head: two dark sensor eyes beside the bridge canopy (row 14, cols 23 / 28),
  two pale shoulder ports aft of it (rows 18-19, cols 22 / 29: lit in both
  sprites, so glazed), a dark slot across the spine's root (row 20), and the
  crimson cheek strips down the head's flanks (rows 14-23, cols 18-21 / 30-33).
- Neck: a second, smaller canopy on the spine (rows 33-34) aft of the
  sensor dome.
- Outriggers: a pale sensor head at each forward end (rows 12-14); a vent
  grille on each outboard flank, glowing orange only to starboard (rows
  16-18, cols 38-40; both sprites light only that side).
- Main body: the two round emitter wells at X 6, Y +-9 (rows 46-50, cols
  16-18 / 33-35), a grey rim round a dark well, the displacer's emitters.
- Missile pods: grey lattice: raised ribs across the pod deck with dark
  wells between (rows 39-48).
- Aft: the dark hatch well on the spine (rows 66-68) in a grey frame, and
  grey link frames between the waist and the domes (rows 60-62, Y +-12).
- Engine block (rows 74-94): a raised collar across the spine's root (row
  73, the light arc), two light longitudinal rails at Y +-5.5 and the
  centre stack between them, raised drive housings over the outer drives
  (Y +-12) with dark troughs between, read off the light and dark columns.
- Drives: a bezel round each bell where it leaves the block's aft face.
- Pirate wear in form, not paint: "heavily modified - and poorly maintained"
  (the skin). Mismatched plates welded over the two armour domes and the
  wing roots, with weld beads round the domes' feet.
- Seams: dark joint bands across the crimson decks where the sections meet.
Nothing here moves the pass 1 silhouette (the overlay is re-run after every
change; budget -0.002 IoU).
"""
import math
import bmesh
import bpy
from mathutils import Matrix, Vector


def ccw(poly):
    area = sum(x0*y1-x1*y0 for (x0, y0), (x1, y1) in zip(poly, poly[1:]+poly[:1]))
    return poly if area > 0 else list(reversed(poly))


def z_at(kit, x, y, fallback=0.0):
    hit = kit.surface(x, y)
    return hit[0].z if hit else fallback


def on_surface(kit, name, x, y, dims, mat, yaw=0.0, lift=0.0, bevel=.06):
    """A box laid on the hull at (x, y), tilted to the surface normal there."""
    hit = kit.surface(x, y)
    if hit is None:
        return None
    p, n = hit
    ob = kit.box(name, (0, 0, 0), dims, mat, bevel)
    rot = n.to_track_quat('Z', 'X').to_matrix().to_4x4() @ Matrix.Rotation(math.radians(yaw), 4, 'Z')
    ob.matrix_world = Matrix.Translation(p+n*(dims[2]/2-.12+lift)) @ rot
    return ob


def ribbon(kit, name, pts, width, height, mat, sink=.05):
    """A thin strip draped over the hull along plan points [(x, y), ...]."""
    verts, faces = [], []
    n = len(pts)
    for i, (x, y) in enumerate(pts):
        ax, ay = pts[max(i-1, 0)]
        bx, by = pts[min(i+1, n-1)]
        dx, dy = bx-ax, by-ay
        L = math.hypot(dx, dy)
        nx, ny = -dy/L*width/2, dx/L*width/2
        za = z_at(kit, x+nx, y+ny)-sink
        zb = z_at(kit, x-nx, y-ny)-sink
        verts += [(x+nx, y+ny, za), (x-nx, y-ny, zb), (x-nx, y-ny, zb+height+sink), (x+nx, y+ny, za+height+sink)]
    for i in range(n-1):
        a, b = 4*i, 4*(i+1)
        faces += [(a, a+1, b+1, b), (a+1, a+2, b+2, b+1), (a+2, a+3, b+3, b+2), (a+3, a, b, b+3)]
    faces += [(0, 1, 2, 3), tuple(reversed(range(4*(n-1), 4*n)))]
    return kit.mesh(name, verts, faces, mat)


def line(a, b, step=.8):
    (ax, ay), (bx, by) = a, b
    k = max(2, int(math.hypot(bx-ax, by-ay)/step)+1)
    return [(ax+(bx-ax)*i/(k-1), ay+(by-ay)*i/(k-1)) for i in range(k)]


def build(kit):
    red, steel, dark, glass = kit.red, kit.steel, kit.dark, kit.glass
    sides = (('port', 1), ('starboard', -1))

    # --- Head. ---
    for side, s in sides:
        z = z_at(kit, 40.2, s*2.3, 4)
        kit.box(f'Hull head {side} sensor eye', (40.2, s*2.3, z), (1.2, 1.1, .5), dark, 0)
        kit.cylinder(f'Hull head {side} sensor eye lens', (40.2, s*2.3, z+.2), .35, .12, glass, 'Z', 10, 0)
        z = z_at(kit, 35.6, s*3.4, 4)
        kit.box(f'Hull head {side} shoulder port', (35.6, s*3.4, z), (1.6/kit.mpu*.6, 1.0/kit.mpu, .3), glass, 0)
        ribbon(kit, f'Hull head {side} cheek strip', line((41.2, s*5.6), (31.5, s*6.4)), 1.8, .35, red)
    z = z_at(kit, 33.6, 0, 5)
    kit.box('Hull spine root slot', (33.6, 0, z), (.8, 2.4, .5), dark, 0)

    # --- Neck: the second canopy. ---
    z = z_at(kit, 20.4, 0, 5)
    kit.cylinder('Hull neck aft canopy frame', (20.4, 0, z+.1), 1.9, .5, steel, 'Z', 12, .06)
    bpy.ops.mesh.primitive_uv_sphere_add(segments=12, ring_count=6, radius=1.0, location=(20.4, 0, z+.35))
    ob = bpy.context.object
    ob.scale = (1.5, 1.4, .8)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    kit.finish(ob, 'Hull neck aft canopy glazing', glass)

    # --- Outriggers: sensor heads, flank vent grilles (glowing to starboard). ---
    for side, s in sides:
        z = z_at(kit, 41.4, s*13.4, 3)
        kit.box(f'Hull {side} outrigger sensor head', (41.0, s*13.4, z+.2), (3.0, 2.6, 1.0), steel, .12)
        kit.cylinder(f'Hull {side} outrigger sensor lens', (42.6, s*13.4, z+.25), .7, .4, glass, 'X', 12, 0)
        kit.box(f'Hull {side} outrigger vent well', (37.2, s*15.95, .6), (4.4, .5, 2.4), dark, 0)
        for k, x in enumerate((35.8, 36.7, 37.6, 38.5), 1):
            kit.box(f'Hull {side} outrigger vent slat {k}', (x, s*16.05, .6), (.35, .4, 2.2),
                    kit.vent if s < 0 else steel, 0)

    # --- Main body: emitter wells. ---
    for side, s in sides:
        z = z_at(kit, 6, s*9, 3)
        kit.cylinder(f'Hull main {side} emitter rim', (6, s*9, z+.05), 2.4, .7, steel, 'Z', 16, .1)
        kit.cylinder(f'Hull main {side} emitter well', (6, s*9, z+.42), 1.5, .08, dark, 'Z', 16, 0)
        kit.cylinder(f'Hull main {side} emitter core', (6, s*9, z+.35), .7, .25, steel, 'Z', 12, 0)

    # --- Missile pods: lattice ribs and dark wells. ---
    for side, s in sides:
        yc = s*19.2
        for k, x in enumerate((6.0, 8.6, 11.2, 13.4), 1):
            z = z_at(kit, x, yc, 3)
            kit.box(f'Pod {side} missile rib {k}', (x, yc, z+.2), (.8, 9.0, .7), steel, .1)
        for k, x in enumerate((7.3, 9.9, 12.3), 1):
            z = z_at(kit, x, yc, 3)
            kit.box(f'Pod {side} missile well {k}', (x, yc, z+.02), (1.6, 7.6, .1), dark, 0)

    # --- Aft: hatch well, link frames. ---
    z = z_at(kit, -12.6, 0, 5)
    kit.box('Hull aft hatch frame', (-12.6, 0, z+.1), (4.6, 4.0, .5), steel, .1)
    kit.box('Hull aft hatch well', (-12.6, 0, z+.36), (3.0, 2.6, .1), dark, 0)
    for side, s in sides:
        ribbon(kit, f'Hull aft {side} link frame', line((-5.6, s*11.5), (-9.4, s*12.6)), 1.4, .45, steel)

    # --- Engine block: collar, rails, centre stack, drive housings, troughs. ---
    ribbon(kit, 'Hull engine collar', [(-18.6+.6*math.cos(a)*0, 7.2*math.sin(a)) for a in
                                         [math.radians(t) for t in range(-90, 91, 15)]], 1.3, .7, steel)
    for side, s in sides:
        ribbon(kit, f'Hull engine {side} rail', line((-20.5, s*5.6), (-36.5, s*5.6)), 1.2, .6, steel)
        hz = z_at(kit, -30, s*12, 4)
        hood = kit.box(f'Hull engine {side} drive housing', (-29.8, s*12.0, hz+.5), (13.0, 6.4, 1.6), steel, .3)
        for k, x in enumerate((-25.0, -28.0, -31.0, -34.0), 1):
            kit.box(f'Hull engine {side} housing fin {k}', (x, s*12.0, hz+1.4), (.5, 5.6, .5), steel, 0)
        for k, x in enumerate((-24.5, -31.5), 1):
            z = z_at(kit, x, s*8.6, 4)
            kit.box(f'Hull engine {side} trough {k}', (x, s*8.6, z+.02), (5.0, 1.8, .1), dark, 0)
    for k, x in enumerate((-22.5, -26.0, -29.5, -33.0), 1):
        z = z_at(kit, x, 0, 5)
        kit.box(f'Hull engine centre stack {k}', (x, 0, z+.3), (2.4, 3.6, .9), steel, .12)

    # --- Pirate armour: mismatched plates welded over the domes and wing roots. ---
    import wf_kit
    for side, s in sides if wf_kit.VARIANT == 'pirate' else ():
        x, y = -13.6, s*12.4
        on_surface(kit, f'Hull {side} dome patch plate 1', x+1.6, y+s*1.2, (3.2, 2.6, .35), red, 18*s)
        on_surface(kit, f'Hull {side} dome patch plate 2', x-2.0, y-s*1.4, (2.4, 3.0, .35), steel, -10*s)
        on_surface(kit, f'Hull wing {side} patch plate', 0.6, s*19.6, (3.4, 2.8, .35), red, 30*s)

    # --- Seams across the crimson decks where sections meet. ---
    for k, (a, b) in enumerate((((16.4, 15.8), (16.4, -15.8)), ((-4.6, 11.0), (-4.6, -11.0)),
                                ((30.6, 13.6), (30.6, -13.6))), 1):
        ribbon(kit, f'Hull seam {k}', [p for p in line(a, b) if abs(p[1]) > 4.2 or k == 2], .3, .08, dark)

    # --- Drive bezels round each bell where it leaves the block's aft face. ---
    import wf_engines
    for i, (x, y, w, _) in enumerate(wf_engines.ENGINES, 1):
        rm = wf_engines.mouth_r(w)
        bpy.ops.mesh.primitive_torus_add(major_radius=rm+1.05, minor_radius=.5, major_segments=20, minor_segments=6,
                                         location=(x+.5, y, wf_engines.Z), rotation=(0, math.pi/2, 0))
        kit.finish(bpy.context.object, f'Engine {i} bezel', steel)
