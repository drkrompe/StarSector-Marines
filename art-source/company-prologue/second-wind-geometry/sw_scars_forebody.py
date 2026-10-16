"""Scars: forebody and grapple flank (x > ~20), both sides.

Exposure: for the years in the belt the hulk hung on its bow tether with its
port side and deck in the drift, so the fast strikes that leave a lipped
basin are on the port flank, the grapple sponson and the bow; the starboard
lee has only slow knocks that dent without a lip. Glancing contact scraped
the sponson and the glacis seams. Salvagers unbolted one deck plate.

The ladder here:
- patched (the escape): only the sponson cowl strike, which canon puts a rough
  salvage patch on under the armour collar at arrival, the bow cheek doubler
  over occupied space, the bowed starboard bay jamb shored with cargo rail and
  the jammed bay door welded shut. Everything else left as found.
- refit (berthing/command conversion): only 'the forward collar sorted out' -
  the collar patch becomes a proper plate. Nothing else here was touched.
- restored: heavy (lipped) craters, spalls and torn/missing/buckled plates
  are repainted replacement panels; shallow dents, scrapes and small strikes
  are painted over. The bowed jamb span is cut out and a straight section
  welded in; the door's dog plates ground off; the tether padeye cut off and
  ground flush.
"""
import math
import random
from mathutils import Vector
from sw_damage import (Scar, Geo, V, basis, TAU, kind_basin, kind_spall, kind_gouge,
                       kind_cluster, kind_plate, kind_buckle)

# Occupied space under the spine and command deck (shared with the spine area).
PRESSURE_ZONE = (-6, 32, -8.6, 8.6, 14)
NO_ZONE = (0, 0, 0, 0, 0)   # no site is ever repaired by the escape crew

# The sealed starboard bay's forward frame jamb: the bowed span between the
# coamings, (z, outboard bow) stations. The jamb's aft half lies inside the
# bay buttress (x 26..28), so only the span forward of it is cut and rebuilt.
RIB_SPAN = [(-4.9, 0), (-2.6, .6), (-.4, 1.35), (1.8, .7), (4.0, 0)]
BUTTRESS_FACE_X = 26.0


def restorable(base):
    """Wrap a generic kind so a never-plated row whose restored status is
    'faded' with restore='panel' comes back as a repainted replacement panel
    (the heavy damage engineering re-plated) instead of a painted dent."""
    def fn(d, scar, status):
        P = scar.params
        if status == 'patched' and P.get('patch') == 'salvage' and 'patch_scale' in P:
            # A doubler cut to the crater, not to the whole facet.
            site = d.site(scar, P['origin'], P['target'])
            if site is None:
                return
            el, k = P.get('elong', 1.2), P['patch_scale']
            d.basin_cut(scar, site, P['r'], P['depth'], el, P.get('sides', 8))
            d.salvage_plate(scar, site, P['r']*k*el, P['r']*k)
            return
        if (status == 'faded' and scar.params.get('restore') == 'panel'
                and 'replaced' not in scar.history(d.state)):
            params = dict(scar.params)
            params['r'] = params.get('panel_r', params['r'])   # panel sized to its facet
            shim = Scar(scar.id, scar.kind, 'open open replaced faded', **params)
            return base(d, shim, status)
        return base(d, scar, status)
    return fn


basin = restorable(kind_basin)
spall = restorable(kind_spall)


def kind_dent(d, scar, status):
    """A slow lee knock: the plate dished, not holed, so the dent shows the
    part's own scuffed paint rather than backing. open: the dent. faded:
    painted over (a shallow repainted dish)."""
    P = scar.params
    site = d.site(scar, P['origin'], P['target'])
    if site is None:
        return
    if status == 'faded':
        return kind_basin(d, scar, status)
    d.basin_cut(scar, site, P['r'], P['depth'], P.get('elong', 1.2), P.get('sides', 8),
                d.torn(d.base_mat(site.ob)))


# ---- plates confined to one facet --------------------------------------------
def facet_rim(d, site, a, b, clip=.22):
    """A clipped-corner rectangle (half sizes a along u, b along v) in the plane
    of the facet the site lies on. Each rim point is pulled toward the centre
    until the skin under it is that same facet, so the plate never laps over a
    chine or onto a neighbouring part."""
    p, n, u, v, ob = site
    shape = [(1, 1-clip), (1-clip, 1), (-(1-clip), 1), (-1, 1-clip),
             (-1, -(1-clip)), (-(1-clip), -1), (1-clip, -1), (1, -(1-clip))]
    def on_facet(q):
        hit = d.cast(q+n*2, q-n*2)
        return (hit is not None and hit[2] is ob and hit[1].dot(n) > .995
                and abs((hit[0]-q).dot(n)) < .05)
    rim = []
    for su, sv in shape:
        q = p+u*su*a+v*sv*b
        for _ in range(24):
            # keep a small margin inside the facet edge
            if on_facet(q) and on_facet(q+(q-p).normalized()*.15):
                break
            q = p+(q-p)*.94
        rim.append(q)
    return rim


