# D5 — Timed directional mitigation

> A screen you hold up is not thicker skin. It runs out, it faces one way, and
> the moment it does either of those things you are back to being what you were.

Status: PLANNED — blocks the breacher assist's protective half.
Written: 2026-08-28

Read `combat-durability-nouns.md` before implementing. Coordinates with
`integral-armor-systems.md`, which is the first consumer, and with
`d3-durability-evidence-and-ui.md`, which owns how the pools are reported.

## Problem

The durability model is two pools and a rating. It has no way to express *for
three seconds, from the front, half of what reaches you is turned aside* — and
an integral system needs exactly that. The breacher assist is authored with a
`frontalResistance` and a `shieldedArcDegrees` today and the simulation ignores
both, so a suit advertises a screen it does not have. The Armory currently
declines to mention it for that reason, which is correct and also a hole.

Both obvious shortcuts are wrong, and it is worth writing down why so neither
gets picked up later:

- **More armor capacity while it runs** is the thing `progression-nouns.md`
  forbids by name. It has no facing, no visible expiry, and it inflates the one
  number the design has spent a lot of effort keeping honest. The armour catalog
  already refuses a system that declares capacity, and it would be perverse to
  smuggle the same effect in through the durability side.
- **A flat incoming-damage multiplier** has no facing either, and it is
  invisible: resolved damage would simply be smaller, with nothing in telemetry
  to say why. A player who cannot tell a working screen from a run of lucky
  misses has not been given a capability, only a statistical rumour.

## Goal

**Mitigation** becomes a durability noun in its own right: a bounded fraction of
post-cover damage refused, for an explicit duration, across a bounded arc
measured from the target's facing at the moment of the hit.

It resolves **after cover and before armor**. Cover is a property of the world
the shot crossed; mitigation is a property of the target at that instant; armor
is what the target is made of. That order keeps physical protection from being
either bypassed or double-counted, which is the rule cover already follows, and
it means a mitigated hit still spends what remains against armor at the ordinary
efficiency rather than skipping a step.

**Mitigated damage is reported as its own resolved quantity**, never folded into
armor. This is the requirement that makes the concept worth having rather than a
cheaper way to write a multiplier: the same number that tells the player their
screen worked tells the balance harness whether the arc asymmetry moved.

## Standing rules

- **Never total.** Resistance stays below 1. A hit that cannot land is an
  off-switch, and the fight stops being lethal for as long as it is up.
- **Never all-round.** The arc stays below 360°, measured against the target's
  facing when the hit resolves — so a breacher who turns to deal with something
  behind them loses the front they were covering. This is what keeps an entry a
  squad problem rather than a solo trick.
- **Always expiring.** Mitigation has a duration, and the duration is authored.
  There is no mitigation without a clock.
- **It is not a pool and never regenerates one.** Mitigation refuses damage; it
  does not restore armor or structure. Armor reaching zero stays an armor break.
- **It does not touch accuracy.** Whether a shot lands is upstream and already
  has its own authored lever in a pattern's incoming-accuracy multiplier.
  Mitigation acts on damage that has already survived hit testing.
- **Mitigations do not sum.** When more than one applies, the strongest single
  one does. Summation is how two individually reasonable authored numbers reach
  immunity without anyone noticing, and the arc rule cannot save a design that
  has already reached 1.

## Scope

- A live mitigation capability on the target, carrying fraction, arc, facing
  reference, and remaining duration. It is optional in the same sense armor is:
  most actors never have one.
- `DurabilityModel` extended to consume it in the same shared calculation that
  serves live application, AI prediction, and balance tests. Prediction that
  ignores mitigation would make the AI systematically wrong about exactly the
  moment the capability exists to create.
- Arc resolution against the incoming direction and the target's facing, at the
  seam where cover is already resolved.
- Telemetry: mitigated damage recorded alongside resolved armor and structure
  damage, and surfaced by whatever `d3-durability-evidence-and-ui.md` builds.
- The breacher assist becoming the first carrier, replacing the movement-only
  effect with the authored pair.

## Out of scope

- Localized facings, ablative segments, or per-limb armor for ordinary
  durability. Mitigation is a temporary capability with an arc; it is not an
  argument for giving every actor permanent facings.
- Damage types and resistances-by-type.
- Armor regeneration or repair.
- Mech and vehicle adoption. The concept must be general, but proving it on one
  infantry system first keeps the first authored numbers honest.

## Acceptance

- A mitigated hit resolves less damage than the same hit outside the arc, and
  the difference appears in telemetry as mitigation rather than as armor.
- Turning the target so the shot arrives outside the arc removes the effect
  entirely, in the same tick.
- The effect ends on expiry with no residue: the target resolves damage exactly
  as it did before the system was spent.
- Two simultaneous mitigations resolve as the stronger, not the sum.
- The TTK harness reports the arc contribution separately, and the shipped
  band-versus-grade and arc bounds still hold with the breacher assist live.

## Open questions

- Whether cover and mitigation should ever interact beyond ordering — a marine
  behind a wall *and* behind a screen is currently just both, which is probably
  right and is worth confirming against a measured case rather than assuming.
- Whether the arc should be authored in degrees or as a named facing band.
  Degrees are what the breacher assist already declares and are easier to
  validate; a named band would be easier to read in the Armory.
