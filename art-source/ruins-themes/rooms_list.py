"""The ruins rooms' art list (``ruins-theme-rooms``, ``RuinsRoom``): every piece's family, footprint, block-out kind,
cover and what it is. ``make_guide`` draws the families from it, ``export.py`` writes their atlas metadata from it and
packs them on the ``ruins-rooms`` sheet. A piece stands against the north wall, its working face to the room.

Each entry: (id, cells w, cells h, kind, layer, name, what). Layer ``stand`` blocks with medium cover; ``floor`` and
``overhead`` do not block.
"""

FAMILIES = {
    "rooms-control": [
        ("control-desk", 3, 1, "r/console", "stand", "Control desk", "A run of three operator consoles, screens to the room."),
        ("control-desk-burnt", 3, 1, "r/burnt", "stand", "Control desk, burnt", "The console run burned out, its screens blown."),
        ("mimic-board", 3, 1, "r/board", "stand", "Mimic board", "A generic plant mimic board on the wall."),
        None,
        ("shift-desk", 2, 1, "r/desk", "stand", "Shift desk", "The shift lead's desk, a terminal on it."),
        ("ceiling-panel", 2, 1, "r/panel", "stand", "Fallen ceiling panel", "A fallen ceiling panel with its cable loom."),
        ("sample-hatch", 2, 1, "r/hatch", "stand", "Sample hatch", "A pass-through hatch in the hall-side wall, a tray in front."),
        ("tally-terminal", 1, 1, "r/terminal", "stand", "Tally terminal", "A stores tally terminal on a post."),
    ],
    "rooms-lab": [
        ("fume-hood", 2, 1, "r/hood", "stand", "Fume hood", "A lab fume hood, sash half down."),
        ("lab-bench", 3, 1, "r/bench", "stand", "Lab bench", "A lab bench with glassware and a scope."),
        ("shelf-toppled", 2, 1, "r/toppled", "stand", "Toppled shelf", "A toppled lab shelf, its contents spilled."),
        None,
        ("sample-store", 1, 2, "r/store", "stand", "Sample store", "A tall chilled sample cabinet."),
        ("reject-bin", 1, 1, "r/bin", "stand", "Reject bin", "A lidded reject bin, hazard-banded."),
        ("parts-rack", 1, 2, "r/rack", "stand", "Parts rack", "A narrow spares rack, bins of parts."),
        ("parts-trolley", 1, 1, "r/trolley", "stand", "Parts trolley", "A parts trolley."),
    ],
    "rooms-shop": [
        ("rework-stand", 3, 3, "r/frame", "floor", "Rework stand", "A low frame a unit is bolted to for rework; open for the unit laid on it."),
        ("workshop-hoist", 3, 3, "r/gantry", "overhead", "Workshop hoist", "A small gantry hoist over the stand, open in the middle. Drawn above units."),
        None,
        ("hoist-fallen", 2, 1, "r/load", "stand", "Hoist's fallen load", "The hoist's dropped load and chain."),
        ("tool-wall", 3, 1, "r/bench", "stand", "Tool wall", "A tool board and bench against the wall."),
    ],
    "rooms-crew": [
        ("crew-locker", 1, 2, "r/locker", "stand", "Crew lockers", "A pair of crew lockers."),
        ("crew-locker-open", 1, 2, "r/locker-open", "stand", "Crew lockers, open", "The lockers, doors open, kit spilled."),
        ("crew-bunk", 1, 2, "r/bunk", "stand", "Crew bunk", "A crew bunk, bedding rumpled."),
        ("store-rack", 1, 2, "r/rack", "stand", "Store rack", "A tall store rack."),
        None,
        ("mess-table-tipped", 3, 1, "r/tipped", "stand", "Tipped mess table", "A mess table on its side as a barricade, scorch-marked."),
        ("store-shelf", 2, 1, "r/shelf", "stand", "Store shelf", "Steel store shelving, mostly stripped."),
    ],
}

