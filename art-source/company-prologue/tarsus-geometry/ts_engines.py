"""Tarsus drives: two main drive units and four auxiliaries at the stern.

tarsus.ship gives six LOW_TECH slots at angle 180: mains (-78, 19) and
(-78, -18), width 20; auxiliaries (-76, 26) w6, (-76, 11) w8, (-76, -11) w8,
(-76, -25) w6. The auxiliaries' widths overlap the mains' in plan, so they
are hung in a row beneath them (the sprite shows two big copper cans only
from above). Each drive is a whole unit -- a can sunk into the drive block
and a bell at the slot -- never a nozzle poking out of the hull."""
from ts_kit import revolve_x, prism, section
import ts_hull as H

ENGINES = [(-78, 19, 20, 180), (-76, 26, 6, 180), (-76, 11, 8, 180),
           (-76, -11, 8, 180), (-76, -25, 6, 180), (-78, -18, 20, 180)]
MAIN_Z = 1.5
AUX_Z = -10.5
ANCHORS = {}   # index -> ((x, y, z) of the exhaust anchor, exhaust direction)


def _z(w):
    return MAIN_Z if w >= 20 else AUX_Z


def build(kit):
    # Drive block: the stern machinery the drive units are sunk into (owned here, not by ts_hull,
    # so the whole drive section the boarded state shoots out lives in one module).
    drive = prism(kit, 'Hull drive block',
                  H.plan([(H.DRIVE_FRONT, 38.0), (-66.0, 35.0), (H.DRIVE_BACK, 33.0)]),
                  section(H.DRIVE_KEEL, H.DRIVE_DECK, 3, 3), kit.grey)
    kit.hull.append(drive)
    for i, (x, yy, w, a) in enumerate(ENGINES, 1):
        z = _z(w)
        r = w/2
        main = w >= 20
        # Drive can: from inside the block out to the bell's throat.
        x0 = H.DRIVE_FRONT-6 if main else -64.0
        xt = x+4.0                       # throat plane, ahead of the slot
        revolve_x(kit, f'Engine {i} drive can', [(x0, r*.92), (xt-2, r*.92), (xt-.5, r), (xt, r*.95),
                                               (xt, .01), (x0, .01)], yy, z, kit.copper if main else kit.rust,
                  n=32 if main else 20)
        # Aft-facing bell: flares from the throat to the slot's mouth.
        revolve_x(kit, f'Engine {i} aft-facing bell', [(xt, r*.72), (x, r*.98), (x, r*.9), (xt-.3, r*.58)], yy, z, kit.steel, n=32 if main else 20)
        revolve_x(kit, f'Engine {i} mouth lip', [(x+.25, r*.9), (x+.25, r), (x-.35, r), (x-.35, r*.9)],
                  yy, z, kit.grey, n=32 if main else 20)
        revolve_x(kit, f'Engine {i} dark throat', [(xt-.4, r*.6), (xt-.4, .01), (xt+.5, .01), (xt+.5, r*.6)],
                  yy, z, kit.dark, n=32 if main else 20)
        e = kit.empty(f'Engine {i} exhaust anchor', (x-.4, yy, z), a)
        ANCHORS[i] = ((x-.4, yy, z), (-1.0, 0.0, 0.0))
    # The auxiliaries hang in a skirt under the mains' cans.
    prism(kit, 'Hull drive skirt', H.plan([(-62.0, 31.0), (-70.0, 30.0), (-72.5, 28.5)]),
          section(AUX_Z-5.0, H.DRIVE_KEEL+1, 2, .5), kit.grey)
