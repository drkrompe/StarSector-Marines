"""Mudskipper pressure hull: forebody, waist, aft hull and their fittings.

Planform is measured off the vanilla sprite (graphics/ships/mudskipper/
mudskipper.png, 64x84, hull centre at sprite pixel 32,39 from bottom-left):
a broad forebody with sponsons (+-28.5 over x 21..33), a narrower cargo
waist (+-22.5 over x 0..19), and a broad aft hull (+-28.5) carrying the
drive units built by ms_engines. Sections are one keel chamfer, one vertical
wall and one shoulder chamfer; decks are flat and step between blocks.
Heights are art interpretation: the sprite supplies only the plan.
"""
from ms_kit import prism, section, boolean, facet, plane_cut, crisp

def clip(poly, a, b, c):
    """Keep the part of a plan polygon where a*x+b*y <= c (Sutherland-Hodgman)."""
    out = []
    for i in range(len(poly)):
        p, q = poly[i-1], poly[i]
        fp, fq = a*p[0]+b*p[1]-c, a*q[0]+b*q[1]-c
        if fq <= 0:
            if fp > 0:
                t = fp/(fp-fq)
                out.append((p[0]+(q[0]-p[0])*t, p[1]+(q[1]-p[1])*t))
            out.append(q)
        elif fp <= 0:
            t = fp/(fp-fq)
            out.append((p[0]+(q[0]-p[0])*t, p[1]+(q[1]-p[1])*t))
    return [p for i,p in enumerate(out) if abs(p[0]-out[i-1][0])+abs(p[1]-out[i-1][1]) > 1e-6]


KEEL = -8.5
FORE_DECK, WAIST_DECK, AFT_DECK = 9.0, 8.5, 9.5
AFT_FACE = -16.0     # aft hull ends here; the drive units bolt on behind it

FORE_PLAN = [(18.5,-22.5),(21,-27.5),(33,-28.5),(39,-24),(39.5,-20),
             (39.5,20),(39,24),(33,28.5),(21,27.5),(18.5,22.5)]
WAIST_PLAN = [(-2,-22.5),(19.5,-22.5),(19.5,22.5),(-2,22.5)]
AFT_PLAN = [(AFT_FACE,-28.5),(-4,-28.5),(-1,-27),(-1,27),(-4,28.5),(AFT_FACE,28.5)]
# Sponson plan (port; mirrored), the forebody outline outboard of y 21.5
# with no acute corners, so its chamfer insets cannot cross over.
SPONSON_PLAN = [(19,21.5),(19.5,23.5),(21,27.5),(33,28.5),(39,24),(39.3,21.5)]
# Forward end of each stripe (port; mirrored): from y 16..19.5 at x 28 it
# angles in to y 8.5..12 at x 35, beside the cockpit (y +-8).
STRIPE_BEND = [(28,16),(28,19.5),(35,12),(35,8.5)]
# The forebody core is the waist width carried forward; the sponsons outboard
# of it (ms_hull.build_forebody) make up the sprite's full +-28.5 shoulder.
FORE_CORE = clip(clip(FORE_PLAN, 0, 1, 22.5), 0, -1, 22.5)
COCKPIT_PLAN = [(26,-8),(39,-8),(42.5,-4.5),(42.5,4.5),(39,8),(26,8)]
HOOD_PLAN = [(6,-8.5),(25,-8.5),(25,8.5),(6,8.5)]

# Deck plateaus aft to fore for the manifest: (x0, x1, deck z).
DECKS = [(AFT_FACE,-1,AFT_DECK),(-2,19.5,WAIST_DECK),(18.5,39.5,FORE_DECK),
         (6,25,13.0),(26,42.5,12.0)]


