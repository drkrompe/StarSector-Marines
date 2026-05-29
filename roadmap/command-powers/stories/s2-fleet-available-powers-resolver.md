# S2 — Fleet → available-powers resolver

> The diegetic-loadout core: your fleet is your spellbook. Maps real fleet
> composition to the set of powers the player may slot.

## ⚠ Settle first: what does "committed" mean?

This story sits on the one **still-open** fork (overview § Still open #1):

- **Explicit detachment** — the player nominates an "orbital support
  detachment" pre-mission; those ships source the powers, are unavailable for a
  concurrent space engagement, and are the ones at CR/crew risk from
  counterplay. *Legible risk, real fleet-management decision.* **Recommended.**
- **Implicit fleet** — the whole fleet contributes; lower friction, but the
  risk is invisible until it happens.

Resolve this before building — it changes the resolver's *input* (a detachment
vs the whole fleet) and the pre-battle UI.

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
