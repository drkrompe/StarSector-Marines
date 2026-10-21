# Colonist layers

Overhead civilian, engineer and scientist layers for the colony's noncombatants.
Each profession is an independent body, head and foot layer, composited exactly
as the marines' and the ship crew's are. Noncombatants never emit weapon or
muzzle-flash layers.

**The colonists wear the marines' anatomy**, the same way the ship crew do
(`../ship-crew/README.md`). The guide is the crew's: the shipped marine fatigues
body, bare head and infantry boot alpha enlarged six times on one 1536x1024 sheet
(`../templates/ship-crew-silhouettes.png`, made by `ship_crew.py guide`). The first
colonist pass came from chroma boards (`sources/`) on a 150x100 body canvas of its
own, and read as rounder, narrower-shouldered figures with a different neck socket
beside the marines.

Each profession is its own generation (`prompts/_shared.txt` plus
`prompts/<role>.txt`; the exact text sent is `prompts/<role>.full.txt`) with three
references: the guide for geometry, the accepted crew command sheet
(`../ship-crew/raw/command.png`) for pixel-art style, and the matching chroma board
in `sources/` for the profession's identity (the civilian's rust work jacket and
satchel strap, the engineer's sealed hazard helmet and ochre shoulder panels, the
scientist's lab coat and compact headset). Raw masters and their provenance are
under `raw/`.

From the repository root:

```powershell
python art-source/colonist-modular-topdown/colonists.py export
```

Export is the crew's own (`ship_crew.export_roles`): each guide frame is cut from
the raw, downsampled with nearest-neighbour sampling to the marine layer's size
(body 150x120, head 72x88, foot 38x52), and its alpha is kept only inside the
marine silhouette, and the body is squashed vertically by the crew's
`BODY_Y_SCALE` about the neck socket, the same shorter torso the crew carry. Only the nine PNGs under
`mod/graphics/battle/colonist-modular-topdown/` ship; each profession loads its own
`foot.png` rather than the marine boot.
