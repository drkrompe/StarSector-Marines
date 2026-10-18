"""Shepherd drives: the two main drive pods on the aft crossbeam and the four
maneuvering-jet clusters at its tips (the maneuveringjets system).

Engine slots from shepherd.ship, in file order (index i = 1..6):
  1 (-40, 20) w16 angle 180, 2 (-40, -19) w16 angle 180 - the main drives;
  3 (-22, 35) w2 140, 4 (-30, 33) w3 145, 5 (-22, -33) w2 220,
  6 (-30, -32) w3 215 - small LOW_TECH jets angled out and aft.
Read off drone_tender.png, each main drive is a whole burnt-orange mound
(X -14..-34, Y 12..27) ending in a grey bell (X -34..-41, Y 15..24): the
pod is the whole mound, not a nozzle stuck on the hull.

Names follow the Second Wind: 'Engine <i> aft-facing bell', 'Engine <i>
mouth lip', 'Engine <i> dark throat', 'Engine <i> exhaust anchor' (an empty
on the mouth whose +X is the exhaust direction), plus 'Engine <i> drive pod
casing' / 'Engine <i> pylon' for the mains and 'Engine <i> jet housing' for
the jets.
"""
import math
import bpy
from mathutils import Matrix, Vector
from sh_kit import pod, revolve_x

# (x, y, width, angle) exactly as shepherd.ship lists them.
ENGINES = [(-40, 20, 16, 180), (-40, -19, 16, 180), (-22, 35, 2, 140), (-30, 33, 3, 145),
           (-22, -33, 2, 220), (-30, -32, 3, 215)]
POD_R = 7.0     # drive pod casing: vertical half-height
POD_RY = 8.0    # and half-width, the sprite's orange mound (Y 12..28)
MAIN_BELL = {'mouth_r': 4.6, 'throat_r': 3.2, 'length': 6.0}
ANCHORS = {}   # engine index -> (location, exhaust direction), filled by build()


def bell(kit, i, loc, angle, rm, rt, length, wall, throat_at=None):
    """Bell, lip and throat built mouth-at-origin along +X, then turned so the
    exhaust (-X before turning) points along the slot angle. throat_at puts
    the dark throat that far in from the mouth (default: at the bell's end),
    ahead of anything the bell's base is sunk into."""
    ta = length-.1*rt if throat_at is None else throat_at
    tr = rm+(rt-rm)*ta/length
    parts = [
        revolve_x(kit, f'Engine {i} aft-facing bell', [(0, rm), (0, rm+wall), (length, rt+wall), (length, rt)],
                  0, 0, kit.steel, 24),
        revolve_x(kit, f'Engine {i} mouth lip', [(-.15*rm, rm-.05), (-.15*rm, rm+wall*2.2), (.12*rm, rm+wall*2.2),
                                                (.12*rm, rm-.05)], 0, 0, kit.grey, 24),
        kit.cylinder(f'Engine {i} dark throat', (ta, 0, 0), tr, .2*rt, kit.dark, 'X', 24, 0),
    ]
    turn = Matrix.Translation(loc) @ Matrix.Rotation(math.radians(angle-180), 4, 'Z')
    for ob in parts:
        bpy.context.view_layer.update()
        ob.matrix_world = turn @ ob.matrix_world
    d = Vector((math.cos(math.radians(angle)), math.sin(math.radians(angle)), 0))
    anchor = kit.empty(f'Engine {i} exhaust anchor', loc, angle)
    ANCHORS[i] = (tuple(round(v, 3) for v in loc), tuple(round(v, 4) for v in d))
    return anchor


def build(kit):
    ANCHORS.clear()
    for i, (x, y, w, angle) in enumerate(ENGINES[:2], 1):
        side = 'port' if y > 0 else 'starboard'
        casing = pod(kit, f'Engine {i} drive pod casing', [(-36, 5.5, 4.6), (-34.5, 7.5, 6.2), (-32, POD_RY, POD_R),
                                                           (-16, POD_RY, POD_R), (-12.5, 6.0, 5.0)],
                     y, 0, kit.orange, 12)
        casing['side'] = side
        kit.hull.append(casing)
        kit.box(f'Engine {i} pylon', (-14.5, y, 0), (9, 9, 6), kit.steel, .2)
        b = MAIN_BELL
        bell(kit, i, Vector((x-.5, y, 0)), angle, b['mouth_r'], b['throat_r'], b['length'], .35, 4.0)
    # Jet clusters: a housing block run out from the crossbeam tip to the slot.
    for i, (x, y, w, angle) in enumerate(ENGINES[2:], 3):
        s = 1 if y > 0 else -1
        inner = 29.0 if x > -26 else 26.0
        kit.box(f'Engine {i} jet housing', (x+.5, s*(inner+abs(y))/2-s*.8, 0), (5, abs(y)-inner, 3.6),
                kit.steel, .2)
        r = .5+.35*w
        d = Vector((math.cos(math.radians(angle)), math.sin(math.radians(angle)), 0))
        bell(kit, i, Vector((x, y, 0))+d*.2, angle, r, r*.7, 1.6*r, .15)
