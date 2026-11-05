# Battle radio mix and content-feel pass

Status: READY — implementation is shipped; manual play acceptance remains.
Written: 2026-08-23
Updated: 2026-11-04 — the passive combat reports are gone; order exchanges are what remains to accept.

Read `battle-radio-nouns.md` before running or changing this validation.

## Purpose

Order exchanges are shipped, but their final acceptance question is
experiential: do they remain intelligible and useful without crowding combat
sound or music in real battles?

## Validation pass

Play representative ground battles in the Marine Operations battle screen,
retasking squads through their ordinary and company-wide orders.

Record only observable mix, overlap, repetition, misleading-content, or
positioning failures with enough battle context to reproduce them. If tuning
or line changes are needed, contract a separate bounded story against the
standing radio laws; do not reopen either retired implementation story.

## Acceptance

- Calls are intelligible at normal combat and music levels without becoming a
  continuous foreground voice track.
- The speaking squad's identity reads credibly in its radio portrait and subtitle.
- Orders describe assignments that actually changed and do not feel late or
  misleading under the exchange gap.
- Repetition and cadence are tolerable across a representative battle.
- Any required tuning or content change is captured as a concrete follow-up
  with observed evidence.

## Out of scope

- Automated tests for a manual audio-feel pass.
- New exchange vocabulary, speaker classes, faction identities, or net behavior.
- Speculative changes to timing, volume, or lines without an observed
  failure.
