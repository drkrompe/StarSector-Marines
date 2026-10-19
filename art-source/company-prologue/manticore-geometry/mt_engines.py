"""The five drives: whole drive units, each bell seated in a drive can.

manticore.ship has five LOW_TECH engine slots, all angle 180, Z 0 here:
  Engine 1  (-66,  17) width 20 length 64   port main
  Engine 2  (-62,   0) width 20 length 48   centre main
  Engine 3  (-66, -16) width 20 length 64   starboard main
  Engine 4  (-62,  30) width 12 length 32   port outer
  Engine 5  (-62, -30) width 12 length 32   starboard outer
Engine i is the .ship's slot i-1. The slot point is the bell mouth (the
exhaust starts there). The aft body's face is at X -56; the sprite shows the
gold bells standing aft of it in dark machinery, so each drive is a can
standing out of the aft face, its bell flaring from the can's lip to the
mouth, and the can running forward into the aft body.

Names follow the standing decision: `Engine <i> drive can`, `Engine <i>
aft-facing bell`, `Engine <i> mouth lip`, `Engine <i> dark throat`,
`Engine <i> exhaust anchor` (an empty at the slot, +X down the exhaust) and
`Engine <i> drive glow anchor` (an empty at the throat, +X aft).
"""
import bpy
from mt_kit import boolean, revolve_x

ENGINES = [(-66, 17, 20, 64), (-62, 0, 20, 48), (-66, -16, 20, 64), (-62, 30, 12, 32), (-62, -30, 12, 32)]
Z = 0.0
MOUTH = .30    # mouth radius / slot width
THROAT = .62   # throat radius / mouth radius
LENGTH = .5    # bell length / slot width
WALL = .06     # wall thickness / slot width
EXPOSED = .3   # bell length standing proud of its can, / slot width
CAN_FRONT = -30.0
HOST = 'Hull aft body'


def mouth_r(w):
    return MOUTH*w


def build(kit):
    host = bpy.data.objects[HOST]
    for i, (x, y, w, length) in enumerate(ENGINES, 1):
        rm, wall = mouth_r(w), WALL*w
        rt = THROAT*rm
        xt = x+LENGTH*w
        n = 16 if w >= 16 else 12
        can_r = rm+wall+.06*w
        can_aft = x+EXPOSED*w
        # Open the host's aft face round the can (applied, never left live).
        cutter = revolve_x(kit, f'Temporary Engine {i} well cutter',
                           [(can_aft-1, 0), (can_aft-1, can_r+.4), (xt+2, can_r+.4), (xt+2, 0)], y, Z, kit.dark, n)
        boolean(host, cutter)
        revolve_x(kit, f'Engine {i} drive can', [(can_aft, 0), (can_aft, can_r), (can_aft+1.2, can_r+.5),
                                                (CAN_FRONT, can_r+.5), (CAN_FRONT, 0)], y, Z, kit.steel, n)
        revolve_x(kit, f'Engine {i} aft-facing bell', [(xt, rt), (x, rm), (x, rm+wall), (xt, rt+wall)],
                  y, Z, kit.plate, n)
        revolve_x(kit, f'Engine {i} mouth lip', [(x+.35*wall, rm-.2*wall), (x-.25*wall, rm), (x-.25*wall, rm+1.6*wall),
                                                (x+.6*wall, rm+1.8*wall)], y, Z, kit.steel, n)
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
