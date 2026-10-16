"""Scars: spine area. Rows and kinds owned by this module (see sw_damage).

The dorsal spine, the command deck, the raised access trunks, the sensor
mast, the four turrets and the waist service runs.

The spine and command deck are the occupied core of the ship. In the escape
the crew sealed it: a welded doubler over the one heavy strike on the waist
housing and bolted pressure patches on every small strike over it, nothing
elsewhere. The refit (the berthing/command conversion) works exactly here, so
it is where patches become proper plates; strikes outside the occupied zone
stay as they were until the restoration paints them over.
"""
import math
import random
from mathutils import Vector
from sw_damage import Scar, Geo, basis, TAU, kind_cluster, cluster_sites, in_zone

V = Vector
X, Y, Z = V((1,0,0)), V((0,1,0)), V((0,0,1))

# Occupied space the crew sealed: every strike over the spine and command deck
# (x0, x1, y0, y1, z above). Cluster sites inside it are pressure-patched in
# patched and plated in refit.
PRESSURE_ZONE = (-6, 32, -8.6, 8.6, 14)

# Turret parts kept when the gun houses were stripped: the barbette stays
# bolted down, so all four slots remain.
TURRET_KEEP = ('barbette foot', 'armoured collar', 'traverse gap')
# Gun-house parts that carry the other owner's paint on the two mismatched mounts.
TURRET_HOUSE = ('gun house', 'rear bustle', 'mantlet', 'roof hatch', 'optics blister')
# Mast parts lost when it sheared (all reinstalled with the new mast in refit).
MAST_LOST = ('Mast crosstree', 'Scanner turntable', 'Search radar scanner bar',
             'Masthead light', 'Crosstree signal light')
MAST_NEW = ('Mast crosstree', 'Scanner turntable', 'Search radar scanner bar')


def box_of(d, ob):
    lo, hi = d.box(ob)
    return lo, hi, (lo+hi)/2


# ---- sealed strikes over the spine -------------------------------------------
def sealed_patch(d, key, site, r):
    """A bolted pressure patch brake-bent to the skin, a borrowed hab-door
    gasket squeezed out under its edge. Unlike a flat plate on four snapped
    corners it stays a plate where the roof chamfers away."""
    p, n, u, v, _ = site
    skin = d.skin
    def clear(h):
        # Nothing standing proud of the roof (radome, pedestal, hatch) under
        # the patch's edge: a crew cuts the patch to fit beside it.
        for k in range(12):
            a = TAU*k/12
            q = p+(u*math.cos(a)+v*math.sin(a))*(h+.12)*1.3
            hit = d.cast(q+n*6, q-n*6)
            if hit and hit[2].name not in skin and (hit[0]-p).dot(n) > .25:
                return False
        return True
    half = r*1.4+.25
    for f in (1., .85, .7, .55):
        if clear(half*f):
            break
    half *= f
    mat = d.salvage[sum(map(ord, key)) % 3]
    d.grid_plate(key, 'seal gasket', p, n, u, v, half+.12, half+.12, d.rubber, cut=.08, lift=.03,
                 thick=.06, k=2, only=d.skin, weld=False)
    rim, n = d.grid_plate(key, 'pressure patch', p, n, u, v, half, half, mat, cut=.08, lift=.09,
                          thick=.09, k=2, only=d.skin, weld=False)
    studs = d.part(key, 'bolt heads', d.steel, .005)
    centre = sum(rim, Vector())/len(rim)
    for q0, q1 in zip(rim, rim[1:]+rim[:1]):
        steps = max(2, round((q1-q0).length/.45))
        for i in range(steps):
            q = q0.lerp(q1, (i+.5)/steps).lerp(centre, .12)
            studs.disc(q+n*.11, n, .05, .05, 6)


