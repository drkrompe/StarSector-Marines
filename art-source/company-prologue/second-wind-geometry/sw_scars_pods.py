"""Scars: the two drive pods and their housing decks (x < ~-5, |y| > ~15).

The port pod hung windward for the years in the belt: its aft shoulder took
the heaviest strike on the ship (continuity scar 2), its outer can was
crushed against the anchor rock as the hulk swung on its tether, and the top
outboard fins of its heat-sink bank were bent. The lee (starboard) pod only
took slow dents. Salvagers lifted both drive coils, the turbopumps off both
housing decks, the port can feed pipes and both pods' access panels.

The escape ran on the starboard pod alone, on a salvaged drive coil tied
down where its turbopumps had stood and a jury propellant feed through a
salvaged pump; the port pod was left dead with its feed lines blind-flanged
(nozzles open: never capped). It stays cold through the refit. Restoration
reinstalls the machinery as built and lights both pods.
"""
import math
import bpy
import random
from mathutils import Vector
from sw_damage import Scar, ENGINE_STATUSES, Geo, basis, TAU
from sw_engines import (ENGINES, BELL_R, FIN_X, BELL_L, BANDS_N, STEP, COLLAR_AFT,
                        FACE_GAP, CAN, CAN_FACE_R, bell_curve)

V = Vector
X, Y, Z = V((1,0,0)), V((0,1,0)), V((0,0,1))

# Coil cradle on each housing deck, where the turbopump housing stood.
CRADLE_X, CRADLE_Y = -30.0, 31.0
# Feed-pipe stubs at each can: (|y|, z at the cut end's root, z at the end).
STUBS = ((32, 5.8, 6.9), (23.6, 4.4, 5.5), (32, 8.6, 7.7), (23.6, 8.6, 7.7))
# Jury propellant feed (starboard frame): pylon fuel trunks -> salvaged pump
# on the housing deck -> the inner can's feed pipe.
FEED_HOSE = [(-21.5,-13.2,8.1), (-24.5,-15.2,8.6), (-27.4,-18.6,10.2), (-29.0,-20.6,11.0)]
FEED_PUMP = (-30.4, -21.0, 11.0)
FEED_TO_CAN = [(-31.8,-21.0,11.0), (-32.8,-22.3,10.0), (-33.2,-23.6,8.8)]


def deck(d, x, y, s):
    """Top of the thrust housing deck at (x, y), ignoring what stood on it."""
    names = {o.name for o in d.named('Thrust housing', s)}
    hit = d.cast((x, y, 30), (x, y, 0), names)
    return hit[0] if hit else V((x, y, 9.8))


# ---- drive pods ---------------------------------------------------------------
def cradle(d, scar, s, stubs):
    """The coil lifted out: empty saddles on the housing deck. stubs: 'cut'
    (feed lines torch-cut short), 'flanged' (cut and blind-flanged by the
    escape crew) or None (feed lines left intact)."""
    x, y = CRADLE_X, s*CRADLE_Y
    top = deck(d, x, y, s)
    zc = top.z+2.35
    saddle = d.part(scar, 'empty coil saddle', d.salvage[1], .04)
    pads = d.part(scar, 'saddle bearing pad', d.dark, .02)
    bolts = d.part(scar, 'saddle bolt heads', d.steel, .005)
    for dy in (-3.15, 3.15):
        saddle.obox(V((x, y+dy, (top.z+zc)/2-.4)), X, Y, Z, 1.5, .22, (zc-top.z)/2-.4)
        saddle.obox(V((x, y+dy, top.z+.07)), X, Y, Z, 1.8, .5, .07)
        for sx in (-1, 1):
            a = math.radians(38)*sx
            ax, az = V((math.cos(a), 0, -math.sin(a))), V((math.sin(a), 0, math.cos(a)))
            c = V((x+sx*1.05, y+dy, zc-1.55))
            saddle.obox(c, ax, Y, az, .75, .22, .2)
            pads.obox(c+az*.22, ax, Y, az, .6, .18, .03)
        for dx in (-1.45, 1.45):
            bolts.disc((x+dx, y+dy, top.z+.17), Z, .1, .08, 6)
    if not stubs:
        return
    stub = d.part(scar, 'cut feed line stub', d.steel, .02)
    if stubs == 'flanged':
        ends = d.part(scar, 'blind flange', d.salvage[2], .02)
    else:
        ends = d.part(scar, 'cut pipe end', d.dark, 0)
    for py, z0, z1 in STUBS:
        stub.disc((-33.2, s*py, (z0+z1)/2), Z, .45, abs(z1-z0), 10)
        if stubs == 'flanged':
            ends.disc((-33.2, s*py, z1), Z, .72, .16, 10)
            up = 1 if z1 > z0 else -1
            for k in range(4):
                a = TAU*(k+.5)/4
                bolts.disc((-33.2+.55*math.cos(a), s*py+.55*math.sin(a), z1+.1*up), Z, .07, .06, 6)
        else:
            ends.disc((-33.2, s*py, z1), Z, .38, .04, 10)


