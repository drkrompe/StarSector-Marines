"""One sizing basis for every cinematic hull: metres per unit from what the hull holds.

A sprite says nothing about size. A hull's real size is the volume its contents
need, divided among the space a ship of its class cannot give to them:

    contents  = max crew x the class's habitable m3 per person
              + cargo x the calibrated cargo m3 per unit + fuel x FUEL_M3_PER_UNIT
              + hangars (from the fighters' own sizes) + boat bays
    enclosed  = contents / (1 - machinery share of its class)
    metres per unit = cube root(enclosed m3 / measured enclosed units3)

Enclosed volume is defined one way for every hull: the union of the pressure
hull with its holds, hangars and boat bays, never drive pods, engine cans,
armour laid over the hull or open structure (SOLIDS below, measured by
ship_scale_measure.py inside the hull's .blend). Before a model exists,
estimate_units3() gives a rough figure from the sprite's plan area.

The cargo unit's volume is the calibrated constant: it is solved from the
Hound (A), whose size is fixed by modelled human-scale fittings, and applied
to every hull. The rest are real-world figures. All are justified in
ship-scale.md; change them there and here together, never per hull.

Run `python ship_scale.py` for the side-by-side table of the hulls in HULLS.
A driver calls record(name) and stores it under 'scale' in its geometry.json.
"""
import json
import sys

# Per class: habitable m3 per person at max crew (berths, messes, work
# stations, medical, recreation and the passages between them), the share of
# the enclosed volume no crew, cargo, fuel or craft can use (armour; drive,
# reactor and flux plant; weapons and magazines; structure, tanks, stores,
# life support and trunks), and the hangar volume per craft as a multiple of its
# bare length x span x height box.
# Habitable: naval warships give 15-30 m3 a person for voyages of weeks; the
# ISS gives its crew of 7 about 55 m3 each (388 m3); 40-70 spans a cramped
# warship to a roomy long-haul ship. Machinery: real warships give machinery
# spaces 20-25% of their internal volume; drive plant shrinks as a share with
# size, while armour and weapons grow with class. Hangar: a Nimitz's
# 210 x 33 x 8 m hangar (about 55,000 m3) holds 25-30 aircraft, about 1.8x each
# F/A-18's box; servicing in space and ordnance handling add to it.
# "Capitals should feel like capital ships" (owner): where a range is left, a
# capital takes the generous end and a small hull the lean end.
CLASSES = {
    'frigate':   {'habitable_m3_per_person': 40.0, 'hangar_box_factor': 2.5,
                  'shares': {'armour': .06, 'drive': .22, 'weapons': .06, 'structure': .12}},
    'destroyer': {'habitable_m3_per_person': 50.0, 'hangar_box_factor': 2.6,
                  'shares': {'armour': .08, 'drive': .20, 'weapons': .07, 'structure': .12}},
    'cruiser':   {'habitable_m3_per_person': 60.0, 'hangar_box_factor': 2.8,
                  'shares': {'armour': .11, 'drive': .18, 'weapons': .09, 'structure': .12}},
    'capital':   {'habitable_m3_per_person': 70.0, 'hangar_box_factor': 3.0,
                  'shares': {'armour': .18, 'drive': .16, 'weapons': .12, 'structure': .12}},
    'freighter': {'habitable_m3_per_person': 40.0, 'hangar_box_factor': 2.5,
                  'shares': {'armour': .03, 'drive': .15, 'weapons': .02, 'structure': .12}},
}
# One fuel unit is one tank-container load: an ISO tank holds about 26 m3, plus
# 25% for tankage, insulation and plumbing.
FUEL_M3_PER_UNIT = 32.0
SPARES_PER_WING = 1
# A boat bay holds one carried boat: the mod's deck program's 16 x 10 m boat
# bay at 6 m clear height.
BOAT_BAY_M3 = 960.0

# Calibration anchor: the Hound (A)'s size is fixed by modelled human-scale
# fittings (a 1.4 x 2.0 m aft door, a 1.7 x 2.1 m airlock, 0.9 x 1.0 m
# viewports, walkway rails, a 1.8 m mannequin) at 0.88 m/unit, 0.83 cramped.
# The cargo unit is solved so the Hound's contents fill its measured volume.
ANCHOR = {'hull': 'Hound (A)', 'metres_per_unit': .88, 'cramped': .83}

