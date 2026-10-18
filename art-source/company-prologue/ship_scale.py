"""One sizing basis for every cinematic hull: metres per unit from what the hull holds.

A sprite says nothing about size. A hull's real size is the volume its contents
need, divided among the space a ship of its class cannot give to them:

    contents  = max crew x HABITABLE_M3_PER_PERSON x the class's habitable factor
              + cargo x CARGO_M3_PER_UNIT + fuel x FUEL_M3_PER_UNIT
              + hangars (from the fighters' own sizes) + boat bays
    enclosed  = contents / (1 - machinery share of its class)
    metres per unit = cube root(enclosed m3 / measured enclosed units3)

Enclosed volume is defined one way for every hull: the union of the pressure
hull with its holds, hangars and boat bays, never drive pods, engine cans,
armour laid over the hull or open structure (SOLIDS below, measured by
ship_scale_measure.py inside the hull's .blend). Before a model exists,
estimate_units3() gives a rough figure from the sprite's plan area.

Crew room is the dominant term and comes from the mod's own deck program, the
same for every crew size; capitals take the generous end (owner's choice,
2026-10-18: option C, capital factor 1.45). All constants are justified in
ship-scale.md; change them there and here together, never per hull.

Run `python ship_scale.py` for the side-by-side table of the hulls in HULLS.
A driver calls record(name) and stores it under 'scale' in its geometry.json.
"""
import json
import sys

# Habitable volume per person at max crew: berths, messes, work stations,
# medical, recreation and the passages between them, gross of circulation.
# The mod's deck program (DeckSizing.programFor, the program the game's deck
# maps are drawn from) gives a 40-crew frigate 2,427 m2 of rooms, about 61 m2 a
# person: x1.40 circulation x3.2 m decks = 273 m3. The Hound (A)'s fittings
# (doors, airlock, viewports, rails, mannequin) were built at 0.88 m/unit,
# which on its old pressure-hull figure needed 328. 300 is between them, for
# every crew size: no economy of scale. Far above naval norms (15-30 m3) and
# the ISS (55): a Starsector crew lives aboard for months.
HABITABLE_M3_PER_PERSON = 300.0
HABITABLE_RANGE = (273.0, 328.0)

# Per class: the habitable factor on that figure, the share of the enclosed
# volume no crew, cargo, fuel or craft can use (armour; drive, reactor and flux
# plant; weapons and magazines; structure, tanks, stores, life support and
# trunks), and the hangar volume per craft as a multiple of its bare
# length x span x height box.
# Machinery: real warships give machinery spaces 20-25% of their internal
# volume; drive plant shrinks as a share with size, while armour and weapons
# grow with class. Hangar: a Nimitz's 210 x 33 x 8 m hangar (about 55,000 m3)
# holds 25-30 aircraft, about 1.8x each F/A-18's box; servicing in space and
# ordnance handling add to it.
# "Capitals should feel like capital ships" (owner): a capital takes the
# generous end - crew room x1.45 (owner's choice, so capitals reach about 4x a
# frigate's length), hangars 3.0x, deep armour, big magazines.
CLASSES = {
    'frigate':   {'habitable_factor': 1.0, 'hangar_box_factor': 2.5,
                  'shares': {'armour': .06, 'drive': .22, 'weapons': .06, 'structure': .12}},
    'destroyer': {'habitable_factor': 1.05, 'hangar_box_factor': 2.6,
                  'shares': {'armour': .08, 'drive': .20, 'weapons': .07, 'structure': .12}},
    'cruiser':   {'habitable_factor': 1.1, 'hangar_box_factor': 2.8,
                  'shares': {'armour': .11, 'drive': .18, 'weapons': .09, 'structure': .12}},
    'capital':   {'habitable_factor': 1.45, 'hangar_box_factor': 3.0,
                  'shares': {'armour': .18, 'drive': .16, 'weapons': .12, 'structure': .12}},
    'freighter': {'habitable_factor': 1.0, 'hangar_box_factor': 2.5,
                  'shares': {'armour': .03, 'drive': .15, 'weapons': .02, 'structure': .12}},
}
# One cargo unit is taken as one tonne of goods: a stowage factor of about
# 1.5 m3/t for mixed palletised cargo, plus 30% for hold access and handling.
CARGO_M3_PER_UNIT = 2.0
# One fuel unit is taken as one tonne at about 0.8 t/m3, plus 20% tankage.
FUEL_M3_PER_UNIT = 1.5
SPARES_PER_WING = 1
# A boat bay holds one carried boat: the deck program's 16 x 10 m boat bay at
# 6 m clear height.
BOAT_BAY_M3 = 960.0

