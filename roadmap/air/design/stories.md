# Air — open stories

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-10-14 — ship escort built (lead-ship relation, lead-weighted threat picture and anti-air pick); the board row now names only the deferred support-hull no-bombardment rule.

| Story | State | Outcome |
| --- | --- | --- |
| `fighter-air-entities.md` | ACTIVE — the overlay class and the profile's dead tuning are gone; the package rename remains | Fighters are ordinary air entities flying ordinary sorties. Committed wings arrive on an `AirCorridor` from off the map and are dispatched by `AirCoverSystem`. |
| `air-manual-acceptance.md` | PLANNED | Verify centre-of-gravity rotation, hardpoint placement and per-mount sight, and thrust-weighted engine effects together in a live battle. |
| `warship-on-station.md` | PLANNED | A Paragon hovers over the battle and sweeps its laser lance across compounds, razing walls, roofs and units, friendly fire included. |
| `tender-ship.md` | ACTIVE — slice 1, the swarm choreography, pilot control and AI by stance built | The vanilla Shepherd as a mobile drone and construction ship: defence drones anchored to the hull and builder drones that work a site until a turret stands. Point defence by the drones' mining lasers is built. Player control through the direct-control seam is built. The wing launches, escorts, recalls and retreats as real flights. AI by stance is built: an unpiloted ATTACK or DEFEND tender lays its own sites. Open: the revetment build option, the story hand-out of a campaign Shepherd (a stated dependency; it will give the player one if they lack one), live acceptance. |
| Support-ship no-bombardment (DECIDED, deferred) | OPEN | Support hulls will not hunt ground (until a gunned support hull flies); class weights, carrier biases and escort are built (`warship-behaviours.md`, Classes, Decided and Decided: escort). Escort against hostile ships waits on ships as targets (G4). |
| Large planetary defences against ships; ships as targets (OPEN) | OPEN | The remaining rule changes behind the air-superiority half of `warship-behaviours.md` (gaps G4, G5). Ship weapons against aircraft and aircraft against ships shipped; the declared-hazard stand-in for ground threats is decided (Decision 2); the rest wait on owner direction. |

Future ship collision, wing composition, anti-air/survivability, modeled fighter
fire, and camera-Z work are extension paths in `air-nouns.md`, not contracted
stories yet.
