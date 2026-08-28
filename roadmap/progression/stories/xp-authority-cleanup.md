# Cleanup — One persistent XP authority

Status: READY — unblocked; issued experience bands removed the award model this was waiting on.
Written: 2026-08-23
Updated: 2026-08-27 — rank-and-file XP is no longer a progression dial, so the second authority is now plainly obsolete rather than pending a decision.

Read `progression-nouns.md` before accepting or implementing this cleanup.

## Evidence

Production progression applies persisted XP through `MissionResolver` and
`MarineRoster.applySoldierOutcome` from a frozen mission outcome.
`CombatService.addExperience` instead mutates a battle-local `SoldierProfile`;
it has test callers but no production caller.

That unused seam made a second XP authority appear legitimate. With experience
now issued from the squad loadout definition rather than accumulated per marine,
there is no campaign award model for it to belong to: the method is obsolete
unless a bounded non-campaign purpose is documented and tested.

This cleanup now also owns the disposal of `MarineSoldier.experienceXp` itself.
It stops being a mechanical input; decide explicitly whether it remains a
displayed service statistic or is removed, and record which.

## Acceptance

- Either remove `CombatService.addExperience` and its obsolete test surface, or
  document and test a bounded non-campaign purpose for it.
- `MarineSoldier.experienceXp` is explicitly kept as a displayed statistic or
  removed; it is not left as an inert field with no stated role.
- No battle-local caller can change a campaign marine's persisted quality.
- Debug fixtures may author starting bands, but are not an authority over
  campaign quality.