# Fighters in metres at their own scales (fighters-geometry, broadsword-geometry;
# Hoplon and Piranha from their sprites at 0.50 m/px until they are modelled).
FIGHTERS_M = {
    'broadsword': (15.5, 14.1, 3.3), 'hoplon': (18.0, 19.0, 4.0), 'piranha': (17.0, 15.0, 3.3),
    'longbow': (15.0, 14.4, 2.6), 'thunder': (18.5, 10.8, 2.6), 'dagger': (16.4, 12.1, 2.3),
    'talon': (12.1, 12.1, 2.5), 'wasp': (9.8, 8.1, 2.4),
    # The Shepherd's Borer drones: an assumption (4.4 m long, 4.0 m across the arms,
    # 1.8 m high) until a Borer is modelled.
    'borer': (4.4, 4.0, 1.8),
}

# Plan-area estimate before a model exists: volume = k x (sprite alpha area)^1.5,
# k measured on the built models under SOLIDS' definition (Hound 0.090, Second
# Wind 0.172, Legion 0.117). Treat an estimate as +-25% in volume (+-8% m/u).
ESTIMATE_K = {'frigate': .131, 'destroyer': .127, 'cruiser': .122, 'capital': .117, 'freighter': .131}

# Frigate-to-capital length: the game's sprite lengths (Hound 72, Lasher 96,
# Dominator 180, Legion 308, Onslaught 384 px) are a floor on the ratio, not the
# method. A capital under about 4x a frigate's length, or under its own sprite
# ratio, is flagged and never published silently.
CAPITAL_TO_FRIGATE_FLOOR = 4.0


def hull_class(hull_size, designation=''):
    """Starsector hullSize and designation -> one of CLASSES."""
    d = designation.lower()
    if any(w in d for w in ('freighter', 'transport', 'tanker', 'liner', 'shuttle')):
        return 'freighter'
    return {'FRIGATE': 'frigate', 'DESTROYER': 'destroyer', 'CRUISER': 'cruiser',
            'CAPITAL_SHIP': 'capital', 'CAPITAL': 'capital'}[hull_size.upper()]


def hangar_m3(wings, cls):
    """wings: [(fighter id, craft per wing), ...] -> hangar m3 for the whole complement."""
    total = 0.0
    for fid, n in wings:
        l, s, h = FIGHTERS_M[fid]
        total += (n+SPARES_PER_WING)*l*s*h*CLASSES[cls]['hangar_box_factor']
    return total


def estimate_units3(sprite_alpha_area, cls):
    return ESTIMATE_K[cls]*sprite_alpha_area**1.5


def machinery_share(cls):
    return sum(CLASSES[cls]['shares'].values())


def solve(h, habitable=HABITABLE_M3_PER_PERSON):
    c = CLASSES[h['cls']]
    parts = {'crew': h['crew_max']*habitable*c['habitable_factor'], 'cargo': h['cargo']*CARGO_M3_PER_UNIT,
             'fuel': h['fuel']*FUEL_M3_PER_UNIT, 'hangar': hangar_m3(h.get('wings', ()), h['cls']),
             'boat_bays': h.get('boat_bays', 0)*BOAT_BAY_M3}
    contents = sum(parts.values())
    enclosed = contents/(1-machinery_share(h['cls']))
    return {'contents_m3': {k: round(v) for k, v in parts.items()}, 'contents_total_m3': round(contents),
            'machinery_share': round(machinery_share(h['cls']), 3), 'enclosed_m3': round(enclosed),
            'metres_per_unit': (enclosed/h['units3'])**(1/3)}


