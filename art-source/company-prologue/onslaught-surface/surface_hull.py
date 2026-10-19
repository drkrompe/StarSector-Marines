"""The Onslaught (XIV)'s inputs to the shared surfacing harness (../second-wind-surface/hull.py).

Every harness script runs with `--hull ../onslaught-surface` (after `--` for
Blender) or SURF_HULL set to this folder; see README.md and rebuild.sh for the
sequence. Livery: Hegemony XIV, read off onslaught_hegemony.png and matched to
the Legion (XIV)'s armour colours (legion-geometry/lg_kit.py): Hegemony orange
armour bands over a warm brown-grey hull, dark grey armour blocks, bone-white
spine and citadel, gunmetal machinery, near-black recesses. A picket flagship
on station: service wear, heavier than the Hound's.

Detail passes 1-2 built the plating as alternating tones (dark brown, bone-brown,
blue-grey steel, weathered orange, pale steel). Graded to their parents' means
those tones would vanish, so each is its own class, derived from the measured
parent by the offset the geometry was designed with (derive_palette).
"""
NAME = 'Onslaught (XIV)'
BLEND = 'build/geom/onslaught-geometry.blend'   # rebuild.sh step 1: ../onslaught-geometry/build_onslaught.py -- --out <this>/build/geom
GROUPS = ['arms', 'top', 'hull', 'stern']
MATERIAL_CLASS = {
    # orange armour bands and their weathered segments
    'Hegemony orange armour': 'orange',
    'Detail hatch orange': 'orange',
    'Hegemony orange weathered': 'orange_w',
    'Hegemony weathered orange plate': 'orange_w',
    'Detail plate dirty orange': 'orange_w',
    # the warm brown-grey hull and its plate tones
    'Hegemony brown-grey structure': 'brown',
    'Hegemony hatch plate': 'brown',
    'Hegemony hatch plate.001': 'brown',
    'Detail hatch plate': 'brown',
    'Hegemony dark brown plate': 'brown_dark',
    'Detail plate dark brown': 'brown_dark',
    'Hegemony plate dark brown': 'brown_dark',
    'Stern plate dark red-brown': 'brown_dark',
    'Radiator fin red-brown': 'brown_dark',
    'Hegemony bone-brown plate': 'bone_brown',
    'Hegemony plate bone-brown': 'bone_brown',
    'Hegemony plate bone-brown doubler': 'bone_brown',
    'Stern plate bone-brown': 'bone_brown',
    'Detail plate bone brown': 'bone_brown',
    # bone-white spine, citadel and pale fittings
    'Hegemony bone white spine': 'bone',
    'Header band pale': 'bone',
    'Valve wheel pale': 'bone',
    'Detail actuator rod': 'bone',
    # dark grey armour blocks and blue-grey steel plating
    'Hegemony dark grey armour': 'grey',
    'Hegemony blue-grey steel plate': 'steel_blue',
    'Hegemony plate grey-blue steel': 'steel_blue',
    'Detail plate blue steel': 'steel_blue',
    'Stern plate blue-grey steel': 'steel_blue',
    # machinery steels
    'Gunmetal machinery': 'gunmetal',
    'Detail heat shield steel': 'gunmetal',
    'Conduit steel': 'steel_pale',
    'Hull conduit steel': 'steel_pale',
    'Detail pipe steel': 'steel_pale',
    'Pale pipe steel': 'steel_pale',
    'Hull tank steel': 'steel_pale',
    'Top run plate steel': 'steel_pale',
    'Header plate steel': 'steel_pale',
    'Detail machinery light steel': 'steel_pale',
    'Detail plate light grey': 'steel_pale',
    'Detail plate bone grey': 'steel_pale',
    # recesses and seams
    'Recess and machinery': 'dark',
    'Hull seam black': 'dark',
    'Detail seam black': 'dark',
    'Stern seam black': 'dark',
    'Plate seam black': 'dark',
    'Plate seam shadow': 'dark',
}
# Kept on their own materials (no class, no swatch): the bridge glazing (a
# swatch is a painted plate; on the Shepherd's panes it read as steel), lamps
# and running lights, the conduit bronze, the guns' canvas blast bags.
CLASS_ID = {'orange': (255, 128, 0), 'orange_w': (128, 64, 0), 'brown': (255, 0, 0), 'brown_dark': (128, 0, 0),
            'bone_brown': (255, 128, 128), 'bone': (255, 255, 0), 'grey': (0, 255, 0), 'steel_blue': (0, 128, 255),
            'gunmetal': (0, 0, 255), 'steel_pale': (0, 255, 255), 'dark': (128, 0, 255), 'other': (255, 0, 255)}
