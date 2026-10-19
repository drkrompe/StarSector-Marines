"""People at work across Greywater Down's port for C5, so the pull-out reads as a working place.

Kilo Nine's own people (families, Nessa, her yard hands, customs) are `c5_scene.cast()`. This adds
everyone else, each at a job that makes sense where they stand, and times them to what moves
around them: the cranes, the baggage train, the lighter coming down.

What the camera shows decides where the effort goes. C5's opening frame is Kilo Nine's south half;
the rise starts at 18 s and by about 22 s a person is two pixels across. So the readable ring is
what comes into frame in those four seconds, about x 200-500 and y 340-530:

  in the yard   a woman pegging out washing with her daughter bringing the basket (added to the
                yard's own walk, `yard_extras`); welders on the scaffold arms at the hull.
  round it      guards at Nessa's gate and a courier stopping to talk to them; the gardener at the
                vegetable rows with a child, a hand checking the fuel tanks, another walking the
                east fence; store hands carrying crates from the stacks to the hangar; cutters at
                the salvage under the derrick with a slinger signalling; a smoke break in a ring by
                the hangar.
  the hall      a queue along the arrivals hall's north face shuffling up to two customs officers
                at the door, more passengers walking up to join it; porters unloading the baggage
                train when it stops; people out of the south door with their bags to wait at the
                roadside; a road gang on the kerb with its foreman walking the line.
  further out   lashers and a signaller at each container crane, working in step with its spreader;
                a marshaller waving the lighter down onto pad 95 and its stores carried up once it
                is down; loaders at the freight terminal; in Nessa's works riggers under the plate
                crane, welders at the hull sections, store hands, mechanics at the flatbed, a
                mechanic at the generators; and a few at every freighter berth and lighter pad.

Every place people work has its own walk map (`c5_crowd.Nav`) a few dozen metres across, read
from the scene like the yard's, so nobody walks through anything and each map stays cheap. They
never overlap one another or the yard's own map. Anything that drives through a site during the
shot (`port_life`'s traffic, the cranes' boxes) is blocked along its whole way, so nobody stands
in a lane; `mover_clips` checks it.

    populate(skip, frames, frame0) -> [(site, nav, walkers)]
    yard_extras() -> groups for `c5_scene`'s own walk
    posed(frame0, frames) -> [(z, walkers)]: people standing on things above the ground
"""
import math
import random
import time

from mathutils import Vector

import c4x_scene as c4
import c5_crowd as crowd

W = crowd.Walker
REPEAT = 12                     # a loop's steps repeated this many times outlasts any shot
FPS = c4.FPS


class Site:
    """A place people work: its walk map's box, a point on its open ground, its ground height."""

    def __init__(self, name, box, open_at, z=0.06):
        self.name, self.box, self.z = name, box, z
        self.open_at = [Vector(p) for p in open_at] if isinstance(open_at[0], (tuple, list, Vector)) else [Vector(open_at)]
        self.groups = []
        self.n = 0

    def person(self, role, **kw):
        self.n += 1
        return W('Port %s %d %s' % (self.name, self.n, role), role, **kw)

    def add(self, members, spawn, plan, t0=0.0, slots=None):
        g = crowd.Group(members if isinstance(members, list) else [members], Vector(spawn), t0, plan, slots)
        self.groups.append(g)
        return g

    def post(self, role, p, look=None, pose='stand', t0=0.0, **kw):
        """Somebody standing at a job: facing `look` (a point or a person) doing `pose`."""
        w = self.person(role, look=look, pose=pose, **kw)
        self.add(w, p, [('stay',)], t0)
        return w

    def beat(self, role, points, wait=(2.0, 4.0), look=None, pose='stand', rng=None, speed=1.1, t0=0.0):
        """Somebody walking a round between points, pausing at each."""
        rng = rng or random.Random(self.n)
        w = self.person(role, look=look, pose=pose, speed=speed)
        plan = []
        for _ in range(REPEAT):
            for p in points:
                plan += [('go', Vector(p), 0.5), ('wait', rng.uniform(*wait))]
        self.add(w, points[-1], plan, t0)
        return w

    def fetch(self, role, a, b, wait_a=2.5, wait_b=1.5, pose_a='work', pose_b='stand', look_a=None,
              look_b=None, t0=0.0, start_at_b=False, speed=1.15, **kw):
        """Somebody carrying from a to b and going back for more."""
        w = self.person(role, speed=speed, **kw)
        a, b = Vector(a), Vector(b)
        leg_a = [('face', look_a), ('pose', pose_a), ('wait', wait_a), ('carry', True), ('pose', 'stand'),
                 ('go', b, 0.5)]
        leg_b = [('face', look_b), ('pose', pose_b), ('wait', wait_b), ('carry', False), ('pose', 'stand'),
                 ('go', a, 0.5)]
        plan = ([('carry', True), ('go', b, 0.5)] + (leg_b + leg_a) * REPEAT if start_at_b
                else (leg_a + leg_b) * REPEAT)
        self.add(w, a if not start_at_b else a.lerp(b, 0.5), plan, t0)
        return w

    def ring(self, roles, centre, r=0.95, t0=0.0, phase=0.0):
        """People standing round in a ring, facing in, talking."""
        out = []
        for k, role in enumerate(roles):
            a = phase + 2 * math.pi * k / len(roles)
            p = Vector(centre) + Vector((math.cos(a), math.sin(a))) * r
            out.append(self.post(role, p, look=Vector(centre), pose='talk', t0=t0))
        return out

    def line(self, role, a, b, n, pose='crouch', look=None):
        """A gang spaced along a line, at work."""
        a, b = Vector(a), Vector(b)
        return [self.post(role, a.lerp(b, k / max(1, n - 1)), look=look, pose=pose) for k in range(n)]


