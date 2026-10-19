"""The five drives: whole drive units, the bell sunk into a drive can.

lasher.ship has five LOW_TECH engine slots, all angle 180, Z 0 here:
  Engine 1  (-34,   0) width 16 length 64   main drive, in the spine's tail
  Engine 2  (-28,  14) width  8 length 32   port nacelle, inboard
  Engine 3  (-26,  19) width  8 length 32   port nacelle, outboard
  Engine 4  (-28, -13) width  8 length 32   starboard nacelle, inboard
  Engine 5  (-26, -18) width  8 length 32   starboard nacelle, outboard
Engine i is the .ship's slot i-1. The slot point is the bell mouth (the
exhaust starts there); the bell narrows forward into its can, which runs
forward into the spine or nacelle. Mouth radius 0.36 x width: the sprite's
tail (rows 89-94) narrows to Y +-4 at X -33, so the main bell is a little
under the slot width.

Names follow the standing decision: `Engine <i> drive can`, `Engine <i>
aft-facing bell`, `Engine <i> mouth lip`, `Engine <i> dark throat`,
`Engine <i> exhaust anchor` (an empty at the slot, +X down the exhaust) and
`Engine <i> drive glow anchor` (an empty at the throat, +X aft) for the glow
inside the bell. lasher_lights.png shows no drive glow at rest.
"""
import bpy
from ls_kit import boolean, revolve_x

ENGINES = [(-34, 0, 16, 64), (-28, 14, 8, 32), (-26, 19, 8, 32), (-28, -13, 8, 32), (-26, -18, 8, 32)]
Z = 0.0
MOUTH = .30    # mouth radius / slot width
THROAT = .62   # throat radius / mouth radius
LENGTH = .5    # bell length / slot width
WALL = .06     # wall thickness / slot width


def mouth_r(w):
    return MOUTH*w


HOSTS = {1: 'Hull spine', 2: 'Hull port engine nacelle', 3: 'Hull port engine nacelle',
         4: 'Hull starboard engine nacelle', 5: 'Hull starboard engine nacelle'}


def build(kit):
    for i, (x, y, w, length) in enumerate(ENGINES, 1):
        # Open the host's aft face round the bell (applied, never left live).
        host = bpy.data.objects[HOSTS[i]]
        rm, wall = mouth_r(w), WALL*w
        cutter = revolve_x(kit, f'Temporary Engine {i} bell well cutter',
                           [(x-2, 0), (x-2, rm+.9*wall), (x+LENGTH*w, THROAT*rm+.9*wall), (x+LENGTH*w, 0)],
                           y, Z, kit.dark, 16)
        boolean(host, cutter)
        rm = mouth_r(w)
        rt = THROAT*rm
        xt = x+LENGTH*w
        wall = WALL*w
        can_front = -20.0 if w >= 12 else -19.0
        can_r = rm+wall+.25*w/8
        n = 16 if w >= 12 else 12
        revolve_x(kit, f'Engine {i} drive can', [(xt-.2, 0), (xt-.2, can_r), (can_front, can_r*.92),
                                                (can_front, 0)], y, Z, kit.steel, n)
        revolve_x(kit, f'Engine {i} aft-facing bell', [(xt, rt), (x, rm), (x, rm+wall), (xt, rt+wall)],
                  y, Z, kit.steel, n)
        revolve_x(kit, f'Engine {i} mouth lip', [(x+.35*wall, rm-.2*wall), (x-.25*wall, rm), (x-.25*wall, rm+1.6*wall),
                                                (x+.6*wall, rm+1.8*wall)], y, Z, kit.accent, n)
        kit.cylinder(f'Engine {i} dark throat', (xt+.1, y, Z), rt+.05, .3, kit.dark, 'X', n, 0)
        kit.empty(f'Engine {i} exhaust anchor', (x, y, Z), 180, size=w/4)
        kit.empty(f'Engine {i} drive glow anchor', (xt-.05, y, Z), 180, size=w/6)


def records():
    return [{'index': i, 'slot': i-1, 'objects': f'Engine {i} ...', 'anchor': f'Engine {i} exhaust anchor',
             'glow_anchor': f'Engine {i} drive glow anchor',
             'location': [x, y, Z], 'width': w, 'length': length, 'contrailSize': 64, 'style': 'LOW_TECH',
             'angle': 180, 'exhaust_direction': [-1, 0, 0], 'mouth_radius': round(mouth_r(w), 2),
             'bell': f'Engine {i} aft-facing bell', 'lip': f'Engine {i} mouth lip',
             'throat': f'Engine {i} dark throat', 'can': f'Engine {i} drive can'}
            for i, (x, y, w, length) in enumerate(ENGINES, 1)]