def coil(d, scar, s):
    """A cased salvaged drive coil tied down on saddles where the pumps were;
    leads to a junction box, a coolant pair up to the fin bank."""
    x, y = CRADLE_X, s*CRADLE_Y
    o = -s  # y - o*k is k outboard of the coil centre
    top = deck(d, x, y, s)
    zc = top.z+2.35
    core = d.part(scar, 'salvaged coil casing', d.salvage[0], .04)
    wind = d.part(scar, 'salvaged coil winding', d.copper, .02)
    caps = d.part(scar, 'coil end cap', d.steel, .03)
    saddle = d.part(scar, 'coil saddle', d.salvage[1], .04)
    bolts = d.part(scar, 'saddle bolt heads', d.steel, .005)
    beads = d.part(scar, 'weld bead', d.weld, .01)
    # Copper only in the cooling windows between casing sections.
    wind.disc((x, y, zc), Y, 1.72, 8.0, 16)
    for y0, y1 in ((-4,-2.3), (-1.5,-.4), (.4,1.5), (2.3,4)):
        core.disc((x, y+(y0+y1)/2, zc), Y, 1.9, y1-y0, 16)
    for dy in (-4.15, 4.15):
        caps.disc((x, y+dy, zc), Y, 2.0, .3, 16)
    for dy in (-3.15, 3.15):
        caps.disc((x, y+dy, zc), Y, 1.97, .26, 16)  # hold-down band
        saddle.obox(V((x, y+dy, (top.z+zc)/2-.4)), X, Y, Z, 1.5, .22, (zc-top.z)/2-.4)
        saddle.obox(V((x, y+dy, top.z+.07)), X, Y, Z, 1.8, .5, .07)
        for sx in (-1, 1):  # tie rods from the band down to the saddle foot
            caps.disc((x+sx*2.02, y+dy, (top.z+zc)/2+.1), Z, .09, zc-top.z+.3, 6)
        for dx in (-1.45, 1.45):
            bolts.disc((x+dx, y+dy, top.z+.17), Z, .1, .08, 6)
        beads.bead(V((x-1.8, y+dy-.52, top.z)), V((x+1.8, y+dy-.52, top.z)), Z, .08)
    # Terminal box on the outboard end, leads clipped down to a junction box
    # which takes them into the housing through one conduit.
    caps.obox(V((x, y-o*4.75, zc)), X, Y, Z, .7, .32, .7)
    jb = V((-31.7, y-o*5.4, top.z+.45))
    caps.obox(jb, X, Y, Z, .45, .7, .42)
    leads = d.part(scar, 'coil lead', d.cable, .01)
    clips = d.part(scar, 'cable clip', d.steel, .01)
    for dy in (-.25, .25):
        leads.tube([V((x-.3, y-o*4.95, zc-.5+dy)), V((x-.6, y-o*(5.2+dy), top.z+.35)),
                    V((jb.x+.5, jb.y+dy, top.z+.35))], .13, 8)
    clips.obox(V((x-1.2, y-o*5.2, top.z+.35)), X, Y, Z, .15, .55, .2)
    leads.tube([jb+V((-.45,0,0)), jb+V((-1.1,0,-.6)), jb+V((-1.6,0,-1.6))], .22, 8)
    hose = d.part(scar, 'coolant hose', d.cable, .01)
    for dx in (-.45, .45):
        hose.tube([V((x+dx, y+o*4.3, zc+.4)), V((x+dx+1.2, y+o*5.3, zc+1.0)),
                   V((-26.4+dx, s*24.8, 15.15)), V((-24.2+dx, s*24.8, 14.75))], .16, 8)


def jury_feed(d, scar, s):
    """Propellant to a pod whose turbopumps were stripped: a hose off the
    pylon fuel trunks to a salvaged pump, then down to the can feed."""
    m = lambda p: V((p[0], -s*p[1], p[2]))  # tables are in the starboard frame
    hose = d.part(scar, 'jury feed hose', d.cable, .01)
    clips = d.part(scar, 'feed clip', d.steel, .01)
    pump = d.part(scar, 'salvaged feed pump', d.salvage[2], .04)
    bolts = d.part(scar, 'pump bolt heads', d.steel, .005)
    path = [m(q) for q in FEED_HOSE]
    pp = m(FEED_PUMP)
    hose.tube(path+[pp-V((1.1,0,0))], .2, 8)
    hose.tube([pp+V((1.1,0,0))]+[m(q) for q in FEED_TO_CAN], .2, 8)
    pump.disc(pp, X, .75, 2.0, 10)
    pump.obox(pp-V((0,0,.75)), X, Y, Z, 1.1, .7, .1)
    for dx in (-.8, .8):
        bolts.disc(pp+V((dx, 0, -.6)), Z, .08, .08, 6)
    for a, b in zip(path, path[1:]):
        t = (b-a).normalized()
        clips.obox((a+b)/2, t, Z.cross(t).normalized(), Z, .15, .32, .3)


def pod_engines(s):
    return [(i, x, y, w) for i, (x, y, w) in enumerate(ENGINES, 1) if y*s > 0]


def anchors(d, scar, s, running):
    """Tag each engine's exhaust anchor running or not for this state."""
    for i, *_ in pod_engines(s):
        ob = bpy.data.objects.get(f'Engine {i} exhaust anchor')
        if ob is not None:
            d.tag(ob, 'running', running, scar)


def throats(d, scar, s):
    """Both engines of a pod firing: the named dark throat glows (the part the
    cinematic registers its glow on) and the bell takes heat."""
    for i, *_ in pod_engines(s):
        throat = d.find(f'Engine {i} dark throat', None)
        if throat:
            d.override(throat, d.glow, scar)
        bell = d.find(f'Engine {i} aft-facing bell', None)
        if bell:
            d.override(bell, d.heat, scar)
    anchors(d, scar, s, True)


def cold(d, scar, s):
    """A pod shut down after it last ran: soot-scorched bells, no glow."""
    soot = d.m('Scar cold scorched bell', (.022,.02,.019), .35, .8)
    for i, *_ in pod_engines(s):
        bell = d.find(f'Engine {i} aft-facing bell', None)
        if bell:
            d.override(bell, soot, scar)
    anchors(d, scar, s, False)


