"""Central hull: the fore and aft body, the dorsal and tail spines and the
orange armour belts and bow blades. The command dome is lg_bridge.

Planforms are measured off legion_xiv.png (300x308, .ship centre 150,149):
pixel column c, row r covers X 158-r..159-r, Y 149-c..150-c. Port halves are
written out and mirrored. Heights are proposed art: the sprite gives none.

Read off the sprite:
- the bow is a grey faceted prow between two broad orange armour blades
  (X 80-148), which taper into the outer belt;
- the outer belt sweeps aft and outboard between the forward and aft flight
  decks to the sponson's tail (X -88, Y 138);
- the inner belt is a collar round the dome's head (X 80) that runs aft and
  outboard past the machinery bays to the engine block (X -110, Y 73);
- aft of X -40 the body drops to a lower machinery bed either side of the
  tail spine, which ends in the spike at X -137.
The torso is deep (owner review of the first blockout: "too flat; a thicker
torso with wing-like arms"): a ventral keel hangs under the fore and aft body
with its own chamfered belly, raked up into the bow as a long forefoot and up
into the stern under the engine block, so the sponsons read as arms held out
from a heavy body rather than one slab.

Pass 2 detail. C2 sees the starboard flank in deep shadow, where depth reads
only by silhouette, sunlit upward ledges and planet-shine on downward faces,
so the detail is ledges, strakes, steps and real seam gaps rather than paint:
- the prow ramp carries chevron armour plates draped on it (the sprite's
  chevron facets X 110-155), an armour lip round the bow face, frame ribs on
  the face and chine facets at its lower corners;
- the bow blades are two courses of armour: a base course with a ledge and a
  narrower upper course with a sloped outboard face, both split by seams;
- the belts are runs of armour modules (base course, stepped upper course with
  a sloped outboard face, chamfered end caps) over a dark seam underlay; the
  outer belt steps down from its torso run to its arm run on a stair-like
  transition piece over the shoulder, and the inner belt steps down over the
  torso's aft wall (X -40) to an aft run in the machinery bed;
- the shoulder (torso deck z 10 over arm deck z 4) is an armoured wall with
  buttresses, wall plates, a coping ledge and hatches; the torso flank and the
  ventral keel carry stacked strakes and plate courses; the keel has a keel
  line, bottom plates, frame bands and chine facets on the forefoot and stern
  rake;
- the torso deck is plated in segmented modules over a dark seam underlay,
  clear of the turret discs and the sensor domes, with the round housings
  (not turret slots) and service hatches the sprite shows;
- aft, the machinery bed is a recess with conduits, feed trunks and
  transverse ribs; the tail spine is vertebral: a channel of rungs X -17..-54,
  then collars over the bed and dorsal ribs down to the spike.
Person-scale parts are true metres at lg_kit.METRES_PER_UNIT (lg_kit.PERSON).
"""
import math
import bmesh
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from lg_kit import PERSON, boolean, facet_normal, mirror_y, plane_cut, prism, section, ccw, inset

KEEL, DECK = -20.0, 10.0


def full(port):
    """Port half (bow to stern, Y >= 0) closed by its mirror into one CCW plan."""
    stern_to_bow = [(x, -y) for x, y in reversed(port) if y > 0]
    return ccw(port + stern_to_bow)


# Fore body: bow tip to X -40, out to the sponson roots (the sponsons overlap it).
FORE_PORT = [(157, 0), (156, 3), (152, 10), (148, 18), (148, 34), (141, 41), (132, 44), (120, 51),
             (112, 55), (104, 59), (96, 65), (88, 69), (84, 74), (40, 80), (-40, 80), (-40, 0)]
FORE = section(KEEL, DECK, 7.0, 2.0)
# Aft body: the lower machinery bed from X -40 to the engine block, out to the notch.
AFT_PORT = [(-38, 0), (-38, 80), (-60, 80), (-76, 86), (-96, 84), (-100, 82), (-100, 0)]
AFT = section(KEEL+3, 4.0, 6.0, 1.5)
# Dorsal spine over the prow, ahead of the dome.
SPINE_PORT = [(152, 0), (151, 6), (146, 15), (130, 21), (104, 23), (84, 20), (64, 12), (60, 0)]
SPINE = [(0, DECK-2), (0, 15.0), (2.5, 17.5)]
# Tail spine: from behind the dome to the spike.
TAIL_PORT = [(8, 0), (8, 12), (-40, 14), (-92, 12), (-100, 8), (-120, 8), (-126, 6), (-136, 3), (-138, 0)]
TAIL = section(-12.0, 14.0, 4.0, 2.5)

# Ventral keel: the deep belly under the fore and aft body, inside their keel chamfers.
VENTRAL_PORT = [(118, 0), (110, 28), (96, 48), (70, 60), (-60, 62), (-90, 60), (-108, 48), (-122, 30), (-126, 0)]
VENTRAL = [(12.0, -44.0), (0, -32.0), (0, KEEL+4.0)]   # top sunk into the aft body (keel -17)

# Bow blades: the broad orange armour either side of the prow.
BLADE_PORT = [(148, 35), (141, 41), (132, 44), (120, 51), (112, 55), (104, 59), (96, 65), (88, 69),
              (80, 72), (74, 66), (80, 57), (92, 50), (106, 45), (122, 39), (138, 34), (146, 32)]
# The outer belt crosses from the torso onto the wing arm, whose deck is 6 lower,
# so it is two runs, one on the torso and one stepped down onto the arm, joined
# by a stair-like transition piece over the shoulder.
OUTER_BELT = [(80, 64), (70, 70), (63, 74.5)]
OUTER_STEP = ((63, 74.5), (52.5, 83.5))
ARM_BELT = [(52.5, 83.5), (48, 87), (40, 96), (30, 99), (20, 96), (10, 94),
            (0, 101), (-12, 108), (-26, 114), (-42, 119), (-56, 124), (-72, 131), (-88, 138)]
