# Battle radio mix and content-feel pass

Status: READY — implementation is shipped; manual play acceptance remains.
Written: 2026-08-23
Updated: 2026-10-22 — only Marine Operations voices squad radio now; the positional MW4 pool was dropped.

Read `battle-radio-nouns.md` before running or changing this validation.

## Purpose

The radio policy and expanded cue library are shipped, but their final
acceptance question is experiential: do radio reports remain intelligible
and useful without crowding combat sound or music in real battles?

## Validation pass

Play representative ground battles in the Marine Operations battle screen.
Exercise ordinary contact and fallback calls plus
friendly fire, hostile-mech contact, enemy-down, and sustained engagements.

Record only observable mix, overlap, repetition, misleading-content, or
positioning failures with enough battle context to reproduce them. If tuning
or pool changes are needed, contract a separate bounded story against the
standing radio laws; do not reopen either retired implementation story.

## Acceptance

- Calls are intelligible at normal combat and music levels without becoming a
  continuous foreground voice track.
- The speaking squad's identity reads credibly in its radio portrait and subtitle.
- Event lines describe facts that actually occurred and do not feel late or
  misleading under the shared voice budget.
- Repetition and cadence are tolerable across a representative battle.
- Any required tuning or content change is captured as a concrete follow-up
  with observed evidence.

## Out of scope

- Automated tests for a manual audio-feel pass.
- New event vocabulary, speaker classes, faction identities, or net behavior.
- Speculative changes to timing, volume, or pool contents without an observed
  failure.
