"""Mudskipper dorsal fittings: the cabin hood, stripe panels, the dorsal cargo
hatch, the turret-well coaming and the aft hatch panels either side of the
turret, plus the sparse deck furniture between them.

Everything here stands on the decks ms_hull builds (WAIST_DECK, FORE_DECK,
AFT_DECK). Small fittings seat by ray-cast (Kit.surface) so they follow the
deck's flats and avoid its shoulder chamfers. The turret ring at (-9, 0) is
ms_turrets: nothing here comes within 3.8 units of it. The hidden WS 002
marker at (17, 0) is left clear above the hood roof (1.5-unit radius).

Read off the vanilla sprite: the hood's arched brow ringed with dark studs
(x 14..21), two staggered columns of dark ports in the light deck panels
between the hood and the red stripes (passenger-cabin skylights, the
liner role), a few dark vent slots in the outer band, the tan 2x3 cargo
grating at x 0..6, the tan coaming round the turret well, and the white aft
hatches with 2x2 dark ports. Furniture (rails, lamps, beacon, mast, winch)
is lore: a civilian ferry that carries passengers and works small ports.
"""
import math
from ms_kit import prism, facet, plane_cut, crisp, boolean, material
from ms_hull import HOOD_PLAN, STRIPE_BEND, WAIST_DECK, FORE_DECK, AFT_DECK

HOOD_ROOF = 13.0                 # flat top of the hood (HOOD_PLAN profile)
ARCH_CENTRE, ARCH_A, ARCH_B = (14.0, 0.0), 7.4, 6.2   # the arched brow rim
WS002 = (17.0, 0.0)


def seat(kit, x, y, hx, hy):
    """Highest deck z under a hx-by-hy rectangle (half sizes) at (x, y)."""
    zs = []
    for px, py in ((x,y),(x-hx,y-hy),(x+hx,y-hy),(x-hx,y+hy),(x+hx,y+hy)):
        hit = kit.surface(px, py)
        if hit is None:
            raise RuntimeError(f'Deck fitting at ({x}, {y}) has no supporting hull surface')
        zs.append(hit[0].z)
    if max(zs)-min(zs) > .05:
        print(f'ms_deck: fitting at ({x:.1f}, {y:.1f}) straddles a deck step ({min(zs):.2f}..{max(zs):.2f})')
    return max(zs)


def framed(kit, name, loc, outer, inner, height, frame_mat, fill_mat, fill_drop=.12):
    """A raised frame (box minus box) with a dark or glazed fill sunk inside it."""
    x, y, z = loc
    fr = kit.box(name+' frame', (x,y,z+height/2), (*outer, height), frame_mat, 0)
    cut = kit.box('Temporary '+name+' opening', (x,y,z+height/2+.2), (*inner, height+.6), frame_mat, 0)
    boolean(fr, cut, 'DIFFERENCE')
    crisp(fr, .05)
    kit.box(name, (x,y,z+(height-fill_drop)/2), (inner[0]+.02, inner[1]+.02, height-fill_drop), fill_mat, 0)
    return fr


def materials(kit):
    if not hasattr(kit, 'deck_hood'):
        # The hood reads a shade lighter than the hull in the sprite.
        kit.deck_hood = material('Deck cabin hood plate', (.60, .60, .58), .25)
        kit.deck_skylight = material('Deck passenger skylight', (.04, .06, .08), .2, .12, emission=(.6, .5, .3))
        # A dim cabin glow behind dark glass, not a lit panel.
        kit.deck_skylight.node_tree.nodes['Principled BSDF'].inputs['Emission Strength'].default_value = .12
        kit.deck_beacon = material('Deck navigation beacon', (.8, .12, .08), emission=(1.0, .15, .08))


