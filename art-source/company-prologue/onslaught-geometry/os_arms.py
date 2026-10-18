"""The two gun arms: base mass, gun gallery and the armour that belongs to them.

Split out of os_hull/os_armour so the arms can be shaped on their own. Every
object built here is collected in ARM_PARTS[side] and then posed by tilt():
the arms need not share the core's plane (owner, round 2), and tilting them
lets more of their guns bear (owner).
"""
import math
from os_kit import arched_mass, mirror_y, plane_cut, prism, facet_normal
from os_hull import (ARM_GALLERY, ARM_INNER, ARM_OUTER, DECK, GALLERY_TOP, gallery_plan, keel_curve, mass,
                     port_plan)
from os_armour import _side, band, clip_x, ridge

ARM_PARTS = {'port': [], 'starboard': []}

# The arm's own section (os_hull.ARM copied and deepened): a heavy, round-bellied
# forearm rather than a shallow platform. (keel, deck, bilge in, bilge h, shoulder
# in, shoulder h, crown, crown w), in the arm's own frame before tilt().
ARM = (-40.0, 8.0, 20.0, 24.0, 16.0, 18.0, 3.0, 10.0)

# The pose (owner notes 5, 8, 9). The arm guns' slot angles all point outboard
# and forward (WS 001-006 at 65-75 deg, the Devastators at 80, WS 010/011 to the
# beam), with arcs reaching from dead ahead round to the quarter. So each arm is
# rolled outboard-down about its own long axis and pitched nose-down about a
# horizontal axis, both through its root at the core: the gun deck then faces
# forward and outboard, toward the middle of those arcs, every turret can
# depress below the horizon on its own side, and the deck steps up from the nose
# aft, so the guns aft fire over the ones ahead of them and over the TPC noses.
# The drop puts the whole forearm below the core's terrace and glacis, a level
# of its own hanging off the shoulder. The plan is not yawed: the sprite fixes it.
ROLL = 16.0      # deg, outboard edge down
PITCH = 6.0      # deg, nose down
DROP = 7.0       # units, straight down after the rotation
ROOT = (40.0, 78.0, -10.0)   # port; the pivot: the arm's root against the core, at mid-depth
AXIS = (222.0, -36.0)        # port; the arm's long axis in plan (along its gun gallery)


def arm_cuts(ob, pad=0.0):
    """os_hull.arm_cuts for the deeper arm: the keel sweeps up to the TPC nose and
    rounds up into the heel."""
    keel_curve(ob, 'arm keel', 96, 214, ARM[0], 36.0, (1, 0), n=5, pad=pad)
    keel_curve(ob, 'arm heel', 14, -16, ARM[0], 14.0, (-1, 0), n=3, pad=pad)


def pose(s):
    """The arm's rigid pose as a 4x4 world matrix (s = 1 port, -1 starboard)."""
    from mathutils import Matrix, Vector
    u = Vector((AXIS[0], s*AXIS[1], 0.0)).normalized()
    w = Vector((0, 0, 1)).cross(u)
    p = Vector((ROOT[0], s*ROOT[1], ROOT[2]))
    r = Matrix.Rotation(math.radians(PITCH), 4, w) @ Matrix.Rotation(math.radians(-s*ROLL), 4, u)
    return Matrix.Translation(p+Vector((0, 0, -DROP))) @ r @ Matrix.Translation(-p)


def tilt(kit, side, s):
    """Pose one arm's parts: bake the rigid pose into every mesh (so the later ray
    casts against kit.hull see it) and move any empty."""
    import bpy
    m = pose(s)
    for ob in ARM_PARTS[side]:
        if ob.type == 'MESH':
            ob.data.transform(m @ ob.matrix_world)
            ob.matrix_world = ob.matrix_world.__class__.Identity(4)
            ob.data.update()
        else:
            ob.matrix_world = m @ ob.matrix_world
    bpy.context.view_layer.update()


