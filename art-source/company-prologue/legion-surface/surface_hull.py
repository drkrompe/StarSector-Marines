"""The Legion (XIV) battlecarrier's inputs to the shared surfacing harness (../second-wind-surface/hull.py).

Every harness script runs with `--hull ../legion-surface` (after `--` for
Blender) or SURF_HULL set to this folder; see README.md and rebuild.sh for the
sequence. Livery: the Hegemony XIV battlecarrier read off legion_xiv.png
(reference/sprite-livery.png): warm grey hull plating on the torso, spines,
sponsons and belly; XIV orange armour on the bow blades, belts and wing
plates; tan plating on the wing armour's second course; the bone-white
command dome; gunmetal machinery; near-black recesses and the dark flight
decks. Damage-free patrol hull, service wear.
"""
NAME = 'Legion'
BLEND = 'build/geom/legion-surface.blend'    # make_groups.py: the geometry build (../legion-geometry/build_legion.py) cut along the tile lines
GROUPS = ['top', 'side', 'belly']
MATERIAL_CLASS = {
    'XIV grey hull plating': 'grey',
    'XIV orange armour': 'orange',
    'XIV tan hull plating': 'tan',
    'XIV bone white dome': 'bone',
    'Gunmetal machinery': 'gunmetal',
    'Recess and machinery': 'dark',
    'Flight deck': 'deck',
}
# Kept on their own materials (no class, no swatch): the bridge glazing and
# sensor lens, every lamp, running light and hangar light, and the turrets,
# which carry the shared turret kit's own military livery ('Turret ...',
# 'Missile body ...').
CLASS_ID = {'grey': (255, 0, 0), 'orange': (255, 128, 0), 'tan': (255, 255, 0), 'bone': (0, 255, 0),
            'gunmetal': (0, 0, 255), 'dark': (128, 0, 255), 'deck': (0, 255, 255), 'other': (255, 0, 255)}
CLASSES = ['grey', 'orange', 'tan', 'bone', 'gunmetal', 'dark', 'deck']
# Swap check: the warm paints (orange, tan) and the bone dome are their own
# families; the grey plating, gunmetal, recesses and decks are one neutral family.
FAMILY = {'grey': 'neutral', 'orange': 'orange', 'tan': 'tan', 'bone': 'bone',
          'gunmetal': 'neutral', 'dark': 'neutral', 'deck': 'neutral'}
BACKGROUND = (70, 110, 170)
SWATCH_UNITS = 9.0                  # hull units one swatch image spans (8.9 m at 0.99 m/unit)
SWATCH_ORDER = ['grey', 'orange', 'tan', 'bone', 'gunmetal', 'dark']
SWATCH_DERIVED = [('deck', 'dark')]
MEASURED_CLASSES = ['grey', 'orange', 'tan', 'bone', 'gunmetal', 'dark']
REFERENCE_CAMERA = 'three-quarter'
SHEARED_FACES = True                # lg_droop bends the wing arms per vertex after they are built


def derive_palette(classes):
    # The reference's measured spreads carry facet shading and, on thin parts,
    # their neighbours at the registration edge: the means stand, the spreads
    # are owned (as on the Shepherd and the Hermes).
    owned = {'grey': [9.0, 0.6, 1.4], 'orange': [8.0, 2.0, 3.5], 'tan': [8.0, 1.0, 2.5],
             'bone': [7.0, 0.5, 1.2], 'gunmetal': [10.0, 0.6, 1.6], 'dark': [6.5, 0.5, 0.8]}
    out = {c: dict(classes[c], std=std, std_measured=classes[c]['std'],
                   note='spread owned: shading and edge contamination on the reference inflate it')
           for c, std in owned.items()}
    # The flight decks are inside the hangars, a few dark pixels on the
    # reference: an owned dull deck plate a step lighter than the recesses.
    return out | {'deck': {'derived': 'owned: dark flight deck plate, a step above the recess tone',
                           'mean': [14.0, 1.0, 1.5], 'std': [5.0, 0.5, 0.8]}}


VARIATION_SPAN = {'grey': (3.0, 0.4, 0.9), 'orange': (3.0, 0.8, 1.2), 'tan': (3.0, 0.6, 1.2),
                  'bone': (2.5, 0.4, 0.8), 'gunmetal': (3.0, 0.4, 0.8), 'dark': (2.0, 0.2, 0.3),
                  'deck': (2.0, 0.2, 0.3)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'grey': (1.0, 1.0, 1.0), 'orange': (1.0, 1.0, 1.0), 'tan': (1.0, 1.0, 1.0),
                 'bone': (1.0, 1.0, 1.0), 'gunmetal': (0.0, 0.35, 0.7), 'dark': (0.0, 0.0, 0.25),
                 'deck': (0.0, 0.0, 0.25)}
# The ten drive bells (geometry.json engines; lg_engines).
ENGINES = [(-118, 62, -4.0), (-118, 57, -4.0), (-117, -56, -4.0), (-118, -61, -4.0), (-123, 44, -4.0),
           (-124, -44, -4.0), (-128, 26, -4.0), (-128, -26, -4.0), (-123, 13, -4.0), (-123, -12, -4.0)]
STERN_SOOT = (-95.0, -115.0)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Hull fore body'

# Review: the build's own cameras plus the sides it does not have.
REVIEW_WHOLE = ['three-quarter', 'starboard-side', 'port-side', 'top', 'front', 'stern',
                'port-aft-quarter', 'under-quarter']
REVIEW_CLOSE = ['bow-detail', 'bridge-detail', 'turret-detail', 'flight-deck-detail',
                'aft-deck-detail', 'flank-detail', 'engine-detail', 'stern-detail']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    'port-aft-quarter': (-45, 28, 420, (0, 0, 0)),
    'under-quarter': (40, -35, 420, (0, 0, -10)),
}
REVIEW_PAGES = [('views-whole', ['three-quarter', 'starboard-side', 'top', 'port-aft-quarter']),
                ('views-whole-2', ['port-side', 'front', 'stern', 'under-quarter']),
                ('views-close', ['bow-detail', 'bridge-detail', 'turret-detail', 'flight-deck-detail']),
                ('views-close-2', ['aft-deck-detail', 'flank-detail', 'engine-detail', 'stern-detail'])]
CAPTION = 'Legion (XIV) battlecarrier surfacing, Hegemony XIV livery: graded Codex part skins + graded class swatches on every other base face.'
FACE_FAMILY_MIN = 0.5                # a skinned face needs half its tile paint in its own family (apply_skin.py)
