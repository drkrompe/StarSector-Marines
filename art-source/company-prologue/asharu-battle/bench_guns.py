"""Bench tests for W4 (asharu-effects-plan.md): every gun and energy weapon reads as its own.

Each test arms real mounts with swfx_guns.arm (the game weapon the build's manifest or its variant
names) and fires them with swfx_guns.fire_weapon at the game's cadence, framed close: W0 found a
light MG's flash unreadable at 360 m. See effects_bench.py for the pattern.

Builds and the weapons on them (game variants):
  manticore-p   t1 Hellbore (modelled; the owner's leaning), t2/t4 light assault guns,
                t3 light needler (twin), t5/t6 Harpoon pods (W3)
  lasher        t1-t3 light machine guns (Ammo Feeder)
  wolf-p        t1 ion cannon, t2 mining blaster
  wolf          (merchant) t1 ion cannon, t2/t3 PD lasers (W2), t4 heavy blaster
  colossus-mk3  t7/t8 light autocannons (WS 011/012), t1-t6 light machine guns
  hound-p       t1 assault chaingun, t2 light dual MG; no finished build carries a Vulcan (the
                Buffalo Mk.II and Dram are not built), so its twin mount stands in for one.

A turret trains onto a target (swfx_guns.aim, through fire_weapon's target) only on a ship placed
with own_hull=True: otherwise its pivot is the build's, shared by every copy.
"""
import math
import random

SURVEY_BUILDS = ['lasher', 'manticore-p', 'wolf-p', 'wolf', 'colossus-mk3', 'hound-p']


def _M(ship):
    from mathutils import Matrix
    return ship.matrix @ Matrix.Scale(ship.mpu, 4)


def muzzle(ship, turret, tag):
    """World position (metres) and line of fire of one of a placed ship's muzzles."""
    from mathutils import Vector
    import swfx_guns
    m = _M(ship) @ swfx_guns.anchor_matrix(ship.object(f'SWFX anchor turret {turret} {tag}'))
    return m.translation, (m.to_3x3() @ Vector((1, 0, 0))).normalized()


def to_copy(ship, p):
    """A world point (metres) in the ship's own copy space (units), where its kit objects live."""
    from mathutils import Vector
    return _M(ship).inverted() @ Vector(p)


def survey(bench):
    for i, b in enumerate(SURVEY_BUILDS):
        s = bench.ship(b if b != 'wolf' else 'wolf-m', b, at=(0, 400 * i, 0))
        with s.kit() as (ctx, k):
            for a in sorted(ctx.anchors.guns, key=lambda g: (g.turret, g.tag)):
                p, d = muzzle(s, a.turret, a.tag)
                print(f'SURVEY {b:13} t{a.turret} {a.tag:14} {a.kind:8} scale {a.scale:.2f} '
                      f'muzzle ({p.x:.1f}, {p.y - 400 * i:.1f}, {p.z:.1f}) axis ({d.x:.2f}, {d.y:.2f}, {d.z:.2f})',
                      flush=True)


def _cast(bench):
    """A world-space ray cast against everything placed: (hit, point, normal)."""
    bpy = bench.bpy
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()

    def cast(origin, direction):
        hit, p, n, *_ = bench.scene.ray_cast(dg, origin, direction)
        return hit, p, n
    return cast


# ======================================================================================= tests

def hellbore(bench):
    """The Manticore (P)'s main gun, one shot: the huge orange flash lingering 0.75 s, the slow
    orange-white slug in its glow, lighting the bow as it leaves."""
    m = bench.ship('manticore', 'manticore-p', throttle=.3)
    with m.kit() as (ctx, k):
        k.guns.arm(ctx, 1, 'hellbore')
        k.guns.fire_weapon(ctx, 1, 2, .1)
    bench.camera((75, 95, 40), (70, 0, 6), 28)


def manticore_secondaries(bench):
    """The Manticore (P)'s light needler (twin, bow: 15-round bursts of long white needles) and
    port light assault gun (orange HE shells every 0.25 s)."""
    m = bench.ship('manticore', 'manticore-p', throttle=.3)
    with m.kit() as (ctx, k):
        k.guns.arm(ctx, 3, 'lightneedler')
        k.guns.arm(ctx, 2, 'lightag')
        k.guns.fire_weapon(ctx, 3, 1, .8)
        k.guns.fire_weapon(ctx, 2, 1, .8)
    bench.camera((130, 150, 70), (60, 50, 0), 30)


def _lashers(bench, close=False):
    b = bench.ship('lasher-b', 'lasher', at=(0, -40, 0), throttle=.6)
    if not close:
        a = bench.ship('lasher-a', 'lasher', at=(0, 40, 0), throttle=.6)
        with a.kit() as (ctx, k):
            for t in (1, 3):
                k.guns.arm(ctx, t, 'lightmg')
                k.guns.fire_weapon(ctx, t, 1, 1.25)
    with b.kit() as (ctx, k):
        k.guns.feeder(ctx, 3, 1, 40)
        k.guns.feeder(ctx, 2, 1, 40)
        for t in (2, 3):
            k.guns.arm(ctx, t, 'lightmg')
            k.guns.fire_weapon(ctx, t, 1 if not close else 8, 1.25, feeder=True)
    return b


