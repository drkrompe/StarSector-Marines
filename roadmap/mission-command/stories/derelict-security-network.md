# Derelict security network

Status: IN PROGRESS — every mechanics slice is built, with its scenes (What
is built): zones, the director over walkers and flocks, the derelict heavy
line, spotting, the node, repair drones, folded posts and power branches. The
walker is its own four-legged chassis in its picked sentinel body, and the
posts and nodes ship their picked looks. Feeds slice 5 of
`ruins-expedition-fork.md` through the ruins occupant (`ruins-expedition.md`,
Occupants).

Written: 2026-11-09

Updated: 2026-11-09 — slices 1 to 4 built. Owner picks (2026-11-09), all on
the recommended options: `derelict-mech-line` (a), a heavy line of its own;
`derelict-swarm-size` (a), one flock of twelve to sixteen per zone;
`derelict-flock-counters` (a), area fire and cover, flocks keeping out of
roofed rooms. Interiors added (Interiors), from the owner's note that some
sites are megastructures the company explores inside.

Read `derelict-lore-guide.md` in full first, then `ruins-expedition.md`
(The Ruins mission family, Derelict security), `extraction-command.md` (the
swarm director, the pattern this director follows, and the security-network
director it leaves open for Silent Colony) and `mechs-nouns.md`.

## Goal

Derelict security fights on the ground as drone flocks and heavy walkers
(owner, 2026-11-09): dormant security zones that wake on what their sensors
see; flocks that go up first, find, pin and screen on a bounded leash; heavies
that anchor their layers and come to what the flocks have pinned; a
contingency on the vault breach; and a dead node that stops relaying, repairing
and rebuilding. It is a mission director with honest knowledge, like the Rav's
swarm director, and it commands flocks as units.

## Slices

1. **Zones and states.** A security zone is a control node with the cradles,
   walkers, posts and repair drones it controls, and its perimeter. States are
   dormant, alert and awake; the wake triggers are a sensor contact, gunfire
   inside the perimeter and damage to a unit of the zone; an awake zone with no
   contact for a while settles to alert. Linked zones go alert on a relay.
   Unit-test the state rules on a zone alone.
2. **The director.** The security-network director gives each woken zone's
   flock a task (probe, screen, strike, pursue to its leash, return) through
   the flock model (`air-nouns.md`, Flocks), and holds each walker inside its
   perimeter at the layer's chokes and courts, sending it to what the flocks
   have pinned there. In the contingency every flock that can reach the haul's
   escort strikes it and the walkers walk to the egress route and hold it. It
   publishes its phase, each zone's state, each flock's task and only aggregated
   target contexts. `sceneEvidence` scenes for a wake, a relay, a flock's leash,
   a heavy coming to a pin, and the contingency.
3. **The derelict heavy line.** One new heavy chassis with its own art, sound
   and doctrine, outside the player's grid and never fielded by the company
   (`mech-lines.md`), in three loadouts: line frame (heavy cannon, plate),
   siege frame (mortar and missile racks), vault frame (heaviest plate). Domain
   and contingency are liveries. Mech parts and mech doctrine otherwise
   (`mechs-nouns.md`); art and sound through picks.
4. **Spotting.** A siege frame fires on contacts its zone's flocks hold, so a
   flock blinded by smoke or killed leaves it firing on old contacts. Uses the
   existing indirect-fire path; no new knowledge.
5. **The node.** An armoured, unarmed structure. Destroyed, its zone stops
   relaying both ways, stops repairing and rebuilding drones, and is out of the
   contingency's call.
6. **Repair drones.** The tender's builder drone working a damaged post, cradle
   or walker back up while its node stands and its stock lasts, standing down
   under fire.
7. **Posts that wake.** A folded defence post that does not fire or take a
   target until its zone is awake. Secondary: a few per zone at doors and
   courts.
8. **Power branches.** At a site whose power hall feeds the network, a
   junction that cuts a branch powers down the zones on it (`ruins-expedition.md`, Works).
9. **Looks.** Domain and contingency looks for the posts and node
   (`tech-families.md`), with picks. The cradle's look is the flocks'
   (`air-nouns.md`, Flocks).

## Interiors

