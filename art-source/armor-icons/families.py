"""The armor appearance families that own an armory image, and where their battle layers live.

Shared by contact_sheet.py (review), the identity-reference builder and export.py. A family's
battle layers are the identity authority for its armory image: palette, plate shapes, markings.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / 'mod/data/marines/marine-armor-catalog.armor.json'
LAYERS = ROOT / 'mod/graphics/battle/marine-modular-topdown/variants/armor'

# family -> battle layer folder (relative to LAYERS), as BattleSprites.loadLayeredFamily loads it.
FAMILIES = {
    'ARMORLESS': 'armorless',
    'MILITIA': 'militia',
    'OUTLAW': 'outlaw',
    'BLUE_SCOUT': 'blue-scout',
    'CHARCOAL': 'charcoal',
    'ARMY_GREEN': 'army-green',
    'AEGIS_COMPOSITE': 'aegis',
    'PALATINE': 'palatine',
    'FURNACE_LINE': 'furnace-line',
    'REAVER': 'reaver',
    'RED_ELITE': 'red-heavy',
    'SPECTER_HEAVY': 'specter-heavy',
    'ARBALEST_HEAVY': 'specter-heavy/paints/olive',
    'BOARDING_JACK': 'boarding-jack',
    'BULWARK_HEAVY': 'bulwark-heavy',
    'RELIQUARY_HEAVY': 'reliquary-heavy',
    'LIONS_MANTLE': 'lions-mantle',
    'FOUNDRY_BREAKER': 'foundry-breaker',
}


def slug(family):
    return family.lower().replace('_', '-')


def icon_path(family):
    """The shipped armory image for a family, as the catalog's iconPath names it."""
    return f'graphics/ui/armory/armor-{slug(family)}.png'


def layer_files(family):
    folder = LAYERS / FAMILIES[family]
    names = ['body', 'head', 'shoulder-left', 'shoulder-right', 'foot']
    return [(n, folder / f'{n}.png') for n in names if (folder / f'{n}.png').exists()]
