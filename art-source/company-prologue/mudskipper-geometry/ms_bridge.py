"""Mudskipper bridge: the bow cockpit, its glazing, lamps and sensors, and
the bow fenders along the crenellated front edge.

The sprite's grey dome on the centreline (x 26..42.5, y +-8) is the command
cockpit; COCKPIT_PLAN in ms_hull is its footprint. Read off the sprite
(rows 3..20, cols 23..41):
  x 24..27   a dark band behind the dome: the cab's aft wall is set back
             in a dark recess under the roof's aft edge;
  x 27..30   the roof falls aft, split into three plates by seams at y +-5;
  x 30..33   the bright crown, with two yellow lamps at y +-4.5;
  x 33..37   a dark band: the forward viewport, raked under a visor brow,
             a sensor housing at its centre;
  x 37       a pale floodlight on the centreline at the windscreen sill;
  x 39..40   a hole on the centreline: a tow eye well with its pin;
  x 39..42   tan corner bumpers with lamps at y +-6.5, two yellow lamps at
             y +-3.5 on the nose, and a dark front face (the crenellation),
             carried outboard by the tan fender blocks at y 9.5..22.
"""
import math
import bpy
from mathutils import Vector
from ms_kit import prism, section, facet, plane_cut, crisp, boolean, material
from ms_hull import COCKPIT_PLAN

CROWN = 12.6         # cab roof
BODY_TOP = 9.6       # top of the armoured body the cab stands on
SCREEN_TOP = 33.6    # windscreen meets the crown here
SCREEN_RAKE = 38     # degrees below horizontal
NOSE_GLACIS = (38.8, 27)   # where the nose glacis starts, and its rake


def _materials(kit):
    if getattr(kit, 'bridge_smoked', None) is None:
        kit.bridge_smoked = material('Bridge smoked glazing', (.012, .016, .018), .4, .06,
                                     emission=(.012, .02, .024))
        kit.bridge_nav_red = material('Bridge navigation lamp red', (.6, .05, .04), emission=(.9, .08, .05))
        kit.bridge_nav_green = material('Bridge navigation lamp green', (.05, .55, .2), emission=(.08, .85, .3))
        kit.bridge_white = material('Bridge anticollision lamp', (.85, .85, .8), emission=(.95, .95, .9))


def _on_plane(kit, name, origin, rake, along, out, dims, mat, y=0, bevel=.05):
    """A box lying in a plane that falls forward (+X) at `rake` degrees from
    `origin`: `along` units down the slope, `out` units off its surface."""
    a = math.radians(rake)
    t = Vector((math.cos(a), 0, -math.sin(a)))
    n = Vector((math.sin(a), 0, math.cos(a)))
    c = Vector(origin) + t*along + n*out
    ob = kit.box(name, (c.x, y, c.z), dims, mat, bevel)
    ob.rotation_euler = (0, a, 0)
    return ob


def _cab_cuts(ob, aft_rake, back=0.0):
    """The windscreen rake forward and the roof's fall aft, optionally set
    `back` units behind the surfaces (for the dark core behind the seams)."""
    for name, point, normal in [facet('Bridge windscreen', (SCREEN_TOP, 0, CROWN), (1, 0), SCREEN_RAKE),
                                facet('Bridge aft roof', (30, 0, CROWN), (-1, 0), aft_rake)]:
        plane_cut(ob, name, point - normal*back, normal)


