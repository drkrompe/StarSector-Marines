"""The ruins plant stores' loads (``ruins-plant-brief`` section 4): the base game's goods drawn top-down as rack and
pallet loads, in each vanilla cargo icon's colours and silhouette, so a player reads the commodity. ``make_guide``
blocks them out, ``export.py`` cuts them, adds each load's field-cache variant (the same load on the recovery skid's
amber rails, ``loot-nouns.md``) and packs the ``ruins-stores`` sheet.

Each entry: (id, cells w, cells h, vanilla icon, cache, name, what). Rows are separated by None.
"""

LOADS = [
    ("store-metals", 2, 2, "refined_metal", True, "Metals", "Grey strapped ingot stacks on a pallet."),
    ("store-transplutonics", 2, 2, "raremetals", True, "Transplutonics", "A shielded rack of silver-blue canisters."),
    ("store-volatiles", 2, 2, "volatiles", True, "Volatiles", "A rack of blue pressure tanks."),
    ("store-supplies", 2, 2, "supplies", True, "Supplies", "Yellow-banded supply crates on a pallet."),
    ("store-organics", 2, 2, "organics", True, "Organics", "A cluster of green organics drums on a pallet."),
    None,
    ("store-heavy-machinery", 3, 3, "heavymachinery", True, "Heavy machinery", "An orange machine crated on a skid."),
    ("store-ore", 2, 2, "ore", True, "Ore", "A steel ore bin heaped with dark rock."),
    ("store-transplutonic-ore", 2, 2, "rareore", True, "Transplutonic ore",
     "A steel ore bin heaped with pale blue-white crystalline ore."),
    ("store-fuel", 2, 2, "fuel", True, "Fuel", "Red fuel tanks in a hazard-marked bay."),
    ("store-domestic-goods", 2, 2, "goods", True, "Domestic goods", "Mixed crates and cases on a pallet."),
    None,
    ("store-rack-bay", 2, 4, None, False, "Empty rack bay", "A store rack bay, two pallet places deep, standing empty."),
    ("store-rack-bay-stripped", 2, 4, None, False, "Stripped rack bay",
     "A store rack bay stripped to a few leftovers: a split case, loose straps."),
    ("store-pallet", 2, 2, None, False, "Empty pallet", "An empty steel-runnered pallet."),
]


def loads():
    return [e for e in LOADS if e]
