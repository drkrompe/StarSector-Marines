# Marine-Side Reinforcement

Status: PLANNED

Written: 2026-08-23

Read `reinforcement-nouns.md` before implementing this story.

## Current substrate

Requests and faction rosters can represent either side, but every current means
rejects non-defender requests and the installed triggers describe defender
response. Marine tickets may exist without a production dispatch contract.

## Goal

Define and ship one coherent marine-side reinforcement path without assuming
that defender compounds, priorities, and triggers are automatically symmetric.

## Decisions

- Name the marine trigger and the player-facing authority that may post it.
- State which marine-held capability supplies each eligible delivery means.
- Decide whether dispatch is automatic, player-authorized, or mission-authored.
- Preserve the shared request, ticket, objective, and means-selection laws.

## Acceptance

A marine request has an explicit source, pays authoritative capacity, chooses
only genuinely available marine means, and produces ordinary faction-correct
actors with a visible tactical objective. Defender behavior remains unchanged.

## Out of scope

- Treating captured defender infrastructure as marine supply without a design.
- Command-power deck selection or campaign fleet commitment.
- General strength scaling across all means.