def build_hood(kit):
    # Dorsal cabin hood: the light raised housing over the centreline, the
    # passenger cabin of a liner-role ferry.
    hood = prism(kit, 'Dorsal cabin hood', HOOD_PLAN, [(0,WAIST_DECK-1),(0,11.5),(2,HOOD_ROOF)], kit.deck_hood)
    plane_cut(hood, 'Hood brow', *facet('Hood brow', (22,0,HOOD_ROOF), (1,0), 45)[1:])
    crisp(hood)
    # Segmented housing: two dark seam bands wrap the walls and roof, so the
    # hood reads as three bolted modules (aft trunk, mid cabin, arched brow).
    for x in (10.6, 14.0):
        for s in (1,-1):
            kit.box('Hood segment seam', (x,s*8.53,9.95), (.32,.12,3.1), kit.dark, 0)
            # Along the shoulder chamfer (y 8.5, z 11.5 up to y 6.5, z 13).
            sh = kit.box('Hood segment seam', (x,s*7.54,12.3), (.32,.12,2.5), kit.dark, 0)
            sh.rotation_euler = (s*math.atan2(2,1.5), 0, 0)
        kit.box('Hood segment seam', (x,0,HOOD_ROOF+.01), (.32,13,.04), kit.dark, 0)
    # Roof plates on the aft trunk and mid cabin: proud bolted covers whose
    # gaps show the seam bands.
    for x0, x1 in ((8.2,10.4),(10.8,13.8)):
        kit.box('Hood roof plate', ((x0+x1)/2,0,HOOD_ROOF+.1), (x1-x0,12.2,.2), kit.deck_hood, .06)
    # Cabin windows along both hood walls: a liner's passenger rows.
    for s in (1,-1):
        for x in (7.6, 9.3, 11.6, 13.0, 15.4, 17.0, 18.6, 20.2):
            kit.box('Hood cabin window', (x,s*8.52,10.3), (.95,.1,.75), kit.deck_skylight, 0)
    # Arched brow: the sprite's arch at the hood's forward end, a raised
    # crescent rim on the roof ringed with dark studs. Its middle stays open,
    # which also keeps WS 002 at (17, 0) clear.
    cx, cy = ARCH_CENTRE
    n = 14
    outer = [(cx+ARCH_A*math.sin(t), cy+ARCH_B*math.cos(t)) for t in (math.pi*k/n for k in range(n+1))]
    inner = [(cx+(ARCH_A-1.1)*math.sin(t), cy+(ARCH_B-1.1)*math.cos(t)) for t in (math.pi*(n-k)/n for k in range(n+1))]
    rim = prism(kit, 'Hood arched brow rim', outer+inner, [(0,HOOD_ROOF-.05),(0,HOOD_ROOF+.4),(.12,HOOD_ROOF+.52)], kit.deck_hood)
    crisp(rim, .04)
    for k in range(1, 12):
        t = math.pi*k/12
        kit.cylinder('Hood brow stud', (cx+(ARCH_A-.55)*math.sin(t), cy+(ARCH_B-.55)*math.cos(t), HOOD_ROOF+.6),
                     .2, .2, kit.dark, 'Z', 8, 0)
    # Dorsal airlock on the mid cabin roof: coaming ring, lid, hinge and wheel.
    ax, az = 12.3, HOOD_ROOF+.2
    kit.cylinder('Hood dorsal airlock coaming', (ax,0,az+.25), 1.45, .5, kit.steel, 'Z', 16, .05)
    kit.cylinder('Hood dorsal airlock gap', (ax,0,az+.52), 1.12, .06, kit.dark, 'Z', 16, 0)
    kit.cylinder('Hood dorsal airlock lid', (ax,0,az+.6), 1.05, .18, kit.deck_hood, 'Z', 16, .04)
    kit.box('Hood dorsal airlock hinge', (ax-1.45,0,az+.55), (.5,1.2,.4), kit.steel, .05)
    kit.cylinder('Hood dorsal airlock wheel', (ax+.1,0,az+.8), .45, .1, kit.dark, 'Z', 12, 0)
    # Grab rails along both roof edges of the aft trunk and mid cabin.
    for s in (1,-1):
        y = s*5.7
        kit.cylinder('Hood grab rail', (11.0,y,HOOD_ROOF+.95), .11, 5.6, kit.steel, 'X', 8, 0)
        for x in (8.5, 11.0, 13.5):
            kit.cylinder('Hood grab rail stanchion', (x,y,HOOD_ROOF+.55), .1, .8, kit.steel, 'Z', 8, 0)
    # Navigation beacon and a whip antenna on the aft trunk roof.
    kit.cylinder('Hood navigation beacon base', (8.9,-3.4,HOOD_ROOF+.5), .5, .6, kit.steel, 'Z', 10, .04)
    kit.cylinder('Hood navigation beacon lamp', (8.9,-3.4,HOOD_ROOF+1.0), .32, .4, kit.deck_beacon, 'Z', 10, .04)
    kit.cylinder('Hood antenna mount', (8.9,3.4,HOOD_ROOF+.45), .4, .5, kit.steel, 'Z', 10, .04)
    kit.cylinder('Hood antenna whip', (8.9,3.4,HOOD_ROOF+2.1), .07, 2.9, kit.steel, 'Z', 6, 0)
    kit.cylinder('Hood antenna tip', (8.9,3.4,HOOD_ROOF+3.6), .14, .14, kit.dark, 'Z', 6, 0)
    # Deck floodlights on the hood's aft wall, lighting the cargo hatch.
    for s in (1,-1):
        kit.box('Hood deck floodlight housing', (5.75,s*6.6,11.1), (.5,1.1,.7), kit.steel, .05)
        kit.box('Hood deck floodlight lens', (5.47,s*6.6,11.1), (.08,.85,.45), kit.lamp, 0)


