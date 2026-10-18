"""Mudskipper landing legs: four short telescoping legs that extend out of the keel.

A backwater ferry sets down on unprepared fields, so the four pads under the
keel are legs: each is a fixed housing in a dark well, an outer sleeve (stage
1) and a chrome ram (stage 2) sliding straight down out of it, and a foot.
Stowed, the foot sits snug under its housing (the build's default, so every
earlier review keeps its silhouette); deployed, the sleeve and ram run out
LEG_TRAVEL units together and the soles stand on GROUND, which clears
the keel by KEEL - GROUND units.

Each leg's moving parts hang from two slide empties:
  'Leg <name> stage 1 slide'   sleeve, gland, stage 2 slide
  'Leg <name> stage 2 slide'   ram, ankle, foot, sole, guide rod
Both move along hull -Z only. Drivers tie the slides to two custom
properties on the 'Landing control' empty, so one value poses all four legs;
keyframe them to animate:
  deploy  0 stowed .. 1 deployed (soles on GROUND, unloaded)
  load    0 .. 1 the ship's weight on the legs: each ram strokes up
          COMPRESS units, so the soles sit COMPRESS higher in hull space.
          A shot lowers the ship (its root) by the same amount, keeping
          the feet planted: landed, the ground is at GROUND + COMPRESS * load.
"""
import bpy
from ms_kit import material
from ms_hull import KEEL

# (name, x, y): the four pad stations, two under the forebody and two under the aft hull.
LEGS = (('fore port', 29, 12), ('fore starboard', 29, -12), ('aft port', -9, 17), ('aft starboard', -9, -17))
HOUSING_BOTTOM = KEEL - 1.2          # underside of the fixed housing
STOWED_SOLE = KEEL - 2.4             # sole plane stowed (the old fixed pads)
STAGE_1, STAGE_2 = 1.4, 1.2          # travel of each stage, hull units
LEG_TRAVEL = STAGE_1 + STAGE_2
GROUND = STOWED_SOLE - LEG_TRAVEL    # sole plane deployed
CONTROL = 'Landing control'          # also carries ms_freight's door and ramp knobs
COMPRESS = .6                        # oleo stroke under the ship's weight, hull units


def _parent(objs, parent):
    bpy.context.view_layer.update()
    for o in objs:
        w = o.matrix_world.copy()
        o.parent = parent
        o.matrix_parent_inverse = parent.matrix_world.inverted()
        o.matrix_world = w


def _slide(name, loc, travel, control, parent=None, compress=0.0):
    e = bpy.data.objects.new(name, None)
    e.empty_display_type, e.empty_display_size = 'SINGLE_ARROW', 1.2
    bpy.context.collection.objects.link(e)
    e.location = loc
    if parent is not None:
        _parent([e], parent)
    e['travel_units'] = travel
    e['axis'] = 'hull -Z'
    z0 = e.location.z
    fc = e.driver_add('location', 2)
    drv = fc.driver
    drv.type = 'SCRIPTED'
    for prop in ('deploy', 'load'):
        v = drv.variables.new()
        v.name, v.type = prop, 'SINGLE_PROP'
        v.targets[0].id = control
        v.targets[0].data_path = f'["{prop}"]'
    d = 'max(0.0, min(1.0, deploy))'
    drv.expression = f'{z0:.4f} - {travel:.4f}*{d}' + (f' + {compress:.4f}*max(0.0, min(1.0, load))*{d}' if compress else '')
    return e


def build_leg(kit, mats, control, name, x, y):
    # Fixed to the hull: the dark well, the housing and the guide bushing.
    kit.box(f'Leg {name} well', (x, y, KEEL - .32), (4.2, 4.2, .1), kit.dark, 0)
    kit.box(f'Leg {name} housing', (x, y, KEEL - .75), (2.6, 2.6, .9), kit.steel, .1)
    kit.cylinder(f'Leg {name} guide bushing', (x + 1.55, y, KEEL - .9), .32, .6, kit.steel, vertices=12, bevel=0)
    kit.box(f'Leg {name} guide bushing web', (x + 1.3, y, KEEL - .75), (.5, .5, .9), kit.steel, .04)
    # Stage 1: the outer sleeve, stowed inside the housing and hull.
    hb = HOUSING_BOTTOM
    s1 = [kit.cylinder(f'Leg {name} sleeve', (x, y, hb + 1.3), .72, 2.6, kit.steel, vertices=16, bevel=.03),
          kit.cylinder(f'Leg {name} sleeve gland', (x, y, hb + .1), .86, .22, kit.steel, vertices=16, bevel=.03)]
    # Stage 2: the chrome ram, the ankle and the foot under the housing.
    sole = STOWED_SOLE
    s2 = [kit.cylinder(f'Leg {name} ram', (x, y, hb + .75), .5, 2.6, mats['chrome'], vertices=16, bevel=.02),
          kit.cylinder(f'Leg {name} ankle', (x, y, sole + .65), .62, .3, kit.steel, vertices=16, bevel=.03),
          kit.box(f'Leg {name} foot', (x, y, sole + .325), (4.4, 3.6, .35), kit.steel, .12),
          kit.box(f'Leg {name} sole', (x, y, sole + .075), (4.2, 3.4, .15), mats['rubber'], .04),
          kit.cylinder(f'Leg {name} guide rod', (x + 1.55, y, sole + .5 + 2.0), .16, 4.0, mats['chrome'], vertices=8, bevel=0)]
    for dx in (-.88, .88):
        s2.append(kit.box(f'Leg {name} foot rib', (x + dx, y, sole + .55), (.25, 3.2, .12), kit.steel, .02))
    p1 = _slide(f'Leg {name} stage 1 slide', (x, y, hb), STAGE_1, control)
    _parent(s1, p1)
    p2 = _slide(f'Leg {name} stage 2 slide', (x, y, hb - .3), STAGE_2, control, p1, COMPRESS)
    _parent(s2, p2)
    return p1


def build(kit):
    mats = {'chrome': material('Leg ram chrome', (.6, .62, .64), .9, .25),
            'rubber': material('Leg sole rubber', (.05, .05, .05), 0, .9)}
    control = bpy.data.objects.new(CONTROL, None)
    control.empty_display_type, control.empty_display_size = 'CUBE', 1.0
    bpy.context.collection.objects.link(control)
    control.location = (10, 0, GROUND)
    for prop, text in (('deploy', '0 stowed, 1 deployed (soles on the ground plane)'),
                       ('load', "0 .. 1 the ship's weight on the legs (rams compress COMPRESS units)")):
        control[prop] = 0.0
        control.id_properties_ui(prop).update(min=0.0, max=1.0, soft_min=0.0, soft_max=1.0, description=text)
    control['compress_units'] = COMPRESS
    control['ground_z_deployed'] = GROUND
    control['travel_units'] = LEG_TRAVEL
    for name, x, y in LEGS:
        build_leg(kit, mats, control, name, x, y)
    return control
