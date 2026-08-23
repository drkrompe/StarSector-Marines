# Squad-id terminology cleanup

Status: PROPOSED — Terra synthesis finding
Written: 2026-08-23
Updated: 2026-08-23 — proposed from noun/code reconciliation.

Read `company-view-nouns.md` before changing this story.

## Problem

Mission and stationing APIs use names such as `deployedFireteamIds` and
`fireteamIds` for values that are persistent `MarineSquad.id()` values. A squad
now means twelve marines in three derived four-marine fire teams, so the old
name is no longer an informal synonym: it misstates an identity boundary and
invites future code to treat a squad selection as one team.

The misleading comments on immediate UI seams have been corrected. Renaming the
API and payload fields is separate because some values cross campaign save and
event boundaries.

## Open outcome

Rename squad-bearing fields, parameters, builders, and accessors to use
`squadIds` consistently across mission outcomes, results, stationing and
garrison event payloads, response and lapse resolution, and their tests.

Before changing persisted fields, determine the XStream/save compatibility
contract. Preserve existing saves through aliases or migration when practical;
if compatibility is deliberately broken, document and version that decision
instead of allowing an accidental field rename to make it.

## Acceptance

- No API, field, parameter, comment, or test calls a `MarineSquad.id()` a
  fire-team id.
- Genuine four-marine fire-team identifiers, if introduced later, have a
  distinct type and vocabulary.
- Existing serialized outcomes and pending stationing/garrison events either
  load correctly or follow an explicit, tested compatibility decision.
- Mission selection, results, response, lapse, and stationing behavior are
  unchanged apart from terminology and migration handling.
