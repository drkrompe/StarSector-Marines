# Cleanup — One target-market resolution authority

Status: PROPOSED — preserve both current predicates while removing the duplicate scan.
Written: 2026-08-23

Read `campaign-battle-bridge-nouns.md` before accepting or implementing this
cleanup.

## Evidence

`MissionLaunch` currently asks `DetachmentResolver.planetHasHeavyArmaments`
for the defender-mech gate and separately asks `TargetProfileResolver.resolve`
for the target profile. Both operations traverse the vanilla economy by target
planet name, but their derived facts are not interchangeable: heavy industry
and orbital works may permit heavy armor without increasing the profile's
defense level, while the economic-function vocabulary also groups light
industry into its broader industry role.

The bridge law is one campaign-boundary read followed by plain values. The
parallel scans are the legacy boolean and its structured successor coexisting,
not two intended authorities.

## Intended outcome

Resolve the target market once and derive both the immutable target profile and
the existing heavy-armament eligibility fact from that one boundary result.
Choose an explicit plain-data representation rather than re-deriving the mech
predicate from lossy profile fields.

## Acceptance

- Mission launch performs one target-market lookup for these battle facts.
- Heavy industry, orbital works, ground defenses, and heavy batteries remain
  eligible for heavy armament; light industry alone and no-market scenarios
  remain ineligible.
- Every mission path receives the same target snapshot or explicit Neutral
  result; no battle/generation code reaches back to `MarketAPI`.
- Existing target-profile, defender-roster, and neutral-path behavior remains
  covered by focused verification when this code cleanup is implemented.

## Out of scope

- Changing which markets may field mechs.
- Making defense level the defender-roster authority.
- Adding new target-profile consumers or campaign-state reads.
