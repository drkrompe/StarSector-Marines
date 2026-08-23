# Scripted Reinforcement Triggers

Status: PLANNED

Written: 2026-08-23

Read `reinforcement-nouns.md` before implementing this story.

## Current substrate

`SCRIPTED_TIMER` exists as a diegetic request reason, and external callers may
post requests, but there is no authored mission trigger contract for timed or
event-driven reinforcement.

## Goal

Let a mission author declare a bounded reinforcement trigger that enters the
same request, ticket, means, and objective flow as reactive triggers.

## Decisions

- Define the supported timing/event inputs and one-shot or repeat semantics.
- Require explicit side, strength, delivery hint policy, and optional objective.
- Decide whether inability to pay waits, expires, or reports a mission error.
- Keep reason informational; mission-specific behavior stays in trigger data.

## Acceptance

An authored trigger posts through the shared service at the declared moment,
cannot bypass resource or means feasibility, and produces the same delivery
lifecycle as an equivalent reactive request.

## Out of scope

- A general-purpose scripting language.
- Hardcoded means selection in mission data.
- Commander-initiated or player-manual requests.
