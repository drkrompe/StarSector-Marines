# Derelict drone flocks

Status: PLANNED — owner direction ("heavy emphasis on drone swarms (flocking
behaviors) and heavy mechs"); nothing built.

Written: 2026-11-09

Read `derelict-lore-guide.md` (The flock, The flock and the Rav) first, then
`air-nouns.md` (the air body, fighters and drones), `tender-ship.md` (the wing
that already flies drones from a bay) and the drone package (`Drone`,
`DroneSeparation`, `DroneSwarmAction`, `DroneHub`).

## Goal

A **flock** is a new ground-battle unit: twelve to sixteen low-flying swarm
drones that move as one body by flocking, pour down streets, split round walls
and re-form, wrap a target and strafe it in passes. The network commands the
flock, never its drones. It is derelict security's signature
(`derelict-security-network.md`), and the model is general: nothing in it is
derelict-only.

## Model

- **Members and anchor.** A flock is a set of member drones and an **anchor**:
  a point its commander moves along a route. Each member steers to the anchor
  plus three local terms over its neighbours in the flock: **separation** (the
  shipped `DroneSeparation` push), **alignment** (match their heading) and
  **cohesion** (close on their centre). Weights are flock data, so a tight
  "wasp" flock and a loose "murmuration" are tuning, not code.
- **Low flight.** A swarm drone flies below the rooflines. It crosses low cover
  (rubble, sandbags, fences) and never walls, roofs or roofed interiors. Each
  member routes on the ground navigation for open cells (streets, courts, open
  doors, breaches), so a wall between member and anchor splits the flock by
  each member's own route, and cohesion re-forms it past the wall.
- **Tasks.** A commander gives a flock one task with an anchor route: **screen**
  (hold a station between a threat and what it covers), **probe** (fly a route
  and report), **strike** (close, wrap and strafe a target), **pursue** (follow
  a contact to a leash), **return** (go home to its cradle). The flock decides
  only how its members fly it.
- **Strike.** On strike the anchor sits on the target and the members take
  orbit slots round it, strafing when their nose comes on (the shipped orbit and
  fire-arc), the slots turning so the flock wraps the target rather than
  stacking on one side.
- **Loss and re-forming.** A member dies by the ordinary damage rules and falls
  where it is hit. A flock under a third of its strength returns to its cradle;
  a cradle merges what comes back and rebuilds lost drones at a slow rate while
  its node stands.
- **Sight.** Every member is a sensor: what any member sees is the flock's, and
  the flock's is its commander's. Smoke and walls block it as anyone's.
- **Countering it.** Nothing special: area weapons hit several members because
  they are close together; a flock cannot reach shooters in a roofed room; flak
  and point defence engage members as fliers. The counters fall out of the
  model; none is a rule.

## Slices

1. **Flock steering.** A pure steering function: a member's next velocity from
   its state, its neighbours' and the anchor, with separation, alignment and
   cohesion weights. Unit-tested on small inputs: members spread from a point,
   align to a shared heading, close on a moving anchor, and never stack.
2. **Low flight and routing.** Swarm drones as air bodies held below roof
   height, routed on open ground. A `sceneEvidence` scene: a flock sent past a
   building splits round it and re-forms; a flock never enters a roofed room.
3. **The flock unit and its tasks.** The flock entity (members, anchor, task,
   strength), its tasks and the strike's orbit slots. Scenes for screen, probe,
   strike and return.
4. **Cradles.** The drone hub as a flock's home: launch, recovery, merging and
   slow rebuild while its node stands.
5. **Cost.** Measure the flock's per-tick cost with four flocks of sixteen on a
   vast map (`profileSwarmFlow` or a sibling profile), and bound the neighbour
   search to the flock's own members.
6. **Counters measured.** `loadoutMatchup` or a scene: a flock against a squad
   in the open, the same squad indoors, and with a squad automatic and grenades.
   This confirms the counters fall out of the model; it is not a balance target.
7. **Look.** Swarm-drone art in the Domain and contingency looks, and the
   flock's sound (a rising swarm whine), through picks.

## Constraints

- Not the Rav. Flocks never use the Rav's prey field or its spawning; a flock's
  numbers are its cradle's.
- The flock is commanded only by a mission director or commander through tasks;
  no member is ever ordered on its own.
- No knowledge a member did not see.

## Done

A flock plays in a derelict expedition and the owner has seen it pour, split,
re-form and strike. Fold the model into `air-nouns.md`, add a row to the air
`shipped.md`, and delete this story.