ARM_STEP = 6.0
# The inner belt runs on the torso deck to X -34, steps down over the torso's aft
# wall (X -40) and runs on in the machinery bed to the engine block face (X -96).
INNER_BELT = [(80, -2), (70, 8), (60, 16), (48, 23), (36, 22), (24, 17), (12, 20), (0, 28), (-12, 34),
              (-30, 43), (-34, 44.8)]
INNER_STEP = ((-34, 44.8), (-46.5, 50.3))
INNER_AFT = [(-46.5, 50.3), (-48, 51), (-66, 57), (-84, 65), (-96.6, 69.9)]
INNER_DROP = 7.0
BELT_BASE, BELT_LEDGE, BELT_TOP = DECK-3, 11.6, 15.0

# Turret discs (slot, ring radius) and other parts the deck stays clear round.
TURRET_DISCS = [((42, 56), 12.5), ((42, -56), 12.5), ((80, 34), 9.5), ((80, -34), 9.5), ((104, 0), 9.5),
                ((76, 60), 5.5), ((76, -60), 5.5)]
KEEP_CLEAR = [((-8, 66), 8.0), ((-8, -66), 8.0)]      # lg_fittings' sensor domes
# Round housings the sprite shows on the deck (not turret slots): (x, |y|, radius).
RING_HOUSINGS = [(95.5, 23.0, 3.6), (-20.5, 21.5, 5.0)]
# Bolted service hatches on the torso deck (x, |y|).
DECK_HATCHES = [(60, 46), (25, 70), (4, 52), (-26, 61), (-30, 26)]
# Shoulder buttresses (x) along the shoulder wall, and hatches between them.
BUTTRESS_X = [79.0, 69.0, 44.0, 33.0, 20.0, 7.0, -6.0, -19.0, -32.0]
SHOULDER_HATCH_X = [13.5, -25.5]
# Machinery bed recess in the aft body (x0, x1, |y0|, |y1|) and its floor.
BED = (-94.0, -46.0, 16.0, 76.0)
BED_FLOOR = .6


# --- small geometry helpers ------------------------------------------------------

# The Legion's armour-run helpers (Acc, sweep, modules, capped, ...) live in the shared kit.
from ship_detail_kit import (Acc, hexa, box, obox, cyl, rod_x, _norm, normals, sweep, arclen, point_at,
                             sub_polyline, modules, capped, dist_seg, dist_poly, inside, flip, Caster)


def bounded_cut(target, name, point, normal, lo, hi):
    """Remove the part of `target` on the normal side of a plane, inside the box lo..hi only."""
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co = Vector(tuple(l+(h-l)*(c+.5) for l, h, c in zip(lo, hi, v.co)))
    geom = bm.verts[:]+bm.edges[:]+bm.faces[:]
    bmesh.ops.bisect_plane(bm, geom=geom, plane_co=Vector(point), plane_no=Vector(normal).normalized(),
                           clear_inner=True)
    bmesh.ops.holes_fill(bm, edges=bm.edges[:], sides=0)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    me = bpy.data.meshes.new('Temporary '+name+' cutter')
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new('Temporary '+name+' cutter', me)
    bpy.context.collection.objects.link(ob)
    boolean(target, ob)


def bow_facets(kit, ob, name):
    # Bounded to the bow by position: forward of X 128 the deck falls away at
    # 20 degrees, and forward of X 118 the keel rises at 26 degrees; neither
    # plane reaches the ship behind those stations.
    plane_cut(ob, name+' bow deck facet', (128, 0, DECK), facet_normal((1, 0), 20), size=400)
    plane_cut(ob, name+' bow keel facet', (118, 0, KEEL), (0.438, 0, -0.899), size=400)


def deck_facet_z(x):
    """Torso deck height at X: flat 10, falling at 20 degrees forward of X 128."""
    return DECK-math.tan(math.radians(20))*max(0.0, x-128)


def keel_facet_z(x):
    return KEEL+(0.438/0.899)*max(0.0, x-118)


def sided(n, sy):
    return (n[0], sy*n[1], n[2])


# --- belts -----------------------------------------------------------------------

def belt_profiles(z0, ledge, top):
    """Base course and stepped upper course; +d is the outboard side (the sloped face)."""
    base = [(-4.0, z0), (4.0, z0), (4.0, ledge-.5), (3.5, ledge), (-3.5, ledge), (-4.0, ledge-.5)]
    upper = [(-2.9, ledge-.4), (2.9, ledge-.4), (2.9, ledge+.5), (1.0, top), (-2.1, top), (-2.9, top-.8)]
    return base, upper


def outboard_sign(pts, sy):
    nrm = normals(pts)
    i = len(pts)//2
    return 1.0 if nrm[i][1]*sy >= 0 else -1.0


def belt_run(acc, under, pts2, sy, dz=0.0, length=15.0, gap=1.0):
    """Armour modules along a plan polyline, with a dark underlay showing in the seams."""
    base, upper = belt_profiles(BELT_BASE+dz, BELT_LEDGE+dz, BELT_TOP+dz)
    pts = [(x, y, 0.0) for x, y in pts2]
    for mod in modules(pts, length, gap):
        sg = outboard_sign(mod, sy)
        for prof in (base, upper):
            st, profs = capped(mod, prof, cap=.45, shrink=.88)
            sweep(acc, st, profs, sg)
    sweep(under, pts, [(-3.2, BELT_BASE+dz), (3.2, BELT_BASE+dz), (3.2, BELT_LEDGE+dz-1.0), (-3.2, BELT_LEDGE+dz-1.0)])