CLASSES = ['orange', 'orange_w', 'brown', 'brown_dark', 'bone_brown', 'bone', 'grey', 'steel_blue',
           'gunmetal', 'steel_pale', 'dark']
# Swap check: orange and bone are paint families of their own (orange painted
# where brown belongs is a swap); the browns, greys, steels and recesses are
# one neutral family (lighter or darker is tone, reported).
FAMILY = {'orange': 'orange', 'orange_w': 'orange', 'bone': 'bone',
          'brown': 'neutral', 'brown_dark': 'neutral', 'bone_brown': 'neutral', 'grey': 'neutral',
          'steel_blue': 'neutral', 'gunmetal': 'neutral', 'steel_pale': 'neutral', 'dark': 'neutral'}
# Orange, brown and bone sit near the default light-grey background; a mid blue
# is far from every class (as the Hermes, Mudskipper and Shepherd).
BACKGROUND = (70, 110, 170)
SWATCH_UNITS = 9.0                  # hull units one swatch image spans (7.7 m at 0.86 m/unit)
SWATCH_ORDER = ['orange', 'brown', 'bone', 'grey', 'gunmetal', 'dark']
SWATCH_DERIVED = [('orange_w', 'orange'), ('brown_dark', 'brown'), ('bone_brown', 'brown'),
                  ('steel_blue', 'grey'), ('steel_pale', 'gunmetal')]
MEASURED_CLASSES = ['orange', 'brown', 'bone', 'grey', 'gunmetal', 'dark']
REFERENCE_CAMERA = 'three-quarter'
# The hull's lofts and heightfield masses carry non-planar quads: against the polygon tree
# the visibility test dropped most of the prow flank (3 of 48 faces) and the bilge planes.
# The triangle tree reads them as drawn (parts.select_faces).
SHEARED_FACES = True

# Derived classes: CIELAB offset from the measured parent, taken from the
# geometry's own base colours (weathered orange plates against the orange
# armour, the plate tones against the brown-grey structure, blue steel against
# the dark grey armour, the pale pipe and tank steels against gunmetal).
DERIVED = {'orange_w': ('orange', (-11.0, -5.0, -11.0)),
           'brown_dark': ('brown', (-14.0, -2.0, -2.0)),
           'bone_brown': ('brown', (+17.0, -2.0, +3.0)),
           'steel_blue': ('grey', (+3.0, -1.0, -4.5)),
           'steel_pale': ('gunmetal', (+16.0, 0.0, +2.0))}


