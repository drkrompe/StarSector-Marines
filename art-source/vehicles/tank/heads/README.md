# Onager turret heads

The Onager's turret is modular (`convoy-nouns.md`, the Onager's turret is modular): the part in its cannon card picks the
head it wears. Every head is the approved angular housing (`../turret-master.png`) with its family's gun in the mantlet,
painted at the turret frame's registration, so heads share the drawn size, pivot and muzzle.

| Sheet frames (`army-tank.png`) | Head | Parts |
|---|---|---|
| 1 live, 3 wreck | heavy cannon (`../`, the approved turret) | `HC_20`, `HC_10` |
| 4 live, 5 wreck | autocannon | `AC_6`, `AC_6_QS`, `DC_5`, `DC_4_QS` |
| 6 live, 7 wreck | rotary | `CHAINGUN`, `CHAINGUN_HF`, `LIGHT_MACHINE_GUN` |

## Files

- `guide.py` writes `turret-target.png` (the approved turret alone, the identity authority) and `guide-<head>.png` (the
  target with its cannon cut away and the head's gun as flat grey blocks, the geometry authority).
- `<head>-prompt.txt`, `<head>-raw.png`, `<head>-raw.png.provenance.json`: the ImageGen edit of the target to each guide
  (`tools/codex_imagegen.py`, `codex-imagegen` skill). The autocannon brief names the tank turret; the rotary brief had to
  describe the turret as a vehicle roof module to pass the generator's filter.
- The rail head the linear cannon wore (frames 8 and 9) was retired with the linear cannon; its successor, the HC-10,
  is one large bore and wears the heavy cannon's head.
- `pack.py` keeps the sheet's four base frames (written by `../pack.ps1`) and appends each head's live frame (its raw,
  registered to the approved housing by the scale and offset in `registration.json`, which it measures when absent or with
  `--measure`) and wreck frame (the approved wreck housing, its cannon cut away, with the head's gun charred and drooped).

Re-run after changing a raw: `python art-source/vehicles/tank/heads/pack.py` (after `../pack.ps1` when the base frames
change). The sheet's frame count is `VehicleType.BREAKTHROUGH_TANK`'s; its texture-usage figures
(`art-source/texture-usage/usage.json`) are whole-sheet sizes, so a change of frame count scales them with it.
Look evidence: `createSnapshots -Psnapshot=tank-heads`.
