# Ship sickbay and clinic

The generated sickbay uses warm off-white casework, pale sage upholstery and
graphite bases. Ten ward beds face a four-cell stretcher aisle in the standard
14x10 room, with a reserved side gap at every bed. The clinic end supplies an
examination couch, supply trolley, narrow privacy screen, nurse charting station,
two medical cabinets and scrub sink. The staffed bank swaps flanks when a hatch
would remove its desk. The examination couch can shift into the remaining clear
position; optional trolley/screen placements yield to hatch approaches.

## Geometry and sources

`SickbayClinicTemplate` creates a 1024x1024 RGBA guide with ten components in two
columns and five rows. Slot origins are x=64/576 and y=20/220/420/620/820; source
cell size is 80px. Reading order: 1x2 bed base, two opposing 1x2 linen layers,
1x1 monitor, 2x1 nurse station, 2x1 cabinet, 1x1 sink, 1x1 trolley, 2x1 examination
couch and 1x2 privacy screen. The screen's side voids are intentional.

Built-in ImageGen textured the guide with the barracks sheet as a material
reference, using `sickbay-clinic.prompt.txt`. Three subsequent source edits moved
the linen layers into enough empty space for their padded export canvases; the
exact sequence is retained in `sickbay-clinic.registration-prompts.txt`. Initial
and intermediate images are retained under `sources/sickbay-clinic-*`. The accepted
unchanged 1254x1254 RGBA sheet is `sickbay-clinic.raw.png`. Requested move distances
were approximate; measured final canvases in the authoring JSON are authoritative.

The ordinary exporter crops and downsamples to 64px per cell. Full linen canvases
register the mattress inside the side rails and between the head and foot boards.
There is no pixel painting, alpha keying or post-export patch, and no height or
normal companion. Pillow-at-image-bottom linen goes on the low-Y rank; the opposite
rank uses pillow-at-image-top linen. All assemblies turn and mirror with the room.

## Placement and evidence

A complete 1x3 bed bay is checked before placing its blocking bed base, decorative
linen and blocking monitor, so a hatch cannot leave orphan equipment. Its treatment
point stands beside the bed, or at the foot for a final column without a side gap.
Blocking is deferred until circulation acceptance. The clinic publishes separate
TREAT, STOW and WASH work; decorative supplies do not assert campaign stock.
Authored layouts remain authoritative, and generic ground-side sickbays keep their
existing fitting. No patients or patient behavior are added by this art pass.

With JAVA_HOME set as documented in CLAUDE.md:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/SickbayClinicTemplate.java art-source/templates/sickbay-clinic.png
'{"name":"sickbay-clinic"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=ship-materials'
```

The suite shows the actual generated Valkyrie sickbay at 24 and 64px per cell,
plus four posed fitting views. These use production collectors and the headless
Java2D renderer, not a live OpenGL capture. Small direct tests cover all eight
rotations/mirrors, ward and clinical hatches, preserved work and aisle access,
whole bay omission, linen padding and the screen's transparent side regions.
