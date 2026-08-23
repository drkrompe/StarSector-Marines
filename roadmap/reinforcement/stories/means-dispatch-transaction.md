# Reinforcement Means Dispatch Transaction

Status: PLANNED — concrete cohesion cleanup.

Written: 2026-08-23

Read `reinforcement-nouns.md` before implementing this story.

## Problem

`canFulfill` and `dispatch` are separate, but dispatch returns no result. If
live state invalidates a destination between those calls, a means may abort
after the request and ticket have already been committed, preventing orderly
fall-through to the next means. Later target timeout can recover tactical work,
but it is not an atomic delivery contract.

## Goal

Make means fulfillment report an explicit committed, retryable, or rejected
outcome so the dispatcher owns ticket and fallback semantics at one boundary.

## Acceptance

- A successful outcome means the delivery actor or squad exists and owns the
  request objective.
- A pre-commit rejection may fall through to the next means without double
  spending.
- A transient retry keeps the request and its payment state coherent.
- Prepaid counterattack failure preserves its stated sunk-reserve law.
- Existing convoy, shuttle, and walk-in behavior is unchanged on success.

## Out of scope

- Rewriting destination scoring or delivery lifecycles.
- Solving every later in-flight destruction case inside dispatch.
- Changing means priority or reinforcement balance.
