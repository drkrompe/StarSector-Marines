# Next session — campaign loot handoff

Read [`overview.md`](overview.md) for feature shape and
[`stories/s1-recovery-manifest.md`](stories/s1-recovery-manifest.md) for the
implemented foundation.

## Where we are (2026-08-12)

S1 is code-complete and automated-green. A victorious contract mission now
freezes a deterministic, faction/industry-flavored `LootManifest`; Results shows
the entitlement, stack count, and selection-value budget. **S2 is also
code-complete:** Results routes to a dedicated budget-aware picker with live
icons, persistent selection, and over-budget gating. Cargo remains untouched,
intentionally. See [`stories/s2-picker-screen.md`](stories/s2-picker-screen.md).
The shared implementation commit is `254de744`.

**S3 cargo settlement is now code-complete:** planner `037586cf`, live apply +
confirmation UI `90f01acc`. The picker previews independent cargo/fuel/personnel
capacity, transfers what fits, and fences overflow at 75% exactly once.

**S4 recovery modifiers are code-complete** in `f27294c8`. Salvage Expert and
player-fleet Salvage Rig/Gantry bonuses are frozen at mission completion, grow
the deterministic recovery pool, and are visible on Results. The expert's 10%
high-value-quartile roll is part of the manifest seed and replay-stable.

**S5 rare AI-core recovery is code-complete** in `d5f0b779`. Assault/Raid/
Sabotage missions against AI-linked factions or facilities can now add
risk-tiered gamma/beta/alpha cores to the same deterministic catalog and
settlement pipeline.

Before marking S1 shipped, smoke-test one patron contract in game:

1. Move the salvage slider away from baseline and confirm displayed payout is
   multiplied once (the slice removed a pre-existing double multiplier).
2. Win the mission and confirm the debrief salvage summary fits the card.
3. Repeat/reopen with the same mission facts and confirm the summary is stable.

Also smoke-test the S2 layout using the checklist in its story (12-card fit,
modded long names, sprite thumbnails, state colors).

## S3 in-game shipping checkpoint

- Before confirmation, record cargo space, fuel, marines, weapons, and credits.
- Select a mix that partially overflows all three capacity buckets; verify the
  preview's carried/fenced quantities and 75% payment.
- Confirm once and verify exact cargo/credit deltas, then ensure the resolved
  outcome cannot be reopened or claimed twice.
- Also complete the S1/S2 visual checks above. If clean, move all three stories
  to `complete/` together with the smoke-test record.

## Immediate next — shipping checkpoint

- Repeat the S1–S3 checks above with a captain carrying `SALVAGE_EXPERT`, then
  with Salvage Rig/Gantry hardware; confirm Results and pool size reflect the
  frozen modifier.
- Use an eligible AI-linked strike to verify core icon, value, selection, cargo
  transfer, and overflow fencing. Confirm ordinary targets do not expose cores.
- If clean, move S1–S5 to `complete/` with the smoke-test record. Blueprint and
  tech-recovery support remains deliberately deferred.
