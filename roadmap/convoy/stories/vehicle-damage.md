# Story: anti-vehicle weapons → trucks take damage

Status: PLANNED — convoy vehicles do not yet participate in damage/destruction authority.

Written: 2026-05-28

Updated: 2026-08-23 — normalized around the current component-native vehicle model.

Read `convoy-nouns.md` first. This story wires vehicles into combat damage so
the player can counter a reinforcement push.

## Scope

- Add component-native health/damage state using the existing battle damage
  authority rather than reviving the retired `Vehicle` object model.
- **Damage sources.** Marines' rocket launchers + mech LRMs damage trucks;
  direct fire (rifles) does less than rockets.
- **HP-zero → wreck.** Remove the live vehicle authority and create a persistent
  wreck presentation/obstacle. Define which navigation state the wreck changes
  and how route/clearance inputs observe or invalidate that change; do not
  assume a render doodad blocks motion by itself.
- **Crew fate.** Driver/passengers on a destroyed truck either die or
  eject as scattered militia (1–2 survivors, low HP), drawn from the
  per-faction roster.

## Why it unblocks other work

Air ↔ ground interaction (shuttle A2G turrets vs. trucks) becomes meaningful
once vehicles can be damaged. This story is the prerequisite.

## Acceptance

Anti-vehicle fire can destroy an APC; destruction resolves its passengers once,
replaces the live actor with an honest wreck/obstacle, and forces later vehicles
to stop or reroute without clipping it. Ordinary small-arms fire remains a
meaningfully weaker counter.