def facet_plate(d, scar, site, a, b, mat, part, lift=.05, thick=.1):
    p, n, *_ = site
    rim = facet_rim(d, site, a, b)
    d.part(scar, part, mat, .03).flat([q+n*lift for q in rim], n, thick)
    return rim, n


def kind_cheek(d, scar, status):
    """Port bow cheek strike, on the cheek facet above the sponson.
    open: lipped basin. patched: lips dressed, a doubler of dark salvage stock
    cut to the crater plus a weld margin, flat on the facet and welded all
    round (the escape crew's pressure work). faded: the doubler cut off and a
    proper plate in the same footprint, repainted to the hull, seams showing."""
    P = scar.params
    site = d.site(scar, P['origin'], P['target'])
    if site is None:
        return
    r, el = P['r'], P.get('elong', 1.2)
    a, b = r*1.4*el, r*1.4
    if status == 'open':
        return kind_basin(d, scar, status)
    p, n, u, v, ob = site
    if status == 'patched':
        d.basin_cut(scar, site, r, P['depth'], el, P.get('sides', 8))
        rim, n = facet_plate(d, scar, site, a, b, d.salvage[0], 'salvage doubler', .05, .12)
        beads = d.part(scar, 'weld bead', d.weld, .01)
        for q0, q1 in zip(rim, rim[1:]+rim[:1]):
            beads.bead(q0+n*.05, q1+n*.05, n, .12)
    elif status in ('replaced', 'faded'):
        base = d.base_mat(ob)
        mat = d.primer if status == 'replaced' else d.repaint(base)
        rim, n = facet_plate(d, scar, site, a, b, mat, 'replacement plate', .01, .06)
        seam = d.part(scar, 'seam', d.sealant if status == 'replaced' else mat, .005)
        for q0, q1 in zip(rim, rim[1:]+rim[:1]):
            seam.bead(q0+n*.01, q1+n*.01, n, .05)
        studs = d.part(scar, 'fasteners', d.fastener if status == 'replaced' else mat, .005)
        centre = sum(rim, Vector())/len(rim)
        for q0, q1 in zip(rim, rim[1:]+rim[:1]):
            steps = max(1, round((q1-q0).length/.7))
            for i in range(steps):
                q = q0.lerp(q1, (i+.5)/steps).lerp(centre, .1)
                studs.disc(q+n*.1, n, .06, .05, 6)


# ---- a crease on a flat panel ------------------------------------------------
def kind_crease(d, scar, status):
    """A slow knock on a flat panel: a shallow crease pressed in along a line,
    a few planar facets either side, no lip and no hole.
    params: origin, target (the knock), dir (in-plane line direction),
            length, depth, w (half width at the surface).
    open: the crease in the panel's scuffed paint. faded: faired almost flush
    and painted over; the line still reads."""
    P = scar.params
    site = d.site(scar, P['origin'], P['target'])
    if site is None:
        return
    p, n, _, _, ob = site
    t = V(P['dir'])
    t = (t-n*t.dot(n)).normalized()
    side = n.cross(t).normalized()
    L, dep, w = P['length'], P['depth'], P['w']
    if status == 'faded':
        dep, w = dep*.2, w*.6
        mat = d.repaint(d.base_mat(ob))
    else:
        mat = d.torn(d.base_mat(ob))
    # stations along the line: (position, depth share, width share, kink)
    stations = [(-.5, .06, .12, 0), (-.18, 1, 1, .12), (.2, .8, .85, -.1), (.5, .06, .12, 0)]
    rings = []
    for f, ds, ws, kink in stations:
        c = p+t*f*L+side*kink*w
        dd, ww = max(dep*ds, .02), w*ws
        W = ww*(dd+.4)/dd
        rings.append([c+side*W+n*.4, c-side*W+n*.4, c-n*dd])
    geo = Geo()
    geo.loft(rings)
    # Open, the stiffener it crosses is creased with it; faired, the
    # stiffener was renewed and only the door skin keeps the line.
    d.cut(geo, [ob.name] if status == 'faded' else None, mat)


