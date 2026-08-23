# Reinforcement Status Readout

Status: PLANNED

Written: 2026-08-23

Read `reinforcement-nouns.md` before implementing this story.

## Goal

Give the player a concise view of their authoritative reinforcement capacity
and already-committed inbound force.

## Contract

- Ticket balance comes from `BattleResources`; the UI owns no shadow budget.
- Inbound counts only dispatched actors that have not completed or failed their
  delivery lifecycle.
- Supply loss and prepaid counterattack commitments remain distinguishable from
  ordinary available tickets.
- Enemy reinforcement remains world-readable unless a separate fog/intel story
  establishes another rule.

## Acceptance

The readout changes with production, spending, supply capture, dispatch,
arrival, and delivery failure without polling implementation-private lists or
inventing a second state machine.

## Out of scope

- A full reinforcement control panel or manual dispatch.
- Enemy omniscience through fog of war.
- Rebalancing ticket production.
