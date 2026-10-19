"""Kite (P) pass 2 hull detail: cockpit and cabin, crew doors, the ventral
boarding collar, the belly's heat shield, plating and the pirate crew's
add-ons, lamps and RCS.

Read off the sprite (aeroshuttle_pirates.png, see kp_hull for the mapping):
- the cockpit: the cream arc at rows 7-10 (X 28..31) across the spine's nose
  is the windscreen over the pilots' deck, raked into the spine's nose fall;
- the cabin: the bright dots in two files either side of the spine (cols 36-37
  and 44-45, rows 7-26, X 12..31) are lit windows in the spine's side walls,
  the boarding party's cabin running back to the turret;
- the dark brown patches over the shoulders and wing roots of the pirate
  skin (against the base skin's clean grey) are the crew's welded scrap
  plates; the red X across the hull is paint, left to the texture pass;
- the wing tips' dark trailing blocks carry the navigation lamps.

The belly is not in the sprite. As an aeroshuttle it lands and re-enters, so
it carries an entry heat shield, gear doors and a ventral boarding collar: a
boarding shuttle mates its collar to the prize's hull and cuts through.

Every part is seated by ray casts onto the hull solids, never at a
hard-coded height.
"""
import math
import bpy
from mathutils import Matrix, Vector
from kp_kit import bx, blocks, boolean, mirror, prism
import kp_hull

SPINE = 'Hull dorsal spine'
FUSE = 'Hull fuselage'
WING = 'Hull wing body'
HUMP = 'Hull aft hump'

# Spine nose fall (kp_hull.build_spine): a plane through (29.5, 0, SPINE_DECK)
# falling forward at 38 degrees.
NOSE_FALL = (29.5, 38.0)
# Cabin windows in each spine side wall, X centres (the sprite's dot files).
WINDOWS_X = (25.0, 21.8, 18.6, 15.4, 12.2, 9.0, 5.8)
WINDOW_W, WINDOW_H = 1.5, 1.0
CREW_DOOR_X = (20.0, 23.2)        # on the neck's side wall, forward of the shoulders
CREW_DOOR_Z = (-2.9, 1.0)
COLLAR_X, COLLAR_R = 6.0, 3.6     # ventral boarding collar under the cabin


def ray(obj, origin, direction):
    """(point, normal) where a ray meets the evaluated object, or None."""
    bpy.context.view_layer.update()
    ev = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mw = obj.matrix_world
    inv = mw.inverted()
    o = inv @ Vector(origin)
    d = (inv.to_3x3() @ Vector(direction)).normalized()
    hit, p, n, _ = ev.ray_cast(o, d)
    if not hit:
        return None
    return mw @ p, (mw.to_3x3() @ n).normalized()


def frame(centre, normal, xdir=(1, 0, 0)):
    """4x4 matrix at centre whose local Z is the normal and local X lies along xdir."""
    z = Vector(normal).normalized()
    xv = Vector(xdir)
    x = (xv - z*xv.dot(z))
    if x.length < 1e-6:
        x = Vector((0, 1, 0)) - z*z.y
    x.normalize()
    y = z.cross(x)
    m = Matrix((x, y, z)).transposed().to_4x4()
    m.translation = Vector(centre)
    return m


def oriented_box(kit, name, centre, normal, dims, mat, bevel=.04, xdir=(1, 0, 0)):
    """Box of dims (along xdir, across, along the normal) centred at centre."""
    ob = kit.box(name, (0, 0, 0), dims, mat, bevel)
    ob.matrix_world = frame(centre, normal, xdir)
    return ob


def recess(kit, target, name, centre, normal, dims, xdir=(1, 0, 0)):
    """Cut a pocket of dims into target, its mouth on the surface at centre."""
    c = Vector(centre) - Vector(normal).normalized()*dims[2]/2 + Vector(normal).normalized()*.3
    cutter = oriented_box(kit, 'Temporary ' + name + ' cutter', c, normal, (dims[0], dims[1], dims[2] + .6),
                          kit.dark, 0, xdir)
    boolean(bpy.data.objects[target], cutter, 'DIFFERENCE')


# --- cockpit and cabin -----------------------------------------------------------

