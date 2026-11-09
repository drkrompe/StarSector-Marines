# Fleet facilities: the company without a company ship

Status: ACTIVE — direction. The fleet's receipts, neutral start, Engineering's standalone tree, the mock rooms and their lift are implemented; dropping the ship choice is not.
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
reveals a deck room; any other purchase is confirmed in place. Until the mock
rooms exist every purchase is confirmed in place. The tree cannot be closed onto
a deck; its back button returns to headquarters. The cards still carry the
Second Wind's lore and Castle's portrait, which a later pass should rewrite for
a fleet.

Every track starts at its neutral band, the figures missions and evidence are
tuned at, and sells only what lies past it, priced as the Second Wind's tiers
are. There is no Deficit to buy back, because the Deficit is her story. For most
tracks neutral is Restored. A reserved room (range, gym, sick bay, lounge) and
the reserve hangar are neutral while sealed, so they start shut and their
opening is bought (`FleetFacilities`, `RefitTrack.fleetStart`). Receipts are
the fleet's, saved under no hull, and the launch reads them for every company
not on the Second Wind start. The fabrication core stands restored and has no
fleet orders yet, so tiers that need Fabrication I wait on them.

## Mock rooms

The Mech Lab, the boat bay and the barracks are cropped from one **standard
deck** (`CompanyShip.fleet`, `DeckSizing.fleetPlan`): a small fixed program owed
to no hull, holding only what the fleet has bought and the few rooms a company
needs around them (bridge, plant, stores, machine shop, armory, crew quarters,
mess). No screen shows it whole; Engineering draws no deck at all, and each room
screen frames its own compartment. The deck is laid again when an upgrade
changes a room. Counts come straight from the tier, so lift and fabrication
limits stay exact. Three tracks exist on the fleet's tree alone
(`RefitTrack.fleetOnly`), priced as the Second Wind's matching yard orders:

| Track | Neutral start | Restored | Improved I | Improved II |
|---|---|---|---|---|
| Mech gantries (Fabrication) | no Mech Lab | 1 gantry (5,000) | 2 (5,000) | 4, a full lance (10,000) |
| Boat berths (Structure) | 1 berth | 1 berth | 2 (8,000) | 3 (12,000) |
| Barracks (Structure) | 2 bunkrooms | 2 bunkrooms | 4 (4,000) | 6 (8,000) |

- **Mech Lab.** The four-pad lance bay appears with the first gantry; with none
  bought the facility is absent, not empty. Unbought pads stand idle: closed to
  work, never dressed as wrecks (owner, 2026-11-09: the first gantry is itself
  an upgrade).
- **Boat bay.** Each working berth is its own bay and one boat of lift; the
  company's boats are kept under the fleet's id rather than a hull's.
- **Barracks.** Nine billets per bunkroom; billets with commanders bound the
  company's size (owner, 2026-11-09), though nothing yet caps the roster by
  billets.

## Open questions

- What happens to an existing save whose company lives aboard a generated
  deck: keep its deck until it next moves, or convert it to fleet facilities
  at Restored with its working counts carried over.
- Whether ambient crew life (`ship-interiors-nouns.md`, Ambient life) runs in
  the mock rooms, and what stands beyond their doors.
- Retiring deck generation (`DeckSizing`, packing by outline) for company
  homes once no company lives on a generated deck; prize decks still use it.
