# Ship crew layers

Overhead crew uniforms for the ship-deck watch: command, engineering, medical and
service. Each role is an independent body, head and foot layer, composited exactly
as the marines' are.

**The crew wear the marines' anatomy.** The silhouette guide is not drawn by hand:
`ship_crew.py guide` enlarges the alpha of the shipped marine fatigues body, bare
head and infantry boot by six and lays them on one 1536x1024 sheet
(`../templates/ship-crew-silhouettes.png`). A reskin that keeps the guide therefore
registers in the compositor like the marine it stands beside: same shoulder taper,
same neck socket, same boot. The first crew pass drew its own rounded-square torso
and an oversized neck hole and read as a box next to the marines.

Each role is its own generation (`prompts/_shared.txt` plus `prompts/<role>.txt`;
the exact text sent is `prompts/<role>.full.txt`), in the marines' pixel-art style.
Command was generated first against the marine sources as style reference; the
other three used the accepted command sheet as their style reference. Raw masters
and their provenance are under `raw/`.

From the repository root:

```powershell
python art-source/ship-crew/ship_crew.py guide
python art-source/ship-crew/ship_crew.py export
```

Export cuts each guide frame from the raw (scaled to the raw's actual size),
downsamples with nearest-neighbour sampling to the marine layer's own size
(body 150x120, head 72x88, foot 38x52), and keeps the generated alpha only inside
the marine silhouette. The body is then squashed vertically by `BODY_Y_SCALE`
(0.85) about the neck socket's centre row, so crew and colonists carry a shorter
torso than the marines while the head still seats in the socket. No colour keying, no hand repair. Only the twelve exported
PNGs under `mod/graphics/battle/ship-crew-modular-topdown/` ship.

`createSnapshots -Psnapshot=ship-duty` renders the crew on a generated ship deck.
