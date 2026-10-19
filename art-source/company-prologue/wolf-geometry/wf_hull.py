"""The Wolf (P)'s structure: bow fork, head and outriggers, neck, main body
with its missile pods, aft body with the armour domes, engine block.

Read off wolf_pirates.png (52x96, .ship centre (26, 42) from the bottom edge;
sprite column c, row r covers X 53-r..54-r and Y 25-c..26-c), checked
against wolf_base.png, which shows the same hull unpainted:
- Bow fork (rows 1-10): a gunmetal claw of five tines, the outer pair
  heaviest (Y +-6.5..10), an inner pair (Y +-2.5..4.5) and the WS 004
  hardpoint channel on the centreline, all springing from a solid base
  across Y +-10 at rows 8-10.
- Head (rows 11-23): a grey block Y +-10 with crimson cheek plates
  (Y +-5..9.5), a white bridge canopy on the centreline (rows 13-16), and a
  grey outrigger either side out to Y +-16 (cols 10-15, 36-41). The
  starboard outrigger carries an orange heat glow (rows 16-18, cols 38-40;
  the port one does not: the sprite is asymmetric there).
- Neck (rows 24-36): crimson flanks to Y +-14, notched at row 25, a grey
  spine Y +-4.5 with a domed sensor housing (rows 26-30) and a second
  canopy (rows 33-34), and amber hull lamps at rows 28 (cols 14, 37).
- Main body (rows 37-58): the widest section. A crimson core Y +-17 round
  the WS 001 turret ring (rows 39-48, radius 5 about X 11), two round
  emitter wells at X 6, Y +-9 (the same in the base sprite), grey missile
  pods Y +-13.5..25 (rows 39-49) round the WS 005 / WS 006 hardpoints, and
  crimson wing-root lobes behind them (rows 49-57) out to Y +-23.
- Waist and aft body (rows 58-73): pinched to Y +-15.5 at row 61, then two
  crimson armour domes (rows 63-72, centre X -13.5, Y +-12.5) where the
  base hull's WS 002 / WS 003 turrets stand: the pirate skin removes those
  slots and paints armour over the rings. A grey centre spine with a dark
  hatch well (rows 66-68).
- Engine block (rows 74-94): grey machinery Y +-14..18 carrying the three
  HIGH_TECH drives on its aft face.
"""
import math
import bmesh
import bpy
from wf_kit import facet_normal, plane_cut, prism, section, sym, revolve_x

FORK = sym([(47.5, 9.4), (43, 9.4), (43, 9.0)])
OUTER_TINE = [(53.8, 6.4), (53.8, 8.6), (51, 10), (44, 10), (44, 5.3), (51, 5.3)]
HEAD = sym([(43.6, 8.5), (42.5, 10), (31, 10), (30.5, 9)])
OUTRIGGER = [(43.5, 12.4), (43.5, 13.9), (42.0, 15.3), (40.6, 16.2), (32.5, 16.2), (30.6, 15.8), (30.6, 10.4),
             (42, 10.4)]
NECK = sym([(31.5, 14.2), (30.4, 13.0), (29, 13.0), (27.8, 14.6), (19, 14.9), (17, 16.4), (15.5, 16.4)])
MAIN = sym([(18.6, 14.6), (17.3, 17.2), (5.5, 17.5), (4.2, 23.6), (1.5, 23.6), (-0.6, 22.6), (-2.2, 21.0),
            (-3.4, 19.0), (-5.0, 17.6), (-6.4, 16.0), (-8.5, 15.6)])
POD = [(14.6, 18.0), (14.6, 23.8), (13.6, 24.7), (5.4, 24.7), (4.4, 23.8), (4.4, 15.0), (6.0, 13.6),
       (13.0, 13.6)]
AFT = sym([(-5, 11), (-9, 15.5), (-19.5, 15.6), (-21, 13)])
ENGINE_BLOCK = sym([(-18.5, 16.3), (-21, 15.0), (-23.5, 13.9), (-27, 14.6), (-30, 15.4), (-33.6, 16.6),
                    (-35, 16.0), (-37.3, 13), (-38.6, 10.5), (-38.8, 1)])
