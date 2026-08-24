# Commander field analysis

Status: DRAFT — define and expose the read-only diagnostic before a commander acts on it.

Written: 2026-08-23

Read `ai-nouns.md` before planning this story.

## Intent

Turn each faction's immutable commander influence snapshot into an inspectable
frontline, bulge, and breakthrough diagnostic. The result helps validate that
the side's own imperfect knowledge produces a coherent strategic picture; it
does not yet change any squad assignment.

## Scope

- Derive a deterministic signed local-balance/frontline representation from
  the existing friendly and believed-hostile fields.
- Identify and expose bounded candidate bulges and breakthroughs with enough
  provenance to explain their supporting field values and topology boundary.
- Publish the result through existing debug/diagnostic surfaces for both
  factions, preserving the distinction between each side's knowledge.
- Cover disconnected rooms, walls, sparse/empty fields, and deterministic
  ties with focused automated tests when implementation begins.

## Constraints

- Consume only the immutable snapshot for the faction being analyzed. Do not
  inspect hidden enemy positions, other-faction beliefs, player vision, or
  live assignment state to improve the answer.
- The result is read-only. It must not redirect a squad, create a reserve,
  publish a subordinate briefing, or alter tactical doctrine.
- Keep map geometry ownership with `mapgen-nouns.md`; this story analyzes the
  supplied tactical field rather than creating a second navigation model.

## Exit

Fold the durable diagnostic vocabulary and honesty law into `ai-nouns.md`,
add this story to `shipped.md`, and delete it when the diagnostic is shipped.
