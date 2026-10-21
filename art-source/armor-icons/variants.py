"""Armor patterns that get their own armory image instead of sharing their family's.

  python art-source/armor-icons/variants.py         writes prompts/<pattern-slug>.prompt.txt for every variant

A variant wears its family's battle layers, so its armory image is generated as a variant of the
family's accepted armory image: that image is the base design, composition and finish, the battle
layers stay the identity authority, and the brief names only what the pattern's catalog text says
is different (an added shield, a medical harness, a survey array, patched recovered plate).
"""
from briefs import PROMPTS
from families import ROOT, slug

# pattern slug (id without "armor.") -> (family, what makes this pattern different)
VARIANTS = {
    'cordon-shell': ('MILITIA', """Cordon surplus shell: an obsolete station-security cuirass sold off after its seals, shock liners or liability tags failed inspection, made wearable again by a local armourer. Make it older and cruder than the Ward kit: a dented rigid station-security chest cuirass in place of the fabric carrier, mismatched replacement shoulder plates of different metals, a cracked and taped seal ring at the collar, a faded yellow inspection tag tied on with wire (no readable text), tired joints wrapped in tape, a scratched helmet with a taped visor. Fewer pouches. Clearly protection no professional force would trust."""),
    'riot-shell': ('MILITIA', """Kestrel riot shell: Tri-Tachyon corporate security surplus built to walk a picket line into a crowd, sold by the container. Keep the grey-green and steel palette but make the cuirass smoother and more uniform, with a full-face riot helmet with a clear face shield, and add the defining piece: a tall rectangular ballistic riot shield standing upright beside the cuirass, grey-green with a narrow dark vision slot and a scuffed steel rim, its handle side away from the viewer. Little powered assist: no servos or power packs."""),
    'orderly': ('MILITIA', """Orderly aid harness: a Luddic Church parish infirmary kit for the section's stretcher bearer. Keep the grey-green carrier and steel shoulder plates, but replace the weapon pouches with medical stowage: rolled dressings, two plasma pouches with clear bags and tubing, a folded canvas stretcher strapped diagonally behind the carrier (two long pale aluminium poles with worn wooden hand grips at the ends and off-white canvas rolled tight between them, unmistakably a stretcher, never a bundle of tubes or barrels), and an off-white aid armband and helmet patch bearing a small ochre sunburst (no red cross, no text). Practical and humble, a little cleaner than the security kit."""),
    'lashplate-harness': ('OUTLAW', """Lashplate salvage harness: a cargo frame and pressure web with slabs of whatever a breaker crew could drill and wire into place. Cruder and cheaper than the Blackforge rig: an open tubular cargo frame worn like a harness, a pressure-web of straps, and a few uneven slabs of brick-red, rust and bare steel plate drilled through and wired on with visible twisted wire, exposed hoses and lines, badly balanced. The helmet is a scuffed salvage pressure helmet with one replacement plate. Expendable-hand protection; no spikes or skulls."""),
    'packframe': ('OUTLAW', """Packframe support harness: an unpowered cargo frame with its load arms cut short and a launch rail welded where a crate used to sit. Keep the outlaw brick-red, rust and soot palette on its few plates, and make the defining piece a rugged tubular backpack frame with two stubby cut-down load arms and a short steel launch rail welded across its top carrying one olive rocket with a yellow-and-black hazard band. The frame stands upright behind the vest. Independent crew work: bolts, welds, no powered parts."""),
    'pathfinder': ('BLUE_SCOUT', """Pathfinder recon suit: the Hegemony does not buy corporate sensor suits, it strips a Legionary to the frame and hangs a surveying array off it. Keep the dark navy and slate plate palette so it matches the battle sprite, but make it blunter and military rather than corporate: squared rugged plates with exposed frame members, khaki Hegemony stencil marks (no letters), and a boxy surveying array on a short mast clamped to the back of the vest: a rangefinder box, a small dish and a folded antenna. Heavier, louder, easy to fix."""),
    'outrider': ('BLUE_SCOUT', """Outrider deep-recon suit: a Persean League coalition scout suit a section can live in for a week. Keep the dark navy and slate plate palette, add a large rugged endurance pack with water bladders and a bedroll standing behind the vest, a tall folding net antenna and a compact array block, faded teal coalition maintenance tags and a small plate of three abstract bars (no letters). Long-endurance, well kept, more kit than armour."""),
    'redoubt': ('CHARCOAL', """Redoubt siege plate: Legionary-pattern plate with every weight limit taken off, for a marine who is not expected to go anywhere. Keep the charcoal and grey-green palette, teal lamps and orange marks, but double the plate: thick stacked slabs over the chest and belly, a high armoured gorget, huge layered pauldrons and heavy forearm slabs, a helmet with a thick armoured brow and a narrow visor. Show it as a massive cuirass with its helmet set down in front and its heavy gauntlets laid beside it. Static, miserable to walk in, built to hold a breach."""),
    'vigil': ('FURNACE_LINE', """Vigil suppression shell: Sindrian Diktat state-security armour, a riot shell built up to stop a rifle round, issued to people expected to go through a door on a schedule. Keep the furnace-crimson, soot-black, copper and amber palette, make the plates smoother and the helmet a closed full-face security helmet with a dark visor, and add the defining piece: a heavy crimson-and-black ballistic door shield with an amber-lit vision slot standing upright beside the suit. Lighter than the Lion's Guard, heavier than a riot shell; no flames or skulls."""),
    'corpsman': ('ARMY_GREEN', """Corpsman field rig: the Hegemony's section corpsman, equipped properly. Keep the olive-drab Legionary suit, add a compact powered harness carrying a surgeon's worth of consumables: a refrigerated medical case on the back with a small pale frost-blue status lamp, rows of olive medical pouches, a clamp rack of injector tubes on the chest, and an off-white helmet band and shoulder patch with a small khaki medical mark of a plain ring and bar (no red cross, no text). Both hands free: nothing in the gauntlets."""),
    'specter-recovered': ('SPECTER_HEAVY', """Recovered Specter battlesuit: a rebuilt corporate breach shell with mismatched composite panels and tired actuators. Keep the faceted blue-grey Specter shell and its shapes, but replace several panels with mismatched ones (one dull olive pauldron plate, a primer-grey chest wedge, a bare unpainted forearm plate), add patch welds, scorches and filler over old damage, a frayed cable wrap at one joint, and only one working cyan strip. Still clearly a Specter, clearly second-hand."""),
    'arbalest-recovered': ('ARBALEST_HEAVY', """Recovered Arbalest battlesuit: a salvaged support shell with replacement plate and a worn load-bearing frame. Keep the olive faceted Arbalest shell and its shoulder battery, but the battery is smaller, two short tubes instead of four, on a scuffed replacement bracket; several plates are mismatched replacements (a grey-green pauldron, a primer-grey chest wedge), with patch welds, worn paint and a frayed hose wrap. Still clearly an Arbalest, clearly a downgrade."""),
}