def kind_drive(d, scar, status):
    """A drive pod's life (ENGINE_STATUSES).
    params: side (1 port, -1 starboard), stubs ('cut' or None) when dead.
    dead:    coil lifted out, empty cradle; feed lines cut or left. Cold,
             bells soot-scorched.
    capped:  cold, empty cradle, cut feed lines blind-flanged, its warning
             lights dark, bells soot-scorched from the last time it ran.
             Nozzles stay open.
    jury:    salvaged coil tied down on the cradle, jury propellant feed,
             throats lit.
    running: the pod as built (the strip rows reinstate its machinery),
             throats lit.
    Every status tags 'Engine {i} exhaust anchor' running True/False."""
    s = scar.params['side']
    if status == 'dead':
        cradle(d, scar, s, scar.params.get('stubs'))
        cold(d, scar, s)
    elif status == 'capped':
        cradle(d, scar, s, 'flanged')
        for ob in d.named('Drive warning light', s):
            d.override(ob, d.dead, scar)
        cold(d, scar, s)
    elif status == 'jury':
        coil(d, scar, s)
        jury_feed(d, scar, s)
        throats(d, scar, s)
    elif status == 'running':
        throats(d, scar, s)


# ---- stripped machinery ------------------------------------------------------
def kind_pod_strip(d, scar, status):
    """Pod machinery stripped by salvagers (as the core strip kind, but the
    bare pads are seated on the housing deck itself, not on the hidden part).
    params: parts [(name prefix, side)], pads [(x, y, side, hx, hy)],
            keep: substrings of names the prefixes match but must stay.
    open/patched: hidden, bare pads and studs. replaced/faded: reinstalled."""
    if status not in ('open', 'patched'):
        return
    keep = scar.params.get('keep', ())
    for name, sign in scar.params['parts']:
        for ob in d.named(name, sign):
            if not any(k in ob.name for k in keep):
                d.hide(ob, scar)
    for x, y, s, hx, hy in scar.params.get('pads', ()):
        top = deck(d, x, y, s)+Z*.04
        d.part(scar, 'stripped pad', d.dark, .02).obox(top, X, Y, Z, hx, hy, .05)
        studs = d.part(scar, 'stripped stud', d.steel, .01)
        for sx in (-1, 1):
            for sy in (-1, 0, 1):
                studs.disc(top+V((sx*(hx-.5), sy*(hy-.5), .15)), Z, .16, .25, 6)


# ---- the crushed can and the bent fins --------------------------------------
# Engine 1's crush: the strike came in from outboard and a little above, on
# the can's upper outboard flats (STRIKE_DEG, in the engine's (outboard, up)
# plane). Knots of every angular profile sit on multiples of 30 deg, which
# land on vertices of both the 36-segment bell sheets and the 48-segment
# lip and rings, so the folds come out as crisp creases, not swells.
STRIKE_DEG = 30.0
# Inward travel at the strike centre of the mouth: 40% of the 3.9 bell radius.
MOUTH_CRUSH = 1.56
# The struck side is pressed flat against a plane (tilted TILT_DEG off the
# strike centre, the rock having come in glancing), so the flat is planar
# and its edges are the crease lines; a shallow fold runs along the flat at
# FOLD_DEG. The flanks bulge out (BULGE, by angle from the strike).
TILT_DEG = 10.0
FOLD_DEG, FOLD, FOLD_W = 10.0, .16, 35.0
BULGE = [(-180,0), (-120,.07), (-90,.2), (-60,.09), (-30,0), (60,0), (90,.2), (120,.07), (180,0)]
# Fraction of MOUTH_CRUSH by depth forward of the mouth plane: full over the
# mouth, a circumferential buckle at .3 and 1.2, gone before the throat.
CRUSH_DEPTH = [(-1,1), (.3,1), (1.4,.5), (2.7,0)]
# The bell cocked off its axis toward the strike: no rotation at the throat
# (it stays seated on the chamber), BEND_DEG from the gimbal plane aft.
BEND_DEG = 5.0
GIMBAL_A = .6
# Gimbal ring dished aft on the struck side (units by angle from the strike).
RING_DISH = [(-180,0), (-90,0), (-30,.2), (0,.36), (30,.3), (90,0), (180,0)]
# Sprung film-cooling bands: band -> (shift, toward deg from the strike, aft tilt).
SPRUNG = {2: (.36, 75, .14), 4: (.22, -50, .1)}


def piecewise(knots, t):
    for (t0, v0), (t1, v1) in zip(knots, knots[1:]):
        if t <= t1:
            return v0+(v1-v0)*(t-t0)/(t1-t0) if t >= t0 else v0
    return knots[-1][1]


def rel_deg(phi):
    """Angle (deg) from the strike centre, in [-180, 180)."""
    return (math.degrees(phi)-STRIKE_DEG+180) % 360-180


