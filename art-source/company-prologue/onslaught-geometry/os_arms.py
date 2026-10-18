"""The two gun arms: base mass, gun gallery and the armour that belongs to them.

Split out of os_hull/os_armour so the arms can be shaped on their own. Every
object built here is collected in ARM_PARTS[side] and then posed by tilt():
the arms need not share the core's plane (owner, round 2), and tilting them
lets more of their guns bear (owner).
"""
import math
from os_kit import arched_mass, mirror_y, plane_cut, prism, facet_normal
from os_hull import (ARM, ARM_GALLERY, ARM_INNER, ARM_OUTER, DECK, GALLERY_TOP, arm_cuts, gallery_plan, mass,
                     port_plan)
from os_armour import _side, band, clip_x, ridge

ARM_PARTS = {'port': [], 'starboard': []}


def tilt(kit, side, s):
    """Pose one arm's parts (identity for now)."""
    return


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
        # Broadside fin, two tiers.
        fin = [(48, 78), (30, 97), (-8, 106), (-14, 92), (-6, 68), (20, 72)]
        prism(kit, f'Armour {side} broadside fin', _side(fin, s), [(0.0, 2.0), (0.0, 12.0), (2.0, 14.0)],
              kit.orange, bevel=.2)
        crest = [(38, 83), (26, 95), (-2, 101), (-7, 91), (0, 75), (18, 78)]
        prism(kit, f'Armour {side} broadside fin crest', _side(crest, s), [(0.0, 13.0), (0.0, 16.4), (1.6, 18.0)],
              kit.orange, bevel=.2)
        ARM_PARTS[side] = [o for o in bpy.data.objects if o not in before]
        tilt(kit, side, s)
