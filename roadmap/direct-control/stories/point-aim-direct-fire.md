# Point-aim direct fire

Status: PLANNED

Written: 2026-09-23

Read `direct-control-nouns.md` and `ballistics-nouns.md` first.

## Goal

Let a ground direct-fire weapon commit a round along a player-supplied world
bearing with no target entity, while sharing the existing contact, damage,
flight, and presentation authorities. This is the firing substrate for the
first controlled Marine.

## Plan

1. Add a typed point-aim request beside entity-target fire intent. Validate
   shooter, weapon, reach policy, cadence, and ammunition at the serial firing
   boundary. A wall on the ray is a physical stop, not a reason to refuse a
   manual trigger.
2. Extend aim sampling and `BallisticResolver` with a target-free trajectory:
   calibrate weapon/profile/stance dispersion around the cursor bearing, then
   trace physical contacts in travel order. Preserve source and hardpoint
   origin, cover and friendly-fire rules, flight time, and payload scheduling.
3. Carry point aim through burst continuation and shot telemetry. The same
   committed path must drive damage and `ShotEvent`.

## Acceptance

- A point shot can be fired toward empty ground without inventing a target,
  stop at a nearer wall or cover, strike an interposing moving body, and leave
  its modeled reach without a phantom impact when unobstructed.
- Cursor-on-body does not guarantee a hit; weapon spread and moving-fire
  penalty remain observable. No target id is invented or revealed through fog.
- Friendly catches and training holds use the existing rules. Cooldown,
  ammunition, burst spacing, delayed damage, and visual flight agree with an
  ordinary direct shot.
- Small direct-fire unit tests cover sampled direction and ordered contacts.
  A focused opt-in scene compares a controlled shooter against physical
  obstacles and moving targets without adding a whole battle to `test`.

Indirect weapons and area placement are outside this story; they need their
own ground-point targeting contracts.