# ---- the collar patch -------------------------------------------------------
def collar_patch(d, scar, site, r):
    """The rough patch under the sponson's armour collar canon puts there at
    arrival: an irregular salvaged steel plate, heavy uneven welds."""
    p, n, u, v, _ = site
    rng = random.Random(scar.id+' collar')
    angles = sorted(TAU*(k+rng.uniform(-.25, .25))/7 for k in range(7))
    ring = [d.snap(p+u*math.cos(a)*r*1.45*rng.uniform(.85, 1.12)
                   +v*math.sin(a)*r*1.2*rng.uniform(.85, 1.12), n, .06) for a in angles]
    d.part(scar, 'collar salvage patch', d.salvage[1], .03).flat(ring, n, .14)
    beads = d.part(scar, 'weld bead', d.weld, .01)
    for i in range(len(ring)):
        beads.bead(ring[i], ring[(i+1) % len(ring)], n, .15)


def kind_collar(d, scar, status):
    """Sponson cowl strike. open: lipped basin. patched: lips dressed, the
    rough collar patch. replaced: 'the forward collar sorted out' - a proper
    plate. faded: that plate repainted."""
    if status == 'patched':
        kind_basin(d, scar, status)   # patch='none': the cut, lips dressed flat
        site = d.site(scar, scar.params['origin'], scar.params['target'])
        if site:
            collar_patch(d, scar, site, scar.params['r'])
    else:
        kind_basin(d, scar, status)


# ---- the bow tether ---------------------------------------------------------
def kind_tether(d, scar, status):
    """The bow padeye the hulk was moored by, on the windward flank.
    open/patched: padeye, the parted tether's stub still shackled on, a bare
    chafe band round it (nobody aboard needed it gone for the flight).
    faded: padeye cut off and ground flush; the chafe band repainted, its
    outline and the padeye's footprint still read under the paint."""
    P = scar.params
    site = d.site(scar, P['origin'], P['target'])
    if site is None:
        return
    p, n, _, _, ob = site
    u = V((1, 0, 0))
    v = n.cross(u).normalized()
    u = v.cross(n).normalized()
    ring = [d.snap(p+u*sx*1.5+v*sy*1.1, n, .02) for sx, sy in ((-1,-1),(1,-1),(1,1),(-1,1))]
    if status in ('open', 'patched'):
        d.part(scar, 'chafe band', d.salvage[1], .01).flat(ring, n, .03)
        eye = d.part(scar, 'padeye', d.steel, .03)
        eye.obox(p+n*.18, u, v, n, .7, .5, .14)
        for sy in (-1, 1):
            eye.obox(p+n*.65+v*sy*.2, u, v, n, .4, .06, .45)
        eye.disc(p+n*.85, v, .13, .7, 8)
        end = p+n*2.2+u*.5-v*.3
        rope = d.part(scar, 'frayed tether stub', d.cable, .01)
        rope.tube([p+n*.85, p+n*1.5+u*.3, end], .22, 8)
        for du, dv in ((.35, .2), (-.1, .4), (.25, -.35)):
            rope.tube([end, end+n*.5+u*du+v*dv], .07, 5)
    elif status == 'faded':
        paint = d.repaint(d.base_mat(ob))
        d.part(scar, 'repainted chafe band', paint, .01).flat(ring, n, .02)
        stump = d.part(scar, 'ground padeye stump', paint, .01)
        stump.obox(p+n*.08, u, v, n, .7, .5, .06)


# ---- the bowed starboard bay jamb -------------------------------------------
def jamb_frame(d):
    jamb = d.find('Bay jamb', (26, -23), -1)
    if jamb is None:
        return None
    lo, hi = d.box(jamb)
    return jamb, lo, hi