def kind_sealed_cluster(d, scar, status):
    """A strike cluster over the occupied spine: as the generic cluster, but
    patched strikes inside the zone get a pressure patch that follows the
    roof's chamfers (see sealed_patch)."""
    if status != 'patched':
        return kind_cluster(d, scar, status)
    zone = scar.params.get('zone')
    for key, site, r, depth, el in cluster_sites(d, scar):
        d.basin_cut(key, site, r, depth, el, 6)
        if in_zone(zone, site.p):
            sealed_patch(d, key, site, r)


# ---- turrets ---------------------------------------------------------------
def kind_turret(d, scar, status):
    """A point-defence mount.
    params: index (1..4), mismatched (gun house came in another owner's primer).
    open: gun house stripped; barbette foot, collar and traverse gap remain
    with a bare stud ring on the collar. patched: the gantry mount carried
    aboard and bolted back on a bare adapter ring with fresh bolts.
    replaced: not used. faded: adapter ring and any mismatched house
    repainted to a near match."""
    i = scar.params['index']
    collar = d.find(f'Turret {i} armoured collar', None)
    if collar is None:
        return
    lo, hi, c = box_of(d, collar)
    R, top = (hi.x-lo.x)/2, hi.z
    if status == 'open':
        for ob in d.named(f'Turret {i} '):
            if not any(k in ob.name for k in TURRET_KEEP):
                d.hide(ob, scar)
        studs = d.part(scar, 'bare stud', d.steel, .005)
        for k in range(16):
            a = TAU*(k+.5)/16
            studs.disc((c.x+(R-.25)*math.cos(a), c.y+(R-.25)*math.sin(a), top+.12), Z, .09, .24, 6)
        return
    faded = status == 'faded'
    ring_mat = d.repaint(d.base_mat(collar)) if faded else d.salvage[1]
    d.part(scar, 'adapter ring', ring_mat, .02).disc((c.x, c.y, top+.07), Z, R*.97, .14, 24)
    bolts = d.part(scar, 'adapter bolts', ring_mat if faded else d.steel, .005)
    for k in range(16):
        a = TAU*(k+.5)/16
        bolts.disc((c.x+(R-.2)*math.cos(a), c.y+(R-.2)*math.sin(a), top+.19), Z, .08, .08, 6)
    if scar.params.get('mismatched'):
        for ob in d.named(f'Turret {i} '):
            if any(k in ob.name for k in TURRET_HOUSE):
                d.override(ob, d.repaint(d.base_mat(ob)) if faded else d.salvage[0], scar)


# ---- sensor mast -----------------------------------------------------------
def mast_frame(d):
    pylon = d.find('Mast pylon', None)
    if pylon is None:
        return None
    lo, hi, c = box_of(d, pylon)
    return pylon, c.x, lo.z, hi.z


def pylon_ring(mx, z, z0, z1, grow=0.):
    """The pylon's tapered section at height z (it runs .6x.55 to .25x.3)."""
    t = (z-z0)/(z1-z0)
    hx, hy = .6+(.25-.6)*t+grow, .55+(.3-.55)*t+grow
    return [V((mx+sx*hx, sy*hy, z)) for sx, sy in ((-1,-1),(1,-1),(1,1),(-1,1))]