Owner, 2026-11-09: some ruin sites are megastructures or bunkers; the company
lands outside and works into roofed rooms and corridors with the vault deep
inside (`mapgen-nouns.md`, Ruins ground: the far end is inside). The two halves of
the force split along the roofline:

- **Flocks own the exterior and the approaches.** A flock never enters a
  roofed cell; a strike on a unit under a roof holds the threshold, the doorway
  or breach where its way out meets open ground (`air-nouns.md`,
  Flocks). The company indoors is out of the flocks'
  reach, which is the counter the owner picked.
- **Heavies and posts hold the interior.** Zones laid along the vault's route
  put the vault's zone, and at the larger grades the next, inside, so their
  walkers stand in halls, corridors and antechambers. A pin under a roof still
  counts: the flock holding the doorway pins the unit inside, and the zone's
  line frames come to it through the corridors the flock cannot use.
- **The vault frame is the interior guardian.** It stands on the vault, comes
  only to what enters `VAULT_REACH_CELLS` of it, and stays there in the
  contingency, when the line frames walk out to the egress; a vault breach
  inside is fought by the heavies, the haul's way out by the flocks.
- **Sized for corridors.** The walker fits the narrowest passage an interior is
  carved with: its radius (0.72) stands in a `CorridorStage.CORRIDOR_WIDTH`
  corridor (2 cells), tested by `DerelictWalkerFrameTest`. A narrower door or
  seal door would shut it out; the interior's ways must stay corridor width
  wherever a walker is posted beyond them.
- **Posts that wake** (slice 7) stand at interior doors and courts as well as
  outside.

Built: where the site has an interior (`RuinsDelve.Interior`), the walkers
are posted by its own places rather than by route cells (What is built, The
seam into the battle).

## What is built

Everything is in `battle.derelict`.

- **Zones (slice 1).** `SecurityZone` holds one zone's state (dormant, alert,
  awake), its node cell and a Chebyshev perimeter. A stir (contact, gunfire
  inside the perimeter, damage) wakes it; an awake zone quiet for
  `SETTLE_TICKS` settles to alert, never to dormant; a dead node still wakes
  on its own senses but neither relays nor hears a relay, and is out of the
  contingency's call. `SecurityNetwork` holds the zones and their relay links:
  a waking zone alerts its linked neighbours one hop; `breachVault` calls the
  contingency, which keeps every live zone awake for the rest of the battle.
- **Posture (slice 2, walkers).** `WalkerPosture.decide` is the pure rule per
  walker: dormant or alert holds the post; awake, a line frame comes to the
  nearest pin inside its own perimeter, a vault frame only to a pin within
  `VAULT_REACH_CELLS` of its post, a siege frame holds and fires on the
  freshest sighting within its reach; in the contingency line frames hold the
  egress cell nearest their post (past the perimeter), the vault frame stays on
  the vault, and a siege frame fires on the haul's escort first.
- **Tasks (slice 2, flocks).** `FlockTasking.decide` is the pure rule per
  flock: under a third of strength it goes home; in the contingency it
  strikes the haul's escort off the leash; a dormant zone's flock stays home,
  an alert one screens its node; an awake one strikes inside the perimeter,
  pursues to `LEASH_CELLS` past it, screens the perimeter's edge toward
  anything beyond, and probes where its zone was stirred when it holds
  nothing.
- **The director.** `DerelictSecurityDirector`, ticked by `BattleSimulation`
  every half second, learns only the side's merged contacts
  (`getCommanderInfluence(DEFENDER)`) and its flocks' reports, stirs the zones
  they fall in and the zones whose walkers lost plate or structure, calls the
  contingency when `RuinsVaultObjective.breached()`, and then gives each
  walker lance its posture as a `MISSION_COMMAND` assignment (`defendArea` at
  the post or egress, `attackMove` onto a pin) and each flock its task. A zone
  with no flock up is its walkers' own eyes: they come to and fire on what the
  network's units hold. The haul's escort is the units the network has seen
  within `ESCORT_RADIUS` of the vault since the breach.
