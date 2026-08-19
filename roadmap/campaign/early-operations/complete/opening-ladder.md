# Opening ladder — shipped 2026-08-19

The Independent broker now offers a two-step introduction for a genuinely
green company.

## What shipped

- `Hold Until Relieved` appears while the roster has at most eighteen soldiers
  and no soldier has reached Regular experience. One employer militia shuttle
  and one player sortie join eight fixed local militia against twelve advancing
  bandits.
- Victory records the existing story-completion key and unlocks `Take Back the
  Depot`: one employer militia shuttle plus two player sorties against twelve
  militia raiders.
- Both operations have empty fighter rosters and a dedicated LOW-scale battle
  setup that omits mechs, defense posts, shuttle turrets, and reinforcement
  systems.
- Employer shuttle seats use LOW-risk militia equipment/personnel rolls. The
  campaign deployment overlay still begins after those ships, so named player
  soldiers and their mixed persistent gear occupy only player seats.
- A symmetric `OpeningOperationCommand` can order either faction toward its
  nearest finite enemy force while preserving authored local garrisons. Ambient
  defender patrol behavior now yields to explicit commander assignments.
- Defeat leaves each job available for retry; victory removes that rung exactly
  once through `MarineRoster.completedStoryIds`.

## Verification

- Focused eligibility, routing, commander, and battle-composition tests pass.
- `gradlew.bat build` passes on the complete repository.

## Deferred tuning

Live play must decide whether the relief force can hold long enough for a slow
player transport, and whether either mission wins too readily without player
intervention. Future tuning should adjust militia counts and approach distance
before adding regulars or heavy support.
