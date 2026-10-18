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
from mathutils import Matrix, Vector
from sw_turrets import MOUNTS
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
# Story. Point defence was worth money, so the salvagers lifted every gun house
# they could sell. Two they could not: the port (windward) mounts took drift
# strikes. Turret 1's barrel was bent and the blow slewed the house off its slot
# until the traverse ring seized, so the house will not lift off the collar;
# turret 3's house was holed through the roof and its barrel snapped at the
# chase. Both were left as scrap. The escape crew bolted the two good houses
# back on from the salvagers' gantry (turret 2's from another hull, in that
# owner's primer) and re-energised the mount bus for them, which put the feed
# torn inside turret 3's holed house live: reaching it means cutting the house
# open, so it was left. The conversion isolated it; the restoration fitted new
# houses on 1 and 3.

def turret_frame(d, i):
    """Mount i's local frame from the clean ship: centre, slot axes, scale,
    house base and gun-axis heights (sw_turrets builds everything above the
    traverse gap bow-facing and trains it to the slot angle)."""
    x, y, angle, s, heavy = MOUNTS[i-1]
    collar = d.find(f'Turret {i} armoured collar', None)
    if collar is None:
        return None
    lo, hi = d.box(collar)
    a = math.radians(angle)
    f, l = V((math.cos(a), math.sin(a), 0)), V((-math.sin(a), math.cos(a), 0))
    zb = hi.z+.2*s
    c = V((x, y, 0))
    return dict(collar=collar, c=c, f=f, l=l, s=s, heavy=heavy, zb=zb, gz=zb+.95*s,
                L=lambda px, py, z: c+f*px*s+l*py*s+Z*z)


def turret_part(d, i, label):
    """The base object 'Turret <i> <label>' exactly (not its namesakes)."""
    for ob in d.named(f'Turret {i} {label}'):
        if ob.name == f'Turret {i} {label}':
            return ob
    return None


def barrel_stations(heavy):
    """Local stations (house units) along a gun: breech, chase start,
    tube start, brake start, muzzle (sw_turrets.barrel)."""
    L = 1.25 if heavy else 1.0
    s0 = 3.0
    chase = s0+1.7
    tube = chase+2.6*L
    brake = tube+2.3*L
    return s0, chase, tube, brake, brake+1.0


def moved(geo, fn):
    """A Geo with every vertex mapped through fn (the jam's slew)."""
    out = Geo()
    out.add([fn(V(q)) for q in geo.v], geo.f)
    return out


def strike_basin(rng, p, n, u, v, r, depth, elong, sides=8):
    """A faceted strike basin like Damage.basin_cut, built for explicit targets."""
    def ring(rad, h, jit, shift):
        pts = []
        for k in range(sides):
            a = TAU*(k+rng.uniform(-jit, jit))/sides
            rr = rad*rng.uniform(1-jit*.7, 1+jit*.7)
            pts.append(p+shift+u*math.cos(a)*rr*elong+v*math.sin(a)*rr+n*h)
        return pts
    geo = Geo()
    down = u*r*.18
    geo.loft([ring(r*1.18, .55, .2, V((0, 0, 0))), ring(r*.66, -depth*.5, .25, down*.6),
              ring(r*.3, -depth, .3, down)])
    return geo


def torn_lips(rng, p, n, u, v, r, elong, count, inward):
    """Torn plate tongues round a strike: crushed outward (dent) or bent in
    along the strike (holed), faceted triangles of plate thickness."""
    geo = Geo()
    for k in range(count):
        a = TAU*(k+.5+rng.uniform(-.2, .2))/count
        span = TAU/count*rng.uniform(.5, .8)
        at = lambda rad, ang: p+u*math.cos(ang)*rad*elong+v*math.sin(ang)*rad
        if inward:
            tip = at(r*rng.uniform(.35, .55), a)-n*r*rng.uniform(.45, .8)
            tri = [at(r*.98, a-span/2)-n*.02, at(r*.98, a+span/2)-n*.02, tip]
        else:
            tip = at(r*rng.uniform(1.0, 1.15), a)+n*r*rng.uniform(.2, .35)
            tri = [at(r*1.05, a-span/2)-n*.03, at(r*1.05, a+span/2)-n*.03, tip]
        geo.loft([[q-n*.04 for q in tri], [q+n*.04 for q in tri]])
    return geo


