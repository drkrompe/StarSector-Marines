"""The stern: aft body with its open machinery wells, the tail and the two aft leg blades.

Read off onslaught_hegemony.png at 6-10x (rows 240-372; X = 244 - row, Y = 144 - col):

  leg blades   each aft leg is a long raked blade, about 22 wide, running aft and
               OUTBOARD at ~16 deg from a root on the aft deck at X -46 to a point at
               (-126, +-55); an orange stripe runs its whole inner chine (from
               (-52, 32) to (-121, 54)). Its after end is raked in plan, from the
               outboard corner (-100, 69.5) back to the inboard point.
  wells        outboard of each blade the outer well (X -18..-84, the diagonal pipe
               bundles); between blade and tail the inner well (from X -46 aft, the
               pale fore-and-aft banks). Both are OPEN ASTERN: nothing closes their
               after ends but the machinery itself (os_machinery) and the drives.
  root deck    the solid aft deck X -18..-46 inboard of Y 49.5, from which the
               blade and the tail run aft.
  tail         a raised spine between the inner wells carrying the radiator
               channel (os_machinery), stepping down aft to the mains' face; the
               two main bells (E5/E6) stand proud of it (os_engines).

Silhouette (owner, round 3: "still needs a lot of silhouette work"): the masses
here are CHINED, not rounded: chined_mass gives every side a straight sloped
plate between hard chines instead of a quarter-round, on the same heightfield
construction as os_kit.arched_mass. The stern profile steps down from the core:
root deck 10 -> tail 17 under the spine's end -> the raked after face of the aft
body -> the blades falling to their points. The blade's section is asymmetric:
a steep inner face carrying the stripe, a crest, and a long glacis falling
outboard to the quarter pod.

Joins (owner note 2): the aft body's plan reaches forward inside the core (to X
10) so its deck meets the core's rising aft slope instead of leaving a valley at
the core's aft face; the tail's flat top runs out to Y 18 so the bridge spine's
keel (16) beds in it; each blade's root is buried in the root deck and its
forward top ramps down to the deck; the stripe is a skin lifted off the blade's
own top, so it cannot float.

os_hull keeps its earlier stern constants (importable, unused here); the plans
below are the stern's, and os_machinery and os_engines read them from here.
"""
import math
import bpy
from os_kit import _edge_distance, arched_mass, boolean, ccw, facet_normal, mirror_y, plane_cut, prism
from os_hull import full
from os_armour import shell_strip

# --- plans (port halves) ------------------------------------------------------

# The aft body: os_hull's AFT_OUTER with its front carried forward inside the core.
AFT_OUTER = [(10, 70), (-2, 84), (-4, 124), (-16, 118.5), (-24, 114.5), (-40, 109.5), (-60, 103.5), (-70, 100.5),
             (-72, 84), (-80, 74.5), (-88, 65.5), (-96, 68.5), (-101, 69), (-102, 42)]
TAIL_HALF = [(-10, 22), (-50, 20.5), (-104, 20.5), (-108, 18.5), (-112, 17.5), (-113.5, 14), (-113.5, 0)]
# The blade's inner chine line (the stripe's edge): Y = BLADE_Y0 + BLADE_K * (BLADE_X0 - X).
BLADE_X0, BLADE_Y0, BLADE_K = -50.0, 29.0, 20.0/72.0
ROOT_X = -46.0                    # after face of the root deck
ROOT_Y = 49.5                     # outboard edge of the root deck (the outer well's inner wall)


def blade_inner_y(x):
    return BLADE_Y0+BLADE_K*(BLADE_X0-x)


LEG_PORT = [(-40, 49.5), (-60, 54.5), (-80, 62), (-90, 66.5), (-100, 69.5), (-104, 68.2), (-112, 64),
            (-120, 59.5), (-126, 55), (-121, blade_inner_y(-121)), (-40, blade_inner_y(-40))]
# Everything cut down to the well floor, per side: the outer well, the blade's footprint
# (the blade is its own mass) and the inner well, open astern past the after face.
WELL_CUT = [(-18, ROOT_Y), (-18, 104), (-40, 101), (-64, 94), (-71, 81), (-84, 63.8), (-108, 76), (-116, 76),
            (-116, 20.0), (ROOT_X, 20.0)]
# The outer well alone (machinery reads it): forward wall X -18, outer wall, after
# corner, then the blade's outer flank and the root deck's outboard edge.
WELL_PORT = [(-18, ROOT_Y+.5), (-18, 104), (-40, 101), (-64, 94), (-71, 81), (-84, 63.8), (-80, 62), (-60, 54.5),
             (-42, ROOT_Y+.5)]