def kind_jamb(d, scar, status):
    """The sealed starboard bay's forward frame jamb, bowed outboard by the
    knock that jammed the door. The bowed span between the coamings is cut out
    of the base jamb and stands as its own part.
    open: bowed, flanges crumpled at the apex. patched: shored with a length
    of cargo rail clamped to both coamings and wedged at the bow.
    faded: the bowed span cut out and a straight section welded in, painted
    to match; the weld seams at both ends still read."""
    got = jamb_frame(d)
    if got is None:
        return
    jamb, lo, hi = got
    z0, z1 = RIB_SPAN[0][0], RIB_SPAN[-1][0]
    xf = lo.x
    cutter = Geo()
    X, Y, Z = V((1, 0, 0)), V((0, 1, 0)), V((0, 0, 1))
    cutter.obox(V(((xf-.2+BUTTRESS_FACE_X+.1)/2, (lo.y+hi.y)/2, (z0+z1)/2)), X, Y, Z,
                (BUTTRESS_FACE_X+.1-(xf-.2))/2, (hi.y-lo.y)/2+.2, (z1-z0)/2)
    d.cut(cutter, [jamb.name])
    y_in, y_out = hi.y, lo.y       # inboard and outboard faces (starboard: -y is out)
    x1 = BUTTRESS_FACE_X-.01
    def section(z, bow, grow=.02):
        return [V((xf-grow, y_out-bow-grow, z)), V((x1, y_out-bow-grow, z)),
                V((x1, y_in-bow+grow, z)), V((xf-grow, y_in-bow+grow, z))]
    if status in ('open', 'patched'):
        mat = d.torn(d.base_mat(jamb))
        span = d.part(scar, 'bowed jamb span', mat, .08)
        span.loft([section(z, bow) for z, bow in RIB_SPAN])
        crumple = d.part(scar, 'crumpled flange', d.torn(d.base_mat(jamb)), .02)
        apex_z, apex = max(RIB_SPAN, key=lambda s: s[1])
        for dz, side in ((-.6, 1), (.3, -1)):
            x = xf if side > 0 else x1
            base = V((x, y_out-apex-.02, apex_z+dz))
            tri = [base+V((0, 0, -.7)), base+V((0, 0, .6)), base+V((-side*.45, -.35, 0))]
            crumple.loft([[q+V((side*.12, 0, 0)) for q in tri], tri])
        if status == 'patched':
            shore(d, scar, lo, hi, apex)
    elif status == 'faded':
        paint = d.repaint(d.base_mat(jamb))
        span = d.part(scar, 'replacement jamb section', paint, .08)
        span.loft([section(z0, 0, .03), section(z1, 0, .03)])
        seam = d.part(scar, 'jamb weld seam', paint, .005)
        for z in (z0, z1):
            ring = section(z, 0, .05)
            for a, b in zip(ring, ring[1:]+ring[:1]):
                seam.bead(a, b, Z if z == z0 else -Z, .07)


def shore(d, scar, lo, hi, apex):
    """A length of cargo rail outboard of the bowed jamb, clamped to the
    coamings at both ends and wedged against the bow."""
    out = V((0, -1, 0))
    rail_y = lo.y-apex-.22-.15
    x = (lo.x+BUTTRESS_FACE_X)/2
    za, zb = -5.6, 4.7
    a, b = V((x, rail_y, za)), V((x, rail_y, zb))
    rail = d.part(scar, 'cargo rail shore', d.rail, .05)
    rail.bar(a, b, out, .7, .3)
    slots = d.part(scar, 'cargo rail tie-down slot', d.dark, 0)
    for k in range(int((b-a).length/.9)):
        slots.obox(a+V((0, 0, .45+k*.9))+out*.15, V((0, 0, 1)), V((1, 0, 0)), out, .16, .09, .02)
    wedges = d.part(scar, 'shore wedge', d.salvage[1], .02)
    for z, bow in RIB_SPAN[1:-1]:
        face = lo.y-bow
        gap = face-(rail_y+.15)
        if gap > .08:
            wedges.obox(V((x, (face+rail_y+.15)/2, z)), V((1, 0, 0)), V((0, 1, 0)), V((0, 0, 1)), .3, gap/2, .25)
    clamps = d.part(scar, 'rail clamp bracket', d.steel, .04)
    bolts = d.part(scar, 'bolt heads', d.steel, .005)
    for z in (-5.5, 4.6):      # the coamings
        face = lo.y
        clamps.obox(V((x, (face+rail_y)/2, z)), V((1, 0, 0)), V((0, 1, 0)), V((0, 0, 1)),
                    .5, abs(rail_y-face)/2+.15, .22)
        for dx in (-.3, .3):
            bolts.disc((x+dx, rail_y-.17, z), out, .08, .06, 6)


# ---- the jammed starboard bay door ------------------------------------------
def dog_sites(d):
    door = d.find('Starboard bay sealed sectional door', None)
    if door is None:
        return None
    lo, hi = d.box(door)
    yf = lo.y-.4    # proud of the door stiffeners
    sites = []
    for z in (lo.z+1.4, (lo.z+hi.z)/2, hi.z-1.4):
        for x in (lo.x+.5, hi.x-.5):
            sites.append((x, z, .9, .4))
    for x in (7.0, 14.0, 21.0):
        sites.append((x, lo.z+.55, .4, .8))
    return yf, sites


