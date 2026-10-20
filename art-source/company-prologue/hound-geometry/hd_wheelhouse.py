"""The bridge: the deckhouse forward of the turret, made to read as one.

Owner review (pass 3) read the deckhouse as the ship's bridge and asked for
it to look like one. hd_hull still builds the deckhouse solid ('Hull shielded
hold deckhouse', a name WS 001's feed chute ray-casts against) with a steep
raked window wall; this module cuts the glazing into it and adds what makes
a bridge read at cinematic distance:
- a raked forward window band under an overhanging visor brow, with heavy
  mullions and a sill; the starboard end stays armour where the WS 001 feed
  chute leaves the hold (Y -6), behind an armoured feed port;
- side window bands wrapping aft from the forward corners, two small ports
  beside the aft door;
- roof rails with a gap at the access ladder, a lattice sensor mast with a
  yard, nav lamps and a radome, a searchlight and a small dish;
- a person-height door (the pass-2 aft access hatch, re-proportioned in
  hd_hull), a step, a tread walkway from the door along the port side, and
  deck-edge rails.
Scale: 0.88 m per unit, so a 1.8 m person is 2.05 units. The door is 1.6 x
2.3 units (1.4 x 2.0 m), the window band 1.25 units (1.1 m) deep with its
sill at 1.5 units (1.3 m) above the bridge floor, the rails 1.15 units
(1.0 m) high. Nothing here reaches outside the deckhouse plan apart from
the brow, which stays over the body's forward deck, so the silhouette is
unchanged and nothing enters WS 002's traverse (its barrel tip stays aft
of X 4.3 and below the roof).
Emissives: none. hound_lights.png has no light on the deckhouse forward of
its three amber roof lamps (hd_hull), so the bridge glazing is dark tinted
glass (`Wheelhouse glazing`, the surfacing's glass class) and the mast's
yard lamps are fittings with dark lenses (ship-detail-guide.md, Lamps).
"""
import math
import bpy
from mathutils import Matrix, Vector
from hd_kit import boolean, material

HOUSE = 'Hull shielded hold deckhouse'
DECK_TOP = 6.42          # top of the deck plates around the deckhouse
ROOF = 9.7
# Forward window wall: a plane through (18.5, 7.6) leaning back 20 degrees
# (hd_hull cuts it); the window band sits on it.
FRONT_X, FRONT_Z, LEAN = 18.5, 7.6, math.radians(20)
FRONT_BAND = (7.8, 9.05)          # z of the band's bottom and top on the face
FRONT_SPAN = (-4.4, 8.7)          # y; starboard of -4.4 is the feed-port armour
SIDE_BAND = (7.65, 8.65)          # z
SIDE_SPAN = (11.4, 16.6)          # x
WALL_Y = {'port': 11.0, 'starboard': -10.5}
AFT_PORTS = [(3.0, 4.4), (-2.0, -.6)]   # y spans of the small aft ports
MAST = (15.9, 1.2)
LADDER_Y = (-6.4, -5.2)


def framed(kit, name, centre, u, v, n, du, dv, dn, mat, bevel=0.0):
    """A box whose local x, y, z lie along u, v, n (unit vectors) at centre."""
    ob = kit.box(name, (0, 0, 0), (du, dv, dn), mat, bevel)
    m = Matrix((u, v, n)).transposed().to_4x4()
    m.translation = centre
    ob.matrix_world = m
    return ob


def unlit_glass():
    """Dark tinted bridge glass, not lit: the lights layer has no viewport here."""
    m = bpy.data.materials.get('Wheelhouse glazing')
    return m or material('Wheelhouse glazing', (.05, .08, .09), .2, .15)


def band(kit, house, label, centre, u, v, n, width, height, mullions, sill=True):
    """Recess a window band into a wall face and glaze it with mullions.
    centre lies on the face; u runs along the band, v up it, n outward."""
    c = Vector(centre)
    cut = framed(kit, 'Temporary window recess', c, u, v, n, width, height, .6, kit.dark)
    boolean(house, cut)
    framed(kit, f'Wheelhouse {label} glazing', c-n*.26, u, v, n, width-.04, height-.04, .06, unlit_glass())
    framed(kit, f'Wheelhouse {label} glazing backing', c-n*.32, u, v, n, width, height, .04, kit.dark)
    for k in range(mullions+1):
        t = -width/2+k*width/mullions
        t = max(-width/2+.11, min(width/2-.11, t))
        framed(kit, f'Wheelhouse {label} mullion {k+1}', c+u*t-n*.12, u, v, n, .22, height, .3, kit.steel, .03)
    framed(kit, f'Wheelhouse {label} header', c+v*(height/2+.08)+n*.02, u, v, n, width+.3, .18, .2, kit.steel, .03)
    if sill:
        framed(kit, f'Wheelhouse {label} sill', c-v*(height/2+.1)+n*.1, u, v, n, width+.4, .2, .45, kit.steel, .04)


