"""Writes the ruins rooms' ImageGen briefs (``prompts/rooms-*.prompt.txt``) from ``rooms_list.py``. Run from this
folder: ``python rooms_prompts.py``."""
import rooms_list

HEAD = """Asset: a sheet of separate top-down tactical-game sprites: the furniture and kit of the rooms off an abandoned Domain-era megastructure plant's service corridors (control room, workshop, lab, crew quarters, stores) in a spacefaring civilization, seen from directly above (orthographic, 90 degrees, no perspective), for the Starsector Marines battle map. One cell is about one soldier wide.
Geometry: reference 1 is the layout authority. Reskin each grey block-out in place: keep every piece exactly inside its block, at its size, position and outline; do not move, enlarge, merge or add pieces. The transparent space between and around blocks stays fully transparent. Every piece stands against a wall along its TOP edge: its back is the top edge, its working face (screens, doors, drawers, the side a person uses) faces DOWN the image into the room.
Style: match reference 2 (the same plant's works-hall kit) for material: crisp painterly-pixel game sprite, blue-gray and gunmetal plate, dark recessed seams, bolted edges, worn ochre trim, small dim amber or cyan status lights, long dead and dusty, adult military sci-fi; detail that still reads when shrunk to 128 px a cell. Not cartoon, no thick outlines, no bright saturated flat colours. Each piece must read as its own thing: never repeat one design across pieces.
View: strictly top-down. Only top surfaces are visible: never a front face or side wall in perspective. Soft upper-left object-local light.
Background: true transparent background with clean alpha; no floor, base tile, shadow slab, border, labels or text (a mimic board's diagram has no readable letters).
"""

EXTRA = {
    "rooms-boards-a": "Each mimic board is a wall-mounted control-room board seen from above: a shallow steel cabinet along the wall, its long face a dark mimic panel showing a schematic of its own line in coloured lines, nodes and small lamps, all different: ",
    "rooms-boards-b": "Each mimic board is a wall-mounted control-room board seen from above: a shallow steel cabinet along the wall, its long face a dark mimic panel showing a schematic of its own line in coloured lines, nodes and small lamps, all different: ",
    "rooms-instruments-a": "Each is a lab test instrument on its own base, different in shape: ",
    "rooms-instruments-b": "Each is a lab test instrument or bench on its own base, different in shape: ",
    "rooms-stocks-a": "Each is the load on a store shelf (seen from above, the stock itself on a low steel shelf), different: ",
    "rooms-stocks-b": "Each is the load on a store shelf (seen from above, the stock itself on a low steel shelf), different: ",
}


# What each piece looks like from above, where the list's one line is too thin for ImageGen.
DETAIL = {
    "qubit-dewar": "a squat insulated cryogen dewar (round white-frosted lid with a vent stack) in a test frame with cable trunks and a small readout panel",
    "incubator": "a boxy incubator cabinet, its top a glass inspection panel over racked culture flasks with a faint green glow, control strip at the front",
    "core-probe": "a probe station: a flat chuck holding a square lattice wafer under a gantry arm with fine probe needles, a monitor at the back",
    "coil-test-rig": "a toroidal copper field coil standing in a rectangular steel test frame with clamps, heavy power leads trailing to the back",
    "magnet-bench": "a curved synchrotron magnet segment lying on a granite measuring bench, a laser tracker head on a post beside it",
    "core-sample-rack": "a low rack of long split cylindrical rock cores in trays, grey and rust-banded stone, labels as plain tags",
    "assay-bench": "a reagent bench: rows of small stoppered bottles in a tray, a titration stand and a sealed glove box at one end",
    "tally-bench": "an inspection bench: a lit inspection plate with a scanning arm, a stack of totes at one side",
    "charge-test-cell": "a small armoured test cell: a thick steel box with a heavy bolted lid and a blast vent, hazard banded",
    "assay-furnace": "a small assay furnace: a refractory brick block with a round crucible port glowing faintly, tongs on a hook, a vent hood",
    "sorter-module": "a robotic sorter head: a three-armed rotary picker on a square base, chutes and a reject flap, cables coiled",
    "cryogen-dewars": "a row of five small frost-rimmed dewar flasks", "nutrient-drums": "three green-banded nutrient drums",
    "feedstock-canisters": "five sealed grey powder feedstock canisters", "rack-module-crates": "three foam-packed module crates, lids off one",
    "coolant-drums": "three blue-banded coolant drums", "cable-drums": "two big drums of thick superconducting cable",
    "drill-rods": "a bundle of long steel drill rods on two cradles", "reagent-drums": "three yellow hazard-banded reagent drums",
    "spool-cores": "two large empty fibre spool cores", "emitter-cases": "three hard cases for holo emitters, one open",
    "tote-stacks": "four stacked plastic dispatch totes", "charge-cases": "three armoured charge cases with red bands",
    "ingot-stacks": "stacks of dull cast metal ingots",
}


def brief(fam):
    rows, row, out = [], [], []
    for e in rooms_list.FAMILIES[fam]:
        if e is None:
            rows.append(row); row = []
        else:
            row.append(e)
    rows.append(row)
    k = 0
    lines = [HEAD]
    if fam in EXTRA:
        lines.append(EXTRA[fam] + "see the list.")
    names = ["top row", "second row", "third row", "fourth row"]
    for r, entries in enumerate(rows):
        lines.append(f"Subjects, {names[r]}, left to right:")
        for pid, w, h, kind, layer, name, what in entries:
            k += 1
            extra = ""
            if layer == "floor":
                extra = " It is floor level and open in the middle."
            elif layer == "overhead":
                extra = " It hangs overhead and is open in the middle: legs and beams only."
            detail = f" Seen from above: {DETAIL[pid]}." if pid in DETAIL else ""
            lines.append(f"{k}. {pid} ({w}x{h}): {what}{detail}{extra}")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    for fam in rooms_list.FAMILIES:
        open(f"prompts/{fam}.prompt.txt", "w", encoding="utf-8").write(brief(fam))
        print(fam)
