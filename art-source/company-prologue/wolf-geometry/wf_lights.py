"""Lamps: the sprite's own hull lamps plus ordinary running lights.

wolf.ship has no lights layer, but both sprites paint two amber hull lamps on
the neck (row 28, cols 14 and 37: X 26, Y +-11.5); they are built here as
emissive lamps. Ordinary practice adds red at the port extremity (the missile
pod's outboard face), green at the starboard and white at the stern. Each
lamp's origin is its position, so a shot can attach a point light or flare.
Positions, colours and blink go in geometry.json `running_lights`.
"""
import bpy

RED, GREEN, WHITE, AMBER = (1.0, .06, .03), (.08, 1.0, .3), (1.0, .95, .85), (1.0, .78, .38)
LIGHTS = []


def lamp(kit, name, loc, colour, role, period, phase, mats, r=.45):
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1, radius=r, location=loc)
    ob = bpy.context.object
    kit.finish(ob, name, mats[colour])
    LIGHTS.append({'name': name, 'position': [round(v, 2) for v in loc], 'colour': colour, 'role': role,
                   'blink': {'period_s': period, 'phase_s': phase, 'duty': .18}})
    return ob


def build(kit):
    from wf_kit import material
    LIGHTS.clear()
    mats = {'red': material('Wolf running light red', (.1, .01, .01), 0, .4, emission=RED, strength=12.0),
            'green': material('Wolf running light green', (.01, .1, .03), 0, .4, emission=GREEN, strength=12.0),
            'white': material('Wolf running light white', (.2, .2, .2), 0, .4, emission=WHITE, strength=10.0),
            'amber': kit.lamp}
    for side, s in (('port', 1), ('starboard', -1)):
        z = kit.surface(26, s*11.5)[0].z
        lamp(kit, f'Light {side} neck lamp 1', (26, s*11.5, z+.2), 'amber', 'hull lamp (sprite, row 28)', 0, 0,
             mats, .7)
    lamp(kit, 'Light port pod tip 1', (9.5, 25.6, -1.5), 'red', 'port side light (extremity)', 0, 0, mats)
    lamp(kit, 'Light starboard pod tip 1', (9.5, -25.6, -1.5), 'green', 'starboard side light (extremity)', 0, 0, mats)
    z = kit.surface(-30, 0)[0].z
    lamp(kit, 'Light centre stern 1', (-30, 0, z+.35), 'white', 'stern light', 0, 0, mats)


def records():
    return [dict(l) for l in LIGHTS]
