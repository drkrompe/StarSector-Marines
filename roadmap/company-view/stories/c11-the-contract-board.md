# C11 — The contract board: seeing work you are not standing on

> Every offer in the sector already exists. The player can see one of them
> at a time, and only while docked at the market that minted it.

Status: PLANNED
Written: 2026-08-22
Updated: 2026-08-23 — migrated under `company-view-nouns.md`.

Depends on the shipped campaign-map home in `company-view-nouns.md`.
Independent of the other open stories in this track.

Read `company-view-nouns.md` before changing this story.

## Problem

`ContractGenerator.tick` walks **every house in the sector**, every day. It
gates on rank, status, and `ContractEligibility.patronEligible` — and on
nothing about where the player is. Offers are minted straight into the
contracts table with `contractMarketId` set to the patron's own market, the
code's own comment calling it "the patron's market is the meeting/origin."

The model already says offers exist sector-wide. Two lines in
`MissionGenerator.generateFromContracts` are what the player experiences
instead:

```java
if (state.contractPatronHouseId[i] != client.patronHouseId) continue;
if (state.contractMarketId[i] != pickupSlot) continue;   // the docked market
```

Patron you happen to be talking to, **and** planet you happen to be standing
on. That is not a visibility rule anyone designed. It is the shape of the only
surface that was ever built — a patron client inside a planet interaction — and
the filter exists because that surface had no way to express anything else.

The result is a company that finds work by flying somewhere and asking. Which
is a fine *early-game* fiction, and a bad permanent one: it makes the sector
feel empty in proportion to how much work is actually in it.

### The caps are the real constraint

Raising visibility without raising supply produces an empty board. Today:

| Knob | Value | Consequence |
| --- | --- | --- |
| `GLOBAL_OFFER_CAP` | 20 | The whole sector holds twenty live offers |
| `PER_PATRON_OFFER_CAP` | 1 | A patron never shows you a choice |
| `OFFER_CHANCE_PER_DAY` | 0.05 | ~1 roll per patron per 20 days |

A sector-wide board over that tuning is a twenty-row list in which comparing
two jobs from the same employer is **impossible by construction**. The filters
this story builds have nothing to bite on until the supply moves. So the tuning
change is not a follow-up here; it is slice 1.

That said, twenty rows also settles the layout question cheaply: at this scale
`ScrollRegionWidget` is sufficient and pagination is over-engineering.
The standing presentation law fixes the ladder: compact, then paginate, and
always state the off-screen count. Pagination is the named escalation, not the
opening move.

## Goal

A board that answers **"where should the company go next?"** from anywhere in
the sector, and that makes the answer depend on the company's standing rather
than on its flight path.

The organizing test in `company-view-nouns.md` applies here too: what decision does each element
inform, and can it be made here? A board that lists work the player cannot
evaluate, cannot reach, and cannot man is a travel-planning tool pretending to
be a contract screen.

## Design

### Range and resolution are separate axes

The tempting design is a visibility toggle: offers are hidden, then they are
not. That produces one flat moment and no texture. Distance instead governs
**how much resolution** an offer has, not whether it exists.

| Tier | Reach | What a row shows | Accept |
| --- | --- | --- | --- |
| **Docked** | The market you are at | Everything — terms, target, deadline, the patron's briefing | Yes |
| **In system** | Every market in the current system | Everything | No — dock first |
| **Feed** | Sector-wide, earned | Patron, type, patron tier, payout band, system, days left | No |

Long range tells the player **where to fly**. Short range tells them **whether
to take it**. Never hide that work exists — hide its detail. Hidden existence
reads as the game withholding; hidden detail reads as distance, which is what
it is.

The in-system rung earns its place by deleting tedium rather than granting
power: being in the system is already a travel commitment, and the reward is
not having to dock at all five planets to comparison-shop.

### Acceptance stays local at every tier

This is the load-bearing constraint, and it is the one worth defending when the
board later feels like it should just have an Accept button.

G32, recorded in `contracts-nouns.md`, established remote action — its **Deploy
Now** opens Marine Ops against a contract's own market from deep space. But
what it lets the player do remotely is *respond to an obligation they already
signed*. Taking on new work is the opposite direction. If a contract can be
signed from anywhere:

- travel stops being a cost, so distance stops being a filter dimension worth
  having, which removes the board's own most interesting column;
- the patron machinery degrades. `PatronBriefingComposer`,
  `PatronBriefingContextComposer`, `PatronChronicleMemory`,
  `PatronEngagementMemory`, `PatronLocalEchoComposer`, `PatronMemoryComposer`
  and the voice types under them exist so employers read as characters with
  history. All of that is attached to the briefing, which is attached to
  arriving. A board with remote accept routes around every one of them and
  leaves rows in a spreadsheet.

So the board's terminal action is **Set course**, not **Accept**.

### What earns the feed: MRB standing, then money — twice

*Settled 2026-08-22.* The sector-wide feed is gated in three parts, and each
part fails differently:

