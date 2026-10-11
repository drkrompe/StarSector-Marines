# Barrel damage states

Built-in ImageGen reskinned `BarrelDamageReskinTemplate` using the retained
`../templates/barrel-damage.png` geometry guide and `barrel-reference.png`
material reference. The exact request is `barrel-damage.prompt.txt`; the
accepted output is `barrel-damage.raw.png`. Generated alpha retains the open
centers of the curved metal shells and the gaps between scattered scraps.

The returned sheet is 1254 x 1254, with four full 627px frames: industrial
blue-gray/yellow drum clusters in the left column and olive-gray shipboard
drums on the right; damaged above destroyed. Full-frame cuts preserve pivots
and margins. Industrial clusters occupy one game cell, while shipboard stock
retains its two-by-two footprint. The ordinary exporter owns the atlas and
catalog; there is no post-export repaint or alpha key.

```powershell
$env:JAVA_HOME = 'C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.2/jbr'
'{"name":"barrel-damage"}' | tools/authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=barrel-damage'
```

The guide derives intact silhouettes by catalog id, adds dent and crack paths,
and lays out open curved shells, loose lids and separate metal fragments.
ImageGen supplies the finished surface material.

`BarrelDamage` consumes existing weapon structural damage, both direct and
radial. Each group owns 100 structure; existing positive grid HP on a blocking
industrial cluster remains its initial pool. A blast spends damage once per
group even when it visits all four shipboard cells. A surviving hit keeps cover
and obstruction. Destruction removes barrel cover and opens blocking industrial
clusters through `MapEditor`, retaining original ground. Shipboard stock has
no independent cover or obstruction: its rack or pallet remains intact. Swaps
retain rotation and mirroring.

`BarrelDamageTest` exercises both identities, all orientations, walkable and
blocking placement, blast deduplication, neighboring cover and underlying bases.
`BarrelDamageArtTest` checks full-frame metadata, margins, separate fragments
and an open shell's center spatially. The registered snapshot suite renders
intact, damaged and destroyed rows through combat mutation and the actual map
renderer at 32px and 64px per cell; the rightmost column retains pallet bases.
