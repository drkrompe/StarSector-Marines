# Faction armor ImageGen source prompts

Generated with the built-in ImageGen tool on 2026-08-26. Every prompt used an
accepted retained layer as its geometry and style reference. The delivered PNGs in each
named subdirectory are the alpha-cleaned ImageGen sources consumed by
`build_variants.py`; runtime normalization must not be used as a new generation
reference.

All matte-backed outputs were cleaned with the imagegen skill's
`remove_chroma_key.py --auto-key corners --soft-matte --despill` helper. Aegis head
arrived with native alpha and was retained without chroma processing.

## Aegis composite

References: `../../blue-scout-body.png` and `../../blue-scout-head.png`.

### Body

> Edit the referenced accepted modular infantry body layer into the Aegis composite line suit for a Starsector-inspired ground-combat game.
>
> Treat the reference image as the exact geometry, registration, scale, pixel-art rendering style, and lighting-direction target. Produce exactly one isolated strict 90-degree zenith orthographic shoulder-and-upper-body armor layer, facing north/up. Preserve the centered empty oval helmet socket so a separate rotating helmet layer can fit inside it. Keep the complete armor silhouette centered with generous padding.
>
> Change the armor design, not merely its hue: sleek Tri-Tachyon corporate composite construction, interlocking tapered laminate plates, narrow articulated shoulder shells, compact powered-balance housings, fine neural/HUD conduit channels, and a restrained asymmetric electronic-warfare module on the wearer's left shoulder. The suit must read as a fast standard line suit, slimmer and more precise than a heavy battlesuit while still clearly powered and combat-rated.
>
> Palette: near-black graphite foundation, deep desaturated midnight-blue composite plates, cool gunmetal joints, extremely restrained cyan status insets, and one tiny abstract white corporate datum mark with no letters. Materials are matte ceramic-composite and machined metal with tight seams and modest operational wear.
>
> BACKGROUND REQUIREMENT: place the object on one perfectly uniform solid #ff00ff magenta background covering every background pixel edge-to-edge. No gradient, vignette, floor, shadow, ambient halo, reflection, texture, transparency checkerboard, or color variation in the background.
>
> No head, helmet, face, neck, weapon, hands, arms, feet, legs, person, readable text, logo, scenery, perspective, isometric angle, vertical side planes, glow bloom, royal blue, superhero styling, or additional objects.

### Head

> Edit the referenced accepted modular infantry helmet layer into the helmet for the Aegis composite line suit. Treat the reference image as the exact geometry, registration, scale, pixel-art rendering style, north/up orientation, and centered rotation-pivot target. Produce exactly one isolated helmet top in strict 90-degree zenith orthographic view, facing north/up, centered with generous padding. Preserve a compact oval footprint that fits the modular body socket.
>
> Change the design: sleek Tri-Tachyon corporate composite helmet, tapered overlapping graphite and deep desaturated midnight-blue plates, a slim forward sensor brow, compact neural-interface housings along the sides, fine machined seams, one tiny cyan north-facing status inset, and one tiny abstract white datum mark with no letters. It should match a fast standard line suit, not a massive heavy shell. Matte ceramic-composite, cool gunmetal joints, modest operational wear.
>
> BACKGROUND REQUIREMENT: one perfectly uniform solid #ff00ff magenta field covering every background pixel edge-to-edge. No gradient, vignette, floor, shadow, halo, reflection, texture, transparency checkerboard, or color variation. No shoulders, torso, collar, neck ring, face, weapon, hands, person, readable text, logo, scenery, perspective, isometric angle, side view, glow bloom, royal blue, superhero styling, or additional objects.

## Palatine legacy suit

References: `../../army-green-body.png` and `../../army-green-head.png`.

### Body

