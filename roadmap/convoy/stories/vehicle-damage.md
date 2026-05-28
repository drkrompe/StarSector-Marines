# Story: anti-vehicle weapons → trucks take damage

**Queued.** V1 trucks have no HP and never take damage. This wires them
into the existing damage system so the player can counter a reinforcement
push.

## Scope

- **`Vehicle.hp` field** — already wired-forward on `Shuttle`; copy the
  pattern.
- **Damage sources.** Marines' rocket launchers + mech LRMs damage trucks;
  direct fire (rifles) does less than rockets.
- **HP-zero → wreck.** New `wreckedSpriteFrame` on `VehicleType` (or a
  separate `wrecks.png` sheet). The wreck stays as a blocking doodad on
  the road — which the road-reservation / footprint check already treats
  as non-walkable terrain, so pathing routes around it for free.
- **Crew fate.** Driver/passengers on a destroyed truck either die or
  eject as scattered militia (1–2 survivors, low HP), drawn from the
  per-faction roster.

## Why it unblocks other work

Air ↔ ground interaction (shuttle A2G turrets vs. trucks) is easy once
vehicles have HP — see the Parked list in [`../overview.md`](../overview.md).
This story is the prerequisite.
