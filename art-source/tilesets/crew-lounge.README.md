# Ship crew lounge

The standard 16x12 lounge has four recreation groups: screen-watching with a
sofa and coffee table, two facing sofas with an end table, a four-seat games
table, and a pool table with clear playing positions. Refreshments and books
occupy the side margin; two restrained planters soften the seating areas.
A two-cell cross-passage joins the groups and a perimeter path serves hatches.

## Geometry and art

`CrewLoungeTemplate` draws the retained 1024x1024 RGBA guide at 64px/cell,
with slot origins (48,32) and a 336x240 pitch. Twelve slots, row-major:

1. 3x1 sofa, backrest at image bottom; 3x1 sofa, backrest at image top;
   2x1 coffee table.
2. 2x1 sparse magazines/cup overlay; 2x2 games table; 2x2 game/cards overlay.
3. 3x2 pool table; round 1x1 stool; 3x1 media console.
4. 1x3 refreshment counter; 1x3 bookshelf; 1x1 planter.

Built-in ImageGen reskinned the guide using `armory-workshop.raw.png` only as a
material reference. `crew-lounge.prompt.txt` retains the exact prompt, and the
accepted unchanged 1254x1254 RGBA output is `crew-lounge.raw.png`. The authoring
JSON records measured cuts and padded overlay canvases. The ordinary exporter
crops/downsamples to 64px/cell; no keying, repainting or post-export patch is
used. This sheet has no height/normal companions.

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
'{"name":"crew-lounge"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=ship-materials'
```

The suite shows the actual generated Valkyrie lounge at 24 and 64px/cell,
its adjoining passage and four turned fitting views. These are production
render collectors with the headless Java2D drain, not a live OpenGL capture.
Direct tests cover all eight poses, hatches on every side, exclusive reachable
activity, preserved circulation, walkable game seats and transparent overlay rims.
