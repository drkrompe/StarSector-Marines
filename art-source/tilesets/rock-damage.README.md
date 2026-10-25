# Rock damage states

The seven nature rock groups (`nature.rock-small-1..2`, `-medium-1..2`,
`-large-1..3`) have cracked and shattered states. All three states are painted
from geometry guides, in order:

1. `art-source/templates/field-stones-guide.py` draws the seven groups as smooth,
   top-lit stones, one per frame on a 4x2 sheet. ImageGen reskins it into
   `field-stones/field-stones.png` (`field-stones.prompt.txt`).
2. `art-source/templates/field-stones-damage-guide.py` reads those accepted stones
   and draws the guides for each state. The damaged guide keeps each silhouette
   with cracks and chips. The destroyed guide breaks it into chunks drawn in
   toward their own centres, with gravel between them. ImageGen reskins the guides
   into `field-stones-damaged.png` and `-destroyed.png`, with the intact sheet as
   the identity reference.
3. `art-source/tilesets/nature-tiles-refit.py` cuts all three states at the
   intact group's alpha box:
   - It joins the intact frames into the nature strip.
   - It writes `rock-damage.raw.png` with the damaged sheet above the destroyed
     sheet, and rewrites the fourteen rects here.
   - It shifts each state onto its group's mean colour.
   - It slides apart any stones that sit side by side with clear columns between
     them, moving every state the same way. The strip loader would otherwise
     split such a group into two frames.

   Both the strip and this export stretch a cut to one cell, so equal boxes keep
   the three states in register.

The shipped set is chosen by `STONE_SET` and `STONE_GRADE` at the top of the
refit script. The pale stones in `field-stones/` read too bright on woodland
ground. The set that ships is `field-stones/mossed/`: the same groups reskinned
with moss and lichen, using the pale stones darkened as the identity reference,
and taken down a step by the `quiet` grade. Its three states each have their own
prompt and provenance beside them. The pale set stays as the identity source for
the damage guides.

Then export both sheets and re-derive the relief maps:

```powershell
$env:JAVA_HOME = 'C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.2/jbr'
'{"name":"nature-tiles"}' | tools/authoring.cmd tileset_export -
'{"name":"rock-damage"}' | tools/authoring.cmd tileset_export -
.\gradlew.bat :asset-pipeline:deriveTileMaps
.\gradlew.bat createSnapshots '-Psnapshot=rock-damage'
```

The `rock-damage` suite renders intact, cracked and shattered rows at 32 and 64
pixels per cell through the map renderer and the combat mutation path; that is
the registration check. The floor look shot in `undergrowth-look` shows the
stones in a scene.

`RockDamage` owns per-cell structure (50/100/200 by size). The first structural
hit replaces the nature overlay with the matching doodad; surviving damage
keeps original cover. Rubble has zero cover and opens blocking boulders through
`MapEditor`. Walkable nature cover is removable independently of static props.
Original ground, neighboring cover and permanent cliffs remain. Existing weapon
wall-damage payloads, both direct and radial, drive these transitions.
`RockDamageTest` checks all identities, state/cover/navigation transitions,
overlap and cliff preservation.
