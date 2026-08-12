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

## Immediate next slice — recovery modifiers

- Add `SALVAGE_EXPERT` to the captain trait set: +25% recovered tonnage and one
  +10%-chance high-value bias, with the latter expressed in roll inputs rather
  than hidden campaign RNG.
- Detect player-fleet Salvage Rigs (+10% each, cap +40%) and Salvage Gantry at
  half effect; freeze the resulting modifier on `MissionOutcome` so debrief and
  roll agree.
- Then gate AI-core candidates by mission/target context. Blueprints remain
  deferred until the tech-recovery trait exists.
