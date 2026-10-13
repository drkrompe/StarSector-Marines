# Air — open stories

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-10-13 — battery fort and its aspects built (forts slices 1 and 2); shield fort built (slice 3).

| Story | State | Outcome |
| --- | --- | --- |
| `ground-defences.md` (direction) | ACTIVE — slices 1-5 built (signals, multi-cell structures, fortress battery, shield generator, defence nests); art, AI awareness and tuning outstanding | Ground defences as three things from the market: defence nests, fortress batteries with ship-grade guns whose envelope a ship must respect, and a shield generator whose dome soaks ship fire until overloaded; marines open the sky by taking fire control and generators. Gap G5. |
| `forts.md` (direction) | ACTIVE — slices 1-3 built (battery fort, aspects as dependencies, shield fort); redoubt/citadel, richer command awareness and art outstanding | Ground defences as authored forts: walled compound layouts whose aspects (guns, fire control, magazine, power, emitter, sensors) are shut down one by one, by ground forces or by overwhelming ship fire once the dome is down. |
| `fighter-air-entities.md` | ACTIVE — the overlay class and the profile's dead tuning are gone; the package rename remains | Fighters are ordinary air entities flying ordinary sorties. Committed wings arrive on an `AirCorridor` from off the map and are dispatched by `AirCoverSystem`. |
| Close Air Support gunship (board row; no story doc) | BUILT — live acceptance and tuning open | A committed Mudskipper is the source of a called gunship: it circles overhead of a seen cell and works it with the hull's own turret, then leaves (`air-nouns.md` Gunships; `command-powers-nouns.md`). Open: a live look at the hull scale and the turret under the game's camera and its sound; draft numbers (ring 7 cells, 40 s visit, one Heavy MG, 3 CP, 2 supplies, two activations, 25 s cooldown) were set without a balance run, and one visit clears a garrison of two squads of weak defenders in the evidence scene; no campaign writeback for a lost boat (law 9); a hostile gunship for defended worlds; ground anti-air counterplay is whatever already reaches an aircraft. Evidence: `createSnapshots -Psnapshot=close-air-support`, `sceneEvidence -Pscene=close-air-support`. |
| `air-manual-acceptance.md` | PLANNED | Verify centre-of-gravity rotation, hardpoint placement and per-mount sight, and thrust-weighted engine effects together in a live battle. |
| `warship-on-station.md` | PLANNED | A Paragon hovers over the battle and sweeps its laser lance across compounds, razing walls, roofs and units, friendly fire included. |
| `tender-ship.md` | ACTIVE — slice 1, the swarm choreography, pilot control and AI by stance built | The vanilla Shepherd as a mobile drone and construction ship: defence drones anchored to the hull and builder drones that work a site until a turret stands. Point defence by the drones' mining lasers is built. Player control through the direct-control seam is built. The wing launches, escorts, recalls and retreats as real flights. AI by stance is built: an unpiloted ATTACK or DEFEND tender lays its own sites. Open: the revetment build option, the story hand-out of a campaign Shepherd (a stated dependency; it will give the player one if they lack one), live acceptance. |
| Support-ship no-bombardment (DECIDED, deferred) | OPEN | Support hulls will not hunt ground (until a gunned support hull flies); class weights, carrier biases and escort are built (`warship-behaviours.md`, Classes, Decided and Decided: escort). Escort against hostile ships waits on ships as targets (G4). |
| Large planetary defences against ships; ships as targets (OPEN) | OPEN | The remaining rule changes behind the air-superiority half of `warship-behaviours.md` (gaps G4, G5). Ship weapons against aircraft and aircraft against ships shipped; the declared-hazard stand-in for ground threats is decided (Decision 2); the rest wait on owner direction. |

Future ship collision, wing composition, anti-air/survivability, modeled fighter
fire, and camera-Z work are extension paths in `air-nouns.md`, not contracted
stories yet.
