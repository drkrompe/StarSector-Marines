"""Anhedral: the wing arms angle down outboard of the torso wall; the hangars under them stay level.

Owner review: "taper those wings so they angle down as well". The arms
already thin outboard (lg_wings' underside taper); this lowers them as well.
It runs after lg_hull, lg_bridge and lg_wings have built, and before anything
that seats itself by ray-cast (engines, fittings, turrets), as a shear: every
vertex of every part built so far that lies outboard of |Y| ROOT drops by
(|Y| - ROOT) x tan(ANGLE). It goes by position rather than by name, because
belt step pieces and shared seam underlays cross the torso wall onto the arms;
the torso itself lies inboard of the wall and is untouched (the aft body's
notch edge, out to |Y| 86, dips by at most a unit). A shear rather
than a rotation keeps the plan view, and so the sprite silhouette, exactly as
it was, and keeps vertical walls vertical. The arm, its armour, its deck
fittings, the well coamings and the outer belt's arm run go down with it.

Pass 2b: the hangars do NOT. Nothing parks, rolls or is towed on a 10-degree
cant, so lg_wings builds every hangar part (pod, lane gear, gallery, anchors,
keyable empties and their children) in its final, level position and flags it
'lg_level'; this leaves flagged parts and all parented children alone. Only
the pod's top, sunk into the arm, follows the droop, and lg_wings applies that
itself. The exported records are therefore level too: correct() leaves launch
points and mouths where lg_wings put them and only notes the anhedral.
"""
import math
import bpy

ROOT = 80.0                  # the torso wall the arms spring from
ANGLE = 10.0                 # degrees of anhedral


def droop(y):
    """Height change at lateral position y (negative outboard of the root)."""
    return -max(0.0, abs(y)-ROOT)*math.tan(math.radians(ANGLE))


def apply(kit):
    bpy.context.view_layer.update()
    for ob in list(bpy.context.scene.objects):
        if ob.parent is not None:     # children follow their parent
            continue
        if ob.get('lg_level'):        # built level in its final position (the hangars)
            continue
        if ob.type not in ('MESH', 'EMPTY') or ob.name.startswith('Camera'):
            continue
        if ob.type == 'EMPTY':
            ob.location.z += droop(ob.location.y)
            continue
        if ob.data.users > 1:   # a linked duplicate: shear its own copy, not the shared mesh
            ob.data = ob.data.copy()
        mw = ob.matrix_world
        inv = mw.inverted()
        moved = False
        for v in ob.data.vertices:
            w = mw @ v.co
            dz = droop(w.y)
            if dz:
                w.z += dz
                v.co = inv @ w
                moved = True
        if moved:
            ob.data.update()


def correct(record):
    """A lg_wings.deck_record, annotated with the anhedral (its hangar parts are built level, so no heights move)."""
    record['anhedral'] = {'angle_deg': ANGLE, 'root_abs_y': ROOT, 'hangar_level': True,
                          'note': 'the arm above (deck, armour, wells, turrets) slopes down outboard; the hangar '
                                  'under it is level: floor, launch points, mouths, doors and gallery heights are '
                                  'final as given. Arm deck at the outboard lane wall is z %.2f.'
                                  % (4.0+droop(128.0))}
    return record
