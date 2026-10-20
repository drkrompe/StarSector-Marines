"""Pass 2: the detail manticore_pirate.png implies, laid on the pass 1 hull.

Read off the sprite at 6-10x (the hull has no lights layer, so glazing and
heat glow follow its shading and a low-tech destroyer's needs):
- Wings and sensor domes: (round 11) open framework behind the leading-edge
  glacis, in mt_frames, the domes on platforms in it.
- Main ring collar: the dark segmented ring round the Mark IX (radius ~20),
  with radial ribs.
- Neck: transverse gunmetal ribs across the spine (rows 30-45) between the red
  neck rails (mt_hull).
- Core: a dorsal ridge from the bow ring to the main collar, seam bands, and
  flank viewports for the crew decks (low tech: few, small).
- Command dome: a glazed band across its forward face under a visor brow,
  a sensor mast and dish on its roof.
- Aft body: (round 10) its machinery and heat-vent bays are in mt_frames;
  here, the canister-flak hatches at the hidden system slots WS 006-009.
Every part is named `Hull ...` or `Bridge ...`, and none crosses the
silhouette by more than a cell, so the top-view IoU holds.
"""
import math
import bpy
from mt_kit import boolean, facet_normal, plane_cut, prism, section, sym
from mt_hull import FALL, RAKE, wing_drop, ccw, droop, shear, BRIDGE_DECK, CORE_DECK, AFT_DECK, MAIN_WELL, WING_DECK

# The port trailing edge (mt_hull.WING, round 9: one straight line): its pod end,
# the unit vector along it inboard, and the unit normal pointing inboard-forward.
TE_ROOT = (6.0, 72.5)
_dx, _dy = -17.5-6.0, 47-72.5
_ln = math.hypot(_dx, _dy)
SWEEP_T = (_dx/_ln, _dy/_ln)
SWEEP_N = (-SWEEP_T[1], SWEEP_T[0])

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
        # (Round 11: the wings behind the glacis are open framework; the sensor
        # domes stand on platforms in it, mt_frames.)
        # --- crew-deck viewports: one row down each wall of the bridge plinth ---
        for k, x in enumerate((-1, -5, -9, -15, -19), 1):
            for row, z in enumerate((CORE_DECK-.75,)):
                kit.box(f'Hull core {side} viewport {k}.{row+1}', (x, s*16.35, z), (vp[0], .3, vp[1]), kit.glass, 0)
        # (Round 10: the aft machinery and heat vents are exposed bays in mt_frames.)
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
