# Campaign backend

Status: IN PROGRESS — saved data, the company fleet, sector scripts and the docked port moved; markets, news and world basics remain

Written: 2026-11-02

Read `campaign-nouns.md`, Ownership. The tool it serves is in `build-and-run.md`.

## Scope

Put what the mod's screens and rules ask of the running campaign behind
`CampaignBackend`, so a stand-in can answer them and `playBattle` can host the
company's screens with no game booted, including an infinite economy. The shipped
mod keeps the game's answers. This is not a wrapper around the game's sector: a
family answers in the mod's own terms where a few files ask, because returning a
game type makes a stand-in implement the game's interfaces. The company fleet is the
exception: 45 files read its members and cargo, so it stays `CampaignFleetAPI` and a
stand-in answers with proxies, as the tests already do.

## Families

Each moves whole, in its own commit, when the next screen in `playBattle` needs it,
and each gets a pass in the real game: much of this code has no headless coverage.

| Family | Reads today | Status |
| --- | --- | --- |
| Saved data | `getPersistentData` | Moved, but for four files that hold a sector of their own |
| Company fleet, with its purse and cargo | `getPlayerFleet`, then its members and cargo | Moved, but for six files that hold a sector or are a rule command |
| Sector scripts | `getScripts`: the roster, campaign state and event presenter | Moved |
| Docked port | `CivilizedPort.docked`: the store, and buying in the rooms | Moved |
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
