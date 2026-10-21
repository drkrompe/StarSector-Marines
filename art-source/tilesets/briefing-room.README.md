# Ship briefing room

The raw is two generations side by side (2508x1254). The left half is the
original built-in ImageGen reskin of the first guide (1024 square, 64px/cell),
with the armory sheet only as a muted material reference and the request in
`briefing-room.prompt.txt`: a 6x2 table, a separate 6x2 tactical overlay with
broad clear margins, a 4x1 display and four 1x1 chair headings. Cuts were
measured from returned alpha, not scaled blindly from guide coordinates. Chair
backrests are named by their location in image space. The map overlay owns no
collision or cover; the complete table and chairs are physical fixtures.

The right half, from x=1254, is the real-size lectern. A cell is about 0.75 m
(a crewman's shoulders are 0.6 cells), and the original 2x1 presenter's desk
was 1.4 m wide. `BriefingRoomTemplate` draws a 0.6 x 0.5 m lectern in a 1x1
frame at 320 px per cell, and Codex `image_gen` reskinned it with the left half
as material reference (`briefing-room-refit.prompt.txt`, provenance in
`briefing-room.raw.png.provenance.json`). It came back about a third larger
than its guide shape, so its square cut is scaled to make it 0.6 m wide (0.45 m
deep). `real-scale-montage.py` joins the halves without touching a pixel. Its
plain front panel faces the top of the frame, where a wall-backed piece has its
wall; the reading desk, screen and notes are on its bottom edge, the speaker's
side, worked from the row below. No height or normal companions belong to this
atlas.

```powershell
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/BriefingRoomTemplate.java art-source/templates/briefing-room.png
python art-source/templates/real-scale-montage.py <original-raw.png> <refit.png> art-source/tilesets/briefing-room.raw.png
'{"name":"briefing-room"}' | .\tools\authoring.cmd tileset_export -
```

The ship fitting groups eight seats around one table, with a presentation bank
and lectern beyond a clear aisle; the lectern stands one cell clear of the
display. All layers share the room pose. It deliberately publishes no
permanent jobs, preserving briefing as an event. Small and ground conference
rooms keep the existing fitting; authored layouts retain priority.

`ShipBriefingRoomFittingTest` checks all eight poses, every perimeter hatch,
simultaneous hatches, connected free floor, seat arrangement and the transparent
table rim. `createSnapshots -Psnapshot=ship-materials` supplies generated ship
views at 24/64px per cell and four rotated fitting views through production
render collectors drained by headless Java2D, not live OpenGL.