> Edit the referenced accepted modular infantry body layer into the Palatine legacy line suit. Treat the reference as the exact geometry, registration, scale, pixel-art style, lighting, and strict 90-degree zenith north/up target. Produce exactly one isolated shoulder-and-upper-body armor layer with the complete silhouette centered and a centered empty oval helmet socket. Change the design into carefully husbanded Luddic Church powered armor: broad hand-fitted ceramic plates over an old pressure frame, layered rounded pauldrons, visible artisan brass fasteners, conservative joint housings, and a small sunburst-shaped devotional plate with no readable text. It must read as a standard line suit, not a heavy battlesuit. Palette of weathered ivory ceramic, muted sage and deep olive structure, dark iron, aged brass, and restrained faded ochre cloth seals. Maintained and dignified but visibly old; no glowing electronics.
>
> BACKGROUND REQUIREMENT: one perfectly uniform solid #ff00ff magenta field covering every background pixel edge-to-edge, or genuine transparent alpha if the reference transparency is preserved. No gradient, vignette, floor, shadow, halo, reflection, texture, checkerboard, or background variation. No head, helmet, face, neck, weapon, hands, arms, legs, person, readable text, logo, scenery, perspective, vertical side view, spikes, fantasy robes, ornate cathedral architecture, or extra objects.

### Head

> Edit the referenced accepted modular helmet layer into the Palatine legacy line helmet. Preserve exact registration, scale, pixel-art style, centered rotation pivot, strict 90-degree zenith view, compact oval footprint, and north/up orientation. Exactly one isolated helmet top. Design: artisan-restored Luddic Church pressure helmet with overlapping weathered ivory ceramic crown plates, muted sage structural bands, dark iron rim, aged brass fasteners, a small forward sunburst-shaped devotional boss with no readable text, and conservative mechanical seams. Dignified, old, line-weight rather than heavy, with no glowing electronics.
>
> BACKGROUND REQUIREMENT: one perfectly uniform solid #ff00ff magenta field covering every background pixel edge-to-edge, or genuine transparent alpha if the reference transparency is preserved. No gradient, vignette, floor, shadow, halo, reflection, texture, checkerboard, or background variation. No shoulders, torso, collar, neck ring, face, weapon, hands, person, readable text, logo, scenery, perspective, side view, fantasy helm, halo, spikes, or extra objects.

## Furnace state line suit

References: `../../army-green-body.png` and `../../army-green-head.png`.

### Body

> Edit the referenced accepted modular infantry body layer into the Furnace state line suit. Preserve the exact registration, scale, pixel-art style, strict 90-degree zenith north/up projection, centered complete silhouette, and centered empty oval helmet socket. Exactly one isolated shoulder-and-upper-body armor layer. Redesign it as Sindrian Diktat petro-industrial powered line armor: thick angular sacrificial laminate panels, squared shoulder tanks, exposed black cooling loops, stamped state-manufacture housings, rigid bilateral construction, and a conspicuous central rank plate with one abstract geometric mark and no letters. Standard line weight, not a heavy battlesuit. Palette of furnace-dark crimson and burgundy laminate, soot-black frame, oily gunmetal, heat-stained copper, and tiny amber warning insets. Hot, weighty, disciplined, mass-produced, visibly maintained.
>
> BACKGROUND REQUIREMENT: one perfectly uniform solid #ff00ff magenta field covering every background pixel edge-to-edge, or genuine transparent alpha if the reference transparency is preserved. No gradient, vignette, floor, shadow, halo, reflection, texture, checkerboard, or background variation. No head, helmet, face, neck, weapon, hands, arms, legs, person, readable text, logo, scenery, perspective, vertical side view, flames, skulls, glossy parade armor, or extra objects.

### Head

> Edit the referenced accepted modular helmet layer into the Furnace state line helmet. Preserve exact registration, scale, pixel-art style, centered rotation pivot, compact oval footprint, strict 90-degree zenith view, and north/up orientation. Exactly one isolated helmet top. Design: squared Sindrian state pressure helmet with furnace-dark crimson laminate crown, soot-black structural rim, oily gunmetal side housings, two restrained copper cooling channels, a compact amber forward status bar, and one abstract geometric state mark with no readable letters. Standard line weight, disciplined and petro-industrial rather than ornate.
>
> BACKGROUND REQUIREMENT: one perfectly uniform solid #ff00ff magenta field covering every background pixel edge-to-edge, or genuine transparent alpha if the reference transparency is preserved. No gradient, vignette, floor, shadow, halo, reflection, texture, checkerboard, or background variation. No shoulders, torso, collar, neck ring, face, weapon, hands, person, readable text, logo, scenery, perspective, side view, flames, horns, skulls, or extra objects.