def build_shell(kit):
    hull = prism(kit, 'Main hull - level datum Z0', AFT_PLAN, section(KEEL, 5, AFT_DECK), kit.plate)
    for name, plan, prof in [('Cargo waist block', WAIST_PLAN, section(KEEL, 4.5, WAIST_DECK, 2.5, 2)),
                             ('Forebody core block', FORE_CORE, [(2.5,KEEL),(0,KEEL+2.5),(0,FORE_DECK)])]:
        boolean(hull, prism(kit, name, plan, prof, kit.plate), 'UNION')
    # The forebody rakes at the bow: a glacis over the nose and a chin under it.
    for name, point, normal in [facet('Bow glacis', (37,0,FORE_DECK), (1,0), 30),
                                facet('Chin rake', (36,0,KEEL), (1,0), 145)]:
        plane_cut(hull, name, point, normal)
    crisp(hull, .25)
    kit.hull = hull
    return hull


def build_forebody(kit):
    # Sponsons: the grey-green outer band of the forebody, hung on the core
    # and stepped below its deck so the shoulder reads as a separate stratum.
    for s, label in ((1,'Port'),(-1,'Starboard')):
        plan = [(x,s*y) for x,y in SPONSON_PLAN]
        sp = prism(kit, f'{label} forebody sponson', plan, section(KEEL+1.5, 5, 7, 1.5, 1.5, proud=.3), kit.sponson)
        plane_cut(sp, 'Sponson chin', *facet('Sponson chin', (36,0,KEEL+1.5), (1,0), 145)[1:])
        crisp(sp)
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
    # Dorsal cabin hood: the light raised housing over the centreline.
    hood = prism(kit, 'Dorsal cabin hood', HOOD_PLAN, [(0,WAIST_DECK-1),(0,11.5),(2,13)], kit.plate)
    plane_cut(hood, 'Hood brow', *facet('Hood brow', (22,0,13), (1,0), 45)[1:])
    crisp(hood)
    # Stripe panels either side of the hood, flat on the deck. As in the
    # sprite, each runs straight aft of x 28 and then angles in at 45 degrees
    # to end against the cockpit's flank.
    for s in (1,-1):
        kit.box('Red stripe panel', (11,s*17.75,WAIST_DECK+.15), (16,3.5,.3), kit.stripe, .05)
        kit.box('Red stripe panel', (23.5,s*17.75,FORE_DECK+.15), (9,3.5,.3), kit.stripe, .05)
        bend = prism(kit, 'Red stripe panel angled', [(x,s*y) for x,y in STRIPE_BEND],
                     [(0,FORE_DECK),(0,FORE_DECK+.3)], kit.stripe)
        crisp(bend, .05)


def build_waist(kit):
    # Generous bay doors on both flanks of the cargo waist: two leaves on a
    # dark frame, proud of the wall.
    for s, label in ((1,'Port'),(-1,'Starboard')):
        kit.box(f'{label} cargo bay door frame', (9.5,s*22.6,-.5), (17,.4,8.6), kit.dark, 0)
        for x, leaf in ((5.3,'aft'),(13.7,'forward')):
            kit.box(f'{label} cargo bay door {leaf} leaf', (x,s*22.75,-.5), (8.1,.5,7.8), kit.plate, .1)
    # Dorsal cargo hatch between the hood and the turret (the sprite's tan
    # grating) and the frame around the turret well.
    kit.box('Dorsal cargo hatch', (2.5,0,WAIST_DECK+.15), (8,13,.3), kit.tan, .08)


def build_aft(kit):
    # The two white dorsal hatch panels either side of the turret.
    for s in (1,-1):
        kit.box('Aft dorsal hatch panel', (-7.5,s*13.5,AFT_DECK+.15), (11,7.5,.3), kit.plate, .08)
        kit.box('Aft dorsal hatch seam', (-7.5,s*13.5,AFT_DECK+.18), (.3,7.6,.3), kit.dark, 0)
    # Flank lockers: the tan boxes on the aft hull's outer edge.
    for s, label in ((1,'Port'),(-1,'Starboard')):
        kit.box(f'{label} flank locker', (-9,s*27.4,2), (9,3.2,8), kit.tan, .2)


def build(kit):
    build_shell(kit)
    build_forebody(kit)
    build_waist(kit)
    build_aft(kit)