def handover(site, giver_role, taker_role, store, meet, onward, wait_store=2.5, wait_onward=2.0,
             look_store=None, look_onward=None, start=0.0, taker_from=None, gap=1.1):
    """One carries from the store to a meeting point and hands it to another, who carries it on
    while the first goes back for the next. They face each other for the hand-over. Before `start`
    the giver waits at the store; the taker appears at `taker_from` (a door, a ramp) at `start`."""
    store, meet, onward = Vector(store), Vector(meet), Vector(onward)
    d = (onward - meet).normalized() * gap * 0.5
    g = site.person(giver_role, speed=1.15, look=look_store, pose='folded' if start else 'stand')
    t = site.person(taker_role, speed=1.1)
    giver = [('face', look_store), ('pose', 'work'), ('wait', wait_store), ('carry', True), ('pose', 'stand'),
             ('go', meet - d, 0.35), ('meet', t, gap + 0.5), ('face', t), ('wait', 0.8), ('carry', False),
             ('wait', 0.4), ('face', None), ('go', store, 0.5)]
    taker = [('go', meet + d, 0.35), ('meet', g, gap + 0.5), ('face', g), ('wait', 1.0), ('carry', True),
             ('wait', 0.2), ('face', None), ('go', onward, 0.5), ('face', look_onward), ('pose', 'work'),
             ('wait', wait_onward), ('carry', False), ('pose', 'stand')]
    site.add(g, store, ([('until', start)] if start else []) + giver * REPEAT)
    site.add(t, taker_from or onward, taker * REPEAT, start)
    return g, t


def queue(site, door, slots, officials, parties, rng, interval=2.6, first=1.2):
    """A queue at a door: `parties` are (size, where they start, when), front to back. A party
    walks up together but queues one behind another, a slot each; the front person is let through
    every `interval` seconds or so, and on each the line shuffles up a slot, front first."""
    door = Vector(door)
    for k, p in enumerate(officials):
        site.post('customs', p, look=Vector(slots[0]), pose='talk' if k == 0 else 'folded')
    people = []
    for size, start, t0 in parties:
        speed = rng.uniform(1.0, 1.2)
        for m in range(size):
            child = size >= 3 and m == size - 1
            w = site.person('family', speed=speed, bag=not child and rng.random() < 0.7,
                            scale=0.62 if child else rng.uniform(0.95, 1.05))
            if tuple(start) in slots:                    # already in line: a slot each
                at = Vector(slots[min(slots.index(tuple(start)) + m, len(slots) - 1)])
            else:
                at = Vector(start) + Vector((rng.uniform(-0.6, 0.6), rng.uniform(-0.6, 0.6)))
            people.append((w, at, t0 + (0.4 * m if tuple(start) not in slots else 0.0)))
    served, t = [], first
    for _ in people:
        served.append(t)
        t += interval * rng.uniform(0.75, 1.3)
    for i, (w, start, t0) in enumerate(people):
        def place(at):
            return min(sum(1 for j in range(i) if served[j] > at), len(slots) - 1)
        arrive = t0
        for _ in range(3):                              # when they get there depends on which slot is theirs
            arrive = t0 + (start - Vector(slots[place(arrive)])).length / w.speed * 1.1 + 0.5

        def ahead(k):                                   # whom a person in slot k faces: the one in front
            return Vector(slots[k - 1]) if k else door
        prev = place(arrive)
        plan = [('go', Vector(slots[prev]), 0.45), ('face', ahead(prev))]
        for j in range(i):
            if served[j] > arrive:
                nxt = place(served[j] + 0.01)
                if nxt != prev:
                    plan += [('until', served[j] + 0.3 + 0.35 * (i - j)), ('go', Vector(slots[nxt]), 0.45),
                             ('face', ahead(nxt))]
                    prev = nxt
        w.look = ahead(prev)
        plan += [('until', served[i]), ('face', None), ('go', door, 0.6), ('enter', door)]
        site.add(w, start, plan, t0)
    return served


# ---------------------------------------------------------------- what moves, read off the scene

