# Air — open stories

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-10-11 — warship missile flight and swarm bursts shipped; later missile items remain on `warship-missiles.md`.

| Story | State | Outcome |
| --- | --- | --- |
| `fighter-air-entities.md` | ACTIVE — the overlay class and the profile's dead tuning are gone; the package rename remains | Fighters are ordinary air entities flying ordinary sorties. Committed wings arrive on an `AirCorridor` from off the map and are dispatched by `AirCoverSystem`. |
| `air-manual-acceptance.md` | PLANNED | Verify centre-of-gravity rotation, hardpoint placement and per-mount sight, and thrust-weighted engine effects together in a live battle. |
| `warship-on-station.md` | PLANNED | A Paragon hovers over the battle and sweeps its laser lance across compounds, razing walls, roofs and units, friendly fire included. |
| `warship-posture-readout.md` | PLANNED | A warship has a selectable, sticky, readable posture (air superiority, support, intercept, bombard, withdraw) chosen from a threat picture of what the sim already tracks. Slice 1 of `warship-behaviours.md`. |
| `warship-objective-fire.md` | PLANNED | Support fire is biased to the mission objective and avoids avoidable friendly fire; danger-close is an explicit mission flag. Slice 2 of `warship-behaviours.md`. |
| Warship intercept (open, unwritten) | OPEN | Defender ship denies inbound drops: LZ-timed area fire ranked by payload value; needs the hostile-shuttle query and an answer to whether a detonation hurts a craft on its pad. |
| Warship withdraw (open, unwritten) | OPEN | Self-preservation posture: hull, overload and shield entry; movement away; exit hysteresis. |
| Ship class bias (open, unwritten) | OPEN | Read `hullSize`, `hints`, fighter bays and shield type in `Warship.fit`; carrier, escort and support weights. |
| Ship weapons against aircraft; aircraft against ships; ground hazard to ships; ships as targets (open, owner decisions first) | OPEN | The four rule changes behind the air-superiority half of `warship-behaviours.md` (gaps G2 to G5). |

Future ship collision, wing composition, anti-air/survivability, modeled fighter
fire, and camera-Z work are extension paths in `air-nouns.md`, not contracted
stories yet.
