"""The per-family art direction for the armory images, and the brief each generation is sent.

  python art-source/armor-icons/briefs.py           writes prompts/<slug>.prompt.txt for every family

Each brief is the shared framing below plus the family's own identity paragraph. Composition comes
from one of the four former tier icons by weight class (unpowered kit, light kit, line suit, power
suit), so a family's image keeps the armory's established product-shot look while its design comes
from its own battle layers (refs/<slug>-identity.png).
"""
from families import FAMILIES, ROOT, slug

PROMPTS = ROOT / 'art-source/armor-icons/prompts'
TIER = 'art-source/armor-icons/composition/armor-tier-{}.png'

# Composition reference and the sentence that names the arrangement.
LAYOUTS = {
    'kit': (TIER.format('1-field-kit'),
            'a folded set of field clothing and its webbing laid out together, with a pair of boots, like a quartermaster issue bundle'),
    'light': (TIER.format('2-scout'),
              'a torso vest or cuirass standing upright, its helmet set down in front at the lower left, a pair of gloves and a few pouches at the lower right'),
    'line': (TIER.format('3-combat'),
             'one complete upper-body suit displayed as on an invisible stand, seen from the front: helmet on top, cuirass, pauldrons and upper-arm and forearm plates, down to the belt, with no person inside'),
    'power': (TIER.format('4-heavy'),
              'a massive powered-armour cuirass with its pauldrons standing upright at the upper right, its helmet set down in front at the lower left, and the two armoured gauntlets and forearms laid at the lower right'),
}

