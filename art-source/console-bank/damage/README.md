# Composable furnishing damage

Twenty-four intact props have 48 paired damaged/destroyed sprites:

- Six console-bank modules: both straights, inside/outside corners and two caps.
- Seven main-stores pieces: rack, retaining frame, cartons, cases, pallet, tally
  and packing benches. Drum stock reuses its existing barrel-damage pair.
- Ten sickbay-clinic pieces, including both linen orientations, plus the
  mess-galley stool used at ground readouts and in clinic waiting areas.

## Sources and registration

Built-in ImageGen supplied all finished pixels. Each family has a retained
`*.tsv` source manifest, `*-guide.png`, `*-reference.png` and exact `*.prompt.txt`.
`ComposableDamageTemplate` makes the full-footprint guide and reference: rows
one/three are standing damage; rows two/four are the corresponding separated
low remnants. The frame layer owns only its thin rails and three open windows.
Linen remains a separate soft layer; it does not grow a second bed frame.

The initial storage and clinic passes are retained here. The refinement prompts
break the retaining frame into thin rails and the trolley into separate parts.
The final examination-couch refinement separates its upholstery and rails so the
wreck leaves substantial floor visible between fragments.
The clinic raw PNG contains coloured RGB under zero alpha; this is not an opaque
backdrop. Native transparency is retained unchanged and was checked over the
actual game floor. There is no colour key, alpha transfer, repaint or atlas patch.

Accepted raw sheets and authoring documents live in `art-source/tilesets/`:
`console-bank-damage`, `main-stores-damage`, `sickbay-clinic-damage`.
Their actual raw dimensions are respectively 1086x1448, 1254x1254 and 1536x1024.
`ComposableDamageRegistration` measures each assigned slot, restores the intact
sprite's full-frame padding for standing damage, and retains a local full frame
around destroyed fragments. Its reviewed `*-registered.tsv` cuts are recorded
in the authoring documents. Export resamples these frames to 128 pixels/cell.
No height or normal companions belong to these sheets.

## Runtime

The existing prop-damage channel owns state swaps, original placement rotation
and mirroring, attack deduplication, cover and floor release. Consoles and powered
medical equipment use existing smoke/fire cues; inert bases use collapse dust.
Zero-cover stock, frames, linen and stools have silent damage states. Base
collapse also destroys its completely contained supported layers; destruction
of a stock layer alone does not clear a surviving base. Neighbouring modules,
monitors and unrelated furniture stay independent.

Both the built-in registry and provider catalog declare all three exported sheets.
The room editor, ship and ground maps use the same ids, so their placements
receive these pairs without replacing their authored layouts.

## Reproduce and review

With JAVA_HOME set as in CLAUDE.md, run the source-file utilities from the project
root (the column counts are 3, 4 and 6 for console-bank, main-stores and sickbay-clinic):

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/ComposableDamageTemplate.java art-source/console-bank/damage/console-bank.tsv 3
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/ComposableDamageRegistration.java console-bank 3
'{"name":"console-bank-damage"}' | .\tools\authoring.cmd tileset_export -
'{"name":"main-stores-damage"}' | .\tools\authoring.cmd tileset_export -
'{"name":"sickbay-clinic-damage"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=composable-damage'
```

The registered suite uses the production MapEditor damage entry point and
Java2D battle renderer for all three families, three states and four rotations.
Its 1920x1080 boards are retained in `review/`. This is not live OpenGL evidence.
Small direct tests cover state/physical invariants, whole-footprint attack
deduplication, supported-layer collapse, surviving overlapping cover, transparent
frame openings and sparse wreck coverage.

## Armory and utility equipment

The ground installation upgrade also adopts the existing `armory-workshop` kit
and `ship-engineering.generator`, `control` and `switchgear`. Their 22 missing
state sprites are built-in ImageGen edits of the retained geometry guides and
intact references. The two exact submitted prompts are:

- `art-source/tilesets/armory-workshop-damage.prompt.txt`
- `art-source/tilesets/installation-utility-damage.prompt.txt`

For each family, this directory retains the input manifest, generated guide,
intact reference, guide cuts and registered output cuts. The original returned
PNGs are the matching `.raw.png` files in `art-source/tilesets`; authoring JSON
records the full-footprint crops. No manual raster painting, alpha transfer or
background keying was used. Utility source RGB contains dark background color
under transparent pixels; native alpha and the ordinary export preserve the
clear gutters and wreck gaps.

The armory sheet contains counters, rack bases, rifles, open rack frames,
benches, bench tools, ready-use lockers and ammunition cabinets. Transparent
layers keep their own state pair and collapse with their supporting base.
The utility sheet contains generator, readout and switchgear pairs. Standing
states retain cover; destroyed states use zero cover and height. Effects on
powered equipment are presentation cues, with no secondary blast damage.

Rebuild registration with `ComposableDamageRegistration <family> <columns>`
(4 for `armory-workshop`, 3 for `installation-utility`), then use the retained
cuts in the normal authoring records and run `tileset_export` for
`armory-workshop-damage` and `installation-utility-damage`.
`createSnapshots -Psnapshot=composable-damage` exercises real structural damage,
layer collapse, released obstruction and rotations; `review/armory-turn-*` and
`review/utility-turn-*` retain those production-rendered boards.