def cockpit(kit):
    x0, angle = NOSE_FALL
    a = math.radians(angle)
    n = Vector((math.sin(a), 0, math.cos(a)))
    up = Vector((-math.cos(a), 0, math.sin(a)))      # up the slope
    zc = kp_hull.SPINE_DECK - 1.25
    centre = Vector((x0 + (kp_hull.SPINE_DECK - zc)/math.tan(a), 0, zc))
    length = 1.9/math.sin(a)                          # 1.9 units of height on the face
    recess(kit, SPINE, 'windscreen', centre, n, (length, 4.4, .45), xdir=up)
    glass = oriented_box(kit, 'Bridge windscreen glazing', centre - n*.32, n, (length - .2, 4.2, .12), kit.glass,
                         .02, xdir=up)
    # Mullions: a centre post and two frames dividing four panes.
    posts = []
    for y in (-1.05, 0.0, 1.05):
        p = oriented_box(kit, 'Bridge windscreen mullion', centre - n*.22 + Vector((0, y, 0)), n,
                         (length - .2, .16, .16), kit.steel, .02, xdir=up)
        posts.append(p)
    _join(posts, 'Bridge windscreen mullions')
    # A brow over the windscreen, the sprite's cream arc.
    brow_c = centre + up*(length/2 + .25) + n*.12
    oriented_box(kit, 'Bridge windscreen brow', brow_c, n, (.5, 4.9, .35), kit.cream, .06, xdir=up)
    return glass


def cabin_windows(kit):
    spine = bpy.data.objects[SPINE]
    wall_z = kp_hull.SPINE_DECK - 2.3
    panes = []
    for s in (1, -1):
        for x in WINDOWS_X:
            hit = ray(spine, (x, s*20, wall_z), (0, -s, 0))
            if not hit:
                continue
            p, nrm = hit
            recess(kit, SPINE, 'cabin window', p, nrm, (WINDOW_W + .3, WINDOW_H + .3, .35))
            panes.append(oriented_box(kit, 'Cabin window pane', p - nrm*.25, nrm, (WINDOW_W, WINDOW_H, .1),
                                      kit.window, .02))
    _join(panes, 'Cabin window glazing')


def crew_doors(kit):
    """A crew door in each neck side wall: a recessed leaf with its frame."""
    fuse = bpy.data.objects[FUSE]
    x0, x1 = CREW_DOOR_X
    z0, z1 = CREW_DOOR_Z
    for s, side in ((1, 'port'), (-1, 'starboard')):
        hit = ray(fuse, ((x0 + x1)/2, s*30, (z0 + z1)/2), (0, -s, 0))
        if not hit:
            continue
        p, nrm = hit
        recess(kit, FUSE, f'{side} crew door', p, nrm, (x1 - x0 + .4, z1 - z0 + .4, .4), xdir=(1, 0, 0))
        oriented_box(kit, f'Hull {side} crew door leaf', p - nrm*.3, nrm, (x1 - x0, z1 - z0, .15), kit.panel, .04)
        oriented_box(kit, f'Hull {side} crew door handle', p - nrm*.2 + Vector((-(x1 - x0)/2 + .45, 0, 0)), nrm,
                     (.18, .7, .12), kit.steel, .02)
        # A step bar under the sill.
        oriented_box(kit, f'Hull {side} crew door step', p + nrm*.12 + Vector((0, 0, -(z1 - z0)/2 - .35)), nrm,
                     (x1 - x0 - .4, .14, .3), kit.steel, .02)


# --- belly ------------------------------------------------------------------------

