"""Pass 2: the detail manticore_pirate.png implies, laid on the pass 1 hull.

Read off the sprite at 6-10x (the hull has no lights layer, so glazing and
heat glow follow its shading and a low-tech destroyer's needs):
- Wings: two big raised red armour plates a side between the core and the
  launcher wells (the bright angular plates, rows 45-90), each a slab with
  its outboard edge falling; a dark trailing-edge strake running down the
  wing's swept trailing edge, with joints; the gunmetal rib that carries each
  outer pod (the dark spar from the core to the pod).
- Sensor domes: the tan bulbs at (5, +-35), on low plinths.
- Main ring collar: the dark segmented ring round the Mark IX (radius ~20),
  with radial ribs.
- Neck: transverse gunmetal ribs across the spine (rows 30-45) between the red
  neck rails (mt_hull).
- Core: a dorsal ridge from the bow ring to the main collar, seam bands, and
  flank viewports for the crew decks (low tech: few, small).
- Command dome: a glazed band across its forward face under a visor brow,
  a sensor mast and dish on its roof.
- Aft body: longitudinal machinery ribs and two heat-vent wells either side
  of the centreline (the sprite's dark slatted blocks, rows 125-140), and the
  canister-flak hatches at the hidden system slots WS 006-009.
Every part is named `Hull ...` or `Bridge ...`, and none crosses the
silhouette by more than a cell, so the top-view IoU holds.
"""
import math
import bpy
from mt_kit import boolean, facet_normal, plane_cut, prism, section, sym
from mt_hull import FALL, RAKE, wing_drop, ccw, droop, shear, BRIDGE_DECK, CORE_DECK, AFT_DECK, MAIN_WELL, WING_DECK

VIEWPORT_M = (1.0, .8)       # one viewport pane
BRIDGE_BAND_M = 1.1          # the dome's glazed band height


def strip(kit, name, points, width, z, height, mat):
    """A thin raised ribbon along a polyline in plan, its base at z."""
    verts, faces = [], []
    n = len(points)
    for i, (x, y) in enumerate(points):
        ax, ay = points[max(i-1, 0)]
        bx, by = points[min(i+1, n-1)]
        dx, dy = bx-ax, by-ay
        length = math.hypot(dx, dy)
        nx, ny = -dy/length*width/2, dx/length*width/2
        verts += [(x+nx, y+ny, z), (x-nx, y-ny, z), (x-nx, y-ny, z+height), (x+nx, y+ny, z+height)]
    for i in range(n-1):
        a, b = 4*i, 4*(i+1)
        faces += [(a, a+1, b+1, b), (a+1, a+2, b+2, b+1), (a+2, a+3, b+3, b+2), (a+3, a, b, b+3)]
    faces += [(0, 1, 2, 3), tuple(reversed(range(4*(n-1), 4*n)))]
    return kit.mesh(name, verts, faces, mat)


def seat(kit, x, y, fallback=0.0):
    hit = kit.surface(x, y)
    return hit[0].z if hit else fallback


def wing_z(y, deck=WING_DECK):
    """The wing deck's height at |y| (what `shear` will lower an unsheared part to)."""
    return deck-wing_drop(y)