def turret_jam(d, scar, i, T, deg, bend):
    """Turret i jammed `deg` off its slot (CCW from above) with its barrel bent
    at the chase/tube joint. Shape keys only: every part above the traverse gap
    slewed about the collar's vertical axis, the tube and brake first turned
    about the kink. Returns the slew, for parts and cuts laid on the house."""
    jam = Matrix.Rotation(math.radians(deg), 3, 'Z')
    c = T['c']
    slew = lambda w: c+jam @ (w-c)
    yaw, pitch = (math.radians(a) for a in bend)
    s0, chase, tube, brake, muzzle = barrel_stations(T['heavy'])
    K = T['L'](tube, 0, T['gz'])
    f = T['f']
    bent = (Matrix.Rotation(yaw, 3, 'Z') @ f)*math.cos(pitch)-Z*math.sin(pitch)
    turn = f.rotation_difference(bent).to_matrix()
    kink = lambda w: K+turn @ (w-K) if (w-K).dot(f) > .02 else w
    for ob in d.named(f'Turret {i} '):
        if any(k in ob.name for k in TURRET_KEEP):
            continue
        if any(k in ob.name for k in ('barrel tube', 'muzzle brake')):
            d.shape(ob, scar, lambda w: slew(kink(w)))
        else:
            d.shape(ob, scar, slew)
    s = T['s']
    # The joint buckled: the chase's skin crumpled round the kink, bulged
    # out on the inside of the bend and split open on the outside.
    rng = random.Random(scar.id+' kink')
    mid = (f+bent).normalized()
    inside = (bent-f).normalized()
    side = mid.cross(inside).normalized()
    ring = lambda off, r0: [K+mid*off+(inside*math.cos(TAU*k/7)+side*math.sin(TAU*k/7))*r0*s
                            *rng.uniform(.9, 1.12)+inside*.06*s for k in range(7)]
    crumple = Geo()
    crumple.loft([ring(-.22*s, .45), ring(0, .53), ring(.2*s, .43)])
    crumple.obox(K+inside*.4*s, mid, side, inside, .2*s, .24*s, .1*s)
    chase_ob = turret_part(d, i, 'gun barrel chase')
    d.part(scar, 'buckled barrel joint', d.torn(d.base_mat(chase_ob)), .02).add(moved(crumple, slew).v, crumple.f)
    split = Geo()
    split.obox(K-inside*.47*s, mid, side, inside, .05*s, .2*s, .05*s)
    d.part(scar, 'split barrel skin', d.dark, 0).add(moved(split, slew).v, split.f)
    return slew


def turret_house_strike(d, scar, i, T, origin, target, r, depth, holed, slew=None):
    """A strike on the gun house, cast on the clean house. A dent keeps its
    floor and crushes its lips out; a hole punches into the house's dark
    interior with the plate tongues bent in along the strike and two frame
    ribs showing. Returns the site (unslewed) or None."""
    house = turret_part(d, i, 'gun house')
    if house is None:
        return None
    hit = d.cast(T['L'](*origin), T['L'](*target), only={house.name})
    if hit is None:
        print('scar: no surface for', scar.id, 'house strike')
        return None
    p, n, _ = hit
    aim = (T['L'](*target)-T['L'](*origin)).normalized()
    track = aim-n*aim.dot(n)
    u = track.normalized() if track.length > .2 else basis(n)[0]
    v = n.cross(u)
    slew = slew or (lambda w: w)
    rng = random.Random(scar.id+' house')
    el = 1.2
    cut = strike_basin(rng, p, n, u, v, r, depth, el)
    d.cut(moved(cut, slew), [house.name], d.dark if holed else d.torn(d.base_mat(house)))
    lips = torn_lips(rng, p, n, u, v, r, el, 7 if holed else 6, holed)
    # A tongue whose root is off the house (past an edge) has nothing to tear
    # from: drop it rather than leave it in the air.
    kept = Geo()
    for k in range(0, len(lips.f), 5):
        verts = sorted({vi for face in lips.f[k:k+5] for vi in face})
        pts = [V(lips.v[vi]) for vi in verts]
        roots = pts[:2]
        hits = [d.cast(q+n*.6, q-n*.6, only={house.name}) for q in roots]
        if all(h and (h[0]-q).length < .12 for h, q in zip(hits, roots)):
            kept.add(pts, [tuple(verts.index(vi) for vi in face) for face in lips.f[k:k+5]])
    d.part(scar, 'torn house plate', d.torn(d.base_mat(house)), .01).add(moved(kept, slew).v, kept.f)
    if holed:
        ribs = Geo()
        for k in (-1, 1):
            q = p-n*(depth*.45)+u*k*r*.42
            ribs.bar(q-v*r*1.1, q+v*r*1.1, n, .14, .22)
        d.part(scar, 'exposed house frame', d.steel, .01).add(moved(ribs, slew).v, ribs.f)
        # A grazing strike: the roof plate torn up and peeled back over the
        # hole at its downrange rim, in two faceted bends.
        hinge = p+u*r*el*.92
        a, b = hinge-v*r*.95, hinge+v*r*.9
        up1 = (-u*math.cos(math.radians(38))+n*math.sin(math.radians(38))).normalized()
        up2 = (-u*math.cos(math.radians(100))+n*math.sin(math.radians(100))).normalized()
        a1, b1 = a+up1*r*.8+v*.1, b+up1*r*.7-v*.05
        tip = [a1+up2*r*.35+v*.15, a1.lerp(b1, .4)+up2*r*.6, b1+up2*r*.28-v*.1]
        flap = Geo()
        for ring in ([a, b, b1, a1], [a1, b1]+tip[::-1]):
            q = sum(ring, V((0, 0, 0)))/len(ring)
            nn = (ring[1]-ring[0]).cross(ring[-1]-ring[0]).normalized()
            flap.flat([x-nn*.04 for x in ring], nn, .08)
        d.part(scar, 'peeled roof plate', d.torn(d.base_mat(house)), .01).add(moved(flap, slew).v, flap.f)
    return p, n, u, v


