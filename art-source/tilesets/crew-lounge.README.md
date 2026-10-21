# Ship crew lounge

The standard 16x12 lounge has four recreation groups: screen-watching with a
sofa and coffee table, two facing sofas with an end table, a four-seat games
table, and a pool table with clear playing positions. Refreshments and books
occupy the side margin; two restrained planters soften the seating areas.
A two-cell cross-passage joins the groups and a perimeter path serves hatches.

## Geometry and art

The raw is two generations side by side (2508x1254). The left half is the
original built-in ImageGen reskin of the first guide (1024 square, 64px/cell,
slot origins (48,32), 336x240 pitch), with `armory-workshop.raw.png` only as a
material reference and the request in `crew-lounge.prompt.txt`. Twelve slots,
row-major:

1. 3x1 sofa, backrest at image bottom; 3x1 sofa, backrest at image top;
   2x1 coffee table.
2. 2x1 sparse magazines/cup overlay; 2x2 games table; 2x2 game/cards overlay.
3. 3x2 pool table; round 1x1 stool; 3x1 media console.
4. 1x3 refreshment counter; 1x3 bookshelf; 1x1 planter.

The right half, from x=1254, is the real-size refit of four of them. A cell is
about 0.75 m (a crewman's shoulders are 0.6 cells). The games table was 1.42 m
across, the stool 0.66 m and the bookshelf 0.66 m deep. `CrewLoungeTemplate`
draws them at 320 px per cell inside their old footprints: a 1.05 m square
table centred in its 2x2 frame (it stays 2x2 because it seats two a side) with
its game overlay inside the felt, a 0.4 m round stool, and a 2.1 x 0.35 m
bookshelf against the right edge of its 1x3 frame. That right edge is its wall:
a wall-backed tall piece takes one more quarter turn than a wide one, and the
lounge fitting stands it with its back to the side lane. Its open front faces
left. Codex `image_gen` reskinned the guide with the left half as material
reference (`crew-lounge-refit.prompt.txt`, provenance in
`crew-lounge.raw.png.provenance.json`); every piece landed within a few pixels
of its guide shape, so they are cut as full placement frames.
`real-scale-montage.py` joins the halves without touching a pixel.

The authoring JSON records measured cuts and padded overlay canvases. The
ordinary exporter crops/downsamples to 64px/cell; no keying, repainting or
post-export patch is used. This sheet has no height/normal companions.

## Placement and evidence

Furniture and table layers follow the complete room pose. Sofas keep their
backs toward the outside of their group. The fitting checks complete group
space before placing its furniture and publishes only UNWIND activity, with
separate reachable positions for the nine sofa places, four games seats, two
pool players and refreshment use. Sofa activity stands immediately in front
of its seat; this pass does not add seated character animation. Round stools
stay traversable at their exclusive activity cells, as in the mess. Tables
and other substantial furniture become blocking only after circulation acceptance.
The game pieces are visual dressing, not a playable minigame.

Authored layouts retain priority. Ground-side lounges and rooms smaller than
the ship's full recreation footprint keep the existing lounge fitting.

With JAVA_HOME set as documented in CLAUDE.md:

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/CrewLoungeTemplate.java art-source/templates/crew-lounge.png
python art-source/templates/real-scale-montage.py <original-raw.png> <refit.png> art-source/tilesets/crew-lounge.raw.png
'{"name":"crew-lounge"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=ship-materials'
```

The suite shows the actual generated Valkyrie lounge at 24 and 64px/cell,
its adjoining passage and four turned fitting views. These are production
render collectors with the headless Java2D drain, not a live OpenGL capture.
Direct tests cover all eight poses, hatches on every side, exclusive reachable
activity, preserved circulation, walkable game seats and transparent overlay rims.
