# System-scoped contract supply

Status: IN PROGRESS

Written: 2026-09-01

## Goal

Make persisted offers the sole authority for ordinary production work. Most
markets should have no open mission, while a populated system may hold a small
set of opportunities without every faction manufacturing a parallel board when
the player opens Marine Operations.

## Scope

- Retire the faction-direct industry mission list from the planet UI's ordinary
  production path. Preserve the industry-mission catalog as candidate content
  for `faction-ground-contract-policy.md`; do not delete or reinterpret it here.
- Keep `CampaignState` contract rows as the one persisted source of ordinary
  offers, including their patron, origin market, expiry, acceptance, and
  settlement authority.
- Add geographic supply ceilings to standard generation: at most one open offer
  per origin market and at most three across markets in one star system. Retain
  the existing per-patron and sector-wide ceilings.
- Derive ordinary faction clients from work that actually exists at the current
  market. A planet with no offer or authored mission shows an honest empty state
  instead of permanent owner, Independent, Pirate, and neighboring-faction rows.
- Preserve debug work, authored story missions, campaign events, recoveries,
  active stationing work, and in-progress Planetary Assault phases. Those have
  their own creation or commitment authority and must not disappear because
  ordinary supply is sparse.

## Constraints

- Scarcity is a ceiling, not a quota. A system may legitimately contain zero
  offers, and generation must not mint filler to maintain a minimum count.
- System membership is derived from the live sector through an injectable
  read-only topology seam. It is not a second persisted world graph.
- Existing saves keep their open rows. The new ceilings govern additional
  generation rather than deleting or rewriting offers already promised.
- No client, screen, or mission projection may invent an offer that campaign
  state does not own.

## Acceptance

- A second patron at a market cannot add a second open offer there.
- A fourth market in one system cannot add a fourth open offer while three are
  already open elsewhere in that system.
- Offers in different systems remain independent until the sector-wide cap is
  reached.
- An ordinary faction with no authored local mission is absent from the client
  list; a patron with a persisted offer remains visible.
- A market with no visible work presents an explicit no-contract state.
- Story, event, recovery, stationing, and in-progress multi-phase routes retain
  their established visibility and writeback.
- Focused tests and `gradlew.bat build` are green.