def build_body(kit):
    # The armoured body the cab stands on, its nose raked into a glacis.
    body = prism(kit, 'Bow cockpit block', COCKPIT_PLAN, section(-3, 8.8, BODY_TOP, 2, .8), kit.plate)
    x0, rake = NOSE_GLACIS
    for name, point, normal in [facet('Cockpit nose glacis', (x0, 0, BODY_TOP), (1, 0), rake),
                                facet('Cockpit chin', (40, 0, -3), (1, 0), 140)]:
        plane_cut(body, name, point, normal)
    # Tow eye: the sprite's centreline hole at x 39..40, a well with a pin.
    well = kit.box('Temporary tow eye well cutter', (39.7, 0, 9.6), (1.4, 2.0, 3.0), kit.dark, 0)
    boolean(body, well, 'DIFFERENCE')
    # A seam groove round the nose: the body is two armour strata.
    slab = kit.box('Temporary nose seam slab', (36, 0, 6.05), (22, 20, .28), kit.dark, 0)
    inner = prism(kit, 'Temporary nose seam core', COCKPIT_PLAN, [(.2, 5.8), (.2, 6.3)], kit.dark)
    boolean(slab, inner, 'DIFFERENCE')
    boolean(body, slab, 'DIFFERENCE')
    crisp(body)
    prism(kit, 'Bridge nose seam', COCKPIT_PLAN, [(.16, 5.9), (.16, 6.2)], kit.dark)
    kit.box('Bridge tow eye well floor', (39.7, 0, 8.15), (1.3, 1.9, .3), kit.dark, 0)
    kit.cylinder('Bridge tow eye pin', (39.7, 0, 8.85), .3, 2.2, kit.steel, 'Y', 12, .04)
    return body


def build_cab(kit):
    # The crew cab: three armour plate sections with dark seams between
    # them (the sprite's seams at y +-5), set on the body.
    for label, y0, y1, aft_rake in (('centre', -4.5, 4.5, 29), ('port', 4.9, 7.7, 35), ('starboard', -7.7, -4.9, 35)):
        plan = [(26.8, y0), (37.2, y0), (37.2, y1), (26.8, y1)]
        cab = prism(kit, f'Bridge cab {label} section', plan,
                    [(.3, 9.0), (0, 9.3), (0, CROWN-.35), (.35, CROWN)], kit.plate)
        _cab_cuts(cab, aft_rake)
        crisp(cab, .12)
    core = kit.box('Bridge cab seam core', (31.8, 0, 10.6), (9.6, 14.6, 3.2), kit.dark, 0)
    _cab_cuts(core, 29, back=.35)
    # The dark band behind the dome: the cab's aft wall set into a recess.
    kit.box('Bridge aft recess', (26.6, 0, 9.55), (1.1, 14.6, 1.7), kit.dark, 0)


def build_viewport(kit):
    # Forward viewport: smoked glazing behind heavy mullions, under a brow.
    origin = (SCREEN_TOP, 0, CROWN)
    kit.bridge_glazing = _on_plane(kit, 'Cockpit forward glazing', origin, SCREEN_RAKE, 2.35, .05,
                                   (4.4, 14.8, .2), kit.bridge_smoked, bevel=0)
    for y in (0, 2.35, -2.35, 4.7, -4.7, 7.45, -7.45):
        _on_plane(kit, 'Bridge windscreen mullion', origin, SCREEN_RAKE, 2.3, .2,
                  (4.6, .55, .44), kit.plate, y)
    _on_plane(kit, 'Bridge windscreen sill', origin, SCREEN_RAKE, 4.5, .15, (.7, 15.4, .55), kit.plate)
    # Visor brow: a plate peak over the top of the glazing, falling forward.
    brow = kit.box('Bridge visor brow', (34.0, 0, 12.45), (1.8, 15.4, .6), kit.plate, .1)
    brow.rotation_euler = (0, math.radians(12), 0)
    # The sprite's orange-brown blob at x 34: a sensor housing on the brow.
    kit.box('Bridge brow sensor housing', (34.1, 0, 13.1), (.9, 1.8, .6), kit.steel, .08)
    kit.box('Bridge brow sensor lens', (34.55, 0, 13.1), (.1, 1.3, .32), kit.bridge_smoked, 0)
    # Navigation lamps at the brow ends: red to port, green to starboard.
    kit.box('Bridge navigation lamp port', (34.2, 7.75, 12.55), (.6, .3, .4), kit.bridge_nav_red, .04)
    kit.box('Bridge navigation lamp starboard', (34.2, -7.75, 12.55), (.6, .3, .4), kit.bridge_nav_green, .04)
    # Side windows: two small ports in each flank section.
    for s in (1, -1):
        for x in (31.1, 32.7):
            kit.box('Bridge side window', (x, s*7.74, 10.95), (1.2, .2, .8), kit.bridge_smoked, 0)
            for dx, dz, dims in ((0, .55, (1.6, .3, .3)), (0, -.55, (1.6, .3, .3)),
                                 (.75, 0, (.3, .3, 1.4)), (-.75, 0, (.3, .3, 1.4))):
                kit.box('Bridge side window frame', (x+dx, s*7.78, 10.95+dz), dims, kit.steel, .04)