def record(name):
    """The 'scale' block a geometry.json stores: inputs, constants, result, range and fittings."""
    h = HULLS[name]
    mid = solve(h)
    mpu = mid['metres_per_unit']
    lo, hi = (solve(h, x)['metres_per_unit'] for x in HABITABLE_RANGE)
    return {
        'method': 'ship_scale.py: contents (crew, cargo, fuel, hangars, boat bays) over the class machinery '
                  'share, against the measured enclosed volume',
        'inputs': {**{k: (round(v) if k == 'units3' else v) for k, v in h.items() if k != 'fittings_units'},
                   'volume_source': VOLUME_SOURCE[name]},
        'constants': {'habitable_m3_per_person': HABITABLE_M3_PER_PERSON, 'class': CLASSES[h['cls']],
                      'cargo_m3_per_unit': CARGO_M3_PER_UNIT, 'fuel_m3_per_unit': FUEL_M3_PER_UNIT,
                      'boat_bay_m3': BOAT_BAY_M3, 'spares_per_wing': SPARES_PER_WING},
        **{k: v for k, v in mid.items() if k != 'metres_per_unit'},
        'solved_metres_per_unit': round(mpu, 3),
        'range': {f'habitable_{int(HABITABLE_RANGE[0])}': round(lo, 3),
                  f'habitable_{int(HABITABLE_RANGE[1])}': round(hi, 3)},
        'enclosed_m3_per_person_at_max_crew': round(mid['enclosed_m3']/h['crew_max']),
        'length_m': round(h['length_units']*mpu, 1),
        'fittings_m': {k: [round(x*mpu, 2) for x in v] for k, v in h.get('fittings_units', {}).items()},
    }


# The hulls compared side by side. units3: measured under SOLIDS where a model
# exists, else a plan-area estimate. sprite_px is the .ship's sprite length.
# fittings_units are modelled openings in hull units.
HULLS = {
    'Legion (XIV)': dict(units3=1678689, crew_max=1500, cargo=300, fuel=300, cls='capital',
                         wings=[('broadsword', 3)]*2+[('hoplon', 2)]*2, length_units=294.9, sprite_px=308,
                         fittings_units={'personnel door h': [3.4*.61/.99], 'rail h': [1.8*.61/.99], 'transfer door w x h': [34, 10]}),
    'Onslaught': dict(units3=2538239, crew_max=1500, cargo=300, fuel=400, cls='capital',
                      length_units=351.7, sprite_px=384,
                      fittings_units={'bridge glazing band h': [1.65], 'bridge pane w': [1.29],
                                      'crown hatch d': [2.12], 'service hatch w x l': [1.06, 1.65]}),
    'Lasher': dict(units3=32793, crew_max=50, cargo=40, fuel=25, cls='frigate',
                   length_units=94.7, sprite_px=96,
                   fittings_units={'viewport w x h': [1.05, .84], 'bridge glazing band h': [1.05]}),
    'Hound (A)': dict(units3=13720, crew_max=40, cargo=75, fuel=30, cls='freighter', length_units=69.1, sprite_px=72,
                      fittings_units={'aft door w x h': [1.59, 2.27], 'airlock w x h': [1.93, 2.39]}),
    # The Shepherd's four Borers live in an open work bay (open to space above and
    # outboard), which is open structure and outside SOLIDS, so no hangar term is
    # charged against the enclosed volume; the bay is sized from the Borers in her
    # own geometry.json (scale.drone_bay).
    'Shepherd': dict(units3=42356, crew_max=30, cargo=100, fuel=40, cls='frigate', length_units=86.0,
                     sprite_px=92, fittings_units={'crew door w x h': [1.35, 2.4]}),
    'Second Wind (Cerberus)': dict(units3=78443, crew_max=50, cargo=100, fuel=40, cls='freighter', boat_bays=2,
                                   length_units=101.5, fittings_units={'boat bay length x clear h': [23.1, 9.1]}),
}
VOLUME_SOURCE = {'Legion (XIV)': 'measured (SOLIDS)', 'Hound (A)': 'measured (SOLIDS)',
                 'Second Wind (Cerberus)': 'measured (SOLIDS)', 'Onslaught': 'measured (SOLIDS)',
                 'Lasher': 'measured (SOLIDS)', 'Shepherd': 'measured (SOLIDS)'}
PUBLISHED = {'Legion (XIV)': .99, 'Hound (A)': .88, 'Second Wind (Cerberus)': .65, 'Lasher': .95, 'Onslaught': .85,
             'Shepherd': .78}
FRIGATES = ('Hound (A)', 'Lasher')

