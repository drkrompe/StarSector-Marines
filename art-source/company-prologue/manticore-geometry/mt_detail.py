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
from mt_kit import boolean, facet_normal, plane_cut, prism, section, sweep, sym
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


GLAZE_Z = BRIDGE_DECK+1.7    # the glazing band's sill (round 14: lowered into the dome's wall)
GLAZE_DEPTH = .5             # how far the band is let into the wall
GLAZE_FRONT = -6.4           # the band wraps the dome's face forward of this x
MULLION_M = 1.0              # bridge mullion pitch
WINDOW_M = (1.5, .6)         # a crew-deck window, width x height (two panes)
WINDOW_XS = (-2.5, -6.5, -10.5, -14.5, -18.5, -22.5)
WINDOW_Z = CORE_DECK-.75     # window centre on the plinth's wall


def _wall_y(x):
    """The port plinth wall's |y| at x where the windows sit (it widens aft)."""
    return 15.99-(x+1)*.0223+.3   # round 15: the wall's armour plate stands .3 proud


RADAR_M = 3.2               # the bridge search radar's reflector diameter


def cabinet(kit, name, centre, z, dims):
    """A weatherproof equipment cabinet: a rounded box on a plinth, with a
    louvred face, a door seam and handles."""
    from mt_frames import struts
    x, y = centre
    w, d, h = dims
    kit.smooth(kit.box(name+' plinth', (x, y, z+.06), (w+.25, d+.25, .2), kit.dark, .05), segments=2)
    kit.smooth(kit.box(name, (x, y, z+.16+h/2), (w, d, h), kit.steel, .12), segments=3)
    lou, n = [], 6
    for k in range(n):
        zz = z+.35+k*(h-.45)/(n-1)
        lou.append(((x-w*.42, y+d/2+.02, zz), (x-w*.05, y+d/2+.02, zz), .03, .045))
    lou += [((x+w*.04, y+d/2+.01, z+.25), (x+w*.04, y+d/2+.01, z+h+.05), .015, .015),
            ((x+w*.3, y+d/2+.06, z+.16+h*.4), (x+w*.3, y+d/2+.06, z+.16+h*.65), .035, .035, 8)]
    kit.smooth(struts(kit, name+' louvres', lou, kit.plate), .01, 1)


def crew_windows(kit, side, s):
    from mt_frames import struts
    w, h = WINDOW_M[0]/kit.mpu, WINDOW_M[1]/kit.mpu
    cut, frame, mull = [], [], []
    for k, x in enumerate(WINDOW_XS, 1):
        y, z0, z1 = _wall_y(x), WINDOW_Z-h/2, WINDOW_Z+h/2
        x0, x1 = x-w/2, x+w/2
        cut.append(((x, s*(y-.4), WINDOW_Z), (x, s*(y+1.5), WINDOW_Z), w/2, h/2))
        kit.box(f'Hull core {side} viewport {k}.1', (x, s*(y-.36), WINDOW_Z), (w, .06, h), kit.glass, 0)
        f, yf = .09, s*(y+.03)
        frame += [((x0-f, yf, z0-f), (x1+f, yf, z0-f), f, f), ((x0-f, yf, z1+f), (x1+f, yf, z1+f), f, f),
                  ((x0-f, yf, z0-f), (x0-f, yf, z1+f), f, f), ((x1+f, yf, z0-f), (x1+f, yf, z1+f), f, f)]
        mull.append(((x, s*(y-.18), z0), (x, s*(y-.18), z1), .06, .09))
        hood = kit.box(f'Hull core {side} viewport {k}.1 hood', (x, s*(y+.16), z1+.24), (w+.5, .36, .14), kit.steel, .05)
        kit.smooth(hood, segments=2)
    cutter = struts(kit, f'Temporary {side} window cutter', cut, kit.dark)
    boolean(bpy.data.objects['Bridge command tier'], cutter)
    for ob in (struts(kit, f'Hull core {side} window frames', frame, kit.steel),
               struts(kit, f'Hull core {side} window mullions', mull, kit.steel)):
        kit.smooth(ob, .03, 2)