class Crush:
    """An engine's crush field, in world space around the engine axis.
    Angles are in the engine's (away-from-partner u, up) plane, as in
    sw_engines; depth a runs forward of the mouth plane."""
    def __init__(self, engine):
        self.x, self.y, w = ENGINES[engine]
        self.w = w
        self.r = BELL_R[w]
        self.side = 1 if self.y > 0 else -1
        self.u = self.side*(1 if w == 12 else -1)
        a = math.radians(STRIKE_DEG)
        self.s = V((0, self.u*math.cos(a), math.sin(a)))   # outward at the strike centre
        self.L = BELL_L*self.r
        self.rho = bell_curve(self.r)

    def polar(self, w):
        dy, dz = (w.y-self.y)*self.u, w.z
        return math.hypot(dy, dz), math.atan2(dz, dy)

    def radial(self, phi):
        return V((0, self.u*math.cos(phi), math.sin(phi)))

    def at(self, phi_deg, rho, a):
        """World point at angle phi (deg), radius rho, depth a."""
        return V((self.x+a, self.y, 0))+self.radial(math.radians(phi_deg))*rho

    def offset(self, w):
        """Displacement of a clean world point: each shell pressed flat
        against a plane MOUTH_CRUSH (times depth falloff) inside its own
        radius, folded along the flat, the flanks bulged."""
        rho, phi = self.polar(w)
        f = piecewise(CRUSH_DEPTH, w.x-self.x)
        if not f or rho < 1e-6:
            return V((0, 0, 0))
        D = MOUTH_CRUSH*f
        th = math.radians(rel_deg(phi)-TILT_DEG)
        push = max(0, D-rho*(1-math.cos(th)))
        rel = rel_deg(phi)
        if push:
            push += FOLD*f*max(0, 1-abs(rel-FOLD_DEG)/FOLD_W)
        s = self.radial(math.radians(STRIKE_DEG+TILT_DEG))
        bulge = piecewise(BULGE, rel)*f
        return -s*push+self.radial(phi)*bulge
    def bend(self, q, w):
        """Cock a crushed point q (clean point w) about the throat, in the
        plane of the engine axis and the strike: the mouth swings toward the
        strike side, the throat stays where it is."""
        a = w.x-self.x
        t = min(1, max(0, (self.L-a)/(self.L-GIMBAL_A)))
        if not t:
            return q
        th = math.radians(BEND_DEG)*t
        pivot = V((self.x+self.L, self.y, 0))
        v = q-pivot
        px, ps = v.x, v.dot(self.s)
        nx, ns = px*math.cos(th)+ps*math.sin(th), -px*math.sin(th)+ps*math.cos(th)
        return q+X*(nx-px)+self.s*(ns-ps)

    def full(self, w):
        """Crushed and cocked: everything that hangs on the gimbal."""
        return self.bend(self(w), w)

    def exhaust_dir(self):
        th = math.radians(BEND_DEG)
        return -X*math.cos(th)+self.s*math.sin(th)

    def dish(self, w):
        _, phi = self.polar(w)
        return V((-piecewise(RING_DISH, rel_deg(phi)), 0, 0))

    def __call__(self, w):
        return w+self.offset(w)


def liner_band(c, w):
    """Film-cooling band index of a clean liner point (joints carry two rings:
    the deeper band's starts inset by the step)."""
    a = w.x-c.x
    pitch = c.L/BANDS_N
    j = a/pitch
    J = round(j)
    if abs(j-J) < 1e-3 and 0 < J < BANDS_N:
        rho, _ = c.polar(w)
        return J if rho < c.rho(a)-STEP*c.r*.5 else J-1
    return min(max(int(j), 0), BANDS_N)


def collar_flap(d, scar, c, part, mat, phi0, phi1, r_in, r_out, x_fore, x_aft, peel):
    """A torn span of an annular plate: stations every 7.5 deg from phi0
    (still attached) to phi1 (free, jagged), peeled aft and out by
    peel(fraction) = (aft, out)."""
    geo = d.part(scar, part, mat, .02)
    n = max(2, round(abs(phi1-phi0)/7.5))
    rng = random.Random(scar.id+part)
    rings = []
    for k in range(n+1):
        t = k/n
        phi = phi0+(phi1-phi0)*t
        aft, out = peel(t)
        ro = r_out-(rng.uniform(.15, .45) if k == n else 0)
        ri = r_in+(rng.uniform(0, .2) if k == n else 0)
        pts = []
        for rr, xx in ((ri, x_fore), (ro, x_fore), (ro, x_aft), (ri, x_aft)):
            q = c.at(phi, rr, xx-c.x)
            pts.append(c(q)+c.radial(math.radians(phi))*out-X*aft)
        rings.append(pts)
    geo.loft(rings)


def snapped_rod(d, scar, c, ob):
    """The top gimbal ram: the ring dished aft under it and the rod broke.
    The span between the lever ear and the gland is cut out; a stub stays
    in the gland and the ear keeps a bent stub, sprung up and outboard."""
    lo, hi = d.box(ob)
    py, pz = (lo.y+hi.y)/2, (lo.z+hi.z)/2
    rr = (hi.y-lo.y)/2
    x_aft, x_fore = lo.x, hi.x          # pinned in the ear, into the gland
    cut = Geo()
    a0, a1 = x_aft+.26, x_fore-.28
    cut.obox(V(((a0+a1)/2, py, pz)), X, Y, Z, (a1-a0)/2, rr+.12, rr+.12)
    d.cut(cut, [ob.name], d.backing)
    chrome = d.m('Scar broken actuator rod chrome', (.5, .52, .53), .9, .22)
    fracture = d.m('Scar fresh fracture', (.62, .6, .56), .85, .5)
    rod = d.part(scar, 'snapped actuator rod', chrome, .01)
    ends = d.part(scar, 'rod fracture face', fracture, 0)
    # Gland stub: straight, broken off .45 aft of the gland.
    g0, g1 = V((a1+.05, py, pz)), V((a1-.45, py, pz))
    rod.tube([g0, g1], rr, 10)
    ends.tube([g1, g1+V((-.07, .03, .05)), g1+V((-.12, -.02, .09))], rr*.75, 6)
    # Ear stub: follows the ring's dish and crush, then kinks up/outboard.
    e0 = V((a0-.05, py, pz))
    e0 = c.bend(c(e0)+c.dish(e0), e0)
    out = V((0, c.u*.35, .85)).normalized()
    e1 = e0+X*.45
    e2 = e1+(X*.7+out*.45).normalized()*.6
    rod.tube([e0, e1, e2], rr, 10)
    tip = (e2-e1).normalized()
    ends.tube([e2, e2+tip*.08+V((0, .03, .04)), e2+tip*.13+V((0, -.03, .08))], rr*.75, 6)


# The can's struck span is rebuilt as its own crumpled wall (the 12-sided
# taper has no rings between its face and its band, too coarse to dent with
# a shape key): columns of 7.5 deg across SPAN_DEG, SPAN_ROWS rows from the
# face to the band, full wall thickness down to the bell well.
SPAN_DEG = (-45.0, 75.0)
SPAN_COLS, SPAN_ROWS = 48, 24
# Torn through the wall at the aft rim: depth of the tear forward of the
# face (units) by angle (deg).
HOLE_DEPTH = [(0,0), (8,.45), (15,1.4), (22,1.95), (30,1.9), (37,1.5), (45,1.0), (52,.5), (58,0)]
# The outer skin over the tear, folded out about a hinge FLAP_ROW rows in.
FLAP_ROW = 9