## Reaver reinforced rig

References: `../../outlaw-body.png` and `../../outlaw-head.png`.

### Body

> Edit the referenced accepted modular infantry body layer into the Reaver reinforced rig. Preserve exact registration, scale, pixel-art style, strict 90-degree zenith north/up projection, centered complete silhouette, and centered empty oval helmet socket. Exactly one isolated shoulder-and-upper-body armor layer. Redesign it as an outlaw powered line rig rebuilt from a Blackforge frame: asymmetrical overlapping ship-plate slabs, a civilian load-bearing exoframe visible between panels, mismatched powered braces, welded reinforcement ribs, patched pressure seals, cable guards, and one scavenged sensor box. It must feel faster and more coherent than a crude heavy rig while remaining visibly improvised. Palette of oxidized brown, faded brick red, soot black, bare dark steel, one replacement mustard panel, and tiny scavenged cyan indicators. Hard-used and repaired, not comedic junk.
>
> BACKGROUND REQUIREMENT: one perfectly uniform solid #ff00ff magenta field covering every background pixel edge-to-edge, or genuine transparent alpha if the reference transparency is preserved. No gradient, vignette, floor, shadow, halo, reflection, texture, checkerboard, or background variation. No head, helmet, face, neck, weapon, hands, arms, legs, person, readable text, logo, scenery, perspective, vertical side view, spikes, skulls, chains, gore, or extra objects.

### Head

> Edit the referenced accepted modular helmet layer into the Reaver reinforced rig helmet. Preserve exact registration, scale, pixel-art style, centered rotation pivot, compact oval footprint, strict 90-degree zenith view, and north/up orientation. Exactly one isolated helmet top. Design: asymmetric outlaw pressure helmet rebuilt from mismatched security plates, faded brick-red left crown, oxidized dark-steel right crown, welded brow reinforcement, patched seals, one scavenged side sensor, exposed fasteners, and a tiny cyan forward orientation lamp. Coherent enough for powered line combat but unmistakably field-rebuilt.
>
> BACKGROUND REQUIREMENT: one perfectly uniform solid #ff00ff magenta field covering every background pixel edge-to-edge, or genuine transparent alpha if the reference transparency is preserved. No gradient, vignette, floor, shadow, halo, reflection, texture, checkerboard, or background variation. No shoulders, torso, collar, neck ring, face, weapon, hands, person, readable text, logo, scenery, perspective, side view, spikes, skulls, horns, gore, or extra objects.

## Specter composite battlesuit

References: `../../red-heavy-body.png` and `../../red-heavy-head.png`.

### Body

> Edit the referenced accepted modular heavy infantry body layer into the Specter composite battlesuit. Preserve exact registration, scale, pixel-art style, strict 90-degree zenith north/up projection, centered complete silhouette, and centered empty oval helmet socket. Exactly one isolated shoulder-and-upper-body armor layer. Redesign it as a Tri-Tachyon corporate walking-tank shell: broad but aerodynamically tapered composite shoulders, layered graphite ablative wedges, high-output articulated joint housings, compact dorsal power spine, neural-control channels, flush sensor apertures, and an asymmetric drone-control blister. Heavy and unmistakably armored, yet sleeker and more mobile-looking than conventional slab armor. Palette near-black graphite, deep midnight-blue composite, cool titanium edges, extremely restrained cyan status insets, and one tiny abstract white datum mark without letters. Matte precision construction with tight seams and controlled field wear.
>
> BACKGROUND REQUIREMENT: one perfectly uniform solid #ff00ff magenta field covering every background pixel edge-to-edge, or genuine transparent alpha if the reference transparency is preserved. No gradient, vignette, floor, shadow, halo, reflection, texture, checkerboard, or background variation. No head, helmet, face, neck, weapon, hands, arms, legs, person, readable text, logo, scenery, perspective, vertical side view, superhero armor, glossy plastic, bloom, or extra objects.

### Head