def step_piece(acc, a, b, lo_dz):
    """Stair-like transition from a high run ending at a down to a low run starting at b."""
    u = _norm(b[0]-a[0], b[1]-a[1])
    length = math.dist(a, b)
    hi_top, lo_top = BELT_TOP, BELT_TOP+lo_dz
    zb = BELT_BASE+lo_dz
    tiers = [(1.0, lo_top, 4.3), (2/3, (hi_top+lo_top)/2+.5, 3.8), (1/3, hi_top, 3.3)]
    for frac, top, hw in tiers:
        seg = length*frac
        acc.add(*obox((a[0]+u[0]*seg/2, a[1]+u[1]*seg/2), u, seg/2, hw, zb, top))
    # A sub-step at the foot of each riser.
    for frac, top, hw in tiers[1:]:
        seg = length*frac
        e = (a[0]+u[0]*(seg+.6), a[1]+u[1]*(seg+.6))
        acc.add(*obox(e, u, .6, hw-.4, zb, top-.9))


# --- build ---------------------------------------------------------------------

def build(kit):
    fore = prism(kit, 'Hull fore body', full(FORE_PORT), FORE, kit.plate)
    bow_facets(kit, fore, 'Hull fore body')
    # Chine facets at the bow face's lower corners.
    for sy in (1, -1):
        bounded_cut(fore, f'Hull fore body bow chine {sy}', (152, sy*24, -4.5), sided((.7, .45, -.55), sy),
                    (132, 0 if sy > 0 else -60, -30), (170, 60 if sy > 0 else 0, 6))
    aft = prism(kit, 'Hull aft body', full(AFT_PORT), AFT, kit.plate)
    # The machinery bed: a recess either side of the tail spine.
    for sy in (1, -1):
        x0, x1, y0, y1 = BED
        lo, hi = sorted((sy*y0, sy*y1))
        boolean(aft, kit.mesh('Temporary machinery bed cutter', *box(x0, x1, lo, hi, BED_FLOOR-.4, 12), kit.dark))
    spine = prism(kit, 'Hull dorsal spine', full(SPINE_PORT), SPINE, kit.plate)
    bow_facets(kit, spine, 'Hull dorsal spine')
    # Transverse seams across the dorsal spine (clear of WS 005 at X 104).
    for gx in (76.0, 87.0, 119.0):
        ztop = min(17.5, DECK+math.tan(math.radians(20))*(128-gx))
        boolean(spine, kit.mesh('Temporary spine seam cutter', *box(gx-.45, gx+.45, -30, 30, ztop-.9, 30), kit.dark))
    # Vent slots beside the spine's crown (sprite X 85-100, Y 14-19).
    for sy in (1, -1):
        lo, hi = sorted((sy*14.0, sy*18.6))
        boolean(spine, kit.mesh('Temporary spine vent cutter', *box(85.5, 99.5, lo, hi, 16.2, 30), kit.dark))
    tail = prism(kit, 'Hull tail spine', full(TAIL_PORT), TAIL, kit.bone)
    # The spine falls away aft of the engine block into the spike (X -137).
    plane_cut(tail, 'Hull tail spine spike facet', (-110, 0, 13), facet_normal((-1, 0), 17), size=400)
    # The vertebral channel along its crown (sprite: dark ribs X -1..-54; the dome's neck covers X > -16).
    boolean(tail, kit.mesh('Temporary tail channel cutter', *box(-54.0, -17.0, -5.0, 5.0, 11.6, 30), kit.dark))
    keel = prism(kit, 'Hull ventral keel', full(VENTRAL_PORT), VENTRAL, kit.plate)
    # Raked forefoot and stern: bounded by position, as the bow facets are.
    plane_cut(keel, 'Hull ventral keel forefoot', (60, 0, -44), (0.53, 0, -0.848), size=400)
    plane_cut(keel, 'Hull ventral keel stern rake', (-78, 0, -44), (-0.574, 0, -0.819), size=400)
    # Chine facets: the forefoot and stern rake are faceted wedges, not one flat ramp each.
    for sy in (1, -1):
        bounded_cut(keel, f'Hull ventral keel forefoot chine {sy}', (104, sy*20, -16.5), sided((.67, .37, -.64), sy),
                    (55, 0 if sy > 0 else -80, -60), (130, 80 if sy > 0 else 0, -10))
        bounded_cut(keel, f'Hull ventral keel stern chine {sy}', (-116, sy*20, -17.5), sided((-.745, .456, -.487), sy),
                    (-140, 0 if sy > 0 else -80, -60), (-60, 80 if sy > 0 else 0, -10))
    kit.hull += [fore, aft, spine, tail, keel]

    dark = Acc()
    for side, sy in (('port', 1), ('starboard', -1)):
        build_blade(kit, side, sy)
        f = (lambda p: flip(p)) if sy < 0 else (lambda p: p)
        outer, under = Acc(), Acc()
        belt_run(outer, under, f(OUTER_BELT), sy)
        ob = outer.build(kit, f'Armour {side} outer belt', kit.orange)
        arm, aunder = Acc(), Acc()
        belt_run(arm, aunder, f(ARM_BELT), sy, dz=-ARM_STEP)
        ab = arm.build(kit, f'Armour {side} outer belt arm run', kit.orange)
        stp = Acc()
        a, b = f(list(OUTER_STEP))
        step_piece(stp, a, b, -ARM_STEP)
        sp = stp.build(kit, f'Armour {side} outer belt step piece', kit.orange)
        inner, iunder = Acc(), Acc()
        belt_run(inner, iunder, f(INNER_BELT), sy)
        belt_run(inner, iunder, f(INNER_AFT), sy, dz=-INNER_DROP, length=13.0)
        ib = inner.build(kit, f'Armour {side} inner belt', kit.orange)
        stp = Acc()
        a, b = f(list(INNER_STEP))
        step_piece(stp, a, b, -INNER_DROP)
        isp = stp.build(kit, f'Armour {side} inner belt step piece', kit.orange)
        for acc in (under, aunder, iunder):
            dark.add(acc.verts, acc.faces)
        kit.hull += [ob, ab, sp, ib, isp]
    dark.build(kit, 'Armour belt seam underlay', kit.dark)

    build_shoulders(kit)
    build_flanks(kit)
    build_keel_detail(kit)
    build_prow_detail(kit)
    build_deck(kit)
    build_machinery_bed(kit)
    build_tail_spine(kit)