def kind_door(d, scar, status):
    """The starboard bay door, jammed by the knock that bowed its jamb.
    open: jammed, nothing added (the strike row shows the dent).
    patched: welded shut with dog plates to the frame and sill.
    faded: the door freed, the dogs cut off and the stubs ground near flush
    and painted over."""
    got = dog_sites(d)
    if got is None:
        return
    yf, sites = got
    out = V((0, -1, 0))
    X, Y, Z = V((1, 0, 0)), V((0, 1, 0)), V((0, 0, 1))
    if status == 'patched':
        dogs = d.part(scar, 'welded dog plate', d.salvage[1], .02)
        beads = d.part(scar, 'weld bead', d.weld, .01)
        for x, z, hx, hz in sites:
            dogs.obox(V((x, yf, z)), X, Y, Z, hx, .08, hz)
            for sx in (-1, 1):
                beads.bead(V((x+sx*hx, yf-.08, z-hz)), V((x+sx*hx, yf-.08, z+hz)), out, .07)
    elif status == 'faded':
        door = d.find('Starboard bay sealed sectional door', None)
        paint = d.repaint(d.base_mat(door))
        stubs = d.part(scar, 'ground dog stub', paint, .01)
        for x, z, hx, hz in sites:
            for sx in (-1, 1):
                stubs.obox(V((x+sx*hx, yf+.37, z)), X, Y, Z, .12, .05, hz*.9)   # ground near flush on the door face


KINDS = {'fb-cheek': kind_cheek, 'fb-crease': kind_crease, 'fb-basin': basin, 'fb-dent': kind_dent, 'fb-spall': spall, 'fb-collar': kind_collar,
         'fb-tether': kind_tether, 'fb-jamb': kind_jamb, 'fb-door': kind_door}