> Edit the referenced accepted modular heavy helmet layer into the Specter composite battlesuit helmet. Preserve exact registration, scale, pixel-art style, centered rotation pivot, strict 90-degree zenith view, oval footprint, and north/up orientation. Exactly one isolated helmet top. Design: broad Tri-Tachyon heavy composite helmet with tapered graphite and midnight-blue plates, reinforced titanium brow, compact neural-control housings, flush sensor apertures, a short cyan forward status slit, and one tiny abstract white datum mark without letters. Heavy but sleek, precise, matte, maintenance-intensive.
>
> BACKGROUND REQUIREMENT: one perfectly uniform solid #ff00ff magenta field covering every background pixel edge-to-edge, or genuine transparent alpha if the reference transparency is preserved. No gradient, vignette, floor, shadow, halo, reflection, texture, checkerboard, or background variation. No shoulders, torso, collar, neck ring, face, weapon, hands, person, readable text, logo, scenery, perspective, side view, superhero styling, glow bloom, or extra objects.

## Bulwark coalition battlesuit

References: `../../charcoal-body.png` and `../../charcoal-head.png`.

### Body

> Edit the referenced accepted modular infantry body layer into the Bulwark coalition battlesuit. Preserve exact registration, pixel-art style, strict 90-degree zenith north/up projection, centered complete silhouette, and centered empty oval helmet socket, but give the armor a broader heavy silhouette within the canvas. Exactly one isolated shoulder-and-upper-body layer. Design a Persean League heavy suit built from modular blocks member worlds can reproduce: large rectangular laminate pauldrons, replaceable actuator cassettes, visible standardized locking rails, practical segmented chest blocks, redundant mechanical connectors, and one small coalition identification plate with three abstract bars and no letters. Balanced walking-tank mass, neither sleek nor crude. Palette dark slate, muted navy-gray, gunmetal, dull tan edge plates, and restrained faded teal maintenance tags. Field-serviceable, scuffed, disciplined.
>
> BACKGROUND REQUIREMENT: one perfectly uniform solid #ff00ff magenta field covering every background pixel edge-to-edge, or genuine transparent alpha if the reference transparency is preserved. No gradient, vignette, floor, shadow, halo, reflection, texture, checkerboard, or background variation. No head, helmet, face, neck, weapon, hands, arms, legs, person, readable text, logo, scenery, perspective, vertical side view, flags, heraldry, spikes, or extra objects.

The first output added a vignette. The final body source used this correction prompt with
that output as its edit target:

> Edit the referenced generated Bulwark coalition battlesuit body asset. Preserve the armor object exactly pixel-for-pixel in design, silhouette, placement, orientation, color, details, empty helmet socket, lighting, and pixel-art style. Change only the background. Replace every background pixel, including the dark vignette, ambient shadow, and the empty helmet-socket interior, with one perfectly uniform solid #ff00ff magenta field edge-to-edge. The magenta must be completely flat: no gradient, texture, shadow, halo, reflection, transparency checkerboard, or color variation. Do not add, remove, crop, repaint, resize, rotate, or otherwise alter any part of the armor. Exactly one armor body object, no other objects.

### Head

> Edit the referenced accepted modular helmet layer into the Bulwark coalition battlesuit helmet. Preserve exact registration, pixel-art style, centered rotation pivot, strict 90-degree zenith view, oval footprint, and north/up orientation, while making it read as a broader heavy shell. Exactly one isolated helmet top. Design: practical League modular helmet with dark slate segmented crown, muted navy-gray side plates, gunmetal rim, replaceable forward sensor cassette, standardized locking rails, dull tan wear edges, and three tiny abstract coalition bars with no letters. Balanced, reproducible, field-serviceable, visibly heavy.
>
> BACKGROUND REQUIREMENT: one perfectly uniform solid #ff00ff magenta field covering every background pixel edge-to-edge, or genuine transparent alpha if the reference transparency is preserved. No gradient, vignette, floor, shadow, halo, reflection, texture, checkerboard, or background variation. No shoulders, torso, collar, neck ring, face, weapon, hands, person, readable text, logo, scenery, perspective, side view, heraldry, spikes, or extra objects.

