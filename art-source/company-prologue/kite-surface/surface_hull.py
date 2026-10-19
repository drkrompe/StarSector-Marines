"""The Kite (P)'s inputs to the shared surfacing harness (../second-wind-surface/hull.py).

Every harness script runs with `--hull ../kite-surface` (after `--` for
Blender) or SURF_HULL set to this folder; see README.md and rebuild.sh for the
sequence. Livery: the vanilla pirate refit read off aeroshuttle_pirates.png
(reference/sprite-livery.png): a near-black warm brown-grey hull, a grimy
bone cabin spine and hump, rust-brown welded scrap plates, gunmetal drives,
launchers and sponsons, near-black recesses, tinted glazing. The sprite's
red claw slashes are livery too; they are laid on afterwards as one decal
(livery_decal.py) so grading never pulls them toward the hull's palette.
Heavy, neglected wear: a pirate crew's shuttle.
"""
NAME = 'Kite (P)'
BLEND = 'build/geom/kite-geometry.blend'   # rebuild.sh step 1: ../kite-geometry/build_kite.py -- --out <this>/build/geom
GROUPS = ['body', 'deck']
MATERIAL_CLASS = {
    'Cream hull plate': 'cream',
    'Grey hull panel': 'hull',
    'Gunmetal machinery': 'gunmetal',
    'Drive frame plate': 'gunmetal',
    'Recess and machinery': 'dark',
    'Heat shield tile': 'dark',
    'Cockpit glazing': 'glass',
    'Pirate scrap plate': 'scrap',
    'Pirate bash plate': 'scrap',
    'Drive heat-shield wrap': 'cream',     # the sprite's pale bands round the drive cans
}
# Kept on their own materials (no class, no swatch): the turret kit's own
# weathered paints, the drive bell liners, chrome rods,
# the launchers' hazard amber, Swarmer red, marking white and scorch plate,
# cabin window glow and lamps.
CLASS_ID = {'cream': (255, 0, 0), 'hull': (0, 255, 0), 'gunmetal': (0, 0, 255), 'dark': (255, 255, 0),
            'glass': (0, 255, 255), 'scrap': (255, 128, 0), 'other': (255, 0, 255)}
CLASSES = ['cream', 'hull', 'gunmetal', 'dark', 'scrap', 'glass']
# Swap check: bone spine painted where the dark hull belongs (or the reverse)
# is a swap, as is rust scrap; hull, gunmetal, recess and glass differ in tone.
FAMILY = {'cream': 'cream', 'hull': 'neutral', 'gunmetal': 'neutral', 'dark': 'neutral', 'glass': 'neutral',
          'scrap': 'rust'}
# A mid blue is far from every class.
BACKGROUND = (70, 110, 170)
SWATCH_UNITS = 12.0                 # hull units one swatch image spans (9.6 m at 0.80 m/unit)
SWATCH_ORDER = ['cream', 'hull', 'gunmetal', 'dark', 'scrap']
SWATCH_DERIVED = [('glass', 'dark')]
MEASURED_CLASSES = ['cream', 'hull', 'gunmetal', 'dark', 'scrap']
REFERENCE_CAMERA = 'port-quarter'


def derive_palette(classes):
    # Thin gunmetal fittings and recess slots pick up their neighbours at the
    # registration edge: their means stand, the spreads are owned (as on the
    # Hermes and the Hound).
    out = {}
    for c, std in (('gunmetal', [9.0, 0.8, 1.6]), ('dark', [6.0, 0.6, 1.0])):
        if c in classes:
            out[c] = dict(classes[c], std=std, std_measured=classes[c]['std'],
                          note='spread owned: edge contamination on the reference inflates it')
    return out | {'glass': {'derived': 'owned: tinted armoured glazing, dark with a faint cool cast',
                            'mean': [20.0, -2.0, -5.0], 'std': [5.0, 0.8, 1.2]}}


VARIATION_SPAN = {'cream': (4.0, 1.0, 2.0), 'hull': (3.5, 0.8, 1.5), 'gunmetal': (3.0, 0.4, 0.8),
                  'dark': (2.0, 0.2, 0.3), 'scrap': (5.0, 2.0, 3.0), 'glass': (1.5, 0.2, 0.3)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'cream': (1.0, 1.0, 1.0), 'hull': (1.0, 0.8, 1.0), 'gunmetal': (0.0, 0.35, 0.7),
                 'dark': (0.0, 0.0, 0.25), 'scrap': (0.6, 0.5, 1.0), 'glass': (0.0, 0.0, 0.0)}
# Main drive mouths (geometry.json engines 1-4, 7, 8), for soot.
ENGINES = [(-21.0, 28.0, -1.6), (-27.0, 13.0, -0.5), (-27.0, -13.0, -0.5), (-21.0, -28.0, -1.6),
           (-22.0, -20.0, -1.0), (-22.0, 21.0, -1.0)]
STERN_SOOT = (-14.0, -24.0)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Hull fuselage'

# Review: the build's own cameras plus the sides it does not have.
REVIEW_WHOLE = ['port-quarter', 'stern-quarter', 'top', 'starboard-quarter']
REVIEW_CLOSE = ['cockpit-detail', 'stern-detail']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    'starboard-quarter': (135, 30, 125, (4, 0, -1)),
}
REVIEW_PAGES = [('views-whole', ['port-quarter', 'starboard-quarter', 'stern-quarter', 'top'])]
CAPTION = 'Kite (P) surfacing, pirate refit livery: graded Codex part skins + graded class swatches on every other base face.'