- **The flock seam.** `FlockCommandPort` (reports in, task and anchor out) is
  the director's only view of flocks; `FlockSystemPort` implements it on the
  shipped `FlockSystem` (`air-nouns.md`, Flocks), launching one `WASP`
  flock of `FLOCK_SIZE` per zone over its node with the zone's perimeter plus
  the leash, and not re-giving an order the flock already stands on.
- **The heavy line (slice 3).** `MechVariant.SECURITY_WALKER` (`MG-40`, catalog
  `security_walker`) is one new heavy chassis, `automated()`: kept out of the
  Mech Lab's ceilings, the debug bay, field sheds and every pattern list, so
  the company never fields, builds or refits it. `DerelictWalkerFrame` gives
  its three loadouts (line: HC-10, AC-6, two SRM-5; siege: two chainguns, two
  LRM-15 frag racks; vault: two HC-10, two SRM-15) and their plate as a share
  of the chassis's (siege 0.85, vault 1.45). Nothing of the Bulwark's (owner,
  2026-11-09): its own chassis appearance (`CHASSIS_DERELICT`), body plan
  (`MechBodyPlan.DERELICT`: a low torso over legs, struck mostly in the torso)
  and numbers, all UNTUNED. Its arm guns sit in two side ports of the torso
  and its racks on its back; shared systems (movement, weapon components, mech
  doctrine and targeting) are the mechs' own.
- **The walker's legs.** A torso on four reaching legs (owner, 2026-11-09:
  "a central torso on 3-4 legs that reach out and walk or pull along walls";
  pick `derelict-walker-legs`, four). `DerelictLegRig` is the drawn rig,
  render-only. A gait clock runs on the distance covered: at a walk the legs
  lift one at a time in a four-beat sequence, three feet always down; above
  `RUN_SPEED` the diagonal pairs trot with a moment of suspension. A foot stays
  planted until its turn, then lands ahead of where the body will be;
  standing, a foot dragged out of place steps back alone, and a torso that
  jumps stands fresh. The drawn body carries its weight (owner: it looked to
  hover): it sways over the standing feet, dips as a leg lifts, bounces at a
  run and leans into its speed. Each leg chooses its ground (owner's wall-reach
  answer, "mix"): a wall face within `GRIP_CELLS` takes the foot up on it, and
  in the open it stands on the floor. Each leg is a thigh and a shin jointed at
  a drawn knee: `DerelictLegPose` solves the knee in three dimensions from the
  body's height, so a foot near the body raises the knee and a foot on a wall
  stands higher; seen from above the knee is held between three and five tenths of the
  way from hip to foot, so it never reads over the hip or past the foot (owner, 2026-11-09). The torso still paths, collides and stands on floor cells, so
  pathing is untouched; moving over or along wall cells would need a
  passability view of its own, read by both the planner and terrain motion as
  `HoverSkirt` and `WallCrush` are, and is not built. The shipped parts are
  the sentinel body (owner, 2026-11-09: pick `derelict-walker-look-v3`,
  sentinel), painted after the vanilla Domain derelict ships over `guide.py`'s
  geometry and cut by `export.py` (`walker-sentinel-v3`); the cupola and crab
  candidates stay in `art-source/derelict-walker/`. The vault frame shares it.
  In battle each leg has a knee with its own structure, and a standing walker
  blocks small bodies only at its feet, so people pass under its torso
  (`ruins-guardians.md`, The walker's legs and what passes under it).
- **Walker doctrine.** The line and vault frames run the balanced mech
  doctrine and the siege frame long-range support. Armored support, which the
  line first had, paces friendly infantry and holds where it stands with none
  to pace, so a walker never left its post; `derelict-network` caught it. The
  director walks a walker to a cell it is to hold (the egress, or its post
  after a pin) by attack-move, and holds it there by defending the area once
  within reach (`walksTo`), since the doctrine defends an area where it
  stands.
- **Spotting (slice 4).** The director hands a siege frame's squad each fresh
  sighting it fires on as a `BeliefSource.SPOTTED` contact
  (`Squad.observeSpottedContact`). `MechTargeting`, when nothing else is
  engageable, takes a spotted belief seen within `SPOTTED_FRESH_TICKS` for a
  mech with an indirect mount, within that mount's range and the hips'
  traverse, without a line of sight; the shipped no-sight accuracy penalty of
  indirect fire applies. Not built: firing on a cell after the sighting goes
  stale, so a blinded flock leaves the siege frame without a target rather
  than firing on the old contact.
- **The seam into the battle.** `BattleSetup.createRuins` with a derelict
  occupant (`RuinsOccupant.isDerelict`) posts no generic squads or patrols and
  calls `DerelictSecurity.install(sim, grade)`, which lays the grade's zones
  along the vault's first lift route (`zonesAlong`: the vault first, the last
  fifth of the route left to the company, perimeters meeting), posts the
  grade's walkers (`heavies`, `zoneOf`: the vault frame at the vault, the siege
  frame mid-route, line frames outward in turn), sets the egress cells
  (`egressAlong`), launches the flocks and installs the director in place of
  `ExtractionDefenderCommand`. `RuinsExpeditionMissionFactory` names the
  occupant's own faction for a derelict site. Ground with no ruins vault
  passes its own route and source cell to the route overload of `install`
  (The Fall, `long-survey-arc.md`); with no vault objective the contingency
  is never called.