# What counts as enclosed volume, one way for every hull: the pressure hull with
# its holds, hangars and boat bays; never drive pods or engine cans, armour laid
# over the hull, or open structure. ship_scale_measure.py measures the union of
# these solids inside the hull's .blend.
SOLIDS = {
    'legion': {'blend': 'legion-geometry/legion-geometry.blend', 'step': 2.0,
               'include': lambda n: n in (
                   'Hull fore body', 'Hull aft body', 'Hull ventral keel', 'Hull dorsal spine', 'Hull tail spine',
                   'Bridge command dome', 'Wing port sponson', 'Wing starboard sponson', 'Wing port hangar',
                   'Wing starboard hangar', 'Wing port transfer gallery', 'Wing starboard transfer gallery')},
    'hound': {'blend': 'hound-geometry/hound-geometry.blend', 'step': .5,
              'include': lambda n: n in ('Hull cargo body', 'Hull shielded hold deckhouse', 'Hull engine spar',
                                         'Bridge capsule hull', 'Bridge capsule nose')},
    # The Lasher's .blend is a build output (build/lasher/lasher.blend), never tracked;
    # its driver re-measures these solids on every build. The engine nacelles are
    # drive pods and the gun and missile pods are weapons: both excluded.
    'lasher': {'blend': '../../build/lasher/lasher.blend', 'step': .5,
               'include': lambda n: n in ('Hull bow head', 'Hull nose cap', 'Hull spine', 'Hull wing body',
                                          'Hull ventral keel')},
    # The Onslaught's .blend is a build output (onslaught-geometry/onslaught-geometry.blend,
    # ignored); its driver re-measures these solids on every build. Armour bands and the
    # plating laid over the flanks, the drive pods and engine cans are excluded; the
    # stern machinery wells are open to space and already cut out of the aft body.
    'onslaught': {'blend': 'onslaught-geometry/onslaught-geometry.blend', 'step': 2.0,
                  'include': lambda n: n in (
                      'Hull prow', 'Hull core', 'Hull port arm', 'Hull starboard arm', 'Hull aft body', 'Hull tail',
                      'Hull port aft leg', 'Hull starboard aft leg', 'Hull port waist', 'Hull starboard waist',
                      'Bridge spine', 'Bridge command dome', 'Bridge aft dome', 'Hull port arm gallery',
                      'Hull starboard arm gallery', 'Hull prow gallery', 'Hull core terrace',
                      'Hull core inner terrace', 'Hull belly')},
    # The Shepherd's port drone bay is an open work bay (owner, pass 3): open to
    # space above and outboard, so its air is open structure, never enclosed. Its
    # tubs below the deck and the forward workshop block are counted; the gantry
    # drum, outriggers and drive pods are not.
    'shepherd': {'blend': 'shepherd-geometry/shepherd-geometry.blend', 'step': .5,
                 'include': lambda n: n in ('Hull central body', 'Hull port spine', 'Hull aft machinery block',
                                            'Bridge hull', 'Drone bay aft hangar pod',
                                            'Drone bay forward hangar pod', 'Drone bay forward service block')},
    'second-wind': {'blend': 'second-wind-geometry/second-wind-geometry.blend', 'step': .5,
                    'include': lambda n: n in ('Main hull - level datum Z0', 'Command deck housing',
                                               'Spine waist housing')},
}


def table():
    rows = {n: record(n) for n in HULLS}
    out = []
    for n, r in rows.items():
        h = HULLS[n]
        ratios = {f: r['length_m']/rows[f]['length_m'] for f in FRIGATES}
        sprite = {f: h['sprite_px']/HULLS[f]['sprite_px'] for f in FRIGATES if h.get('sprite_px')}
        flag = h['cls'] == 'capital' and any(
            ratios[f] < CAPITAL_TO_FRIGATE_FLOOR or ratios[f] < sprite.get(f, 0) for f in FRIGATES)
        out.append((n, r, ratios, sprite, flag))
    return out


if __name__ == '__main__':
    if '--json' in sys.argv:
        print(json.dumps({n: record(n) for n in HULLS}, indent=2))
        sys.exit()
    print(f"{'hull':24} {'m/u':>6} {'range':>12} {'now':>5} {'length':>7} {'x Hound':>8} {'x Lasher':>8} "
          f"{'sprite H/L':>10} {'m3/pers':>7}  fittings")
    for n, r, ratios, sprite, flag in table():
        sp = '/'.join(f'{sprite[f]:.2f}' for f in FRIGATES) if sprite else ''
        fit = '; '.join(f"{k} {' x '.join(f'{v:.2f}' for v in vs)} m" for k, vs in r['fittings_m'].items())
        print(f"{n:24} {r['solved_metres_per_unit']:6.3f} {'-'.join(f'{v:.3f}' for v in r['range'].values()):>12} "
              f"{PUBLISHED.get(n, ''):>5} {r['length_m']:7.1f} {ratios['Hound (A)']:8.2f} {ratios['Lasher']:8.2f} "
              f"{sp:>10} {r['enclosed_m3_per_person_at_max_crew']:7}  {fit}"
              + ('  FLAG: capital under 4x a frigate or its sprite ratio' if flag else ''))
