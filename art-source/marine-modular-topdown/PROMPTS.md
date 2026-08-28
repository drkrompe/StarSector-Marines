# Successful ImageGen prompt recipes

All assets were generated with the built-in ImageGen tool on a flat `#ff00ff` background,
then converted to alpha locally.

## Body/shoulder layer

Use an accepted body as geometry/style reference. Request exactly one strict 90-degree
zenith shoulder-and-upper-body mass. Explicitly require an empty centered oval helmet
socket and prohibit head, helmet, weapon, hands, feet, legs, perspective, and vertical
planes. Describe only the desired armor silhouette, plate language, palette, and markings.

## Head layer

Use an accepted head as geometry/registration reference. Request exactly one isolated
helmet top with a readable north marker and fixed center pivot. Explicitly prohibit
shoulders, torso, collar, neck ring, weapon, face, perspective, and all other objects.

## Weapon layer

Use an accepted weapon as scale/orientation reference. Request exactly one self-contained
rectangular top-view mass with rear/base at south and muzzle at north. Repeat that it is
operator-free and prohibit hands, gloves, arms, shoulders, body, projectiles, beams,
flashes, slings, bipods, ammunition, and other objects.

## Variant-specific briefs used

- Charcoal armor: dark charcoal and desaturated gray-green, muted teal hardware, tiny
  warm orange markings.
- Blue scout: near-charcoal foundation, very dark desaturated navy and slate blue-gray
  plates, gunmetal hardware, chipped paint, grime, matte wear, and tiny faded tan
  markings. Explicitly prohibit royal/cobalt/electric blue, white panels, cyan glow,
  glossy plastic, clean heroic armor, and superhero/power-ranger styling.
- Red heavy/elite: near-charcoal structure, dark desaturated oxblood plates, blackened
  gunmetal, muted brass, faded bone veteran chevron, extensive disciplined field wear.
- Outlaw: asymmetric faded brick-red, oxidized brown, soot-black, replacement panels,
  mismatched bolts and welded repairs; prohibit spikes, skulls and comedic junk.
- Army green: mass-issued faded olive drab/forest green over charcoal, dull khaki edges,
  black hardware, dusty seams and campaign wear.
- Militia: gray-green carrier, brown reinforced fabric, mismatched dull-steel shoulder
  plates, straps, buckles and patched industrial protection; explicitly below marine-grade.
- Rocket launcher: original near-future anti-armor launcher inspired by the broad
  functional massing of a modern Javelin; thick battered olive-drab tube, armored
  gunmetal end collars, chunky asymmetric command/sighting unit, restrained ochre
  warning marks, and a silhouette substantially larger and wider than a rifle.
- Laser gun: slim gunmetal emitter, squared power cell, cyan inset conduits, cobalt panel,
  contained glow only.
- SMG/light machine gun: compact shortened receiver and barrel, thick practical fore-end,
  small box magazine, worn dark gunmetal and steel; explicitly shorter and stockier than
  the standard rifle.
- DMR/compact railgun: long narrow reinforced barrel shroud, squared receiver, small flush
  magazine, restrained cool-blue rail details, and a practical precision silhouette;
  explicitly longer and slimmer than the standard rifle.

The SMG and DMR were generated on a uniform `#00ff00` chroma background because green was
absent from their weapon palettes. Both prompts required exact zenith orthographic framing,
north-pointing muzzles, complete silhouettes with generous padding, and no operator, hands,
arms, straps, floor, shadow, reflection, muzzle flash, text, or perspective tilt.

## Armorless and equipment-grade exemplars

- Armorless body: olive-drab cloth fatigues, dark undershirt, canvas web harness and soft
  shoulder fabric; explicit prohibition on hard plates, pauldrons, head, arms and weapon;
  the empty round head socket remains centered for the independent layer.
- Armorless head: bare crown/back of skull, close-cropped dark hair, ears barely visible and
  a subtle north-facing hairline cue; explicit prohibition on helmet, neck armor and shoulders.
- Surplus rifle: accepted rifle geometry retained, but rendered in worn parkerized steel,
  faded olive furniture, a repaired seam, taped grip and restrained field wear.
- Masterwork DMR: accepted DMR geometry retained, with precise dark gunmetal/charcoal ceramic,
  tighter seams, titanium edges and restrained cyan status insets; quality reads through fit
  and construction rather than ornament or glow.

All four were generated with the built-in ImageGen tool on flat `#00ff00`, converted to alpha
with the ImageGen chroma helper, normalized with `build_variants.py`, and checked in composed
224 px previews before runtime registration.