# The inner well (machinery reads it): root face, tail flank, open after end, blade's inner flank.
WELL_INNER = [(ROOT_X, 21.0), (-101, 21.0), (-101, blade_inner_y(-101)), (ROOT_X, blade_inner_y(ROOT_X))]
WELL_FLOOR = -10.0
# The raked after-face cut, port: the outer quarter aft of the outer well's forward part.
RAKE_REGION = [(-14, 130), (-14, ROOT_Y+1.5), (-60, 56), (-84, 66.5), (-100, 72.5), (-130, 72.5), (-130, 130)]
RAKE_DEG = 13.0                   # the outer quarters' deck falls aft at this slope
SILL_Z = -6.0                     # where the raked after-face cut crosses the outer well's after wall
MAINS_FACE_X = -105.0             # the tail's after face; the main bells stand proud aft of it
FLARE_Z, FLARE_DEG = -2.0, 30.0   # the outer quarters' lower side leans inboard from this chine

# --- sections: (keel, deck, bilge_in, bilge_h, tumble_in, tumble_h, crown, crown_w), chined.
AFT = (-32.0, 10.0, 12.0, 12.0, 7.0, 13.0, 0.0, 0.0)
TAIL = (-34.0, 17.0, 12.0, 16.0, 4.0, 9.0, 1.0, 6.0)
LEG = (-26.0, 15.5, 5.0, 10.0, 3.0, 4.0, 0.0, 0.0)
# The blade's crest: CREST_Z0 at CREST_X0 (the root deck's height plus the blade's
# step), climbing CREST_DEG aft to the deck height LEG[1] at the spur.
CREST_X0, CREST_Z0, CREST_DEG = -50.0, 13.0, 2.5
KNUCKLE_DEG = 22.0                # the blade's keel rises aft from X -62, meeting the floor at the after face
SPUR_X, SPUR_DEG = -102.0, 47.0   # the second keel knuckle, clear of the aft body
# Keel strake fairings: (x, z, deg) of a plane rising aft that takes each strake into the hull
# at its keel's knuckle.
AFT_STRAKE_FAIR = (-56.0, -32.0-2.4, 40.0)
TAIL_STRAKE_FAIR = (-66.0, -34.0-2.4, 42.0)
AFT_FACE_UNDERCUT = 8.0           # the after faces lean aft this much at the top


def chined_mass(kit, name, plan, spec, mat, step=3.0):
    """os_kit.arched_mass with straight chamfers for its shoulder and bilge: every side is a
    wall, then one flat sloped plate rising tumble_h over tumble_in to the deck (and a
    plate falling bilge_h over bilge_in to the keel), meeting on hard chines; the crown
    rises linearly too. Same heightfield construction, so nothing folds over."""
    keel, deck, bi, bh, ti, th, crown, cw = spec
    ob = arched_mass(kit, name, plan, keel, deck, bi, bh, ti, th, crown, cw, mat=mat, step=step)
    plan = ccw(plan)
    hi, lo = deck-th, keel+bh
    mid = (hi+lo)/2
    for v in ob.data.vertices:
        d = _edge_distance((v.co.x, v.co.y), plan)
        if v.co.z > mid:
            z = hi+(th*min(1.0, d/ti) if ti > 0 else th)
            if crown and cw > 0:
                z += crown*min(1.0, max(0.0, d-ti)/cw)
        else:
            z = lo-(bh*min(1.0, d/bi) if bi > 0 else bh)
        v.co.z = z
    ob.data.update()
    return ob


def sloped_cut(kit, ob, plan, z_at, top=60.0):
    """Remove from `ob` everything above the plane z_at(x, y) (linear) inside `plan`."""
    plan = ccw(plan)
    n = len(plan)
    verts = [(x, y, z_at(x, y)) for x, y in plan]+[(x, y, top) for x, y in plan]
    faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))]+[(k, (k+1) % n, n+(k+1) % n, n+k) for k in range(n)]
    boolean(ob, kit.mesh('Temporary sloped cutter', verts, faces, kit.dark))


def _side(plan, s):
    plan = ccw(plan)
    return plan if s > 0 else mirror_y(plan)


def line_cut(ob, name, a, b, s, keep_left, z=0.0, tilt=0.0):
    """Cut `ob` by the plane through plan line a-b (port coordinates, mirrored by s) at
    height z, removing the side right of a->b (keep_left) or left of it. `tilt` leans the
    plane: positive tips the removed side's normal down, so the cut undercuts."""
    ax, ay = a[0], s*a[1]
    bx, by = b[0], s*b[1]
    dx, dy = bx-ax, by-ay
    length = math.hypot(dx, dy)
    nx, ny = dy/length, -dx/length                 # right of travel
    if not keep_left:
        nx, ny = -nx, -ny
    t = math.radians(tilt)
    plane_cut(ob, name, (ax, ay, z), (nx*math.cos(t), ny*math.cos(t), -math.sin(t)))