def kind_mast(d, scar, status):
    """The roof sensor mast.
    open: sheared above the pedestal on a raking line; crosstree, scanner,
    masthead and crosstree lights gone, the starboard whip lost and the port
    whip bent over. patched: the same with a jury whip lashed to the stub and
    a salvaged nav sensor on a bracket, its lead run down to the roof.
    replaced: the ragged stub dressed square and a new flanged upper pylon
    fitted, crosstree and scanner reinstalled in shop primer, whips back.
    faded: the same repainted to a near match, the flange joint still there."""
    frame = mast_frame(d)
    if frame is None:
        return
    pylon, mx, z0, z1 = frame
    if status in ('open', 'patched'):
        geo = Geo()
        c = V((mx, 0, z0+1.0))
        n = V((.35, .2, 1)).normalized()
        u, v = basis(n)
        geo.obox(c+n*3, u, v, n, 4, 4, 3)
        d.cut(geo, [pylon.name], d.torn(d.base_mat(pylon)))
        # Torn tongues of the pylon's skin left standing on the low side of
        # the shear, bent outboard.
        tongues = d.part(scar, 'torn tongue', d.torn(d.base_mat(pylon)), .01)
        on_plane = lambda x, y: c.z-(n.x*(x-c.x)+n.y*(y-c.y))/n.z
        for face, along, t0, t1, lean in ((X, Y, -.2, .75, .9),):
            zf = on_plane(mx+.4, .35)
            ring = pylon_ring(mx, zf, z0, z1)
            hx, hy = ring[2].x-mx, ring[2].y
            half = hx if face is X else hy
            base = V((mx, 0, 0))+face*half
            a = base+along*(t0*(hy if face is X else hx))
            b = base+along*(t1*(hy if face is X else hx))
            a.z, b.z = on_plane(a.x, a.y)-.02, on_plane(b.x, b.y)-.02
            tip = a.lerp(b, .65)+Z*.4+face*lean*.45
            tri = [a, b, tip]
            tongues.loft([[q-face*.05 for q in tri], [q+face*.05 for q in tri]])
        for name in MAST_LOST:
            for ob in d.named(name):
                d.hide(ob, scar)
        whips = sorted(d.named('Whip antenna'), key=lambda o: box_of(d, o)[2].y)
        if whips:
            d.hide(whips[0], scar)  # starboard whip lost
        if len(whips) > 1:
            # Port whip bent over: the span above the kink cut out and the bent
            # span added in its place.
            w = whips[-1]
            wlo, whi, wc = box_of(d, w)
            kink = d.ground(wc.x+.35, wc.y)+.7
            cut = Geo()
            cut.obox(V((wc.x, wc.y, (kink+whi.z+.5)/2)), X, Y, Z, .3, .3, (whi.z+.5-kink)/2)
            d.cut(cut, [w.name])
            bent = d.part(scar, 'bent whip', d.torn(d.base_mat(w)), 0)
            p0 = V((wc.x, wc.y, kink-.05))
            p1 = p0+Z*.25
            lean = V((-.55, .35, .78)).normalized()
            p2 = p1+lean*.5
            droop = V((-.8, .45, -.15)).normalized()
            bent.tube([p0, p1, p2, p2+droop*1.2], .06, 6)
        if status == 'patched':
            whip = d.part(scar, 'jury whip', d.steel, .01)
            whip.disc((mx+.75, 0, z0+2.4), Z, .11, 4.2, 6)
            bands = d.part(scar, 'hose clamp lashing', d.salvage[1], .01)
            for z in (z0+.25, z0+.6):
                bands.disc((mx+.6, 0, z), Z, .26, .12, 8)
            bracket = d.part(scar, 'nav sensor bracket', d.steel, .02)
            bracket.obox(V((mx-.85, 0, z0+.45)), X, Y, Z, .3, .1, .08)
            bracket.obox(V((mx-1.1, 0, z0+.62)), X, Y, Z, .08, .1, .22)
            nav = d.part(scar, 'salvaged nav sensor', d.salvage[0], .03)
            nav.disc((mx-1.1, 0, z0+1.0), Z, .45, .32, 12)
            window = d.m('Scar salvaged sensor window', (.05, .08, .1), .2, .15)
            d.part(scar, 'nav sensor window', window, 0).disc((mx-1.1, 0, z0+1.18), Z, .3, .04, 12)
            lead = d.part(scar, 'nav sensor lead', d.cable, .01)
            roof = d.ground(mx-1.3, .5)
            lead.tube([V((mx-1.1, .35, z0+.9)), V((mx-1.25, .5, z0+.4)), V((mx-1.3, .5, roof+.05)),
                       V((mx-2.6, .5, d.ground(mx-2.6, .5)+.05))], .06, 6)
        return
    # replaced / faded: a new upper section on the dressed stub.
    zc = z0+.55
    geo = Geo()
    geo.obox(V((mx, 0, (zc+z1+1)/2)), X, Y, Z, 1.5, 1.5, (z1+1-zc)/2)
    d.cut(geo, [pylon.name], d.weld)
    replaced = status == 'replaced'
    mat = d.primer if replaced else d.repaint(d.base_mat(pylon))
    sec = d.part(scar, 'new pylon section', mat, .05)
    sec.loft([pylon_ring(mx, zc+.12, z0, z1), pylon_ring(mx, z1, z0, z1)])
    flange = d.part(scar, 'pylon flange', mat, .02)
    flange.loft([pylon_ring(mx, zc, z0, z1, .16), pylon_ring(mx, zc+.12, z0, z1, .16)])
    bolts = d.part(scar, 'flange bolts', d.fastener if replaced else mat, .005)
    ring = pylon_ring(mx, zc+.16, z0, z1, .08)
    for q0, q1 in zip(ring, ring[1:]+ring[:1]):
        for t in (.25, .75):
            bolts.disc(q0.lerp(q1, t), Z, .06, .08, 6)
    seam = d.part(scar, 'flange seam', d.sealant if replaced else mat, .005)
    rim = pylon_ring(mx, zc+.12, z0, z1, .01)
    for q0, q1 in zip(rim, rim[1:]+rim[:1]):
        seam.bead(q0, q1, Z, .04)
    for name in MAST_NEW:
        for ob in d.named(name):
            d.override(ob, d.primer if replaced else d.repaint(d.base_mat(ob)), scar)