1. **MRB reputation qualifies you.** Below the bar the feed cannot be bought at
   any price. This is the part that is *earned* rather than paid.
2. **A one-time activation fee turns it on.** A real capital outlay, not a
   rounding error — the moment the player commits to being a company that buys
   information.
3. **A monthly maintenance fee keeps it on**, and the player can cancel it
   from this screen at any time.

The fiction is already written. `themes.md` casts the Mercenary Review Board as
the neutral broker that "aggregates contract history, flags breaches, and rates
the company's reliability" — an organisation that already holds every
contract in the sector is the obvious thing to subscribe to, and being
*allowed* to subscribe is exactly what a credibility score should buy.

Why the fee is split rather than a single number:

- **`playerMrbRep` starts doing something the player can feel.** Today MRB rep
  gates which patrons will offer work
  (the shipped eligibility gate recorded in `contracts-nouns.md`) — a gate on
  offers the player never sees, so the number moves and nothing observable
  changes.
- **The maintenance fee lands on the standing pane's headline number.**
  It is upkeep, so it shortens the runway the standing pane leads with. An
  information advantage that costs runway is a trade rather than a reward.
- **Cancelling is cheap and re-activating is not.** A company in the DESPERATE
  band can drop the feed to buy weeks of payroll — and goes blind and local
  again, right when it most needs to find work. Because switching back on costs
  the activation fee a second time, cancelling is a decision with a cost rather
  than a toggle flipped every lean month. That asymmetry is the whole point:
  it is what stops the optimal play from being "subscribe only in the week you
  are shopping."

The three parts are separable and the story can ship them together or in order;
what they buy jointly is a standing the company can lose **three** ways — fall
below the bar, fail to pay, or cancel and not be able to afford the way back.

### Filters and relevance

Filter dimensions, all read straight off the contracts table or derivable:
**patron / faction**, **contract type**, **payout band**, **distance in
jumps**, **days remaining**.

Default sort is relevance, and relevance is a **visible composite, never a
black box** — the player must be able to see why a row is at the top:

- distance in jumps,
- payout against the company's monthly upkeep (the runway currency the standing
  pane already speaks),
- **can the company actually field it** — required drops checked against
  the shipped `CompanyStanding.available` read model.

That last one does work no player can do by eye. A board that dims jobs the
company cannot man today, and says why, converts a list into advice. It also
connects the two panes: the reason a row is dim is a personnel fact the company
pane explains.

**The patron column is first-class and never collapses.** The filters exist to
narrow the list, not to reduce employers to a payout number; the moment the
board reads as a spreadsheet, the entire patron-voice investment is bypassed.

## Sequencing

Depends only on the shipped ability and planet-free `ScreenId.COMPANY_HQ`
host. It shares the standing pane's derived rollups, including
`CompanyStanding.available`.

**Against the shipped clock.** The clock pane and the board split the deadline
sources between them, and the split is by *valence*:

- An obligation **bites**. A missed response deadline costs reputation and
  loses the garrison; a stationing term ending changes the company's income.
  Those belong to the shipped clock.
- An opportunity **lapses**. A missed offer costs nothing — the player simply
  does not get the job. That is this board.

So `contractOfferExpiresTick` belongs here, while the clock renders only
obligations. The boundary follows consequence rather than layout preference.

The one case that would genuinely earn an offer a slot on the clock pane is an
offer the player has **decided on** and is flying toward — a watchlist. Not
modelled, and deliberately not built here; see Open questions.

**Against the contracts track.** `contracts-nouns.md` owns the
*rules* — what MRB standing buys, what the feed costs, offer supply. This story
owns the *surface*. The tuning slice below is a contracts-track change made
from here because the board is what makes it observable; it should be reviewed
against `contracts-nouns.md` and `contracts-live-acceptance.md` before it lands.

## Slices

1. **Supply.** Raise `GLOBAL_OFFER_CAP` and `PER_PATRON_OFFER_CAP` so a patron
   can hold more than one open offer and the sector carries a board's worth of
   work. Pure tuning, no UI. Verify the contracts table and
   `ContractTableCompactor` behave at the new ceiling, and that offer lapse
   still clears rows at the higher rate.
2. **The board, docked and in-system.** The pane, `ScrollRegionWidget`, the
   row, the filters, relevance sort, and the two nearest reach tiers. No feed,
   no fees — this rung is available to everyone from the first hour and is
   already a strict improvement on flying to five planets.
3. **The feed.** MRB threshold, activation fee, monthly maintenance as upkeep,
   subscribe/cancel on this screen, and the reduced-resolution row. Cancelling
   is immediate; re-activating costs the activation fee again.
4. **Set course.** The row's terminal action targets the patron's market, so
   the board closes its own loop rather than leaving the player to find the
   system on the map.

Slices 2–4 are independently valuable. Slice 1 gates the usefulness of all of
them but not their correctness.

## Acceptance

- Every offer the generator has minted is reachable through some tier — no
  offer is permanently invisible at any standing.
