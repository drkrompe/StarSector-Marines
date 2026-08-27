# Deck capacity and upgrades

Status: PROPOSED — gated on a named economic owner.

Written: 2026-08-26

Read `ship-interiors-nouns.md` before implementing this story. Depends on
`company-ship-deck-adoption.md`.

Make facility capacity spatial, then let a bounded upgrade transaction change the
room. This is the story the whole model exists for: buying bay space adds a
gantry the player can watch a technician work at, and berthing is the number of
beds actually on the deck.

## The gate

Ship interiors is not an economic authority. This story cannot start until an
owner is named for what an upgrade costs, where the money comes from, and how a
purchase is transacted — progression and campaign are the candidates. The
`backlog.md` entry *Personnel scale* anticipates exactly this: it defers a
capacity model until company growth has a concrete campaign pressure. A company ship
with a countable number of berths is that pressure. Resolve the ownership
question before contracting the work, and record the answer in the owning noun
doc rather than here.

## Scope

- Facility capacity published as a fact derived from counts of fixtures in
  service, per law 4, with personnel and mech authorities consuming it instead
  of their current fixed limits. A damaged ship therefore publishes less
  capacity without the compartment changing.
- An upgrade transaction that changes a facility's parameters, regenerates its
  compartment, and validates the resulting deck before committing.
- Hull-bounded growth: a deck has finite area, so expanding one facility must be
  paid for out of another. The trade is presented as a decision, not a blocked
  button with no explanation.

## Constraints

- An upgrade that does not change the space does not belong here. Non-spatial
  improvements are ordinary progression.
- Capacity has one owner. Do not introduce a roster limit that can disagree with
  the number of berths.
- A failed or invalid upgrade leaves the previous deck intact. Regenerate and
  validate before replacing.
- Nothing shipped has saves to preserve, so the persisted shape may change
  outright rather than migrate.

## Acceptance

- Roster and heavy-asset limits are read from facility capacity, and no fixed
  limit constant survives for either.
- A purchased upgrade visibly changes the compartment: more fixtures, larger
  extent, and ambient routes that scale with them.
- Expanding a facility past the available hull area forces an explicit trade
  against another facility, and the cost is legible before committing.
- The post-upgrade deck passes the same connectivity and deployability
  validation as a freshly generated one.
- An upgrade that would strand personnel or assets beyond the resulting capacity
  is refused with a stated reason, not silently truncated.

## Out of scope

New facility types, boarding, and the presentation surface for browsing and
buying upgrades. This story owns the transaction and its spatial consequence.
