# Campaign architecture

Status: ACTIVE

Written: 2026-08-23

## Purpose

Campaign architecture keeps long-lived campaign facts coherent across saves,
daily simulation, UI reconstruction, and external game integration. It is a
runtime contract, not a second policy model for houses, contracts, or events.

## Persistent state and identity

Campaign state is save-persisted data; autonomous behavior is transient and is
rebuilt after load. Persistent campaign domains use compact, data-oriented
tables and stable domain identities. A caller may retain an identity, never an
implementation row position.

Most politically historical records remain as terminal or dormant rows so
their identity and attribution survive. Owner-approved compaction is allowed
only where the owning domain proves that references use stable identities and
that live obligations cannot be discarded. Contract maintenance is the current
example. Compaction is not a general shortcut for erasing history or bypassing
release and recovery obligations.

Registries and lookup indexes exist to resolve stable identity efficiently.
They do not prohibit deliberate table scans: scans are valid for bounded,
order-sensitive, or correctness-first work when a measured need has not
justified another index.

## Autonomous execution

A **campaign system** is stateless, day-driven behavior over persistent state.
It declares the domains it reads and writes, and the framework executes the
registered systems serially in a behaviorally significant order. Declarations
make dependencies reviewable; they do not grant permission to assume parallel
execution or to reorder policies casually.

Interactive input, mission resolution, and presentation belong to their owning
feature boundaries. They do not become autonomous systems merely because they
touch campaign state. `campaign-framework-nouns.md` owns time and system-order
semantics.

## External-state boundary

Campaign policy may settle explicit company-owned resources through its
authorised adapters: for example, accepted terms, retainers, recovery, or an
event commitment can affect player cargo or credits. Such writes must be
atomic, attributable, and replay-safe.

Only `t3-endgame-nouns.md` may change vanilla market ownership, faction identity,
or faction diplomacy. Other campaign domains may prepare or observe the facts
that T3 needs, but cannot perform that external political transition.

## Extension laws

- Give every persisted fact a stable identity and an owner before exposing it
  to other campaign features.
- Preserve load reconstruction and replay safety before adding convenience UI
  or background automation.
- Add an index when a demonstrated access pattern needs one; do not replace a
  clear bounded scan with speculative data structure complexity.
- Treat system ordering and external writes as behavior, not implementation
  detail. Change them only with an explicit consumer/producer contract.
