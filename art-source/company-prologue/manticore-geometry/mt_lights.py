"""Running lights: small emissive lamps whose origins are the light anchors.

manticore_pirate.png has no lights layer, so these follow ordinary practice
for a warship: red at the port wing tip (the outer turret pod), green at the
starboard, white at the stern and on the prow. Each lamp's object origin is
its position, so a shot can attach a point light or a flare to it.
Positions, colours and blink go in geometry.json `running_lights`.
"""
import sys
from pathlib import Path

import bpy
from mathutils import Vector

sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'ship-detail-kit'))
import ship_detail_kit as sdk

RED, GREEN, WHITE = (1.0, .06, .03), (.08, 1.0, .3), (1.0, .95, .85)
LIGHTS = []


from mt_hull import POD, nose_drop, wing_drop
POD_LAMP_Z = 9.6-wing_drop(POD[1])-nose_drop(POD[0])   # near the top of the drooping gun pod's outboard face

def lamp(kit, name, loc, colour, role, period, phase, mats, aim=(0, 0, 1)):
    """Detail kit lamp fitting (bracket, housing, guard; only the lens glows), its lens
    centred on loc. The anchor is an empty named for the light at loc."""
    S = sdk.Scale(kit.mpu)
    a = Vector(aim).normalized()
    base = Vector(loc)-a*S.u(.2)*2.0
    sdk.lamp_fitting(kit, S, name, tuple(base), tuple(a), {'housing': kit.steel, 'lens': mats[colour]})
    ob = bpy.data.objects.new(name, None)
    ob.location = loc
    ob.empty_display_size = .5
    bpy.context.collection.objects.link(ob)
    LIGHTS.append({'name': name, 'position': [round(v, 2) for v in loc], 'colour': colour, 'role': role,
                   'blink': {'period_s': period, 'phase_s': phase, 'duty': .18}})
    return ob


def build(kit):
    from mt_kit import material
    LIGHTS.clear()
    mats = {'red': material('Manticore running light red', (.1, .01, .01), 0, .4, emission=RED, strength=12.0),
            'green': material('Manticore running light green', (.01, .1, .03), 0, .4, emission=GREEN, strength=12.0),
            'white': material('Manticore running light white', (.2, .2, .2), 0, .4, emission=WHITE, strength=10.0)}
    lamp(kit, 'Light port wing tip 1', (23, POD[1]+12.85, POD_LAMP_Z), 'red', 'port side light (extremity)', 0, 0, mats,
         aim=(0, 1, 0))
    lamp(kit, 'Light starboard wing tip 1', (23, -POD[1]-12.85, POD_LAMP_Z), 'green', 'starboard side light (extremity)', 0, 0, mats,
         aim=(0, -1, 0))
    z = kit.surface(-48, 0)[0].z
    lamp(kit, 'Light centre stern 1', (-48, 0, z+.35), 'white', 'stern light', 0, 0, mats)
    z = kit.surface(83, 0)[0].z
    lamp(kit, 'Light centre prow 1', (83, 0, z+.35), 'white', 'prow anti-collision strobe', 1.6, .4, mats)


def records():
    return [dict(l) for l in LIGHTS]