def rail(kit, name, pts, z0, height, mat, pitch=1.6):
    """Stanchions along a polyline and two rails (mid and top)."""
    P = [Vector((x, y, z0)) for x, y in pts]
    k = 0
    for a, b in zip(P, P[1:]):
        d = b-a
        length = d.length
        steps = max(1, round(length/pitch))
        u = d.normalized()
        for s in range(steps+(1 if b is P[-1] else 0)):
            p = a+u*(length*s/steps)
            k += 1
            kit.box(f'{name} stanchion {k}', (p.x, p.y, z0+height/2), (.12, .12, height), mat, 0)
        for h, tag in ((height*.5, 'mid'), (height, 'top')):
            c = (a+b)/2
            ob = kit.box(f'{name} {tag} rail {k}', (c.x, c.y, z0+h), (length, .1, .1), mat, 0)
            ob.rotation_euler[2] = math.atan2(d.y, d.x)


def mast(kit):
    x, y = MAST
    z0, z1 = ROOF, ROOF+4.2
    kit.box('Wheelhouse mast step', (x, y, z0+.15), (1.5, 1.5, .3), kit.steel, .05)
    # Four tapering legs and three cross-braced bands: a lattice mast.
    for k, (sx, sy) in enumerate(((1, 1), (1, -1), (-1, -1), (-1, 1)), 1):
        a = Vector((x+sx*.6, y+sy*.6, z0+.3))
        b = Vector((x+sx*.18, y+sy*.18, z1))
        d = b-a
        ob = kit.box(f'Wheelhouse mast leg {k}', (a+b)/2, (.13, .13, d.length), kit.steel, 0)
        ob.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    for k, f in enumerate((.25, .5, .75), 1):
        z = z0+.3+(z1-z0-.3)*f
        r = .6-(.6-.18)*f
        for j, (dx, dy, w, l) in enumerate(((r, 0, .08, 2*r), (-r, 0, .08, 2*r), (0, r, 2*r, .08), (0, -r, 2*r, .08)), 1):
            kit.box(f'Wheelhouse mast band {k}.{j}', (x+dx, y+dy, z), (w, l, .08), kit.steel, 0)
    kit.box('Wheelhouse mast yard', (x, y, z1-.9), (.16, 3.6, .16), kit.steel, 0)
    # Dark lenses: the lights layer has no pixel at the mast (a shot's effects may light them).
    nav = material('Wheelhouse nav lamp', (.2, .05, .03), 0, .3)
    for s in (1, -1):
        kit.cylinder(f"Wheelhouse mast yard lamp {'port' if s > 0 else 'starboard'}", (x, y+s*1.85, z1-.75), .14, .25, nav, 'Z', 8, 0)
    kit.cylinder('Wheelhouse radome', (x, y, z1+.25), .55, .5, kit.cream, 'Z', 12, .1)
    kit.cylinder('Wheelhouse radome cap', (x, y, z1+.55), .3, .1, kit.cream, 'Z', 12, 0)
    kit.box('Wheelhouse whip antenna', (x-.2, y-.3, z1+1.4), (.05, .05, 2.2), kit.steel, 0)


def roof(kit):
    # Rail round the roof edge, inset from the chamfer; a gap where the
    # ladder arrives on the aft edge.
    edge = [(7.2, LADDER_Y[0]), (7.2, -9.5), (17.0, -9.5), (17.0, 10.0), (7.2, 10.0), (7.2, LADDER_Y[1])]
    rail(kit, 'Wheelhouse roof rail', edge, ROOF, 1.15, kit.steel)
    mast(kit)
    # Searchlight on a pedestal, forward port corner, trained ahead.
    kit.cylinder('Wheelhouse searchlight pedestal', (16.2, 7.8, ROOF+.35), .3, .7, kit.steel, 'Z', 8, .03)
    kit.box('Wheelhouse searchlight yoke', (16.2, 7.8, ROOF+.85), (.3, 1.2, .3), kit.steel, .03)
    kit.cylinder('Wheelhouse searchlight drum', (16.3, 7.8, ROOF+1.15), .45, .8, kit.steel, 'X', 12, .04)
    kit.cylinder('Wheelhouse searchlight lens', (16.72, 7.8, ROOF+1.15), .38, .05, kit.cream, 'X', 12, 0)
    # Small comms dish on a post, starboard aft of the feed armour.
    kit.cylinder('Wheelhouse dish post', (15.4, -7.2, ROOF+.4), .14, .8, kit.steel, 'Z', 8, 0)
    dish = kit.cylinder('Wheelhouse dish', (15.4, -7.2, ROOF+1.0), .7, .12, kit.cream, 'Z', 12, .03)
    dish.rotation_euler = (math.radians(-30), math.radians(20), 0)