def belly(kit):
    """Heat shield over the flat fuselage and wing keels, gear doors and the
    ventral boarding collar."""
    k = kp_hull.KEEL
    plan = mirror([(30.5, 0), (30.5, 6.0), (27.0, 9.5), (19.0, 10.5), (14.0, 16.5), (-19.0, 16.5),
                   (-21.0, 13.5), (-22.5, 5.5), (-22.8, 0)])
    prism(kit, 'Hull belly heat shield', plan, [(0, k - .12), (0, k + .05)], kit.shield)
    wk = kp_hull.WING_KEEL
    for s, side in ((1, 'port'), (-1, 'starboard')):
        wplan = [(12.0, s*21.0), (9.5, s*27.0), (-2.0, s*29.0), (-7.0, s*37.5), (-14.5, s*37.5), (-14.5, s*31.5),
                 (-19.5, s*31.5), (-20.0, s*21.0)]
        prism(kit, f'Hull {side} wing heat shield', wplan, [(0, wk - .1), (0, wk + .05)], kit.shield)
    # Gear doors: nose (one) and mains (two), as leaf panels proud of the shield.
    doors = [('nose', 24.0, 0.0, 5.5, 2.6), ('port main', -6.0, 10.5, 7.0, 3.2), ('starboard main', -6.0, -10.5, 7.0, 3.2)]
    for name, x, y, l, w in doors:
        bx(kit, f'Hull {name} gear door', x - l/2, x + l/2, y - w/2, y + w/2, k - .22, k - .1, kit.panel, .05)
        blocks(kit, f'Hull {name} gear door seam', [(x - l/2 - .12, x + l/2 + .12, y - w/2 - .12, y - w/2 - .04, k - .2, k - .1),
                                                   (x - l/2 - .12, x + l/2 + .12, y + w/2 + .04, y + w/2 + .12, k - .2, k - .1),
                                                   (x - .04, x + .04, y - w/2, y + w/2, k - .24, k - .1)], kit.dark)
    # Ventral boarding collar: a docking ring with clamps and a hatch.
    x, r = COLLAR_X, COLLAR_R
    kit.cylinder('Hull ventral boarding collar ring', (x, 0, k - .55), r, .9, kit.steel, 'Z', 48, .08)
    kit.cylinder('Hull ventral boarding collar seal', (x, 0, k - 1.02), r - .25, .08, kit.dark, 'Z', 48, 0)
    kit.cylinder('Hull ventral boarding hatch', (x, 0, k - .98), r - .8, .12, kit.panel, 'Z', 32, .03)
    clamps = []
    for q in range(6):
        a = math.tau*q/6 + math.pi/6
        cx, cy = x + (r + .2)*math.cos(a), (r + .2)*math.sin(a)
        c = kit.box('Hull ventral boarding clamp', (cx, cy, k - .6), (1.0, .8, 1.1), kit.steel, .06)
        c.rotation_euler[2] = a
        clamps.append(c)
    _join(clamps, 'Hull ventral boarding clamps')


# --- plating and the pirate crew's add-ons -------------------------------------------

def plate_on(kit, obj, x, y, l, w, t, mat, name, angle=0.0):
    """A plate l x w x t laid on obj's top surface at (x, y), turned by angle (deg)."""
    hit = ray(obj, (x, y, 60), (0, 0, -1))
    if not hit:
        return None
    p, nrm = hit
    a = math.radians(angle)
    return oriented_box(kit, name, p + nrm*(t/2 - .05), nrm, (l, w, t), mat, .05, xdir=(math.cos(a), math.sin(a), 0))


def bolts_on(kit, obj, pts, name):
    out = []
    for x, y in pts:
        hit = ray(obj, (x, y, 60), (0, 0, -1))
        if hit:
            p, nrm = hit
            out.append(oriented_box(kit, name, p + nrm*.08, nrm, (.22, .22, .18), kit.steel, .03))
    _join(out, name)


def seam_strip(kit, obj, p0, p1, width=.14, n=24):
    """A dark seam line draped over obj's top between plan points p0 and p1:
    a quad strip laid on ray-cast surface points, a hair proud."""
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    L = math.hypot(dx, dy)
    ux, uy = -dy/L*width/2, dx/L*width/2
    rows = []
    for k in range(n + 1):
        t = k/n
        hit = ray(obj, (x0 + dx*t, y0 + dy*t, 60), (0, 0, -1))
        if not hit:
            continue
        p, nrm = hit
        q = p + nrm*.035
        rows.append([(q.x + ux, q.y + uy, q.z), (q.x - ux, q.y - uy, q.z)])
    if len(rows) < 2:
        return None
    verts = [v for r in rows for v in r]
    faces = [(2*i, 2*i + 1, 2*i + 3, 2*i + 2) for i in range(len(rows) - 1)]
    return kit.mesh('Hull seam', verts, faces, kit.dark)


def plating(kit):
    """Panel seams draped over the fuselage shoulders and the wing: frames
    across the body and chordwise lines on the wing, the module joints of a
    built-up hull."""
    fuse, wing = bpy.data.objects[FUSE], bpy.data.objects[WING]
    seams = []
    for x in (17.5, 10.5, 3.5, -3.5, -10.5, -17.5):         # body frames across the shoulders
        for s in (1, -1):
            seams.append(seam_strip(kit, fuse, (x, s*11.0), (x, s*19.2)))
    for s in (1, -1):                                        # long seam down each shoulder
        seams.append(seam_strip(kit, fuse, (18.0, s*15.5), (-20.0, s*15.5), n=40))
        for y in (24.0, 31.0):                               # wing chordwise lines
            x0 = 10.0 if y < 28 else -2.0
            seams.append(seam_strip(kit, wing, (x0, s*y), (-15.5 if y < 32 else -15.0, s*y), n=30))
        for x in (4.0, -4.5, -11.5):                         # wing spanwise lines
            y1 = {4.0: 28.0, -4.5: 33.6, -11.5: 39.4}[x]
            seams.append(seam_strip(kit, wing, (x, s*20.0), (x, s*y1), n=20))
    _join(seams, 'Hull panel seams')