def can_radius(c, phi_deg, x):
    """Outer surface radius of the clean 12-sided can taper at (phi, x)."""
    xb, rb = CAN[c.w][-1]
    xf, rf = c.x+FACE_GAP, CAN_FACE_R[c.w]
    r = rf+(rb-rf)*(x-xf)/(xb-xf)
    delta = ((phi_deg-15) % 30)-15
    return r*math.cos(math.radians(15))/math.cos(math.radians(delta))


def grid_solid(geo, outer, inner, present, truth=None):
    """Closed solid between two node grids (lists of columns of points):
    faces for present cells and side walls on every open edge. With
    truth(i0, j0, i1, j1) (the true outer surface at the middle of a cell
    diagonal), each cell is split along the diagonal that lies on the
    surface, so a crease crossing the grid stays one straight fold rather
    than a sawtooth."""
    ni, nj = len(outer), len(outer[0])
    base = len(geo.v)
    idx = lambda g, i, j: base+g*ni*nj+i*nj+j
    verts = [p for col in outer for p in col]+[p for col in inner for p in col]
    faces = []
    has = lambda i, j: 0 <= i < ni-1 and 0 <= j < nj-1 and present(i, j)
    for i in range(ni-1):
        for j in range(nj-1):
            if not present(i, j):
                continue
            q = [(i, j), (i+1, j), (i+1, j+1), (i, j+1)]
            if truth is None:
                tris = [q]
            else:
                err = lambda a, b: (truth(*a, *b)-(outer[a[0]][a[1]]+outer[b[0]][b[1]])/2).length
                if err(q[0], q[2]) <= err(q[1], q[3]):
                    tris = [[q[0], q[1], q[2]], [q[0], q[2], q[3]]]
                else:
                    tris = [[q[0], q[1], q[3]], [q[1], q[2], q[3]]]
            for t in tris:
                faces.append(tuple(idx(0, a, b) for a, b in t))
                faces.append(tuple(idx(1, a, b) for a, b in reversed(t)))
            for (a, b), (e, f), nb in (((i, j), (i+1, j), (i, j-1)), ((i+1, j), (i+1, j+1), (i+1, j)),
                                       ((i+1, j+1), (i, j+1), (i, j+1)), ((i, j+1), (i, j), (i-1, j))):
                if not has(*nb):
                    faces.append((idx(0, a, b), idx(1, a, b), idx(1, e, f), idx(0, e, f)))
    geo.v += [tuple(p) for p in verts]
    geo.f += faces


