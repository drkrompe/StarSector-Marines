# Ship mess and galley

Generated ship messes use a coordinated graphite, grey-sage and stainless kit.
Long dining tables have six round bolted stools each, with sparse meal settings
on alternating tables. A servery separates the public apron from preparation,
cold storage, cooking ranges and washing. Aligned two-cell staff openings join
the galley aisles. The standard 22x12 fitting provides nine tables and 54 dining
points in every rotation and mirror, before any fallback hatch removes furniture.
Small secondary messes remain dining-only; authored layouts remain authoritative.
Ground-side messes keep their existing furniture.

## Retained geometry and export

`MessGalleyTemplate` produces a 1024x1024 RGBA guide with eight slots in two
columns and four rows. Slot pitch is 512x256, origin (64,32), and source cell
size is 96px. Reading across then down: 3x1 bare table, 3x1 transparent meal
settings, 1x1 round stool, then five 1x2 units: range, prep bench, double sink,
cold store and servery. The range controls and serving rail face image right.

Built-in ImageGen textured this guide using `armory-workshop.raw.png` solely as
a material reference. Its exact prompt is `mess-galley.prompt.txt`; the unchanged
1254x1254 RGBA result is `mess-galley.raw.png`. Actual component registration
changed, so the authoring JSON records measured full canvases. The meal canvas
includes padding to register its two trays inside the tabletop. The stool keeps
a 160px square canvas around its 98px body, leaving room for a seated occupant.
The ordinary exporter crops and downsamples to 64px per world cell. Generated
alpha is preserved; no painting, keying or post-export pixel patches were used.
There are no normal or height companion maps.

## Fitting contract

Tables and appliances are blocking assemblies applied only after circulation
acceptance. Meal settings are nonblocking layers inside their table. Each visible
stool is drawn at an accepted exclusive MESS point; its cell remains traversable
so an actor can occupy it. Round stools have no directional back, so their art
needs no pose variants. Diners target the adjacent table cell rather than its
far end. Cooks and storekeepers work from the galley aisles, behind the servery.
Decorative meals are scenery, not campaign food stock.

## Reproduction and evidence

Set JAVA_HOME as documented in CLAUDE.md, then:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/MessGalleyTemplate.java art-source/templates/mess-galley.png
'{"name":"mess-galley"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=ship-materials'
```

The suite shows the actual generated Valkyrie mess at 24 and 64px per cell,
with adjoining compartments, plus four quarter-turn fitting views. These use
production render collectors and a headless Java2D drain, not live-game OpenGL.
Direct fitting tests check all eight poses, six accessible seats per table,
separated galley work, aligned staff passages, hatches on all four sides,
small-room dining and the transparent middle of the meal overlay.
