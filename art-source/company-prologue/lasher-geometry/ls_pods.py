"""The four weapon pods: two gun pods on the neck, two missile pods outboard.

Read off lasher_base.png: each pod is a gunmetal block whose forward face is
forked into two prongs (row 32 for the gun pods, rows 46-47 for the missile
pods), the hardpoint weapon sitting in the gap between them. The gap is
centred on the slot's Y, so the asymmetric slots (WS 002 at Y +16, WS 003
at Y -15) give slightly different prongs port and starboard.
- Gun pods (rows 32-44, cols 13-23): X 17..29 with prongs to X 31, Y from
  the neck (10.5) to 21. Striped dark vent bands on top.
- Missile pods (rows 46-56, cols 1-13): X 5..15.5 with prongs to X 17,
  Y 20.5..33, sitting on the wing body.
"""
from ls_kit import prism, section
from ls_hull import ccw

GUN_POD = dict(x0=17, x1=27.6, prong=29.3, inner=9.5, outer=21, keel=-4.5, deck=6.2)
MISSILE_POD = dict(x0=5, x1=13.6, prong=15.2, inner=20.5, outer=33, keel=-3.5, deck=5.2)
GAP = 1.6  # half-width of the gap the weapon sits in


def pod(kit, name, d, slot_y, s):
    """One pod on side s (+1 port, -1 starboard) with a forward gap centred on |slot_y|."""
    y0, y1 = d['inner'], d['outer']
    body = ccw([(d['x1'], s*y0), (d['x1'], s*y1), (d['x0'], s*y1), (d['x0'], s*y0)])
    prof = section(d['keel'], d['deck'], 1.0, 1.0)
    ob = prism(kit, f'{name} body', body, prof, kit.steel, .12)
    g0, g1 = abs(slot_y)-GAP, abs(slot_y)+GAP
    prongs = []
    for k, (a, b) in enumerate(((y0, g0), (g1, y1)), 1):
        plan = ccw([(d['prong'], s*(a+.4)), (d['prong'], s*(b-.4)), (d['x1']-.5, s*b), (d['x1']-.5, s*a)])
        prongs.append(prism(kit, f'{name} prong {k}', plan, section(d['keel']+1.2, d['deck']-.6, .6, .8),
                            kit.steel, .1))
    return ob, prongs


def build(kit):
    import ls_mounts
    slots = {s['id']: s for s in ls_mounts.SLOTS}
    for side, s in (('port', 1), ('starboard', -1)):
        gun_slot = slots['WS 002' if s > 0 else 'WS 003']['location'][1]
        mis_slot = slots['WS 001' if s > 0 else 'WS 004']['location'][1]
        body, _ = pod(kit, f'Pod {side} gun', GUN_POD, gun_slot, s)
        kit.hull.append(body)
        for k, x in enumerate((19.5, 21.5, 25.5), 1):  # striped vent bands (cols 16-21)
            kit.box(f'Pod {side} gun vent {k}', (x, s*18.5, GUN_POD['deck']+.02), (.9, 3.8, .25), kit.dark, 0)
        kit.box(f'Pod {side} gun spine plate', (23, s*12.6, GUN_POD['deck']+.25), (10, 2.2, .5), kit.orange, .1)
        body, _ = pod(kit, f'Pod {side} missile', MISSILE_POD, mis_slot, s)
        kit.hull.append(body)
        kit.box(f'Pod {side} missile vent', (8, s*27, MISSILE_POD['deck']+.02), (3.4, 9, .25), kit.dark, 0)
        kit.box(f'Pod {side} missile hatch', (11.6, s*30.8, MISSILE_POD['deck']+.2), (3.5, 2.6, .4), kit.steel, .08)
