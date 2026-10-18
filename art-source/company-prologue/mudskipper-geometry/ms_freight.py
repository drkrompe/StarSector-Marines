"""Mudskipper freight doors and ramps: how cargo leaves the waist bays on a landing.

Landed, the bay floor stands about 7.7 m above the ground: the keel is 3.5 m
below the floor and the legs (ms_legs) add 4.25 m. So each waist flank pairs
its cargo door with a freight ramp:

* Door. The two leaves (ms_hull.build_bay_doors) are plug doors. Each first
  moves PLUG units inboard, behind the dark door frame, then runs SLIDE units
  along the track into a pocket in the hull: the aft leaf aft, the forward
  leaf forward. 'Port cargo bay door aft leaf slide' and the others carry the
  leaf with its stiffeners, handle and track rollers.
* Ramp. A steel deck stowed flat under the bay floor, its outboard end flush
  in a slot in the sill. That end face carries the sill's yellow hazard tread,
  so a stowed ramp reads as the sill always did. Deploying, the ramp first
  runs out OUT units through the slot ('<side> freight ramp slide', along
  hull +-Y), then tips down about the slot lip ('<side> freight ramp tilt',
  about hull X) until its toe meets the ground.

The tilt angle comes from the legs' own pose: the driver reads `deploy` and
`load` and solves the angle so the toe touches the ground wherever the legs
put it (the landed, loaded pose gives about 28 degrees); with the legs up it
tips to the stowed soles' plane.

Every motion is a driver on the 'Landing control' empty (ms_legs.CONTROL):
  door_port, door_starboard   0 shut .. 1 open (plug over the first third)
  ramp_port, ramp_starboard   0 stowed .. 1 down (runs out over the first
                              70%, tips over the rest)
"""
import math
import bpy
from ms_kit import material
from ms_hull import SIDES
import ms_legs

PLUG, SLIDE = .8, 7.6             # leaf travel inboard, then along the track
WALL = 22.5                       # waist flank |y|
LIP_Y = WALL + .65                # outboard face of the sill
LIP_Z = -4.45                     # ramp deck top at the lip (the bay floor)
RAMP_T = .45                      # deck thickness
RAMP_X = (2.7, 17.3)              # deck span along the bay (opening 2.4..17.6)
RAMP_LEN = 20.0                   # whole deck, stowed under the floor
OUT = 18.0                        # how far it runs out before it tips
EXTEND_SHARE = .7                 # share of ramp_<side> spent running out
TOE_DEPTH = RAMP_T + .02          # deck top to the toe plate's underside


def _parent(objs, parent):
    bpy.context.view_layer.update()
    for o in objs:
        w = o.matrix_world.copy()
        o.parent = parent
        o.matrix_parent_inverse = parent.matrix_world.inverted()
        o.matrix_world = w


def _empty(name, loc, parent=None, kind='SINGLE_ARROW', size=1.5):
    e = bpy.data.objects.new(name, None)
    e.empty_display_type, e.empty_display_size = kind, size
    bpy.context.collection.objects.link(e)
    e.location = loc
    if parent is not None:
        _parent([e], parent)
    return e


def _drive(ob, path, index, control, props, expression):
    fc = ob.driver_add(path, index)
    drv = fc.driver
    drv.type = 'SCRIPTED'
    for p in props:
        v = drv.variables.new()
        v.name, v.type = p, 'SINGLE_PROP'
        v.targets[0].id = control
        v.targets[0].data_path = f'["{p}"]'
    drv.expression = expression


def clamp(e):
    return f'max(0.0, min(1.0, {e}))'


def build_doors(kit, control):
    for (s, leaf), parts in sorted(kit.bay_door_leaves.items()):
        label = 'Port' if s > 0 else 'Starboard'
        prop = f'door_{label.lower()}'
        lf = next(o for o in parts if o.name.startswith(f'{label} cargo bay door {leaf} leaf'))
        e = _empty(f'{label} cargo bay door {leaf} leaf slide', lf.matrix_world.translation.copy())
        e['plug_units'], e['slide_units'] = PLUG, SLIDE
        _parent(parts, e)
        y0, x0 = e.location.y, e.location.x
        along = -1 if leaf == 'aft' else 1
        _drive(e, 'location', 1, control, (prop,), f'{y0:.4f} - {s * PLUG:.4f}*{clamp(prop + "*3.0")}')
        _drive(e, 'location', 0, control, (prop,), f'{x0:.4f} + {along * SLIDE:.4f}*{clamp(prop + "*1.5 - 0.5")}')


