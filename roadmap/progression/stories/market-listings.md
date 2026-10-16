# Market listings

> A story beat can put a named card on sale at a named market, and the card
> stays on the shelf until the company buys it.

Status: PLANNED — first of two; `arc-issue-cards.md` is its first consumer.
Written: 2026-10-15

Read `progression-nouns.md` (Kit and armory: the four acquisition channels and
the Restricted tier) and `campaign-kit-arc.md` before implementing.

## Problem

The only way a story can hand the company kit today is to put a card straight
into fleet cargo, as the Agreus settlements do with the recovered Specter and
Arbalest. A story cannot say "this is now for sale here". A card placed in a
market's own cargo does not survive. `FactionEquipmentMarketStock` clears every
loadout card from an open market on each vanilla restock (about monthly) and on
every game load, and refills it from the faction pools.

So every authored reward is either free or absent. There is no way to make a
reward something to go and buy, and no way for a place to become known for what
it sells. Thunder Run's employer cannot start stocking escort issue.

## Scope

**A market listing**, a new progression noun. It is a named card offered at one
named market (or at every open market of one faction) because a story beat said
so. A listing is not a channel and not a reward:

- it does not teach anything, and it is not the card itself;
- it bypasses the reputation and victory thresholds, because the story is the
  permission;
- it does **not** bypass the Restricted tier. A listing for Restricted kit is
  refused unless the company holds a military warrant naming it, since a listing
  must never be a back door around a warrant.

Each listing has:

- a card id;
- a scope, either a market id or a faction id;
- the event that created it;
- the day it was created.

It persists in `CampaignState` in the interned-id shape the released-kit and
military-warrant tables use. A save from before listings restores empty.

**The planner reads listings first.** `FactionEquipmentMarketStockPlanner`
places every live listing for the market on its own shelf before it draws the
rotating stock. Listing slots are additional: they never displace the monthly
market or licence slots. A listed card is left out of the random draw for that
market, so it never appears twice.

**Lifetime.** A listing is withdrawn once the company knows the card or
carries it, using the same "unavailable" set the planner already reads. That
covers buying it. If the company sells the card back before learning it, the
listing returns at the next restock, which is the right answer: the shelf still
has it. Nothing else expires a listing, so it is permanent until satisfied.

**Freshness.** Creating a listing refreshes that market's stock immediately,
the way `FactionEquipmentMarketStock.refreshAllMarkets` seeds existing saves.
The player should not wait a month to see the reward the debrief named.

**Authoring.** Story settlement creates a listing with one call:
`MarketListings.list(state, cardId, scope, reason, day)`. It is idempotent per
(card, scope). Settlement code stays responsible for being once-only, as the
Agreus settlements already are.

**Legibility.** A listed card's tooltip says why it is there, with one line
naming the event, for example *"Stocked after the Thunder Run convoy."* The
debrief that creates a listing names the market, so the player knows where to
go. The market's own cargo screen is otherwise unchanged.

## Out of scope

- Which cards are listed and where: `arc-issue-cards.md`.
- Price changes or discounts for listed cards. Listed cards price like every
  other card. A discount would need its own story.
- Listings in submarkets other than the open market.

## Acceptance

- A unit test on the planner: a live listing appears at its market alongside
  the unchanged rotating stock, in its own slot. It is not drawn a second time.
  It is withdrawn once the card is unavailable, and returns if the card becomes
  available again.
- A unit test that a faction-scoped listing appears at every open market of
  that faction and nowhere else.
- A unit test that listing a Restricted card without the issuer's military
  warrant is refused, and with the warrant is accepted.
- A unit test on persistence: a listing survives the `CampaignState`
  read-resolve path, and a pre-listing save restores empty.
- Every new test completes in under one second (CLAUDE.md).

## Plan

1. `CampaignState` columns and `MarketListings` (list, live listings for a
   market, reason).
2. The planner's listing shelf, plus the immediate refresh on creation.
3. The tooltip reason line.
4. Fold into `progression-nouns.md` beside the four channels. Listings are not
   a fifth channel; they are authored stock that the market channel displays.
