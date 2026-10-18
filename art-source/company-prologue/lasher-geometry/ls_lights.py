"""Running lights: small emissive lamps whose origins are the light anchors.

lasher_lights.png lights only the bridge arc, the shoulder and spine
viewports and the aft heat vents (all built as glazing in ls_hull); it has
no navigation lamps, so these follow ordinary practice for a warship in
formation: red at the port wing tip, green at the starboard, white at the
stern and on the prow. Each lamp's object origin is its position, so a shot
can attach a point light or a flare to it. Positions, colours and blink go
in geometry.json `running_lights`.
"""
import bpy

RED, GREEN, WHITE = (1.0, .06, .03), (.08, 1.0, .3), (1.0, .95, .85)
LIGHTS = []


def lamp(kit, name, loc, colour, role, period, phase, mats):
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1, radius=.4, location=loc)
    ob = bpy.context.object
    kit.finish(ob, name, mats[colour])
    LIGHTS.append({'name': name, 'position': [round(v, 2) for v in loc], 'colour': colour, 'role': role,
                   'blink': {'period_s': period, 'phase_s': phase, 'duty': .18}})
    return ob


def build(kit):
    from ls_kit import material
    LIGHTS.clear()
    mats = {'red': material('Lasher running light red', (.1, .01, .01), 0, .4, emission=RED, strength=12.0),
            'green': material('Lasher running light green', (.01, .1, .03), 0, .4, emission=GREEN, strength=12.0),
            'white': material('Lasher running light white', (.2, .2, .2), 0, .4, emission=WHITE, strength=10.0)}
    lamp(kit, 'Light port wing tip 1', (-11.5, 33.8, .4), 'red', 'port side light (extremity)', 0, 0, mats)
    lamp(kit, 'Light starboard wing tip 1', (-11.5, -33.8, .4), 'green', 'starboard side light (extremity)', 0, 0, mats)
    z = kit.surface(-27, 0)[0].z
    lamp(kit, 'Light centre stern 1', (-27, 0, z+.35), 'white', 'stern light', 0, 0, mats)
    z = kit.surface(57.5, 0)[0].z
    lamp(kit, 'Light centre prow 1', (57.5, 0, z+.35), 'white', 'prow anti-collision strobe', 1.6, .4, mats)


def records():
    return [dict(l) for l in LIGHTS]
