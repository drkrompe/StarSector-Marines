# Extraction objective contract

Status: IN PROGRESS — implementation and automated evidence are complete; fold and retirement remain.

Written: 2026-08-27

Updated: 2026-08-27 — shipped the generic objective, variant projection, disclosure, fixture, and short duplicate evidence pass.

Read `mission-command-nouns.md`, `extraction-command.md`, and
`contracts-nouns.md` before planning this story.

## Intent

Replace generic Extraction's elimination placeholder with an authoritative
objective seam that scenario-specific corridor and branch commanders can
adapt without duplicating payload truth.

## Scope

- Define stable payload/cohort identity, source, destination/egress, progress,
  escort/control relationship, loss, abandonment, boarding, and terminal law.
- Separate public route geometry, own-side payload state, opponent disclosure,
  and neutral outcome facts.
- Define the minimum adapter contract used by ordinary Extraction, Civilian
  Rescue, Silent Colony, and future recovery/escort variants.
- Decide which opposition shapes require a squad commander and which require a
  mission director with different force and knowledge semantics.
- Add fixture serialization and neutral evidence before a generic command
  strategy consumes the contract.

## Acceptance

- [x] A fixture can resolve success or failure from payload/cohort and egress
  state without eliminating either faction.
- [x] Each perspective receives only its legally disclosed objective state.
- [x] Civilian Rescue and Silent Colony can adapt the contract without losing
  their cohort/branch-specific laws.
- [x] A timeout remains a timeout, and neutral payload progress never feeds
  back into an unauthorized commander or director.
- [x] New evidence is selected by `-Pmission` rather than a new Gradle task.

## Constraints

- Do not force every Extraction variant into one identical route or opponent.
- Do not move civilian, archive, survivor, loot, or campaign consequence
  authority into mission command.

## Exit

Fold generic corridor/payload command laws into `extraction-command.md`, fold
objective law into its owning mission noun doc, add the story to `shipped.md`,
and delete it when the contract ships.
