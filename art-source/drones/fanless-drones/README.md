# Fanless drones

The battle drones' shipped art (`DroneLook`), painted from geometry guides after the owner found the earlier drones
"too into the 'fan' concept". Nothing here flies on a fan, rotor or duct: every outline comes from what the drone does.
The concepts these follow are in `../concepts-fanless/` (vectored-thrust pods for the Tri-Tachyon wing, faction forms
for the hub and swarm drones).

- `guide.py` writes the three guides; `pack.py` cuts the painted sheets into `mod/graphics/battle/drones/`.
- `tt-*`: the Tri-Tachyon wing (escort, sensor, point-defence halo, strike) as vectored-thrust pods.
- `family-*`: a drone hub's drone as low-tech, midline and high-tech hubs build it (`DroneLook.forHub`).
- `swarm-*`: the flock's swarm dart in Domain and contingency liveries, painted side by side in one sheet so both
  are the same machine. The flock cradles are still cut by `../swarm-drones/pack.py`.
- `repair-*`: the derelict network's unarmed repair drone (welding arm, stock canister, lift pods) in Domain and
  contingency liveries, after the owner found the vanilla Borer it wore before low-res.

Each `*-brief.txt` is the brief as sent to `tools/codex_imagegen.py`; `*-raw.png.provenance.json` records every
reference and its role (the guide as geometry authority, the concept sheet as design reference, vanilla hulls and the
old swarm sprites as livery only, `tt-hover-scout` as rendering style).