def kind_crushed_can(d, scar, status):
    """The port outer can and its nozzle, crushed from outboard against the
    anchor rock as the hulk swung on its tether.
    params: origin, target (the strike, for the site record), engine,
            dent (centre deg, (aft, fore) x of the floor, depth,
                  (fore, aft) slopes of the side plates).
    open/patched: the can's struck span stove in: a flat floor between two
    sloped plates, three planes meeting in straight creases across the
    struck flats
    and torn through by its aft rim, the skin folded out and a broken frame
    showing in the void; the bell mouth pressed flat out of round on the
    struck side (~24% of the radius) with crisp creases, two film-cooling
    bands sprung off-axis, the heat-shield band slipped, the gimbal ring
    dished aft, the top ram snapped, the shroud collar torn with a span
    peeled aft. Shape keys on the Engine parts; nothing is capped. The
    exhaust anchor (never moved) is tagged mouth_deformed with the
    out-of-round mouth axes, so a cinematic does not plume it.
    replaced/faded: a replacement can and nozzle, i.e. the drive as built
    (handled as 'gone' by the ladder)."""
    if status not in ('open', 'patched'):
        return
    P = scar.params
    d.site(scar, P['origin'], P['target'])
    i = P['engine']+1
    c = Crush(P['engine'])
    x, y = c.x, c.y
    k = c.r/3.9
    can = d.find('Thrust bay engine can', (x+5, y), c.side)
    if can is None:
        return
    face = x+FACE_GAP
    strike = math.radians(STRIKE_DEG)
    dent_deg, (x_aft, x_fore), dent_d, (k_fore, k_aft) = P['dent']
    dent_n = c.radial(math.radians(dent_deg))
    xm = (x_aft+x_fore)/2
    floor = c.at(dent_deg, can_radius(c, dent_deg, xm), xm-x)-dent_n*dent_d
    # Every plane's normal lies in the (strike, X) plane, so every crease
    # runs at constant x: along a row of the span's grid, never across it.
    planes = [(floor, dent_n),
              (floor+X*(x_fore-xm), (dent_n-X*k_fore).normalized()),
              (floor+X*(x_aft-xm), (dent_n+X*k_aft).normalized())]

    def dent(w):
        """The flank's dent: the can's outer skin (at w's angle and x)
        pushed in to the outermost of four planes through the pit's bottom,
        so it comes out as flat plates with crisp creases. Outer skin and
        bore move together."""
        _, phi = c.polar(w)
        pd = math.degrees(phi)
        q = c.at(pd, can_radius(c, pd, w.x), w.x-x)
        h = max(-(q-b).dot(n)/dent_n.dot(n) for b, n in planes)
        return dent_n*min(0, h)

    def field(w):
        return w+c.offset(w)+dent(w)
    d.shape(can, scar, field)

    # The span: cut out of the can (a hair wider, so the tear reads as a
    # dark seam) and rebuilt crumpled.
    lo_deg, hi_deg = SPAN_DEG
    back = CAN[c.w][-1][0]-.05
    bore = c.r+.45
    geo = Geo()
    rings = []
    for t in range(SPAN_COLS+1):
        phi = lo_deg-.6+(hi_deg-lo_deg+1.2)*t/SPAN_COLS
        rings.append([c.at(phi, rr, xx-x) for rr, xx in
                      ((bore-.3, face-.3), (7.6, face-.3), (7.6, back), (bore-.3, back))])
    geo.loft(rings)
    d.cut(geo, [can.name], d.backing)
    rng = random.Random(scar.id+' span')
    row_h = (back+.05-face)/SPAN_ROWS
    col_w = (hi_deg-lo_deg)/SPAN_COLS
    def hole(a):
        """Rows of column a torn away from the face."""
        if not 0 <= a < SPAN_COLS:
            return 0
        depth = piecewise(HOLE_DEPTH, lo_deg+col_w*(a+.5))
        return sum(1 for b in range(SPAN_ROWS) if (b+.5)*row_h < depth)
    nodes = {}
    for a in range(SPAN_COLS+1):
        for b in range(SPAN_ROWS+1):
            phi = lo_deg+(hi_deg-lo_deg)*a/SPAN_COLS
            xx = face+(back+.05-face)*b/SPAN_ROWS
            # Only the tear's edge is ragged; the dent keeps its clean facets.
            torn = any(0 <= b+db < hole(a+da) for da in (-1, 0) for db in (-1, 0))
            r1, r2 = rng.uniform(-1.4, 1.4), rng.uniform(-.09, .09)
            if torn and 0 < a < SPAN_COLS:
                phi += r1   # rows stay straight: the creases run along them
            nodes[a, b] = (phi, xx)
    outer = [[field(c.at(nodes[a, b][0], can_radius(c, *nodes[a, b]), nodes[a, b][1]-x))
              for b in range(SPAN_ROWS+1)] for a in range(SPAN_COLS+1)]
    inner = [[field(c.at(nodes[a, b][0], bore, nodes[a, b][1]-x))
              for b in range(SPAN_ROWS+1)] for a in range(SPAN_COLS+1)]
    metal = d.torn(d.base_mat(can))
    def truth(a0, b0, a1, b1):
        phi = (nodes[a0, b0][0]+nodes[a1, b1][0])/2
        xx = (nodes[a0, b0][1]+nodes[a1, b1][1])/2
        return field(c.at(phi, can_radius(c, phi, xx), xx-x))
    grid_solid(d.part(scar, 'crumpled can span', metal, .02), outer, inner,
               lambda a, b: b >= hole(a), truth)

    # The skin over the tear, folded out about the hole's forward edge.
    cols = [a for a in range(SPAN_COLS) if hole(a) > FLAP_ROW]
    a0, a1 = cols[0], cols[-1]+1
    fo, fi = [], []
    for a in range(a0, a1+1):
        hinge = outer[a][FLAP_ROW]
        en = c.radial(math.radians(nodes[a, FLAP_ROW][0]))
        v1 = -X*math.cos(math.radians(42))+en*math.sin(math.radians(42))
        v2 = -X*math.cos(math.radians(84))+en*math.sin(math.radians(84))
        col_o, col_i = [], []
        for b in range(FLAP_ROW+1):
            dist = nodes[a, FLAP_ROW][1]-nodes[a, b][1]
            q = hinge+v1*min(dist, .55)+v2*max(0, dist-.55)
            nrm = v1.cross(c.radial(math.radians(nodes[a, b][0]+90))).normalized()
            if nrm.dot(en) < 0:
                nrm = -nrm
            col_o.append(q)
            col_i.append(q-nrm*.18)
        fo.append(col_o)
        fi.append(col_i)
    grid_solid(d.part(scar, 'torn can skin', metal, .02), fo, fi, lambda a, b: True)

    # A frame ring inside the tear, broken over the strike and bent in.
    frame = d.part(scar, 'broken can frame', d.steel, .02)
    fx = face+.75
    for lo, hi, bend in ((8.0, 27.0, 0), (29.0, 46.0, 1)):
        rings = []
        for t in range(5):
            phi = lo+(hi-lo)*t/4
            f = (t/4)*bend
            ring = [c.at(phi, rr, xx-x) for rr, xx in
                    ((bore+.1, fx-.07), (bore+.65, fx-.07), (bore+.65, fx+.07), (bore+.1, fx+.07))]
            rings.append([field(q)-c.radial(math.radians(phi))*.45*f-X*.25*f for q in ring])
        frame.loft(rings)

    pre = f'Engine {i} '
    for ob in d.named(pre):
        name = ob.name[len(pre):]
        if name.startswith('aft-facing bell'):
            def liner(w):
                q = c(w)
                spec = SPRUNG.get(liner_band(c, w))
                if spec:
                    shift, toward, tilt = spec
                    t = strike+math.radians(toward)
                    _, phi = c.polar(w)
                    q = q+c.radial(t)*shift-X*tilt*math.cos(phi-t)
                return c.bend(q, w)
            d.shape(ob, scar, liner)
        elif name.startswith('heat-shield band'):
            # Shaken loose on its seat: slipped aft and off the axis.
            d.shape(ob, scar, lambda w: c.bend(c(w)-X*.22+c.radial(strike+math.pi)*.12, w))
        elif name.startswith(('gimbal ring', 'gimbal trunnion', 'trunnion pin cap', 'gimbal lever ear')):
            d.shape(ob, scar, lambda w: c.bend(c(w)+c.dish(w), w))
        elif name.startswith('shroud collar'):
            d.shape(ob, scar, c)   # fixed to the can face: crushed, not cocked
        elif name.startswith('actuator rod') and d.box(ob)[0].z > 3:
            snapped_rod(d, scar, c, ob)
        elif not name.startswith(('dark throat', 'injector', 'throat ring')):
            d.shape(ob, scar, c.full)

    # Torn shroud collar: the struck span cut out, part of it peeled aft.
    collar = d.find(pre+'shroud collar', None)
    if collar is not None:
        x0, x1 = face+.2, x+COLLAR_AFT*k
        geo = Geo()
        rings = []
        for phi in (-28, -14, 0, 16, 32):
            t = STRIKE_DEG+phi
            rings.append([c.at(t, rr, xx-x) for rr, xx in
                          ((3.2, x0+.3), (6.6, x0+.3), (6.6, x1-.3), (3.2, x1-.3))])
        geo.loft(rings)
        d.cut(geo, [collar.name], d.backing)
        r_in = c.r+.45
        r_out = CAN_FACE_R[c.w]*math.cos(math.pi/12)-.15
        collar_flap(d, scar, c, 'torn collar span', d.torn(d.base_mat(collar)),
                    STRIKE_DEG+30, STRIKE_DEG-8, r_in, r_out, x1, x0,
                    lambda t: (piecewise([(0,0), (.4,.15), (.7,.5), (1,1.05)], t),
                               piecewise([(0,0), (.4,.06), (.7,.3), (1,.6)], t)))

    # The anchor: never moved; tag the cold, out-of-round mouth.
    anchor = bpy.data.objects.get(f'Engine {i} exhaust anchor')
    if anchor is not None:
        mouth = [c.full(c.at(t, c.r, 0)) for t in range(0, 360, 2)]
        ax = c.exhaust_dir()
        flat = lambda v: (v-ax*v.dot(ax)).normalized()
        span = lambda v: max(q.dot(v) for q in mouth)-min(q.dot(v) for q in mouth)
        centre = sum(mouth, V((0, 0, 0)))/len(mouth)
        m = .65
        d.tag(anchor, 'mouth_deformed', True, scar)
        # Minor and major: the narrowest and widest calipers across the
        # mouth, square to the cocked exhaust axis.
        dirs = [flat(c.radial(math.radians(t))) for t in range(0, 180, 2)]
        minor = min(dirs, key=span)
        major = max(dirs, key=span)
        d.tag(anchor, 'mouth_minor_m', round(span(minor)*m, 3), scar)
        d.tag(anchor, 'mouth_major_m', round(span(major)*m, 3), scar)
        d.tag(anchor, 'mouth_axis_dir', [round(v, 4) for v in ax], scar)
        d.tag(anchor, 'mouth_tilt_deg', BEND_DEG, scar)
        d.tag(anchor, 'mouth_round_m', round(2*c.r*m, 3), scar)
        d.tag(anchor, 'mouth_minor_dir', [round(v, 4) for v in minor], scar)
        d.tag(anchor, 'mouth_centre_offset_m',
              [round((centre[q]-anchor.location[q])*m, 3) for q in range(3)], scar)