def build_blade(kit, side, sy):
    plan = ccw(BLADE_PORT) if sy > 0 else mirror_y(ccw(BLADE_PORT))
    # Base course: sunk into the fore body (inset a little so its outboard face never
    # shares the body wall's plane), a ledge at z 12, falling at 22 deg forward of X 128.
    blade = prism(kit, f'Armour {side} bow blade', plan, [(.3, -4.0), (.3, 11.3), (1.0, 12.0)], kit.orange)
    plane_cut(blade, f'Armour {side} bow blade facet', (128, 0, 12.0), facet_normal((1, 0), 22), size=400)
    # Its buried foot stops 1.2 under the deck, above the bow flank's recess.
    ylo, yhi = (0, 90) if sy > 0 else (-90, 0)
    bounded_cut(blade, f'Armour {side} bow blade foot', (128, 0, DECK-1.2), (0, 0, -1), (50, ylo, -30), (128, yhi, 30))
    fn = facet_normal((1, 0), 20)
    bounded_cut(blade, f'Armour {side} bow blade foot fwd', (128, 0, DECK-1.2), (-fn[0], 0, -fn[2]),
                (128, ylo, -30), (170, yhi, 30))
    # Upper course: armour modules along the blade's centre line, each with a ledge above the
    # base course and a sloped outboard face, tapering to the tip and falling at 26 deg
    # forward of X 124; seam gaps between the modules show the dark underlay.
    outer, innr = BLADE_PORT[:9], BLADE_PORT[9:]

    def base_top(x):
        return 12.0-math.tan(math.radians(22))*max(0.0, x-128)

    def prof(p):
        x, y = p[0], abs(p[1])
        hw = max(.9, min(dist_poly((x, y), outer), dist_poly((x, y), innr))-2.0)
        top = min(16.6, 16.6-math.tan(math.radians(26))*max(0.0, x-124))
        zl = base_top(x)+.6
        z0 = min(10.8, base_top(x)-1.0)
        return [(-hw, z0), (hw, z0), (hw, zl), (hw*.2, top), (-hw+.6, top), (-hw, top-.6)]
    centre = [(144.5, 34.5), (136, 38.5), (126, 43), (115, 48.5), (104, 53.5), (94, 58), (86, 61.5), (80.5, 64.5)]
    centre = centre if sy > 0 else flip(centre)
    up, under = Acc(), Acc()
    pts = [(x, y, 0.0) for x, y in centre]
    for mod in modules(pts, 12.5, 1.1):
        if all(math.dist(p[:2], (76, sy*60)) < 7.0 for p in (mod[0], mod[-1])):
            continue
        st, profs = capped(mod, prof)
        sweep(up, st, profs, outboard_sign(mod, sy))
    uw = [min(1.8, prof(p)[1][0]-.4) for p in pts]
    sweep(under, pts, [[(-w, min(10.8, base_top(p[0])-1.0)), (w, min(10.8, base_top(p[0])-1.0)),
                        (w, base_top(p[0])+.3), (-w, base_top(p[0])+.3)] for p, w in zip(pts, uw)])
    upper = up.build(kit, f'Armour {side} bow blade upper course', kit.orange)
    under.build(kit, f'Armour {side} bow blade seam underlay', kit.dark)
    kit.hull += [blade, upper]


