# S3 — Orbital Fire Support (first real combat power)

> The most legible "I did something" power. A zone-targeted barrage with a
> telegraph and delay, gated on a warship in orbit, paid for in munitions.

## Goal

A barrage power: target a cell/zone → telegraph marker → short delay → area
damage to units in the zone. Gated on an orbital-fire-capable ship in the
committed fleet; consumes supplies/munitions on use.

## Scope

**In:**
- Zone targeting → telegraph (visible marker so it isn't a guaranteed instakill)
  → delay → AoE damage application to units in radius.
- Cost: CP (activation) + **supplies/munitions on use** (overview § cost layer
  2).
- Gating: requires a source ship (Onslaught / Invictus / `ground_support`
  hull mod — see survey) present in the committed fleet via the S2 resolver.
- Friendly-fire handling decision (does it hit your own squads in the zone?).

**Out:**
- Counter-battery counterplay (a later refinement; the telegraph already gives a
  reaction window).
- Close air support (separate family), VFX polish.

## Design notes

- The **telegraph + delay** is the counterplay seam: units can move out, and it
  leaves room for a future enemy counter-battery response. Attrition-not-
  deletion applies to *the player's source ship* only if it can be contested
  (not in this story).
- Damage application: the flyby overlay already pokes the sim via
  `BattleSimulation#applyExternalDamage` — reuse/generalize that path rather
  than inventing a new one.
- Lore sourcing: Onslaught "ballistic potential", Invictus "gauntleted fist",
  the "Kardakes" orbital-siege platform, and the literal `ground_support`
  hull mod (survey § strong hooks).

## Dependencies

- S1 (framework), S2 (gating/resolver).
- Sim damage application path.

## Acceptance

With an Onslaught in the committed fleet, the barrage power is slottable; firing
it on a cell shows a telegraph, and after the delay every unit in the radius
takes damage; supplies are deducted on use; CP is spent and the cooldown starts.