def build(kit):
    vp = [m/kit.mpu for m in VIEWPORT_M]
    for side, s in (('port', 1), ('starboard', -1)):
        # --- wing armour plates: raised slabs, outboard edges falling with the deck ---
        for k, plan in enumerate(([(30, 25.5), (30, 31), (24, 34), (20, 50), (12, 52), (8, 30), (12, 25.5)],
                                  [(6, 27), (8, 38), (2, 52), (-6, 54), (-14, 46), (-12, 27)],
                                  [(19, 53.5), (10, 64), (3, 62.5), (-3, 56.5), (4, 53.5)]), 1):
            pts = ccw([(x, s*y) for x, y in plan])
            z0 = WING_DECK-.6   # unsheared; `shear` lays it on the drooping deck
            plate = shear(prism(kit, f'Hull wing {side} armour plate {k}', pts, section(z0, z0+2.8, .2, 1.0),
                                kit.red, .12))
            plane_cut(plate, f'{side} plate {k} outboard fall', (0, s*36, z0+2.8-wing_drop(36)-.3),
                      facet_normal((0, s), FALL))
            xf = max(x for x, _ in plan)
            plane_cut(plate, f'{side} plate {k} forward bevel', (xf-3.5, s*40, z0+2.8), facet_normal((1, -.35*s), RAKE))
            kit.hull.append(droop(plate))
        # Leading-edge armour (owner, round 6: angular armour on the arms, after
        # the Onslaught): a thick red plate along each arm's root leading edge,
        # its face raked steeply back and its top falling outboard.
        skirt = prism(kit, f'Hull wing {side} leading armour',
                      ccw([(42, s*20.5), (35, s*25.5), (32, s*31), (32, s*34), (25, s*34), (25, s*28), (33, s*20.5)]),
                      section(WING_DECK-7.0, WING_DECK+2.6, 2.0, 1.2), kit.red, .12)
        plane_cut(skirt, f'{side} skirt forward rake', (33, 0, WING_DECK+2.6), facet_normal((1, 0), RAKE))
        plane_cut(skirt, f'{side} skirt outboard fall', (0, s*27, WING_DECK+2.6), facet_normal((0, s), FALL))
        kit.hull.append(droop(shear(skirt)))
        # Trailing-edge strake: a dark gunmetal band down the swept trailing edge.
        te = [(8.5, 70.5), (-3, 58.5), (-16, 45.5)]
        for i in range(len(te)-1):
            (x0, y0), (x1, y1) = te[i], te[i+1]
            dx, dy = x1-x0, y1-y0
            ln = math.hypot(dx, dy)
            nx, ny = dy/ln*3.2, -dx/ln*3.2  # inboard offset
            pts = ccw([(x0, s*y0), (x1, s*y1), (x1-nx, s*(y1-ny)), (x0-nx, s*(y0-ny))])
            zt = WING_DECK
            droop(shear(prism(kit, f'Hull wing {side} trailing strake {i+1}', pts, section(zt-1.6, zt+.5, .1, .3),
                        kit.steel, .08)))
        # Pod spar: the gunmetal rib from the core shoulder to the outer pod.
        spar = strip(kit, f'Hull wing {side} pod spar', [(18, s*24), (21, s*40), (22, s*54)], 3.4, WING_DECK-.8,
                     1.9, kit.steel)
        droop(shear(spar))
        # Sensor dome on its plinth (the tan bulb at (5, +-35)).
        z = seat(kit, 5, s*35, WING_DECK)
        kit.cylinder(f'Hull wing {side} sensor plinth', (5, s*35, z+.4), 4.6, 1.2, kit.steel, vertices=12, bevel=.1)
        bpy.ops.mesh.primitive_uv_sphere_add(segments=16, ring_count=8, radius=3.8, location=(5, s*35, z+1.0))
        dome = bpy.context.object
        dome.scale.z = .8
        bpy.ops.object.transform_apply(scale=True)
        kit.finish(dome, f'Hull wing {side} sensor dome', kit.pale)
        # --- crew-deck viewports: one row down each wall of the bridge plinth ---
        for k, x in enumerate((-1, -5, -9, -15, -19), 1):
            for row, z in enumerate((CORE_DECK-.75,)):
                kit.box(f'Hull core {side} viewport {k}.{row+1}', (x, s*16.35, z), (vp[0], .3, vp[1]), kit.glass, 0)
        # --- aft body: machinery ribs and heat-vent wells ---
        for k, y in enumerate((27, 32), 1):
            kit.box(f'Hull aft {side} machinery rib {k}', (-40, s*y, seat(kit, -40, s*y, AFT_DECK)+.2), (26, 1.4, .9),
                    kit.steel, .1)
        zv = seat(kit, -42, s*8, CORE_DECK)
        kit.box(f'Hull aft {side} heat vent well', (-42, s*8, zv-.15), (10, 5.0, .5), kit.dark, 0)
        for k, y in enumerate((6.6, 8.0, 9.4), 1):
            kit.box(f'Hull aft {side} heat vent slat {k}', (-42, s*y, zv+.08), (8.5, .4, .25), kit.vent, 0)
        # --- canister-flak hatches at the hidden system slots ---
        for sid, (x, y) in (('WS 007' if s > 0 else 'WS 006', (36, 21)), ('WS 008' if s > 0 else 'WS 009', (-19, 40))):
            z = seat(kit, x, s*y, 5.0)
            kit.cylinder(f'Hull {sid} flak hatch', (x, s*y, z+.12), 2.4, .5, kit.steel, vertices=10, bevel=.08)
            kit.cylinder(f'Hull {sid} flak hatch well', (x, s*y, z+.4), 1.6, .1, kit.dark, vertices=10, bevel=0)

    # --- neck ribs across the glacis, each laid on the slope it crosses ---
    for k, x in enumerate((47, 49.6, 52.2, 54.8, 57.4, 60), 1):
        z0, z1 = seat(kit, x-1, 0), seat(kit, x+1, 0)
        rib = kit.box(f'Hull neck rib {k}', (x, 0, (z0+z1)/2+.3), (1.0, 16, .8), kit.steel, .12)
        rib.rotation_euler.y = math.atan2(z0-z1, 2)
    # --- main gun well: radial ribs round its lip, each seated on the deck it
    # crosses (the lip falls with the glacis ahead of the gun) ---
    r, d = MAIN_WELL
    for k in range(16):
        a = 2*math.pi*(k+.5)/16
        x, y = 21+(r+1.0)*math.cos(a), (r+1.0)*math.sin(a)
        ob = kit.box(f'Hull main ring rib {k+1}', (x, y, seat(kit, x, y, CORE_DECK)+.1), (1.6, .7, .5), kit.steel, .08)
        ob.rotation_euler.z = a
    # --- command dome: glazed band, visor brow, mast and dish ---
    arc = [(-1.6-6.5*(1-math.cos(a)), 9*math.sin(a)) for a in [math.radians(d) for d in range(-60, 61, 12)]]
    zg = BRIDGE_DECK+2.4
    strip(kit, 'Bridge glazing band', arc, .6, zg, BRIDGE_BAND_M/kit.mpu, kit.glass)
    strip(kit, 'Bridge visor brow', [(x+.5, y*1.05) for x, y in arc], .9, zg+BRIDGE_BAND_M/kit.mpu, .6, kit.steel)
    zr = seat(kit, -12, 0, BRIDGE_DECK+5)
    kit.cylinder('Bridge sensor mast', (-13, 0, zr+2.4), .45, 4.8, kit.steel, vertices=8, bevel=0)
    kit.cylinder('Bridge sensor dish', (-13, 0, zr+4.9), 1.8, .35, kit.plate, vertices=16, bevel=.05)
    kit.box('Bridge roof equipment', (-9, 3.5, zr+.4), (3, 2.2, 1.0), kit.steel, .1)
    # --- bow head: sensor well and the prow strake ---