def build_roof(kit):
    # The sprite's two yellow lamps on the crown, in low steel housings.
    for s in (1, -1):
        kit.box('Bridge lamp housing', (31.5, s*4.0, CROWN+.15), (1.2, .9, .4), kit.steel, .06)
        kit.box('Bow running lamp', (31.6, s*4.0, CROWN+.4), (.8, .65, .25), kit.lamp, .04)
    # Bolted service covers on the flank sections' crowns.
    for s in (1, -1):
        kit.box('Bridge roof cover seam', (32.0, s*6.25, CROWN+.03), (2.3, 2.1, .08), kit.dark, 0)
        kit.box('Bridge roof cover', (32.0, s*6.25, CROWN+.06), (2.0, 1.8, .12), kit.plate, .03)
        for dx in (-.75, .75):
            for dy in (-.65, .65):
                kit.cylinder('Bridge roof cover bolt', (32.0+dx, s*6.25+dy, CROWN+.15), .1, .1, kit.steel, 'Z', 6, 0)
    # Short mast on the crown: a square tube, a yard with two whips and an
    # anticollision lamp on top.
    kit.box('Bridge mast footing', (30.9, 0, CROWN+.15), (1.4, 1.4, .3), kit.steel, .06)
    kit.box('Bridge mast', (30.9, 0, CROWN+2.3), (.5, .5, 4.4), kit.steel, .05)
    kit.box('Bridge mast yard', (30.9, 0, CROWN+3.6), (.3, 3.4, .3), kit.steel, .04)
    for s in (1, -1):
        kit.cylinder('Bridge mast whip antenna', (30.9, s*1.5, CROWN+4.75), .07, 2.3, kit.steel, 'Z', 6, 0)
    kit.cylinder('Bridge mast anticollision lamp', (30.9, 0, CROWN+4.7), .28, .5, kit.bridge_white, 'Z', 8, .03)
    # Comm dish to starboard and sensor blister to port on the aft roof slope.
    kit.cylinder('Bridge comm dish pedestal', (28.3, -2.3, 12.2), .35, 2.2, kit.steel, 'Z', 8, .03)
    bpy_dir = Vector((.55, -.35, .76)).normalized()
    bpy.ops.mesh.primitive_cone_add(vertices=12, radius1=1.15, radius2=.3, depth=.45,
                                    location=Vector((28.3, -2.3, 13.5)) + bpy_dir*.1)
    dish = kit.finish(bpy.context.object, 'Bridge comm dish', kit.plate, .03)
    dish.rotation_euler = (-bpy_dir).to_track_quat('Z', 'Y').to_euler()
    feed = kit.cylinder('Bridge comm dish feed', tuple(Vector((28.3, -2.3, 13.5)) + bpy_dir*.6), .08, 1.1, kit.steel, 'Z', 6, 0)
    feed.rotation_euler = bpy_dir.to_track_quat('Z', 'Y').to_euler()
    kit.cylinder('Bridge sensor blister base', (28.3, 2.3, 11.9), .85, 1.0, kit.steel, 'Z', 8, .04)
    bpy.ops.mesh.primitive_cone_add(vertices=8, radius1=.85, radius2=.45, depth=.5, location=(28.3, 2.3, 12.65))
    kit.finish(bpy.context.object, 'Bridge sensor blister', kit.bridge_smoked, .03)