def turret_snap(d, scar, i, T, at):
    """The barrel snapped at station `at` on the chase: the tube and brake
    gone (the outboard span was lost to the drift), the chase broken off on
    a jagged crown with a bright fracture face round its dark bore."""
    for label in ('gun barrel tube', 'gun muzzle brake', 'gun muzzle brake port'):
        d.hide(turret_part(d, i, label), scar)
    chase = turret_part(d, i, 'gun barrel chase')
    if chase is None:
        return
    s, f, l = T['s'], T['f'], T['l']
    axis_at = lambda px: T['L'](px, 0, T['gz'])
    fracture = d.m('Scar fracture bright steel', (.72, .71, .68), .55, .5)
    # A jagged crown: the break wanders up and down the chase round its
    # rim, its facets running in to the bore.
    rng = random.Random(scar.id+' snap')
    sides = 11
    up = Z-f*f.dot(Z)
    up.normalize()
    rim0, rim1 = [], []
    for k in range(sides):
        a = TAU*k/sides
        radial = l*math.cos(a)+up*math.sin(a)
        # An oblique break, its face turned up where the strike bent the
        # barrel down until the top tore, a ragged tongue left at each side
        # and the rim chipped.
        st = at+(-.7*math.sin(a)+(.55 if k in (1, 9) else 0)+rng.uniform(-.22, .22))*s
        rim0.append(axis_at(st)+radial*.75*s)
        rim1.append(axis_at(at+5*s)+radial*.75*s)
    geo = Geo()
    c0, c1 = axis_at(at+.02*s)-up*.08*s, axis_at(at+5*s)
    verts = [c0]+rim0+rim1+[c1]
    faces = [(0, 1+(k+1) % sides, 1+k) for k in range(sides)]
    faces += [(1+k, 1+(k+1) % sides, 1+sides+(k+1) % sides, 1+sides+k) for k in range(sides)]
    faces += [(2*sides+1, 1+sides+k, 1+sides+(k+1) % sides) for k in range(sides)]
    geo.add(verts, faces)
    d.cut(geo, [chase.name], fracture)
    bore = Geo()
    bore.disc(axis_at(at+.6), f, .15*s, 2.4*s, 10)
    d.cut(bore, [chase.name], d.dark)


def turret_feed(d, scar, i, T, site, state):
    """The mount's power feed, torn inside the holed house: two severed
    conductors hanging out of the hole over the port side, bright copper
    ends. Live once the crew re-energised the mount bus (patched); the
    conversion isolated it and booted the ends (refit)."""
    p, n, u, v = site
    l, s = T['l'], T['s']
    run = d.part(scar, 'torn power feed', d.cable, .01)
    cores = d.part(scar, 'torn feed conductor', d.copper, .005)
    ends = []
    for dv, drop, r in ((-.18, .55, .13), (.2, .85, .1)):
        q0 = p-n*.9+v*dv
        q1 = p+n*.2+v*dv+l*.1
        q2 = q1+l*.45*s+n*.15-Z*.15
        q3 = q2+l*.35*s-Z*drop*s
        run.tube([q0, q1, q2, q3], r, 6)
        tip = (q3-q2).normalized()
        cores.disc(q3+tip*.04, tip, r*.6, .14, 6)
        ends.append((q3+tip*.1, tip))
    if state == 'refit':
        boots = d.part(scar, 'isolation boot', d.rubber, .01)
        for q, tip in ends:
            boots.disc(q-tip*.05, tip, .17, .32, 8)
    q, tip = ends[0]
    d.anchor(scar, 'spark', q, (tip+l*.4).normalized(), sparking=(state == 'patched'), radius_m=.8)


