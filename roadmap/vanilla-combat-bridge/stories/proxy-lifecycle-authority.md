# Proxy Lifecycle Authority

Status: PROPOSED — concrete cohesion cleanup.

Written: 2026-08-23

Read `vanilla-combat-bridge-nouns.md` before implementing this story.

## Problem

The proxy mirror says the simulation owns ground death, but it currently removes
a proxy whenever vanilla reports that proxy dead. If the simulation entity is
still alive, the structure silently loses vanilla targetability and the two
lifecycles disagree.

## Goal

Make every proxy exit resolve through one explicit simulation-owned lifecycle
policy.

## Acceptance

- A live sim entity cannot permanently lose its proxy because vanilla destroyed
  the disposable avatar.
- Vanilla damage is translated into the simulation before any proxy retirement
  decision.
- Proxy recreation, retention, or terminal translation is explicit and
  idempotent.
- A sim death removes its proxy exactly once and remains the authoritative kill.
- Initialization and engine callbacks cannot create duplicate links or death
  subscriptions.

## Out of scope

- Adding infantry proxies.
- Changing proxy target shapes or cross-scale balance.
- Implementing return fire from ground structures.
