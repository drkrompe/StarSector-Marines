"""Running lights: small emissive lamps whose origins are the light anchors.

The Broadsword has no lights layer, so these follow ordinary practice for a
fighter flying in company (the Lasher's ls_lights is the pattern): red on the
port blade tip, green on the starboard, a white tail light on the aft face of
the spine, and a white anti-collision strobe on the dorsal plate behind the
canopy. Each lamp's object origin is its position, so a shot can attach a
point light or a flare to it. Positions, colours and blink go in
geometry.json `running_lights`.
"""
import bpy

RED, GREEN, WHITE = (1.0, .06, .03), (.08, 1.0, .3), (1.0, .95, .85)
LIGHTS = []
R = .22   # lamp radius, units (0.11 m)


def lamp(kit, name, loc, colour, role, period, phase, mats):
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1, radius=R, location=loc)
    ob = bpy.context.object
    kit.finish(ob, name, mats[colour])
    LIGHTS.append({'name': name, 'position': [round(v, 2) for v in loc], 'colour': colour, 'role': role,
                   'blink': {'period_s': period, 'phase_s': phase, 'duty': .18}})
    return ob


def build(kit):
    from bw_kit import material
    LIGHTS.clear()
    mats = {'red': material('Broadsword running light red', (.1, .01, .01), 0, .4, emission=RED, strength=12.0),
            'green': material('Broadsword running light green', (.01, .1, .03), 0, .4, emission=GREEN, strength=12.0),
            'white': material('Broadsword running light white', (.2, .2, .2), 0, .4, emission=WHITE, strength=10.0)}
    # Blade tips: on the outboard edge rail's forward end, half sunk.
    for side, s, colour in (('port', 1, 'red'), ('starboard', -1, 'green')):
        lamp(kit, f'Light {side} blade tip 1', (1.6, s*13.86, .1), colour, f'{side} side light (extremity)', 0, 0, mats)
    lamp(kit, 'Light centre tail 1', (-7.02, 0, .5), 'white', 'tail light', 0, 0, mats)
    z = kit.surface(3.2, 0)[0].z
    lamp(kit, 'Light centre dorsal 1', (3.2, 0, z+.08), 'white', 'anti-collision strobe', 1.2, .3, mats)


def records():
    return [dict(l) for l in LIGHTS]
