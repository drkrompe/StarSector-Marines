# Air — open stories

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-10-12 — drone tender slice 1 built, the rest on the board; warship posture, objective-aware fire, landing-denial intercept, missile flight, swarm bursts and ship-versus-air fire shipped; later missile items remain on `warship-missiles.md`.

| Story | State | Outcome |
| --- | --- | --- |
| `fighter-air-entities.md` | ACTIVE — the overlay class and the profile's dead tuning are gone; the package rename remains | Fighters are ordinary air entities flying ordinary sorties. Committed wings arrive on an `AirCorridor` from off the map and are dispatched by `AirCoverSystem`. |
| `air-manual-acceptance.md` | PLANNED | Verify centre-of-gravity rotation, hardpoint placement and per-mount sight, and thrust-weighted engine effects together in a live battle. |
| `warship-on-station.md` | PLANNED | A Paragon hovers over the battle and sweeps its laser lance across compounds, razing walls, roofs and units, friendly fire included. |
| `tender-ship.md` | ACTIVE — slice 1 and the swarm choreography built | The vanilla Shepherd as a mobile drone and construction ship: defence drones anchored to the hull and builder drones that work a site until a turret stands. Open: player control through the direct-control seam, AI by stance, more build options, the story hand-out of a campaign Shepherd (a stated dependency; it will give the player one if they lack one), live acceptance. |
| Ship class bias (open, unwritten) | OPEN | Read `hullSize`, `hints`, fighter bays and shield type in `Warship.fit`; carrier, escort and support weights. |
| Aircraft against ships; large planetary defences against ships; ships as targets (OPEN) | OPEN | The rule changes behind the air-superiority half of `warship-behaviours.md` (gaps G3 to G5). Ship weapons against aircraft shipped; the declared-hazard stand-in for ground threats is decided (Decision 2); the rest wait on owner direction. |

Future ship collision, wing composition, anti-air/survivability, modeled fighter
fire, and camera-Z work are extension paths in `air-nouns.md`, not contracted
stories yet.
