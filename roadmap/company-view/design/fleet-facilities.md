# Fleet facilities: the company without a company ship

Status: DRAFT — direction. Nothing here is implemented.
Written: 2026-11-09

Players ask to run the company from their own ship rather than the Second Wind.
The generated deck (`company-ship.md`) can house a company on any destroyer or
larger, but its quality depends on the hull's outline, modded hulls vary
without limit, capacity is whatever the packing happened to fit, and the refit
tree only ever runs on the founding hull. This doc replaces that path for
sandbox play with something simpler: **outside the Second Wind there is no
company ship.**

It extends `company-view-nouns.md` (Engineering, Mech Lab, Boat Deck,
Barracks) and `second-wind-refit-tree.md` (bands, cores, tracks). It
supersedes `company-ship.md` and `company-ship-selection.md` for every company
that is not aboard the Second Wind.

## Two kinds of company home

| | Second Wind | Fleet facilities |
|---|---|---|
| Who has it | the Cerberus start | every other start |
| Where the rooms are | her drawn, pinned deck | standalone mock rooms, no hull around them |
| Engineering | the systems tree over her live deck | the same tree on its own |
| Starting band | Deficit, with her story gates | Restored |
| Upgrades belong to | her (`ship-interiors-nouns.md`) | the fleet |

The Second Wind is unchanged (owner, 2026-11-09).

## Fleet facilities

A sandbox company's facilities belong to **the fleet**: somewhere in it is a
ship that runs the Mech Lab, the boat bay and the barracks, and the player
never has to say which. There is nothing to designate, transfer, refit into or
lose (owner, 2026-11-09).

- **No designation, no transfer screen**, no refusal of a hull too small to
  live aboard, no refit fee for moving.
- **No loss of home.** Losing a ship costs what the base game says it costs;
  there is no sinking toll on the company's marines, spares or holds.
- **No presence gate.** The company screens open wherever Marine Operations
  does. A Starsector player with no ships has already lost the game, so the
  company never needs a check of its own (owner, 2026-11-09).
- **No reading of the fleet for capacity.** Billets, gantries and berths come
  from the facilities' upgrades and from commanders alone, never from the
  fleet's crew, cargo or hull sizes (owner, 2026-11-09: "more headache than
  it's worth"). Hull mods and d-mods change nothing in the facilities.
- **Upgrades stay with the fleet.** Nothing is attached to a hull, so selling,
  losing or replacing ships never takes an upgrade away.

## Engineering is the tree alone

The systems tree (`second-wind-refit-tree.md`) is already a modal over the
deck. For fleet facilities it stands on its own, with no deck behind it; the
`ShipBackdrop` starfield may stay as set dressing. Cores, tracks, tiers,
requirements, prices and cards read the same. A purchase whose
track owns a mock room reveals that room changed, the way `RefitReveal`
reveals a deck room; any other purchase is confirmed in place.

Every track starts at **Restored**, the band missions and evidence are tuned
at, and sells only its Improved tiers, priced as the Second Wind's are. There
is no Deficit to buy back, because the Deficit is her story.

## Mock rooms

The Mech Lab, the boat bay and the barracks are **authored rooms in fixed
sizes**, drawn with the same room vocabulary and renderer as every facility,
seen alone, with no ship outline around them. The upgrade tier picks the
size; no hull shape ever has to fit them. Counts come straight from the tier,
so lift, fabrication and billet limits stay exact.

- **Mech Lab.** Grows by gantries: one, two, then four, modelled on the
  Second Wind's four-gantry lab. The first gantry is itself an upgrade
  (owner, 2026-11-09), the cheapest node on its track, rather than a story
  gate like Take Back the Depot.
- **Boat bay.** Grows by berths; working berths are the company's lift.
- **Barracks.** Grows by bunkrooms; billets with commanders bound the
  company's size (owner, 2026-11-09).

The tier steps, the starting fit and the prices are open; the next section
lists them.

## Open questions

- The tier steps for each room, and which tiers need which cores.
- Whether a new sandbox company starts with one boat berth working, or must
  buy it as it buys its first gantry.
- What happens to an existing save whose company lives aboard a generated
  deck: keep its deck until it next moves, or convert it to fleet facilities
  at Restored with its working counts carried over.
- Whether ambient crew life (`ship-interiors-nouns.md`, Ambient life) runs in
  the mock rooms, and what stands beyond their doors.
- Retiring deck generation (`DeckSizing`, packing by outline) for company
  homes once no company lives on a generated deck; prize decks still use it.
