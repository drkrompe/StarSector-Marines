# Rock damage states

Seven built-in nature rock groups have cracked and shattered states, generated
with built-in ImageGen from the retained geometry and material-reference plates.
The exact request is `rock-damage.prompt.txt`; the returned image is
`rock-damage.raw.png`. `rock-damage.tileset-authoring.json` records the reviewed
cuts from its actual 1254 x 1254 canvas. The returned row spacing differs from
the guide, so cuts use the observed transparent gutters rather than assuming
four equal rows. Export preserves generated alpha and does not trim/recenter.

Regenerate the guide and actual combat-state previews with:

```powershell
$env:JAVA_HOME = 'C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.2/jbr'
.\gradlew.bat createSnapshots '-Psnapshot=rock-damage'
'{"name":"rock-damage"}' | tools/authoring.cmd tileset_export -
```

`RockDamageReskinTemplate` reads intact source silhouettes, overlays branching
crack guides, and lays out small separated fragment guides. These are constraints
for the ImageGen reskin, not final procedural art. The seven identities keep
their ordering: small 1/2, medium 1/2, large 1/2/3. The final atlas contains
seven damaged frames followed by seven destroyed frames, each one game cell.

`RockDamage` owns per-cell structure (50/100/200 by size). The first structural
hit replaces the nature overlay with the matching doodad; surviving damage
keeps original cover. Rubble has zero cover and opens blocking boulders through
`MapEditor`. Walkable nature cover is removable independently of static props.
Original ground, neighboring cover and permanent cliffs remain. Existing weapon
wall-damage payloads, both direct and radial, drive these transitions.

`RockDamageTest` checks all identities, state/cover/navigation transitions,
overlap and cliff preservation. `RockDamageArtTest` checks transparent borders
and separated rubble components. The registered visual suite renders intact,
cracked and shattered rows at 32 and 64 pixels per cell through the actual map
renderer and combat mutation seam.
