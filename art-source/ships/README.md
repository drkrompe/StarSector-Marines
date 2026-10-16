# Remastered hull art

Ground-battle art for warship hulls that have to be drawn far larger than their
vanilla sprites: a ship landed at true scale beside infantry
(`landed-ships-true-scale.md`). The fleet-combat sprites are the game's and are
not replaced; `GroundHullArt` swaps a remaster in for the ground battle only,
berthed and flying alike, so detail does not change when a ship lifts.

| Hull | Vanilla sprite | Guide | Master | Shipped |
| --- | --- | --- | --- | --- |
| Venture | `graphics/ships/venture/venture.png` (194x190) | `templates/venture-vanilla-guide.png` | `raw/venture.raw.png` (1267x1241) | `mod/graphics/battle/ships/venture.png` (1164x1140, 6x) |
| Hound (P) | `graphics/ships/hound/hound_pirates.png` (62x72) | `templates/hound-pirates-vanilla-guide.png` | `raw/hound-pirates.raw.png` (1163x1352) | `mod/graphics/battle/ships/hound_pirates.png` (992x1152, 16x) |

`generate.sh <ship>` pipes `prompts/<ship>.prompt.txt` to the Codex CLI's
built-in image generation with the vanilla sprite enlarged 8x
(`templates/<ship>-vanilla-guide-8x.png`) as the edit target and geometry
guide, and keeps the untouched result under `raw/`. The prompts ask for a
faithful remaster of the same ship with empty weapon sockets, since the game
draws turrets on them.

`python -I art-source/ships/pack.py` is the mechanical exporter: it scales the
master's opaque bounds onto the vanilla sprite's opaque bounds on a canvas that
is a whole multiple of the vanilla canvas, so slot, nozzle and armour-grid
fractions read off the vanilla `.ship` land on the same parts of the remaster.
It only places and resamples; it does not recolour or redraw.