def scrap_armour(kit):
    """The pirate refit: welded scrap plates over the shoulders, the wing roots
    and the nose cheeks, a little mismatched, each with a bolt row."""
    fuse, wing = bpy.data.objects[FUSE], bpy.data.objects[WING]
    plates = [  # (obj, x, y, l, w, angle)
        (fuse, 12.5, 15.0, 6.5, 3.6, -8), (fuse, 5.0, 16.5, 7.0, 3.0, 4), (fuse, -8.0, 15.5, 8.5, 3.4, -3),
        (fuse, 12.0, -15.5, 5.5, 3.2, 10), (fuse, -1.0, -16.5, 9.0, 3.0, -5), (fuse, -14.0, -15.0, 6.0, 3.8, 6),
        (wing, -2.0, 33.0, 5.5, 3.0, 50), (wing, -11.0, 36.0, 6.0, 3.4, 2), (wing, 0.0, -31.0, 6.5, 2.8, -40),
        (wing, -12.5, -35.5, 5.0, 3.5, -6), (wing, 6.0, 26.0, 5.0, 2.4, 10), (wing, 7.0, -25.5, 4.5, 2.6, -12),
        (fuse, 28.0, 9.0, 4.0, 2.4, 35), (fuse, 28.5, -9.5, 3.6, 2.4, -38),
    ]
    out, bolt_pts = [], {}
    for k, (obj, x, y, l, w, ang) in enumerate(plates):
        pl = plate_on(kit, obj, x, y, l, w, .45, kit.scrap, 'Pirate scrap plate', ang)
        if pl:
            out.append(pl)
            a = math.radians(ang)
            ux, uy = math.cos(a), math.sin(a)
            pts = [(x + ux*t - uy*sgn*(w/2 - .35), y + uy*t + ux*sgn*(w/2 - .35))
                   for sgn in (1, -1) for t in [l*(q/4 - .5)*.85 for q in range(5)]]
            bolt_pts.setdefault(obj.name, []).extend(pts)
    _join(out, 'Pirate scrap plates')
    for name, pts in bolt_pts.items():
        bolts_on(kit, bpy.data.objects[name], pts, 'Pirate scrap plate bolts')
    # Weld-on bash bars across the nose, where a boarding shuttle meets a hull.
    bars = []
    for z_off, y in ((0, 0),):
        hit = ray(fuse, (60, 0, 0.5), (-1, 0, 0))
        if hit:
            p, nrm = hit
            for dz in (-1.6, 0.0, 1.6):
                bars.append(oriented_box(kit, 'Pirate bash bar', p + Vector((.25, 0, dz)), Vector((1, 0, 0)),
                                         (.35, 3.0 - abs(dz)*.6, .5), kit.steel, .05, xdir=(0, 0, 1)))
    _join(bars, 'Pirate nose bash bars')


# --- dorsal fittings, lamps and RCS ---------------------------------------------------

def dorsal(kit):
    spine, hump = bpy.data.objects[SPINE], bpy.data.objects[HUMP]
    # Antenna mast and a sensor pod on the hump's aft end, behind the turret's
    # 200-degree arc (it trains +-100 from ahead), a spotlight forward.
    hit = ray(hump, (-19.5, 0, 60), (0, 0, -1))
    if hit:
        p, _ = hit
        kit.cylinder('Sensor mast', (p.x, 0, p.z + 1.6), .14, 3.2, kit.steel, 'Z', 8, .02)
        kit.box('Sensor mast pod', (p.x, 0, p.z + 2.9), (.9, .5, .5), kit.steel, .06)
        kit.cylinder('Sensor whip antenna', (p.x - .5, .9, p.z + 2.4), .05, 4.8, kit.steel, 'Z', 6, 0)
    hit = ray(spine, (27.0, 0, 60), (0, 0, -1))
    if hit:
        p, _ = hit
        kit.box('Dorsal spotlight housing', (p.x, 0, p.z + .35), (1.0, .9, .7), kit.steel, .06)
        kit.cylinder('Dorsal spotlight lens', (p.x + .52, 0, p.z + .4), .3, .06, kit.lamp, 'X', 16, 0)
    # A dorsal hatch on the hump aft of the turret.
    hit = ray(hump, (-15.0, 0, 60), (0, 0, -1))
    if hit:
        p, nrm = hit
        oriented_box(kit, 'Hull dorsal hatch', p + nrm*.06, nrm, (2.6, 2.2, .18), kit.panel, .04)
        oriented_box(kit, 'Hull dorsal hatch rim', p + nrm*.02, nrm, (3.0, 2.6, .1), kit.dark, .02)