BOARDS = [("cold-loop-board", "cryostat loop temperatures"), ("culture-board", "culture vat states"),
          ("print-queue-board", "the print queue"), ("lattice-board", "AI core burn-in states"),
          ("field-board", "the fusion torus field"), ("beam-board", "the synchrotron beam position"),
          ("bore-board", "bore depth and torque"), ("reactor-board", "reactor column temperatures"),
          ("draw-board", "fibre draw speed and tension"), ("director-board", "holostage calibration"),
          ("dispatch-board", "the dispatch schedule"), ("range-board", "the proof range"),
          ("pour-board", "the foundry pour")]
FAMILIES["rooms-boards-a"] = []
FAMILIES["rooms-boards-b"] = []
for k, (pid, what) in enumerate(BOARDS):
    fam = FAMILIES["rooms-boards-a" if k < 7 else "rooms-boards-b"]
    if len(fam) % 4 == 3:
        fam.append(None)
    fam.append((pid, 3, 1, "r/board", "stand", pid.replace("-", " ").capitalize(),
                f"The control room's mimic board of its hall's line: {what}."))

FAMILIES["rooms-instruments-a"] = [
    ("qubit-dewar", 2, 2, "r/instrument", "stand", "Qubit dewar rig", "A dewar test rig for cryo units."),
    ("incubator", 2, 2, "r/instrument", "stand", "Sample incubator", "A culture sample incubator."),
    ("core-probe", 2, 2, "r/instrument", "stand", "Core probe station", "A probe station for AI core lattices."),
    None,
    ("coil-test-rig", 2, 2, "r/instrument", "stand", "Coil test rig", "A field coil on a test frame."),
    ("magnet-bench", 2, 2, "r/instrument", "stand", "Magnet bench", "A magnet segment on a measuring bench."),
]
FAMILIES["rooms-instruments-b"] = [
    ("core-sample-rack", 2, 2, "r/rack", "stand", "Core sample rack", "Rock cores from the bore, racked."),
    ("assay-bench", 2, 2, "r/bench", "stand", "Assay bench", "A reagent assay bench."),
    ("tally-bench", 2, 2, "r/instrument", "stand", "Inspection bench", "An inspection and tally bench."),
    None,
    ("charge-test-cell", 2, 2, "r/instrument", "stand", "Charge test cell", "A small armoured test cell."),
    ("assay-furnace", 2, 2, "r/instrument", "stand", "Assay furnace", "A small assay furnace."),
    ("sorter-module", 3, 3, "r/sorter", "stand", "Sorter module", "A sorter head, the high bay's spare on the rework stand."),
]
STOCKS = [("cryogen-dewars", "r/canisters", "cryogen dewars"), ("nutrient-drums", "r/drums", "nutrient drums"),
          ("feedstock-canisters", "r/canisters", "print feedstock canisters"),
          ("rack-module-crates", "r/crates", "AI rack module crates"), ("coolant-drums", "r/drums", "coolant drums"),
          ("cable-drums", "r/reels", "superconducting cable drums"), ("drill-rods", "r/rods", "drill rods"),
          ("reagent-drums", "r/drums", "reagent drums"), ("spool-cores", "r/reels", "empty fibre spool cores"),
          ("emitter-cases", "r/crates", "holo emitter cases"), ("tote-stacks", "r/totes", "dispatch totes"),
          ("charge-cases", "r/crates", "charge cases"), ("ingot-stacks", "r/totes", "cast ingots")]
FAMILIES["rooms-stocks-a"] = []
FAMILIES["rooms-stocks-b"] = []
for k, (pid, kind, what) in enumerate(STOCKS):
    fam = FAMILIES["rooms-stocks-a" if k < 7 else "rooms-stocks-b"]
    if len(fam) % 5 == 4:
        fam.append(None)
    fam.append((pid, 2, 1, kind, "stand", pid.replace("-", " ").capitalize(), f"A store shelf's load: {what}."))


def pieces():
    """Every piece's entry, in family order."""
    return [e for fam in FAMILIES.values() for e in fam if e]