def build_stripes(kit):
    # Stripe panels either side of the hood, flat on the deck. As in the
    # sprite, each runs straight aft of x 28 and then angles in at 45 degrees
    # to end against the cockpit's flank.
    for s in (1,-1):
        kit.box('Red stripe panel', (11,s*17.75,WAIST_DECK+.15), (16,3.5,.3), kit.stripe, .05)
        kit.box('Red stripe panel', (23.5,s*17.75,FORE_DECK+.15), (9,3.5,.3), kit.stripe, .05)
        bend = prism(kit, 'Red stripe panel angled', [(x,s*y) for x,y in STRIPE_BEND],
                     [(0,FORE_DECK),(0,FORE_DECK+.3)], kit.stripe)
        crisp(bend, .05)
        # The sprite breaks the stripe at x 8.5: a plate joint.
        kit.box('Red stripe panel joint', (8.5,s*17.75,WAIST_DECK+.31), (.25,3.55,.04), kit.dark, 0)


def build_waist_ports(kit):
    # Two staggered columns of dark ports in the light deck panels between the
    # hood and each stripe (sprite y 12.5 and 14.5, pitch 4): passenger-cabin
    # skylights, glazed and sunk in raised frames.
    cols = [(12.5, (1.8, 5.8, 9.8, 13.8)), (14.5, (3.2, 7.2, 11.2, 15.2, 19.9))]
    for s in (1,-1):
        for yc, xs in cols:
            for x in xs:
                z = seat(kit, x, s*yc, 1.0, .6)
                framed(kit, 'Waist deck skylight', (x,s*yc,z-.02), (2.0,1.2), (1.4,.65), .32,
                       kit.plate, kit.deck_skylight)
        # Vent slots in the outer band (sprite y 20..21 at x 19.5 and 23).
        for x in (19.8, 23.0):
            z = seat(kit, x, s*20.4, .6, .45)
            framed(kit, 'Deck edge vent slot', (x,s*20.4,z-.02), (1.2,.9), (.8,.5), .28, kit.plate, kit.dark)


def build_cargo_hatch(kit):
    # Dorsal cargo hatch: the sprite's tan grating at x 0..6, |y| < 7. A tan
    # coaming round a dark well, two rows of three grille leaves, and hinges.
    x0, x1, hy = 0.3, 5.9, 7.0
    xc = (x0+x1)/2
    z = seat(kit, xc, 0, (x1-x0)/2, hy)
    co = kit.box('Dorsal cargo hatch', (xc,0,z+.25), (x1-x0,2*hy,.5), kit.tan, 0)
    boolean(co, kit.box('Temporary cargo hatch opening', (xc,0,z+.25), (x1-x0-1.1,2*hy-1.1,1.5), kit.tan, 0), 'DIFFERENCE')
    crisp(co, .08)
    kit.box('Dorsal cargo hatch well', (xc,0,z+.08), (x1-x0-1.05,2*hy-1.05,.16), kit.dark, 0)
    lx = (x1-x0-1.1)/2          # two leaves along x
    ly = (2*hy-1.1)/3           # three leaves across y
    for i in range(2):
        cxl = x0+.55+lx*(i+.5)
        for j in range(3):
            cyl = -hy+.55+ly*(j+.5)
            # Each leaf: a tan rim and four slats over the dark well.
            fr = kit.box('Dorsal cargo hatch leaf', (cxl,cyl,z+.3), (lx-.25,ly-.25,.18), kit.tan, 0)
            boolean(fr, kit.box('Temporary hatch leaf opening', (cxl,cyl,z+.3), (lx-.85,ly-.85,1), kit.tan, 0), 'DIFFERENCE')
            for k in range(4):
                sx = cxl-(lx-.85)/2+(lx-.85)*(k+.5)/4
                kit.box('Dorsal cargo hatch slat', (sx,cyl,z+.3), (.2,ly-.8,.14), kit.tan, 0)
    # Hinge knuckles on the aft coaming edge, one per leaf column.
    for j in range(3):
        cyl = -hy+.55+ly*(j+.5)
        kit.cylinder('Dorsal cargo hatch hinge', (x0+.3,cyl,z+.58), .22, 1.4, kit.steel, 'Y', 8, 0)