def lamps(kit):
    wing = bpy.data.objects[WING]
    for s, mat, side in ((1, kit.nav_red, 'port'), (-1, kit.nav_green, 'starboard')):
        hit = ray(wing, (-11.5, s*60, -1.5), (0, -s, 0))
        if hit:
            p, nrm = hit
            oriented_box(kit, f'Lamp {side} wing-tip nav', p + nrm*.15, nrm, (.8, .5, .3), mat, .05)
    fuse = bpy.data.objects[FUSE]
    hit = ray(fuse, (-60, 0, 1.5), (1, 0, 0))
    if hit:
        p, nrm = hit
        oriented_box(kit, 'Lamp stern white', p + nrm*.15, nrm, (.6, .6, .3), kit.lamp, .05, xdir=(0, 1, 0))
    hit = ray(bpy.data.objects[SPINE], (12.0, 0, 60), (0, 0, -1))
    if hit:
        p, nrm = hit
        kit.cylinder('Lamp dorsal beacon', (p.x, 0, p.z + .2), .3, .4, kit.nav_red, 'Z', 12, .03)
    # Nose landing lights either side of the chin.
    for s, side in ((1, 'port'), (-1, 'starboard')):
        hit = ray(fuse, (60, s*4.5, -2.5), (-1, 0, 0))
        if hit:
            p, nrm = hit
            oriented_box(kit, f'Lamp {side} landing light', p + nrm*.08, nrm, (.9, .6, .14), kit.lamp, .03,
                         xdir=(0, 1, 0))


def rcs(kit):
    """RCS quads on the nose cheeks and the wing tips' leading corners (the
    maneuveringjets system), each with an anchor per nozzle."""
    fuse, wing = bpy.data.objects[FUSE], bpy.data.objects[WING]
    sites = [('nose port', fuse, (33.0, 30, 0.5), (0, -1, 0)), ('nose starboard', fuse, (33.0, -30, 0.5), (0, 1, 0)),
             ('wing port', wing, (-7.5, 60, -1.4), (0, -1, 0)), ('wing starboard', wing, (-7.5, -60, -1.4), (0, 1, 0))]
    for name, obj, origin, d in sites:
        hit = ray(obj, origin, d)
        if not hit:
            continue
        p, nrm = hit
        oriented_box(kit, f'RCS {name} block', p + nrm*.3, nrm, (1.4, 1.1, .7), kit.steel, .06)
        for k, (dx, dz) in enumerate(((-.35, 0), (.35, 0))):
            c = p + nrm*.7 + Vector((dx, 0, dz))
            kit.cylinder(f'RCS {name} nozzle {k+1}', c, .22, .3, kit.dark, 'Y', 10, .02)
            an = bpy.data.objects.new(f'RCS {name} anchor {k+1}', None)
            an.empty_display_type = 'SINGLE_ARROW'
            an.location = c + nrm*.15
            an.rotation_euler = nrm.to_track_quat('Z', 'Y').to_euler()
            bpy.context.collection.objects.link(an)


def _join(objs, name):
    objs = [o for o in objs if o]
    if not objs:
        return None
    for o in objs:
        for m in list(o.modifiers):
            bpy.context.view_layer.objects.active = o
            bpy.ops.object.modifier_apply(modifier=m.name)
    bpy.ops.object.select_all(action='DESELECT')
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    if len(objs) > 1:
        bpy.ops.object.join()
    objs[0].name = name
    return objs[0]


def build(kit):
    cockpit(kit)
    cabin_windows(kit)
    crew_doors(kit)
    belly(kit)
    plating(kit)
    scrap_armour(kit)
    dorsal(kit)
    lamps(kit)
    rcs(kit)
