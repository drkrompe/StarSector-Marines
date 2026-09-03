# Orbital lift: the marines come down from orbit, and the whole force lands early

Status: PLANNED — owner direction on 2026-09-02 after playing the 560x336
Conquest: "we're simply not able to get enough soldiers onto the field."

Written: 2026-09-02

## What the matrix and the owner both saw

Marine arrivals fly in from the map edge and back out to it, three shuttles on
a fixed cycle, so the cadence is **~460 ticks plus ~5.3 ticks per cell the
beachhead sits inland** and it does not scale with the force. Full Strength
commits 34 squads through the same three pairs Reinforced uses for 17 and
lands its last squad with 449 ticks of the battle left; alive squads plateau
at 12–15 from tick 6,000 onward because arrivals exactly replace losses. The
force is committed in penny packets and destroyed at the far end of a long
approach. Reinforced-south, which lands at 29% of the battle, is the fixture
that fights; Full Strength, which lands at 97%, is the one that cannot.
`conquest-560-contact.md` measured all of that and named it a finding, not a
fix, because the cadence is a mission-authored lever.

The owner has now decided the lever. Two changes, one story.

## The model

**Marine shuttles come from orbit.** A marine arrival does not cross the map
edge. It appears at a **descent point** a stated number of cells off its berth
on the side away from the objective, at altitude, and flies down; after
deboarding it climbs back to the descent point and is gone. The next load is
already in orbit. A round trip is therefore the descent, the turnaround on the
pad and the climb — a constant measured in cells of descent, never in map
size and never in how far inland the standoff put the beachhead. This is the
hand-wave the owner asked for, and it is honest: the transports are in orbit,
and only the last leg is flown.

This is a **marine arrival policy**, not a change to the air corridor.
Defender shuttles (`ShuttleMeans`) are planetary and keep crossing their own
edge; aircraft sorties keep their strips. The descent point is a corridor of
its own kind, and `AirCorridor` learns one more way to be made.

**The lift scales with the seats.** The mission states a **landing share**:
the fraction of the battle by which the whole committed force should be on
the ground, defaulting to the share the winning fixture already has (about
30%). From seats, shuttle capacity and the constant round trip, the plan
derives how many shuttles fly and how many are in the air at once — waves
overlap, one wave descending while the last is still on the pad — and how
many berths that needs. `dropZoneCount` and `shuttlePairsPerZone` remain
authorable overrides; when the mission says nothing they are derived from the
share rather than fixed at three. A berth is a place on the landing precinct,
so the landing place's program grows the berths the lift needs.

**Arrival order is the commander's.** More lift does not change which squad
lands first; the arrival slots keep their order and their lane assignment.

## What it does not do

- It does not make the defender's lift orbital, or scale defender
  reinforcement; that is `reinforcement-strength-scaling.md`.
- It does not decide what a lost beachhead costs the lift; that stays the open
  row on the conquest board, and this story gives it something to cost.

## Acceptance

- The cycle no longer moves with the beachhead: measured on reinforced-south at
  `CLOSE` and at `FAR`, the round trip is the same to within one turnaround.
- On full-strength-west the last squad lands before the stated landing share
  of the battle, and the alive-squad plateau is gone from the trace.
- Full Conquest matrix, chain on, both fixtures, against main's baseline
  (south 22 captures / 17 held, west 12 / 6): the west holds more than 6; the
  south does not lose held ground.
- The annotated review frames show shuttles appearing at the descent point
  rather than at the edge, and the fixture record states the lift.
- `simDeterminism` green.

## Plan

1. `AirCorridor.descent(...)` and a marine-side orbital lift in the arrival
   setup; a `LandingShare` on the mission with the derived shuttle count and
   in-flight overlap; unit tests on the derivation.
2. Arrival slots and berth count follow the derivation; the landing precinct
   program takes the berth count.
3. Matrix, frames, fold into `reinforcement-nouns.md` and `conquest-nouns.md`
   (the arrival policy paragraph).
