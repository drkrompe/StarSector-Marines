"""The Colossus Mk.III's inputs to the shared surfacing harness (../second-wind-surface/hull.py).

Every harness script runs with `--hull ../colossus-mk3-surface` (after `--` for
Blender) or SURF_HULL set to this folder; see README.md and rebuild.sh. Forked from
../colossus-surface (the accepted Colossus); the part tables follow the Mk.III's
geometry (cut-down side modules, the launch bay, the refit's side skirts).

Livery: the pirates' read off colossus3_pirate.png: crimson enamel slapped over the
hold, the aft body and the bridge's base deck; blackened rust-brown plating on the
side modules and the drive pods, with crimson blocks on the pods' aft plates; the
Colossus's pale weathered-grey longerons and bridge shell kept; hazard orange,
gunmetal machinery, and the refit armour (sponsons, tubs, skirts, glacis) in the
same dark steel. Wear: `battle`, hard service plus fighting: scuffed, pitted,
scorched round the guns and drives, never repaired properly.

The cargo containers, the scavenged plates (their old grey primer and faded blue)
and the launch bay's red lamps keep their own materials.
"""
NAME = 'Colossus Mk.III'
BLEND = 'build/geom/colossus-mk3-geometry.blend'   # rebuild.sh step 1: ../colossus-mk3-geometry/build_colossus_mk3.py -- --out <this>/build/geom
GROUPS = ['hold', 'modules', 'spine', 'bridge', 'pods']
MATERIAL_CLASS = {
    'Pirate crimson hull plating': 'red',
    'Drive pod crimson aft plate': 'red_w',
    'Blackened module plating': 'maroon',
    'Pale weathered grey structure': 'pale',
    'Burnt orange panel band': 'orange',
    'Gunmetal machinery': 'gunmetal',
    'Refit armour plate': 'gunmetal',
    'Weld bead': 'dark',
    'Ceramic heat-shield tile': 'tile',
    'Recess and lattice': 'dark',
    'Seam groove shadow': 'dark',
    'Bridge seam groove shadow': 'dark',
}
# Kept on their own materials (no class, no swatch): the scavenged plates, the
# launch bay's red lamps, the container liveries,
# the bridge glazing and dark port glass (a swatch is a painted plate; on the
# Shepherd's panes it read as steel), running lamps, the drive bell liners,
# nozzle lips, heat-shield bands and chambers, and the pods' fender rubber.
CLASS_ID = {'red': (255, 0, 0), 'red_w': (255, 128, 128), 'maroon': (128, 0, 0), 'pale': (255, 255, 0),
            'orange': (255, 128, 0), 'blue': (0, 128, 255), 'gunmetal': (0, 0, 255), 'tile': (0, 255, 0),
            'dark': (128, 0, 255), 'other': (255, 0, 255)}
CLASSES = ['red', 'red_w', 'maroon', 'pale', 'orange', 'gunmetal', 'tile', 'dark']
# Swap check: the reds (oxide, worn, maroon) are one paint family (darker or
# lighter red is tone, reported); orange, blue and pale are their own; the
# steels, tiles and recesses are one neutral family.
FAMILY = {'red': 'red', 'red_w': 'red', 'maroon': 'red', 'pale': 'pale', 'orange': 'orange', 'blue': 'blue',
          'gunmetal': 'neutral', 'tile': 'neutral', 'dark': 'neutral'}
# Red, maroon and pale grey sit near the default grey background; a mid blue is
# far from them. The faded-blue bands are close to it but small, and their
# measurement is checked against the geometry's designed colour (derive_palette).
BACKGROUND = (70, 110, 170)
SWATCH_UNITS = 12.0                 # hull units one swatch image spans (6 m at 0.50 m/unit)
# The Mk.III has no faded-blue panel bands (the refit stripped them), so no blue class;
# the scavenged faded-blue plates keep their own material. Near-black and the tiles are neutral and regrade from gunmetal by a shift.
SWATCH_ORDER = ['red', 'maroon', 'pale', 'orange', 'gunmetal']
SWATCH_DERIVED = [('red_w', 'red'), ('dark', 'gunmetal'), ('tile', 'gunmetal')]
MEASURED_CLASSES = ['red', 'maroon', 'pale', 'orange', 'gunmetal', 'dark']
REFERENCE_CAMERA = 'three-quarter'

