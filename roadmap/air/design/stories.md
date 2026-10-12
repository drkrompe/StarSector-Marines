# Air — open stories

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-10-11 — warship posture, objective-aware fire, landing-denial intercept, missile flight and ship-versus-air fire shipped; the swarm-burst slice remains.

| Story | State | Outcome |
| --- | --- | --- |
| `fighter-air-entities.md` | ACTIVE — the overlay class and the profile's dead tuning are gone; the package rename remains | Fighters are ordinary air entities flying ordinary sorties. Committed wings arrive on an `AirCorridor` from off the map and are dispatched by `AirCoverSystem`. |
| `air-manual-acceptance.md` | PLANNED | Verify centre-of-gravity rotation, hardpoint placement and per-mount sight, and thrust-weighted engine effects together in a live battle. |
| `warship-on-station.md` | PLANNED | A Paragon hovers over the battle and sweeps its laser lance across compounds, razing walls, roofs and units, friendly fire included. |
| `warship-swarm-burst.md` | PLANNED | A salvo is a volley of bodies that fans, weaves and arrives staggered; MIRVs bloom with their true payload; point defence kills bodies and is saturated by volume. Slice 2 of `warship-missiles.md`. |
| Ship class bias (open, unwritten) | OPEN | Read `hullSize`, `hints`, fighter bays and shield type in `Warship.fit`; carrier, escort and support weights. |
| Aircraft against ships; large planetary defences against ships; ships as targets (OPEN) | OPEN | The rule changes behind the air-superiority half of `warship-behaviours.md` (gaps G3 to G5). Ship weapons against aircraft shipped; the declared-hazard stand-in for ground threats is decided (Decision 2); the rest wait on owner direction. |

Future ship collision, wing composition, anti-air/survivability, modeled fighter
fire, and camera-Z work are extension paths in `air-nouns.md`, not contracted
stories yet.
