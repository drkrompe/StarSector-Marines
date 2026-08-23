# Monotonic clock contract comments

Status: READY

Written: 2026-08-23

## Outcome

Make source documentation describe the shipped campaign-day contract without
changing behavior.

## Scope

Correct comments that label a `CampaignClock` value as the sector calendar-day
component, beginning with the `CampaignSystem` tick parameter and persisted
campaign cadence fields. Audit the campaign package for the same ambiguity and
leave intentional calendar-date presentation language intact.

Do not alter clock arithmetic, anchor migration, system ordering, or any
feature deadline.

## Acceptance

- Public system and state comments identify the tick argument and persisted
  cadence values as monotonic campaign days.
- No comment tells a caller to derive elapsed time from Starsector's repeating
  calendar-day component.
- The documented distinction agrees with `CampaignClock` and
  `campaign-framework-nouns.md`.

## Verification

This is documentation-only cleanup; inspect the changed comments and search
for the stale wording. Do not run tests solely for it.
