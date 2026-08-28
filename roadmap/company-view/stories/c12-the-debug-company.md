# C12 — Debug-company combined arms

Status: DEFERRED

> **Quality axis re-sourced, 2026-08-27.** Experience is issued with the armour
> pattern (`progression-nouns.md`), so a stage's quality axis is now the best
> armour it has collected rather than authored XP. `DebugBilletPlan` states that
> ceiling and `DebugCompany` draws each squad's armour doctrine from the faction
> catalog within it: flavour stays randomized, the stage keeps meaning what it
> says, and coverage becomes per band. The three bands partition the authored
> catalog with no unreachable doctrine, which a test now guards.
> `DebugBilletPlan` now states only that ceiling; its authored-XP method is
> gone. — slices 1–2 shipped; waiting for player-side vehicle deployment
Written: 2026-08-23
Updated: 2026-08-23 — folded the detached debug roster and mech-stage defaults into `company-view-nouns.md`.

Read `company-view-nouns.md` before changing this story.

## Open outcome

When player-side ground vehicles become deployable, a debug-company stage can
declare a representative convoy alongside its infantry and mech support. The
fixture should exercise the same player deployment seam rather than inventing a
debug-only vehicle path.

`DebugCompanyStage` is the extension point: it already describes a campaign
arc position and its supporting arms. `MechSupport` is the precedent for a
command-power-delivered supporting asset, but it is not evidence that a player
vehicle deployment contract exists yet.

## Gate

Do not implement this story until the player-side vehicle deployment authority
and lifecycle are defined in their owning feature. Once that exists, replace
this gate with concrete dependencies on those noun laws and stories.

## Acceptance

- A stage-selected convoy uses the production player deployment path.
- The debug fixture remains detached from campaign persistence.
- Stage selection and explicit debug pickers have one documented precedence
  rule rather than silently overwriting each other.
- Infantry identity, task-force behavior, and mech support remain unchanged.
