"""Mudskipper bridge: the bow cockpit dome, its glazing and lamps, and the
bow fenders along the crenellated front edge.

The sprite's grey dome on the centreline (x 26..42.5, y +-8) is the command
cockpit; COCKPIT_PLAN in ms_hull is its footprint.
"""
from ms_kit import prism, section, facet, plane_cut, crisp
from ms_hull import COCKPIT_PLAN


def build(kit):
    # Cockpit: the sprite's grey bow dome on the centreline, standing above
    # the forebody deck and ahead of its front edge, glazed across its glacis.
    cp = prism(kit, 'Bow cockpit block', COCKPIT_PLAN, section(-3, 5.5, 12, 2, 2), kit.plate)
    for name, point, normal in [facet('Cockpit glacis', (38,0,12), (1,0), 35),
                                facet('Cockpit chin', (40,0,-3), (1,0), 140)]:
        plane_cut(cp, name, point, normal)
    crisp(cp)
    glaze = kit.box('Cockpit forward glazing', (39.9,0,10.8), (2.6,10,.3), kit.glass, 0)
    glaze.rotation_euler = (0, 0.6109, 0)
    for s in (1,-1):
        kit.box('Bow running lamp', (31.5,s*4.5,12.15), (1,1,.3), kit.lamp, 0)
    # Bow fenders: the crenellated front edge in the sprite (x 39..42).
    for y0, y1 in ((9.5,15),(16.5,22)):
        for s in (1,-1):
            kit.box('Bow fender block', (40.3,s*(y0+y1)/2,0), (3,y1-y0,6), kit.tan, .2)
