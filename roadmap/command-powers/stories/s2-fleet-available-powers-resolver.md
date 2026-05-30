# S2 — Fleet → available-powers resolver

> The diegetic-loadout core: your fleet is your spellbook. Maps real fleet
> composition to the set of powers the player may slot.

## ✅ Fork resolved: explicit detachment (unifying powers + fighters + shuttles)

Fork #1 is settled — **explicit detachment** (overview § "The commitment layer").
The player commits a detachment of their fleet; that detachment is the *single
source* of all three battle-support kinds (active command powers, passive
fighter cover, shuttle transport), replacing the prior three independent
whole-fleet scans. The **employer/contract is a co-source** (`Mission` now
carries `employerPowerIds` alongside `clientFighterSupport` / `employerShuttles`).

### Build decomposition (3 slices, one session)

- **Slice 1 — Resolver core + powers-from-fleet (foundation).** New
  `ops.detachment` package (`Detachment`, `DetachmentResolver`, `PowerCatalog`);
  `BattleSimulation.setCommandPowers` + `CommandPowerService.setPowers` (empty
  ctor); shared `ops.MissionLaunch.buildSimulation` both `onAccept` paths route
  through (collapsing the Briefing/Comms duplication); `Mission.employerPowerIds`
  (empty default); baseline `ReconPing` always seeded so the loop stays
  demoable. Committed set = whole fleet → default behavior preserved; only the
  *source of powers* + accept dedup change.
- **Slice 2 — Employer co-source + baseline gate.** `MissionGenerator.rollEmployerPowers`
  lights up the patron/contract co-source (`Mission.employerPowerIds` →
  `PowerCatalog`); the baseline ReconPing is gated behind
  `DevConfig.ALWAYS_GRANT_RECON_PING`. **Note:** *power* narrowing-to-committed-
  subset moved to Slice 3 — it's meaningless before the commit UI exists (a recon
  ship is neither a transport nor a carrier, so there's nothing to narrow
  against). Shuttles are *already* a committed subset via `deselectedTransports`.
- **Slice 3 — Unified Committed-Detachment UI + narrowing.** Replace
  `PlayerFleetWings.fromPlayerFleet` whole-fleet auto with committed-carriers →
  wings; carrier-bay opt-in toggles alongside the transport toggles (one
  "Committed Detachment" section). The committed-ship set this UI produces is
  what finally narrows power-sourcing (and fighter cover) off the whole fleet.

## Goal

A resolver: `(committed fleet/detachment) → Set<CommandPowerType>`, plus the
pre-battle **loadout-budget slotting** step (command level → N slottable
powers; player picks which available powers to bring).

## Scope

**In:**
- A **data-driven** ship-id / hull-mod-id → power mapping (a table in
  `mod/data/…`), seeded from
  [`../ship-hullmod-survey.md`](../ship-hullmod-survey.md) through the
  projection lens (overview § selection lens).
- The resolver reading mission fleet/detachment composition against that table.
- Pre-battle slotting UI: show available powers, enforce the command-level
  budget cap, let the player pick.

**Out:**
- New power *behaviors* (reuse S1's recon + a stub or two).
- Resource costs (S3), FOB (S7), the budget *curve* (S5 — a constant cap is
  fine here).

## Design notes

- **Capacity vs roster** stay separate (overview axis 2): this story is the
  *roster* axis. Baseline roster = vanilla acquisition *is* the unlock, so the
  mapping table is the whole mechanism — **spoils-tier "super" mods are just new
  rows**, which is exactly why the table is data-driven.
- Mapping is many-to-one and one-to-many: a power may be enabled by several
  ships; one ship may enable several powers (Survey Equipment's double life —
  overview § still-open #2).

## Dependencies

- Campaign fleet state access (`CampaignState`).
- S1 framework. A stub command level (constant budget) is fine; real curve in S5.

## Acceptance

Bringing a Valkyrie surfaces the marine-drop power as slottable; removing it
hides the power; the command-level budget caps how many slottable powers the
player can actually bring into the battle.