def build_nose(kit):
    x0, rake = NOSE_GLACIS
    # The sprite's pale centreline floodlight at the windscreen sill.
    kit.box('Bridge floodlight housing', (38.05, 0, BODY_TOP+.3), (.9, 1.5, .6), kit.steel, .06)
    kit.box('Bridge floodlight lens', (38.52, 0, BODY_TOP+.3), (.08, 1.1, .38), kit.lamp, 0)
    # Two yellow lamps on the nose glacis (sprite x 40, y +-3.5).
    for s in (1, -1):
        _on_plane(kit, 'Bridge nose lamp housing', (x0, 0, BODY_TOP), rake, 1.9, .12, (1.0, 1.1, .35), kit.steel, s*3.2)
        _on_plane(kit, 'Bow running lamp', (x0, 0, BODY_TOP), rake, 1.9, .3, (.7, .8, .2), kit.lamp, s*3.2)
    # Docking collar on the nose face below the glacis, on the centreline.
    ring = kit.cylinder('Bridge docking collar', (42.35, 0, 3.4), 1.6, .45, kit.steel, 'X', 16, .05)
    bore = kit.cylinder('Temporary collar bore', (42.5, 0, 3.4), 1.0, .6, kit.dark, 'X', 16, 0)
    boolean(ring, bore, 'DIFFERENCE')
    kit.cylinder('Bridge docking collar hatch', (42.3, 0, 3.4), 1.05, .3, kit.dark, 'X', 16, 0)
    for k in range(6):
        a = k*math.pi/3 + math.pi/6
        kit.cylinder('Bridge docking collar bolt', (42.58, 1.3*math.cos(a), 3.4+1.3*math.sin(a)), .11, .1, kit.plate, 'X', 6, 0)
    # Tan corner bumpers on the nose's raked corners, each with a lamp.
    for s in (1, -1):
        out = Vector((.7071, s*.7071, 0))
        c = Vector((40.75, s*6.25, 3.0)) - out*.25
        bump = kit.box('Bow fender block', tuple(c), (3.6, .7, 4.0), kit.tan, .15)
        bump.rotation_euler = (0, 0, -s*math.pi/4)
        lamp = kit.box('Bow running lamp', tuple(c + Vector((0, 0, 2.12))), (.8, .45, .25), kit.lamp, .04)
        lamp.rotation_euler = (0, 0, -s*math.pi/4)
        for k in (-1, 1):
            p = c + out*.38 + Vector((.7071, -s*.7071, 0))*k*1.2
            b = kit.cylinder('Bow fender bolt', tuple(p), .17, .14, kit.steel, 'X', 6, 0)
            b.rotation_euler = (0, math.pi/2, s*math.pi/4)


def build_fenders(kit):
    # Bow fenders: the crenellated front edge in the sprite (x 39..42).
    # Each is a tan bumper on a steel backing plate bolted to the bow, with
    # a steel rub strake and bolt heads on its face; the inner pair carry
    # the sprite's yellow lamps at y +-14.
    for y0, y1 in ((9.5, 15), (16.5, 22)):
        for s in (1, -1):
            yc, w = s*(y0+y1)/2, y1-y0
            kit.box('Bow fender backing plate', (39.4, yc, 0), (1.3, w, 6), kit.steel, .12)
            kit.box('Bow fender block', (40.8, yc, 0), (2.0, w-.4, 5.2), kit.tan, .25)
            kit.box('Bow fender rub strake', (41.8, yc, 0), (.25, w-1.4, .7), kit.steel, .05)
            for dy in (-(w/2-.9), w/2-.9):
                for z in (-1.7, 1.7):
                    kit.cylinder('Bow fender bolt', (41.82, yc+dy, z), .17, .14, kit.steel, 'X', 6, 0)
            if y0 < 10:
                kit.box('Bow fender lamp housing', (40.4, s*14.0, 2.75), (.9, .9, .3), kit.steel, .04)
                kit.box('Bow running lamp', (40.4, s*14.0, 2.95), (.65, .65, .2), kit.lamp, .03)


def build(kit):
    _materials(kit)
    build_body(kit)
    build_cab(kit)
    build_viewport(kit)
    build_roof(kit)
    build_nose(kit)
    build_fenders(kit)
