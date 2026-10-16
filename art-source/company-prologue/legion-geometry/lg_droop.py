"""Anhedral: the wing arms angle down outboard of the torso wall.

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
it was, and keeps vertical walls vertical. The hangar pods, their decks and
lights, and the outer belt's arm run go down with the arm. The anchors
(mouths, launch points) move by the same function, and the exported hangar
records are corrected with droop().
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
    """A lg_wings.deck_record with its heights carried down by the anhedral."""
    record['launch_points'] = [[x, y, round(z+droop(y), 3)] for x, y, z in record['launch_points']]
    c = record['mouth']['centre']
    record['mouth']['centre'] = [c[0], c[1], round(c[2]+droop(c[1]), 3)]
    record['anhedral'] = {'angle_deg': ANGLE, 'root_abs_y': ROOT,
                          'note': 'the hangar floor and roof slope down outboard with the arm; heights given at the point'}
    return record