def walkway(kit):
    grate = material('Wheelhouse tread grating', (.11, .115, .12), .6, .55)
    z = DECK_TOP
    # Port side, between the deckhouse wall and the deck edge, then round
    # the aft wall to the door.
    kit.box('Wheelhouse walkway port', (11.6, 11.42, z+.04), (11.4, .72, .08), grate, 0)
    kit.box('Wheelhouse walkway aft', (5.0, 5.9, z+.04), (1.2, 11.8, .08), grate, 0)
    for k in range(13):
        x = 6.3+k*.85
        kit.box(f'Wheelhouse walkway port tread {k+1}', (x, 11.42, z+.1), (.08, .66, .04), kit.steel, 0)
    rail(kit, 'Wheelhouse deck rail', [(17.0, 11.75), (6.4, 11.75)], z, 1.15, kit.steel)
    # Ladder up the aft wall to the roof, beside the door.
    for s in LADDER_Y:
        kit.box('Wheelhouse ladder stile', (5.9, s, (z+ROOF+.9)/2), (.1, .1, ROOF+.9-z), kit.steel, 0)
    for k in range(7):
        kit.box(f'Wheelhouse ladder rung {k+1}', (5.9, sum(LADDER_Y)/2, z+.45+k*.5), (.08, LADDER_Y[1]-LADDER_Y[0], .08), kit.steel, 0)
    kit.box('Wheelhouse door step', (5.2, .9, z+.09), (.8, 1.9, .18), kit.steel, .03)


def build(kit):
    house = bpy.data.objects[HOUSE]
    # Forward band on the raked window wall.
    n = Vector((math.cos(LEAN), 0, math.sin(LEAN)))
    v = Vector((-math.sin(LEAN), 0, math.cos(LEAN)))
    u = Vector((0, 1, 0))
    zc = sum(FRONT_BAND)/2
    xc = FRONT_X-math.tan(LEAN)*(zc-FRONT_Z)
    height = (FRONT_BAND[1]-FRONT_BAND[0])/math.cos(LEAN)
    width = FRONT_SPAN[1]-FRONT_SPAN[0]
    band(kit, house, 'forward window', (xc, sum(FRONT_SPAN)/2, zc), u, v, n, width, height, 9)
    # Visor brow over the forward band, overhanging the deck ahead.
    brow = kit.box('Wheelhouse visor brow', (18.25, -.2, ROOF-.18), (2.0, 19.0, .3), kit.rust, .06)
    brow.rotation_euler[1] = math.radians(8)
    for k, y in enumerate((-8.6, -4.4, 0.2, 4.6, 8.9), 1):
        kit.box(f'Wheelhouse visor brow gusset {k}', (17.65, y, ROOF-.55), (.9, .2, .6), kit.steel, .02)
    # Armoured feed port where WS 001's belt chute leaves the hold.
    z = 8.8
    x = FRONT_X-math.tan(LEAN)*(z-FRONT_Z)
    framed(kit, 'Wheelhouse feed port armour', Vector((x, -6.0, z)), u, v, n, 2.6, 2.0, .4, kit.steel, .06)
    # Side bands, port and starboard.
    for label, y in WALL_Y.items():
        s = 1 if y > 0 else -1
        nn = Vector((0, s, 0))
        uu = Vector((1, 0, 0)) if s < 0 else Vector((-1, 0, 0))
        band(kit, house, f'{label} window', (sum(SIDE_SPAN)/2, y, sum(SIDE_BAND)/2), uu, Vector((0, 0, 1)), nn,
             SIDE_SPAN[1]-SIDE_SPAN[0], SIDE_BAND[1]-SIDE_BAND[0], 4)
    # Two small ports in the aft wall, either side of the door.
    for k, (y0, y1) in enumerate(AFT_PORTS, 1):
        band(kit, house, f'aft port {k}', (6.0, (y0+y1)/2, 8.1), Vector((0, -1, 0)), Vector((0, 0, 1)),
             Vector((-1, 0, 0)), y1-y0, .8, 1, sill=False)
    roof(kit)
    walkway(kit)


def scale_figure(kit):
    """A 1.8 m mannequin (2.05 units at 0.88 m per unit) for review renders only."""
    skin = material('Scale figure', (.85, .75, .2), 0, .6)
    x, y, z = 8.2, 11.42, DECK_TOP+.08
    kit.box('Scale figure legs', (x, y, z+.47), (.32, .42, .94), skin, .05)
    kit.box('Scale figure torso', (x, y, z+1.3), (.36, .55, .76), skin, .06)
    kit.cylinder('Scale figure head', (x, y, z+1.88), .14, .3, skin, 'Z', 12, .05)