def build_shoulders(kit):
    """The shoulder: torso deck (z 10) standing 6 over the arm deck (z 4) as an armoured wall."""
    plates, cope, butt, hatch, door = Acc(), Acc(), Acc(), Acc(), Acc()
    for sy in (1, -1):
        line = [(84, 74), (40, 80), (-40, 80)]
        line = line if sy > 0 else flip(line)
        sg = outboard_sign(line, sy)
        pts = [(x, y, 0.0) for x, y in line]
        # Coping ledge under the deck chamfer: sunlit top, planet-lit underside.
        sweep(cope, pts, [(-.6, 7.9), (1.4, 7.9), (1.4, 8.6), (.8, 9.0), (-.6, 9.0)], sg)

        def at(x):
            for (ax, ay), (bx, by) in zip(line, line[1:]):
                if min(ax, bx) <= x <= max(ax, bx):
                    d = _norm(bx-ax, by-ay)
                    return ay+(by-ay)*(x-ax)/(bx-ax), d, (-d[1]*sg, d[0]*sg)
        # Buttresses: sloped wedges from the arm deck to the coping, on foot plates.
        for bx in BUTTRESS_X:
            py, (ux, uy), n = at(bx)
            q = lambda s, o, z: (bx+ux*s+n[0]*o, py+uy*s+n[1]*o, z)
            hw = 1.3
            butt.add(*hexa([q(-hw, -.4, 3.0), q(hw, -.4, 3.0), q(hw, 3.4, 3.0), q(-hw, 3.4, 3.0),
                            q(-hw, -.4, 7.9), q(hw, -.4, 7.9), q(hw, 1.1, 7.9), q(-hw, 1.1, 7.9)]))
            butt.add(*hexa([q(-hw-.5, -.2, 3.0), q(hw+.5, -.2, 3.0), q(hw+.5, 4.0, 3.0), q(-hw-.5, 4.0, 3.0),
                            q(-hw-.5, -.2, 4.6), q(hw+.5, -.2, 4.6), q(hw+.5, 4.0, 4.6), q(-hw-.5, 4.0, 4.6)]))
        # Wall plates between the buttresses, skipping the belt crossing (X 50-64) and the hatches.
        xs = sorted(BUTTRESS_X)
        for xa, xb in zip(xs, xs[1:]):
            if xa < 64 and xb > 50:
                continue
            for x0, x1 in ((xa+1.8, (xa+xb)/2-.45), ((xa+xb)/2+.45, xb-1.8)):
                if any(x0-1.8 < hx < x1+1.8 for hx in SHOULDER_HATCH_X):
                    continue
                seg = [(x, at(x)[0], 0.0) for x in (x1, x0)]
                sweep(plates, seg, [(-.3, 4.4), (.55, 4.4), (.55, 7.2), (.25, 7.6), (-.3, 7.6)], sg)
        # Hatches: 1.3 x 2.1 m doors in a frame, true metres at any scale.
        P = PERSON
        for hx in SHOULDER_HATCH_X:
            hy = at(hx)[0]
            hatch.add(*box(hx-1.7*P, hx+1.7*P, *sorted((hy-sy*.2, hy+sy*.45)), 4.1, 4.1+3.75*P))
            door.add(*box(hx-1.05*P, hx+1.05*P, *sorted((hy+sy*.3, hy+sy*.6)), 4.3, 4.3+3.4*P))
    cope.build(kit, 'Hull torso shoulder coping', kit.plate)
    butt.build(kit, 'Hull torso shoulder buttresses', kit.plate)
    plates.build(kit, 'Hull torso shoulder wall plates', kit.plate)
    hatch.build(kit, 'Hull torso shoulder hatch frames', kit.steel)
    door.build(kit, 'Hull torso shoulder hatch doors', kit.dark)


FLANK_PORT = [(148, 34), (141, 41), (132, 44), (120, 51), (112, 55), (104, 59), (96, 65), (88, 69),
              (84, 74), (40, 80), (-40, 80)]


def recess(kit, target, name, pts2, zlo, zhi, depth, sg, ribs=None, rib_step=None):
    """Cut a recessed band into a wall along a plan polyline (from `depth` inside the wall
    outward), so the wall above and below it stand as flush strakes with an overhang
    (downward face, for planet-shine) and a sill (upward face, for the sun). Nothing
    protrudes past the planform, so the top silhouette is untouched. Optional frame
    ribs stand in the recess, flush with the wall."""
    pts = [(x, y, 0.0) for x, y in pts2]
    profs = []
    for x, y, _ in pts:
        lo, hi = zlo(x), zhi(x)
        hi = max(hi, lo+.3)
        profs.append([(-depth, lo), (3.0, lo), (3.0, hi), (-depth, hi)])
    cut = Acc()
    sweep(cut, pts, profs, sg)
    boolean(target, cut.build(kit, 'Temporary '+name+' cutter', kit.steel))
    if ribs is None:
        return
    s = arclen(pts)
    k = rib_step
    while k < s[-1]-rib_step*.5:
        x, y, _ = point_at(pts, s, k)
        i = max(j for j in range(len(s)-1) if s[j] <= k)
        d = _norm(pts[i+1][0]-pts[i][0], pts[i+1][1]-pts[i][1])
        n = (-d[1]*sg, d[0]*sg)
        lo, hi = zlo(x), zhi(x)
        if hi-lo > 1.2:
            q = lambda t, o, z: (x+d[0]*t+n[0]*o, y+d[1]*t+n[1]*o, z)
            ribs.add(*hexa([q(-.6, -depth-.2, lo-.1), q(.6, -depth-.2, lo-.1), q(.6, -.15, lo-.1), q(-.6, -.15, lo-.1),
                            q(-.6, -depth-.2, hi+.1), q(.6, -depth-.2, hi+.1), q(.6, -.15, hi+.1), q(-.6, -.15, hi+.1)]))
        k += rib_step