DOME = (-13.6, 12.4, 5.2)            # centre X, |Y|, plan radius of the armour domes

# Heights (proposed art; the game gives none). One keel chamfer, one wall,
# one shoulder chamfer per section.
FORK_Z = (-6.5, 4.5)
HEAD_Z = (-11.0, 7.0)
OUTRIGGER_Z = (-8.0, 5.5)
NECK_Z = (-12.5, 6.0)
MAIN_Z = (-14.5, 6.0)
POD_Z = (-9.5, 5.0)
AFT_Z = (-14.0, 6.0)
ENGINE_Z = (-13.0, 7.5)
SPINE_TOP = 8.5


def ccw(poly):
    area = sum(x0*y1-x1*y0 for (x0, y0), (x1, y1) in zip(poly, poly[1:]+poly[:1]))
    return poly if area > 0 else list(reversed(poly))


def mirror(poly):
    return ccw([(x, -y) for x, y in poly])


def sides(plan):
    return (('port', 1, ccw(plan)), ('starboard', -1, mirror(plan)))


def dome(kit, name, x, y, r, z0, h, mat, n=16, rings=4):
    """A flattened spherical cap of plan radius r rising h above z0, faceted."""
    verts, faces = [], []
    for k in range(rings+1):
        a = (math.pi/2)*k/rings
        rr, zz = r*math.cos(a), z0+h*math.sin(a)
        for j in range(n if k < rings else 1):
            t = 2*math.pi*j/n
            verts.append((x+rr*math.cos(t), y+rr*math.sin(t), zz))
    for k in range(rings-1):
        for j in range(n):
            j2 = (j+1) % n
            faces.append((k*n+j, k*n+j2, (k+1)*n+j2, (k+1)*n+j))
    top = rings*n
    faces += [((rings-1)*n+j, (rings-1)*n+(j+1) % n, top) for j in range(n)]
    # Skirt down to the floor so the cap is a closed solid seated in the deck.
    base = len(verts)
    verts += [(x+r*math.cos(2*math.pi*j/n), y+r*math.sin(2*math.pi*j/n), z0-2.0) for j in range(n)]
    faces += [(base+j, base+(j+1) % n, (j+1) % n, j) for j in range(n)]
    faces.append(tuple(reversed(range(base, base+n))))
    return kit.mesh(name, verts, faces, mat)


def shear(ob, y0, k, top_only=True):
    """Lower vertices outboard of |Y| y0 by k per unit, so a deck crowns over the
    spine and falls away to the flanks (a crease at |Y| = y0 keeps faces planar)."""
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    for s in (1, -1):
        bmesh.ops.bisect_plane(bm, geom=bm.verts[:]+bm.edges[:]+bm.faces[:], plane_co=(0, s*y0, 0),
                               plane_no=(0, 1, 0))
    for v in bm.verts:
        if (not top_only or v.co.z > 0) and abs(v.co.y) > y0:
            v.co.z -= k*(abs(v.co.y)-y0)
    bm.to_mesh(ob.data)
    bm.free()
    ob.data.update()
    return ob


def lobe(kit, name, centre, radii, mat, yaw=0.0, segments=16, rings=8):
    """A faceted ellipsoid, mostly buried in the hull it rounds out: the curved
    crimson forms the sprite's highlights show (a high-tech hull)."""
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments, ring_count=rings, radius=1, location=centre)
    ob = bpy.context.object
    ob.scale = radii
    ob.rotation_euler = (0, 0, math.radians(yaw))
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    return kit.finish(ob, name, mat)


def seat(kit, x, y, fallback):
    hit = kit.surface(x, y)
    return hit[0].z if hit else fallback


