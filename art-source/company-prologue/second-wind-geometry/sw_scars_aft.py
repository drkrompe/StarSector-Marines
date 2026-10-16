"""Scars: aft area. Rows and kinds owned by this module (see sw_damage).

The centre aft deck between the pods and the stern notch: the engine-room
roof, its hoist rails and plant well, and the stern cradle machinery. The
deck faced the drift dorsally, so its fast strikes are lipped; the starboard
(lee) half lost a plate to salvagers rather than to rock. The external power
run from the starboard pod's jury coil to the engine room is the one piece
of flight work on this deck.
"""
import math
from mathutils import Vector
from sw_damage import Scar, TAU

V = Vector

# The run's plan (x, y); heights are cast onto the clean ship. It starts by
# the jury coil's junction box on the starboard housing deck (sw_scars_pods)
# and ends at the engine-room access hatch.
CABLE_PLAN = [(-29.6,-26.8),(-27.4,-22),(-24,-17),(-20.5,-13.5),(-17.5,-11.6),(-14,-8.2),
              (-11.6,-4.7),(-11.0,-3.5)]
HATCH = 'Engine room access hatch'


def cable_paths(d, plan, offsets, step=.3, span=3, lift=.19):
    """A laid cable pair's paths at each lateral offset from the plan line,
    sampled densely and draped on the clean ship. The pair shares one height
    profile (the higher surface under either), taken as the upper envelope
    over +-span stations and eased, so the run bridges small gaps and pipe
    valleys and drops down a wall rather than cutting through air or
    snaking into slots. Index i is the same station on every path."""
    stations = []   # (point, tangent)
    for (ax, ay), (bx, by) in zip(plan, plan[1:]):
        a, b = V((ax, ay, 0)), V((bx, by, 0))
        t = (b-a).normalized()
        k = max(1, math.ceil((b-a).length/step))
        stations += [(a.lerp(b, i/k), t) for i in range(k)]
    stations.append((V((plan[-1][0], plan[-1][1], 0)), stations[-1][1]))
    flat = {off: [q+V((-t.y, t.x, 0))*off for q, t in stations] for off in offsets}
    raw = [max(d.ground(flat[off][i].x, flat[off][i].y) for off in offsets) for i in range(len(stations))]
    n = len(raw)
    env = [max(raw[max(0, i-span):i+span+1]) for i in range(n)]
    eased = [sum(env[max(0, i-1):i+2])/len(env[max(0, i-1):i+2]) for i in range(n)]
    z = [max(e, r)+lift for e, r in zip(eased, raw)]
    paths = {off: [V((q.x, q.y, z[i])) for i, q in enumerate(flat[off])] for off in offsets}
    return paths, raw


PAIR = .17       # each cable's offset from the bundle line
CABLE_R = .15    # the pair nearly touching


def frame(t, side, up):
    """Orthonormal (t, side, up) with up kept on the requested side."""
    side = (side-t*side.dot(t)).normalized()
    u = t.cross(side).normalized()
    if u.dot(up) < 0:
        u = -u
    return t, side, u


def saddle(d, scar, c, t, side, up, feet=True):
    """A saddle clip over the cable bundle: a strap across the top, a leg
    down each side and a bolted foot outboard of each leg."""
    t, side, up = frame(t, side, up)
    strap = d.part(scar, 'saddle clip', d.steel, .01)
    top = PAIR+CABLE_R+.05
    hr = CABLE_R+.03
    strap.obox(c+up*(hr+.03), t, side, up, .08, top, .03)
    for sx in (-1, 1):
        strap.obox(c+side*sx*top+up*.015, t, side, up, .08, .03, hr+.015)
        if feet:
            strap.obox(c+side*sx*(top+.13)-up*(hr-.02), t, side, up, .08, .14, .025)
            d.part(scar, 'clip bolt', d.fastener, .005).disc(
                c+side*sx*(top+.15)-up*(hr-.06), up, .045, .05, 6)


