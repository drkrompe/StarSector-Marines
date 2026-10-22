# Residential bed damage

The ground berthing fitting, home bedrooms and the clinic fallback use four
directional residential beds, `doodad.residential-bed-h`, `-head-e`, `-v` and
`-head-n`. These states belong to those exact ids; the ship's bunk is a separate
family (`barracks-bunks`).

The beds are real length: a 0.72 x 2.1 m single in a 3x1 (or 1x3) frame, where
a cell is about 0.75 m. `ResidentialBedTemplate` draws the guide at 320 px per
cell, and `art-source/doodads/derive_residential_frames.py` turns the cut master
into the four facings without trimming the frame.

The damaged and destroyed states are one built-in ImageGen generation over
`ComposableDamageTemplate`'s guide for `art-source/console-bank/damage/berthing-bed.tsv`
(the four intact sources as reference), with the brief in
`berthing-bed-damage.prompt.txt` and provenance in
`berthing-bed-damage.raw.png.provenance.json`. Columns are head W, E, S, N; rows
are damaged and destroyed. ImageGen returned 1774x887;
`ComposableDamageRegistration berthing-bed 4` measured the full placement
frames recorded in the authoring document, including transparent margins.

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/ComposableDamageTemplate.java art-source/console-bank/damage/berthing-bed.tsv 4
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/ComposableDamageRegistration.java berthing-bed 4
'{"name":"berthing-bed-damage"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=berthing-bed-damage'
```

Shipped output is `mod/graphics/doodads/berthing-bed-damage.png` plus its
catalog. No secondary producer patches the packed atlas, and this sheet has no
normal/height companions.