SCARS = [
    # ---- windward basins: lipped, fast strikes ----
    Scar('fb-port-sponson-cowl-strike', 'fb-collar', 'open patched replaced faded',
         'Continuity scar 1: canon puts a rough patch under the collar at arrival; '
         'the refit sorted the forward collar out with a proper plate.',
         origin=(33,62,7.6), target=(33,30,7.4), r=2.2, depth=1.0, elong=1.25, shards=8,
         patch='none'),
    Scar('fb-port-bow-cheek-strike', 'fb-cheek', 'open patched patched faded',
         'On the cheek facet above the sponson, over occupied space: a welded doubler for the '
         'flight; untouched by the refit; re-plated in the restoration. Sized to the facet.',
         origin=(56,52,16), target=(44.8,21.5,12.75), r=1.0, depth=.6, elong=1.3, shards=7),
    Scar('fb-port-bow-flank-strike', 'fb-basin', 'open open open faded',
         'Not needed for the flight; re-plated only in the restoration.',
         origin=(60,44,3), target=(51,21,3), r=2.2, depth=1.0, elong=1.2, shards=7,
         restore='panel', panel=1.15),
    Scar('fb-bow-glacis-strike', 'fb-basin', 'open open open faded',
         'Not needed for the flight; re-plated only in the restoration.',
         origin=(76,-3,32), target=(54,-3,10), r=2.2, depth=1.0, elong=1.35, shards=7,
         restore='panel'),
    # ---- lee dents: slow knocks, no lip; painted over when restored ----
    Scar('fb-stbd-forebody-deck-dent', 'fb-dent', 'open open open faded',
         'Lee knock; cosmetic, painted over.',
         origin=(25,-17.5,60), target=(25.6,-17.2,0), r=1.8, depth=.45, elong=1.1),
    Scar('fb-forward-deck-dent', 'fb-dent', 'open open open faded',
         'Lee knock; cosmetic, painted over.',
         origin=(46,-6,60), target=(46.5,-6.5,0), r=1.6, depth=.4, elong=1.15),
    Scar('fb-stbd-flank-dent', 'fb-dent', 'open open open faded',
         'Lee knock; cosmetic, painted over.',
         origin=(43,-62,3.5), target=(43,-30,3.5), r=1.5, depth=.45, elong=1.3),
    Scar('fb-stbd-bay-door-strike', 'fb-crease', 'open open open faded',
         'The slow knock that bowed the jamb and jammed the door: a crease, not a hole; '
         'faired and painted once the door was freed.',
         origin=(23,-60,-.5), target=(23,-20,-.5), dir=(.4,0,.92), length=3.8, depth=.26, w=.75),
    # ---- small strike clusters ----
    Scar('fb-port-forward-flank-cluster', 'cluster', 'open open open faded',
         'Continuity scar 1: small strikes round the sponson; left open, painted over when restored.',
         cast='port', centre=(37,5), spread=(8,3.5), count=12, radii=(.35,.8), zone=NO_ZONE),
    Scar('fb-port-bow-deck-cluster', 'cluster', 'open open open faded',
         'Windward deck; outside the pressure hull, left open.',
         cast='down', centre=(41,19), spread=(5,3), count=7, radii=(.3,.7), zone=NO_ZONE),
    Scar('fb-stbd-bow-deck-cluster', 'cluster', 'open patched patched faded',
         'Only strikes over the command deck got pressure patches.',
         cast='down', centre=(28,-11), spread=(7,5), count=3, radii=(.25,.5), zone=PRESSURE_ZONE),
    # ---- spalls: outer armour torn to backing ----
    Scar('fb-stbd-flank-module-tear', 'fb-spall', 'open open open faded',
         'Armour only, not pressure: left; re-plated when restored.',
         origin=(29,-60,5.5), target=(29,-30,5.5), r=1.8, depth=.7, petals=3, restore='panel', panel_r=1.05),
    Scar('fb-port-citadel-wall-spall', 'fb-spall', 'open open open faded',
         'Armour only: left; re-plated when restored.',
         origin=(21,50,12.4), target=(21,20,12.4), r=1.4, depth=.55, petals=2, restore='panel'),
    Scar('fb-stbd-bow-cheek-spall', 'fb-spall', 'open open open faded',
         'Armour only, on the chine: left; built up with weld and dressed, painted over when restored '
         '(a plate cannot lie flat across the chine).',
         origin=(54,-32,15), target=(47,-18,12), r=1.6, depth=.6, petals=3),
    Scar('fb-port-nose-block-spall', 'fb-spall', 'open open open faded',
         'Armour only: left; re-plated when restored.',
         origin=(68,20,8), target=(57,4,6), r=1.4, depth=.55, petals=2, restore='panel'),
    # ---- gouges ----
    Scar('fb-port-sponson-scrape', 'gouge', 'open open open faded',
         'A rock rolled along the sponson; cosmetic, painted over.',
         start=(29.5,62,-1.2), end=(47,62,-1.8), cast_dir=(0,-1,0), w=.6, depth=.4),
    Scar('fb-port-glacis-gouge', 'gouge', 'open open open faded',
         'Glancing contact across plate seams; painted over.',
         start=(44,4,60), end=(53,9.5,60), cast_dir=(0,0,-1), w=.55, depth=.35),
    # ---- deck armour plates ----
    Scar('fb-port-deck-plate-fwd-torn', 'plate', 'open open open faded',
         'Torn by a strike; deck armour, not flight-critical; replaced when restored.',
         object='Forebody replaceable armor', centre=(32.5,16.75), how='torn', keep=(-1,1)),
    Scar('fb-port-deck-plate-aft-torn', 'plate', 'open open open faded',
         'Torn by a strike; replaced when restored.',
         object='Forebody replaceable armor', centre=(24.5,16.75), how='torn', keep=(1,1)),
    Scar('fb-stbd-deck-plate-unbolted', 'plate', 'open open open faded',
         'Unbolted by salvagers; replaced when restored.',
         object='Forebody replaceable armor', centre=(32.5,-10.25), how='unbolted'),
    Scar('fb-port-deck-plate-buckle', 'buckle', 'open open open faded',
         'Slow heavy contact; left buckled, replaced when restored.',
         object='Forebody replaceable armor', centre=(24.5,10.25), fold=.42),
    # ---- mooring and the starboard bay ----
    Scar('fb-bow-tether-padeye', 'fb-tether', 'open open open faded',
         'Where the hulk was moored; nobody needed it gone until the restoration ground it flush.',
         origin=(39.5,62,-2.2), target=(39.5,30,-2.2)),
    Scar('fb-stbd-bay-jamb', 'fb-jamb', 'open patched patched faded',
         'Bowed by the bay-door knock; shored for the flight; a new section welded in when restored.'),
    Scar('fb-stbd-bay-door', 'fb-door', 'open patched patched faded',
         'Jammed; welded shut for pressure and thrust loads; freed when the jamb was rebuilt.'),
]