def build(kit):
    import bpy
    for side, s in (('port', 1), ('starboard', -1)):
        before = set(bpy.data.objects)
        plan = port_plan(ARM_OUTER, ARM_INNER)
        arm = mass(kit, f'Hull {side} arm', plan if s > 0 else mirror_y(plan), ARM, kit.plate)
        arm_cuts(arm)
        g = arched_mass(kit, f'Hull {side} arm gallery', gallery_plan(ARM_GALLERY, s < 0), DECK['arm']-6,
                        GALLERY_TOP, tumble_in=6.0, tumble_h=7.0, crown=1.5, crown_w=6.0, mat=kit.plate, step=2.0)
        plane_cut(g, 'gallery rise', (80, 0, GALLERY_TOP+1.5), facet_normal((1, 0), 3.0))
        plane_cut(g, 'gallery nose', (192, 0, GALLERY_TOP-4.0), facet_normal((1, 0), 24))
        kit.hull += [arm, g]
        # Arm belts: sloped plates from the gallery down to the outer edge.
        for k, (xa, xb, mat) in enumerate(((210, 151, kit.orange), (149, 106, kit.grey), (104, 72, kit.orange),
                                           (70, 22, kit.grey), (20, -14, kit.grey)), 1):
            b = band(kit, f'Armour {side} arm belt {k}', clip_x(ARM_OUTER, xa, xb), 1.5, 13.0,
                     ridge(-9.0, -2.0, 12.0, 13.0), mat, s)
            plane_cut(b, 'belt nose', (192, 0, 12.0), facet_normal((1, 0), 18))
        # Arm steps: grey blocks stepping down outboard of the aft belt, tops falling forward.
        for k, poly in enumerate(([(100, 110), (100, 122), (87, 123), (86, 111)],
                                  [(62, 113), (62, 124.5), (44, 124.5), (44, 114)],
                                  [(40, 115), (40, 125), (24, 129), (24, 117)],
                                  [(19, 114), (19, 127), (6, 121), (6, 113)]), 1):
            top = 10.0-.5*k
            b = prism(kit, f'Armour {side} arm step {k}', _side(poly, s), [(0.0, -8.0), (0.0, top-1.0), (1.0, top)],
                      kit.grey, bevel=.25)
            plane_cut(b, 'step tooth', (poly[0][0], 0, top-.5), facet_normal((1, 0), 28))
        # Inner coaming facing the notch (an edge listed bow to stern on the arm's
        # inner side is a right-hand edge, so it is offset to the right).
        inner = [p for p in reversed(ARM_INNER) if p[0] >= 104]
        c = band(kit, f'Armour {side} arm coaming', clip_x(inner, 206, 106), -10.0, 9.0, ridge(-9.0, -2.0, 10.0, 9.0),
                 kit.orange, s)
        plane_cut(c, 'coaming nose', (192, 0, 10.0), facet_normal((1, 0), 18))
        # TPC nose cheeks either side of the barrel.
        for k, poly in enumerate(([(213.5, 64.8), (213.5, 66.4), (205.5, 66.2), (205.5, 63.6)],
                                  [(213.5, 73.6), (213.5, 78.6), (205.5, 80.6), (205.5, 74.2)]), 1):
            ch = prism(kit, f'Armour {side} nose cheek {k}', _side(poly, s), [(0.0, -2.0), (0.0, 9.0), (.8, 9.8)],
                       kit.grey, bevel=.25)
            plane_cut(ch, 'cheek nose', (190, 0, 12.0), facet_normal((1, 0), 16))
        ARM_PARTS[side] = [o for o in bpy.data.objects if o not in before]
        tilt(kit, side, s)
        shoulder(kit, side, s)


# The shoulder: the sprite's orange broadside fin at the arm root, kept on the
# core rather than posed with the arm, and carried down into the arm, so it is
# a raised armoured shoulder that the lowered, rolled forearm hangs from. Its
# inboard edge already lies over the core's flank, so it covers the joint.
def shoulder(kit, side, s):
    fin = [(48, 78), (30, 97), (-8, 106), (-14, 92), (-6, 68), (20, 72)]
    prism(kit, f'Armour {side} broadside fin', _side(fin, s), [(0.0, -10.0), (0.0, 12.0), (2.0, 14.0)],
          kit.orange, bevel=.2)
    crest = [(38, 83), (26, 95), (-2, 101), (-7, 91), (0, 75), (18, 78)]
    prism(kit, f'Armour {side} broadside fin crest', _side(crest, s), [(0.0, 13.0), (0.0, 16.4), (1.6, 18.0)],
          kit.orange, bevel=.2)
