Status: DRAFT (a plan awaiting the owner; nothing built)
Written: 2026-10-20

# Port roads: a network that makes sense

The owner's note on the port: traffic is heavy, and the roads were placed haphazardly. This is a
plan for the public port's roads, drawn by `port_roads_plan.py` over an occupancy map of the built
scene: the roads as they are, a proposed network with a hierarchy, which traffic uses which road,
and which shots see it. Nothing in the scene changes until the owner passes it; it would then be
built behind its own switch, off by default. Kilo Nine's gate and the haul road outside it stay.

## The roads as they are

- **Port road** (y 366-378, x -700 to Kilo Nine's gate) and the **cross streets** at x -300, 0 and
  300 down to the freight road: sound, and the only real roads.
- **Berth cargo lanes** run straight from each freighter's stern to the container yard. Sound as
  service lanes.
- **Heavy berth road** (x 43-57) between lighter pads 5 and 95 up to the heavy berth. Sound.
- **Fuel farm road**: a stub that starts 4 m behind berth -310's pad and joins nothing, so a tanker
  could only leave across the pad.
- **Terminal road**: starts inside the heavy berth's walled deck, runs out through its blast wall
  and ends on the terminal shed's wall.
- **No road at all** to the truck park, the terminal lorry park, the container overflow, the depot,
  the fuel farm, the tanker rack, the spares yard or the west hangars; the west truck lot only
  across open apron.
- **The terminal apron** (port bus, baggage trains) is an island: the arrivals hall stands between
  it and the port road.
- **Traffic as loops, not trips.** The container-belt loops run 300 m along the port road and turn
  back at Kilo Nine's gate, and the port-west and port-mid loops do the same on the west half,
  crossing the berth lanes twice a lap. That is most of the heavy traffic the owner sees.

## The proposal

| Tier | Roads | For |
|---|---|---|
| Main haul road | freight road, cross streets -300 / 0 / 300, port road; heavy berth road north to the staging | everything entering or leaving the port |
| Service roads | west connector (x -545, port road to the west hangars) and a back road behind the berths (y 584-610, to the truck park); on the east an arrivals link (x 285, past the bus bays) and a terminal road (x 278, the terminal apron to the lorry park) | the back lots, the terminal, crews and customs |
| Spurs | fuel farm and tanker rack (x -422), west truck lot, spares yard; the rest open off a road directly | lots |
| Pad service rings | one-way, 8 m outside each lighter pad's marker ring, two links to the port road | tugs, dollies and fuel carts at the Hermes pads |

The fuel farm stub and the terminal road through the heavy berth's wall go. The berth cargo lanes
stay as service bays. No road crosses a pad or its keep-out ring (pad radius plus 10 m for a berth,
plus 8 m for a lighter pad), runs through a building or ends on a wall.

**Flows.**
- Freight haulers use the freight road, the cross streets and the container yard.
- The berth carriers stay on their lanes.
- Fuel: tanker rack, fuel spur, back road, west connector, port road, then round pad 5's ring into
  the gap at berth -40.
- Crew carriers and buses: arrivals bays, arrivals link, port road, then Kilo Nine's gate. This is
  the way round 2's crew carrier already drives.
- Customs: the hall's post, arrivals link, port road, then the gate or west to the berths.
- Lighter tugs: heavy staging, heavy berth road, then the pad rings.
- Trucks for the back lots: west connector and back road.

The belt and port-west / port-mid loops turn on the container yard's loading strip instead of the
port road, which leaves the port road carrying trips rather than laps.

## What it changes in the approved shots

- **C3** (the window): forest only. Unchanged.
- **C4 pan, 0-13.4 s:** the whole port, small. All of it shows.
- **C4 linger, 13.4-21.4 s, and C5 0-4 s:** the east half. The heavy berth road, the arrivals link,
  the terminal road, pad rings 5 and 95, and the quieter east port road and Kilo Nine gate junction.
- **C5 4-18 s** (the close view on the ramp): only the arrivals link, along the frame's left edge.
- **C5 rise, 18-26 s, and the title to 40 s:** everything east of about x -540.

Both approved finals would need re-rendering. The Colossus stays the only thing in the air.
