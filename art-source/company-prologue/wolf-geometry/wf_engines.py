"""The three drives: whole drive units, each bell sunk into a drive can in
the engine block's aft face.

wolf.ship has three HIGH_TECH engine slots, all angle 180, width 16, length
64; Z 0 here:
  Engine 1  (-36,  12)   port
  Engine 2  (-40,   0)   centre (the sprite's aft nub, rows 93-94)
  Engine 3  (-36, -11)   starboard
Engine i is the .ship's slot i-1; the pirate skin removes no engine slot.
The slot point is the bell mouth; the bell narrows forward into its can.
Mouth radius 0.20 x width keeps the outer bells and their cans inside the
block's flanks (Y +-15 at X -36, rows 89-90).

Names follow the standing decision: `Engine <i> drive can`, `Engine <i>
aft-facing bell`, `Engine <i> mouth lip`, `Engine <i> dark throat`,
`Engine <i> exhaust anchor` (empty at the slot, +X down the exhaust) and
`Engine <i> drive glow anchor` (empty at the throat, +X aft).
"""
import bpy
from wf_kit import boolean, revolve_x

ENGINES = [(-36, 12, 16, 64), (-40, 0, 16, 64), (-36, -11, 16, 64)]
Z = -2.5   # the drive axis, centred in the deepened engine block
MOUTH = .20    # mouth radius / slot width
THROAT = .62   # throat radius / mouth radius
LENGTH = .45   # bell length / slot width
WALL = .05     # wall thickness / slot width
HOST = 'Hull engine block'


def mouth_r(w):
    return MOUTH*w


def build(kit):
    host = bpy.data.objects[HOST]
    for i, (x, y, w, length) in enumerate(ENGINES, 1):
        rm, wall = mouth_r(w), WALL*w
        rt, xt = THROAT*rm, x+LENGTH*w
        cutter = revolve_x(kit, f'Temporary Engine {i} bell well cutter',
                           [(x-3, 0), (x-3, rm+.9*wall), (xt, rt+.9*wall), (xt, 0)], y, Z, kit.dark, 20)
        boolean(host, cutter)
        can_r = rm+wall+.2
        revolve_x(kit, f'Engine {i} drive can', [(xt-.2, 0), (xt-.2, can_r), (-22.0, can_r*.9), (-22.0, 0)],
                  y, Z, kit.steel, 20)
        revolve_x(kit, f'Engine {i} aft-facing bell', [(xt, rt), (x, rm), (x, rm+wall), (xt, rt+wall)],
                  y, Z, kit.steel, 20)
        revolve_x(kit, f'Engine {i} mouth lip', [(x+.35*wall, rm-.2*wall), (x-.25*wall, rm),
                                                (x-.25*wall, rm+1.6*wall), (x+.6*wall, rm+1.8*wall)],
                  y, Z, kit.steel, 20)
        kit.cylinder(f'Engine {i} dark throat', (xt+.1, y, Z), rt+.05, .3, kit.dark, 'X', 20, 0)
        kit.empty(f'Engine {i} exhaust anchor', (x, y, Z), 180, size=w/4)
        kit.empty(f'Engine {i} drive glow anchor', (xt-.05, y, Z), 180, size=w/6)


def records():
    return [{'index': i, 'slot': i-1, 'objects': f'Engine {i} ...', 'anchor': f'Engine {i} exhaust anchor',
             'glow_anchor': f'Engine {i} drive glow anchor',
             'location': [x, y, Z], 'width': w, 'length': length, 'contrailSize': 64, 'style': 'HIGH_TECH',
             'angle': 180, 'exhaust_direction': [-1, 0, 0], 'mouth_radius': round(mouth_r(w), 2),
             'bell': f'Engine {i} aft-facing bell', 'lip': f'Engine {i} mouth lip',
             'throat': f'Engine {i} dark throat', 'can': f'Engine {i} drive can'}
            for i, (x, y, w, length) in enumerate(ENGINES, 1)]
