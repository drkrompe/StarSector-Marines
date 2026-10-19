"""The twelve drives of falcon.ship: six main drives, six manoeuvring thrusters.

falcon.ship's engineSlots in order (MIDLINE style; the (P) skin removes none):
  Engine 1  (-81,  38) w16 angle 180   port drive block, outboard
  Engine 2  (-83,  27) w16 angle 180   port drive block, middle
  Engine 3  (-84,  16) w16 angle 180   port drive block, inboard (the exposed engine)
  Engine 4  (-83, -16) w16 angle 180   starboard drive block, inboard (exposed)
  Engine 5  (-83, -27) w16 angle 180   starboard drive block, middle
  Engine 6  (-82, -38) w16 angle 180   starboard drive block, outboard
  Engine 7  (-34,  75) w8  angle 160   port outrigger pod thrusters, aft and outboard
  Engine 8  (-39,  65) w8  angle 165
  Engine 9  (-44,  55) w8  angle 170
  Engine 10 (-34, -75) w8  angle 200   starboard outrigger pod thrusters
  Engine 11 (-39, -65) w8  angle 195
  Engine 12 (-44, -55) w8  angle 190
Engine i is the .ship's slot i-1. The slot point is the bell mouth; the bell
narrows forward into its can, which runs forward into the drive block or the
outrigger pod along the slot angle. Z 0 for all (the game is flat).

The (P) skin's description: the pirates' "showy 'improvements'" to the
propulsion, a patchwork of engine components. The sprite shows it on the
drive blocks: the inboard pair are bare engine cores in hazard-yellow bands,
the others cans with yellow chevrons. The cans here are whole drive units
(can, shroud, bell, lip, throat), never nozzles poking out of the hull: a
main drive's shroud carries the can's full radius back to the lip round the
bell, the straight cylinder the sprite draws.

Names follow the standing decision: `Engine <i> drive can`, `Engine <i>
aft-facing bell`, `Engine <i> mouth lip`, `Engine <i> dark throat`,
`Engine <i> exhaust anchor` (an empty at the slot, +X down the exhaust) and
`Engine <i> drive glow anchor` (an empty at the throat, +X aft).
"""
import math
import bpy
from mathutils import Matrix
from fc_kit import boolean, revolve_x

ENGINES = [(-81, 38, 16, 80, 180), (-83, 27, 16, 80, 180), (-84, 16, 16, 80, 180),
           (-83, -16, 16, 80, 180), (-83, -27, 16, 80, 180), (-82, -38, 16, 80, 180),
           (-34, 75, 8, 24, 160), (-39, 65, 8, 28, 165), (-44, 55, 8, 32, 170),
           (-34, -75, 8, 24, 200), (-39, -65, 8, 28, 195), (-44, -55, 8, 32, 190)]
Z = 0.0
MOUTH = .28    # mouth radius / slot width
THROAT = .62   # throat radius / mouth radius
LENGTH = .5    # bell length / slot width
WALL = .05     # wall thickness / slot width
MAIN = (1, 2, 3, 4, 5, 6)
EXPOSED = (3, 4)


def host(i):
    if i <= 3:
        return 'Hull port drive block'
    if i <= 6:
        return 'Hull starboard drive block'
    if i in (9, 12):  # the inboard thruster sits on the prong's aft tip, not the pod
        return 'Hull port prong' if i == 9 else 'Hull starboard prong'
    return 'Hull port outrigger pod' if i <= 9 else 'Hull starboard outrigger pod'


def mouth_r(w):
    return MOUTH*w


def _place(ob, x, y, angle):
    """Parts are built along +X from the origin (mouth at 0, can forward at +X):
    turn them so +X points opposite the exhaust (angle-180) and move to the slot."""
    ob.data.transform(Matrix.Translation((x, y, Z)) @ Matrix.Rotation(math.radians(angle-180), 4, 'Z'))
    return ob