def derive_palette(classes):
    # The reference's measured spreads carry its facet shading and, on thin
    # parts, their neighbours at the registration edge: the means stand, the
    # spreads are owned (as on the Hermes, Hound (A) and Shepherd).
    owned = {'orange': [8.0, 2.0, 3.5], 'brown': [8.0, 1.0, 2.0], 'bone': [7.0, 0.6, 1.5],
             'grey': [8.0, 0.6, 1.2], 'gunmetal': [10.0, 0.6, 1.6], 'dark': [6.5, 0.5, 0.8]}
    out = {c: dict(classes[c], std=std, std_measured=classes[c]['std'],
                   note='spread owned: shading and edge contamination on the reference inflate it')
           for c, std in owned.items()}
    # Gunmetal is ~3k reference pixels, nearly all machinery in shadow in wells and
    # trenches, and measured darker than the dark grey armour (L 23.8 against 26.4).
    # The geometry designs it ~9 L lighter than that armour: the mean is owned at that
    # offset from the measured grey.
    g = out['grey']['mean']
    out['gunmetal'] = dict(out['gunmetal'], mean=[g[0] + 9.0, g[1] - 0.3, g[2] - 0.5],
                           mean_measured=classes['gunmetal']['mean'],
                           note='mean owned (grey + 9 L, the geometry\'s offset): the reference sees it in shadow')
    for c, (parent, off) in DERIVED.items():
        m = out[parent]['mean']
        out[c] = {'derived': f'{parent} + {list(off)} (the geometry\'s designed tone offset)',
                  'mean': [m[0] + off[0], m[1] + off[1], m[2] + off[2]], 'std': list(out[parent]['std'])}
    return out


VARIATION_SPAN = {'orange': (4.0, 1.5, 2.5), 'orange_w': (4.0, 1.5, 2.5), 'brown': (3.5, 0.8, 1.5),
                  'brown_dark': (3.0, 0.8, 1.2), 'bone_brown': (3.5, 0.8, 1.5), 'bone': (3.0, 0.5, 1.0),
                  'grey': (3.0, 0.4, 0.8), 'steel_blue': (3.0, 0.4, 0.8), 'gunmetal': (3.0, 0.4, 0.8),
                  'steel_pale': (3.0, 0.4, 0.8), 'dark': (2.0, 0.2, 0.3)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'orange': (1.0, 1.0, 1.0), 'orange_w': (1.0, 1.0, 1.0), 'brown': (1.0, 0.8, 1.0),
                 'brown_dark': (1.0, 0.8, 1.0), 'bone_brown': (1.0, 0.8, 1.0), 'bone': (1.0, 1.0, 1.0),
                 'grey': (1.0, 0.6, 1.0), 'steel_blue': (1.0, 0.6, 1.0), 'gunmetal': (0.0, 0.35, 0.7),
                 'steel_pale': (0.0, 0.35, 0.7), 'dark': (0.0, 0.0, 0.25)}
# Drive bell centres, hull units (geometry.json engines): quarter pods 1/4,
# the blades' 2/3, the mains 5/6.
ENGINES = [(-82.0, 70.0, -8.0), (-103.0, 40.0, -8.0), (-103.0, -40.0, -8.0), (-82.0, -70.0, -8.0),
           (-113.0, 11.0, -6.0), (-113.0, -11.0, -6.0)]
STERN_SOOT = (-95.0, -118.0)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Hull core'

# Review: the build's own cameras plus the sides it does not have.
REVIEW_WHOLE = ['three-quarter', 'port-side', 'top', 'front', 'stern', 'belly',
                'starboard-side', 'port-aft-quarter', 'under-quarter']
REVIEW_CLOSE = ['bow-detail', 'arm-detail', 'flank-detail', 'deck-detail', 'waist-detail', 'bridge-detail',
                'turret-detail', 'machinery-detail', 'engine-detail', 'stern-detail', 'belly-detail']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    'port-aft-quarter': (-45, 28, 420, (50, 0, 0)),
    'under-quarter': (40, -35, 420, (50, 0, -10)),
}
REVIEW_PAGES = [('views-whole', ['three-quarter', 'port-aft-quarter', 'port-side', 'top']),
                ('views-whole-2', ['front', 'stern', 'starboard-side', 'under-quarter']),
                ('views-close', ['bow-detail', 'arm-detail', 'flank-detail', 'deck-detail']),
                ('views-close-2', ['waist-detail', 'bridge-detail', 'turret-detail', 'belly-detail']),
                ('views-close-3', ['machinery-detail', 'engine-detail', 'stern-detail', 'belly'])]
CAPTION = 'Onslaught (XIV) surfacing, Hegemony XIV livery: graded Codex part skins + graded class swatches on every other base face.'
