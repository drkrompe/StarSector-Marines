# Cleanup — One persistent XP authority

Status: PROPOSED — evaluate after `s4-performance-derived-experience.md` settles awards.
Written: 2026-08-23

Read `progression-nouns.md` before accepting or implementing this cleanup.

## Evidence

Production progression applies persisted XP through `MissionResolver` and
`MarineRoster.applySoldierOutcome` from a frozen mission outcome.
`CombatService.addExperience` instead mutates a battle-local `SoldierProfile`;
it has test callers but no production caller.

That unused seam makes a second XP authority appear legitimate even though the
standing law is that campaign-persistent experience is awarded from the frozen
outcome. S4 must settle the award model before this cleanup chooses whether the
method is obsolete or has a deliberately non-campaign role.

## Acceptance

- After S4, either remove `CombatService.addExperience` and its obsolete test
  surface, or document and test a bounded non-campaign purpose for it.
- No battle-local caller can bypass the frozen-outcome award model to change a
  campaign marine's persistent XP.
- Debug fixtures may author starting XP, but are not an award authority.
