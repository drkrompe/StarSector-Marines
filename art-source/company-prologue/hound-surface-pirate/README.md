# Hound (P): pirate livery

Status: ACTIVE — round 1 for the owner (Asharu battle: two pirate frigates at 2-4 km in S4 and S7).
Written: 2026-10-19

The Hound (P) (`hound_d_pirates.skin`) as a state of the finished Hound, not a
new hull. The skin goes onto hound-geometry's pirate state (`build_hound.py
--state pirate`: welded scrap-armour slabs, chaingun, twin light MG, light
scars HD01-HD11; see its README). The part tables and face selection are
`../hound-surface/`'s (`groups/` re-exports them); run every harness script
with `--hull ../hound-surface-pirate`. `./rebuild.sh` replays everything
except Codex once the pirate geometry is built into `build/hound-p-geom/`.

## Livery

Read off `hound_pirates.png`: a crude, worn crimson repaint where the Hegemony
auxiliary is orange; grimy dark brown-grey structure where it is taupe, and the
welded slabs (`Pirate scrap armour`) take the same class; gunmetal, near-black
recesses, a yellowed cream nose, tinted glazing. The sprite's black slashes are
read as the dark welded slabs, not painted on. Class keys are the Hound's
(`orange` = the red armour, `taupe` = the dark structure). Wear is the `pirate`
row of `wear.json`: poorly maintained and lightly battle-worn (heavier
abrasion, fade and grime than service, drive soot, wrong-shade repaints), well
short of the derelict. Wear colours are owned in `palette.json` (`chalk` fades
crimson to dusty brick, `dust` is dark brown). Scar materials keep their own.

- **Finish reference**: one Codex reskin of the pirate build's reference camera
  with the game sprite as colour key; registered at IoU 0.997.
- **Parts**: four Codex sheets on the Hound's own sheet guides, one swatch
  sheet; no retries. Tiles registered at IoU 0.943 or better.

## Coverage

97-99% of every whole view textured (`review/`); part skin is a few points lower
than the Hound (A)'s because the welded slabs cover skinned plates and take
their class swatch.

## Export

`../hound-export/export_hound.py -- --livery pirate` writes
`hound-export/pirate/`: collection `HOUND (P) | pirate`, root
`HOUND (P) | pirate | root`, the chaingun muzzle (`Anchor weapon WS 001
muzzle`), the twin MG's `Anchor weapon WS 002 muzzle 1` and `2` on the trained
pivot, and the optional `SWFX` effects (the kit's clean drives and three gun
rigs). The chaingun's barrels spin on `Weapon WS 001 rotor`. Verify and review
with `--state pirate`.