- A row at feed resolution states its patron, type, tier, payout band, system,
  and days remaining, and **does not** state terms, target, or briefing text.
- Docking at the offer's market upgrades that row to full detail in place.
- **No tier offers Accept.** The terminal action is Set course.
- Relevance sort is explainable: the player can see distance, payout against
  upkeep, and fieldability on the row that sorted high.
- A job the company cannot man today is dimmed and says why — not hidden.
- Below the MRB bar, the feed cannot be activated at any credit balance, and
  the screen says which of the two is missing.
- Activation debits once; the maintenance fee appears in the standing pane's
  upkeep and shortens the stated runway.
- Cancelling takes effect immediately, stops the maintenance fee, and the board
  falls back to the in-system tier without an error state. Re-activating
  charges the activation fee again, and the screen says so **before** the
  player confirms the cancel.
- If the list is capped, the off-screen count is stated (design commitment 9).
- With no offers in reach, the board says which tier is active and what would
  widen it — never an empty box.
- The board writes no campaign state except the subscription fields.
- `gradlew.bat build` green.

## Automated verification

The reach/resolution rules and the relevance composite are pure and testable;
the pane ships on in-game smoke, the same gate as G5, G13, G32, and the
campaign-map home.

- Reach resolution: a fixture contracts table plus a player location, asserting
  which rows appear at each tier and at what resolution. Including that a
  feed-tier row carries no terms.
- The valence split: `contractOfferExpiresTick` rows appear on the board and
  **not** in `PlayerEventInbox`-derived clock rows.
- Relevance ordering, with deterministic tiebreaks and the empty case.
- Fieldability: a contract requiring more marines than
  `CompanyStanding.available` is marked unfieldable, and the marking follows
  the roster rather than a cached value.
- Subscription state machine: below-bar cannot activate; activation debits once
  and not per tick; cancel stops the maintenance charge on the next cadence and
  not retroactively; re-activation charges again.
- Fee arithmetic against the standing pane's runway — subscribing shortens it
  by exactly the maintenance fee.
- **Manual smoke (shipping gate):** the board from deep space, in system, and
  docked; activate, cancel, re-activate; layout at 1.0x / 1.25x / 1.5x UI
  scale; Set course landing on the right market; and the board at the raised
  offer cap rather than at three rows.

## Files touched

- `campaign/systems/ContractGenerator.java` — the caps (slice 1).
- `campaign/ContractBoard.java` — new; derived rows, reach resolution,
  relevance. Pure, no UI.
- `campaign/CampaignState.java` — the subscription flag and its paid-through
  day. The only new state in this story.
- A campaign system for the maintenance charge, on the existing monthly
  cadence — not a new clock.
- `ops/ContractBoardScreen.java` (or a pane on `CompanyHqScreen`) — new.
- `ops/CompanyHqScreen.java` — the route in.
- Read-only use of the contracts table, `CompanyStanding`, and the market
  registry.

## Out of scope

- **Accepting a contract remotely.** Argued above; the whole design leans on it.
- **Changing what patrons offer, or eligibility.** G21 owns that. Slice 1
  changes *how many* offers exist, not *which*.
- **Negotiation from the board.** Terms are a briefing-flow surface.
- **A vanilla intel-tab entry.** G32 established the mod's intel plugins are
  load-time singletons that only notify at creation; a board there would be
  pull-only.
- **Replacing the patron client.** The docked patron list stays exactly as it
  is — it is the bottom rung of this same ladder, and the board is an
  additional door.
- **Colony infrastructure as the feed's home.** `economy.md` floats an
  Administrative Office brokering contracts; if colonies land, the facility
  becomes a second way to hold the feed. Not a dependency.

## Open questions

- **Does the feed's resolution vary by MRB standing, or only its existence?**
  A ladder where higher credibility buys *more detail* at range would give the
  reputation track a long tail instead of one threshold. It also risks turning
  one clean rule into a matrix the player has to learn.
- **Is a watchlist the right home for "I have decided on this one"?** Marking an
  offer would earn it a clock-pane row with its lapse countdown, closing the gap
  this story opens by moving offers off that pane. Cheap to build, and it adds a
  second place the same row can live — which is the thing this split was trying
  to avoid.
- **Should the fees scale with the company?** Flat numbers are punishing early
  and free late; scaling maintenance with headcount or contract volume keeps it
  a live decision, at the cost of a number the player has to model. The
  activation fee has the opposite pull — a flat one-time cost reads as a
  threshold the company crosses once, and scaling it would blunt that.
- **Does cancelling refund the unused month?** Not refunding is simpler and
  makes the paid-through day meaningful; refunding avoids a player feeling
  punished for cancelling on the wrong day.
- **What does the board look like before the company has any MRB standing at
  all?** `early-operations` deliberately starts the player at a single
  Independent broker, and the board must not undercut that opening by listing
  the sector on day one. The in-system tier may need its own small gate, or the
  opening may simply be a system with one market worth talking to.