class Shot:
    """C5's clock and the things in the scene people work in step with: a crane's spreader, the
    baggage train, a lighter. Everything degrades to fixed timings when the scene lacks them."""

    def __init__(self, frame0, frames):
        self.frame0, self.frames = frame0, frames

    def track(self, name, step=2):
        """[(t, x, y, z)] of an object through C5 (t in C5 seconds), or None if it does not move."""
        import bpy
        ob = bpy.data.objects.get(name)
        if ob is None:
            return None
        fcs = {(fc.data_path, fc.array_index): fc for fc in crowd._fcurves(ob)}
        if not any(k[0] == 'location' for k in fcs):
            return None
        out = []
        for f in range(self.frame0, self.frame0 + self.frames, step):
            p = [fcs[('location', i)].evaluate(f) if ('location', i) in fcs else ob.location[i] for i in range(3)]
            out.append(((f - self.frame0) / FPS, *p))
        return out

    def stops(self, name, near, within=8.0, rest=0.4):
        """[(t0, t1)] while `name` stands still within `within` metres of `near`."""
        tr = self.track(name)
        if not tr:
            return []
        out, cur = [], None
        for (ta, xa, ya, za), (tb, xb, yb, zb) in zip(tr, tr[1:]):
            still = math.hypot(xb - xa, yb - ya) / (tb - ta) < rest and math.hypot(xa - near[0], ya - near[1]) < within
            if still and cur is None:
                cur = ta
            elif not still and cur is not None:
                out.append((cur, ta))
                cur = None
        if cur is not None:
            out.append((cur, tr[-1][0]))
        return [s for s in out if s[1] - s[0] > 1.0]

    def low(self, name, below):
        """[(t0, t1, x, y)] while `name` (a crane's spreader) hangs lower than `below`."""
        tr = self.track(name)
        if not tr:
            return []
        out, cur = [], None
        for t, x, y, z in tr:
            if z < below and cur is None:
                cur = (t, x, y)
            elif z >= below and cur is not None:
                out.append((cur[0], t, cur[1], cur[2]))
                cur = None
        if cur is not None:
            out.append((cur[0], tr[-1][0], cur[1], cur[2]))
        return out

    def landing(self, name, default):
        """When `name` (a lighter coming down) is first on its pad."""
        tr = self.track(name)
        if not tr:
            return default
        floor = min(z for _, _, _, z in tr)
        return next((t for t, x, y, z in tr if z < floor + 0.3), default)


# ---------------------------------------------------------------- near: the gate and the hall

def gate(rng, shot):
    """Nessa's gate: a guard at the gatehouse, another pacing the kerb beside it, and a courier off
    the port road who stops to talk to both before leaning by the gatehouse."""
    s = Site('gate', (268.0, 378.6, 336.0, 388.4), (305.0, 381.0), z=0.1)
    inner = s.post('guard', (321.6, 386.4), look=Vector((307.0, 382.0)), pose='folded')
    outer = s.person('guard', speed=0.9)                # clear of the gate's mouth, where traffic turns in
    beat = [('go', Vector((321.0, 381.2)), 0.4), ('wait', 3.0), ('go', Vector((333.0, 381.2)), 0.4), ('wait', 4.0)]
    courier = s.person('crew', speed=1.3)
    s.add(outer, (321.0, 381.2), beat + [('go', Vector((321.4, 381.0)), 0.4), ('meet', courier, 2.6), ('pose', 'talk'),
                                         ('wait', 7.0), ('pose', 'stand')] + beat * REPEAT)
    s.add(courier, (290.0, 380.6), [('go', Vector((319.4, 380.2)), 0.4), ('meet', outer, 2.4), ('face', outer),
                                    ('pose', 'talk'), ('wait', 6.5), ('pose', 'stand'), ('face', None),
                                    ('go', Vector((318.3, 386.6)), 0.4), ('face', inner), ('pose', 'talk'), ('wait', 6.0),
                                    ('pose', 'folded'), ('stay',)])
    outer.look = courier
    return s


