# Story: captain and fleet recovery modifiers

**Status:** CODE COMPLETE — in-game smoke pending (2026-08-12)

**Implemented in:** `f27294c8`

## Goal

Finish salvage-rights Layer 3: captain expertise and the player's recovery ships
increase the deterministic pool before the picker sees it, and the debrief makes
the modifier visible.

## Locked rules

- `SALVAGE_EXPERT`: +25% recovery pool value and a deterministic 10% chance for
  the roll's first item to come from the catalog's high-value quartile.
- Each `crig` Salvage Rig: +10% fleet recovery.
- A non-`crig` ship carrying `repair_gantry`: +5% fleet recovery. Salvage Rigs
  already carry the hullmod and are not double-counted.
- Fleet recovery caps at +40%; captain + fleet therefore cap at +65% today.
- Resolve against the player's fleet at mission completion and freeze recovery
  bonus + high-value chance on `MissionOutcome`. The loot seed includes both.

## Acceptance

- Pure modifier math covers rig/gantry contributions and the fleet cap; the
  fleet scan excludes rigs from the gantry count.
- Recovery bonus increases the roll target rather than the claim percentage.
- High-value proc uses the manifest's deterministic RNG; repeated generation is
  stable and a 100%-chance test proves the high-value first draw.
- Debrief shows the frozen recovery bonus when nonzero.
- Full build green; in-game rig/gantry smoke test remains the shipping gate.

## Automated verification

- `LootRecoveryModifiersTest` covers expert stacking and the +40% fleet cap.
- `LootRollerTest` covers recovery-pool growth, deterministic inputs, and a
  guaranteed high-value-quartile draw.
- `gradlew.bat build` passes on 2026-08-12.
