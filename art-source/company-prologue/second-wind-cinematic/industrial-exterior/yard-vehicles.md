Status: DRAFT (proposal, awaiting the owner)
Written: 2026-10-20

# Yard vehicles: where the ground crew comes from, and a working port

Two parts, each behind its own switch, off by default, so `main` renders the approved C4 and C5
unchanged until the owner passes them. `yard_vehicle_plan.py` draws the plan of part 1 (start, run
and parked spot of every Kilo Nine vehicle, shipped against proposed) to `build/c4x/k9-vehicle-plan.png`.

## Part 1: the Kilo Nine crew's origins (`K9_ORIGINS=1`)

Shipped, the tug, crew carrier and bowser hop to stand-offs on open dirt at C4 10.4-13.4 s and roll
in from there. Nothing explains where they came from, the tug comes out through the hangar wall (no
door), its run in to the hull goes through a leg of the north-west scaffold tower and a walking
scaffold hand, and the bowser drives in under the south-east scaffold.

With the switch, each vehicle starts where it lives and waits out the landing there, 68-73 m from
the pad centre, clear of the downwash:

| Vehicle | Home | Run to the hull |
|---|---|---|
| Tug | the workshop's garage bay (the annex on its east side), nose north | 34 m straight north, C4 18.4-23.9 s |
| Crew carrier | the motor pool on the south apron, between the washing lines and the tanks | 38 m, north then north-west, 18.9-24.9 s |
| Bowser | the yard's fuel point beside its tanks, a pump at its side | 43 m straight north, 19.4-25.9 s |
| Customs car | unchanged: the arrivals hall's MuniSec post, the port road, in at the gate | |

Every vehicle parks on the same spot at the hull and stops on the same second as before
(`K9_ROLL_IN` stops), so the crews and customs in C5 (`c5_scene.cast`) get out where and when they
always did. Only the start times move, a second earlier, because the runs are longer than the
old roll-ins; each speeds up and slows over 1.6 s and peaks at about 8 m/s.

**Shots changed:** C4 from frame 0 (the vehicles stand at their homes instead of the old starts,
and the 10.4-13.4 s hops are gone) and C4 18.4-21.4 s (the runs). C5 opens on C4's last frame with
the vehicles still driving, so **C5 0-4.5 s changes too**: they arrive from the south instead of
from the north-west and south-east. From C5 4.5 s on, everything stands where it did.

Checks (`port_life.report` on the four vehicles): no clip against anything standing (shipped: 4
tug-frames in the hangar wall), and no new moving pair (shipped: the tug runs into scaffold hand 7).

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