- **Interior posts.** On a site with an interior, `install(sim, grade, delve)`
  posts the walkers by `interiorPosts`: the vault frame on the vault's
  approach, the siege frame in the first entrance, line frames in a
  chamber their own zone keeps, nearest its node, else the interior's chambers
  in turn (its entrances when it has none); each walker
  belongs to the zone whose node is nearest. A zone's flock is homed on the
  first unroofed cell of the route from its node toward the landing
  (`openSkyHome`), so a flock whose zone lies indoors waits at the doorway
  rather than under the roof.
- **The node (slice 5).** `DerelictNode` is each zone's control node, an
  armoured, unarmed structure (a drone hub's structure, drawn as its
  lineage's relay structure: Looks), stood by `install` on the nearest cell to the zone's node cell
  that is off the route, against a wall and leaves three open cells across
  (`nodeCell`), so it never narrows a corridor below a walker's width. The
  director watches it each pulse (`DerelictNode.watch`): damage to it wakes
  its zone; destroyed, the zone loses its node (no relay either way, out of
  the contingency's call) and its flock's cradle stops rebuilding
  (`FlockCommandPort.nodeStands`).
- **Repair drones (slice 6).** `RepairDrone`: each zone puts up one
  unarmed builder drone (the tender's look) over its node. Each pulse it
  picks the most worn of its zone's posts, cradle and walkers below
  `MEND_BELOW`, flies to it in a straight line and, within reach, lays
  `MEND_PER_SECOND` from a `STOCK` for the battle: plate then structure on a
  post or cradle, plate only on a walker (`MechSections.mendArmor`, on the
  most worn standing section; a lost section stays lost). Hit, or with a
  hostile the network holds within `THREAT_CELLS`, it breaks off home and
  stands down `STAND_DOWN_TICKS`; with its node down or dark, or its stock
  spent, it stays home. It can be shot down. It flies over walls and roofs,
  unlike a flock.
- **Posts that wake (slice 7).** `DerelictPost`: each zone stands
  `PER_ZONE` folded gun posts (the Vulcan emplacement's gun and durability,
  drawn as its lineage's folding post: Looks), anchored at the interior's entrances and then its
  chambers that the zone keeps, else beside the zone's route posts, each on a
  wall cell off the route (`besideRoute`) and kept apart. A post's gate is its
  emplacement power: the director powers it only while its zone is awake
  (`DerelictPost.unfolded`), so folded it neither slews, takes a target nor
  fires; it can still be shot, and damage to it wakes its zone.
- **Power branches (slice 8).** `DerelictPower`: each zone is fed through
  the ruins works junction of the layer its node stands in (the nearest layer
  for the vault's zone), on the junction's way-on branch, the one that also
  feeds the layer's gates and, at the last layer, the vault's seal. The feed
  runs through the junctions whether or not the company has restarted the
  hall. Thrown to its store, the zone goes dark (`SecurityZone.powerDown`):
  dormant and deaf to every stir, relay and call, its posts folded, its
  cradle not rebuilding; thrown back, it comes up dormant. So cutting a
  branch buys a quiet way through a zone at the price of the gates and the
  vault seal on it.
- **Looks (slice 9, posts and node).** `DerelictLook` is the network's
  lineage look, set by `install` from the ruin's occupant (Domain security's
  grey and hazard yellow, contingency security's Tri-Tachyon white gone
  dull); the director carries it and the renderer reads it, never the
  battle. A post draws its lineage's base in the paving and, while its power
  is cut, its shut head square to the map; powered, its gun head turning
  with the mount, with no recoil layer. A node draws its lineage's relay
  structure in the hub pass, `NODE_VISUAL_CELLS` long, as the cradle draws
  its housing. Art from ImageGen geometry guides, three post and four node
  designs a sheet per lineage (`art-source/derelict-security`). The looks
  are painted after Starsector's own derelict ship sprites (greebled
  sage-green and khaki plates, hazard yellow, brass, rust), no text or
  emblems, both lineages on one sheet (`-v2b`), and cut by `pack-v2.py`:
  owner picks `derelict-node-look` (cabinet: an armoured relay cabinet with a
  louvred door and status lamps) and `derelict-post-restyle` (the pop-up
  post, a striped well under an iris hatch, redrawn on its own geometry),
  carded in game beside a company Hound. The walker is drawn by its own leg rig (The
  walker's legs).

Evidence: `SecurityNetworkTest`, `WalkerPostureTest`, `FlockTaskingTest`,
`DerelictSecurityTest`; fixtures `ruins-derelict-vast-v1` and
`ruins-derelict-extensive-v1`; `sceneEvidence -Pscene=derelict-post` (a
post holds fire with a fire team in its sight until its zone wakes, then
fires; unseen, it stays folded); `sceneEvidence -Pscene=derelict-repair`
(mend: the drone flies twenty cells and works a post from 50 to 134 of 140;
node-down: it stays home; under-fire: hit, it breaks off home, stays while it
stands down, then goes out again); `createSnapshots
-Psnapshot=derelict-heavies-look` (the walkers at their posts, then the fight
from the first walker contact, then the vast site's vault frame and a line
frame at their interior posts with the roof lifted); `sceneEvidence
-Pscene=derelict-network` (relay: a wake alerts the next zone and not the one
after; pin: a line frame given a contact inside its perimeter comes to it and
engages; pin-outside: one past its perimeter is held for; contingency: the
line frame walks to the egress and holds it, the siege frame fires from its
post, the vault frame stays); `DerelictLegRigTest`;
`createSnapshots -Psnapshot=derelict-heavies-look
-Dsnapshot.derelict.interiorOnly=true` (the interior stills alone, closer); `DerelictLegPoseTest`; `createSnapshots -Psnapshot=derelict-walker-look`
(the vault frame close and in a hall of the vast ruin beside a company
Bulwark, then recordings of it walking and running a route through narrow
hall and open floor, the pace scripted); `createSnapshots
-Psnapshot=derelict-security-look` (a post folded and unfolded and a node, in
either lineage, the look candidates through `-Dsnapshot.artOverlay`).

Not built yet: a `sceneEvidence` loop for a flock's leash (the flock
model's own scenes cover its flight; `FlockTasking` is unit-tested); the
walker's sound, movement over or
along wall cells, the post's own gun, and the repair drone's own look if it
should differ from the tender's (the cradle's look is
`air-nouns.md`, Flocks). Strength is not tuned, and is not tuned from
autopilot runs: it waits on the owner playing a derelict battle. Both
fixtures fly the company's real lift (one Hermes, six twelve-seat trips, as
playBattle's Veteran Company).

## Constraints

- New mechanics are the flock (its own story), the derelict heavy chassis, the
  folded post's wake gate and the node; anything else new is raised first.
- No knowledge the zone's sensors, flocks and relays do not give it, and no
  damage immunity.
- Silent Colony keeps its scripted opposition until a separate decision moves it
  to this director.

## Done

A derelict expedition on each grade plays through the ruins occupant
(`ruins-expedition.md`, Occupants) and the
owner has played one. Fold the director into `ruins-expedition.md`, the units
into their noun docs, add a row to `shipped.md`, and delete this story.
