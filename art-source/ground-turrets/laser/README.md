# High-tech laser heads

The heads of the lasers a high-tech builder fits in place of guns (`ground-turret-tiers.md`, High-tech lasers): the
space game's own laser turrets, up-res'd and painted in Tri-Tachyon white ceramic, graphite and cyan, nose up, each
one ImageGen generation with its prompt and provenance beside it.

| Head | Source | Vanilla turret |
|------|--------|----------------|
| `pd-laser-head.png` | `art-source/vehicles/tank-laser/pd-skin-tt-raw.png` (the laser tank's turret) | PD Laser |
| `burst-laser-head.png` | `burstpd-raw.png` (`burstpd-prompt.txt`) | Burst PD Laser |
| `heavy-laser-head.png` | `art-source/vehicles/tank-laser/pd-skin-red-raw.png` (the PD laser tank's red turret, at hardpoint size) | PD Laser |

```
python art-source/ground-turrets/laser/upres_guide.py    # the vanilla guides, from the install (git-ignored)
python art-source/ground-turrets/laser/export_laser.py   # the heads, into mod/graphics/structures/ground-turrets/laser/
```

The exporter turns each head nose up and scales it about the sprite's centre (the vanilla pivot), and prints the
muzzle (the emitter tip) for the mounts in `turret-emplacements.turret.json`.
