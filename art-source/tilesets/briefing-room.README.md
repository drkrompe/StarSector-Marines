# Ship briefing room

Built-in ImageGen reskin of `BriefingRoomTemplate`, using the armory sheet only
as a muted material reference. No API/CLI generation was used.

- Guide: `art-source/templates/briefing-room.png`, 1024 square, 64px/cell.
- Accepted raw: `briefing-room.raw.png`, 1254 square RGBA.
- Exact prompt: `briefing-room.prompt.txt`.
- Cuts and export: `briefing-room.tileset-authoring.json`.
- Runtime: `mod/graphics/tilesets/briefing-room.png` and matching tileset JSON.

Eight components: a 6x2 table, a separate 6x2 tactical overlay with broad clear
margins, a 4x1 display, a 2x1 lectern, and four 1x1 chair headings. Cuts were
measured from returned alpha, not scaled blindly from guide coordinates.
Chair backrests are named by their location in image space. The map overlay
owns no collision or cover; the complete table and chairs are physical fixtures.
No height or normal companions belong to this atlas.

```powershell
'{"name":"briefing-room"}' | .\tools\authoring.cmd tileset_export -
```

The ship fitting groups eight seats around one table, with a presentation bank
and lectern beyond a clear aisle. All layers share the room pose. It deliberately
publishes no permanent jobs, preserving briefing as an event. Small and ground
conference rooms keep the existing fitting; authored layouts retain priority.

`ShipBriefingRoomFittingTest` checks all eight poses, every perimeter hatch,
simultaneous hatches, connected free floor, seat arrangement and the transparent
table rim. `createSnapshots -Psnapshot=ship-materials` supplies generated ship
views at 24/64px per cell and four rotated fitting views through production
render collectors drained by headless Java2D, not live OpenGL.