# Fighters in metres at their own scales (fighters-geometry, broadsword-geometry;
# Hoplon and Piranha from their sprites at 0.50 m/px until they are modelled).
FIGHTERS_M = {
    'broadsword': (15.5, 14.1, 3.3), 'hoplon': (18.0, 19.0, 4.0), 'piranha': (17.0, 15.0, 3.3),
    'longbow': (15.0, 14.4, 2.6), 'thunder': (18.5, 10.8, 2.6), 'dagger': (16.4, 12.1, 2.3),
    'talon': (12.1, 12.1, 2.5), 'wasp': (9.8, 8.1, 2.4),
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


def _non_cargo(h):
    return (h['crew_max']*CLASSES[h['cls']]['habitable_m3_per_person'] + h['fuel']*FUEL_M3_PER_UNIT
            + hangar_m3(h.get('wings', ()), h['cls']) + h.get('boat_bays', 0)*BOAT_BAY_M3)


def calibrated_cargo_m3(anchor_mpu=None):
    """Cargo m3 per unit that makes the anchor hull's contents fill it at its fittings-fixed scale."""
    h = HULLS[ANCHOR['hull']]
    mpu = ANCHOR['metres_per_unit'] if anchor_mpu is None else anchor_mpu
    contents = mpu**3*h['units3']*(1-machinery_share(h['cls']))
    return (contents-_non_cargo(h))/h['cargo']


def solve(h, cargo_m3):
    c = CLASSES[h['cls']]
    parts = {'crew': h['crew_max']*c['habitable_m3_per_person'], 'cargo': h['cargo']*cargo_m3,
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
    cargo_m3 = calibrated_cargo_m3()
    mid = solve(h, cargo_m3)
    mpu = mid['metres_per_unit']
    lo = solve(h, calibrated_cargo_m3(ANCHOR['cramped']))['metres_per_unit']
    return {
        'method': 'ship_scale.py: contents (crew, cargo, fuel, hangars, boat bays) over the class machinery '
                  'share, against the measured enclosed volume; cargo unit calibrated on the Hound (A)',
        'inputs': {**{k: (round(v) if k == 'units3' else v) for k, v in h.items() if k != 'fittings_units'},
                   'volume_source': VOLUME_SOURCE[name]},
        'constants': {'class': CLASSES[h['cls']], 'cargo_m3_per_unit': round(cargo_m3, 1),
                      'fuel_m3_per_unit': FUEL_M3_PER_UNIT, 'boat_bay_m3': BOAT_BAY_M3,
                      'spares_per_wing': SPARES_PER_WING, 'anchor': ANCHOR},
        **{k: v for k, v in mid.items() if k != 'metres_per_unit'},
        'solved_metres_per_unit': round(mpu, 3),
        'range': {'anchor_cramped': round(lo, 3), 'anchor_published': round(mpu, 3)},
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
                         fittings_units={'personnel door h': [3.4], 'rail h': [1.8], 'transfer door w x h': [34, 10]}),
    'Onslaught': dict(units3=estimate_units3(65541, 'capital'), crew_max=1500, cargo=300, fuel=400, cls='capital',
                      length_units=352, sprite_px=384),
    'Lasher': dict(units3=32793, crew_max=50, cargo=40, fuel=25, cls='frigate',
                   length_units=94.7, sprite_px=96,
                   fittings_units={'viewport w x h': [1.54, 1.23], 'bridge glazing band h': [1.54]}),
    'Hound (A)': dict(units3=13720, crew_max=40, cargo=75, fuel=30, cls='freighter', length_units=69.1, sprite_px=72,
                      fittings_units={'aft door w x h': [1.59, 2.27], 'airlock w x h': [1.93, 2.39]}),
    'Second Wind (Cerberus)': dict(units3=78443, crew_max=50, cargo=100, fuel=40, cls='freighter', boat_bays=2,
                                   length_units=101.5, fittings_units={'boat bay length x clear h': [23.1, 9.1]}),
}
VOLUME_SOURCE = {'Legion (XIV)': 'measured (SOLIDS)', 'Hound (A)': 'measured (SOLIDS)',
                 'Second Wind (Cerberus)': 'measured (SOLIDS)', 'Onslaught': 'estimate, no model',
                 'Lasher': 'measured (SOLIDS)'}
PUBLISHED = {'Legion (XIV)': .61, 'Hound (A)': .88, 'Second Wind (Cerberus)': .65, 'Lasher': .65}
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
    print(f'cargo unit calibrated on the Hound: {calibrated_cargo_m3():.1f} m3 at 0.88, '
          f'{calibrated_cargo_m3(ANCHOR["cramped"]):.1f} m3 at 0.83')
    print(f"{'hull':24} {'m/u':>6} {'@0.83':>6} {'now':>5} {'length':>7} {'x Hound':>8} {'x Lasher':>8} "
          f"{'sprite H/L':>10} {'m3/pers':>7}  fittings")
    for n, r, ratios, sprite, flag in table():
        sp = '/'.join(f'{sprite[f]:.2f}' for f in FRIGATES) if sprite else ''
        fit = '; '.join(f"{k} {' x '.join(f'{v:.2f}' for v in vs)} m" for k, vs in r['fittings_m'].items())
        print(f"{n:24} {r['solved_metres_per_unit']:6.3f} {r['range']['anchor_cramped']:6.3f} "
              f"{PUBLISHED.get(n, ''):>5} {r['length_m']:7.1f} {ratios['Hound (A)']:8.2f} {ratios['Lasher']:8.2f} "
              f"{sp:>10} {r['enclosed_m3_per_person_at_max_crew']:7}  {fit}"
              + ('  FLAG: capital under 4x a frigate or its sprite ratio' if flag else ''))