def hall(rng, shot):
    """The arrivals hall: customs at the north door with a queue along the wall, passengers walking up
    to join it, porters unloading the baggage train, people out of the south door to wait at the
    roadside, a road gang on the kerb, and the ferry's hands at its steps."""
    s = Site('hall', (150.0, 379.0, 289.0, 505.0), (240.0, 438.0), z=0.05)
    door = (240.0, 432.3)
    # Along the north face (the terminal loop runs at y 445, so the line keeps to the wall), round
    # the corner and down the east face.
    slots = ([(240.6 + 1.05 * k, 434.6) for k in range(18)]
             + [(261.4, 433.4 - 1.05 * k) for k in range(20)])
    parties, k = [], 0
    for size in (2, 1, 3, 1, 2, 1, 1, 2, 3, 1, 2, 1, 2, 1, 3, 2):     # already in line
        parties.append((size, slots[min(k, len(slots) - 1)], 0.0))
        k += size
    for i, size in enumerate((2, 1, 3, 1, 2, 1, 2, 1)):             # walking up from the ferry side
        parties.append((size, (213.0 + 3.0 * (i % 3), 439.0 - 0.8 * (i % 2)), 1.0 + 2.6 * i))
    queue(s, door, slots, [(238.8, 433.5), (237.4, 435.0)], parties, rng)
    # Porters at the baggage door (west on the north face): they unload the baggage train while it
    # stands at the hall, and stack bags at the door the rest of the time.
    bag_door = Vector((226.0, 432.6))
    stand = [(t0, t1) for t0, t1 in shot.stops('Baggage tug 1', (240.0, 445.0)) if t1 > 1.0]
    for k in range(3):
        wait_at = Vector((228.0 + 1.6 * k, 438.4))
        w = s.person('port', speed=1.15, look=Vector((236.0, 445.0)), pose='talk')
        plan = []
        for t0, t1 in stand:
            dolly = Vector((235.4 - 3.9 * k, 442.6))     # the dollies trail west of the tug
            plan += [('until', t0 + 0.6 + 0.5 * k)]
            n = max(1, int((t1 - t0 - 2.0) / 7.0))
            for _ in range(n):
                plan += [('go', dolly, 0.5), ('face', dolly + Vector((0, 2))), ('pose', 'work'), ('wait', 1.2),
                         ('carry', True), ('pose', 'stand'), ('go', bag_door, 0.6), ('carry', False), ('wait', 0.6)]
            plan += [('go', wait_at, 0.5), ('face', Vector((236.0, 445.0))), ('pose', 'talk')]
        s.add(w, wait_at, plan + [('stay',)])
    # The ferry's hands at its steps.
    s.post('crew', (163.5, 495.6), look=Vector((200.0, 470.0)), pose='folded')
    s.post('crew', (159.2, 499.8), look=Vector((168.0, 492.0)), pose='talk')
    # Out of the south door with their bags, to the roadside stop to wait.
    south = Vector((240.0, 397.3))
    stop = Vector((262.0, 383.4))
    for i in range(10):                                 # the first few already waiting, one pair on the way
        size = rng.choice((1, 1, 2, 2, 3))
        ms = [s.person('family', speed=1.15, bag=True, scale=0.62 if size == 3 and m == 2 else 1.0) for m in range(size)]
        spot = stop + Vector((2.3 * (i % 5) - 4.0 + rng.uniform(-0.4, 0.4), -1.7 * (i // 5) + rng.uniform(-0.3, 0.3)))
        if i < 4:
            s.add(ms, spot, [('face', Vector((240.0, 372.0))), ('stay',)])
        elif i == 4:
            s.add(ms, south.lerp(spot, 0.5), [('go', spot, 0.6), ('stay',)])
        else:
            s.add(ms, south, [('go', spot, 0.6), ('stay',)], 1.0 + 3.6 * (i - 5))
    s.post('guard', (243.6, 396.4), look=stop, pose='folded')
    # The road gang along the kerb, the foreman walking the line.
    gang = s.line('port', (156.0, 380.4), (196.0, 380.4), 7, look=Vector((176.0, 372.0)))
    s.beat('port', [(157.0, 382.4), (195.0, 382.4)], wait=(1.0, 2.5), look=gang[3], pose='talk', speed=0.8)
    return s


# ---------------------------------------------------------------- near: round Kilo Nine

def k9_east(rng, shot):
    """The strip east of the pad: the gardener at the vegetable rows with a child, a hand at the fuel
    tanks, another walking the east fence."""
    s = Site('k9-east', (453.0, 385.0, 494.0, 512.0), (470.0, 470.0), z=0.15)
    g = s.post('worker', (471.0, 424.4), look=Vector((471.0, 426.0)), pose='crouch')
    s.post('family', (473.6, 416.4), look=g, pose='stand', scale=0.62)
    s.fetch('worker', (462.0, 400.4), (466.0, 412.0), look_a=Vector((455.0, 400.5)), look_b=Vector((470.0, 418.0)),
            wait_a=4.0, wait_b=1.0, pose_a='work')
    s.beat('worker', [(489.4, 440.0), (489.4, 505.0)], wait=(1.5, 3.0), look=Vector((500.0, 470.0)), speed=0.9)
    return s


def k9_stores(rng, shot):
    """The stores north-west of the pad: two hands carrying crates from the stacks round to the
    hangar door, a third checking the stacks."""
    s = Site('k9-stores', (291.0, 472.5, 352.0, 518.4), (330.0, 512.0), z=0.15)
    s.fetch('worker', (309.0, 476.0), (330.0, 517.6), look_a=Vector((314.0, 474.0)), look_b=Vector((330.0, 520.0)),
            wait_a=2.0, wait_b=1.0)
    s.fetch('worker', (321.0, 480.0), (334.0, 517.6), look_a=Vector((318.0, 482.0)), look_b=Vector((334.0, 520.0)),
            wait_a=3.0, wait_b=1.0, start_at_b=True)
    s.beat('worker', [(300.0, 476.0), (305.0, 498.0), (322.0, 500.0)], wait=(2.0, 3.5), pose='work', speed=0.8)
    return s


def k9_salvage(rng, shot):
    """The salvage under the derrick: cutters crouched at the plates, a slinger signalling the hook,
    a hand dragging offcuts to the pile's edge."""
    s = Site('k9-salvage', (405.0, 503.0, 470.0, 556.0), (440.0, 508.0), z=0.15)
    s.post('worker', (418.6, 509.0), look=Vector((420.0, 512.0)), pose='signal')
    for p, q in (((428.0, 512.6), (430.0, 516.0)), ((446.0, 513.0), (446.0, 517.0)), ((459.0, 530.0), (455.0, 531.0))):
        s.post('worker', p, look=Vector(q), pose='crouch')
    s.fetch('worker', (437.0, 512.4), (440.0, 506.0), look_a=Vector((437.0, 516.0)), look_b=Vector((440.0, 503.0)),
            wait_a=3.0, wait_b=0.8)
    return s


def k9_hangar(rng, shot):
    """The hangar: a smoke break in a ring by its east wall, one more strolling out to join them, and
    a mechanic at the door."""
    s = Site('k9-hangar', (348.4, 512.0, 380.0, 566.0), (360.0, 540.0), z=0.15)
    s.ring(['worker', 'worker', 'worker', 'crew'], (354.6, 548.0), r=0.95, phase=0.4)
    late = s.person('worker', speed=1.0, pose='talk')
    s.add(late, (349.4, 527.0), [('go', Vector((355.9, 546.4)), 0.3), ('face', Vector((354.6, 548.0))), ('stay',)], 4.0)
    s.post('worker', (349.6, 560.0), look=Vector((345.0, 560.0)), pose='work')
    return s


def yard_extras():
    """Groups for the yard's own walk (`c5_scene`), in its opening frame and away from the families'
    way to the workshop: a woman pegging out the washing, her daughter bringing the basket."""
    G = crowd.Group
    hang = W('Yard washing', 'family', look=Vector((373.0, 401.0)), pose='work')
    girl = W('Yard washing girl', 'family', scale=0.64, speed=1.0)
    line = Vector((374.0, 398.0))
    groups = [G([hang], line, 0.0, [('stay',)])]
    plan = []
    for _ in range(REPEAT):
        plan += [('carry', True), ('go', line + Vector((1.2, -0.4)), 0.4), ('face', hang), ('wait', 2.5), ('carry', False),
                 ('face', None), ('go', Vector((365.0, 395.2)), 0.5), ('wait', 3.0)]
    groups.append(G([girl], Vector((365.0, 395.2)), 0.0, plan))
    return groups


def posed(frame0, frames):
    """People standing on things above the ground, where no walk map reaches: welders on the
    scaffold arms swung in to Second Wind's hull, crouched at the tip, a mate behind each.
    Returns [(z, walkers)]; they stand still, so they need no walk."""
    kx, ky = c4.K9
    kh = c4.k9_heading()
    out = []
    for side in (-1, 1):
        base = Vector(c4.at(kx, ky, kh, 8, side * (c4.K9_R + 7))[:2])
        a = kh - side * math.pi / 2                     # the arm's heading once swung in
        d = Vector((math.cos(a), math.sin(a)))
        for k, (along, pose) in enumerate(((17.5, 'crouch'), (14.0, 'work'))):
            w = W('Scaffold %d %d' % (side, k), 'worker', pose=pose)
            p = base + d * along
            w.track = [(f, p.x, p.y, a, 0.0, True) for f in range(frames)]
            w.state = [(pose, False)] * frames
            out.append((21.8, w))
    return out


# ---------------------------------------------------------------- further out

def lighter95(rng, shot):
    """Pad 95: its lighter lifted off in C4 and another comes down at about 22 s. A marshaller waves
    it in; the port hands wait by the stores, and once it is down carry them to its crew."""
    s = Site('lighter95', (70.0, 380.0, 149.5, 405.0), (95.0, 386.0))
    down = shot.landing('Lighter 6 return', 22.0)
    m = s.person('port', look=Vector((95.0, 412.0)))
    s.add(m, (84.0, 400.6), [('until', down - 9.0), ('pose', 'signal'), ('until', down + 0.8), ('pose', 'stand'),
                             ('go', Vector((86.0, 393.0)), 0.5), ('pose', 'talk'), ('stay',)])
    for k, (store, meet, foot) in enumerate((((88.0, 389.0), (93.0, 396.0), (97.5, 398.4)),
                                             ((101.0, 389.5), (99.0, 395.0), (101.5, 397.8)))):
        handover(s, 'port', 'crew', store, meet, foot, look_store=Vector(store) + Vector((0, 4)),
                 look_onward=Vector((95.0, 412.0)), start=down + 2.0 + 2.5 * k, taker_from=foot)
    return s


def crane_crew(name, x, spreader, rng, shot, lashers=2, clerk=True, home_dx=-6.0):
    """A container gantry's ground crew: a signaller and lashers. Whenever the spreader comes down
    on a box the lashers walk to its side and crouch at it until it lifts, and the signaller signals
    while it is down; between lifts they stand back and talk."""
    s = Site(name, (x - 28.0, 264.6, x + 28.0, 293.4), (x, 270.0))
    windows = shot.low(spreader, 14.0) or [(1.0, 5.0, x + 1.6, 277.0), (9.5, 14.0, x + 1.6, 277.0),
                                           (17.5, 21.5, x + 14.2, 277.0), (25.5, 30.0, x + 14.2, 277.0)]
    home = Vector((x + home_dx, 270.0))
    sig = s.person('port', look=Vector((x, 280.0)))
    plan = []
    for t0, t1, bx, by in windows:
        plan += [('face', Vector((bx, by))), ('until', t0 - 1.0), ('pose', 'signal'), ('until', t1 + 0.5), ('pose', 'talk')]
    s.add(sig, home, plan + [('stay',)])
    for k in range(lashers):
        w = s.person('port', look=Vector((x, 280.0)), pose='talk', speed=1.3)
        wait_at = home + Vector((1.2 + 1.0 * k, -0.9 + 1.4 * k))
        plan = []
        for t0, t1, bx, by in windows:
            side = Vector((bx + (-4.5 if k == 0 else 4.5), 274.4))       # in the aisle beside the box
            plan += [('until', max(0.0, t0 - 3.5)), ('pose', 'stand'), ('go', side, 0.6), ('face', Vector((bx, by))),
                     ('pose', 'crouch'), ('until', t1), ('pose', 'stand'), ('go', wait_at, 0.6),
                     ('face', home), ('pose', 'talk')]
        s.add(w, wait_at, plan + [('stay',)])
    if clerk:
        s.beat('port', [(x - 16.0, 286.4), (x + 16.0, 286.4)], wait=(2.0, 4.0), pose='work', rng=rng, speed=0.9)
    return s


def containers_east(rng, shot):
    return crane_crew('cy-east', 410.0, 'Crane spreader 3', rng, shot)


def containers_mid(rng, shot):
    return crane_crew('cy-mid', 150.0, 'Crane spreader 2', rng, shot)


def terminal(rng, shot):
    """The freight terminal: loaders carrying crates out of the shed doors to the truck at the dock,
    a clerk checking them off, the driver waiting by his cab."""
    s = Site('terminal', (178.0, 612.0, 262.0, 643.9), (225.0, 632.0))
    s.fetch('port', (220.0, 642.9), (210.2, 638.0), look_a=Vector((220.0, 650.0)), look_b=Vector((205.0, 638.0)))
    s.fetch('port', (235.0, 642.9), (210.4, 634.0), look_a=Vector((235.0, 650.0)), look_b=Vector((205.0, 634.0)),
            start_at_b=True)
    s.fetch('port', (190.0, 642.9), (199.6, 637.0), look_a=Vector((190.0, 650.0)), look_b=Vector((205.0, 637.0)),
            wait_a=3.5)
    s.post('port', (212.6, 642.2), look=Vector((210.0, 636.0)), pose='work')
    s.post('crew', (207.6, 627.6), look=Vector((212.6, 642.2)), pose='folded')
    return s


def staging(rng, shot):
    """The heavy berth's staging: modules waiting for an inbound, two checkers walking them."""
    s = Site('staging', (92.0, 590.0, 160.0, 640.0), (96.0, 595.0))
    a = s.person('port', speed=0.85, pose='work')
    b = s.person('crew', speed=0.85, pose='talk')
    pts = [(96.0, 597.0), (150.0, 597.0), (152.0, 634.0), (96.0, 634.0)]
    plan = []
    for _ in range(REPEAT):
        for p in pts:
            plan += [('go', Vector(p), 0.6), ('wait', rng.uniform(2.0, 4.0))]
    s.add([a, b], pts[-1], plan, slots=[Vector((0, 0)), Vector((0, 0.9))])
    return s


def k9_hall(rng, shot):
    """Nessa's fabrication hall and parts store: store hands carrying parts round to the hall's
    back door, a foreman watching."""
    s = Site('k9-hall', (322.0, 642.5, 400.0, 700.0), (352.0, 650.0), z=0.15)
    s.fetch('worker', (340.0, 666.2), (345.0, 643.6), look_a=Vector((340.0, 662.0)), look_b=Vector((345.0, 640.0)))
    s.fetch('worker', (358.0, 678.0), (360.0, 643.6), look_a=Vector((358.0, 674.0)), look_b=Vector((360.0, 640.0)),
            start_at_b=True, wait_a=3.5)
    s.fetch('worker', (385.0, 666.0), (372.0, 643.6), look_a=Vector((382.0, 666.0)), look_b=Vector((372.0, 640.0)),
            wait_a=2.0, t0=2.0)
    s.post('worker', (396.0, 650.0), look=Vector((360.0, 660.0)), pose='folded')
    return s


def k9_hall_front(rng, shot):
    """The hall's south doors: Nessa's foreman and a hand over a drawing at the west door, a welder
    at the east one."""
    s = Site('k9-front', (316.0, 600.6, 400.0, 607.2), (352.0, 604.0), z=0.15)
    a = s.post('worker', (330.0, 604.6), pose='talk')
    b = s.post('worker', (331.4, 604.0), look=a, pose='work')
    a.look = b
    s.post('worker', (378.0, 605.2), look=Vector((378.0, 610.0)), pose='crouch')
    return s


def k9_plates(rng, shot):
    """Under Nessa's plate crane: a signaller, two riggers hooking a plate, welders at the hull
    sections, and a foreman walking along them."""
    s = Site('k9-plates', (405.5, 598.0, 473.0, 696.0), (440.0, 655.0), z=0.15)
    s.post('worker', (428.5, 637.2), look=Vector((439.0, 620.0)), pose='signal')
    s.post('worker', (431.6, 623.6), look=Vector((438.0, 628.0)), pose='crouch')
    s.post('worker', (444.4, 632.4), look=Vector((438.0, 628.0)), pose='crouch')
    welders = [s.post('worker', p, look=Vector(q), pose='crouch')
               for p, q in (((420.0, 679.0), (420.0, 684.0)), ((437.4, 684.0), (442.0, 684.0)),
                            ((464.0, 689.0), (464.0, 684.0)))]
    s.beat('worker', [(414.0, 676.6), (468.0, 676.6)], wait=(2.0, 3.5), look=welders[1], pose='talk', speed=0.85)
    return s


def k9_turn(rng, shot):
    """The flatbed at the head of the haul road: two mechanics at its open cab and a third bringing
    tools, two strapping the hull section down, its driver waiting."""
    s = Site('k9-turn', (312.0, 718.5, 399.0, 750.0), (380.0, 740.0), z=0.18)
    s.post('worker', (362.0, 734.4), look=Vector((358.0, 734.0)), pose='crouch')
    m = s.post('worker', (361.6, 736.9), look=Vector((358.0, 735.0)), pose='work')
    s.fetch('worker', (370.0, 742.0), (361.4, 731.4), look_a=Vector((372.0, 744.0)), look_b=m,
            wait_a=4.0, wait_b=2.0, pose_b='talk')
    s.post('worker', (349.0, 736.4), look=Vector((349.0, 734.0)), pose='crouch')
    s.post('worker', (343.0, 731.6), look=Vector((343.0, 734.0)), pose='crouch')
    s.post('crew', (366.0, 728.6), look=m, pose='folded')
    return s


def k9_power(rng, shot):
    """The generator sets: a mechanic crouched at the end of one, his mate fetching tools."""
    s = Site('k9-power', (436.0, 697.0, 494.0, 724.0), (440.0, 700.0), z=0.15)
    mech = s.post('worker', (455.4, 716.0), look=Vector((452.0, 716.0)), pose='crouch')
    s.fetch('worker', (472.0, 718.0), (456.8, 717.9), look_a=Vector((470.0, 712.0)), look_b=mech,
            wait_a=3.0, wait_b=2.5, pose_b='talk')
    return s


def k9_scrap(rng, shot):
    """Two at the scrap heap's edge, sorting: one crouched, one carrying offcuts to a pile."""
    s = Site('k9-scrap', (455.0, 719.0, 492.0, 727.0), (470.0, 721.0), z=0.15)
    s.post('worker', (466.0, 723.6), look=Vector((466.0, 730.0)), pose='crouch')
    s.fetch('worker', (478.0, 723.4), (488.0, 721.0), look_a=Vector((478.0, 730.0)), look_b=Vector((490.0, 721.0)),
            wait_a=3.0)
    return s


def far(rng, shot):
    """Everywhere else the rise reveals: a few at every berth, pad and crane, the heavy berth's
    staging and the fuel farm."""
    out = []
    h = c4.ROW_HEADING
    for x, off, who in c4.BERTHS:
        if who == 'ferry':
            continue
        cx, cy = x, c4.BERTH_Y + c4.BERTH_DY[x]
        stern = Vector(c4.at(cx, cy, h, -(c4.BERTH_R + 4), 0)[:2])
        s = Site('berth%d' % int(x), (stern.x - 28, stern.y - 20, stern.x + 28, stern.y + 2),
                 [stern + Vector((15, -10)), stern + Vector((-15, -10))])
        lane = stern.x
        if who in ('hound', 'unloading'):
            s.post('port', (lane - 10.5, stern.y - 5.0), look=Vector((lane, stern.y - 8.0)), pose='signal')
            s.post('port', (lane + 18.0, stern.y - 6.5), look=Vector((lane + 14.0, stern.y - 6.0)), pose='crouch')
            s.post('port', (lane - 19.0, stern.y - 10.5), look=Vector((lane - 14.0, stern.y - 9.0)), pose='crouch')
            s.beat('port', [(lane + 11.0, stern.y - 17.0), (lane + 24.0, stern.y - 17.0)], wait=(2.0, 4.0), pose='work', rng=rng)
            if who == 'hound':
                s.ring(['crew', 'crew', 'crew'], (lane - 18.0, stern.y - 16.0), r=0.85)
        elif who == 'fuelling':
            bow = Vector(c4.at(cx, cy, h, 4, -(c4.BERTH_R + 6))[:2])
            s = Site('berth%d' % int(x), (bow.x - 12, bow.y - 16, bow.x + 14, bow.y + 16), bow + Vector((8, 0)))
            s.post('port', bow + Vector((-1.0, 4.4)), look=bow + Vector((-6, 4)), pose='work')
            s.post('port', bow + Vector((4.0, -2.6)), look=bow, pose='work')
            s.post('crew', bow + Vector((7.0, 3.0)), look=bow + Vector((4.0, -2.6)), pose='talk')
        elif who == 'lifting':
            s.post('port', (lane - 14.0, stern.y + 0.5), look=Vector((cx, cy)), pose='signal')
            s.post('crew', (lane + 16.0, stern.y - 8.0), look=Vector((cx, cy)), pose='folded')
            s.post('crew', (lane + 17.2, stern.y - 9.0), look=Vector((cx, cy)), pose='stand')
        else:                                         # the empty berth: a Hound comes down on it during the rise
            s.post('port', (lane - 14.0, stern.y - 2.0), look=Vector((cx, cy)), pose='signal')
            s.beat('port', [(lane + 20.0, stern.y - 6.0), (lane + 20.0, stern.y - 15.0)], wait=(1.0, 2.0), rng=rng)
        out.append(s)
    for i, (x, who) in enumerate(c4.LIGHTERS):
        if x == 95.0:
            continue                                             # lighter95(): close enough to be worked properly
        s = Site('pad%d' % int(x), (x - 18.0, 380.0, x + 18.0, 405.0), (x, 386.0))
        if who in ('lifting', 'landing'):
            s.post('port', (x - 9.0, 398.0), look=Vector((x, 412.0)), pose='signal')
            s.post('port', (x + 12.0, 392.0), look=Vector((x, 412.0)), pose='folded')
        else:
            s.fetch('port', (x - 3.0, 390.0), (x + 3.0, 398.6), look_a=Vector((x, 393.0)), look_b=Vector((x, 412.0)),
                    t0=rng.uniform(0, 3))
            if i % 2:
                s.post('crew', (x + 6.5, 398.0), look=Vector((x + 3.0, 398.6)), pose='talk')
        out.append(s)
    out.append(crane_crew('cy-380', -380.0, 'Crane spreader 0', rng, shot, lashers=1, clerk=False))
    out.append(crane_crew('cy-120', -120.0, 'Crane spreader 1', rng, shot, lashers=1, clerk=False, home_dx=9.0))
    hx0, hx1, hy0, hy1 = c4.HEAVY
    s = Site('heavy', (hx0 + 18.0, hy0 - 26.0, hx0 + 98.0, hy0 - 2.0), (hx0 + 22.0, hy0 - 4.0))
    s.beat('port', [(hx0 + 24.0, hy0 - 4.0), (hx0 + 90.0, hy0 - 4.0)], wait=(2.0, 4.0), pose='work', rng=rng)
    s.post('crew', (hx0 + 27.0, hy0 - 21.0), look=Vector((hx0 + 40.0, hy0 - 18.0)), pose='talk')
    s.post('port', (hx0 + 28.4, hy0 - 21.6), look=Vector((hx0 + 27.0, hy0 - 21.0)), pose='work')
    out.append(s)
    s = Site('fuelfarm', (-405.0, 638.0, -290.0, 646.0), (-350.0, 642.0))
    a, b = s.person('port', speed=0.9), s.person('port', speed=0.9)
    s.add([a, b], (-398.0, 641.0), [('go', Vector((-296.0, 641.5)), 0.6), ('wait', 3.0), ('go', Vector((-398.0, 641.0)), 0.6),
                                    ('wait', 3.0)] * REPEAT, slots=[Vector((0, 0)), Vector((0, 0.9))])
    out.append(s)
    return out


SITES = [gate, hall, k9_east, k9_stores, k9_salvage, k9_hangar, lighter95, containers_east, containers_mid,
         terminal, staging, k9_hall, k9_hall_front, k9_plates, k9_turn, k9_power, k9_scrap, far]


def populate(skip, frames, frame0, only=None):
    """Cast, walk and check every site. Returns [(site, nav, walkers)]; `only` limits it to sites
    whose names start with one of those prefixes (for working on one place)."""
    rng = random.Random(23)
    shot = Shot(frame0, frames)
    movers = crowd.moving_vehicles(frame0, frame0 + frames - 1)
    sites = []
    for make in SITES:
        made = make(rng, shot)
        sites += made if isinstance(made, list) else [made]
    out = []
    for s in sites:
        if only and not s.name.startswith(tuple(only)):
            continue
        t0 = time.time()
        x0, y0, x1, y1 = s.box
        near = [m for m in movers if any(x0 - 15 < x < x1 + 15 and y0 - 15 < y < y1 + 15 for _, x, y, _ in m[2])]
        nav = crowd.Nav(*s.box)
        nav.read_scene(skip, s.open_at, movers=near)
        walkers = crowd.simulate(nav, s.groups, frames, None)
        inside = crowd.clip_report(nav, walkers)
        caught = set()
        hit = crowd.mover_clips(walkers, near, frame0, caught)
        print('PORT_PEOPLE %-10s walkers=%3d inside-something=%d run-over=%d movers=%d %.1fs%s' % (
            s.name, len(walkers), inside, hit, len(near), time.time() - t0,
            ' ' + str(sorted(caught)[:4]) if caught else ''), flush=True)
        out.append((s, nav, walkers))
    return out