# ---- access trunks ---------------------------------------------------------
def trunk_frame(d, scar):
    P = scar.params
    coaming = d.find(P['name']+' coaming', P['near'], P['sign'])
    if coaming is None:
        return None
    lo, hi, c = box_of(d, coaming)
    return c, max(hi.x-lo.x, hi.y-lo.y)/2, hi.z


def kind_trunk(d, scar, status):
    """A raised access trunk's hatch.
    params: name, sign, near (x, y), dogged.
    open: the hatch seal ring taken by salvagers; on the dogged trunk the
    hinge was cut off too. patched: a borrowed hab-door gasket squeezed into
    the seat; the dogged trunk under a bolted blank with two dog bars.
    replaced: proper seal (base ring back); the dogged trunk reopened as the
    berthing's second exit, on a new hinge in primer.
    faded: that hinge repainted."""
    P = scar.params
    frame = trunk_frame(d, scar)
    if frame is None:
        return
    c, r, top = frame
    name, sign, dogged = P['name'], P['sign'], P.get('dogged')
    if status in ('open', 'patched'):
        for ob in d.named(name+' seal ring', sign):
            d.hide(ob, scar)
        if dogged:
            for ob in d.named(name+' hinge', sign):
                d.hide(ob, scar)
    if status == 'patched':
        d.part(scar, 'borrowed seal gasket', d.rubber, .01).disc((c.x, c.y, top+.34), Z, r*.8, .11, 16)
        if dogged:
            blank = d.part(scar, 'blanking plate', d.salvage[0], .02)
            blank.disc((c.x, c.y, top+.7), Z, r*.93, .2, 12)
            bolts = d.part(scar, 'blank bolts', d.steel, .005)
            for k in range(10):
                a = TAU*(k+.5)/10
                bolts.disc((c.x+r*.8*math.cos(a), c.y+r*.8*math.sin(a), top+.84), Z, .08, .08, 6)
            for dy in (-.45, .45):
                blank.obox(V((c.x, c.y+dy, top+.88)), X, Y, Z, r*.8, .1, .08)
            # The blank's skirt clamps over the collar.
            for k in range(4):
                a = TAU*(k+.5)/4
                q = V((c.x+r*.9*math.cos(a), c.y+r*.9*math.sin(a), top+.45))
                blank.obox(q, V((math.cos(a), math.sin(a), 0)), V((-math.sin(a), math.cos(a), 0)), Z, .08, .25, .25)
    elif status in ('replaced', 'faded') and dogged:
        for ob in d.named(name+' hinge', sign):
            d.override(ob, d.primer if status == 'replaced' else d.repaint(d.base_mat(ob)), scar)


