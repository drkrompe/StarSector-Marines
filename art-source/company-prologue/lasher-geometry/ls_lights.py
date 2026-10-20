"""Running lights: lamp fittings whose lens origins are the light anchors.

lasher_lights.png lights the bridge arc, the shoulder and spine viewports, the aft heat
vents (all built as glazing in ls_hull) and one lamp pixel either side of the bow at
(44, +-8.5). Only what the lights layer lights may glow (ship-detail-guide.md, Lamps): the
port and starboard lights sit on those two bow pixels, red to port and green to
starboard, and their lenses are lit. The stern and prow lights have no pixel on the
layer, so they are fittings with dark lenses: the lamp anchors stay for a shot's effects
(the shared VFX kit lights them from vfx_hull.py), but the hull itself does not glow there.
Each lamp is a bracketed housing with the lens recessed in it (never a bare glowing
sphere); the lens object carries the light's name and its origin is the position.
Positions, colours, blink and whether the lens is lit go in geometry.json `running_lights`.
"""
import math
import bpy
from mathutils import Vector

RED, GREEN, WHITE = (1.0, .06, .03), (.08, 1.0, .3), (1.0, .95, .85)
LIGHTS = []
LENS = .32        # lens radius, units (0.3 m at 0.95 m/u)


def lamp(kit, name, loc, colour, role, period, phase, mats, lit=True, aim=(0, 0, 1)):
    """A lamp fitting at loc (the lens centre) looking along `aim`: a bracket and housing in
    gunmetal, the lens recessed in it, lit only where the lights layer has a light."""
    import ls_kit
    a = Vector(aim).normalized()
    r = LENS
    base = Vector(loc)-a*r*1.1
    rot = Vector((0, 0, 1)).rotation_difference(a).to_euler()
    n = 24
    bpy.ops.mesh.primitive_cylinder_add(vertices=n, radius=r*1.45, depth=r*1.0, location=base+a*r*.1, rotation=rot)
    housing = bpy.context.object
    kit.finish(housing, name+' housing', kit.steel, .03)
    bpy.ops.mesh.primitive_cylinder_add(vertices=12, radius=r*.6, depth=r*1.2, location=base-a*r*.6, rotation=rot)
    kit.finish(bpy.context.object, name+' bracket', kit.steel, .02)
    bpy.ops.mesh.primitive_uv_sphere_add(segments=n, ring_count=8, radius=r, location=loc)
    lens = bpy.context.object
    lens.scale = (1, 1, .55)
    lens.rotation_euler = rot
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    kit.finish(lens, name, mats[colour] if lit else mats['dark lens'])
    for p in lens.data.polygons:
        p.use_smooth = True
    LIGHTS.append({'name': name, 'position': [round(v, 2) for v in loc], 'colour': colour, 'role': role,
                   'lit': lit, 'blink': {'period_s': period, 'phase_s': phase, 'duty': .18}})
    return lens


def build(kit):
    from ls_kit import material
    LIGHTS.clear()
    mats = {'red': material('Lasher running light red', (.1, .01, .01), 0, .4, emission=RED, strength=12.0),
            'green': material('Lasher running light green', (.01, .1, .03), 0, .4, emission=GREEN, strength=12.0),
            'white': material('Lasher running light white', (.2, .2, .2), 0, .4, emission=WHITE, strength=10.0),
            'dark lens': material('Lasher running light lens (unlit)', (.05, .05, .05), .2, .15)}
    for name, y, colour, role in (('Light port wing tip 1', 8.5, 'red', 'port side light (the bow lamp pixel)'),
                                  ('Light starboard wing tip 1', -8.5, 'green', 'starboard side light (the bow lamp pixel)')):
        hit = kit.surface(44, y)
        z = hit[0].z
        lamp(kit, name, (44, y, z+LENS*.9), colour, role, 0, 0, mats, aim=tuple(hit[1]))
    hit = kit.surface(-27, 0)
    lamp(kit, 'Light centre stern 1', (-27, 0, hit[0].z+LENS*.9), 'white', 'stern light (dark lens: not on the lights layer)',
         0, 0, mats, lit=False)
    hit = kit.surface(57.5, 0)
    lamp(kit, 'Light centre prow 1', (57.5, 0, hit[0].z+LENS*.9), 'white',
         'prow anti-collision strobe (dark lens: not on the lights layer)', 1.6, .4, mats, lit=False)


def records():
    return [dict(l) for l in LIGHTS]
