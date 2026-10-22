# Ship sickbay and clinic

The generated sickbay uses warm off-white casework, pale sage upholstery and
graphite bases. Ten real-length ward beds face a two-cell stretcher spine in the
standard 14x10 room, with a reserved side gap at every bed. The clinic end
supplies an examination couch, supply trolley, narrow privacy screen, nurse
charting station, two medical cabinets and scrub sink. The staffed bank swaps
flanks when a hatch would remove its desk. The examination couch can shift into
the remaining clear position; optional trolley/screen placements yield to hatch
approaches.

## Geometry and sources

A cell is about 0.75 m (a crewman's shoulders are 0.6 cells). The ward bed is one
cell by three: a 0.66 x 2.20 m frame round a 0.52 x 2.0 m mattress. It was one by
two, 1.5 m long, and no patient fitted. The rest of the kit was already true.

`sickbay-clinic.raw.png` is two generations side by side, joined untouched by
`templates/real-scale-montage.py`. The left 1254 px are the original built-in
ImageGen sheet: the old `SickbayClinicTemplate` guide of ten components at 80 px
per cell, textured with the barracks sheet as material reference
(`sickbay-clinic.prompt.txt`), then three source edits that moved the linen into
padded canvases (`sickbay-clinic.registration-prompts.txt`; intermediates under
`sources/sickbay-clinic-*`). Its monitor, station, cabinet, sink, trolley,
examination couch and screen (whose side voids are intentional) are cut from it.
The right half is the real-length refit: `SickbayClinicTemplate` now draws only
the bed frame, linen with the pillow at the image top and linen with the pillow
at the image bottom, at 320 px per cell, one 1x3 frame per column on a 1472x1280
guide. ImageGen reskinned it with the old raw as material reference only
(`sickbay-clinic-refit.prompt.txt`; provenance `sickbay-clinic.raw.png.provenance.json`).
It returned the pieces 3-6% large and centred, so each cut is the guide's full
1x3 frame mapped through the piece's measured box onto the guide's box. The
ordinary exporter downsamples to 64 px per cell. There is no pixel painting,
alpha keying or post-export patch, and no height or normal companion.

The bed frame is the same at both ends; the linen chooses the head. A sprite's top
edge lies at its footprint's highest row, so `linen-high` heads a bed at its high
row and `linen-low` at its low row; the ward puts the pillow beside the monitor.
All assemblies turn and mirror with the room.

The damaged and destroyed bed and linen pairs were regenerated at 1x3 the same
way (`console-bank/damage/sickbay-clinic-refit*`, `sickbay-clinic-refit-damage.prompt.txt`)
and form the right half of `sickbay-clinic-damage.raw.png`; see
`console-bank/damage/README.md`.

## Placement and evidence

A complete 1x4 bed bay (monitor and bed) is checked before placing its blocking bed
base, decorative linen and blocking monitor, so a hatch cannot leave orphan
equipment. Its treatment point stands in the side gap level with the middle of the
bed, or at the foot for a final column without a side gap. The clinic's working
rows reach the spine through two-row lanes.
Blocking is deferred until circulation acceptance. The clinic publishes separate
TREAT, STOW and WASH work; decorative supplies do not assert campaign stock.
Authored layouts remain authoritative, and generic ground-side sickbays keep their
existing fitting. No patients or patient behavior are added by this art pass.

Ground medical-campus buildings reuse this art in complete work groups:
monitored real-length ward beds (a 3x6 bay with two clear rows at the foot), examination bays, charting stations, sinks and pharmacy
cabinet runs. Their fitter reserves bedside and working access, scales bed
capacity to the ward, and keeps a compact single-bed fallback for small rooms.
These building furnishings publish no ship crew jobs or patient simulation.
The `console-rooms` snapshot suite retains large and compact clinic views in
`art-source/console-bank/review/clinic-workgroups.png`.

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
