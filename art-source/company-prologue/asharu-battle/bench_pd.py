"""Bench tests for W2, point defence (asharu-effects-plan.md): MG streams walking onto Harpoons
(the S5 screen), a PD laser against Swarmers, the Manticore's canister flak, and flares.

Each test plans the missiles first (swfx_missile_paths), lets point defence pick its kills
(swfx_pd), and then keys both ships, each inside its own ship.kit().
"""
import json

import bench_missiles


def harpoon_screen(bench):
    """S5's missile screen, close on the Lasher: her three MGs hose at the incoming salvo."""
    bench_missiles.harpoon_lane(bench)
    l = bench.ships[1]
    bench.camera(tuple(bench.at(l, (-170, -290, 105))), tuple(bench.at(l, (190, 15, 0))), 30)


def swarmer_laser(bench):
    """A Kite (P)'s four Swarmers weave in on the merchant Wolf's port side; her PD lasers snap
    onto two and burn them."""
    import swfx_missile_paths as mp
    import swfx_pd as pd
    w = bench.ship('wolf', 'wolf', at=(0, 0, 0), heading=90, throttle=.4)
    k = bench.ship('kite', 'kite-p', at=(-950, 40, 30), heading=0, throttle=.7)
    target = bench.at(w, (2, 9, 3))
    with k.kit() as (ctx, kit):
        starts = [(*mp.tube(k, ctx, 2, f'tube {i}'), f't2_{i}') for i in (1, 2, 3, 4)]
    flights = mp.salvo('swarmer', starts, target, mp.space_of(k), frames=[3, 5, 7, 9], spread_m=3.0, seed=11,
                       target_name='wolf')
    with w.kit() as (ctx, kit):
        guns = [pd.gun(w, ctx, t, 'barrel 1', 'laser', range_m=600.0) for t in (2, 3)]
    eng = pd.screen(flights, guns, kills=2, seed=5)
    with w.kit() as (ctx, kit):
        for r in pd.key_screen(ctx, eng, mp.space_of(w)):
            print("BENCH pd keyed", json.dumps(r, default=str), flush=True)
    with k.kit() as (ctx, kit):
        kit.guns.launch_flights(ctx, flights)
    bench_missiles.report(flights, eng, target)
    bench.camera((-420, -520, 200), (-260, 0, 0), 30)


def flak(bench):
    """The merchant Wolf's Harpoons into the Manticore (P): her canister flak throws a ring of
    bursts and kills two of the three crossing it."""
    import swfx_missile_paths as mp
    import swfx_pd as pd
    m = bench.ship('manticore', 'manticore-p', at=(0, 0, 0), heading=0, throttle=.35)
    w = bench.ship('wolf', 'wolf', at=(1500, -150, 60), heading=175, throttle=.5)
    target = bench.at(m, (30, 0, 6))
    with w.kit() as (ctx, kit):
        starts = [(*mp.tube(w, ctx, t, tag), lab) for t, tag, lab in
                  ((5, 'tube 1', 't5_1'), (6, 'tube 1', 't6_1'), (5, 'tube 2', 't5_2'))]
    flights = mp.salvo('harpoon', starts, target, mp.space_of(w), frames=[2, 5, 8], spread_m=6.0, seed=3,
                       target_name='manticore', width=.14)
    arrive = min(fl.end['frame'] for fl in flights)
    bursts = pd.plan_flak(tuple(bench.at(m, (10, 0, 0))), flights, radius_m=230.0, ring=10,
                          window=(arrive - 22, arrive + 4), kills=2, seed=9)
    with m.kit() as (ctx, kit):
        pd.key_flak(ctx, bursts, mp.space_of(m), launch_from=tuple(bench.at(m, (5, 0, 14))))
    with w.kit() as (ctx, kit):
        kit.guns.launch_flights(ctx, flights)
    bench_missiles.report(flights, [], target)
    print('BENCH flak bursts', sorted(b.frame for b in bursts), flush=True)
    bench.camera(tuple(bench.at(m, (-120, -620, 230))), tuple(bench.at(m, (150, 0, 0))), 30)


def flares(bench):
    """The Colossus throws flares from her four launchers as two Swarmers close; one seeker
    takes a flare and peels off onto it, the other holds on."""
    import swfx_missile_paths as mp
    import swfx_pd as pd
    c = bench.ship('colossus', 'colossus', at=(0, 0, 0), heading=0, throttle=.3)
    k = bench.ship('kite', 'kite-p', at=(900, -500, 80), heading=150, throttle=.7)
    target = bench.at(c, (10, -20, 6))
    with k.kit() as (ctx, kit):
        starts = [(*mp.tube(k, ctx, 2, f'tube {i}'), f't2_{i}') for i in (1, 2)]
    flights = mp.salvo('swarmer', starts, target, mp.space_of(k), frames=[3, 6], spread_m=4.0, seed=21,
                       target_name='colossus')
    arrive = min(fl.end['frame'] for fl in flights)
    fl_out = pd.plan_flares(pd.flare_anchors(c), arrive - 26, per_anchor=2, interval=5, seed=4)
    pairs = pd.decoy(flights, fl_out, arrive - 12, count=1, seed=2)
    with c.kit() as (ctx, kit):
        pd.key_flares(ctx, fl_out, mp.space_of(c))
    with k.kit() as (ctx, kit):
        kit.guns.launch_flights(ctx, flights)
    bench_missiles.report(flights, [], target)
    print('BENCH flares from', arrive - 26, 'decoyed', [p[0].label for p in pairs], flush=True)
    bench.camera(tuple(bench.at(c, (360, -520, 260))), tuple(bench.at(c, (120, -60, 0))), 30)


TESTS = {
    'harpoon-screen': dict(
        builds=['manticore-p', 'lasher'], frames=[98, 104, 110, 118],
        note="S5's screen, close on the Lasher: three MG streams walk onto the salvo; two Harpoons pop short, "
             'two get through to her bow.',
        setup=harpoon_screen),
    'swarmer-laser': dict(
        builds=['wolf', 'kite-p'], frames=[32, 38, 41, 50],
        note="Four Swarmers weave in on the merchant Wolf; her red PD lasers snap on, heat two white and pop them.",
        setup=swarmer_laser),
    'flak': dict(
        builds=['manticore-p', 'wolf'], frames=[56, 62, 66, 72],
        note='Canister flak: a ring of small bursts around the Manticore (P) kills two of three incoming Harpoons.',
        setup=flak),
    'flares': dict(
        builds=['colossus', 'kite-p'], frames=[30, 38, 44, 49],
        note='The Colossus throws white-orange flares; one Swarmer peels off onto a flare, the other holds on.',
        setup=flares),
}
