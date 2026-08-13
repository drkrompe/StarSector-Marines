# G8 — defector asylum

**Status:** SLICE 1 COMPLETE — discovered-chain producer next (2026-08-12)

**Implemented:** `94a3f2f1`

## Purpose

The second black-swan archetype is a political defector asking the company for
asylum after exposing a discovered active house plot. It deliberately does not
ship another battle: this is the first event whose pressure comes from keeping a
promise over time. The player may pay to shelter the defector, then later keep
their word or explicitly sell them back when the plotting house makes an offer.

This is the moral compass's first honest integrity source. Refusing before any
promise is not betrayal. Ignoring the later offer preserves the status quo and
therefore protects the defector; only an explicit handover breaks the promise.

## Source and cadence

- A source must be an `ACTIVE` political chain with a persisted discovery day,
  distinct valid actor/target houses, and a valid market.
- A chain may source at most one defector event. The event snapshots source
  chain, actor house, target house, and market so later chain/house changes do
  not rewrite its history.
- Starting on day 60, one deterministic 60-day epoch chooses at most one
  eligible chain by stable chain-id/epoch score. Array order is never a
  tiebreaker; chain id is.
- Discovery must be at least five days old. This keeps the defector from
  materializing on the same tick as the first rumor.
- V1 allows only one open black-swan event across all archetypes. The common
  open-event query gates both defector and civilian-rescue producers. Terminal
  history never blocks a later event.

## Frozen terms

The source chain's persisted tier becomes `tier = unsigned(chainTier) + 1` at
creation. Freeze, rather than later recompute:

- asylum cost: `10 × tier` supplies and `5 × tier` fuel;
- initial choice deadline: creation day + 3;
- custody interval: 10 days after the asylum decision;
- buyout choice deadline: offer day + 3; and
- betrayal offer: `20,000 × tier` credits, hidden until the buyout appears.

The initial surface shows the asylum costs, source/target houses, market, and
deadline. It does not preview the later offer, moral meaning, or chain effects.

## Lifecycle

1. `PENDING_CHOICE`: **Grant asylum** atomically consumes the frozen supplies
   and fuel and enters `COMMITTED`; **Refuse** enters terminal `REFUSED`.
   Passive expiry enters `EXPIRED` and remains morally neutral.
2. `COMMITTED`: the defector remains under protection. On decision day + 10,
   the plotting house's buyout appears and the event enters
   `PENDING_FOLLOWUP`.
3. `PENDING_FOLLOWUP`: **Keep your word** resolves `PROTECTED`; **Hand them
   over** atomically grants the frozen credits and resolves `BETRAYED`.
4. Passing the follow-up deadline resolves `PROTECTED`. Silence is continued
   asylum, never an invented betrayal.

`PENDING_FOLLOWUP` appends to `CampaignEventState`; existing ordinals never
move. A type-specific append-only `DefectorAsylumOutcome` owns `NONE`,
`PROTECTED`, and `BETRAYED`. A resolved row without a non-`NONE` outcome fails
closed in presentation and consequence systems.

## Persistence

Append common event columns for source chain, actor house, target house,
follow-up day/deadline, frozen credit offer, and type-specific outcome. Legacy
saves backfill ids/ticks to `-1`, credits to `0`, and outcome to `NONE`.

`eventState` is the transition authority. First/follow-up decision ticks and
the terminal outcome are written in the same mutation that consumes or grants
resources. Repeated clicks, daily ticks, save/load, and reconstructed intel
cannot charge, pay, resolve, or record consequences twice.

## Consequences

Consequences use the frozen source identities; no generic contract payment or
completion counters are involved.

- `REFUSED`: mercy `-5`; integrity unchanged because no promise was made.
- `PROTECTED`: integrity `+20`, stewardship `+10`. If the source chain is still
  active, reduce its progress by 20 without crossing below zero. Add +5
  reputation with the threatened target house.
- `BETRAYED`: integrity `-25`, stewardship `-10`. If the source chain is still
  active, add 20 progress without resolving it inside the event system. Add +5
  reputation with the actor house and -10 with the threatened target house.
- `EXPIRED`: neutral.

Political progress/reputation changes and moral rows apply exactly once after a
valid terminal outcome. The shared chain system remains the sole owner of chain
resolution on a later daily tick. A chain that already ended receives no
progress mutation, but the player's promise and reputation consequences remain
historical facts.

## Presentation and debug

- A global **Encrypted Channel** intel entry presents the active initial or
  follow-up choice and reconstructs from campaign state on load.
- The first choice says the defector carries evidence about the snapshotted
  actor's move against the target at the market. It makes no omniscient claim
  beyond the already discovered chain.
- The follow-up reveals the exact credit offer and makes the promise explicit.
- Terminal copy reports refuge, handover, refusal, or lost contact. It never
  names axes or numerical moral effects.
- Debug intel can force one production-shaped event from an eligible discovered
  chain and advance it to the follow-up choice. There is no debug mission-picker
  entry because this archetype has no battle.

## Slices

1. ~~**Persistent lifecycle authority** — append identities/state/outcome and
   event columns; backfill legacy saves; implement pure prepare/commit/refuse/
   follow-up/protect/betray transitions and replay tests.~~ Shipped in
   `94a3f2f1`; focused defector, event-column, lifecycle, and rescue-regression
   tests pass.
2. **Discovered-chain producer** — deterministic epoch selection, source
   snapshots, common open-event gating, system ordering, tests.
3. **Encrypted Channel choices** — resource/credit adapters, reconstruction on
   load, two-stage copy and input routing.
4. **World reaction and closure** — chain/reputation consequences, moral source
   rows, terminal dispatch, debug reachability, full save/replay matrix.

## Acceptance

- No undiscovered, terminal, malformed, or already-used chain can source the
  event; array iteration order cannot change the chosen chain.
- Initial expiry is neutral. Follow-up expiry protects the defector.
- Asylum charges once; betrayal pays once; a player cannot reach both terminal
  outcomes or own contradictory outcome data through public policy methods.
- Integrity changes only after a promise is kept or broken.
- Existing civilian-rescue saves and event ordinals remain valid.
- No surface previews the buyout, chain arithmetic, reputation deltas, hidden
  axes, thresholds, or captain-trait consequences.

## Non-goals

- No defector battle, named recruit, captain disagreement, unique item, or
  interrogation minigame.
- No direct chain resolution from the event system.
- No runtime faction/house creation or resurrection.
- No general event scripting DSL; generalize only the shared open-event query
  and appended persistence required by this concrete second consumer.