# family -> (layout, identity paragraph)
FAMILY_ART = {
    'ARMORLESS': ('kit', """Domain-pattern vac fatigues: the unarmoured working uniform of crews, militias and cash-poor infantry. Faded olive-drab cloth shirt and trousers with sealed cuffs and collar, a dark undershirt, and a tan-olive canvas web harness with shoulder straps, chest buckles and small pouches exactly as the overhead torso shows it. No hard plates, no helmet: the overhead head is a bare head of short brown hair, so there is no cap or helmet in this set at all. Worn brown leather boots. Plain, cheap, practical."""),
    'MILITIA': ('light', """Ward security kit and Cordon surplus shell: locally assembled frontier protection below marine grade. A grey-green fabric plate carrier with a square front plate and a brown reinforced-fabric collar, mismatched dull-steel and rust-brown rounded shoulder plates strapped on with buckles, a small faded tan patch with a tiny emblem, patched and re-stitched. The helmet is a sealed grey-green helmet with a brown leather crown panel and a small pale emblem, matching the overhead helmet. Brown work gloves. Tired, mismatched, serviceable, not military issue."""),
    'OUTLAW': ('light', """Blackforge patchwork rig: cut-down security armour, industrial exoframe, ship plate and hand-fitted seals from pirate shops. Asymmetric: faded brick-red and oxblood plates on one side, oxidised brown and soot-black on the other, a black heavy collar ring, mismatched bolts, welded repairs, a small stencilled number on one shoulder plate, and tiny scavenged cyan lamps. The helmet is red-orange on one half and soot-black steel on the other, with a pale scratched panel and small cyan side lamps, matching the overhead helmet. Grimy black gloves. Hard-used and rebuilt, never comedic: no spikes, skulls or chains."""),
    'BLUE_SCOUT': ('light', """Janus scout suit: Tri-Tachyon recon armour of composite segments under a sensor hood and electronic-warfare weave. Very dark desaturated navy and slate blue-grey segmented plates over a near-charcoal base, a heavy rounded collar, gunmetal hardware, chipped paint and matte wear, tiny faded tan markings. The helmet is a smooth slate-blue scout helmet with a small sensor block on the crown, matching the overhead helmet. Dark gloves and slim pouches. Fast and quiet; no royal blue, no glossy plastic, no superhero styling."""),
    'CHARCOAL': ('line', """Bastion line armour: Kazeron's Persean League coalition line plate, layered laminate that member worlds can swap between suits. Dark charcoal and desaturated grey-green rounded plates, a thick padded collar ring, broad rounded pauldrons, muted teal hardware and small teal status lamps at the shoulders, and tiny warm orange markings. The helmet is a smooth grey-green dome with a small orange mark, matching the overhead helmet. Dependable and plain, modest weight."""),
    'ARMY_GREEN': ('line', """Legionary line suit: the Hegemony's mass-issued interchangeable line armour. Faded olive-drab and forest-green rounded plates over charcoal, a thick collar ring, broad rounded pauldrons, dull khaki worn edges and small khaki rank flashes, black hardware, dusty seams and campaign wear. The helmet is a plain olive-green dome, matching the overhead helmet. Disciplined, field-serviceable, built for formations far from a depot."""),
    'AEGIS_COMPOSITE': ('line', """Aegis composite line suit: Tri-Tachyon's sleek corporate composite line armour with predictive threat displays and powered balance. Near-black graphite base, deep desaturated midnight-blue tapered interlocking laminate plates, narrow articulated shoulder shells, compact powered-balance housings, fine conduit channels, very restrained cyan status insets, one tiny abstract white datum mark, and an asymmetric electronic-warfare module on the wearer's left shoulder. The helmet is a slim tapered graphite and midnight-blue helmet with a slim sensor brow and a tiny cyan inset, matching the overhead helmet. Slimmer and more precise than any heavy suit; no royal blue, no glow bloom."""),
    'PALATINE': ('line', """Palatine legacy line suit: Luddic Church powered armour rebuilt from inherited pressure shells. Broad hand-fitted weathered ivory ceramic plates over a muted sage and deep olive frame, layered rounded pauldrons with olive edging, aged brass fasteners and small brass hinge plates, faded ochre cloth seals hanging at the shoulders, dark iron joints, and a small brass sunburst devotional plate in the centre of the chest. The helmet is an ivory ceramic dome with sage bands, brass fasteners and a small forward sunburst boss, matching the overhead helmet. Dignified, old and carefully maintained; no glowing electronics, no fantasy robes, no wings or halo."""),
    'FURNACE_LINE': ('line', """Furnace state line suit: Sindrian Diktat petro-industrial powered line armour. Thick angular sacrificial laminate in furnace-dark crimson and burgundy, squared shoulder tanks, a soot-black frame, oily gunmetal housings, exposed black cooling loops, heat-stained copper fittings, tiny amber warning insets and a central square rank plate with one abstract geometric mark. The helmet is a squared crimson state helmet with a soot-black rim, copper cooling channels and an amber forward status bar, matching the overhead helmet. Rigid, mass-produced, weighty and disciplined; no flames, skulls or parade gloss."""),
    'REAVER': ('line', """Reaver reinforced rig: a pirate Blackforge frame rebuilt with powered bracing and ship plate. Asymmetric overlapping ship-plate slabs in oxidised brown and faded brick red, soot-black and bare dark steel, one replacement mustard-yellow panel, a civilian load-bearing exoframe visible between the panels, mismatched powered braces, welded ribs, patched seals, cable guards, a scavenged sensor box and tiny cyan indicators. The helmet is asymmetric, brick-red on one side and dark oxidised steel on the other, with a welded brow and a tiny cyan lamp, matching the overhead helmet. Coherent enough for powered combat, unmistakably field-rebuilt; no spikes, skulls or chains."""),
    'RED_ELITE': ('power', """XIV heavy battlesuit: the Hegemony's Domain-spec breaching suit of dense ceramic, powered bracing and standardised hardpoints. Near-charcoal structure with dark desaturated oxblood-red plates, blackened gunmetal joints, muted brass fittings, a faded bone-white veteran chevron on the chest, a heavy black collar ring and huge rounded pauldrons, extensive disciplined field wear. The helmet is an oxblood dome with a dark visor band and a small brass mark, matching the overhead helmet. Massive and slow, built to survive punishment."""),
    'SPECTER_HEAVY': ('power', """Specter composite battlesuit: Tri-Tachyon's corporate breach shell, a walking tank built around high-output joints. Cool blue-grey titanium-steel faceted plates over a black ribbed and segmented joint backing, broad tapered angular pauldrons, a split chest of hexagonal plates over two square abdominal blocks, flush recessed cyan status strips on the pauldrons and abdomen, near-black graphite frame. The helmet is a narrow faceted blue-grey steel helmet with a dark central ridge, matching the overhead helmet. Heavy but sleek, precise and matte; no superhero styling, no glossy plastic."""),
    'ARBALEST_HEAVY': ('power', """Arbalest support battlesuit: the Hegemony's powered support shell built around a missile rack, the same articulated shell family as the Specter in Hegemony olive paint. Muted olive-green and grey-green faceted plates over a black ribbed and segmented joint backing, broad tapered angular pauldrons, a split chest of hexagonal plates over two square abdominal blocks, small recessed status strips, worn edges. Add one compact boxy shoulder-mounted rocket battery of four short tubes on its right pauldron, part of the suit. The helmet is a narrow faceted olive helmet with a dark central ridge, matching the overhead helmet. Slow, purposeful, military."""),
    'BOARDING_JACK': ('power', """Boarding Jack rebuilt frame: a pirate-yard loading frame rebuilt for ship boarding. Scuffed safety-orange painted plate over dark steel, welded and riveted mismatched panels, a squarish torso with a round neck ring, two guarded steel hoses running down the chest to a ribbed steel vent block at the belly, a deliberately mismatched pair of shoulders: a long, wide welded slab of a boarding shield on the left with a small grey ram block bolted to its face and a pale yellow hand-lettered service label, and a smaller rounded cargo cap with steel bands on the right. Tiny cyan indicator lamps. The helmet is a round orange pressure helmet with a steel brow handle and two round dark viewports low at the front, matching the overhead helmet. Industrial, crude, heavy; no spikes, skulls or chains."""),
    'BULWARK_HEAVY': ('power', """Bulwark coalition battlesuit: the Persean League's heavy suit of modular plate blocks and replaceable actuators. Dark slate and muted navy-grey rectangular laminate blocks, large rectangular pauldrons with dull tan edge plates, standardised locking rails, replaceable actuator cassettes, segmented chest blocks, gunmetal joints, restrained faded teal maintenance tags and lamps, and a small coalition identification plate of three abstract bars. The helmet is a segmented slate and navy-grey helmet with a forward sensor cassette and teal lamps, matching the overhead helmet. Balanced, reproducible, field-serviceable; no flags or heraldry."""),
    'RELIQUARY_HEAVY': ('power', """Reliquary consecrated battlesuit: an immense artisan-restored Luddic Church legacy machine entrusted to a Knight of Ludd. Dense overlapping weathered ivory ceramic plates, rounded fortress-like pauldrons, deep olive understructure, dark antique iron frame, hand-fitted aged brass locking bands, faded ochre seals, carefully repaired seams, and a central square brass reliquary plate bearing an abstract sunburst. The helmet is a massive ivory ceramic helmet with olive banding, brass locks and a forward sunburst boss, matching the overhead helmet. Extremely heavy, old and sacred through stewardship; no glow, wings, halo, robes or crown."""),
    'LIONS_MANTLE': ('power', """Lion's Mantle guard battlesuit: the Sindrian Lion's Guard's prestigious walking tank. Deep polished crimson layered sacrificial plate, enormous angular pauldrons, blackened gunmetal frame, oversized black cooling loops, heat-stained copper heat exchangers, polished brass edge guards and trim lines, tiny amber indicators, and a restrained abstract golden mane chevron on the chest. The helmet is a crimson guard helmet with a gunmetal rim, brass edge guards and copper channels, matching the overhead helmet. Broad, conspicuous, top-heavy and privileged, formal yet battle-ready; no literal lion, cape, crown or flames."""),
    'FOUNDRY_BREAKER': ('power', """Foundry-breaker industrial rig: a pirate cargo exoskeleton buried under welded ship plate. Huge mismatched rectangular shoulder slabs, exposed industrial lift-frame rails, illicit oversized servos, heavy cable bundles, crude shock padding, irregular weld beads and bolted replacement panels. Soot black and oxidised dark brown with faded industrial-orange panels, bare steel, old yellow-and-black hazard patches and tiny scavenged cyan indicators. The helmet is an oversized improvised pressure shell from an industrial hardhat frame and welded plate, faded orange crown, crude reinforced brow and a small hazard sensor box, matching the overhead helmet. Brutal and plausible, never comedic junk."""),
}