def kind_bent_fins(d, scar, status):
    """Heat-sink fins bent aft-and-fore at their top outboard corner.
    params: side, fins [(fin index, shift)].
    open/patched: bent (shape keys). replaced/faded: straightened or renewed."""
    if status not in ('open', 'patched'):
        return
    s = scar.params['side']
    for i, dx in scar.params['fins']:
        for ob in d.named('Heat-sink fin', s):
            lo, hi = d.box(ob)
            if abs((lo.x+hi.x)/2-FIN_X[i]) > .3:
                continue
            def bend(w, dx=dx):
                y, z = abs(w.y), w.z
                if not (y > 30 and z > 7.5):
                    return w
                reach = min(1, (y-29)/6+max(0, z-7.5)/6)
                return w+X*dx*max(0, reach)
            d.shape(ob, scar, bend)


# ---- the tether padeye ---------------------------------------------------------
def kind_tether(d, scar, status):
    """Where the hulk was moored on the windward flank.
    params: origin, target.
    open:    padeye, the cut tether end still shackled on, a chafe band.
    patched: cut loose for the escape: padeye and chafe band, shackle pin only.
    faded:   the padeye ground off, its weld pad painted over."""
    P = scar.params
    site = d.site(scar, P['origin'], P['target'])
    if site is None:
        return
    p, n = site.p, site.n
    u = X
    v = n.cross(u).normalized()
    if status == 'faded':
        ring = [d.snap(p+u*sx*.8+v*sy*.6, n, .01) for sx, sy in ((-1,-1),(1,-1),(1,1),(-1,1))]
        d.part(scar, 'ground-off weld pad', d.repaint(d.base_mat(site.ob)), .02).flat(ring, n, .05)
        return
    ring = [d.snap(p+u*sx*1.5+v*sy*1.1, n, .02) for sx, sy in ((-1,-1),(1,-1),(1,1),(-1,1))]
    d.part(scar, 'chafe band', d.salvage[1], .01).flat(ring, n, .03)
    eye = d.part(scar, 'padeye', d.steel, .03)
    eye.obox(p+n*.18, u, v, n, .7, .5, .14)
    for sy in (-1, 1):
        eye.obox(p+n*.65+v*sy*.2, u, v, n, .4, .06, .45)
    eye.disc(p+n*.85, v, .13, .7, 8)
    welds = d.part(scar, 'weld bead', d.weld, .01)
    c = [p+u*sx*.7+v*sy*.5+n*.04 for sx, sy in ((-1,-1),(1,-1),(1,1),(-1,1))]
    for a, b in zip(c, c[1:]+c[:1]):
        welds.bead(a, b, n, .07)
    if status == 'open':
        rope = d.part(scar, 'frayed tether stub', d.cable, .01)
        end = p+n*2.2+u*.5-v*.3
        rope.tube([p+n*.85, p+n*1.5+u*.3, end], .22, 8)
        for du, dv in ((.35,.2), (-.1,.4), (.25,-.35)):
            rope.tube([end, end+n*.5+u*du+v*dv], .07, 5)