# ---- waist service runs ----------------------------------------------------
def kind_torch(d, scar, status):
    """A service run torch-cut by salvagers for its contents.
    params: object, centre, gap, r, sign.
    open: square cut, the conductors cut short, heat tint only either side of
    the cut (no power, no fire). patched: a split clamp sleeve over the gap.
    replaced: the tinted ends cut back and a new spool piece flanged in, shop
    primer. faded: that spool repainted, its flanges still standing proud."""
    P = scar.params
    x, y, z = P['centre']
    gap, r = P['gap'], P['r']
    ob = d.find(P['object'], (x, y), P['sign'])
    if ob is None:
        return
    if status in ('replaced', 'faded'):
        gap += P.get('cutback', 1.6)  # cut back past the heat-tinted ends
    geo = Geo()
    geo.obox((x, y, z), X, Y, Z, gap/2, r+.5, r+.5)
    d.cut(geo, [ob.name], d.torn(d.base_mat(ob)))
    if status in ('open', 'patched'):
        tint = d.part(scar, 'heat tint', d.scorch, .02)
        core = d.part(scar, 'cut conductor stub', d.dark, .01)
        copper = d.part(scar, 'cut conductor core', d.copper, .005)
        for sx in (-1, 1):
            end = x+sx*gap/2
            tint.disc((end+sx*.4, y, z), X, r+.03, .8, 16)
            for dy, dz in ((-.2,.15), (.22,0), (0,-.25)):
                q = V((end-sx*.12, y+dy*r/.7, z+dz*r/.7))
                core.disc(q, X, .14*r/.7, .3, 6)
                copper.disc(q-X*sx*.16, X, .07*r/.7, .04, 6)
    if status == 'patched':
        L = gap+1.0
        sleeve = d.part(scar, 'split clamp sleeve', d.salvage[1], .03)
        sleeve.disc((x, y, z), X, r+.14, L, 12)
        for dx in (-L/2+.12, L/2-.12):
            sleeve.disc((x+dx, y, z), X, r+.23, .2, 12)
        lugs = d.part(scar, 'sleeve clamp lug', d.steel, .01)
        for dx in (-L/3, 0, L/3):
            lugs.obox(V((x+dx, y, z+r+.2)), X, Y, Z, .18, .22, .1)
    elif status in ('replaced', 'faded'):
        replaced = status == 'replaced'
        mat = d.primer if replaced else d.repaint(d.base_mat(ob))
        d.part(scar, 'spool piece', mat, .02).disc((x, y, z), X, r, gap, 16)
        flanges = d.part(scar, 'spool flange', mat, .02)
        bolts = d.part(scar, 'flange bolts', d.fastener if replaced else mat, .005)
        u, v = Y, Z
        for sx in (-1, 1):
            end = x+sx*gap/2
            flanges.disc((end, y, z), X, r+.28, .3, 16)
            for k in range(8):
                a = TAU*(k+.5)/8
                q = V((end, y, z))+(u*math.cos(a)+v*math.sin(a))*(r+.17)
                bolts.disc(q, X, .06, .42, 6)
        if replaced:
            seam = d.part(scar, 'flange seam', d.sealant, .005)
            for sx in (-1, 1):
                seam.disc((x+sx*gap/2, y, z), X, r+.3, .06, 16)


KINDS = {'sealed_cluster': kind_sealed_cluster, 'turret': kind_turret, 'mast': kind_mast, 'trunk': kind_trunk, 'torch': kind_torch}