# Derived classes: CIELAB offset from the measured parent, from the geometry's
# own base colours (the pods' worn aft plate against the oxide red; the ceramic
# tiles against the recess black).
DERIVED = {'red_w': ('red', (+3.0, -8.0, -4.0)),
           'tile': ('dark', (+6.0, 0.0, 0.5))}


def derive_palette(classes):
    # The reference's measured spreads carry its facet shading and, on thin
    # parts, their neighbours at the registration edge: the means stand, the
    # spreads are owned (as on the Hermes, Hound (A), Shepherd and Onslaught).
    owned = {'red': [8.0, 1.5, 2.5], 'maroon': [7.0, 1.2, 2.0], 'pale': [8.0, 0.6, 1.2],
             'orange': [8.0, 2.0, 3.5], 'blue': [7.0, 1.0, 2.0], 'gunmetal': [9.0, 0.6, 1.4],
             'dark': [6.0, 0.5, 0.8]}
    out = {c: dict(classes[c], std=std, std_measured=classes[c]['std'],
                   note='spread owned: shading and edge contamination on the reference inflate it')
           for c, std in owned.items() if c in classes}
    # Pale's darkest tenth is near black (L p10 0.25): the longerons' bow end faces carry
    # the pale label but sit in the shadow between the module bow armour and the hold, and
    # render black on the reference. The median (62.0) is the paint; the mean is owned there.
    if 'pale' in out:
        m = out['pale']['mean']
        out['pale'] = dict(out['pale'], mean=[62.0, m[1], m[2]], mean_measured=m,
                           note='mean owned at the measured median L: shadowed longeron end faces drag the mean')
    for c, (parent, off) in DERIVED.items():
        m = out[parent]['mean']
        out[c] = {'derived': f'{parent} + {list(off)} (the geometry\'s designed tone offset)',
                  'mean': [m[0] + off[0], m[1] + off[1], m[2] + off[2]], 'std': list(out[parent]['std'])}
    return out


VARIATION_SPAN = {'red': (3.5, 1.2, 1.5), 'red_w': (3.5, 1.2, 1.5), 'maroon': (3.5, 1.0, 1.2),
                  'pale': (3.0, 0.5, 1.0), 'orange': (4.0, 1.5, 2.5), 'blue': (3.0, 0.8, 1.5),
                  'gunmetal': (3.0, 0.4, 0.8), 'tile': (2.0, 0.2, 0.3), 'dark': (2.0, 0.2, 0.3)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'red': (1.0, 1.0, 1.0), 'red_w': (1.0, 1.0, 1.0), 'maroon': (1.0, 1.0, 1.0),
                 'pale': (1.0, 0.8, 1.0), 'orange': (1.0, 1.0, 1.0), 'blue': (1.0, 1.0, 1.0),
                 'gunmetal': (0.0, 0.35, 0.7), 'tile': (0.0, 0.2, 0.0), 'dark': (0.0, 0.0, 0.25)}
# Drive bell centres, hull units (geometry.json engines): three per pod.
ENGINES = [(-124.5, 64.0, 0.0), (-124.5, 56.0, 0.0), (-124.5, 49.0, 0.0),
           (-124.5, -61.0, 0.0), (-124.5, -54.0, 0.0), (-124.5, -46.0, 0.0)]
STERN_SOOT = (-108.0, -124.0)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Hull central cargo hold'

# Review: the build's own cameras plus the sides it does not have.
REVIEW_WHOLE = ['three-quarter', 'starboard-quarter', 'port-side', 'top', 'front', 'stern',
                'starboard-side', 'port-aft-quarter', 'underside-quarter']
REVIEW_CLOSE = ['bow-detail', 'bay-detail', 'bow-guns', 'sponson-detail', 'refit-wall', 'turret-detail',
                'pod-quarter', 'engine-detail', 'bridge-quarter']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    'starboard-side': (180, 0, 240, (-17, 1.5, -10)),
    'port-aft-quarter': (-45, 28, 300, (-17, 1.5, -5)),
}
REVIEW_PAGES = [('views-whole', ['three-quarter', 'starboard-quarter', 'port-side', 'top']),
                ('views-whole-2', ['port-aft-quarter', 'starboard-side', 'front', 'underside-quarter']),
                ('views-close', ['bow-detail', 'bay-detail', 'bow-guns', 'sponson-detail']),
                ('views-close-2', ['refit-wall', 'turret-detail', 'pod-quarter', 'bridge-quarter'])]
CAPTION = 'Colossus Mk.III surfacing, pirate livery: graded Codex part skins + graded class swatches on every other base face.'
