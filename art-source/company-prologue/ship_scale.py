"""One sizing basis for every cinematic hull: metres per unit from what the hull holds.

A sprite says nothing about size. A hull's real size is the volume its contents
need, divided among the space a ship of its class cannot give to them:

    contents  = max crew x HABITABLE_M3_PER_PERSON
              + cargo x M3_PER_CARGO + fuel x M3_PER_FUEL
              + hangar volume for its fighter bays (from the fighters' own sizes)
    enclosed  = contents / (1 - machinery share of its class)
    metres per unit = cube root(enclosed m3 / measured enclosed units3)

The measured volume is the model's own: the union of the solids a crew could be
inside (pressure hull, hangars, holds), measured on every build by the hull's
driver and passed in as units3. Before a model exists, estimate_units3() gives a
rough figure from the sprite's plan area. The constants are shared by every hull
and justified in ship-scale.md; change them there and here together, never per
hull.

Run `python ship_scale.py` for the side-by-side table of the hulls registered
in HULLS below. A driver calls solve() and stores record() under 'scale' in its
geometry.json.
"""
import json
import math
import sys

# Habitable volume per person at max crew: berths, messes, work stations,
# medical, recreation and the passages between them, gross of circulation.
# ISS: 388 m3 habitable for a crew of 7, about 55 m3 each (NASA). Naval
# warships give their crews about 15-30 m3 each, on voyages of weeks with port
# calls; a Starsector crew lives aboard for months. 55 is the spaceflight figure.
HABITABLE_M3_PER_PERSON = 55.0
HABITABLE_RANGE = (40.0, 70.0)   # cramped warship standard to roomy long-haul
# One cargo unit is taken as one tonne of goods: a stowage factor of about
# 1.5 m3/t for mixed palletised cargo, plus 30% for hold access and handling.
M3_PER_CARGO = 2.0
# One fuel unit is taken as one tonne at about 0.8 t/m3, plus 20% tankage.
M3_PER_FUEL = 1.5
# Hangar volume per embarked craft, as a multiple of its bare length x span x
# height box. Nimitz: a 210 x 33 x 8 m hangar (about 55,000 m3) holds about
# 25-30 aircraft, roughly 2,000 m3 each against an F/A-18's 17 x 13.6 x 4.7 m
# box (1,090 m3), so about 1.8x. Space servicing and fighter ordnance
# handling add to it: 2.5x.
HANGAR_BOX_FACTOR = 2.5
SPARES_PER_WING = 1

# Share of the enclosed volume no crew, cargo, fuel or fighter can use, by class:
# armour, drive/reactor/flux plant, weapons and magazines, and structure,
# tanks, stores, life support and trunks. Real warships give machinery spaces
# about 20-25% of their internal volume; drive plant shrinks as a share with
# size, armour and weapons grow with class, and a freighter carries little of
# either.
CLASS_SHARES = {
    'frigate':   {'armour': .06, 'drive': .22, 'weapons': .06, 'structure': .12},
    'destroyer': {'armour': .08, 'drive': .20, 'weapons': .07, 'structure': .12},
    'cruiser':   {'armour': .10, 'drive': .18, 'weapons': .08, 'structure': .12},
    'capital':   {'armour': .14, 'drive': .16, 'weapons': .09, 'structure': .12},
    'freighter': {'armour': .03, 'drive': .15, 'weapons': .02, 'structure': .12},
}

# Fighters in metres at their own scales (fighters-geometry, broadsword-geometry;
# Hoplon and Piranha from their sprites at 0.50 m/px until they are modelled).
FIGHTERS_M = {
    'broadsword': (15.5, 14.1, 3.3), 'hoplon': (18.0, 19.0, 4.0), 'piranha': (17.0, 15.0, 3.3),
    'longbow': (15.0, 14.4, 2.6), 'thunder': (18.5, 10.8, 2.6), 'dagger': (16.4, 12.1, 2.3),
    'talon': (12.1, 12.1, 2.5), 'wasp': (9.8, 8.1, 2.4),
}

# Plan-area estimate before a model exists: volume = k x (sprite alpha area)^1.5,
# k measured on the built models (Hound 0.188, Cerberus/Second Wind 0.169,
# Mudskipper 0.178, Legion 0.117).
ESTIMATE_K = {'frigate': .178, 'destroyer': .16, 'cruiser': .14, 'capital': .117, 'freighter': .178}


def hull_class(hull_size, designation=''):
    """Starsector hullSize and designation -> one of CLASS_SHARES."""
    d = designation.lower()
    if any(w in d for w in ('freighter', 'transport', 'tanker', 'liner', 'shuttle')):
        return 'freighter'
    return {'FRIGATE': 'frigate', 'DESTROYER': 'destroyer', 'CRUISER': 'cruiser',
            'CAPITAL_SHIP': 'capital', 'CAPITAL': 'capital'}[hull_size.upper()]


def hangar_m3(wings):
    """wings: [(fighter id, craft per wing), ...] -> hangar m3 for the whole complement."""
    total = 0.0
    for fid, n in wings:
        l, s, h = FIGHTERS_M[fid]
        total += (n+SPARES_PER_WING)*l*s*h*HANGAR_BOX_FACTOR
    return total