def build_aft_deck(kit):
    # Turret-well coaming: the sprite's tan square round the turret ring,
    # kept outside 3.8 units of (-9, 0) so the barbette seats freely.
    z = AFT_DECK
    h = .45
    for s in (1,-1):
        kit.box('Turret well coaming', (-8.6,s*6.15,z+h/2), (9.6,1.9,h), kit.tan, .08)
    kit.box('Turret well coaming', (-4.4,0,z+h/2), (1.4,10.4,h), kit.tan, .08)
    kit.box('Turret well coaming', (-13.15,0,z+h/2), (.7,10.4,h), kit.tan, .08)
    for x, y in ((-4.4,4.4),(-4.4,-4.4),(-13.15,4.4),(-13.15,-4.4),(-6.5,6.15),(-11,6.15),(-6.5,-6.15),(-11,-6.15)):
        kit.cylinder('Turret well coaming bolt', (x,y,z+h+.06), .16, .12, kit.dark, 'Z', 6, 0)
    # The two white aft hatch panels either side of the turret, on the aft
    # deck's flat (x -13.3..-3.5): two leaves meeting at a central joint, with
    # the sprite's 2x2 dark ports, latches and end hinges.
    x0, x1, yc, wy = -13.3, -3.6, 15.2, 7.6
    xc = (x0+x1)/2
    for s in (1,-1):
        y = s*yc
        kit.box('Aft dorsal hatch panel', (xc,y,z+.15), (x1-x0,wy,.3), kit.plate, .08)
        kit.box('Aft dorsal hatch seam', (xc,y,z+.18), (.3,wy+.1,.3), kit.dark, 0)
        for px in (xc+2.25, xc-2.25):
            for py in (-1.5, 1.5):
                kit.cylinder('Aft dorsal hatch port rim', (px,y+py,z+.38), .72, .16, kit.steel, 'Z', 12, .03)
                kit.cylinder('Aft dorsal hatch port', (px,y+py,z+.44), .5, .06, kit.deck_skylight, 'Z', 12, 0)
        for py in (-2.6, 2.6):
            kit.box('Aft dorsal hatch latch', (xc,y+py,z+.42), (1.0,.45,.22), kit.steel, .04)
        for hx in (x0+.25, x1-.25):
            kit.cylinder('Aft dorsal hatch hinge', (hx,y,z+.4), .2, wy-1.6, kit.steel, 'Y', 8, 0)


def build_fore_deck(kit):
    # Forebody deck beside the cockpit. Outboard of each stripe bend the
    # sprite shows a dark block (x 30..33, y 18..21): a ventilation grille.
    # Ahead of it, at the deck's forward edge (x 35..37, y 12..18), a dark
    # recess: a mooring winch in its well, the ferry's small-port gear.
    for s in (1,-1):
        z = seat(kit, 31.7, s*19.6, 1.5, 1.4)
        framed(kit, 'Fore deck vent grille', (31.7,s*19.6,z-.02), (3.0,2.8), (2.4,2.2), .35, kit.plate, kit.dark)
        for k in range(4):
            kit.box('Fore deck vent grille louvre', (30.8+k*.6,s*19.6,z+.22), (.18,2.2,.12), kit.steel, 0)
        z = seat(kit, 35.6, s*15.3, 1.1, 2.6)
        kit.box('Fore deck winch well', (35.6,s*15.3,z+.04), (2.2,5.2,.1), kit.dark, 0)
        for dy in (-2.1, 2.1):
            kit.box('Fore deck winch cheek', (35.6,s*15.3+dy,z+.55), (1.3,.4,1.1), kit.steel, .06)
        kit.cylinder('Fore deck winch drum', (35.6,s*15.3,z+.7), .55, 3.8, kit.steel, 'Y', 12, .04)
        kit.cylinder('Fore deck winch cable', (35.6,s*15.3,z+.7), .62, 2.6, kit.tan, 'Y', 12, 0)


def build(kit):
    materials(kit)
    build_hood(kit)
    build_stripes(kit)
    build_waist_ports(kit)
    build_cargo_hatch(kit)
    build_aft_deck(kit)
    build_fore_deck(kit)
