Status: ACTIVE (part 1 approved and on by default; part 2 held)
Written: 2026-10-20
Updated: 2026-10-20 — owner approved round 2 ("Yard review: Looks good"); K9_ORIGINS is now the default

# Yard vehicles: where the ground crew comes from, and a working port

Two parts, each behind its own switch. Part 1 was approved on 2026-10-20 and is on by default
(`K9_ORIGINS=0` is the control that restores the old roll-in); part 2 is held, off by default. `yard_vehicle_plan.py` draws the plan of part 1 (start, journey,
holds and parked spot of every Kilo Nine vehicle, shipped against proposed) to `build/c4x/k9-vehicle-plan.png`.

## Part 1: the Kilo Nine crew at work (`K9_ORIGINS`, default on)

Shipped, the tug, crew carrier and bowser hop to stand-offs on open dirt at C4 10.4-13.4 s and roll
in from there. Nothing explains where they came from, the tug comes out through the hangar wall (no
door), its run in to the hull goes through a leg of the north-west scaffold tower and a walking
scaffold hand, and the bowser drives in under the south-east scaffold.

The first proposal (round 1) started each one at a home in the yard and drove it straight in on the
old cue. The owner found it robotic: everything moved off together after the landing, crept the
last couple of metres and was parked before anybody came off the ship. Round 2 gives each vehicle a
journey with its own timing, and keeps the tug and bowser working while the families come down.
`K9_LEGS` holds the itineraries: legs of (start, end, waypoints, reversing), holding in between.
Times are C4 seconds; C5 opens at C4 21.4.

| Vehicle | From | What it does |
|---|---|---|
| Crew carrier | the arrivals hall's spare (third) bus bay, out of shot at the start of C5's frame | C4 0-15.5: along the port road, in at the gate ahead of customs, up the haul road and onto the spur, where it holds through the touchdown dust. C4 20.6-C5 4.6: pulls up beside the ramp, first to arrive. |
| Tug | the hangar's south apron | C4 3.0-19.0: down the haul road (on its west half, clear of the stores stack) to the spur junction, where it gives way to the customs car. C4 21.2-C5 7.0: along the spur behind customs, stopping while an officer walks across. C5 11.4-14.6: runs past its spot; 15.4-16.6: backs into it and squares up. |
| Bowser | the yard's fuel point beside its tanks, a pump at its side | C4 12.0-17.0: creeps off the fuel point to a holding line 61 m from the pad during the final descent. C4 18.6-C5 1.8: in towards the hull, stopping short. C5 2.4-4.8: eases onto the hookup; its hand walks the hose to the hull and back. |
| Customs car | unchanged: the arrivals hall's MuniSec post, the port road, in at the gate | parks C5 5.0, behind the carrier |

The parked spots at the hull are the approved ones; only the tug's heading changes (it backs in
square to the ramp). The crews get out when their own vehicle stops (`c5_scene.cast`), so the tug
crew is now out at C5 about 17 s, while families are already on the apron. C5's Kilo Nine walk map
is read once every vehicle has stopped; the port's people sites keep the earlier reading, since by
then the Colossus's lift dust covers the heavy berth.

**Shots changed:** C4 from frame 0 (the carrier and tug are on the move from 0 and 3 s; the 10.4-13.4 s
hops are gone; the bowser creeps during the descent) and C5 0-17 s (carrier and bowser arrive by 5 s,
the tug works in until about 16.6 s). From C5 17 s on, everything stands where it did.

Checks (`port_life.report` on the four vehicles, the C5 crowd report): no clip against anything
standing, no new moving pair, and no walker inside a vehicle or under a moving one.

## Part 2: a working port (held)

Held until the owner has seen part 1, because anything new in the port shows in C5's rise, and C5's
final is already rendered. Sites picked on the occupancy map of everything standing and moving
across C4 and C5, all off Kilo Nine and outside C5's close view:

- **Hermes pads 5 and -85:** a cargo loader at each parked Hermes's tail, its deck working up and
  down, and a tug with two dollies out of the berth-gap equipment park to it and back.
- **The ferry tower and the empty berth (-220):** a repair crew at each, with a mobile crane
  slewing between a flatbed and the tower, a boom lift at the tower arm and a utility truck parked.
- **Road works by the arrivals hall:** a roller working slowly along the verge where the road gang
  already stands.
- **Lots:** a stowed mobile crane, a wheel loader, a dump truck and utility trucks in the west
  depot yard and the haulage depot.

Tugs, bowsers, tankers, haulers, flatbeds, forklifts and pickups wear the painted sheets that
exist. The six new shapes (mobile crane, utility truck, boom lift, wheel loader, dump truck, cargo
loader) need one more painted sheet in `vehicle_paint.SHEETS`: one ImageGen generation, not made yet.