def _crest_z(x):
    """Height of the blade's crest at X: it rises aft from the root deck to the spur."""
    return min(LEG[1], CREST_Z0+(CREST_X0-x)*math.tan(math.radians(CREST_DEG)))


def _plane_through(ob, name, a, b, w):
    """Cut `ob` by the plane containing the 3D line a-b and the direction w, removing the side
    whose normal points up (the plane's upper side)."""
    u = [b[i]-a[i] for i in range(3)]
    n = (u[1]*w[2]-u[2]*w[1], u[2]*w[0]-u[0]*w[2], u[0]*w[1]-u[1]*w[0])
    if n[2] < 0:
        n = tuple(-c for c in n)
    plane_cut(ob, name, tuple((a[i]+b[i])/2 for i in range(3)), n)


def _blade(kit, side, s):
    """One raked leg blade. Silhouette (critic, round 4: from astern the arm pods dominated and
    the stern read as one flat band): the crest RISES aft from the root deck to a spur at the
    plan point, the keel sweeps up under it on a second, steeper knuckle once it is clear of the
    aft body, and the after faces are near-vertical, so abeam and on the quarters each blade
    ends in a heavy wedge pointing aft above the drives instead of melting into the deck."""
    leg = chined_mass(kit, f'Hull {side} aft leg', _side(LEG_PORT, s), LEG, kit.plate, step=2.0)
    # Root: the blade's forward top ramps down into the root deck (deck 10) instead of
    # standing on it as a block.
    plane_cut(leg, 'blade root ramp', (-44.0, 0, 9.5), facet_normal((1, 0), 24))
    # The crest rises aft: one plane through the root's crest line, climbing CREST_DEG.
    plane_cut(leg, 'blade crest rise', (CREST_X0, 0, CREST_Z0), facet_normal((1, 0), CREST_DEG))
    # Glacis: from the crest 6 outboard of the inner chine the top falls outboard at 28 deg; the
    # plane holds the rising crest line, so the glacis climbs aft with it.
    a = (-30.0, s*(blade_inner_y(-30.0)+6.0), _crest_z(-30.0))
    b = (-130.0, s*(blade_inner_y(-130.0)+6.0), CREST_Z0+(CREST_X0+130.0)*math.tan(math.radians(CREST_DEG)))
    dx, dy = -100.0, s*(blade_inner_y(-130.0)-blade_inner_y(-30.0))
    length = math.hypot(dx, dy)
    out = (-dy/length, dx/length, 0)                                  # horizontal, outboard
    if out[1]*s < 0:
        out = (dy/length, -dx/length, 0)
    g = math.radians(28)
    _plane_through(leg, 'blade glacis', a, b, (out[0]*math.cos(g), out[1]*math.cos(g), -math.sin(g)))
    # The keel: a straight knuckle rising aft, then, clear of the aft body's after face, a
    # steeper one sweeping up into the spur.
    k = math.radians(KNUCKLE_DEG)
    plane_cut(leg, 'blade keel knuckle', (-62.0, 0, LEG[0]), (-math.sin(k), 0, -math.cos(k)))
    zk = LEG[0]+(-62.0-SPUR_X)*math.tan(k)
    plane_cut(leg, 'blade spur keel', (SPUR_X, 0, zk), (-math.sin(math.radians(SPUR_DEG)), 0, -math.cos(math.radians(SPUR_DEG))))
    # The after faces: the outer one, over the aft body's after corner, is vertical so the
    # blade sits down on the deck there with no slot under it; the one beyond the hull leans a
    # little aft at the top (pivot on the crest), so the spur's top is its aftmost edge. Both
    # keep the plan outline of the point.
    for (p, q), lean in (((LEG_PORT[4], LEG_PORT[6]), 0.0), ((LEG_PORT[6], LEG_PORT[8]), AFT_FACE_UNDERCUT)):
        line_cut(leg, 'blade after rake', p, q, s, keep_left=(s > 0), z=LEG[1], tilt=lean)
    inner = lambda x, d: (x, blade_inner_y(x)+d)
    # The stripe: a skin lifted off the blade's own top, clipped to the inner chamfer and crest.
    st = leg.copy()
    st.data = leg.data.copy()
    st.modifiers.clear()
    bpy.context.collection.objects.link(st)
    st.location.z += .7
    line_cut(st, 'stripe inner', inner(-30, .8), inner(-130, .8), s, keep_left=(s < 0))
    line_cut(st, 'stripe outer', inner(-30, 4.4), inner(-130, 4.4), s, keep_left=(s > 0))
    plane_cut(st, 'stripe fore', (-53.0, 0, 0), (1, 0, 0))
    plane_cut(st, 'stripe low', (0, 0, -1.0), (0, 0, -1))
    st.data.materials.clear()
    kit.finish(st, f'Armour {side} leg stripe', kit.orange)
    return leg