def estimate_units3(sprite_alpha_area, cls):
    return ESTIMATE_K[cls]*sprite_alpha_area**1.5


def solve(units3, crew_max, cargo, fuel, cls, wings=(), habitable=HABITABLE_M3_PER_PERSON):
    shares = CLASS_SHARES[cls]
    machinery = sum(shares.values())
    parts = {'crew': crew_max*habitable, 'cargo': cargo*M3_PER_CARGO, 'fuel': fuel*M3_PER_FUEL,
             'hangar': hangar_m3(wings)}
    contents = sum(parts.values())
    enclosed = contents/(1-machinery)
    return {'contents_m3': {k: round(v) for k, v in parts.items()}, 'contents_total_m3': round(contents),
            'machinery_share': round(machinery, 3), 'enclosed_m3': round(enclosed),
            'metres_per_unit': (enclosed/units3)**(1/3)}


def record(units3, crew_max, cargo, fuel, cls, wings=(), length_units=None, volume_source=''):
    """The 'scale' block a geometry.json stores: inputs, constants, result and its range."""
    mid = solve(units3, crew_max, cargo, fuel, cls, wings)
    lo, hi = (solve(units3, crew_max, cargo, fuel, cls, wings, h)['metres_per_unit'] for h in HABITABLE_RANGE)
    mpu = mid['metres_per_unit']
    out = {
        'method': 'ship_scale.py: contents (crew, cargo, fuel, hangars) over the class machinery share, '
                  'against the measured enclosed volume',
        'inputs': {'enclosed_units3': round(units3), 'volume_source': volume_source, 'hull_class': cls,
                   'crew_max': crew_max, 'cargo': cargo, 'fuel': fuel, 'wings': [list(w) for w in wings]},
        'constants': {'habitable_m3_per_person': HABITABLE_M3_PER_PERSON, 'm3_per_cargo': M3_PER_CARGO,
                      'm3_per_fuel': M3_PER_FUEL, 'hangar_box_factor': HANGAR_BOX_FACTOR,
                      'spares_per_wing': SPARES_PER_WING, 'class_shares': CLASS_SHARES[cls]},
        **{k: v for k, v in mid.items() if k != 'metres_per_unit'},
        'solved_metres_per_unit': round(mpu, 3),
        'range': {f'habitable_{int(HABITABLE_RANGE[0])}': round(lo, 3),
                  f'habitable_{int(HABITABLE_RANGE[1])}': round(hi, 3)},
        'enclosed_m3_per_person_at_max_crew': round(mid['enclosed_m3']/crew_max),
    }
    if length_units:
        out['length_m'] = round(length_units*mpu, 1)
    return out


# The hulls compared side by side. units3 is the model's measured enclosed
# volume where a model exists (its own driver's figure), else a plan-area estimate.
HULLS = {
    'Legion (XIV)': dict(units3=1678689, crew_max=1500, cargo=300, fuel=300, cls='capital',
                         wings=[('broadsword', 3)]*2+[('hoplon', 2)]*2, length_units=294.9,
                         volume_source='legion-geometry: union of torso, spines, dome, arms, hangars, transfer galleries'),
    'Hound (A)': dict(units3=28751, crew_max=40, cargo=75, fuel=30, cls='freighter', length_units=69.1,
                      volume_source='hound-geometry: pressure hull (cargo body, deckhouse, engine spar ...)'),
    'Second Wind (Cerberus)': dict(units3=77000, crew_max=50, cargo=100, fuel=40, cls='freighter',
                                   length_units=101.5, volume_source='second-wind-pipeline.md: main hull'),
    'Onslaught': dict(units3=estimate_units3(65541, 'capital'), crew_max=1500, cargo=300, fuel=400, cls='capital',
                      length_units=352, volume_source='estimate: k 0.117 x sprite area 65,541^1.5 (no model)'),
    'Lasher': dict(units3=estimate_units3(3924, 'frigate'), crew_max=50, cargo=40, fuel=25, cls='frigate',
                   length_units=94, volume_source='estimate: k 0.178 x sprite area 3,924^1.5 (no model)'),
}


def table():
    rows = []
    for name, h in HULLS.items():
        r = record(**h)
        rows.append((name, h['cls'], h['crew_max'], round(h['units3']), r['enclosed_m3'], r['solved_metres_per_unit'],
                     r['range'], r['length_m'], r['enclosed_m3_per_person_at_max_crew']))
    return rows


if __name__ == '__main__':
    if '--json' in sys.argv:
        print(json.dumps({n: record(**h) for n, h in HULLS.items()}, indent=2))
    else:
        print(f"{'hull':24} {'class':9} {'crew':>5} {'units3':>9} {'enclosed m3':>11} {'m/u':>6} "
              f"{'range':>12} {'length m':>8} {'m3/person':>9}")
        for n, c, crew, u, e, m, rg, l, pp in table():
            lo, hi = rg.values()
            print(f'{n:24} {c:9} {crew:5} {u:9} {e:11} {m:6.3f} {lo:5.3f}-{hi:5.3f} {l:8.1f} {pp:9}')