SCARS = [
    # The one heavy strike on the waist housing roof, windward of the walkway.
    Scar('sp-housing-strike', 'basin', 'open patched replaced faded',
         'Strike on the spine housing over occupied space: welded doubler in the escape, '
         'plated properly by the berthing conversion.',
         origin=(3.5,6,60), target=(3.6,5.6,0), r=1.6, depth=.7, elong=1.1, shards=6,
         patch='salvage'),
    Scar('sp-spine-cluster', 'sealed_cluster', 'open patched replaced faded',
         'Small strikes along the spine and command deck: pressure patches, then inserts.',
         cast='down', centre=(16,2), spread=(9,3), count=7, radii=(.25,.5), zone=PRESSURE_ZONE),
    Scar('sp-scattered', 'sealed_cluster', 'open patched replaced faded',
         'Scattered deck strikes; only those over the occupied spine are ever patched.',
         cast='down', centre=(8,8), spread=(44,18), count=12, radii=(.25,.5), zone=PRESSURE_ZONE),
    # Access trunk hatches. Seals were worth taking; the crew borrowed hab-door
    # seals to hold pressure, and dogged one shut whose hinge had been cut off.
    Scar('sp-trunk-fwd-port', 'trunk', 'open patched replaced gone',
         'Hatch seal taken; borrowed gasket; proper seal fitted in the conversion.',
         name='Forward access trunk', sign=1, near=(20.6,12.8)),
    Scar('sp-trunk-fwd-stbd', 'trunk', 'open patched replaced gone',
         'Hatch seal taken; borrowed gasket; proper seal fitted in the conversion.',
         name='Forward access trunk', sign=-1, near=(20.6,-12.8)),
    Scar('sp-trunk-aft-port', 'trunk', 'open patched replaced gone',
         'Hatch seal taken; borrowed gasket; proper seal fitted in the conversion.',
         name='Aft access trunk', sign=1, near=(-3.8,12.6)),
    Scar('sp-trunk-aft-stbd', 'trunk', 'open patched replaced faded',
         'Seal and hinge taken; dogged shut under a bolted blank; reopened on a new hinge '
         'as the berthing\'s second exit.',
         name='Aft access trunk', sign=-1, near=(-3.8,-12.6), dogged=True),
    Scar('sp-mast', 'mast', 'open patched replaced faded',
         'Mast sheared above the pedestal; jury whip and salvaged nav sensor for the flight; '
         'the command conversion fits a proper flanged upper section and scanner.'),
    # Point defence: gun houses stripped, gantry mounts bolted back for the escape.
    Scar('sp-turret-1', 'turret', 'open patched patched faded',
         'Gun house stripped; reinstalled in another owner\'s primer; repainted to near match.',
         index=1, mismatched=True),
    Scar('sp-turret-2', 'turret', 'open patched patched faded',
         'Gun house stripped; reinstalled on an adapter ring; ring painted over.', index=2),
    Scar('sp-turret-3', 'turret', 'open patched patched faded',
         'Gun house stripped; reinstalled in another owner\'s primer; repainted to near match.',
         index=3, mismatched=True),
    Scar('sp-turret-4', 'turret', 'open patched patched faded',
         'Gun house stripped; reinstalled on an adapter ring; ring painted over.', index=4),
    # Salvagers torch-cut two waist runs for their contents.
    Scar('sp-waist-line-cut', 'torch', 'open patched patched faded',
         'Protected waist line cut for its contents: sleeved for the flight, spooled in restoration.',
         object='Protected waist service line', centre=(9.5,24.2,7.2), gap=2.4, r=.7, sign=1),
    Scar('sp-waist-trunk-cut', 'torch', 'open patched replaced faded',
         'Exposed waist trunk feeds the spine: the command conversion re-runs it with a new spool.',
         object='Exposed waist service trunk', centre=(4.0,10.9,11.5), gap=1.4, r=.7, sign=1, cutback=.6),
]
