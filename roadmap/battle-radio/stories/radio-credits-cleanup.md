# Cleanup — Reconcile battle-radio source credits

Status: PROPOSED — confirm the intended credit granularity before editing.
Written: 2026-08-23

Read `battle-radio-nouns.md` before accepting or implementing this cleanup.

## Evidence

The audio manifest contains 214 proprietary placeholder radio clips across
the shipped cue pools. `mod/CREDITS` still names only the twelve clips used by
the original background-radio slice, so its apparently exhaustive "Voice
clips used" list no longer describes the packaged content.

## Intended outcome

Make the credit record accurately cover the proprietary radio sources in the
current manifest. Prefer a durable source/family statement over a brittle
214-line inventory if that is sufficient attribution; otherwise generate or
verify the exact list from the manifest.

This cleanup does not clear the clips for release and does not replace them.
The proprietary-placeholder constraint and eventual replacement direction
remain unchanged.

## Acceptance

- Every packaged proprietary radio source is covered by the credits language.
- The credits do not imply that only the original twelve clips are present.
- The manifest's deprecation note and the credits describe the same provenance
  and release constraint.
- Verification compares the manifest and credits directly; no runtime or test
  suite execution is required for this documentation cleanup.