def build_flanks(kit):
    """Stacked strakes on the torso flank: a ledge under the arms, recessed bands on the
    bow flanks and the aft body's flank in the notch, and the bow face's recess."""
    acc, ribs = Acc(), Acc()
    fore, aft = bpy.data.objects['Hull fore body'], bpy.data.objects['Hull aft body']
    for sy in (1, -1):
        # Under-arm ledge (hidden from above by the arm, so it may stand proud).
        line = [(84, 74), (40, 80), (-40, 80)]
        line = line if sy > 0 else flip(line)
        sg = outboard_sign(line, sy)
        sweep(acc, [(x, y, -9.8) for x, y in line], [(-.5, -1.4), (1.5, -1.4), (1.5, -.3), (.9, .3), (-.5, .3)], sg)
        # Bow flank forward of the arm: a deep recessed band under the deck edge,
        # riding up with the keel facet, with frame ribs standing in it.
        bow = [(146.5, 35.5), (141, 41), (132, 44), (120, 51), (112, 55), (104, 59), (96, 65), (88, 69)]
        bow = bow if sy > 0 else flip(bow)
        recess(kit, fore, 'bow flank recess', bow, lambda x: max(-8.5, keel_facet_z(x)+4.0),
               lambda x: min(3.0, deck_facet_z(x)-1.6), 1.4, outboard_sign(bow, sy), ribs, 5.5)
        # A lower band on the keel chamfer line, under the sill.
        recess(kit, fore, 'bow flank lower recess', bow, lambda x: max(-12.4, keel_facet_z(x)+.9),
               lambda x: max(-11.0, keel_facet_z(x)+2.6), .9, outboard_sign(bow, sy))
        # Aft body flank in the notch behind the arm.
        notch = [(-62, 80.8), (-76, 86), (-96, 84), (-99, 82.5)]
        notch = notch if sy > 0 else flip(notch)
        recess(kit, aft, 'aft flank recess', notch, lambda x: -6.4, lambda x: -1.2, 1.2,
               outboard_sign(notch, sy), ribs, 5.0)
    # Bow face: a recess under the deck edge between the chines.
    prow = [(148, -33), (148, -18), (152, -10), (156, -3), (157, 0), (156, 3), (152, 10), (148, 18), (148, 33)]
    recess(kit, fore, 'bow face recess', prow, lambda x: keel_facet_z(x)+1.2, lambda x: deck_facet_z(x)-1.5,
           1.3, -1.0, ribs, 6.0)
    acc.build(kit, 'Hull torso flank strakes', kit.plate)
    ribs.build(kit, 'Hull torso flank frame ribs', kit.plate)


def build_keel_detail(kit):
    """Plate courses on the ventral keel's walls, the keel line, bottom plates and frame bands."""
    upper, lower, bottom = Acc(), Acc(), Acc()
    wall = [(70, 60), (-60, 62), (-90, 60)]
    for sy in (1, -1):
        line = wall if sy > 0 else flip(wall)
        sg = outboard_sign(line, sy)
        pts = [(x, y, 0.0) for x, y in line]
        for acc, z0, z1, proud in ((upper, -25.6, -20.6, 1.1), (lower, -31.4, -26.2, .6)):
            for mod in modules(pts, 13.0, .9):
                st, profs = capped(mod, [(-.3, z0), (proud, z0), (proud, z1-.4), (proud-.4, z1), (-.3, z1)], cap=.5)
                sweep(acc, st, profs, sg)
    upper.build(kit, 'Hull ventral keel upper plate course', kit.plate)
    lower.build(kit, 'Hull ventral keel lower plate course', kit.plate)

    cast = Caster([bpy.data.objects['Hull ventral keel']])
    # Keel line: a rib along the centreline, up the forefoot and the stern rake.
    pts = []
    for k in range(75):
        x = 104-k*3.0
        z = cast.up(x, 0.0)
        if z is not None and z < -18.0:
            pts.append((x, 0.0, z))
    line = Acc()
    if len(pts) > 1:
        sweep(line, pts, [(-1.7, .5), (1.7, .5), (1.2, -2.3), (-1.2, -2.3)])
    # Frame bands across the forefoot and the stern rake.
    for x in (68.0, 80.0, 92.0, -86.0, -98.0, -110.0):
        band = []
        for k in range(41):
            y = -60+k*3.0
            z = cast.up(x, y)
            if z is not None and z < -18.5:
                band.append((x, y, z))
        if len(band) > 2:
            sweep(line, band, [(-1.1, .4), (1.1, .4), (.8, -.9), (-.8, -.9)])
    line.build(kit, 'Hull ventral keel line', kit.plate)
    # Bottom plates on the flat belly, clear of the keel line.
    for i in range(10):
        x0 = -74+i*13.4
        for j in range(8):
            y0 = -48+j*12.0
            x1, y1 = x0+12.5, y0+11.1
            if y0 < 2.5 and y1 > -2.5:
                y0, y1 = (2.5, y1) if y1 > 6 else (y0, -2.5)
                if y1-y0 < 4:
                    continue
            zs = [cast.up(x, y) for x in (x0, x1) for y in (y0, y1)]
            if any(z is None or z > -43.5 for z in zs):
                continue
            bottom.add(*box(x0, x1, y0, y1, -44.6, -43.8))
    bottom.build(kit, 'Hull ventral keel bottom plates', kit.plate, bevel=.1)


