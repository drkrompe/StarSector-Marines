# Crate damage states

Built-in ImageGen reskinned `CrateDamageReskinTemplate` using the retained
`../templates/crate-damage.png` geometry guide and `crate-reference.png` material
reference. The exact request is `crate-damage.prompt.txt`; the accepted output
is `crate-damage.raw.png`. Its own alpha carries the transparent background,
including between loose panels and spilled contents.
The initial reskin is retained as `crate-damage.reference.png`; the final
ImageGen edit turns and scatters its broken panels using the revised guide.
Both prompts are recorded in order.

The returned 1536 x 1024 sheet matches the guide's 512px cells: storage box,
wooden crate and industrial crate stack across the columns; damaged states
above destroyed states. Full-frame cuts retain pivots, margins and generated
alpha. The existing `doodad.box-dam` placement uses the storage-box damage art
with separate ids preserving its shorter ballistic height. Six unique images
therefore export as eight state definitions, each occupying one game cell.

```powershell
$env:JAVA_HOME = 'C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.2/jbr'
'{"name":"crate-damage"}' | tools/authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=crate-damage'
```

The template derives intact full-cell silhouettes by stable catalog id, defines
crack paths and missing corners, and lays out separated panels and contents.
The generated raster supplies the finished material. The ordinary exporter
owns the shipped atlas and catalog; no post-export repaint or alpha key exists.

Crates join `CoverPropDamage` through existing weapon structural damage, both
direct and radial. Intact crates have 100 structure; pre-damaged boxes start at
50, while existing positive grid HP on blocking placements remains authoritative.
A surviving hit retains original cover and navigation. Destruction retains low
panels and supply debris over the original ground, releases cover, and opens
blocking placements through `MapEditor`. Walkable crate cover is independently
removable so other props and nature cover keep their protection. Co-located
walkable crates keep separate pools; blocking crates share their cell's pool.
All swaps retain rotation and mirroring.

`CrateDamageTest` covers every identity and orientation, walkable and blocking
placements, overlapping profiles, repeated damage, blasts and neighboring cover.
`CrateDamageArtTest` checks metadata, clear borders and separated debris shapes.
The registered snapshot suite renders intact, damaged and destroyed rows at
32px and 64px through the actual map renderer and combat mutation boundary.
