# Retire mech combatant behavior

Status: DRAFT — remove the obsolete behavior shell without changing current mech fire policy.

Written: 2026-08-23

Read `ai-nouns.md` and `mechs-nouns.md` before planning this cleanup.

## Problem

Mech planning already runs through the GOAP path, but the former
`MechCombatantBehavior` still presents itself as a live `UnitBehavior` while
current GOAP actions and tests use its static firing helpers. The name now
misstates ownership: a dormant legacy loop is carrying the shared mech weapon
procedure.

## Scope

- Extract the shared installed-mount firing procedure into a neutral,
  mech-domain helper whose name describes firing rather than behavior.
- Move every current GOAP action, rescue formation, test, and focused Javadoc
  reference to that helper.
- Remove the obsolete behavior singleton and per-unit update path once no
  dispatcher or caller depends on it.
- Preserve all existing mount gating, target/line-of-sight rules, cooldown,
  ammo, burst, and indirect-fire behavior exactly; this is not a balance or
  doctrine rewrite.

## Acceptance

- No production dispatch selects the retired behavior type.
- The neutral helper is the sole owner of the shared mech fire procedure.
- Existing mech role actions retain the same legal-fire decisions and focused
  coverage after the move.

## Out of scope

- Consolidating infantry, mech, and drone action libraries.
- Changing chassis, role, loadout, weapon, or ballistics authority.
- Parallelizing squad replanning or broadly refactoring tactical scoring.

## Exit

Fold the cleared ownership boundary into `ai-nouns.md` or `mechs-nouns.md` as
appropriate, add this story to `shipped.md`, and delete it when shipped.
