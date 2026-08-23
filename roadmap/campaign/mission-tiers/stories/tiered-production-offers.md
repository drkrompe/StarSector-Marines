# Tiered production offers

Status: PROPOSED

Written: 2026-08-23

Read `mission-tier-nouns.md` before implementing this story.

## Goal

Choose a production mission's tier from campaign authority—patron, target, and
player standing—then persist that chosen tier through the briefing and battle
handoff instead of deriving it from risk.

## Acceptance

- Production-generated and contract missions explicitly carry their selected
  tier through every mission boundary.
- Offer policy never produces a type below its tier floor.
- Risk remains a separately derived within-tier pressure signal.
- Debug and authored event/story missions retain explicit, explainable tier
  choices rather than inheriting production policy accidentally.

## Out of scope

Changing the tier curve itself or removing compatibility paths that still serve
headless callers.
