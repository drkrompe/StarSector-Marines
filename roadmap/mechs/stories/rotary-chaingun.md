# Rotary chaingun — hold to fire, spin-up, a belt that refills

Status: IN PROGRESS

Written: 2026-10-23

Read `mechs-nouns.md` (mounts, flux, the missile replenisher) and
`direct-control-nouns.md` (mouse buttons, weapon cycling, ammo strips) first.

## Goal

A mech chaingun feels like a spun-up rotary gun. Today it is a click for a
six-round burst on a mechanical cycle, unlimited, and its sustained rate is
really set by flux. The owner's model (2026-10-23):

- **Firing.** Hold the button to fire. The rate ramps from slow to full over a
  spin-up time; the barrels coast at speed briefly after release, so a quick
  re-press fires at full rate; then they spin down.
- **Ammunition.** Unlimited over a fight. The gun feeds from a belt (a few hundred
  rounds) that refills from an internal hopper at a fixed rate. Holding too long
  empties the belt, and then the gun fires only as fast as the refill.
- **Upgrade path.** Spin-up time, belt refill rate and damage make a better
  chaingun. They are data, and a better chaingun is a better component.
- **Balance.** A stock chaingun's sustained output over a normal engagement
  stays close to today's (`armorMatchup` before/after, Bulwark and Hound rows,
  and the control).
- **AI.** The same mechanic: sustained fire while a target is exposed, barrels
  kept spun while engaged, easing off before the belt runs dry.
- **Direct control.** The trigger is held. The row's strip is the belt (the
  folded `MagazineGauge` belt with a count), and the cycle bar shows spin.

## Scope

Chaingun only (`CHAINGUN`, `NOSE_CHAINGUN`, and their better tier). Missile
racks, the other burst guns and vehicle turrets are unchanged. A sibling story
is moving missile racks to hold-to-ripple in the same files; this one keeps to
the chaingun path.

## Model

- The weapon's `sim.rotary` block owns the feed: spin-up, coast and spin-down
  seconds, the rate at first turn and at full speed, the belt, the refill rate
  and the flux each round costs. A weapon with the block is a **rotary** weapon.
- The mount owns the live state: spin (0 to 1), coast left, belt rounds and
  the fraction of the next round. One rule advances it for the pilot and the AI.
- The refill is the belt's own hopper, the chaingun counterpart of the missile
  replenisher's cadence: it runs every tick for the life of the mech.
- Flux stays the shared reservoir, charged per round; it is small enough that
  the belt, not the reservoir, is what a long hold runs out of.
- The better tier (`component.mech-chaingun-hf`, `component.mech-nose-chaingun-hf`,
  Advanced) spins up faster, refills faster and hits harder.

## Acceptance

- Unit tests (<1 s): spin-up ramp, coast and spin-down, belt drain and refill,
  rate capped at refill once dry, tier differences, AI reserve hysteresis.
- `armorMatchup` before/after for Bulwark and Hound rows and the control.
- Direct-control HUD frame at 1920x1080, 100% UI: belt mid-drain, spinning up.
- Noun docs updated; the story folded and retired when shipped.
