"""The six drives: whole drive units, the bell sunk into a drive can in the
engine block on each side of the stern notch.

enforcer.ship has six LOW_TECH engine slots, all angle 180, Z 0 here:
  Engine 1  (-66,  29) width 24 length 64   port main drive
  Engine 2  (-66, -28) width 24 length 64   starboard main drive
  Engine 3  (-66,  38) width  8 length 32   port, outboard
  Engine 4  (-65,  20) width  8 length 32   port, inboard
  Engine 5  (-65, -20) width  8 length 32   starboard, inboard
  Engine 6  (-66, -37) width  8 length 32   starboard, outboard
Engine i is the .ship's slot i-1 (the game's own slight asymmetry kept). The
slot point is the bell mouth; the bell narrows forward into its can, which
runs forward into the engine block (built here: one either side of the
stern notch, each holding a big drive between two small ones). Mouth radius 0.27 x width on the mains
and 0.30 x on the small drives, so the three bells on a block sit side by
side the way the sprite draws them.

Names follow the standing decision: `Engine <i> drive can`, `Engine <i>
aft-facing bell`, `Engine <i> mouth lip`, `Engine <i> dark throat`,
`Engine <i> exhaust anchor` (an empty at the slot, +X down the exhaust) and
`Engine <i> drive glow anchor` (an empty at the throat, +X aft).
"""
import bpy
from ef_kit import boolean, mirror_y, prism, revolve_x, section

ENGINE_BLOCK = [(-45, 47), (-52, 48.5), (-58, 46.5), (-62, 43.5), (-64, 40), (-64, 17.5), (-60, 15.5),
                (-52, 13.5), (-46, 13)]


ENGINES = [(-66, 29, 24, 64), (-66, -28, 24, 64), (-66, 38, 8, 32), (-65, 20, 8, 32), (-65, -20, 8, 32),
           (-66, -37, 8, 32)]
Z = 0.0
THROAT = .62   # throat radius / mouth radius
WALL = .06     # wall thickness / slot width


def mouth_r(w):
    return (.27 if w >= 16 else .30)*w


def bell_len(w):
    return (.42 if w >= 16 else .55)*w


HOSTS = {1: 'Hull port engine block', 3: 'Hull port engine block', 4: 'Hull port engine block',
         2: 'Hull starboard engine block', 5: 'Hull starboard engine block', 6: 'Hull starboard engine block'}


def build(kit):
    for side, f in (('port', 1), ('starboard', -1)):
        plan = ENGINE_BLOCK if f > 0 else mirror_y(ENGINE_BLOCK)
        kit.hull.append(prism(kit, f'Hull {side} engine block', plan, section(-8.5, 4.5, 2.5, 2.0), kit.steel, .2))
    for i, (x, y, w, length) in enumerate(ENGINES, 1):
        host = bpy.data.objects[HOSTS[i]]
        rm, wall, L = mouth_r(w), WALL*w, bell_len(w)
        cutter = revolve_x(kit, f'Temporary Engine {i} bell well cutter',
                           [(x-2, 0), (x-2, rm+.9*wall), (x+L, THROAT*rm+.9*wall), (x+L, 0)],
                           y, Z, kit.dark, 16)
        boolean(host, cutter)
        rt = THROAT*rm
        xt = x+L
        can_front = -47.0
        can_r = rm+wall+.25*w/8
        n = 20 if w >= 16 else 12
        revolve_x(kit, f'Engine {i} drive can', [(xt-.2, 0), (xt-.2, can_r), (can_front, can_r*.92),
                                                (can_front, 0)], y, Z, kit.steel, n)
        revolve_x(kit, f'Engine {i} aft-facing bell', [(xt, rt), (x, rm), (x, rm+wall), (xt, rt+wall)],
                  y, Z, kit.steel, n)
        revolve_x(kit, f'Engine {i} mouth lip', [(x+.35*wall, rm-.2*wall), (x-.25*wall, rm),
                                                (x-.25*wall, rm+1.6*wall), (x+.6*wall, rm+1.8*wall)],
                  y, Z, kit.trim, n)
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