def build_prow_detail(kit):
    """Chevron armour plates on the prow ramp, the bow armour lip and the bow-face frames."""
    cast = Caster([bpy.data.objects['Hull fore body'], bpy.data.objects['Hull dorsal spine']])
    chev = Acc()
    # Stacked V plates, apex aft on the centreline, arms forward and outboard to the prow's
    # edge or the blade (sprite X 110-155); the seams between them are the chevron lines.
    prow_plan = inset(full(FORE_PORT), 2.2)
    blades = [ccw(BLADE_PORT), mirror_y(ccw(BLADE_PORT))]
    ax_, ay_ = _norm(.62, 1.0)

    def free(x, y):
        return inside((x, y), prow_plan) and not any(inside((x, y), b) or dist_poly((x, y), b+b[:1]) < 1.6
                                                     for b in blades)
    for apex in (126.0, 133.6, 141.2, 148.8):
        arm = 0.0
        while arm < 40 and free(apex+ax_*(arm+1), ay_*(arm+1)):
            arm += 1.0
        if arm < 3:
            continue
        n = max(3, int(arm/2.5))
        plan = [(apex+ax_*arm*(1-k/n), -ay_*arm*(1-k/n)) for k in range(n)]
        plan += [(apex, 0.0)] + [(apex+ax_*arm*k/n, ay_*arm*k/n) for k in range(1, n+1)]
        pts = []
        for x, y in plan:
            z = cast.down(x, y)
            pts.append((x, y, z if z is not None else deck_facet_z(x)))
        st, profs = capped(pts, [(-3.0, -.9), (3.0, -.9), (3.0, .55), (2.5, .85), (-2.5, .85), (-3.0, .55)], cap=.6)
        sweep(chev, st, profs)
    chev.build(kit, 'Hull bow chevron plates', kit.plate)
    # The same stacked V plates under the bow, on the rising keel facet (the bow face's
    # downward facets, lit only by planet-shine), with the keel line run up to the stem.
    under = Acc()
    below = Caster([bpy.data.objects['Hull fore body']])
    for apex in (121.0, 128.6, 136.2, 143.8, 151.4):
        arm = 0.0
        while arm < 50 and inside((apex+ax_*(arm+1), ay_*(arm+1)), prow_plan):
            arm += 1.0
        if arm < 3:
            continue
        n = max(3, int(arm/2.5))
        plan = [(apex+ax_*arm*(1-k/n), -ay_*arm*(1-k/n)) for k in range(n)]
        plan += [(apex, 0.0)] + [(apex+ax_*arm*k/n, ay_*arm*k/n) for k in range(1, n+1)]
        pts = []
        for x, y in plan:
            z = below.up(x, y)
            pts.append((x, y, z if z is not None else keel_facet_z(x)))
        st, profs = capped(pts, [(-3.0, .9), (3.0, .9), (3.0, -.55), (2.5, -.85), (-2.5, -.85), (-3.0, -.55)], cap=.6)
        sweep(under, st, profs)
    stem = []
    for k in range(14):
        x = 118+k*3.0
        z = below.up(x, 0.0)
        if z is not None:
            stem.append((x, 0.0, z))
    if len(stem) > 1:
        sweep(under, stem, [(-1.5, .5), (1.5, .5), (1.1, -1.9), (-1.1, -1.9)])
    under.build(kit, 'Hull bow underside plates', kit.plate)
    # Armour lip round the bow face: a raised rim on the deck edge over the face's
    # recess (+d is into the hull; it stands inside the planform).
    lip = Acc()
    prow = [(148, -34), (148, -18), (152, -10), (156, -3), (157, 0), (156, 3), (152, 10), (148, 18), (148, 34)]
    pts = [(x, y, deck_facet_z(x)) for x, y in prow]
    sweep(lip, pts, [(.08, -.6), (2.0, -.6), (2.0, .55), (1.2, 1.0), (.08, 1.0)])
    lip.build(kit, 'Hull bow armour lip', kit.plate)


def build_deck(kit):
    """Segmented deck plating over a dark seam underlay on the torso deck."""
    region = inset(full(FORE_PORT), 3.2)
    discs = [(c, 1.1*r+.8) for c, r in TURRET_DISCS] + [(c, r+.8) for c, r in KEEP_CLEAR]
    discs += [((x, sy*y), r+1.6) for x, y, r in RING_HOUSINGS for sy in (1, -1)]
    discs += [((x, sy*y), 3.4) for x, y in DECK_HATCHES for sy in (1, -1)]
    belts = []
    for sy in (1, -1):
        belts.append((OUTER_BELT if sy > 0 else flip(OUTER_BELT), 5.4))
        belts.append((INNER_BELT if sy > 0 else flip(INNER_BELT), 5.4))
    blades = [ccw(BLADE_PORT), mirror_y(ccw(BLADE_PORT))]
    spine = full(SPINE_PORT)

    def blocked(p):
        x, y = p
        if x > 126 or x < -37 or not inside(p, region):
            return True
        if -17 < x < 69 and abs(y) < 24.5:          # the command dome and its neck
            return True
        if x < 10 and abs(y) < 16.5:                 # tail spine
            return True
        if inside(p, spine) or dist_poly(p, spine+spine[:1]) < 1.2:
            return True
        for c, r in discs:
            if math.dist(p, c) < r:
                return True
        for pts, w in belts:
            if dist_poly(p, pts) < w:
                return True
        for b in blades:
            if inside(p, b) or dist_poly(p, b+b[:1]) < 1.4:
                return True
        return False

    plates, under = Acc(), Acc()
    PX, PY, GAP = 7.6, 6.2, .8
    for j in range(-14, 15):
        y0 = j*PY-PY/2
        off = PX/2 if j % 2 else 0.0
        for i in range(-6, 24):
            x0 = -38+i*PX+off
            x1, y1 = x0+PX-GAP, y0+PY-GAP
            pts = [(x0, y0), (x1, y0), (x1, y1), (x0, y1), ((x0+x1)/2, (y0+y1)/2)]
            if any(blocked(p) for p in pts):
                continue
            plates.add(*box(x0, x1, y0, y1, DECK+.22, DECK+.8))
            under.add(*box(x0-GAP/2-.05, x1+GAP/2+.05, y0-GAP/2-.05, y1+GAP/2+.05, DECK-.1, DECK+.25))
    pl = plates.build(kit, 'Hull deck plating', kit.plate, bevel=.1)
    under.build(kit, 'Hull deck seam underlay', kit.dark)
    if pl:
        kit.hull.append(pl)

    # Round housings (sprite: light rings, not turret slots) and bolted service hatches.
    cast = Caster(kit.hull)
    hous, core, hat, lid = Acc(), Acc(), Acc(), Acc()
    for x, y, r in RING_HOUSINGS:
        for sy in (1, -1):
            top = DECK+1.6
            hous.add(*cyl(x, sy*y, r, DECK-.5, top, 18))
            core.add(*cyl(x, sy*y, r*.62, top-.6, top+.15, 14))
            hous.add(*cyl(x, sy*y, r*.35, top-.6, top+.5, 10))
    for x, y in DECK_HATCHES:
        for sy in (1, -1):
            z = cast.down(x, sy*y) or DECK
            hat.add(*box(x-1.9, x+1.9, sy*y-1.9, sy*y+1.9, z-.3, z+.55))
            lid.add(*box(x-1.4, x+1.4, sy*y-1.4, sy*y+1.4, z+.4, z+.62))
            for dx in (-1.6, 1.6):           # hinge blocks
                hat.add(*box(x+dx-.3, x+dx+.3, sy*y-1.2, sy*y+1.2, z+.5, z+.95))
    hous.build(kit, 'Hull deck ring housings', kit.steel, bevel=.08)
    core.build(kit, 'Hull deck ring housing cores', kit.dark)
    hat.build(kit, 'Hull deck hatch frames', kit.steel)
    lid.build(kit, 'Hull deck hatch lids', kit.plate)

    # Vent rungs in the spine's crown slots, and frame ribs on the torso's aft wall (X -40).
    ribs = Acc()
    for sy in (1, -1):
        lo, hi = sorted((sy*14.0, sy*18.6))
        x = 87.0
        while x < 99:
            ribs.add(*box(x-.3, x+.3, lo, hi, 16.2, 17.2))
            x += 1.6
        for y in (19.0, 30.0, 41.0, 52.0, 63.0, 73.0):
            ribs.add(*box(-41.4, -39.6, sy*y-.6, sy*y+.6, BED_FLOOR, 8.6))
    ribs.build(kit, 'Hull torso aft wall ribs', kit.plate)


