# Mission counter-attack

Status: IN PROGRESS — untriggered counter-attack shipped on the four procedural defenders with evidence (`conquest-command.md`); the triggered form remains.

Written: 2026-10-21

## Why

The owner (2026-10-21): counter-attacks on the procedural missions "would be
cool, we might need to be reasonable with it though. Triggered versions might
be useful for mission design." Raid, Assault, Sabotage and Extraction defenders
man their places reactively (`garrisonables.md`, mission defenders) but never
push for a place they lose, so a place the marines take there is never
contested and a hold never meets a counter-attack (`conquest-command.md`, a
place the defender loses: where the hold exists the counter-attack does not).

## Scope

- The four procedural defender commanders retake the places that matter to
  their mission on the shared `PlaceCounterattack` (probe, size from observed
  holders, muster, launch together or stand down, give up after two failures),
  generalised rather than forked.
- **Places that matter:** the mission's objective places (the Raid target until
  seized, the unfinished Sabotage sites, the Extraction source until the payload
  is decided) and its key buildings — the buildings its born garrisons hold —
  beside them; on Assault, which has no objective place, every strongpoint.
- **How a place stands** is belief and own force (no capture record exists on
  these maps): lost after hostile movers have been believed on its ground for a
  couple of pulses with no own squad on it, whole again once nothing hostile has
  been believed there for a few.
- **Reasonable:** the mission decides first. The counter-attack recruits only
  the mission's held reserve and quiet authored garrisons away from the fight
  (never a post, an objective's own garrison, an interdictor, a crew); its group
  is at most a share of the side's infantry; it has a budget of pushes a battle;
  and it sizes a place at parity with what it observed and goes once it can
  raise half of that, or not at all (evidence: `conquest-command.md`).
- **Triggered versions:** a mission can author "counter-attack this place when
  <trigger>", consuming the place-directive seam `session/party-places` lands
  in `garrisonables.md`, behind one small adapter. One authored on a mission or
  scene.

## Acceptance

- `commanderEvidence -Pmission=raid,assault,sabotage,extraction`, six seeds each,
  against the same-build control `-Dbattle.mission.counterattack=false`:
  launches, retakes, holds met, timeouts, outcomes, losses, brawl share. The
  missions stay winnable; the brawl share does not fall.
- A scene asserting a procedural-mission counter-attack mechanic on a non-fort
  place (probe, size, launch or stand down), and one for a triggered
  counter-attack.
- Green: `place-counterattack`, `fort-counterattack`, `attacker-holds-ground`,
  `mission-defence`, `mission-envelopes`, `mission-flow`; unit tests under a
  second each.

## Plan

1. `PlaceCounterattack.Doctrine` gains a margin, a share cap, a launch budget and
   a floor kept only out of the pool; Conquest's and the story pools' doctrines
   are unchanged.
2. `MissionPlaces` (places and their standing) and `MissionCounterattack` (the
   mission doctrine, garrison sortie, and the proposal/offer rewrite) wired into
   the four commanders ahead of reactive manning.
3. Evidence against the control; tune what "reasonable" is.
4. Triggered form on the directive seam; scene.
5. Fold into `conquest-command.md` and `garrisonables.md`; ledger; delete.