FRAMING = """Use case: style-matched product render. Asset: the armory catalogue image of one marine armour suit, for a gritty military dark-science-fiction tactical game in the Starsector setting. The image shows the suit as a product shot: {arrangement}.

Image 1 is the composition, camera, lighting and finish reference only: match its arrangement of pieces, front three-quarter viewing angle, the scale the set fills in the frame, soft studio light from the upper left and its realistic hand-painted finish. Do NOT copy its suit design, colours or markings.

Image 2 is the identity authority: this same suit as the game draws it in battle, seen from directly overhead (left: the torso and shoulder plates, with an empty hole where the helmet sits; right: the top of the helmet). The armory image must read unmistakably as this same suit seen from the front: the same palette and where each colour sits, the same plate and pauldron shapes, the same markings, lamps and hardware, the same faction character. Translate the overhead plates into their front-view forms; never show the overhead view itself.

{identity}

Square 1:1 canvas with every piece fully inside the frame and a clear margin. True transparent background, including the gaps between pieces; no ground, no cast-shadow slab, no border, no text, letters, numbers, logos or watermark, no person or face inside the suit, no weapon."""


def brief(family):
    layout, identity = FAMILY_ART[family]
    return FRAMING.format(arrangement=LAYOUTS[layout][1], identity=identity)


def composition_ref(family):
    return LAYOUTS[FAMILY_ART[family][0]][0]


def main():
    assert set(FAMILY_ART) == set(FAMILIES)
    PROMPTS.mkdir(parents=True, exist_ok=True)
    for family in FAMILIES:
        (PROMPTS / f'{slug(family)}.prompt.txt').write_text(brief(family) + '\n', encoding='utf-8')
    print(f'{len(FAMILIES)} briefs in {PROMPTS.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
