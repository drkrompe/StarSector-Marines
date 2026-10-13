# Air — open stories

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-10-13 — ship support direction and its first story added; ship class threat weights and carrier flux and standoff biases built; tender AI by stance and four turret build options built; warship posture, objective-aware fire, landing-denial intercept, missile flight, swarm bursts and ship-versus-air fire shipped; later missile items remain on `warship-missiles.md`.

| Story | State | Outcome |
| --- | --- | --- |
| `ship-on-station-power.md` | PLANNED — slice 1 of `ship-support.md` | One mechanism puts a ship over any battle: a committed fleet warship or Shepherd (or a stock or employer hull) is a Ship on Station command power, called to a cell; it enters from the company's edge, works under the mission stance and departs. Writeback, atmospheric arrival, hostile ship support and the low pass follow in `ship-support.md`. |
| `fighter-air-entities.md` | ACTIVE — the overlay class and the profile's dead tuning are gone; the package rename remains | Fighters are ordinary air entities flying ordinary sorties. Committed wings arrive on an `AirCorridor` from off the map and are dispatched by `AirCoverSystem`. |
| `air-manual-acceptance.md` | PLANNED | Verify centre-of-gravity rotation, hardpoint placement and per-mount sight, and thrust-weighted engine effects together in a live battle. |
| `warship-on-station.md` | PLANNED | A Paragon hovers over the battle and sweeps its laser lance across compounds, razing walls, roofs and units, friendly fire included. |
| `tender-ship.md` | ACTIVE — slice 1, the swarm choreography, pilot control and AI by stance built | The vanilla Shepherd as a mobile drone and construction ship: defence drones anchored to the hull and builder drones that work a site until a turret stands. Point defence by the drones' mining lasers is built. Player control through the direct-control seam is built. The wing launches, escorts, recalls and retreats as real flights. AI by stance is built: an unpiloted ATTACK or DEFEND tender lays its own sites. Open: the revetment build option, the story hand-out of a campaign Shepherd (a stated dependency; it will give the player one if they lack one), live acceptance. |
| Support-ship no-bombardment, shield-type threat weights and escort (DECIDED, deferred) | OPEN | Class threat weights and the carrier flux and standoff biases are built (`warship-behaviours.md`, Classes and Decided). Support hulls will not hunt ground (until a gunned support hull flies); shield type will weight threats and escort is wanted, both waiting on aircraft and ground defences hurting ships and ships as targets (G3 to G5). |
| Aircraft against ships; large planetary defences against ships; ships as targets (OPEN) | OPEN | The rule changes behind the air-superiority half of `warship-behaviours.md` (gaps G3 to G5). Ship weapons against aircraft shipped; the declared-hazard stand-in for ground threats is decided (Decision 2); the rest wait on owner direction. |

Future ship collision, wing composition, anti-air/survivability, modeled fighter
fire, and camera-Z work are extension paths in `air-nouns.md`, not contracted
stories yet.
