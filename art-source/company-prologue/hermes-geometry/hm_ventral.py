"""Hermes underside: the troop ramp and the landing gear.

The Hermes lands marines, so the design needs both. Neither is in the
sprite (a top view cannot show them); both are inferred from the boat's
job and placed where the planform leaves room.

Troop ramp. The spine is the only pressure hull deep enough for standing
headroom, and the wing body and drive block cover its flanks and stern, so
the way out is down. The ramp is hinged on the opening's AFT edge and
lowers its FORWARD end: marines walk down and forward and come out under
the wing's leading edge beside the nose, clear of the drives. Every ramp
part is parented to 'Hull ventral ramp hinge'. Rotating that empty about
its local Y opens it: positive angles lower the toe. The custom
properties on the empty give the axis, sign and the ground-contact angle.

Landing gear. Tricycle: a nose leg under the neck and two main legs under
the wing body, aft of the gun sponsons. Each leg's parts are parented to
'Gear <leg> hinge'; the model is built with the gear extended (rotation 0)
and the empty's 'retract_angle_deg' about 'retract_axis' folds it into its
well (nose aft, mains forward). The driver's --gear up applies it.
"""
import math
import bpy
from hm_kit import bx, boolean, pivot, parent_to
from hm_hull import KEEL, WING_KEEL

GROUND = KEEL - 8.0                      # pad soles, gear extended
RAMP_OPENING = (2.0, 18.0, 6.0)          # x0, x1, half width
RAMP_WELL_TOP = -6.0
RAMP_HINGE = (2.0, 0.0, KEEL)
RAMP_HALF = 5.6
# Legs: name, hinge (x, y, z), retract angle about Y (deg), well (x0, x1, half width about y).
GEAR = [('nose', (31.0, 0.0, KEEL + 1.0), 90.0),
        ('port main', (-8.0, 16.0, WING_KEEL + 1.5), -90.0),
        ('starboard main', (-8.0, -16.0, WING_KEEL + 1.5), -90.0)]


def ramp_contact_angle():
    x0, x1, _ = RAMP_OPENING
    return math.degrees(math.asin(min(1.0, (KEEL - GROUND)/(x1 - x0))))


def build_ramp(kit):
    x0, x1, h = RAMP_OPENING
    well = bx(kit, 'Temporary ramp well', x0, x1, -h, h, KEEL - 1, RAMP_WELL_TOP, kit.dark, 0)
    boolean(kit.hull, well, 'DIFFERENCE')
    bx(kit, 'Hull troop bay well liner', x0 + .05, x1 - .05, -h + .05, h - .05, RAMP_WELL_TOP - .2, RAMP_WELL_TOP - .05, kit.dark, 0)
    hx, hy, hz = RAMP_HINGE
    parts = [bx(kit, 'Hull ventral ramp', hx, x1 - .1, -RAMP_HALF, RAMP_HALF, KEEL - .05, KEEL + .55, kit.panel, .05),
             bx(kit, 'Hull ventral ramp hinge barrel', hx - .3, hx + .3, -RAMP_HALF + .4, RAMP_HALF - .4, hz - .3, hz + .3, kit.dark, .05)]
    hinge = pivot('Hull ventral ramp hinge', RAMP_HINGE,
                  {'open_axis': 'Y', 'open_sign': 1, 'contact_angle_deg': round(ramp_contact_angle(), 1),
                   'note': 'positive rotation about local Y lowers the forward toe'})
    parent_to(parts, hinge)
    return hinge


def build_gear(kit):
    hinges = []
    for name, (x, y, z), retract in GEAR:
        leg = z - GROUND - .8
        # Well the leg folds into: along +X for the mains, -X for the nose.
        sign = -1 if retract > 0 else 1
        wx0, wx1 = sorted((x - sign*1.5, x + sign*(leg + 1.8)))
        host = kit.hull if name == 'nose' else bpy.data.objects['Wing body']
        base = KEEL if name == 'nose' else WING_KEEL
        boolean(host, bx(kit, 'Temporary gear well', wx0, wx1, y - 1.8, y + 1.8, base - 1, z + 1.2, kit.dark, 0), 'DIFFERENCE')
        bx(kit, f'Gear {name} well liner', wx0 + .05, wx1 - .05, y - 1.75, y + 1.75, z + 1.0, z + 1.15, kit.dark, 0)
        parts = [kit.cylinder(f'Gear {name} trunnion', (x, y, z), .9, 2.6, kit.steel, 'Y', 16, .05),
                 kit.cylinder(f'Gear {name} strut', (x, y, z - leg/2), .55, leg, kit.steel, 'Z', 16, .05),
                 kit.cylinder(f'Gear {name} oleo', (x, y, z - leg + .9), .38, 1.8, kit.panel, 'Z', 16, .03),
                 bx(kit, f'Gear {name} pad', x - 1.6, x + 1.6, y - 1.2, y + 1.2, GROUND, GROUND + .8, kit.steel, .12)]
        hinge = pivot(f'Gear {name} hinge', (x, y, z),
                      {'retract_axis': 'Y', 'retract_angle_deg': retract, 'extended_angle_deg': 0.0,
                       'note': 'built extended; rotate about local Y by retract_angle_deg to stow'})
        parent_to(parts, hinge)
        hinges.append(hinge)
    return hinges


def build(kit):
    build_ramp(kit)
    build_gear(kit)