def kind_turret(d, scar, status):
    """A point-defence mount.
    params: index (1..4), mismatched (gun house came in another owner's
    primer), wreck: None, or 'jammed' (barrel bent at the chase/tube joint by
    bend=(yaw, droop) degrees, house dented and slewed jam_deg off its slot
    until the traverse seized) or 'holed' (house holed through the roof,
    barrel snapped on the chase at snap_at, the mount's power feed torn
    inside the hole).
    open: a sound house stripped; barbette foot, collar and traverse gap remain
    with a bare stud ring on the collar. A wrecked house was left in place,
    damaged. patched: a sound house is the gantry mount carried aboard and
    bolted back on a bare adapter ring with fresh bolts; a wreck's ladder never
    uses it. replaced: not used. faded: a new or reinstalled house on the
    adapter ring, ring and any mismatched or new house repainted to a near
    match. Every collar is tagged jammed / jam_deg for this state so the
    export can set its pivot's rest angle (slot angle + jam_deg)."""
    i = scar.params['index']
    T = turret_frame(d, i)
    if T is None:
        return
    collar = T['collar']
    wreck = scar.params.get('wreck')
    jam = scar.params.get('jam_deg', 0.) if wreck == 'jammed' and status == 'open' else 0.
    d.tag(collar, 'jammed', bool(jam), scar)
    d.tag(collar, 'jam_deg', float(jam), scar)
    lo, hi, c = box_of(d, collar)
    R, top = (hi.x-lo.x)/2, hi.z
    if status == 'open' and wreck == 'jammed':
        slew = turret_jam(d, scar, i, T, jam, scar.params.get('bend', (16, 9)))
        turret_house_strike(d, scar, i, T, (.8, -3.2, T['zb']+7), (-.3, -1.1, T['zb']+2),
                            .8, .5, False, slew)
        return
    if status == 'open' and wreck == 'holed':
        site = turret_house_strike(d, scar, i, T, (-1.3, 5.5, T['zb']+5), (-1.4, 1.2, T['zb']+1.6),
                                   1.05, 1.3, True)
        turret_snap(d, scar, i, T, scar.params.get('snap_at', 5.9))
        # The same strike swept the optics blister off the roof.
        blister = turret_part(d, i, 'optics blister')
        if blister is not None:
            d.hide(blister, scar)
            d.hide(turret_part(d, i, 'optics lens'), scar)
        if site:
            turret_feed(d, scar, i, T, site, d.state)
        return
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
    if scar.params.get('mismatched') or (faded and wreck):
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
    # Point defence. The salvagers sold the two sound houses (2, 4); the two
    # windward mounts (1, 3) were wrecked by drift strikes and left as scrap.
    # The escape crew bolted the sound houses back on from the gantry; the
    # restoration fitted new houses on the wrecks.
    Scar('sp-turret-1', 'turret', 'open open open faded',
         'Drift strike bent the barrel at the chase and slewed the house 24 deg aft until the '
         'traverse seized, so it would not lift off: left as scrap, flown dead and jammed; '
         'new house on an adapter ring in restoration.',
         index=1, wreck='jammed', jam_deg=24., bend=(24, 14)),
    Scar('sp-turret-2', 'turret', 'open patched patched faded',
         'Gun house stripped; a gantry house from another hull bolted back in its owner\'s '
         'primer; repainted to near match.', index=2, mismatched=True),
    Scar('sp-turret-3', 'turret', 'open open open faded',
         'House holed through the roof and the barrel snapped on the chase: left as scrap. Its '
         'feed, torn inside the hole, went live when the crew powered the mount bus (sparks); '
         'isolated and booted in the conversion; new house in restoration.',
         index=3, wreck='holed', snap_at=5.9),
    Scar('sp-turret-4', 'turret', 'open patched patched faded',
         'Gun house stripped; its own house recovered from the gantry and reinstalled on an '
         'adapter ring; ring painted over.', index=4),
    # Salvagers torch-cut two waist runs for their contents.
    Scar('sp-waist-line-cut', 'torch', 'open patched patched faded',
         'Protected waist line cut for its contents: sleeved for the flight, spooled in restoration.',
         object='Protected waist service line', centre=(9.5,24.2,7.2), gap=2.4, r=.7, sign=1),
    Scar('sp-waist-trunk-cut', 'torch', 'open patched replaced faded',
         'Exposed waist trunk feeds the spine: the command conversion re-runs it with a new spool.',
         object='Exposed waist service trunk', centre=(4.0,10.9,11.5), gap=1.4, r=.7, sign=1, cutback=.6),
]
