# Ship interior material family

The accepted neutral bulkhead now supplies the generated ship's default walls.
Passages, armories and technical rooms use muted grey-sage resin; crew quarters,
barracks, messes and other inhabited rooms use warm grey resin. Working bays,
stores and engineering keep the accepted sealed concrete. This selection lives
only in the ship recipe and preserves explicitly authored surfaces and vacuum.
It changes no navigation, fixtures, cover, task points or capacity.

Complete legacy 5x7 machine-bay paving is replaced by `auto-shop.bay`, positioned
from the actual 3x6 clear berth plus its head station and side working columns.
The open mouth follows the berth heading in every quarter turn. Short bays,
boat berths and custom/partial paving keep their authored covering. The approved
layered warehouse arrangement and red fixture reskins are still review-fixture
work; this pass does not transplant their fixed coordinates onto generated rooms.

## Retained producer

`ShipInteriorMaterialTemplate` produces the 1024px RGBA guide on a 4x4 grid,
256px pitch, 16px inset and 224px full-cell canvas. The upper left 2x2 contains
window edge strips in N/E/S/W order; upper right contains low open thresholds
in the same order. Adjacent wall cells own the jambs, so adjacent threshold
cells join without adding a middle post. Lower left and lower right are four
samples each of passage and crew deck. Floor samples have no border or joints.

Built-in ImageGen edited that guide with the accepted bulkhead as a separate
material reference. The exact prompt is `ship-interior-materials.prompt.txt`;
the unmodified output is `ship-interior-materials.raw.png` (1254x1254 RGBA).
The output retained the layout but changed its registration: opening canvases
are 286x276px at x=17/329/640/952 and y=16/321. These full canvases retain each
edge strip's offset and clear aperture. Floor crops use opaque interior material
to exclude generated edge bevels. All cuts are recorded in the authoring JSON.
No painting, alpha keying or post-export patching is used.

The wall and both new floor blocks pair to the new openings. There are no new
windows cut into generated rooms: existing opening topology remains authority.
Bulkhead damage currently uses the existing generic rubble fallback; dedicated
ship debris and height/normal companions remain unproduced.

## Reproduction and evidence

With the repository's documented JAVA_HOME:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/ShipInteriorMaterialTemplate.java art-source/templates/ship-interior-materials.png
'{"name":"ship-interior-materials"}' | .\tools\authoring.cmd tileset_export -
'{"name":"ship-bulkheads"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=ship-materials'
```

The suite renders the established Mech Lab review room, cardinal single/double
door and window assemblies, and a generated Valkyrie deck with barracks, mess,
armory and machine-bay close views. It uses the production collectors and the
headless Java2D drain, not a live-game/OpenGL capture. The generated deck views
contain its real fittings; the separate Mech Lab review view retains the proposed
layered storage. No campaign save or installation is modified by this evidence.
