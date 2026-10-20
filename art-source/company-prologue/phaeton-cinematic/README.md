# Phaeton: cinematic export

Status: DONE (both states accepted 2026-10-19, with the multistage fuel cook-off).
Written: 2026-10-19

The Asharu convoy's tanker (`asharu-battle/asharu-battle-shot.md`): in the
column in S1, and the first convoy ship lost in S6 ("The screen breaks"),
where Manticore (P) missiles hit her amidships and she goes up, mid-close at
about 1-1.5 km, centre frame. Two appendable assets, one per condition state,
built from the surfacing package (`../phaeton-surface/`) and the geometry
(`../phaeton-geometry/`). `./finalize.sh` rebuilds, verifies and reviews both.

| State | Asset | Collection | Effects (`SWFX`, optional) |
|-------|-------|------------|----------------------------|
| clean | `phaeton.blend`, `manifest.json` | `PHAETON` | six running drives, four twin turrets' guns, lamps |
| destroyed | `phaeton-destroyed.blend`, `manifest-destroyed.json` | `PHAETON WRECK` | 22 fires, 6 vents, an 8-blast cook-off (shared kit's `fire` group) |

Both blends are outputs (ignored); the manifests and `validation*.json`
stay tracked. Scale: 0.33 m per unit (the geometry's `metres_per_unit`,
also on each root); 232 units, about 76 m.

## Staging the kill

One knob: `PHAETON WRECK | controls` → `break_frame`, the frame the missiles
land. Before it, every wreck section sits in the intact pose; from it the
three sections drift to the wreck pose at 4 s and keep drifting
(`break_rate` scales that), the tank 2 shell fragments and the debris fly out
from the strike point, and the kit's `fir_exp_frame` follows `break_frame`
(driven), so the flash, fireball, shock ring and embers fire at the strike,
and the fuel cooks off over the next six seconds: tank 2's remnant flashes
back (+0.5 s), the aft fuel lines (+1.2 s), tank 1 (+1.9 s), the machinery
(+2.7 s), the fore fuel lines (+3.4 s), tank 3, the biggest (+4.2 s), and
the drive block's tankage last (+5.6 s). Only the main blast and the two
tanks throw a shock ring. Fires and vents come up over the first second.

A shot appends `PHAETON` and `PHAETON WRECK` (each with its `SWFX`) at the
same root transform, shows `PHAETON` before `break_frame` and `PHAETON
WRECK` from it. Because the wreck is in the intact pose at `break_frame`, the
swap is seamless under the flash. For a wreck already burning, set
`fir_exp` 0 on the wreck's `SWFX controls`; `fir_fire`, `fir_vent` and
`fir_smoke` scale the fires.

## Anchors

- `Engine 1-6 exhaust anchor` (+X aft), running when clean, dead in the wreck.
- Clean: `Turret 1-4 pivot` (rotate about local Z to train; rest at the slot
  angle) with `Anchor turret <i> port|starboard gun muzzle`; `Anchor launcher
  1-2 flare muzzle` on the fixed flare packs.
- Destroyed: `Scar <id> fire anchor ...` / `vent anchor ...` (sizes vary per
  anchor, `size_m` / `length_m`), `Scar S0x explosion anchor ...`, and the
  three section empties. The wreck's lamps are dark.

`verify_ship.py -- --state <s>` appends each asset into an empty scene and
checks the structure, the turret pivots, the drives, and for the wreck the
intact pose before `break_frame`, the wreck pose at 4 s, the flying pieces
and the knob link.