def standoff(d, scar, c, t, side, up, base, normal):
    """A stand-off bracket: a saddle on a cradle plate, carried on an arm
    from a bolted base plate on the surface at `base`."""
    t, side, up = frame(t, side, up)
    saddle(d, scar, c, t, side, up, feet=False)
    arm = d.part(scar, 'stand-off bracket', d.salvage[1], .01)
    hr = CABLE_R+.03
    cradle = c-up*(hr+.03)
    arm.obox(cradle, t, side, up, .1, PAIR+CABLE_R+.1, .03)
    root = V(base)+V(normal)*.05
    if (cradle-root).length > .1:
        arm.bar(cradle-up*.03, root, t if abs(t.dot((cradle-root).normalized())) < .9 else side, .14, .1)
    n = V(normal)
    u, v = (side-n*side.dot(n)), None
    if u.length < .1:
        u = t-n*t.dot(n)
    u.normalize()
    v = n.cross(u)
    arm.obox(V(base)+n*.03, u, v, n, .22, .22, .03)
    bolts = d.part(scar, 'clip bolt', d.fastener, .005)
    for sx in (-1, 1):
        bolts.disc(V(base)+n*.08+u*sx*.15, n, .045, .05, 6)


def kind_power_run(d, scar, status):
    """The external power run from the jury coil into the engine room.
    params: plan [(x, y)], hatch (object name prefix).
    open: nothing yet (the engine room is cold and the internal feed is dead;
    there is no run to show). patched: a bundled cable pair laid on the deck,
    held by saddle clips every ~1.7 m where it lies on the surface and by
    stand-off brackets where it bridges or drops down a wall, so it cannot
    move under thrust; it enters the engine room through two glands in a
    bolted blank over the access hatch.
    (gone: power runs internal again, the run and the blank removed.)"""
    if status != 'patched':
        return
    plan = scar.params['plan']
    run = d.part(scar, 'cable', d.cable, .01)
    paths, raw = cable_paths(d, plan, (-PAIR, 0, PAIR))
    for off in (-PAIR, PAIR):
        run.tube(paths[off], CABLE_R, 6)
    centre = paths[0]
    n = len(centre)
    lift = centre[0].z-raw[0]
    tangent = [(centre[min(i+1, n-1)]-centre[max(i-1, 0)]).normalized() for i in range(n)]
    across = [(paths[PAIR][i]-paths[-PAIR][i]).normalized() for i in range(n)]
    steep = [abs(t.z) > .6 for t in tangent]
    # Wall drops (more than 3 m): one stand-off bracket at mid-height.
    i = 0
    supports, drop = [], [False]*n
    while i < n:
        if not steep[i]:
            i += 1
            continue
        j = i
        while j < n and steep[j]:
            j += 1
        zs = [centre[k].z for k in range(i, j)]
        if max(zs)-min(zs) > 3.0:
            mid = (max(zs)+min(zs))/2
            supports.append((min(range(i, j), key=lambda k: abs(centre[k].z-mid)), 'wall'))
            for k in range(i, j):
                drop[k] = True
        i = j
    # Along the run: a saddle clip where the bundle lies on the surface, a
    # stand-off post where it bridges (the fin bank, the pipe valley); about
    # every 1.7 m, never in a wall drop or right beside a wall bracket.
    walked, last = 0., -1.
    for i in range(2, n-2):
        walked += (centre[i]-centre[i-1]).length
        if drop[i] or any(abs(i-k) < 4 for k, _ in supports) or walked-last < 1.7:
            continue
        gap = centre[i].z-lift-raw[i]
        if gap > 2.5:
            continue
        supports.append((i, 'clip' if gap < .12 else 'post'))
        last = walked
    for i, how in supports:
        t, c = tangent[i], centre[i]
        if how == 'wall':
            th = V((t.x, t.y, 0))
            best = None
            for dvec in (-th, th, across[i], -across[i]):
                if dvec.length < .1:
                    continue
                dvec = dvec.normalized()
                hit = d.cast(c, c+dvec*4)
                if hit and (hit[0]-c).length < 3.2 and (best is None or (hit[0]-c).length < (best[0]-c).length):
                    best = hit
            if best is None:
                continue
            away = c-best[0]
            away.z = 0
            standoff(d, scar, c, t, across[i], away.normalized(), best[0], best[1])
        elif how == 'post':
            foot = V((c.x, c.y, raw[i]))
            standoff(d, scar, c, t, across[i], V((0,0,1)), foot, V((0,0,1)))
        else:
            saddle(d, scar, c, t, across[i], V((0,0,1)), feet=True)
    # The bolted gland blank over the hatch lid, the run entering through it.
    coaming = d.find(scar.params['hatch']+' coaming', (-9.6, 0))
    if coaming is None:
        return
    lo, hi = d.box(coaming)
    cx, cy = (lo.x+hi.x)/2, (lo.y+hi.y)/2
    R = (hi.x-lo.x)/2*.92
    lid = d.find(scar.params['hatch']+' hatch lid', (cx, cy))
    top = d.box(lid)[1].z if lid else hi.z+.6
    blank = d.part(scar, 'gland blank', d.salvage[1], .02)
    bolts = d.part(scar, 'blank bolt heads', d.steel, .005)
    blank.disc((cx, cy, top+.08), (0,0,1), R, .16, 12)
    for i in range(12):
        a = TAU*(i+.5)/12
        bolts.disc((cx+(R-.2)*math.cos(a), cy+(R-.2)*math.sin(a), top+.2), (0,0,1), .08, .07, 6)
    # Two glands on the blank's starboard-aft quarter, where the pair arrives.
    end = V((plan[-1][0], plan[-1][1], 0))
    toward = (end-V((cx, cy, 0))).normalized()
    side = V((-toward.y, toward.x, 0))
    ends = [paths[-PAIR][-1], paths[PAIR][-1]]
    for off in (-.26, .26):
        g = V((cx, cy, 0))+toward*(R*.55)+side*off
        blank.disc((g.x, g.y, top+.35), (0,0,1), .24, .4, 8)
        # The lead rises out of the gland, over the blank's rim and down onto
        # the end of its deck run.
        p_end = min(ends, key=lambda e: (e.xy-g.xy).length)
        rim = V((cx, cy, 0))+toward*(R+.12)+side*off
        run.tube([V((g.x, g.y, top+.55)), V((g.x, g.y, top+.7))+toward*.35,
                  V((rim.x, rim.y, top+.3)), p_end], CABLE_R, 6)