## Reliquary consecrated battlesuit

References: `../../red-heavy-body.png` and `../../red-heavy-head.png`.

### Body

> Edit the referenced accepted modular heavy infantry body layer into the Reliquary consecrated battlesuit. Preserve exact registration, scale, pixel-art style, strict 90-degree zenith north/up projection, centered complete silhouette, and centered empty oval helmet socket. Exactly one isolated shoulder-and-upper-body armor layer. Redesign it as an immense artisan-restored Luddic Church legacy machine: dense overlapping ivory ceramic plates, rounded fortress-like pauldrons, massive conservative actuator housings, dark antique iron frame, hand-fitted brass locking bands, carefully repaired seams, and a restrained central reliquary plate bearing an abstract sunburst with no readable text. It should read as extremely resistant, deliberate, old, and sacred through stewardship rather than fantasy magic. Palette weathered ivory, deep olive understructure, dark iron, aged brass, faded ochre seals. No glowing electronics.
>
> BACKGROUND REQUIREMENT: one perfectly uniform solid #ff00ff magenta field covering every background pixel edge-to-edge, or genuine transparent alpha if the reference transparency is preserved. No gradient, vignette, floor, shadow, halo, reflection, texture, checkerboard, or background variation. No head, helmet, face, neck, weapon, hands, arms, legs, person, readable text, logo, scenery, perspective, vertical side view, wings, halo, robes, cathedral ornament, spikes, or extra objects.

### Head

> Edit the referenced accepted modular heavy helmet layer into the Reliquary consecrated battlesuit helmet. Preserve exact registration, scale, pixel-art style, centered rotation pivot, strict 90-degree zenith view, broad oval footprint, and north/up orientation. Exactly one isolated helmet top. Design: massive artisan-restored legacy helmet with overlapping weathered ivory ceramic plates, deep olive structural banding, dark antique iron rim, hand-fitted brass locks, repaired seams, and a restrained forward abstract sunburst boss with no letters. Extremely heavy, deliberate, old, and sacred through maintenance, not fantasy magic. No glow.
>
> BACKGROUND REQUIREMENT: one perfectly uniform solid #ff00ff magenta field covering every background pixel edge-to-edge, or genuine transparent alpha if the reference transparency is preserved. No gradient, vignette, floor, shadow, halo, reflection, texture, checkerboard, or background variation. No shoulders, torso, collar, neck ring, face, weapon, hands, person, readable text, logo, scenery, perspective, side view, halo, crown, wings, horns, robes, or extra objects.

## Lion's Mantle guard battlesuit

References: `../../red-heavy-body.png` and `../../red-heavy-head.png`.

### Body

> Edit the referenced accepted modular heavy infantry body layer into the Lion's Mantle guard battlesuit. Preserve exact registration, scale, pixel-art style, strict 90-degree zenith north/up projection, centered complete silhouette, and centered empty oval helmet socket. Exactly one isolated shoulder-and-upper-body armor layer. Redesign it as a prestigious Sindrian Lion's Guard walking tank: enormous angular petrochemical-laminate pauldrons, thick layered crimson sacrificial plate, oversized black cooling loops, copper heat exchangers, rigid state-security housings, polished brass edge guards, and a restrained abstract golden lion-mane chevron with no letters or literal animal. It should be broad, conspicuous, top-heavy, privileged, and heavily maintained. Palette deep polished crimson, blackened gunmetal, heat-stained copper, muted brass, tiny amber indicators. Formal but battle-ready, not fantasy ceremonial armor.
>
> BACKGROUND REQUIREMENT: one perfectly uniform solid #ff00ff magenta field covering every background pixel edge-to-edge, or genuine transparent alpha if the reference transparency is preserved. No gradient, vignette, floor, shadow, halo, reflection, texture, checkerboard, or background variation. No head, helmet, face, neck, weapon, hands, arms, legs, person, readable text, logo, literal lion, scenery, perspective, vertical side view, cape, crown, spikes, flames, or extra objects.

The first output added a vignette. The final body source used this correction prompt with
that output as its edit target:

