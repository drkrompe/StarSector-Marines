"""The stern: aft body with its open machinery wells, the tail and the two aft legs.

Split out of os_hull so the stern can be shaped on its own (owner: silhouette
work; the critic: the stern read as a flat wall). Plans and specs stay in
os_hull for now (AFT_OUTER, TAIL_HALF, LEG_PORT, WELL_PORT, WELL_FLOOR, AFT,
TAIL, LEG), which os_machinery also reads; copy them here before changing them
and keep os_machinery in step.
"""
import bpy
from os_kit import boolean, ccw, facet_normal, mirror_y, plane_cut, prism
from os_hull import (AFT, AFT_OUTER, LEG, LEG_PORT, TAIL, TAIL_HALF, WELL_FLOOR, WELL_PORT, aft_cuts, full, leg_cuts,
                     mass, tail_cuts)
from os_armour import band, shell_strip, slab


def build(kit):
    aft = mass(kit, 'Hull aft body', full(AFT_OUTER + [(-102, 21)]), AFT, kit.plate)
    aft_cuts(aft)
    for s in (1, -1):
        plan = ccw(WELL_PORT) if s > 0 else mirror_y(ccw(WELL_PORT))
        cutter = prism(kit, 'Temporary machinery well cutter', plan, [(0.0, WELL_FLOOR), (0.0, 30.0)], kit.dark, bevel=0)
        boolean(aft, cutter)
    tail = mass(kit, 'Hull tail', full(TAIL_HALF), TAIL, kit.plate)
    tail_cuts(tail)
    legs = {}
    for side, s in (('port', 1), ('starboard', -1)):
        plan = ccw(LEG_PORT) if s > 0 else mirror_y(ccw(LEG_PORT))
        leg = mass(kit, f'Hull {side} aft leg', plan, LEG, kit.plate)
        leg_cuts(leg)
        legs[side] = leg
        leg_inner = [(-36, 52), (-60, 47), (-104, 46), (-112, 47), (-121, 48.5)]
        st = band(kit, f'Armour {side} leg stripe', leg_inner, -6.5, 5.0, slab(8.0, 15.0), kit.orange, s)
        plane_cut(st, 'stripe aft', (-104, 0, 15.0), facet_normal((-1, 0), 20))
    kit.hull += [aft, tail, *legs.values()]
    shell_strip(kit, 'Armour aft keel strake 1', aft, 1, 34, 2.6, 2.4, kit.grey, xr=(-98, -8))
    shell_strip(kit, 'Armour aft keel strake 3', aft, 1, -34, 2.6, 2.4, kit.grey, xr=(-98, -8))
    shell_strip(kit, 'Armour tail keel strake', tail, 1, 0, 2.6, 2.4, kit.grey, xr=(-110, -12))
    return {'aft': aft, 'tail': tail, 'legs': legs}
