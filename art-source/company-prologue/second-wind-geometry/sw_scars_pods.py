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
from sw_engines import ENGINES, BELL_R, FIN_X

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
def kind_crushed_can(d, scar, status):
    """The port outer can, crushed from outboard against the anchor rock.
    Only the can deforms (shape key); the nozzle ring, bell and lip stay round.
    params: origin, target, r, depth, elong, crush_r, crush_d, engine.
    open/patched: the crush and its basin. replaced/faded: a replacement can,
    i.e. the can as built, only fitted (handled as 'gone' by the ladder)."""
    if status not in ('open', 'patched'):
        return
    P = scar.params
    site = d.site(scar, P['origin'], P['target'])
    if site is None:
        return
    d.basin_cut(scar, site, P['r'], P['depth'], P.get('elong', 1.2), 8)
    x, y, _ = ENGINES[P['engine']]
    can = d.find('Thrust bay engine can', (x+5, y), 1 if y > 0 else -1)
    if can is None:
        return
    p, n = site.p, site.n
    R, depth = P['crush_r'], P['crush_d']
    face = x+.6  # can face ring: held so the shroud collar stays seated and round
    def crush(w):
        dist = (w-p).length
        if dist >= R or w.x < face+.3:
            return w
        return w-n*depth*(1-dist/R)
    d.shape(can, scar, crush)


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
         'Engine 1 can crushed against the anchor rock; nozzle ring stays round. A '
         'replacement can when the port pod is restored.',
         origin=(-38,60,5), target=(-38,33,2.5), r=2.4, depth=1.2, elong=1.2,
         crush_r=5.0, crush_d=1.7, engine=0),
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