def build_machinery_bed(kit):
    """The dark exposed machinery bays aft (sprite X -40..-100, Y 20-80)."""
    x0, x1, y0, y1 = BED
    floor, cond, ribs, trunk = Acc(), Acc(), Acc(), Acc()
    for sy in (1, -1):
        lo, hi = sorted((sy*y0, sy*y1))
        floor.add(*box(x0, x1, lo, hi, BED_FLOOR-.3, BED_FLOOR+.05))
        # Fore-and-aft conduits (the sprite's long dark tubes).
        for y, r in ((30.0, 1.1), (33.0, .8), (38.5, 1.4), (44.0, .9), (47.0, 1.2), (53.0, 1.0),
                     (57.5, 1.5), (63.0, .9), (67.0, 1.2), (72.0, 1.0)):
            cond.add(*rod_x(x0-1.5, x1+.5, sy*y, BED_FLOOR+r, r))
        # Main feed trunks into the engine block (the engine gut), with flange collars.
        for y, r in ((20.0, 2.6), (25.6, 2.1)):
            trunk.add(*rod_x(x0-2.5, x1+.5, sy*y, BED_FLOOR+r, r, n=10))
            for xx in (x1-4, (x0+x1)/2, x0+5):
                trunk.add(*rod_x(xx-.6, xx+.6, sy*y, BED_FLOOR+r, r+.45, n=10))
        # Transverse ribs (frames) saddling the conduits, below the deck line.
        for k in range(8):
            rx = x1-3-k*6.4
            ribs.add(*box(rx-.45, rx+.45, lo, hi, BED_FLOOR, 3.5))
            ribs.add(*box(rx-.9, rx+.9, lo, hi, 3.0, 3.6))
    floor.build(kit, 'Hull machinery bed floor', kit.dark)
    cond.build(kit, 'Hull machinery bed conduits', kit.steel)
    trunk.build(kit, 'Hull machinery bed feed trunks', kit.steel)
    ribs.build(kit, 'Hull machinery bed ribs', kit.steel)


def tail_half_width(x):
    for (ax, ay), (bx, by) in zip(TAIL_PORT, TAIL_PORT[1:]):
        if bx <= x <= ax:
            return ay+(by-ay)*(ax-x)/(ax-bx)
    return 0.0


def tail_top(x):
    return min(14.0, 13.0+math.tan(math.radians(17))*(x+110))


def build_tail_spine(kit):
    """Ribbed vertebral spine: a channel of rungs, collars over the bed, dorsal ribs to the spike."""
    floor, rungs, coll = Acc(), Acc(), Acc()
    floor.add(*box(-54.0, -17.0, -5.0, 5.0, 11.5, 11.8))
    x = -19.0
    while x > -53:
        rungs.add(*box(x-.5, x+.5, -5.0, 5.0, 11.6, 13.3))
        x -= 3.2
    rungs.add(*box(-53.5, -17.5, -1.0, 1.0, 11.6, 12.6))   # the sprite's light centre line
    # Collars over the machinery bed, where the spine stands 10 over the aft deck.
    x = -58.0
    while x > -95:
        hw, top = tail_half_width(x), tail_top(x)
        sweep(coll, [(x+.9, 0.0, 0.0), (x-.9, 0.0, 0.0)],
              [(-hw-.8, 3.4), (hw+.8, 3.4), (hw+.8, top-1.6), (hw-.9, top+.8), (-hw+.9, top+.8), (-hw-.8, top-1.6)])
        x -= 5.0
    # Dorsal ribs over the block and down the spike, inside the spine's width (clear of engines 9/10).
    x = -100.0
    while x > -129:
        hw, top = tail_half_width(x), tail_top(x)
        sweep(coll, [(x+.7, 0.0, 0.0), (x-.7, 0.0, 0.0)],
              [(-hw+.6, top-2.2), (hw-.6, top-2.2), (hw-1.6, top+.9), (-hw+1.6, top+.9)])
        x -= 4.0
    floor.build(kit, 'Hull tail spine channel floor', kit.dark)
    rungs.build(kit, 'Hull tail spine channel rungs', kit.steel)
    coll.build(kit, 'Hull tail spine vertebrae', kit.bone)
