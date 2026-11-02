# Campaign backend

Status: IN PROGRESS — saved data moved; company fleet, purse, markets, news and world basics remain

Written: 2026-11-02

Read `campaign-nouns.md`, Ownership. The tool it serves is in `build-and-run.md`.

## Scope

Put what the mod's screens and rules ask of the running campaign behind
`CampaignBackend`, so a stand-in can answer them and `playBattle` can host the
company's screens with no game booted, including an infinite economy. The shipped
mod keeps the game's answers. This is not a wrapper around the game's sector: where
it can, a family answers in the mod's own terms, because returning `FleetMemberAPI`
or `MarketAPI` would only make a stand-in implement the game's interfaces.

## Families

Each moves whole, in its own commit, when the next screen in `playBattle` needs it,
and each gets a pass in the real game: much of this code has no headless coverage.

| Family | Reads today | Status |
| --- | --- | --- |
| Saved data | `getPersistentData` | Moved, but for four files that hold a sector of their own |
| Company fleet | `getPlayerFleet`, fleet members, flagship | Not started |
| Purse and cargo | credits, marines, cargo | Not started |
| Markets | `getEconomy`, `getMarket`, `getMarketsCopy` | Not started |
| News | intel, `getCampaignUI` messages | Not started |
| World basics | clock, factions | Not started |

## Laws

- Nothing reachable from a saved object keeps a backend in a field; it is read from
  `CampaignBackend` each time.
- `CampaignBackendBoundaryTest` fails on a moved family's call outside the seam. Its
  list of files still holding a sector only shrinks.
- `campaign/` code that really is driving the game (world setup, spawning) stays on
  the game's API. Only what a hosted screen reaches moves.