def build_ramp(kit, control, mats, s, label):
    prop = f'ramp_{label.lower()}'
    xa, xb = RAMP_X
    xc, wx = (xa + xb) / 2, xb - xa
    # Fixed: the slot in the sill face the stowed ramp's end sits in.
    kit.box(f'{label} freight ramp slot', (xc, s * (LIP_Y - .02), LIP_Z - RAMP_T / 2), (wx + .4, .06, RAMP_T + .2), kit.dark, 0)
    tilt = _empty(f'{label} freight ramp tilt', (xc, s * LIP_Y, LIP_Z), kind='ARROWS')
    slide = _empty(f'{label} freight ramp slide', (xc, s * LIP_Y, LIP_Z), tilt)
    # Deck, stowed inboard of the lip: y from LIP_Y - RAMP_LEN to LIP_Y.
    ym = s * (LIP_Y - RAMP_LEN / 2)
    zc = LIP_Z - RAMP_T / 2
    parts = [kit.box(f'{label} freight ramp deck', (xc, ym, zc), (wx, RAMP_LEN, RAMP_T), kit.steel, .06)]
    for dx in (-wx / 2 + .35, wx / 2 - .35):
        # Rails stop short of the end so a stowed ramp shows nothing above the sill.
        parts.append(kit.box(f'{label} freight ramp side rail', (xc + dx, s * (LIP_Y - 1.5 - (RAMP_LEN - 1.5) / 2), LIP_Z + .12),
                             (.3, RAMP_LEN - 1.5, .25), kit.steel, .04))
    for k in range(13):
        y = LIP_Y - 1.6 - k * 1.4
        parts.append(kit.box(f'{label} freight ramp tread', (xc, s * y, LIP_Z + .04), (wx - 1.2, .3, .1), kit.dark, .02))
    for dx in (-wx / 3, 0, wx / 3):
        # Stringers stop short of the toe, so the toe's rubber edge is the lowest point.
        parts.append(kit.box(f'{label} freight ramp stringer', (xc + dx, s * (LIP_Y - 2.5 - (RAMP_LEN - 2.5) / 2), LIP_Z - RAMP_T - .2),
                             (.5, RAMP_LEN - 2.5, .4), kit.steel, .04))
    # The end face: the sill's hazard tread, flush in the slot when stowed.
    parts.append(kit.box(f'{label} cargo bay sill hazard', (xc, s * (LIP_Y + .02), zc), (wx - .4, .05, RAMP_T - .05), kit.hull_hazard, 0))
    parts.append(kit.box(f'{label} freight ramp toe plate', (xc, s * (LIP_Y - .25), LIP_Z - RAMP_T + .02), (wx, .5, .08), mats['toe'], .02))
    _parent(parts, slide)
    run = clamp(f'{prop}/{EXTEND_SHARE}')
    tip = clamp(f'({prop} - {EXTEND_SHARE})/{1 - EXTEND_SHARE:.4f}')
    _drive(slide, 'location', 1, control, (prop,), f'{slide.location.y:.4f} + {s * OUT:.4f}*{run}')
    # Ground in hull space from the legs' pose; rise from the deck top at the lip.
    ground = f'({ms_legs.STOWED_SOLE:.4f} - {ms_legs.LEG_TRAVEL:.4f}*{clamp("deploy")} + {ms_legs.COMPRESS:.4f}*{clamp("load")}*{clamp("deploy")})'
    # The toe's underside (OUT out, TOE_DEPTH below the deck top) is what meets the ground:
    # OUT sin(a) + TOE_DEPTH cos(a) = rise, so a = asin(rise / R) - phi.
    r, phi = math.hypot(OUT, TOE_DEPTH), math.atan2(TOE_DEPTH, OUT)
    angle = f'(asin(max(0.0, min(1.0, ({LIP_Z:.4f} - {ground}) / {r:.4f}))) - {phi:.5f})'
    _drive(tilt, 'rotation_euler', 0, control, (prop, 'deploy', 'load'), f'{-s:.1f}*{angle}*{tip}')
    tilt['ramp_length_out_units'] = OUT
    tilt['axis'] = 'hull X; toe down outboard'


def build(kit, control):
    mats = {'toe': material('Freight ramp toe rubber', (.05, .05, .05), 0, .9)}
    for prop, text in (('door_port', 'port cargo door: 0 shut .. 1 open'),
                       ('door_starboard', 'starboard cargo door: 0 shut .. 1 open'),
                       ('ramp_port', 'port freight ramp: 0 stowed .. 1 down to the ground'),
                       ('ramp_starboard', 'starboard freight ramp: 0 stowed .. 1 down to the ground')):
        control[prop] = 0.0
        control.id_properties_ui(prop).update(min=0.0, max=1.0, soft_min=0.0, soft_max=1.0, description=text)
    # A custom prop added after the depsgraph has evaluated the control is
    # missing from its evaluated copy until tagged, and a driver reading it
    # then is flagged invalid for good (second-wind-vfx's driver gotcha).
    control.update_tag()
    bpy.context.view_layer.update()
    build_doors(kit, control)
    for s, label in SIDES:
        build_ramp(kit, control, mats, s, label)
    revalidate()


def revalidate():
    """Clear any invalid flag on the landing drivers, then re-evaluate."""
    for ob in bpy.data.objects:
        for fc in (ob.animation_data.drivers if ob.animation_data else ()):
            fc.driver.is_valid = True
    bpy.context.view_layer.update()