FRAMING = """Use case: style-matched variant of an existing product render. Asset: the armory catalogue image of one marine armour suit, for a gritty military dark-science-fiction tactical game in the Starsector setting.

Image 1 is the finished armory image of the base suit this pattern belongs to: it is the authority for the design, palette, plate shapes, markings, composition, camera, lighting and realistic hand-painted finish. Produce a variant of this same suit that a player recognises at once as a sibling of Image 1, changing only what is described below and keeping everything else as Image 1 has it.

Image 2 shows how this suit is drawn in battle, from directly overhead (left: the torso and shoulder plates; right: the helmet top). The variant must still match it in palette and silhouette character. Never show the overhead view itself.

{variant}

Square 1:1 canvas with every piece fully inside the frame and a clear margin. True transparent background, including the gaps between pieces; no ground, no cast-shadow slab, no border, no text, letters, numbers, logos or watermark, no person or face inside the suit, no weapon held or worn except an item named above."""


def variant_brief(pattern):
    return FRAMING.format(variant=VARIANTS[pattern][1])


def variant_icon_path(pattern):
    return f'graphics/ui/armory/armor-{pattern}.png'


def base_image(pattern):
    """The family's accepted armory image, the variant's design reference."""
    from families import icon_path
    return ROOT / 'mod' / icon_path(VARIANTS[pattern][0])


def identity_ref(pattern):
    return ROOT / 'art-source/armor-icons/refs' / f'{slug(VARIANTS[pattern][0])}-identity.png'


def main():
    PROMPTS.mkdir(parents=True, exist_ok=True)
    for pattern in VARIANTS:
        (PROMPTS / f'{pattern}.prompt.txt').write_bytes((variant_brief(pattern) + '\n').encode('utf-8'))
    print(f'{len(VARIANTS)} variant briefs in {PROMPTS.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