KINDS = {'power_run': kind_power_run}

SCARS = [
    Scar('aft-deck-strike', 'basin', 'open open open faded',
         'A fast dorsal strike on the port armour plate beside the plant well. '
         'Not pressure or thrust: left open through the escape and the refit; '
         'painted over as a shallow dent once restored.',
         origin=(-23.5,9.3,60), target=(-23.5,9.3,0), r=1.6, depth=.8, elong=1.0, shards=6, sides=8),
    Scar('aft-deck-cluster', 'cluster', 'open open open faded',
         'Small dorsal strikes across the engine-room roof; the armour took them, '
         'nothing was breached, so nobody patched them until the restoration paint.',
         cast='down', centre=(-18,4), spread=(6,5), count=5, radii=(.3,.6)),
    Scar('aft-deck-plate', 'plate', 'open open replaced faded',
         'Starboard (lee) armour plate unbolted by salvagers, not torn: a clean '
         'stud ring. Left bare for the flight; an easy proper plate in the refit.',
         object='Aft deck armour plate', centre=(-23.65,-9.2), how='unbolted'),
    Scar('aft-power-run', 'power_run', 'open patched patched gone',
         'Power from the starboard jury coil to the cold engine room, run outside '
         'and clipped down for thrust; still in service through the limited refit, '
         'gone once restored (internal feed, hatch blank removed).',
         plan=CABLE_PLAN, hatch=HATCH),
    # The stern notch faced the drift too: one fast strike from port-above on
    # the port machinery prong, one glancing scrape along the stern girder.
    Scar('aft-port-prong-strike', 'basin', 'open open open faded',
         'A fast strike from port-above on the port prong armour block over the '
         'cradle winch. Tow-cradle machinery: nothing the flight or the refit needed.',
         origin=(-31,30,32), target=(-29,15,8.4), r=1.1, depth=.55, elong=1.25, shards=5, sides=7),
    Scar('aft-girder-scrape', 'gouge', 'open open open faded',
         'A slow tumbling contact that dragged inboard along the top of the stern '
         'cross girder from its port end, shearing bolt heads. Cosmetic: painted over.',
         start=(-27.9,10.6,60), end=(-28.8,2.4,60), cast_dir=(0,0,-1), w=.5, depth=.32),
]
