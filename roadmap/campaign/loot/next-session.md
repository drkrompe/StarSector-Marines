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

Before marking S1 shipped, smoke-test one patron contract in game:

1. Move the salvage slider away from baseline and confirm displayed payout is
   multiplied once (the slice removed a pre-existing double multiplier).
2. Win the mission and confirm the debrief salvage summary fits the card.
3. Repeat/reopen with the same mission facts and confirm the summary is stable.

Also smoke-test the S2 layout using the checklist in its story (12-card fit,
modded long names, sprite thumbnails, state colors).

## Immediate next slice — cargo settlement

- Introduce an exactly-once settlement result/service; never transfer directly
  from a card widget.
- Preview capacity for each selected stack against the correct vanilla capacity
  bucket (cargo, fuel, or personnel), including partial-stack overflow.
- Add explicit **Confirm Salvage**. Transfer the carryable quantity, fence each
  overflow unit at 75% base value, and show kept/fenced totals before confirming.
- After confirmation, disable re-application and route to mission select. Back
  before confirmation remains side-effect-free.

Trait/fleet modifiers, cores, and blueprints remain deferred per the overview's
delivery order.