def build(kit):
    red, steel, dark = kit.red, kit.steel, kit.dark
    # --- Bow fork: the base and its five tines. ---
    fork = prism(kit, 'Hull bow fork base', ccw(FORK), section(*FORK_Z, 1.8, 1.4), steel)
    plane_cut(fork, 'fork top rake', (46, 0, FORK_Z[1]), facet_normal((1, 0), 25))
    tines = []
    for side, s, plan in sides(OUTER_TINE):
        t = prism(kit, f'Hull bow {side} outer tine', plan, section(-4.5, 4.2, .9, 1.1), steel)
        plane_cut(t, f'{side} outer tine tip rake', (50, 0, 4.2), facet_normal((1, 0), 18))
        plane_cut(t, f'{side} outer tine chin', (50, 0, -4.5), (math.sin(math.radians(20)), 0,
                                                              -math.cos(math.radians(20))))
        tines.append(t)
    # The sprite's inner tines (Y +-2..4) are the WS 004 mining blaster's prongs
    # (wf_mounts): the gun is the claw's middle, sunk in a bay in the fork base.

    # --- Head, outriggers. ---
    head = shear(prism(kit, 'Hull head', ccw(HEAD), section(*HEAD_Z, 3.0, 2.4), steel), 4.0, .22)
    plane_cut(head, 'head brow rake', (41, 0, HEAD_Z[1]), facet_normal((1, 0), 30))
    plane_cut(head, 'head chin rake', (41, 0, HEAD_Z[0]), (.5, 0, -.866))
    outriggers = {}
    for side, s, plan in sides(OUTRIGGER):
        o = prism(kit, f'Hull {side} outrigger', plan, section(*OUTRIGGER_Z, 2.0, 1.6), steel)
        plane_cut(o, f'{side} outrigger nose rake', (41.5, s*13, OUTRIGGER_Z[1]), facet_normal((1, 0), 34))
        plane_cut(o, f'{side} outrigger outer shoulder', (36, s*15, OUTRIGGER_Z[1]), facet_normal((0, s), 35))
        outriggers[side] = o

    # --- Neck. ---
    neck = shear(prism(kit, 'Hull neck', ccw(NECK), section(*NECK_Z, 3.4, 2.8), red), 4.5, .2)
    for s in (1, -1):
        plane_cut(neck, f'neck {s} shoulder', (24, s*10.5, NECK_Z[1]), facet_normal((0, s), 26))

    # --- Main body, pods. ---
    main = shear(prism(kit, 'Hull main body', ccw(MAIN), section(*MAIN_Z, 4.2, 3.2), red), 6.0, .15)
    for s in (1, -1):
        plane_cut(main, f'main {s} wing-root fall', (3, s*15.5, MAIN_Z[1]), facet_normal((0, s), 17))
    plane_cut(main, 'main fore shoulder', (17.5, 0, MAIN_Z[1]), facet_normal((1, 0), 30))
    pods = {}
    for side, s, plan in sides(POD):
        p = prism(kit, f'Pod {side} missile body', plan, section(*POD_Z, 2.2, 1.8), steel)
        plane_cut(p, f'{side} pod outer shoulder', (10, s*24, POD_Z[1]), facet_normal((0, s), 35))
        pods[side] = p

    # Rounded hull where both sprites carry round highlights: the neck flanks
    # (rows 24-36, long curved bands), a central hump aft of the turret ring
    # (rows 50-60, the base sprite's brightest dome) and the wing-root arms that
    # run out to the pods (rows 49-57). Few facets: crisp plates, not bubbles.
    lobes = [lobe(kit, 'Hull centre hump', (-1.6, 0, 3.8), (7.0, 7.4, 3.6), red, 0, 12, 6)]
    for side, s in (('port', 1), ('starboard', -1)):
        lobes.append(lobe(kit, f'Hull neck {side} flank', (23.5, s*9.4, 3.4), (7.8, 4.6, 2.9), red, s*-4, 12, 6))
        lobes.append(lobe(kit, f'Hull wing {side} root', (0.4, s*17.8, 2.4), (4.6, 5.6, 3.0), red, s*-35, 12, 6))
    kit.hull += lobes

    # --- Aft body, armour domes, engine block. ---
    aft = shear(prism(kit, 'Hull aft body', ccw(AFT), section(*AFT_Z, 3.8, 3.0), red), 5.0, .16)
    kit.hull.append(aft)
    domes = []
    x, y, r = DOME
    import wf_kit
    for side, s in (('port', 1), ('starboard', -1)) if wf_kit.VARIANT == 'pirate' else ():
        # The pirate skin's armour over the removed WS 002 / WS 003 rings; the base
        # hull keeps its turrets there (wf_mounts).
        z0 = seat(kit, x, s*y, AFT_Z[1])
        domes.append(dome(kit, f'Hull {side} armour dome', x, s*y, r, z0-.6, 3.4, red))
    if wf_kit.VARIANT == 'merchant':
        # The base hull's aft turret wells (wolf_base.png rows 63-72: round rings
        # reaching Y +-18, the silhouette the pirate domes keep): a full-depth
        # armoured drum built into each flank, keel to a level ring deck, so the
        # WS 002 / WS 003 turrets stand on structure as deep as the hull, not on
        # its sloping edge.
        for side, s in (('port', 1), ('starboard', -1)):
            bpy.ops.mesh.primitive_cylinder_add(vertices=24, radius=r+.4, depth=AFT_Z[1]+1.2-(AFT_Z[0]+.4),
                                                location=(x, s*y, (AFT_Z[1]+1.2+AFT_Z[0]+.4)/2))
            drum = bpy.context.object
            kit.finish(drum, f'Hull {side} turret foundation', red, .3)
            ring_z = AFT_Z[1]+1.2
            kit.cylinder(f'Hull {side} turret foundation ring', (x, s*y, ring_z+.15), r-.3, .3, steel, 'Z', 24, .08)
            domes.append(drum)
    eng = shear(prism(kit, 'Hull engine block', ccw(ENGINE_BLOCK), section(*ENGINE_Z, 3.4, 2.8), steel), 5.0, .2)
    plane_cut(eng, 'engine block aft rake', (-33, 0, ENGINE_Z[1]), facet_normal((-1, 0), 36))

    # --- Spine: the grey centre structure from the engine block to the head. ---
    spine = prism(kit, 'Hull dorsal spine fore', ccw(sym([(32, 4.2), (17.5, 4.6), (16.2, 3.4)])),
                  section(0, SPINE_TOP, 0, 1.4), steel)
    plane_cut(spine, 'spine fore rake', (29, 0, SPINE_TOP), facet_normal((1, 0), 35))
    plane_cut(spine, 'spine fore aft rake', (25.0, 0, SPINE_TOP), facet_normal((-1, 0), 40))
    spine_aft = prism(kit, 'Hull dorsal spine aft', ccw(sym([(-2.5, 3.0), (-6, 3.6), (-33, 3.6)])),
                      section(0, SPINE_TOP-.6, 0, 1.3), steel)
    plane_cut(spine_aft, 'spine aft rake', (-29, 0, SPINE_TOP-.6), facet_normal((-1, 0), 30))
    plane_cut(spine_aft, 'spine aft fore rake', (-4, 0, SPINE_TOP-.6), facet_normal((1, 0), 40))
    # Bridge canopy on the head's centreline (rows 13-16, the white dome) and the
    # neck's domed sensor housing (rows 26-30) with its aft canopy (rows 33-34).
    zc = seat(kit, 39.5, 0, HEAD_Z[1])
    canopy = dome(kit, 'Hull bridge canopy frame', 39.6, 0, 3.4, zc-.3, 2.0, steel, 12, 3)
    glass = dome(kit, 'Hull bridge canopy glazing', 40.2, 0, 2.6, zc-.1, 2.0, kit.glass, 12, 3)
    zs = seat(kit, 26.5, 0, SPINE_TOP)
    sensor = dome(kit, 'Hull neck sensor dome', 26.5, 0, 3.0, zs-.4, 2.2, steel, 14, 3)
    kit.hull += [canopy, sensor]
    keel = prism(kit, 'Hull ventral keel', ccw(sym([(27, 3.2), (24, 5.0), (-16, 5.0), (-20, 3.6)])),
                 section(-18.0, -13.0, 1.8, 0), steel)
    plane_cut(keel, 'keel fore rake', (24, 0, -18.0), (.5, 0, -.866))
    plane_cut(keel, 'keel aft rake', (-15, 0, -18.0), (-.5, 0, -.866))
    kit.hull += [fork, *tines, head, *outriggers.values(), neck, main, *pods.values(), aft, *domes, eng, spine, spine_aft]
    return keel
