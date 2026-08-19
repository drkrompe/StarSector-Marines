# Narrative S4 — Patron-Linked Chronicle References

**Status:** SHIPPED (2026-08-19, `03d8a24e`)

## Goal

Let the comms officer connect a patron to one recent Chronicle fact the player
has already learned, so political events can surface naturally in contract
briefings without inventing motives or hidden knowledge.

## What shipped

- `PatronChronicleMemory` selects one recent confirmed Chronicle row in which
  the current patron is the actor or target. It supports terminal chain
  outcomes, applied throne claims, and kingmaker testaments.
- Eligibility requires valid persisted houses and display names, a valid market,
  event-specific lineage, and knowledge available by the frozen offer day.
  Rumors, unsupported types, self-links, malformed rows, future knowledge, and
  facts older than the inclusive prior 365 days are rejected independently.
- Selection is deterministic by newest learned day, happened day, then immutable
  Chronicle id. Rendering is stable from the current contract id and source
  Chronicle id.
- Six data-authored pools distinguish the patron's actor/target role for chain
  outcomes, throne transfers, and testaments. Lines state only the frozen fact,
  other house, measured chain result where relevant, and age.
- `PatronBriefingContextComposer` now establishes explicit relevance order:
  direct S1/S2 patron history, S4 patron-linked Chronicle fact, then the S3
  same-market engagement echo.
- Missing or malformed S4 content fails closed to S3 or an unchanged briefing.
  No persistence, reputation, economy, or UI layer was added.

## Verification

- Automated coverage locks every eligibility filter, both patron roles, exact
  age boundary, newest-row and id ordering, all six voice pools, token
  replacement, precedence, deterministic replay, save/load, and content
  fallback.
- The authored JSON file, focused S1–S4 matrix, and full `gradlew test` suite
  pass.
- Manual UI validation remains deferred by user direction.

## Deferred

- Captain observations, target name-checks, rumor callbacks, inferred political
  meaning, longer conversations, patron state evolution, and new UI remain
  future stories.