> Edit the referenced generated Lion's Mantle guard battlesuit body asset. Preserve the armor object exactly pixel-for-pixel in design, silhouette, placement, orientation, color, details, empty helmet socket, lighting, and pixel-art style. Change only the background. Replace every background pixel, including the dark vignette, ambient shadow, and the empty helmet-socket interior, with one perfectly uniform solid #ff00ff magenta field edge-to-edge. The magenta must be completely flat: no gradient, texture, shadow, halo, reflection, transparency checkerboard, or color variation. Do not add, remove, crop, repaint, resize, rotate, or otherwise alter any part of the armor. Exactly one armor body object, no other objects.

### Head

> Edit the referenced accepted modular heavy helmet layer into the Lion's Mantle guard helmet. Preserve exact registration, scale, pixel-art style, centered rotation pivot, strict 90-degree zenith view, broad oval footprint, and north/up orientation. Exactly one isolated helmet top. Design: prestigious Sindrian guard helmet with deep polished crimson crown plates, blackened gunmetal rim, oversized copper cooling channels, muted brass edge guards, compact amber forward status lamps, and a restrained abstract golden mane chevron with no letters or literal animal. Heavy, conspicuous, privileged, disciplined, battle-ready.
>
> BACKGROUND REQUIREMENT: one perfectly uniform solid #ff00ff magenta field covering every background pixel edge-to-edge, or genuine transparent alpha if the reference transparency is preserved. No gradient, vignette, floor, shadow, halo, reflection, texture, checkerboard, or background variation. No shoulders, torso, collar, neck ring, face, weapon, hands, person, readable text, logo, literal lion, scenery, perspective, side view, crown, plume, horns, flames, or extra objects.

## Foundry-breaker industrial rig

References: `../../outlaw-body.png` and `../../outlaw-head.png`.

### Body

> Edit the referenced accepted modular infantry body layer into the Foundry-breaker industrial heavy rig. Preserve exact registration, pixel-art style, strict 90-degree zenith north/up projection, centered complete silhouette, and centered empty oval helmet socket, while making the silhouette broader and more unevenly massive. Exactly one isolated shoulder-and-upper-body armor layer. Redesign it as a cargo exoskeleton buried under welded ship plate: huge mismatched rectangular shoulder slabs, exposed industrial lift-frame rails, illicit oversized servos, heavy cable bundles, crude shock padding, irregular weld beads, bolted replacement panels, and a repurposed hazard-control box. It should look able to soak punishment through raw mass but offer poor precision and handling. Palette soot black, oxidized dark brown, faded industrial orange, bare steel, old yellow hazard patches without text, and tiny scavenged cyan indicators. Brutal and plausible, not comedic junk.
>
> BACKGROUND REQUIREMENT: one perfectly uniform solid #ff00ff magenta field covering every background pixel edge-to-edge, or genuine transparent alpha if the reference transparency is preserved. No gradient, vignette, floor, shadow, halo, reflection, texture, checkerboard, or background variation. No head, helmet, face, neck, weapon, hands, arms, legs, person, readable text, logo, scenery, perspective, vertical side view, spikes, skulls, chains, gore, flames, or extra objects.

### Head

> Edit the referenced accepted modular helmet layer into the Foundry-breaker industrial heavy helmet. Preserve exact registration, pixel-art style, centered rotation pivot, strict 90-degree zenith view, broad oval footprint, and north/up orientation. Exactly one isolated helmet top. Design: oversized improvised pressure shell made from an industrial hardhat frame and welded ship plate, asymmetrical soot-black and oxidized-brown slabs, faded orange replacement crown, exposed bolts, a crude reinforced brow, repurposed hazard sensor box, heavy seals, and one tiny scavenged cyan orientation lamp. Massive, crude, plausible, badly matched.
>
> BACKGROUND REQUIREMENT: one perfectly uniform solid #ff00ff magenta field covering every background pixel edge-to-edge, or genuine transparent alpha if the reference transparency is preserved. No gradient, vignette, floor, shadow, halo, reflection, texture, checkerboard, or background variation. No shoulders, torso, collar, neck ring, face, weapon, hands, person, readable text, logo, scenery, perspective, side view, spikes, skulls, horns, chains, gore, or extra objects.