def lasher_mg(bench):
    """Two Lashers' light MGs: A (far) at the game's cadence, 5-round bursts and a 0.4 s pause;
    B (near) under the Ammo Feeder, guns glowing amber and the rate doubled."""
    _lashers(bench)
    bench.camera((60, -170, 55), (120, 0, 5), 30)


def lasher_close(bench):
    """Lasher B's bow gun close up under the Ammo Feeder: the amber barrel glow, the small warm
    flash that lingers a frame or two, and the streaks leaving."""
    _lashers(bench, close=True)
    bench.camera((62, -60, 18), (55, -40, 9), 40)


def _wolf_ion(bench):
    w = bench.ship('wolf-p', 'wolf-p', throttle=.6)
    t = bench.ship('target', 'lasher', at=(175, 0, 0), heading=90, throttle=.3)
    p, d = muzzle(w, 1, 'barrel 1')
    cast = _cast(bench)
    hit = False
    for local in ((8, 0, 8), (0, 0, 8), (8, 0, 5), (16, 0, 6), (-8, 0, 6), (0, 0, 3), (25, 0, 5)):
        aim = (bench.at(t, local) - p).normalized()
        hit, q, n = cast(p + aim * 30, aim)
        if hit and (q - p).length < 250:
            break
    if not hit:
        q, n = p + d * 160, -d
    print('BENCH ion hit', tuple(round(v, 1) for v in q), 'normal', tuple(round(v, 2) for v in n), hit, flush=True)
    rnd = random.Random('w4-emp')
    paths = [k_path for k_path in (__import__('swfx_guns').crawl_path(cast, q, n, rnd, steps=6, step_m=3.5)
                                   for _ in range(5))]
    with w.kit() as (ctx, k):
        k.guns.arm(ctx, 1, 'ioncannon')
        recs = k.guns.fire_weapon(ctx, 1, 1, .6, target=to_copy(w, q))
        hit_frame = recs[0]['hit_frame']
        k.guns.emp_arcs(ctx, hit_frame, [[to_copy(w, pt) for pt in path] for path in paths], frames=8)
    print('BENCH ion fired', [(r['frame'], r['hit_frame']) for r in recs], 'arc points',
          [len(pp) for pp in paths], flush=True)
    return q


def wolf_ion(bench):
    """The Wolf (P)'s ion cannon on a Lasher: the 0.5 s charge glow, the jagged blue bolt
    crackling across, and the strike."""
    _wolf_ion(bench)
    bench.camera((95, -150, 45), (95, 0, 8), 32)


def wolf_ion_charge(bench):
    """The ion cannon's 0.5 s charge-up close: a blue glow gathering at the muzzle, then the
    shot (frame 13)."""
    _wolf_ion(bench)
    bench.camera((30, -25, 16), (17, 0, 10), 40)


def wolf_ion_arcs(bench):
    """The ion strike close up: EMP arcs crawling over the Lasher's hull, flickering."""
    q = _wolf_ion(bench)
    bench.camera((q.x - 28, q.y - 30, q.z + 14), (q.x, q.y, q.z), 40)


def wolf_blasters(bench):
    """Blasters: the Wolf (P)'s red mining blaster (far) and the merchant Wolf's blue heavy
    blaster (near): a white-hot core in a coloured corona that lights the hull it leaves."""
    wp = bench.ship('wolf-p', 'wolf-p', at=(0, 45, 0), throttle=.6)
    wm = bench.ship('wolf-m', 'wolf', at=(0, -45, 0), throttle=.6)
    with wp.kit() as (ctx, k):
        k.guns.arm(ctx, 2, 'miningblaster')
        k.guns.fire_weapon(ctx, 2, 1, 1.2)
    with wm.kit() as (ctx, k):
        k.guns.arm(ctx, 4, 'heavyblaster')
        k.guns.fire_weapon(ctx, 4, 2, 1.2)
    bench.camera((90, -130, -15), (100, 0, 0), 30)     # both are chin mounts: from below


def colossus_ac(bench):
    """The Colossus Mk.III's port light autocannon (twin barrels alternating, a green-white
    shell every 0.5 s) beside its port light MG (warm streaks in bursts): two families side by
    side."""
    c = bench.ship('colossus3', 'colossus-mk3', throttle=.3)
    with c.kit() as (ctx, k):
        k.guns.arm(ctx, 8, 'lightac')
        k.guns.arm(ctx, 1, 'lightmg')
        k.guns.fire_weapon(ctx, 8, 1, 2.0)
        k.guns.fire_weapon(ctx, 1, 1, 2.0)
    bench.camera((119, 18, 48), (55, 52, 12), 35)      # side-on to the autocannon's first 100 m


