# Story: deterministic recovery manifest

**Status:** CODE COMPLETE (2026-08-12) — compile + full Gradle suite green;
in-game debrief smoke test remains before calling the slice shipped.

**Implemented in:** `254de744` (shared S1 + S2 foundation commit).

## Goal

Turn a victorious mission's already-final `salvageEntitlement` into a frozen,
deterministic recovery manifest before building the picker. The result screen
should be able to say how many stacks were recovered and what selection-value
budget the negotiated percentage bought, while applying no cargo mutation yet.

## Rules

- Seed from mission id, type, risk, target faction/industry, base payout, and
  entitlement. Same outcome + same catalog yields the same ordered manifest.
- Catalog is vanilla/mod aware: commodities come from `CommoditySpecAPI`; weapon
  candidates come from the target faction's known weapons and live
  `WeaponSpecAPI` data. Missing faction metadata falls back to the global
  droppable weapon catalog.
- Target pool value is payout × mission-type multiplier × risk multiplier, with
  a small floor. These constants are intentionally centralized for playtest.
- Weighted draws are without replacement, preventing a grid full of duplicate
  weapon entries. Commodity candidates roll stack quantities; weapons roll one.
- Selection budget is `floor(total rolled base value × entitlement / 100)`.
- Mission payout remains the pre-negotiation base; cash multiplication happens
  once in `MissionResolver`. This also removes the pre-existing contract path's
  double application of the cash-for-salvage multiplier.
- No cargo or credits are mutated in this slice. `MissionResolver.apply` remains
  exactly-once for payout/casualties/contract writeback; settlement belongs to
  the later picker-confirm path.

## Acceptance

- Pure tests prove determinism, seed sensitivity, no duplicate candidates,
  entitlement math, and empty-manifest behavior.
- `Mission`/`MissionOutcome` carry the target faction + risk facts the roll needs.
- `MarineOpsContext` freezes the manifest at battle completion.
- Results renders the manifest summary when salvage exists.
- Full Gradle test suite is green.

## Deferred

- Selection UI, cargo capacity, transfer, fence-on-spot conversion.
- Captain/fleet recovery modifiers and high-value bonus.
- AI cores, blueprints, mod-custom special items.

## Landed

- Pure `ops.loot` model (`LootCandidate`, `LootStack`, `LootManifest`, request +
  roller) with canonical catalog ordering, stable FNV-style seed, weighted
  no-replacement draws, and centralized type/risk pool multipliers.
- Live catalog adapter for vanilla/modded faction-known weapons plus supplies,
  fuel, marines, and heavy machinery; target industry adjusts weights.
- Target faction, risk, and base payout now survive mission resolution into the
  immutable outcome. `MarineOpsContext` freezes the generated manifest.
- Debrief salvage row shows entitlement, recovered stack count, and claim-value
  budget. No items or credits move yet.
- Fixed the contract path's pre-existing double cash-multiplier application:
  `Mission.payout` is once again base payout; briefing/debrief resolution apply
  the negotiated multiplier exactly once.
- Four focused roller tests plus the full project suite pass.