def build(kit):
    for i, (x, y, w, length, angle) in enumerate(ENGINES, 1):
        rm, wall = mouth_r(w), WALL*w
        rt, bl = THROAT*rm, LENGTH*w
        main = i in MAIN
        can_len = 34.0 if main else 11.0
        can_r = rm+wall+(.5 if main else .35)
        n = 20 if main else 14
        cutter = revolve_x(kit, f'Temporary Engine {i} bell well cutter',
                           [(-2, 0), (-2, rm+.9*wall), (bl, rt+.9*wall), (bl, 0)], 0, 0, kit.dark, n)
        _place(cutter, x, y, angle)
        boolean(bpy.data.objects[host(i)], cutter)
        mat_can = kit.bare if i in EXPOSED else kit.steel
        _place(revolve_x(kit, f'Engine {i} drive can', [(bl+.3, 0), (bl+.3, can_r), (can_len, can_r*.94),
                                                        (can_len, 0)], 0, 0, mat_can, n), x, y, angle)
        if main:
            # The can's shroud carries its full radius back to the lip round the
            # bell, so the drive reads as the sprite's straight cylinder, not a cone.
            _place(revolve_x(kit, f'Engine {i} bell shroud', [(.5, rm+wall*1.05), (.5, can_r), (bl+.3, can_r),
                                                              (bl+.3, rt+wall*1.05)], 0, 0, mat_can, n), x, y, angle)
        _place(revolve_x(kit, f'Engine {i} aft-facing bell', [(bl, rt), (0, rm), (0, rm+wall), (bl, rt+wall)],
                         0, 0, kit.steel, n), x, y, angle)
        _place(revolve_x(kit, f'Engine {i} mouth lip', [(.35*wall, rm-.2*wall), (-.25*wall, rm),
                                                        (-.25*wall, rm+1.6*wall), (.6*wall, rm+1.8*wall)],
                         0, 0, kit.plate, n), x, y, angle)
        _place(revolve_x(kit, f'Engine {i} dark throat', [(bl-.05, 0), (bl-.05, rt+.05), (bl+.25, rt+.05),
                                                          (bl+.25, 0)], 0, 0, kit.dark, n), x, y, angle)
        if main:
            # The pirates' hazard banding: two yellow rings round each can just
            # forward of the bell (one wide band on the exposed cores).
            bands = ((9.0, 3.4),) if i in EXPOSED else ((7.5, 1.2), (10.0, 1.2))
            for k, (bx, bw) in enumerate(bands, 1):
                _place(revolve_x(kit, f'Engine {i} hazard band {k}',
                                 [(bx, can_r-.4), (bx, can_r+.12), (bx+bw, can_r+.12), (bx+bw, can_r-.4)], 0, 0, kit.hazard, n),
                       x, y, angle)
        a = math.radians(angle)
        kit.empty(f'Engine {i} exhaust anchor', (x, y, Z), angle, size=w/4)
        kit.empty(f'Engine {i} drive glow anchor', (x-bl*math.cos(a)+.05*math.cos(a), y-bl*math.sin(a), Z),
                  angle, size=w/6)


def records():
    out = []
    for i, (x, y, w, length, angle) in enumerate(ENGINES, 1):
        a = math.radians(angle)
        out.append({'index': i, 'slot': i-1, 'role': 'main drive' if i in MAIN else 'manoeuvring thruster',
                    'objects': f'Engine {i} ...', 'anchor': f'Engine {i} exhaust anchor',
                    'glow_anchor': f'Engine {i} drive glow anchor', 'location': [x, y, Z], 'width': w,
                    'length': length, 'contrailSize': 64 if i in MAIN else 128, 'style': 'MIDLINE', 'angle': angle,
                    'exhaust_direction': [round(math.cos(a), 4), round(math.sin(a), 4), 0],
                    'mouth_radius': round(mouth_r(w), 2), 'bell': f'Engine {i} aft-facing bell',
                    'lip': f'Engine {i} mouth lip', 'throat': f'Engine {i} dark throat',
                    'can': f'Engine {i} drive can'})
    return out