KINDS = {'drive': kind_drive, 'pod_strip': kind_pod_strip, 'crushed_can': kind_crushed_can,
         'bent_fins': kind_bent_fins, 'tether': kind_tether}

PUMPS = ('Turbopump housing', 'Turbopump vent slot', 'Pump drive drum')

SCARS = [
    # Engines.
    Scar('pod-port-drive', 'drive', 'dead capped capped running',
         'Coil lifted and feeds cut in the belt; blind-flanged and left cold for the '
         'escape and the refit; restored with a new coil.',
         vocab=ENGINE_STATUSES, side=1, stubs='cut'),
    Scar('pod-starboard-drive', 'drive', 'dead jury jury running',
         'Coil lifted in the belt; the escape ran on a salvaged coil and a jury feed; '
         'restored to the pod as built.',
         vocab=ENGINE_STATUSES, side=-1),
    # Stripped machinery.
    Scar('pod-port-pumps', 'pod_strip', 'open open open gone',
         'Turbopumps taken off the port housing deck; reinstalled at restoration.',
         parts=[(name, 1) for name in PUMPS], pads=[(-30, 32, 1, 2.0, 2.8)]),
    Scar('pod-starboard-pumps', 'pod_strip', 'open open open gone',
         'Turbopumps taken off the starboard deck; the jury coil and feed pump stood in '
         'for them until restoration.',
         parts=[(name, -1) for name in PUMPS], pads=[(-30, -32, -1, 2.0, 2.8)]),
    Scar('pod-port-can-feeds', 'pod_strip', 'open open open gone',
         'Port can feed pipes cut out for their metal; renewed at restoration.',
         parts=[('Can feed pipe', 1)]),
    Scar('pod-casing-access-panels', 'pod_strip', 'open open open gone',
         'Both pods\' outboard access panels carried off; never needed for the flight.',
         parts=[('Casing access panel', 0), ('Panel grab strap', 0)], keep=('frame',)),
    # The crushed can and the bent fins.
    Scar('pod-port-outer-can', 'crushed_can', 'open open open gone',
         'Engine 1 can stove in and torn, bell mouth crushed out of round, top ram snapped, '
         'collar torn: crushed against the anchor rock; a new can and nozzle at restoration.',
         origin=(-38,60,5), target=(-38,33,2.5), engine=0, dent=(25, (-38.9, -37.9), 1.4, (.9, 1.3))),
    Scar('pod-port-fins', 'bent_fins', 'open open open gone',
         'Top outboard fins of the windward bank bent; cold pod, left until restored.',
         side=1, fins=[(1,.75), (2,-.55), (3,.9), (4,.5)]),
    # Windward strikes (continuity scar 2 is the shoulder).
    Scar('pod-port-aft-shoulder', 'basin', 'open open open faded',
         'Heaviest strike on the ship, over the dead pod; never plated, painted over.',
         origin=(-12,58,32), target=(-12,36.5,13.5), r=3.6, depth=1.6, elong=1.2, shards=10),
    Scar('pod-port-lower-casing', 'basin', 'open open open faded',
         'Windward strike low on the port casing.',
         origin=(-9,62,-2), target=(-9,37,-2.5), r=2.0, depth=.9, elong=1.3, shards=6),
    Scar('pod-port-intake-cap', 'basin', 'open open open faded',
         'Windward strike on the port intake cap.',
         origin=(-1,60,5), target=(-1,36,5), r=1.8, depth=.8, elong=1.2, shards=6),
    Scar('pod-starboard-cowl-dent', 'basin', 'open open open faded',
         'Lee-side slow knock: a shallow lipless dent.',
         origin=(-14.5,-33,60), target=(-14,-33.4,0), r=1.5, depth=.4, elong=1.1),
    Scar('pod-port-shoulder-cluster', 'cluster', 'open open open faded',
         'Small strikes round the shoulder crater.',
         cast='down', centre=(-12,32), spread=(6,3.5), count=10, radii=(.3,.75)),
    Scar('pod-port-flank-cluster', 'cluster', 'open open open faded',
         'Small strikes on the port pod flank.',
         cast='port', centre=(-14,3), spread=(8,3), count=7, radii=(.3,.65)),
    Scar('pod-port-contact-scrape', 'gouge', 'open open open faded',
         'Rock rolled along the port pod as the hulk swung.',
         start=(-15,62,-3.2), end=(-31,62,-4.2), cast_dir=(0,-1,0), w=.55, depth=.35),
    Scar('pod-port-cowl-gouge', 'gouge', 'open open open faded',
         'Glancing gouge across the port cowl top.',
         start=(-2.5,27,60), end=(-7,34.5,60), cast_dir=(0,0,-1), w=.6, depth=.4),
    # Casing deck plates.
    Scar('pod-port-casing-plate', 'plate', 'open open open faded',
         'Unbolted by salvagers off the cold pod; plated only at restoration.',
         object='Drive casing armour plate', centre=(-11.85,28.6), how='unbolted'),
    Scar('pod-starboard-casing-plate', 'plate', 'open patched replaced faded',
         'Unbolted by salvagers; salvage sheet over the running pod for the escape, a '
         'proper plate at the refit.',
         object='Drive casing armour plate', centre=(-7.7,-29.45), how='unbolted'),
    Scar('pod-starboard-casing-buckle', 'buckle', 'open open open faded',
         'Casing plate buckled by slow contact; renewed at restoration.',
         object='Drive casing armour plate', centre=(-11.85,-28.6), fold=.36),
    Scar('pod-tether-padeye', 'tether', 'open patched patched faded',
         'The aft mooring point; tether cut at the shackle for the escape; ground off '
         'at restoration.',
         origin=(-12,62,-4.6), target=(-12,30,-4.6)),
]