def bridge_glazing(kit):
    """Round 14: the dome's glazing band let into its wall along the face's own
    curve, dark glass at the back, a mullion every metre, a sill below and a
    rounded visor brow overhanging it."""
    from mt_hull import dome_plan
    from mt_frames import struts
    u = 1/kit.mpu
    z0, z1 = GLAZE_Z, GLAZE_Z+BRIDGE_BAND_M*u
    path = sorted((p for p in dome_plan(24) if p[0] > GLAZE_FRONT), key=lambda p: p[1])  # starboard to port
    # Left of travel (+y) is aft, into the dome: d > 0 is inside the wall.
    dome = bpy.data.objects['Bridge command dome']
    boolean(dome, sweep(kit, 'Temporary glazing cutter', path, [(-1.5, z0), (GLAZE_DEPTH, z0), (GLAZE_DEPTH, z1), (-1.5, z1)],
                        kit.dark, 1, 0))
    sweep(kit, 'Bridge glazing band', path, [(GLAZE_DEPTH-.1, z0), (GLAZE_DEPTH+.05, z0), (GLAZE_DEPTH+.05, z1),
                                             (GLAZE_DEPTH-.1, z1)], kit.glass, 1, 0)
    brow = sweep(kit, 'Bridge visor brow', path,
                 [(GLAZE_DEPTH+.2, z1-.02), (-.15, z1-.02), (-.45, z1+.05), (-.62, z1+.18), (-.62, z1+.34),
                  (-.45, z1+.5), (-.15, z1+.6), (GLAZE_DEPTH+.2, z1+.6)], kit.steel, 1, .04)
    kit.smooth(brow, .04, 2, 40)
    sill = sweep(kit, 'Bridge glazing sill', path, [(GLAZE_DEPTH, z0-.22), (-.32, z0-.22), (-.32, z0-.08),
                                                    (GLAZE_DEPTH, z0+.04)], kit.steel, 1, .03)
    kit.smooth(sill, .03, 2, 40)
    # mullions at an even pitch along the curve, standing in the recess
    seg = [math.hypot(b[0]-a[0], b[1]-a[1]) for a, b in zip(path, path[1:])]
    total, step = sum(seg), MULLION_M*u
    n = int(total/step)
    off = (total-n*step)/2
    bars, acc, i = [], 0.0, 0
    for k in range(n+1):
        t = off+k*step
        while i < len(seg)-1 and acc+seg[i] < t:
            acc += seg[i]
            i += 1
        f = (t-acc)/seg[i]
        (ax, ay), (bx, by) = path[i], path[i+1]
        px, py = ax+(bx-ax)*f, ay+(by-ay)*f
        tx, ty = (bx-ax)/seg[i], (by-ay)/seg[i]
        ix, iy = -ty, tx                      # left of travel: into the wall
        cx, cy = px+ix*GLAZE_DEPTH*.45, py+iy*GLAZE_DEPTH*.45
        bars.append(((cx, cy, z0), (cx, cy, z1), .07, .11))
    kit.smooth(struts(kit, 'Bridge glazing mullions', bars, kit.steel), .025, 2)


def seat(kit, x, y, fallback=0.0):
    hit = kit.surface(x, y)
    return hit[0].z if hit else fallback


def wing_z(y, deck=WING_DECK):
    """The wing deck's height at |y| (what `shear` will lower an unsheared part to)."""
    return deck-wing_drop(y)


def build(kit):
    for side, s in (('port', 1), ('starboard', -1)):
        # (Round 11: the wings behind the glacis are open framework; the sensor
        # domes stand on platforms in it, mt_frames.)
        # --- crew-deck windows: one row down each wall of the bridge plinth.
        # Round 14 (owner: they "read as weird rectangle lights"): each is let
        # into the wall, framed, split by a mullion and hooded, with dark
        # glass set back in the recess. ---
        crew_windows(kit, side, s)
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
    bridge_glazing(kit)
    # Round 14 (owner: "strange low poly radar dish"): a 3.2 m search dish on a
    # yoke and pedestal (mt_fittings), and an equipment cabinet with louvres.
    from mt_fittings import dish
    dish(kit, 'Bridge sensor dish', (-12, 0), seat(kit, -12, 0, BRIDGE_DECK+5), RADAR_M/2/kit.mpu, 0, 12,
         kit.plate, kit.steel, pedestal=.8)
    cabinet(kit, 'Bridge roof equipment', (-8.6, 4.6), seat(kit, -8.6, 4.6, BRIDGE_DECK+5), (2.6, 1.8, 1.0))
    kit.smooth(bpy.data.objects['Bridge command dome'], .14)
    kit.smooth(bpy.data.objects['Bridge command tier'], .3)
    # --- bow head: sensor well and the prow strake ---