def hound_vulcan(bench):
    """A Vulcan's braid: twin barrels alternating at 20 rounds/s, the two streams crossing so the
    stream zig-zags. On the Hound (P)'s twin turret as a stand-in (no Vulcan ship is built)."""
    h = bench.ship('hound', 'hound-p', throttle=.4)
    with h.kit() as (ctx, k):
        k.guns.arm(ctx, 2, 'vulcan')
        k.guns.fire_weapon(ctx, 2, 1, 1.0)
    bench.camera((-50, 30, 130), (-59, 42, 8), 35)     # from above: the barrels sit side by side


def train(bench):
    """Turret training: Lasher A (own hull) trains its bow and port guns onto a point up and off
    to port and fires on it, its flash along its rounds; Lasher B, the same build, keeps its
    rest pose. Prints each trained gun's aim error (degrees) and B's untouched pivot."""
    from mathutils import Vector
    import swfx_guns
    a = bench.ship('lasher-a', 'lasher', at=(0, 40, 0), throttle=.6, own_hull=True)
    b = bench.ship('lasher-b', 'lasher', at=(0, -40, 0), throttle=.6, own_hull=True)
    goal = Vector((220, 160, 60))
    with a.kit() as (ctx, k):
        for t in (1, 3):
            k.guns.arm(ctx, t, 'lightmg')
            k.guns.fire_weapon(ctx, t, 4, 1.0, target=to_copy(a, goal))
    for t in (1, 3):
        p, d = muzzle(a, t, 'barrel 1')
        err = math.degrees(d.angle(goal - p))
        print(f'BENCH train lasher-a t{t} aim error {err:.3f} deg', flush=True)
        assert err < .5, err
    piv_b, _ = swfx_guns.trainers(b.object('SWFX anchor turret 3 barrel 1'))
    print('BENCH train lasher-b t3 pivot z', round(math.degrees(piv_b.rotation_euler.z), 3), flush=True)
    bench.camera((150, -120, 70), (110, 60, 20), 30)


TESTS = {
    'survey': dict(builds=SURVEY_BUILDS, frames=[1], note='mount survey (check only)', setup=survey),
    'train': dict(builds=['lasher'], frames=[6, 12, 18, 24], setup=train,
                  note='Turret training: Lasher A (near) trains bow and port guns onto a point up to port; '
                       'Lasher B keeps its rest pose.'),
    'hellbore': dict(builds=['manticore-p'], frames=[2, 3, 5, 8, 12, 16], setup=hellbore,
                     note='Manticore (P) Hellbore, one shot: huge lingering orange flash, slow orange-white slug '
                          'in its glow (frames 2-16).'),
    'manticore-secondaries': dict(builds=['manticore-p'], frames=[4, 8, 12, 18], setup=manticore_secondaries,
                                  note='Manticore (P): light needler (bow, long white needles in 15-round bursts) '
                                       'and port light assault gun (orange HE shells).'),
    'lasher-mg': dict(builds=['lasher'], frames=[10, 16, 22, 28], setup=lasher_mg,
                      note='Light MGs: Lasher A (far) at game cadence; Lasher B (near) on the Ammo Feeder, '
                           'guns amber, double rate.'),
    'lasher-close': dict(builds=['lasher'], frames=[8, 10, 13, 18], setup=lasher_close,
                         note='Lasher B bow gun close, Ammo Feeder on: amber barrel glow, small warm flash, streaks.'),
    'wolf-ion': dict(builds=['wolf-p', 'lasher'], frames=[7, 13, 15, 18], setup=wolf_ion,
                     note='Wolf (P) ion cannon on a Lasher: charge glow (7), fire (13), jagged bolt, strike.'),
    'wolf-ion-charge': dict(builds=['wolf-p', 'lasher'], frames=[5, 9, 12, 13], setup=wolf_ion_charge,
                            note='Ion cannon charge-up close: the blue glow gathering at the muzzle, then the shot.'),
    'wolf-ion-arcs': dict(builds=['wolf-p', 'lasher'], frames=[18, 20, 23], setup=wolf_ion_arcs,
                          note='Ion strike close: EMP arcs crawling over the Lasher hull, flickering.'),
    'wolf-blasters': dict(builds=['wolf-p', 'wolf'], frames=[3, 6, 9, 12], setup=wolf_blasters,
                          note='Mining blaster (far, red) and heavy blaster (near, blue): white core, '
                               'coloured corona, lighting the hull.'),
    'colossus-ac': dict(builds=['colossus-mk3'], frames=[2, 8, 14, 26], setup=colossus_ac,
                        note='Colossus Mk.III port light autocannon (green-white shells) beside a light MG '
                             '(warm streaks).'),
    'hound-vulcan': dict(builds=['hound-p'], frames=[4, 8, 12, 16], setup=hound_vulcan,
                         note='Vulcan braid (stand-in on the Hound (P) twin mount): two barrels alternating, '
                              'streams crossing.'),
}
