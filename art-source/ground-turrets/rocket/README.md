# Rocket heads

The Locust battery's head (`tender-ship.md`, What a tender can build): the space game's own Locust turret, up-res'd and
repainted in the ground-turret family's olive-drab finish, nose up, one ImageGen generation with its prompt and
provenance beside it. It sits on each look's own round collar, as the laser heads sit on theirs.

| Head | Source | Vanilla turret |
|------|--------|----------------|
| `locust-head.png` | `locust-raw.png` (`locust-prompt.txt`, the brutalist autogun head as the finish reference) | Locust SRM |

```
python art-source/ground-turrets/rocket/upres_guide.py    # the vanilla guide, from the install (git-ignored)
python art-source/ground-turrets/rocket/export_rocket.py  # the head, into mod/graphics/structures/ground-turrets/rocket/
```

The exporter scales the head about the sprite's centre (the vanilla pivot) to 0.8 cells, inside its one-cell collar,
and prints the eight tube mouths for the mount's muzzles in `turret-emplacements.turret.json`.