def build(kit):
    aft = chined_mass(kit, 'Hull aft body', full(AFT_OUTER+[(-102, 21)]), AFT, kit.plate)
    # A defined keel line: flat, then a straight knuckle rising aft at 22 deg.
    plane_cut(aft, 'aft keel knuckle', (-58.0, 0, AFT[0]), (-math.sin(math.radians(22)), 0, -math.cos(math.radians(22))))
    for s in (1, -1):
        cutter = prism(kit, 'Temporary machinery well cutter', _side(WELL_CUT, s), [(0.0, WELL_FLOOR), (0.0, 40.0)],
                       kit.dark, bevel=0)
        boolean(aft, cutter)
    # The outer quarters step down aft: their deck falls from the arms' aft ends to a sill
    # across the outer wells' after walls, so the stern profile steps down from the core
    # and the wells read as open recesses from astern and abeam. Bounded in plan to the
    # outer quarters, so the root deck and the inner wells keep their height.
    for s in (1, -1):
        sloped_cut(kit, aft, _side(RAKE_REGION, s), lambda x, y: SILL_Z+(x+70.0)*math.tan(math.radians(RAKE_DEG)))
        # The outer quarter's glacis: from 12 inboard of its edge the top falls outboard
        # at 35 deg to the side wall's chine - a sloped plate, not a box.
        line_cut(aft, 'outer glacis', (-4, 112.0), (-70, 90.0), s, keep_left=(s > 0), z=AFT[1], tilt=-55)
        # Below the glacis' chine the outer quarter's side is not a wall: a plate leaning
        # inboard as it falls to the bilge (a V section, the arms' chined language), so abeam
        # and on the quarters the stern reads as two sloped plates meeting on a hard chine
        # rather than a flat box side.
        line_cut(aft, 'outer flare', (-10.0, 120.5), (-72.0, 99.8), s, keep_left=(s > 0), z=FLARE_Z, tilt=FLARE_DEG)
        # The root deck's outboard wall over the outer well is a sloped plate too, falling
        # from 9 inboard of it to the well, so abeam the stern reads as plates, not a box.
        sloped_cut(kit, aft, _side([(-17, ROOT_Y-9), (-17, ROOT_Y+6), (ROOT_X-6, ROOT_Y+6), (ROOT_X-6, ROOT_Y-9)], s),
                   lambda x, y: AFT[1]-(abs(y)-ROOT_Y+9.0)*math.tan(math.radians(40)))

    tail = chined_mass(kit, 'Hull tail', full(TAIL_HALF), TAIL, kit.plate)
    # The tail steps down aft of the spine's end to the mains' face, and its keel sweeps up.
    plane_cut(tail, 'tail upper', (-92.0, 0, TAIL[1]), facet_normal((-1, 0), 16))
    plane_cut(tail, 'tail keel knuckle', (-68.0, 0, TAIL[0]), (-math.sin(math.radians(26)), 0, -math.cos(math.radians(26))))
    # The mains' face: the tail ends square at MAINS_FACE_X and the two
    # main bells stand proud of it.
    plane_cut(tail, 'mains face', (MAINS_FACE_X, 0, 0), (-1, 0, 0))

    legs = {}
    for side, s in (('port', 1), ('starboard', -1)):
        legs[side] = _blade(kit, side, s)
    kit.hull += [aft, tail, *legs.values()]
    # Keel strakes along the flat keels. Each fairs into the hull where the keel knuckles up
    # (a plane rising aft faster than the knuckle takes its bottom into the plating): run up
    # the counter they read from astern as bars hanging under the stern.
    for name, src, y, (x0, z0, deg) in (('Armour aft keel strake 1', aft, 34, AFT_STRAKE_FAIR),
                                        ('Armour aft keel strake 3', aft, -34, AFT_STRAKE_FAIR),
                                        ('Armour tail keel strake', tail, 0, TAIL_STRAKE_FAIR)):
        st = shell_strip(kit, name, src, 1, y, 2.6, 2.4, kit.grey, xr=(-104, -8 if src is aft else -12))
        a = math.radians(deg)
        plane_cut(st, 'strake fairing', (x0, 0, z0), (-math.sin(a), 0, -math.cos(a)))
    return {'aft': aft, 'tail': tail, 'legs': legs}
