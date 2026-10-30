# Asharu battle: the wreckfall

Status: DRAFT — blockout round 7 in code, not yet rendered (last posted run: round 6). Hull builds nearly all finished.
Written: 2026-10-19
Updated: 2026-10-20 — plays on arrival at Asharu as scene-setting (owner): told as a comms recording, audio first, then the picture; a brawl, about 2 minutes 10.

The cinematic of the fighting that brought the Colossus and the Shepherd
**Morrow** down onto Asharu, three cycles before the Shepherd-recovery arc. The
battle is a merchant convoy against a pirate raiding fleet in low orbit. It ends
with both ships burning into the atmosphere toward the northern canyons, where
Basin Reclamation will later find them.

## What the story fixes, and what it leaves open

Read from `shepherd-recovery-arc.md`, `shepherd-recovery-dialogue.md`,
`shepherd-recovery.md`, `early-operation-nouns.md` and `mapgen-nouns.md`:

- **Fixed:** two fleets fought in low orbit over Asharu. A Colossus and several
  of her escorts came down in Basin's valley; the broken Colossus lies against
  the northern canyon wall. Morrow, a Shepherd, came down in the same wreckfall,
  "broken-backed in the fall", and her landing left scars. A sealed hold in the
  Colossus stayed sealed until Basin opened it recently.
- **Open:** the story calls it "unnamed low-orbit fighting" and "not a canonical
  vanilla battle". It does not say who the fleets were. Making the attackers
  pirates is this cinematic's choice (the owner's). It fits the game's own
  description of Asharu: the Hegemony sends "patrols only to chase down pirates".
- **Do not invent:** the Colossus's cargo, owner or buyer, Morrow's original
  crew, or any reason the convoy was carrying something special. The convoy is
  ordinary traffic. The pirates want cargo, and the cinematic never shows what
  any of it is.
- **Tone** (`shepherd-recovery-dialogue.md`): a small story, grounded and
  low-tech. This is an ordinary raid on an ordinary convoy that went badly, not
  a great battle.

## The place

**Asharu** is Corvus's desert world. The game's description: an abandoned
terraforming prospect, dunes retaking unbuilt cities, settlers in the
sheltering mountains. Its texture in the blockout is the game's own
`desert01` map (read from the install, never copied), which has scarps and
canyons between dune seas, broken up by procedural relief.

**Surface detail** (owner, 2026-10-22). Over that map lie seven ImageGen detail tiles, each
generated from the layer below so the planet still reads as desert01 from orbit, from 3.3 km a
pixel out to the horizon down to 156 m under S1a, with static settlements painted in where the
cameras see them: under S1a a walled town where two wadis meet, a landing grid and a terraced
open-pit mine; down S12's track a spaceport on a salt pan, a walled city at the canyon mouth and
strip mines. No traffic, no lights. The tiles are on by default (`--surface base` is the
control); the settlements are accepted as they are. **S1a and S12 use matte-painted backdrop
plates** in place of the rendered planet layer (`plates/`, Drive `builds/asharu-plates/`, pinned
by `roster.PLATES`). See `surface/README.md` and `plates/README.md`.

The game also gives Asharu a **derelict orbital platform**: "castoff
bulk-carrier segments welded between a honeycomb of emptied fuel tanks...
hangars stand empty but for a few slumped, stripped hulks... no one would
expect to find anything of value here". That is where the pirates wait.

The blockout builds it to read as that description (`platform_parts`). Five mismatched bulk-carrier
hulls sit along a sagging double girder, one of them knocked askew. Three clusters of drum tanks
are packed between the hulls, with gaps where drums have gone. Two open hangar bays hang under and
beside the hulls, and a broken mast stands off the forward end. It has no spheres and no flat
panels. The first blockout's two 300 m flat "collector" slabs read as big quads, and its eighteen
sphere tanks read as orbs; both are gone.

**The debris field** (owner, 2026-10-22: eleven hulls pouring out of the station read as a
"Looney Tunes clown car"). The platform sits in a field of its own wreckage about 3.3 km by 2 km
by 1 km (`debris_field`). It holds old hulks up to about 340 m, broken hull sections, spars and
plates down to a few metres, all slowly tumbling about their own axes: the big wrecks at a few
tenths of a degree a second, plates at several. Nothing in it is a sphere.

**Nothing in it reads as a box** (owner, 2026-10-22, on preview 12). Each wreck's boxes are what
`--check` measures the hiding against, but the picture draws a broken shape inside each one
(`wreck_meshes`), chosen from the box's proportions: a tapered hull snapped off ragged at one end,
with gashes in its side and a stub of superstructure; an open hull section ragged at both ends;
a bent, torn plate with holes; a kinked truss spar; or a crumpled block torn open at one corner.
They are blockout shapes. The finished field replaces them with modelled wrecks.
Some pieces are sized to the wreck kit's hull-derived sections, so a real hulk drops in at its own
proportions: the Falcon (P)'s cover is 230 x 201 x 112 m (an Onslaught's front section), the
Wolf (P) B's is 103 x 90 x 45 m (a Colossus hold section), one field section is 112 x 89 x 43 m
(a stripped Colossus), and a free-standing Onslaught hulk, 293 x 230 x 112 m with its stern torn
off, lies in the field's north-west corner, clear of the launch corridors and both cameras.

The pirates hide in the field the way a crew would:
- **The station hides only two:** the Manticore (P) in its south hangar and the Colossus Mk.III
  inverted under its biggest hull.
- **Every other hull lies out in the field,** across the whole of it and at every depth, behind a
  wreck at least her own size set between her and the convoy's line of sight, which comes from
  above and from the platform's north side. That wreck is a hulk, a cargo block or a slab of deck.
- **Four are half-seen in S3,** so S4 reveals what the audience half-saw. The noses of the Hound
  (P) C, the Wolf (P) B and the Kite (P) B show past the ends of their hulks, and the drive
  cluster of the Hound (P) A shows past her block.
- **`--check` measures it** (`hide_lines`): for each pirate, her cover's volume against her own
  bounding box (every cover is 1.6 to 8.5 times hers), and how much of her the convoy can see
  before the drives light, from rays cast over her bounding box. It flags EXPOSED, SMALL, the
  intended HALF-SEEN, and HIT for any launch that passes through a wreck or the station.

The launch is one motion across S3 and S4 (`launch`):
- Drives light in a ripple from the Falcon (P), 33.8 s to 38.2 s, in many places across the field.
- Each hull eases out along her nose past her wreck for 4.6 s, then turns onto her climb as she
  gains speed, then burns toward the column. No launch passes through a wreck: the field's seeded
  wreckage is kept out of every launch corridor.
- S3 stills it at 34 s and S4 at 40 s, from the same flights, so the spread grows on screen.

Assumptions the story does not give (owner to confirm or change):

| | Value | Why |
|---|---|---|
| Asharu radius | 5,000 km | A dry world a little smaller than Earth; keeps the limb curving in frame |
| Convoy altitude | 250 km | Low orbit. About 6 km/s; the desert slides past below at about 1.3 degrees a second |
| Horizon | 17.6 degrees below level | Wide shots tilt down so the limb sits high in frame; looking down, the desert fills the frame |
| Sun | low, 16 degrees up, ahead and to the south-east | Long shadows in the dunes; ships side-lit, never backlit silhouettes |
| Orbit direction | prograde to the north-east | So the fall runs on toward the northern canyons, about 1,500 km downrange |

## Orbital geometry

The convoy is in low orbit over the day side. It has slowed to set up its
descent window, which is why it cannot simply outrun anyone. It has just passed
above the derelict platform, which sits about 4.5 km off: 1.8 km lower and 4 km
astern (coordinator, 2026-10-22, brought in from 10 km so the pirates can reach the
column in the time the cuts give them). A lower orbit is a faster one, so a raider that lights its drives there catches up
from behind and below without having to chase.

Every fleet shot happens inside a box a few kilometres across. Ranges between
ships are 0.3-5 km, which is where Starsector's own weapon ranges put a fight at
these hull sizes. Long views (S1, S12) show the planet's limb; close views put
the ships against the desert below. `01-orbit-geometry.png` in each review run
shows both scales.

## What this cinematic is for

The owner's direction (2026-10-20): **make it a spectacle.** The player wants to see combat, and
this is the one place they get three things the game never shows them:

1. **The sprites they know, fighting in 3D.** Every hull here is one the player has flown or
   fought as a top-down sprite. Seeing a Lasher roll onto her back to bring her guns down, or a
   Wolf blink through a gap, is the payoff.
2. **A voiced cast in the universe.** People on the radio, under fire, in their own working
   voices. The game is otherwise text.
3. **A fight over a planet.** The game's battles happen on a flat starfield. This one has the
   desert curving away beneath it and a limb in the sky.

So the fight is a **brawl with a back-and-forth**, not a massacre: the convoy draws blood, and
the pirates pay for their prize. The tone stays grounded. It is still a raid (about a dozen
hulls a side, frigates and destroyers, no capital line), with working voices and no heroics
in the speeches. The spectacle is in the fighting, not in its size.

### Fought the way Starsector fights

The brawl reads as Starsector because it moves to the game's own rhythm:

- **Shields are a cost, not a wall.** A shield goes up to soak a burst and comes down again to
  bleed off flux, so the screen is full of shields flicking up and down. A ship keeping her
  shield up too long stops shooting back.
- **Venting** is a visible decision: the ship glows, can't shoot and can't shield for a few
  seconds. Venting at the wrong moment is how a ship dies.
- **Overload** is the turn of a fight: pressed past its flux, a shield pops in a burst of arcs and
  the ship is open. Most kills here go pressure, then overload, then hull.
- **Ship systems from the game's data:** the Wolf's Displacer blink, the Lasher's Ammo Feeder
  burst, the Manticore's canister flak, the Hound's flares (she has no shield), and the
  Colossus's and Mk.III's flares.
- **Shields from the game's data:**
  - front shields: Lasher (210 deg), Wolf (150), Falcon (270), Colossus and Mk.III (150);
  - omni shields: Manticore, Kite, Tarsus, Phaeton, Mule, Buffalo, Dram, Shepherd;
  - no shield: Hound and Buffalo Mk.II, which is why they rely on armour and flares.

### Space has stars

The owner's call (2026-10-21): a camera exposed for sunlit hulls would lose the stars, but space
reads wrong without them. Every key frame has a starfield, faint and sparse, mostly dim with a
few bright, warm and cool. It is seen only by the camera, so it lights nothing. It is off on the
roster sheets.

### Ships in motion

The owner (2026-10-21): no fleet flies in lockstep or on rails. Each ship's spec is its station,
and `motion.py` moves the ship about it by its mass. It weaves across and over its line, its
spacing to its neighbours breathes fore and aft, its heading follows the weave, it banks into
each turn and its throttle surges as it closes up. Frigates jink hard (about 25 degrees of
heading and 30 of bank); destroyers swing wider and slower; freighters and the Colossus barely
wander. Civilian hulls move a class heavier. The motion is seeded by the ship's key, so it is
deterministic, ships of one build never move alike, and each key frame stills the shot's moment
in it.

### The battle flies (owner, 2026-10-22)

Every ship in every key-frame shot flies, and every shot but S12 has a camera move. A key frame's
pose is where that motion passes at its key time, so a lit still is a sample of the motion the
preview plays, and the stills did not move when the motion went in (`--check` is unchanged).
- **Key times:** S1a 10 s, S1 16, S3 34, S4 40, S6 56, S6b 62, S6c 72, S6d 77, S7 86, S8-S9 95,
  S9b 105, S10 114, S12 130.
- **Speeds are relative to the convoy**, whose co-moving frame the battle is staged in: a ship
  on station is at rest in it, a raider closing from astern flies forward through it at her
  class's speed (frigates about 210 m/s, destroyers 170, cruisers 130), a ship falling out drifts
  aft. The planet holds still beneath, in the finals and the preview alike (owner, 2026-10-27).
  The motion lint holds every hull to her class's top speed and acceleration (`PACE_LIMITS`; owner,
  2026-10-27: "It shouldn't go accelerating out of the scene insanely fast"): frigates 260 m/s and
  150 m/s2, destroyers 220 and 100, cruisers 170 and 60, freighters 80 and 20, drones 180 and 300,
  acceleration taken over a quarter second so a hard turn counts. Designed moves past them are in
  `PACE_EXEMPT` with their reasons.
  The activity lint fails a ship that sits in frame through a battle beat doing nothing (owner,
  2026-10-27: "Everyone just decides to be passive while they aren't the star of the shot?"): over
  1.5 s in frame with no turn of heading, pitch or roll faster than 1 deg/s, no change of speed
  over 5 m/s2 and no fire of her own (`activity_lines`). A pirate joins the fight at her launch, a
  convoy ship at contact (50 s). A ship meant to hold still is in `DESIGNED_IDLE` with its reason.
- **Bending the truth where it cannot be seen** (owner, 2026-10-27: "Note some "bending" of the truth
  for a better shot is warranted. If the camera is close to the action though we run the risk of the player
  seeing the mistake"). The realism lints (pace, activity, firing arcs) fail only where the mistake
  reads: the ship at 150 px of length or more (a 4 m turret is then 6 px), or 60 px in the middle half of the
  frame; anything smaller, or off frame, is logged as a cheat with its size. A tracer is judged by the streak
  (owner: "Right, the viewer would spot tracers that obviously went through a hull"): one drawn through any
  hull, the shooter's or another's, fails wherever the crossing is in frame (`hull_cross_lint`).
- **The pack's climb is flown as its classes can fly it** (owner, 2026-10-27: "I want things done
  right"). Each raider eases out of the field as S3 shows (unchanged), then flies a smooth path
  toward her S6 run (`climbed`): no faster than `CLIMB_SPEED` (90% of her class's top speed) and
  no harder than `CLIMB_ACCEL` (half its acceleration), her turn building and letting off, her
  nose coming onto her run's attitude by 50 s. None of the five S6 shows can reach the position S6
  first had her in from 4.5 km by then, so **the contact comes later**: S5 shows the Manticore (P)
  and Wolf (P) B still climbing, and S6 stages each run back by her shortfall (`CLIMB_SHORT`: the
  Buffalo Mk.II about 260 m, Wolf B and the Manticore about 740, the Falcon 940, Wolf A 1.9 km).
  The pack opens S6 2.8-4.0 km astern of the Tarsus. Longer shots (everything after S4 retimed)
  and a closer platform (no help: S6 places the Falcon and the Buffalo off its line) were the other
  options. Eight debris pieces that lay in the new paths are gone from S3's field (`FLOWN_CLEAR_M`);
  the field is otherwise laid as it was (`LAID_CLIMB_SPEED`).
- **A flown ship (`motion.keyed`, `fly()`)** passes through timed attitude keys and flies where
  her nose points, so the path follows the manoeuvre:
  - the roll-overs the plan calls for: Lasher B's 150 to starboard and the merchant Wolf's to
    port in S6b, the Hound's 110 in S6d, the lead Lasher's dive on her back in S9b;
  - attack runs, breakaways, and the raiders jinking as hard as their class throws itself about
    (`jinking()`);
  - hits that shove and tumble a ship and leave her throttle (drives shot out), recoil that kicks
    a firing ship's nose (`motion.recoil`), and the Wolves' (P) Displacer blinks in S6c;
  - the drives follow the thrust model (`motion.A_FULL`): idle while she coasts, burning in
    proportion to her acceleration, speeding up or hauling round a turn. Raiders are still gaining
    on the column, so they burn. A ship whose drives are out keeps the throttle the story gives it.
- **Forward motion reads** (owner, 2026-10-22): in the convoy's frame a ship on station is parked,
  so the speed is shown by what moves past: the desert under the battle at the true orbital rate,
  escorts working up and down the column (`ESCORT_DRIFT`), raiders closing, and cameras that drop
  back along the column or pass ships. The preview's ground carries seeded fine relief and specks
  near the battle, because the game's map alone is too coarse to show the speed, and a star dome
  for the pans.
- **Riding:** the seated boat rides the Tarsus's slow roll in S7, Morrow and the Borers ride the
  wallowing Colossus in S8-S9, and the prize and the docked Kite ride the Mk.III in S10
  (`motion.carried`, `motion.riding`).
- **Cameras (`motion.camera`)** run through timed keys, and a key's position or aim can follow a
  ship (`track()`). Handheld drift, and the jolt a hit gives the aim (`kicks`), are zero at the key
  time, so they never move a still. The owner (2026-10-22): no shot ends with its camera where it
  started except S12's hold, and focal-length zooms tell the story: a long lens finding a far
  contact (S3, S4, S6), a zoom out for scale (S1, S9, S10). The shot list's camera-move column has
  each shot's move.
  - S1a stays the player's view, straight down and never turning: the optic zooms out, then in
    on the convoy with its focus hunting, and the picture cuts to S1's action camera.
  - S3 creeps in on the platform, then onto the Falcon (P) as her drives light.
  - S4 starts wide off the pack's flank as it climbs out of the platform, then crash-zooms onto
    the Falcon (P).
  - S6 pushes in, handheld. S6b chases Lasher B over her roll. S6c tracks aft along the column.
  - S6d is handheld, close behind the Hound. S7 eases in behind the Tarsus.
  - S8-S9 pulls back from the shot-out pod to the wide. S9b travels with the Lasher's run.
  - S10 cranes down after the falling Colossus. S12 holds.
- **The motion is accepted on previews** (`--motion-preview`, light lane), and only then
  sampled for lit renders.

### Morrow's drones

Morrow is a drone tender, so her four Borers work wherever the story lets them. Each is placed
through ShipFx, so each blinks its beacon, pulses its lift jets and fires its mining laser on its
own. Placements:
- **S8-S9, the pod:** two Borers hold outboard of and under the Colossus's shot-out port pod,
  cutting the burning wreckage free with their lasers.
- **S8-S9, the sortie:** two more launch from Morrow's bay, one at 94.2 s from launch point 3 and
  one at 95.0 s from launch point 2, carrying the bay's motion off the deck. Each goes for the
  attacker sweeping past her.
  - The first rakes the Kite (P) as she passes (95.8-96.2 s); the second rakes the Wolf (P) on her
    port side (96.4-97.5 s). Each then turns back for Morrow.
  - **On a leash to Morrow** (owner, 2026-10-27: "It should be reactive, but not fully leave the
    shepherd and leave the shot"). She ranges no more than 300 m from her (304 m at the top of her
    turn home): an attacker past that is let go, and from 180 m out her aim bends back toward
    Morrow. Both stay in frame to the end of the shot. `drone_lint` holds every drone to her carrier's leash and her top speed, and
    holds one in frame there to the shot's end, unless the beat is listed in `DRONE_EXEMPT`.
  - Each flies her own pursuit (`sortie`): led by the closing time, turning at up to 150 degrees
    a second, jinking, banking with the turn, and every rate eased so `--check` reports no
    jumps.
  - Her drives burn with her acceleration (the thrust model). She tops out at 150 m/s, pushes at
    up to 200 m/s2 and turns no harder than 220 m/s2 sideways, so a fast Borer turns wide.
  - Wherever she passes within 110 m of a hull, her mining laser (`beam_t_ws001`) sweeps stern to
    bow down the near side, with sparks where it cuts. The preview draws the beam as a line and
    the sparks as dots.
  - After her runs she flies home to the Colossus, slowing as she arrives.
- At that range a Borer is about 9 px. It reads by its work lamps, its beacon, its drive and its
  beam.

`near()` places a craft in its parent's frame with its own attitude, so a drone stays on the
pod if the Colossus is re-posed.

### The boarders' boats

The owner (2026-10-22): the boarders cross in small boats, the company's Hermes, launched by the
Kites (P).
- **Launch is never shown.** A boat leaves from under its Kite's belly, where the camera cannot
  see it, and enters the frame already flying.
- **The run is `motion.boarding_run`:**
  - a curve from the launch point to a seat on the prize's deck;
  - a burn out along it, a coast with the drives idling;
  - a yaw end for end, then a braking burn against its motion;
  - so it comes in tail first over the prize's stern and sets down facing aft.
  - `boat()` composes it from the mother's and the prize's stations, so it follows either if
    re-posed, and an animation plays the same run. Given the prize's flight (`carrier`), the
    run follows the prize as she moves.
- **Each boat is its own ShipFx copy** (own hull and kit), so drives, gear and doors key
  per boat. Drives burn on the run, gear comes down on the brake, doors open once down.
- **Livery:** the Hermes is the company's own boat. The pirates' copies are greyed and
  dulled on the appended copy (`livery='scrap'`); the pinned build is never edited.
- **S7:** one boat down on the Tarsus's deck with its doors open, one braking in over her
  stern, one just out from under the starboard Kite. At that range a boat is about 25 px.
- **The seat cast:** `ASHARU seat`, under `--stage-only`/`--fx-check`, casts down through a
  seated boat's gear soles onto the prize. It prints each sole's gap, so a boat floating over
  the deck or sunk into it shows without a render.

### Continuity

Fixed before any production render (owner: make continuity changes without asking):

- **S6c, S7:** the Tarsus's drives are shot out, so her fire burns up out of breaches and her
  smoke rises. Neither streams aft like a drive plume.
- **S9:** the Kite (P) driven off is Kite B. Kite A stays alongside the Tarsus and is docked
  with her in S10.
- **S10:** the Falcon (P) carries the hit she took in S9b: fire and smoke on her flank.
- **S6b:** the escorts' victim is a third Hound (P). The stand-ins stay distant.
- **Line of sight:** every shot fires from a gun that bears on the target and has a clear line
  to it, checked by ray-cast in Blender (`--stage-only --los`). Two shots were firing through
  their own ship: the merchant Wolf through her bridge dish in S6c, the lead Lasher through her
  own third turret in S9b. They now fire from a gun with a clear line.

## Framing: a comms recording

The owner's direction (2026-10-20): the fight happened cycles ago, so the audience **hears it
before they see it**, as if listening to a recording of the comms traffic. The picture follows
once time and place are established.

- **It opens on the recording, not the picture.** The screen shows a playback: channel list,
  waveform, timestamp, source (see On-screen place and time). Over it, the S1 traffic plays as
  audio only: the Colossus calling the window, the escort lead closing up the column. Ordinary
  work, voices only.
- **Then the camera jumps to the action.** On a line (the escort lead's "Escorts, close up the
  column"), the recording screen gives way to S1, and the film shows what the voices describe.
  From there the picture is a reconstruction, and the recording carries the story.
- **The edit follows the radio.** A shot changes when a new voice keys the mic: the camera goes
  to whoever is talking, or to what they are talking about. That gives the brawl its rhythm, and
  explains why the camera can be anywhere.
- **S3 is the exception.** The pirates are running dark and not transmitting. The convoy's
  channel is quiet, chatting about the window, while the picture shows the trap: the audience
  sees what the recording could not hear.
- **It ends on the recording** (owner, 2026-10-22: "we started by hearing a log and we end with
  hearing the comms log"). In S11 the plasma takes the channel: the picture breaks into the
  recording screen's static, the waveform flattens, and CARAVEL's and MORROW's channels drop out
  one at a time. S12, the two trails, plays in silence: an image no recording made. Then S13 cuts
  back to the log. The survivors and the pirates are still on the channel; the terminal weighs the
  event as a local matter, stamps it **NOT ESCALATED**, archives it, sweeps on to other systems and
  shuts down, not on a held frame.
- **When it plays (owner, 2026-10-20): when the player arrives at Asharu,** before the Salvage
  Rights briefing. It sets the scene. Vale's "three cycles back, two fleets fought it out in low
  orbit" then confirms something the player has already seen, rather than introducing it.
- **Nobody in the story recovers it.** The recording is the film's way of telling, not an object
  in the plot: no source, no device, nothing the player finds or keeps. It sets the scene, and
  the story never refers to it again.
- **What it may and may not reveal**, against the arc's reveal order (`shepherd-recovery-dialogue.md`):
  - it shows the wreckfall, the Colossus and her escorts coming down, which the briefing reveals
    next anyway;
  - it never shows or names the hold, its contents, the owner, the buyer, or Basin, which are
    withheld in the arc, some of them permanently;
  - **the convoy side may allude to something special in the Colossus** (owner, 2026-10-22:
    "This is scavenger lore for the 'other' parties. The Colossus has something 'special' in it.
    We might want to allude to that in the cinematic for our writer pass"). The escort lead told to
    hold her at any cost, a master unwilling to abandon ship, a guarded line on the convoy channel.
    The allusion is the writer's, in the comms; the picture shows nothing of the hold;
  - **the pirates know nothing of it** (owner, 2026-10-22: "I don't think the pirates should be
    aware"). To them she is an ordinary prize, and they take the Tarsus as planned;
  - **the wreckfall is scavenger lore about the other wrecks.** The break-up across the surface
    (S12) is where the field's other salvage comes from; the Colossus is the one wreck that matters,
    and she comes down mostly whole, her hold sealed, for Basin to find;
  - it names Morrow on the radio as she goes down with the Colossus. That is deliberate: the
    player later reads her name plate in Basin's yard, and recognises her.

## How the convoy is broken, and why the pirates do not follow

1. **Caught slow.** The convoy is committed to its descent burn: slowed, low,
   with nowhere to run.
2. **Ambush from behind and below.** The pirates have been lying in the
   platform the convoy has just passed, running dark: reactors down, no
   transponders, no drive light. Cold among the platform's stripped hulks,
   each one reads on a scan as one more piece of salvage. Starsector players
   know the move as Go Dark. No asteroid field or other hiding place is
   invented: the platform is the game's own. They light up and climb out of
   it, closing from astern.
3. **The screen goes first, but not cheaply.** The Manticore (P) and the Falcon (P) open at long
   range. The Phaeton tanker, at the convoy's tail, goes up. A Lasher burns.
   The lead Lasher turns back into the fire and is shot up. Then the escorts
   answer: Lasher B and the merchant Wolf roll over onto the climbing raiders and
   break up a Hound (P), and the convoy's Hound kills a Hound (P) at the
   rear before the second one cripples her.
4. **The tail is cut off.** The two Wolves (P) blink through the column between
   the freighters and rake the Tarsus's drives as they pass. The Tarsus, astern of the Colossus, has her drives
   shot out and falls out of the column. Kites (P) lie alongside her with the
   boarders. The rest of the convoy runs on ahead.
5. **The Colossus will not be taken.** The Colossus Mk.III, an armed Colossus
   like her, closes to take her too and shoots out her port tug pod. She fights
   back with flares and both turrets and drives off a Kite. Morrow, under her
   keel, holds her up with her own drives. The smoking lead Lasher makes one last
   run over the Falcon (P), presses her shield into overload and puts her guns
   into the flagship's flank before the Falcon's broadside finishes her.
6. **She falls.** A crippled, slowed freighter in a 250 km orbit does not stay
   there. With one pod gone and the other failing, she loses her orbit, and
   Morrow goes down with her.
7. **The pirates take what they have.** The captured Tarsus is clamped to the
   Colossus Mk.III. A burning freighter heading into the atmosphere, with no
   way to get her back up, is not worth chasing. The rest of the convoy (Mule,
   Buffalos, Dram, the surviving escorts) gets away while the pirates are busy
   with their prize. Two plasma trails run north toward the canyon country.

**The tally**, so the back-and-forth stays honest:

| | Convoy | Pirates |
|---|---|---|
| Destroyed | Phaeton | Hound (P) C (S6b), Hound (P) A (S6d) |
| Crippled or lost | lead Lasher, Lasher A, the convoy's Hound; Colossus and Morrow fall | Falcon (P) hit hard; Kite (P) A driven off |
| Taken | Tarsus | |
| Got away | Mule, both Buffalos, Dram, Lasher B, the merchant Wolf | the rest, with the Tarsus |

This is the owner's "they got their prize above the atmosphere". It keeps the
story's facts: the Colossus's sealed hold is never reached, because she is the
one ship the pirates fail to take. Whether her crew dive her deliberately or
simply lose her is still open; the plan draws it as losing her.

## Fleets

Sizes come from the finished models where they exist. Unbuilt hulls are sized by
`ship_scale.py` option C from the game sprite's plan area (`roster.py`, about
+-8% in length until each is built and measured). For scale: Hound 61 m,
Colossus 108 m, Onslaught 301 m.

### Merchant convoy (13 hulls: 8 freighters and tankers, 5 escorts)

| Hull | Role | Length | Against known ships | Model |
|---|---|---|---|---|---|
| Colossus | the heavy freighter that falls | 108 m | the reference | **finished** |
| Shepherd "Morrow" | drone tender, the convoy's repair ship; falls with the Colossus | 67 m | a little longer than a Hound | **finished** (Ko Combine livery) |
| Borer x4 (Morrow's) | the Shepherd's built-in mining drones, working the convoy's damage | 3.6 m | a small car | **finished** (Drive `builds/borer/8f950d18e`) |
| Tarsus | freighter: boarded and taken, the pirates' prize | 94 m | 0.9 Colossus, much slimmer | **finished** (accepted by the owner), clean and boarded (drives dead) states |
| Phaeton | the convoy's tanker, destroyed in the opening | 77 m | 1.24 Hounds, long and thin | **finished** (both states accepted by the owner): destroyed is an eight-blast cook-off over about 6 s |
| Mule | armed freighter; gets away | 104 m (est.) | about a Colossus | stand-in |
| Buffalo x2 | small freighters; get away | 58 m (est.) | a Hound | stand-in |
| Dram | small tanker; gets away | 48 m (est.) | 0.8 Hound | stand-in |
| Lasher x3 | escort lead and escort frigates | 90 m | 1.5 Hounds | **finished** (independent livery) |
| Wolf | point escort frigate | 70 m (size not settled) | 1.15 Hounds | **finished** (merchant livery of the Wolf build) |
| Hound | rear escort | 61 m | the reference | **finished** (independent livery) |

### Pirate raiders (11 hulls)

| Hull | Role | Length | Against known ships | Model |
|---|---|---|---|---|---|
| Falcon (P) | flagship, light cruiser | 174 m | 1.6 Colossus; 0.6 Onslaught | **finished** (accepted by the owner; faded red kept) |
| Colossus Mk.III | the prize ship: tries for the Colossus, takes the Tarsus | 110 m | the Colossus's twin | **finished** (accepted by the owner); grapples from stowed to clamped, fitted to the Tarsus |
| Manticore (P) | long-range destroyer that breaks the screen | 91 m (0.59 m/u; 95 m across the pods) | 0.85 Colossus | **finished** (textured; no shield yet) |
| Buffalo Mk.II | destroyer | 87 m (est.) | 0.8 Colossus | stand-in, kept distant |
| Wolf (P) x2 | fast frigates that cut the column | 70 m (size not settled) | 1.15 Hounds | **finished** |
| Hound (P) x3 | frigates | 61 m | the reference | **finished** (pirate state of the Hound) |
| Kite (P) x2 | boarding shuttles | 52 m | 0.9 Hound | **finished** |
| Hermes x3 (the Kites' boats) | boarding boats, launched from under the Kites | 9 m | a bus | **finished** (the company's accepted Hermes, Drive `builds/hermes/70f1f58eb`, greyed on the copy; a real pirate livery would be a new build) |

All the pirate hulls are vanilla `pirate_bp` skins or pirate hulls (Colossus
Mk.III), plus the Buffalo Mk.II, which pirate fleets field. Left out on purpose: the
phase frigates (a phase ambush is a different film from this one), and the
Venture Mk.II and Atlas Mk.II, which would make the raid read as a pirate
armada.

## Gunnery: ships roll to fire

Every hull here carries its guns on its dorsal deck. The turrets train in azimuth only, through
the arc in the build's manifest (or the game's weapon slot), and reach from just below the
deck plane (-10 degrees) almost straight up. Fixed hardpoints (missile tubes and the Kite's
launchers) point along the keel, and a guided missile can leave one up to 90 degrees off its
target. So a ship firing at something below her deck has to roll or dive to bring her deck to
bear, and that rolling shapes the fight:

- S6: the lead Lasher banks her deck down onto the Manticore (P) as she turns back.
- S6b: Lasher B rolls 150 degrees, onto her back and nose down, to fire into the climbing Buffalo
  Mk.II; the merchant Wolf rolls the other way, 140 degrees.
- S6c: Wolf (P) B banks hard through the gap, 60-70 degrees, to keep her deck on the Tarsus.
- S6d: the convoy's Hound rolls 110 degrees to put her deck on the Hound (P) climbing beneath her.
- S7: the Wolf (P) makes an inverted gun run over the Tarsus, rolled 110 degrees and nose down.
- S8-S9: the Colossus Mk.III lies about 64 degrees on her side to bring her broadside down on
  the Colossus, grapples half-swung, before she rights herself to take the Tarsus instead.
- S9b: the lead Lasher crosses over the Falcon (P) rolled 160 degrees, on her back, guns down.
- The merchants keep upright: the pirates climb at them from below, so the merchants' guns,
  firing down, are the ones that struggle (the Colossus fires up at the Mk.III easily).

`python battle_blockout.py --check` tests every tracer and missile in every key frame against
the shooter's real mounts and prints "bears" or "CANNOT BEAR" with how far short it is.

## Shot list

About 2 minutes 15 (134 s): 10 s of recording, 40 s of setup and ambush, 60 s of fighting, 24 s of the fall. **K** marks a shot with a blockout key frame. Shot names with a letter (S6b) were added on 2026-10-20 and are renumbered once the list is settled.

| Shot | Length | Picture | Ships seen closest | Camera | Camera move |
|---|---|---|---|---|---|
| S0 | 31 s (-21 to 10) | **The recording, on a Hegemony security traffic terminal.** A CRT boot to the Hegemony crest, then a staged drill-down: the sector's tracked fleets, Corvus with its holders and local traffic, then the flagged contact CV-2215 at Asharu, where the convoy channel is tapped. The S1 traffic plays over it as audio only. From 8.7 s the camera pushes into the optic, the 2D layout of the convoy over Asharu, and on the escort lead's "close up the column" it cuts to picture. The pre-roll runs in negative time, so nothing after S0 moves. | (none) | (a screen, not a camera) | **Timed 2D plate** (`recording_plate.py s0`, frames numbered from the shot start: local 504 is t = 0): boot -20.6 to -11.4 s, sector to -6.15, Corvus to -1.6, the event page from -1.45, the CARAVEL channel lit 1.8-6.4 s and WARDEN LEAD 6.9-9.7 s, then the cut to picture at 10 s. |
| S1a **K** | 6 s | **The view the player knows** (owner, 2026-10-21). The first picture after the recording looks straight down on the convoy, prograde up the frame: the ships small against the desert far below, the way the player has always seen them. Then the camera tips down and pulls into 3D for S1. | the core convoy, from above | 100 mm, straight down from 11 km | **THE SATELLITE OPTIC** (owner, 2026-10-26: "start out and focus in like how a satellite works"; no zoom out, no panning): straight down from 11 km, prograde up, held over the convoy's core and never moving or turning. Wide on the convoy small on the desert (24 mm), then stepping in (60, 150, 300 mm), the focus hunting at each step and snapping sharp. Then a cut to S1. |
| S1 **K** | 8 s | Establishing: the convoy in loose column over the day side, the limb high in frame, the desert sliding beneath. Ordinary work. | the whole convoy, small | 35 mm, high off the north flank | **ONE TAKE WITH S2** (owner, 2026-10-26: no zoom after S1a's; "the same camera"; "move the camera in the scene, not jump"): a still observer 2.5 km off the north flank on one 45 mm lens opens up on the limb, the column low in frame, and tilts down onto the Colossus and Morrow; from 18 s it travels in along its line of sight toward her port bow. No zoom, no shake. |
| S2 | 6 s | Alongside the Colossus. Morrow rides off her starboard quarter; a Lasher slides through the foreground. | Colossus, Morrow, Lasher | 28 mm, tracking alongside at 300 m | **S1's take, continued**: the camera arriving off her port bow (800 m at the cut) carries its speed over it and settles alongside at about 520 m as Lasher A crosses in front of her; Morrow beyond her quarter. 45 mm, no zoom. |
| S3 **K** | 8 s | Running dark. Long lens back down at the derelict platform: dark, stripped, hulks in its bays; hold until it reads as junk. Then one hulk shows a running light, reactor glow behind a bay, a second, a third. Drives light and the first ships ease out. The audience sees the trap before the convoy does. | the platform, the Falcon (P) | 135 mm from the convoy's tail (longer, about 300 mm, is worth trying) | **ONE TAKE WITH S4, HELD AT THE TAIL** (owner, 2026-10-26: "S3/S4 the same camera in a single take"; "maintain the view of S3, us the viewer as the fleet about to be chased"): from the convoy's tail the lens finds the platform (32 to 48 mm) and holds until it reads as junk; the Falcon (P)'s drives light. The camera does not travel. |
| S4 **K** | 6 s | First contact: the hulls easing out of the platform and turning onto their climb, the last drives still lighting, the spread opening on screen, the desert behind them. Continuous with S3. | Falcon (P), Wolves, Hounds (P), Kites (P) | 28 mm, off their flank about 1.5 km out | **S3's take, continued**: still at the convoy's tail on 48 mm, the pack easing out of the platform and climbing toward the column, the view turning a little with the Falcon (P). No cut from S3, no zoom. |
| S5 | 6 s | The opening: the Manticore (P) fires at long range, and rounds and missiles cross 3-4 km of space. | Manticore (P) | 85 mm along the line of fire | **Along the line of fire, ZOOM OUT:** from 900 m behind the Manticore (P), long on her as she fires (130 mm), then widening down the line as the Hellbore slugs and the Harpoon salvo cross toward the column's tail (85 to 50 mm). She is still on her climb (`climbed`), and S6 picks her up where it leaves her, so the cut keeps her where she is. Blocked for the preview only (`m5`). |
| S6 **K** | 8 s | The screen breaks: the Phaeton tanker is struck in the first 2 s and cooks off through the rest, a Lasher burns, and the lead Lasher turns back into the fire and is hit. The raiders are 3-4 km astern, so only the Manticore (P)'s Harpoons reach; no gun on either side fires, and the convoy jinks under the salvos. | lead Lasher, Phaeton, Lasher A | 35 mm, looking aft along the convoy | **ZOOM OUT along the salvo:** long on the Manticore (P) as she fires (85 mm), widening as her missiles reach the Phaeton, to the key framing; then a handheld push, jolted by the hits. |
| S6b **K** | 8 s | The escorts answer. Lasher B and the merchant Wolf turn back and roll onto their backs over the climbing raiders. A Hound (P)'s bare armour soaks it (no shield), then the Ammo Feeder burst breaks her up. First blood to the convoy. | Lasher B, merchant Wolf, Hound (P) C | 50 mm, above and behind the escorts, looking down past them | **Chase:** behind Lasher B over her roll, looking down past her (40 to 60 mm); jolted as the Hound (P) breaks up. |
| S6c **K** | 8 s | The column is cut. Tracking alongside at about 300 m: Wolf (P) B blinks (Displacer) into the gap between the freighters, then rakes the Tarsus's drives; the merchant Wolf chases her through. Wolf (P) A, whom the climb leaves to port as S6 cuts, runs the column's far side under the Colossus's guns, shield up. | Wolves (P), Tarsus, merchant Wolf | 35 mm, tracking alongside the column | **Tracking dolly:** drops back along the column at about 70 m/s, the Wolves (P) passing the lens. |
| S6d **K** | 6 s | The rear guard. The convoy's Hound rolls over to face two Hounds (P) climbing at her. No shields on either side: armour, flares and guns. She kills the first; the second's fire takes her drives, and she falls back on the column trailing flares. Her holding action fails (owner, 2026-10-26): behind the two Hounds (P) the two Kites (P) come up out of the background and fly by beyond her fight, never across her path, on their run at the Tarsus she is guarding. | Hound, Hounds (P) | 35 mm, close behind the Hound | **Handheld chase:** close behind the Hound through her roll-over (30 to 40 mm), jolted by her kill and her hit. The camera is the owner's ("a good action shot"); the Kites (P) are routed through its frame. |
| S7 **K** | 8 s | Boarding the Tarsus: drives shot out, fallen out of the column, Kites (P) alongside, their boats bringing the boarders over to her deck, a Wolf (P) covering; the convoy runs on ahead. The rear guard carries on astern (owner, 2026-10-26: continuity of the Hounds): the convoy's Hound, drives hit, limping back toward the column, the surviving Hound (P) on her tail and firing. Option C (owner, 2026-10-27: "Confirm option C for S7 to S8 as it plays in Latest"): beyond the Tarsus the Colossus Mk.III dives in from astern and above onto the Colossus, already rolled 68-80 deg onto her side: her guns are dorsal, so only her starboard deck turned toward the Colossus lets them bear on the port pod (owner, 2026-10-27; the firing-arc lint). The Colossus banks 20 deg to lift her port turrets onto her and answers into her belly. Rolled, the Mk.III's port bow turret (t10) would fire across her own bow, so it falls silent from 83 s, Morrow turning with her under her keel. | Tarsus, Kite (P), boats, Wolf (P) | 35 mm, from behind the Tarsus | **ORIENTING ORBIT, then PUSH-IN** (owner, 2026-10-26: "S6d to S7 is jarring"; the Kites (P) "fly past to launch their shuttle intrusion"): opens forward of the Tarsus looking aft at the rear guard S6d left; the Hounds (P) have drawn her Hound off, and through that fight the two Kites (P) come in from astern, overtake her and brake onto her beams, their boats dropping as they slow (84.8-85.8 s). The camera circles her port side, panning forward onto the convoy running on ahead (28 to 30 mm), then pushes in to her deck as the first boat sets down (34 mm, which keeps the Mk.III whole at the top of frame as she comes down). |
| S8 | 6 s | Close on the Colossus's aft: the port tug pod is shot out, the drives stutter, debris. | Colossus | 50 mm, close | **Pull back:** from close on the shot-out pod (50 mm), continuing into S9. The two Borers off the pod skim (owner, 2026-10-26): each strafes back and forth under the Mk.III's belly, nose on her, out of her guns' arcs, her beam trailing her travel so it rakes the plating. |
| S9 **K** | 8 s | She will not be taken: the Colossus Mk.III closes from above; she fights with flares and both turrets and drives off the Manticore (P) (the Kites (P) are on the Tarsus); Morrow is under her keel holding her up. Her Borers fight: two by the shot-out pod turn their mining lasers up on the Mk.III, the bigger threat, while two more launch and rake the Wolf and the Manticore. | Colossus, Colossus Mk.III, Morrow, Borers | 28 mm, 600 m off her port side | **ZOOM OUT:** on to the wide, then wider (20 mm) for the Mk.III's scale; jolted by the pod and the Manticore's hit. |
| S9b **K** | 8 s | The lead's last run. Smoking since S6, the lead Lasher dives over the Falcon (P) on her back. The Falcon's broad front shield takes it and flares, then overloads in a burst of arcs, and the Lasher's guns walk down the flagship's open flank. The Falcon's broadside answers, and the Lasher tumbles away burning. | lead Lasher, Falcon (P) | 28 mm, close, just above the Lasher | **Pass-by:** the camera drifts while the Lasher dives past and away, panning after her; jolted by the hits. |
| S10 **K** | 8 s | The prize taken: the Tarsus clamped to the Colossus Mk.III; far below, the Colossus nose-down and losing her orbit, Morrow with her. | Falcon (P), Colossus Mk.III, Tarsus; Colossus and Morrow below | 35 mm, behind and above | **ZOOM OUT to scale:** long on the clamped prize (85 mm), widening and tilting down to the Colossus falling away (24 mm). |
| S11 | 8 s | Entry: from just behind, plasma builds round both hulls; Morrow drifts apart from her. The radio breaks up, the picture breaks into the recording screen's static, the channels drop out, and END OF RECORDING. | Colossus, Morrow | 50 mm, chase | **Chase:** just behind and above the Colossus, closing a little and shaking harder as the plasma builds (42 to 38 mm), framed to keep Morrow as she drifts apart. From 124.5 s the picture breaks into the recording screen's static, and CARAVEL's and MORROW's channels drop out. END OF RECORDING is in S13, back on the log. Blocked for the preview only (`m11`). Entry plasma is **new effects work**: the preview draws it as a sheath cone from each bow, building from 118 to 124 s. |
| S12 **K** | 12 s | **The wreckfall.** After the recording breaks up, in silence: the Colossus and Morrow come down belly first, tilted back as if still trying to climb, each burning in plasma shaped by her own hull (`swfx_entry.sheath`): hot on the front and belly, flames peeling off the windward edges into a smoke wake. Around them, thin and behind, four burning chunks and ten faint streaks of the ships the battle lost. Look passed (owner, 2026-10-22, close still 9: "this would work enough for me. Not perfect but passable"). | colossus, morrow, the wreckfall | 50 mm at the key, from the pirates' orbit | **Tracking push-in:** rides down with the Colossus, 40 mm to the plate's 50 mm at the key, on to 85 mm holding both hulls. **Then the pull-back** (owner, 2026-10-26): from 134 s the observer holds where its ride down left it and zooms back out (85 to 18 mm) to the wider planet, the two trails falling into it, before the log returns at 138 s. |
| S13 | 24.5 s (138 to 162.5) | **Back on the log.** The terminal again, with CARAVEL's and MORROW's channels dark. The survivors call each other; Warden gives up on Caravel and the Morrow and reads the pirates as content with what they caught (owner, 2026-10-22: no pirate sign-off). It opens inside the optic and pulls back out to the whole screen (138.25-139.4 s); the optic keeps the fight's remains: the wrecks marked where they died with debris falling to the limb, SOBIESKI among the pirates, and CARAVEL and MORROW arcing down over the limb, their tracks projected to a crash site. The terminal weighs it local, with no outside faction, and stamps it NOT ESCALATED; it pulls back to Corvus, where the patrol route is redrawn with Asharu left outside it, archives the event on the sector view, sweeps on to other systems, and shuts down to a CRT tune-out. | (none) | (a screen, not a camera) | **Timed 2D plate** (`recording_plate.py s13`), synced to `radio_log.CLOSING`: NOT ESCALATED from about 145.5 s, Corvus 149.15, sector 152.75, ARCHIVED 153.4, the crest back 157.7, tune-out 161.0-161.9. |

## Radio traffic (draft)

The convoy and the pirates talk throughout, as captions with a speaker tag over the picture.
That is the arrival cinematic's caption track: `CinematicShot` captions name a `strings.json`
key and a speaker, and the player pages them (`CaptionPager`). No English is baked into the
frames. The motion preview burns each line in as a caption at its time, so the owner can judge
the traffic's density and whether the edit follows the radio.

It is **voiced**: an in-universe cast, which the game otherwise never has.

**The script is the voiced draft** worked out with the owner line by line on the Battle Cut page
(2026-10-28), each line voiced (ElevenLabs) and placed against its take; `radio_log.TRAFFIC` holds it,
and the table below is that list. It replaced the first draft, written only to time the edit. It
was written to three things, all here:
- **the shot timings, locked** (owner, 2026-10-22, approving the r10 key frames): the "Shot list"
  table above, where sequence time is battle time (S0 0-10 s through S13 138-151 s). Shot lengths
  and cut points stay as they are. **The comms are written to fit the locked cuts; the edit no
  longer moves to fit the lines.** Captions are drawn by the game, not burnt into the frames, so a
  change of dialogue never touches a render. Sound and dialogue are a separate pass and block no
  render;
- **the cast and callsigns**: `radio_log.CALLSIGNS`, summarised in the first rule below;
- **the rules** below, and the window each line must fit (the t column). The writer's lines replace
  `radio_log.TRAFFIC`, which the preview and the playback screens read.

**The writer's task, besides the traffic itself: the Colossus allusion.** Write the convoy side's
allusion to something special aboard the Colossus (see "What it may and may not reveal"): the escorts
told to hold her at any cost, her master unwilling to abandon ship, a guarded line on the convoy
channel. Keep it within the arc's reveal order (`shepherd-recovery-dialogue.md`): it never names the
contents, the owner or the buyer. Only the convoy alludes to it; the pirates never do, and treat
her as an ordinary prize.

Rules:
- **Callsigns, never hull classes** (owner, 2026-10-22: "Ford Focus I need you to be on my flank
  Chrysler Challenger out"). The convoy flies under one callsign, CARAVEL: the Colossus, its master's
  ship, is CARAVEL, and its merchant ships answer to their own names, after Second World War
  merchantmen (owner, 2026-10-28): the Tarsus is SOBIESKI, the Mule THYSVILLE, the Phaeton BATORY and
  the first Buffalo ALBERTVILLE. Its escorts are WARDENs, numbered, with the lead Lasher as
  WARDEN LEAD. Morrow keeps her own name, because the player later reads it on her name plate. The
  pirates have handles: BARON (the Falcon (P)), HOOK (the Colossus Mk.III), LATCH (the boarding Kite),
  ANVIL, KNIFE, CUR, SACK. A caption leads with the callsign under the registry prefix the game gives
  its faction's ships (ISS for the independent convoy; none for the pirates, who fly unregistered),
  then the hull's game designation as a small role word, never the hull name: ISS CARAVEL, Heavy
  Freighter; ISS WARDEN LEAD, Frigate; ISS MORROW, Drone Tender; BARON, Light Cruiser; HOOK, Modified
  Freighter (owner, 2026-10-26; `radio_log.caption`). The terminal uses the same registry (ISS
  CARAVEL, HSS JANGALA-2, UNREGISTERED for the pirates); its channel list keeps the bare callsigns
  heard on air. `radio_log.py` holds the
  cast, every timed line (`TRAFFIC`), and the strings.json key for each line
  (`asharuRadio<Shot>_<n>`). Those keys are not in strings.json yet, because the cinematic is not
  wired into the game.
- **A cast of voices, credited by callsign.** Each voice is a person with a character: the
  Colossus's master is steady and tired, the escort lead is clipped, the rear Hound is young, and
  the pirate flag is unhurried. But nobody gets a personal name. The story keeps Morrow's
  original crew, and every other crew, unnamed (`shepherd-recovery-dialogue.md`): Basin never
  learns who they were, and the cinematic does not tell the player either. A caption tag reads
  CARAVEL or WARDEN LEAD.
- **Flux is spoken.** Shield and flux calls are the kind of thing a player recognises from the
  game ("shields at ninety", "she's venting, now", "overload"). They also carry the
  back-and-forth.
- **Nobody names the cargo**, the owner or the buyer. The convoy side may allude to something
  special in the Colossus, guardedly (owner, 2026-10-22; see "What it may and may not reveal"). The
  pirates know nothing of it: they want what is in the holds, never say what it is, and take the
  Tarsus as planned.
- **Working voices.** Short, procedural, a little tired, and frightened only once it goes bad.
  This is ordinary traffic and an ordinary raid.
- **Silence is a beat.** S3 has no convoy traffic, only the pirate flag's "Hold", and S11-S12
  lose the radio to plasma and then to distance.

| Shot | t (s) | Speaker | Line |
|---|---|---|---|
| S1 | 1.8-7.92 | CARAVEL | All escorts, Caravel. Window opens in forty. Enjoy the view before deep space. |
| S1a | 10-12.94 | WARDEN LEAD | Nothing but void and the odd picket from here. |
| S1 | 16-21.43 | WARDEN TWO | Ha more picket "ping" to drive me crazy. Guess they keep us honest. |
| S2 | 24.6-30.17 | MORROW | Caravel, Morrow. Standing off your starboard quarter. Long ride even to the jump point. |
| S4 | 37.6-41.65 | WARDEN FIVE | Scanners picking up new contacts behind us. No identification. |
| S4 | 42-42.67 | WARDEN LEAD | Say again? |
| S5 | 44.9-49.7 | WARDEN LEAD | Wardens, come about! Caravel, full burn for the nearest picket, now! |
| S5 | 48.38-50 | WARDEN FIVE | Launch, launch! |
| S5 | 50.15-53.81 | WARDEN FIVE | Missiles inbound! Twelve! Point defence, now! |
| S6 | 53.3-54.45 | WARDEN TWO | Two's hit! |
| S6 | 53.8-56.42 | BATORY | Batory, we're hit, we're venting, we're — |
| S6 | 56.99-59 | WARDEN LEAD | Lead turning back. Cover the — |
| S6b | 58-61.77 | WARDEN THREE | Three, rolling in. Four, take her left. |
| S6b | 60.6-62.39 | WARDEN FOUR | No shield. Hit her! |
| S6b | 61.92-62.97 | WARDEN THREE | Feeders on! |
| S6b | 63.12-65.18 | WARDEN THREE | She's breaking up! One down! |
| S6c | 66.4-69.16 | THYSVILLE | Wolf coming in! Another on the far side! |
| S6c | 69.1-74.09 | WARDEN FOUR | Shields up, she blinked — where — there, she's right on them! |
| S6c | 69.8-76.09 | SOBIESKI | Taking fire aft! Flux at ninety, can't hold shield and guns — drives! We've lost drives! |
| S6c | 72.6-75.17 | THYSVILLE | She's through the gap! She's in the column! |
| S6d | 74.3-75.04 | WARDEN FIVE | Two on me — |
| S6d | 76.9-79 | WARDEN FIVE | One down - two passed through. |
| S7 | 81.21-83 | WARDEN FIVE | They're heading for the Sobieski! |
| S7 | 82-85.07 | CARAVEL | That mark three is hammering down on us! |
| S7 | 84.9-87.23 | SOBIESKI | Marines prepare to repel boarders! |
| S8 | 88.4-92.6 | CARAVEL | Taking fire on the port pod! Morrow, keep pressure with the drones! |
| S9 | 91.6-94.34 | MORROW | Borers are out — we're raking her sides. |
| S9 | 93.4-96.08 | CARAVEL | Hits on the port pod! Drive's failing — |
| S9 | 96.47-99.21 | CARAVEL | Pod's gone! Turrets on that freighter! |
| S9 | 96.6-99.01 | MORROW | Destroyer's venting — she's breaking off! |
| S9 | 99.36-102.56 | CARAVEL | We're losing hull here - Where's the warden escort! |
| S9b | 104.91-107.6 | WARDEN LEAD | Her shield's down! Keep on her, make them back off — I'm — |
| S10 | 110.4-113.69 | SOBIESKI | Boarding parties nearing the bridge. Vent the passages - |
| S10 | 112.4-117.27 | CARAVEL | We're losing thrust, we're in the well, we're going down. Morrow, break off. |
| S10 | 116.4-118.05 | MORROW | Staying with you. |
| S11 | 118.2-124.8 | CARAVEL | Hull temperature's climbing — helm, hold forty degrees angle of attack, belly to the burn! Don't let her nose — |
| S11 | 124.5- | | static; the plasma takes the channel; CARAVEL and MORROW drop out |
| S11 | 125-127.54 | MORROW | Come again Caravel losing your signal in the — |
| S12 | 130-137.11 | THYSVILLE | All ships this is Thysville. Regroup on me, we need to tighten formation. Helm what's the pirates heading? |
| S13 | 139-145.14 | WARDEN THREE | This is Three. We're scattered, ships going down. Do we go back for Caravel? Morrow? |
| S13 | 146-151.6 | THYSVILLE | Negative, we don't have the numbers. We form up and continue burn for the nearest picket. |
| S13 | ~145.5 / 153.4 | | NOT ESCALATED; ARCHIVED |

Open for the owner:
- Morrow's "Staying with you" makes her going down with the Colossus a choice. The plan
  currently draws both ships as lost, not dived. Keep the line, or drop it so the fall stays
  ambiguous.
- The pirate lines: with a voiced cast, I'd keep the pirates heard. The Falcon's overload call
  in S9b is the payoff of the brawl.

## Sound

**Voices and some effects go through the comms effect; the rest is full battle sound** (owner,
2026-10-29). Each sound is put on one of two buses: the radio bus is what the open mics pick up (the
voices, an alarm or a breach behind a speaker), through a band, a clipper, and a transmission's static
and squelch; the full bus is the reconstruction's own soundtrack (drives, guns, hits, explosions,
entry). The owner's shuttle reentry clip (`s11-shuttle-reentry`, 2026-10-30) runs from the plasma
building in S11 to the cut into S13, because the burn is still in frame (`battle-audio/reentry.py`
holds it): as made through S11, then muffled by S12's lens, dullest as the camera pulls back.
Otherwise S12 is silent. The fleet's radio fades out over the cut into S13 (owner, 2026-10-30):
Warden Three's line starts as Thysville's S12 line ends and fades out 1.6 s into S13, cutting their
conversation off; Thysville's S13 reply is off on the cue sheet. The cue sheet is the Battle Cut page's Sound effects and Music tabs, each cue
with its time, bus, level and fades set against the picture; its Mixer holds the lane levels, the
comms effect's settings and the music's duck under a voice. These are review settings for judging the
cut, not the game's processing. The effects are generated by `battle-audio/battle_sfx.py` from the
descriptions in `battle-audio/sfx-cues.json` (read back from the page), one paid request per take,
takes and receipts kept in `battle-audio/sfx-takes/`.

**Every ship's drive is placed by the camera.** `battle-audio/engines.py` gives each shot S1a to S11
one engines stem: each ship flies her blockout path as the shot's camera sees it, with the Jangala
arrival's rocket voice (`synth_flyby`), panned by her bearing off the lens, louder and brighter as
her apparent size grows, Doppler-shifted by her closing speed, and swelling with her throttle. Her
length picks the drive: from 100 m a single heavy thruster (the generated `c4_launch_burn_t02` with
the Legion's weight), from 65 m a medium drive, smaller ships a small one, drones none. One constant
scales every stem, so a near pass stays louder than a far one across the cut. A spot effect tied to
one ship carries a pan from where she sits in frame at its time.

**Every Harpoon is heard on its own** (owner, 2026-10-30). `battle-audio/missiles.py` gives S5 and
S6 a Harpoons stem: each missile the shot draws leaves its tube with the `harpoon-launch-one` take
(a thunk, then the motor lighting), and its own motor (the looped `harpoon-motor` take) burns from
ignition to its hit, placed by the camera as the engines are. The launch and hit times are the
finished renders' salvo rows, one tube every 3 frames, lit 0.35 s after the eject. The missile
warning runs from the first ignition (47.0 s) to under Warden Five's "Missiles inbound!".

## On-screen place and time

The recording screen (S0) states where and when, before anything is seen:

> **ASHARU** Corvus · low orbit
> the recording's own date, three cycles before the player's arrival

- **The timestamp is in the game's own date format.** The player computes it at run time as the
  date of the player's arrival minus three cycles, written in the game's own date format, so it
  is right in every save.
- **The channels:** one row per speaker by callsign (CARAVEL, WARDEN LEAD, MORROW and the rest), lighting
  as each speaks. The caption tags in the picture lead with the same names (with the registry prefix
  and designation after them), so the screen teaches the cast before the picture starts.

The earlier plain place card, kept for reference:

> **ASHARU** Corvus
> Low orbit / three cycles ago

- **Relative time, not a date.** The campaign's year depends on the player's save, so a fixed
  cycle number would be wrong for most players. "Three cycles ago" is how every character in the
  arc counts it ("three cycles back", Vale).
- **Drawn by the player, not baked** into the frames, like the captions, so it localises.
- **Two ways to draw it:**
  - the C1 planet lock: a sensor lock on Asharu's limb, titled ASHARU with the rest as its
    detail line. That echoes the arrival cinematic's opening on Jangala, but a lock implies
    somebody's sensor, and here it is nobody's;
  - a plain card, lower left, in for about 4 s from 1.5 s. This needs a new overlay kind, and
    `CinematicShot` already skips kinds it does not know.
- I'd take the plain card: this is a memory, not anybody's sensor view.
- **Sensor locks inside the fight** are a natural use of the same overlay. For example, the
  pirates' lock on the Colossus as they climb in S4, titled with her hull class and never her
  cargo, and the rear escort's lock on the climbing contacts.
- **Decided (owner, 2026-10-20):** it plays when the player arrives at Asharu, as scene-setting.
  It is not a recording the player recovers.

The crash against the canyon wall is a separate shot. It needs both ships'
deferred pass 5 states (Colossus crashed; Morrow grounded and damaged).

## Hull builds

No further hulls are to be ordered from this blockout unless the owner asks
(owner, 2026-10-19). The Enforcer was cut, and a finished Lasher leads the escort.
The Mule, Buffalo, Dram and Buffalo Mk.II stay stand-ins and are only ever seen at a distance
(owner, 2026-10-21). That is why S6b's victim is a third Hound (P), not the second Buffalo Mk.II.

Each build is one Drive folder, copied whole to `build/asharu-battle/builds/<folder>/` by hand,
or by the render queue as a drive input (`queue_jobs.py`), pinned by its blend's sha256.
`roster.FINISHED` holds the same list; change both together. Round 8 (2026-10-20) moved the
`0cb9d83` rebuilds in.

| Hull | Build (Drive `builds/...`) | Local folder | State |
|---|---|---|---|
| Colossus | `colossus/139e8b8b6` | `colossus` | finished |
| Shepherd "Morrow" | `shepherd/c85fb19b6` | `shepherd` | finished (close-shot rebuild; dark glazing, 27 lit lamps, blue ring unlit) |
| Lasher x3 | `lasher-independent/0cb9d83` (`lasher.blend`) | `lasher-independent` | finished (kit rebuild); awaiting the owner's review |
| Hound | `hound-independent/5cb098e10` | `hound-independent` | **accepted** (close-shot rebuild; wheelhouse, mast and ramp lamps unlit) |
| Hound (P) x3 | `hound-p/5cb098e10` | `hound-p` | **accepted** (close-shot rebuild; wheelhouse, mast and ramp lamps unlit) |
| Kite (P) x2 | `kite-p/0cb9d83` | `kite-p` | **accepted** (kit rebuild: hammer, swarmer as a row of four) |
| Falcon (P) | `falcon-p/06784b46f` | `falcon-p` | **accepted** (close-shot rebuild; bridge glazing now dark glass, so S10 loses that glow) |
| Colossus Mk.III | `colossus-mk3/005c3b11b` | `colossus-mk3` | **accepted** |
| Phaeton | `phaeton/e9ab52297` `intact/` and `destroyed/` | `phaeton`, `phaeton-destroyed` | **accepted** (both states, including the eight-blast cook-off) |
| Tarsus | `tarsus/2e7d396fc` `clean/` and `boarded/` | `tarsus`, `tarsus-boarded` | **accepted** (owner, 2026-10-20) |
| Wolf (P) x2, merchant Wolf | `wolf-p/968fc1a01`, `wolf/968fc1a01` (`manifest-merchant.json`) | `wolf-p`, `wolf` | **accepted** (textured, with effects; lift jets idle); size not settled (70 m) |
| Manticore (P) | `manticore-p/056e05c26` | `manticore-p` | **accepted** (textured final; Hellbore and two Harpoon pods; no RCS, lens glow or shield yet) |

Shots leave from the muzzle of the mount that bears (`--check`'s choice), read from each build's
current muzzle data (positions, or anchors found in the blend), so a kit rebuild that moves a
muzzle moves the shot with it.

## Blockout

`battle_blockout.py` builds the scene in Blender. It renders the key frames, two roster sheets (from above and broadside, every hull at true relative size) and the orbit diagram.

- **Hulls** are placed through their manifests at each build's own metres per unit.
- **Condition states are chosen per frame:**
  - the boarded Tarsus in S7 and S10;
  - the Phaeton wreck in S6, with its own SWFX effects.
- **Control knobs are set per frame:**
  - the Mk.III's grapples: 0.5 in S8-S9, 1.0 in S10;
  - the Phaeton's `break_frame`: 2 s into the cook-off in S6.
- **The S10 clamp:** the Tarsus is placed at the Mk.III's `grapples.tarsus_origin_units` through `aboard()`.
- **Stand-ins** are the sprite's silhouette raised into a lens shape and painted with the sprite. A stand-in reads as the right hull at the right size and nothing more.
- **Effects** (tracers, placeholder blasts, drive plumes, entry plasma) are placeholders for the VFX kit.
- **Each ship has its own effects** (`ship_fx.py`). Every placed ship gets its own copy of its
  build's `SWFX` collection, retargeted onto its own controls. A build that drives its hull (gear,
  grapples, the wreck's break-up) gets its own hull copy too. So two Lashers never flash in step,
  and two Wolves (P) can hold different gear.
  - A key frame's `cues` set one ship's knobs or light its guns.
  - `--swfx` renders with the kit on every finished build, its drives replacing the placeholder
    plumes.
  - `--fx-check` proves the copies independent without rendering (see `asharu-effects-plan.md`,
    W0).
- **Layers** (owner, 2026-10-22; the default from r10, `--combined` for one pass). A key frame renders as three view layers into one
  multilayer EXR, `raw/<kf>.layers.exr`. `composite_layers.py` composes it into the still in
  seconds, so a new backdrop or a reworked effect never re-renders a hull:
  - `backdrop`: the planet and the starfield, at 16 samples. A locked camera's backdrop is
    rendered once and cached by camera.
  - **Painted plates** come from the planet session. Their sources and prompts are in `plates/`.
    - The finished plates are on Drive at `roster.PLATES`: `<kf>.backdrop.exr`, scene-linear,
      exactly on the key frame's camera, today k0 (S1a) and k6 (S12). A queue job copies them to
      `build/asharu-battle/plates/`.
    - A plate replaces the rendered backdrop before the view. Where it carries a premultiplied
      matte, the rendered stars show through.
    - A display-referred `<kf>/plate.png` is swapped in after the view instead:
      `out = full + (1 - coverage) * (plate - backdrop)`.
    - The backdrop layer is still rendered (16 samples, once per camera), because both forms
      need it.
    - `--plates DIR` points elsewhere, and `--no-plates` is the control.
  - `ships`: the hulls at full samples. Effects and planet are indirect-only, so drives, muzzle
    flashes, burst lights, the sun and the planet's bounce light still light them.
  - `effects`: volumes, shields, rounds and bursts, with ships and planet as holdouts. Holdouts
    still cast shadows and bounce light.
  - Every layer writes Cryptomatte (object, material, asset) and depth.
  - The composite is the backdrop, with the ships over it and the effects over those
    (premultiplied). It then goes through the same AgX view the classic PNG uses.
  - `--diff REF.png` measures a composite against a combined render of the same frame.
  - **Proof:** k0, at half size and 16 samples, on 2026-10-22.
    - Against a combined render at the same settings: mean 0.60/255, p99 1.0, and 0.31 at an
      8 px blur.
    - The one local residue is the drive plumes, at most 21 levels in an 8 px block, from
      denoising the effects layer on its own.
    - Against the 48-sample r9b still, it is as close as the combined render is (mean 0.8).
  - Collections carry the split: `Stage | ships`, `Stage | effects`, `Backdrop`. A kit object
    inside a hull copy, or a loose object at the scene root, fails the frame (`ASHARU layers leak`).

To check a scene without rendering, run `python battle_blockout.py --check`; `python fight_layout.py` draws the same staging as diagrams. It prints where every ship lands in every key frame, and whether each shot can bear (see Gunnery). `--frames k3b,k5` renders only those frames; a frame already finished in the run folder is skipped, so a stopped job resumes (`--run <name>` fixes the folder across days, `--rerender` forces every frame); and `--skip-render --post <machine> --what <slug>` posts a run to `review/cinematic-asharu-battle/`.

## Open items

One line each, with its owner. Keep this list current, and remove an item when it closes.

- **Kit rounds from stand-in hulls** (coordinator, 2026-10-23): the Buffalo Mk.II (S6b, on Lasher B)
  and the Mule (S6c, on Wolf (P) B) have no kit build, so their fire was cut rather than drawn as
  stand-in streaks. Plan and key their rounds from the stand-in hull's muzzle points (scene_fx.tracer
  without a shooter copy) if the owner wants them seen firing. Effects session.

- **The last look checks before finals** (owner approved the r10 key frames, 2026-10-22: "I think
  we're good"): S8-S9 (k4) with the Borers' mining lasers (`swfx_mining`) re-rendered and shown; S3/S4
  (k1b, k2) shown; the S12 lit span shown. Then the final render plan (24 fps, layered, per-shot chunk
  jobs on the final lane), which waits for the render coordinator's gate and the owner. Blockout session.
- **The comms script.** The voiced draft is in `radio_log.TRAFFIC` (see "Radio traffic (draft)");
  the owner is still refining lines and takes on the Battle Cut page, and each change there is
  carried back here. It blocks no render.
- **The wreck kit is swapped in** for lit renders (`roster.WRECKS`, `builds/asharu-wrecks/33513e7f8`,
  pinned by sha256): the station as one modelled piece, and each box filled by `wreck_kit.pick`.
  The motion preview, and `--blockout-wrecks`, keep the stand-ins. `--check` reports the kit:
  whether the station still matches `platform_parts()` (if not, the wreck session rebuilds it),
  and which hulks the boxes draw. The box contract stays stable. The S3/S4 wrecks read as
  wreckage (owner, 2026-10-22). Wreck session and blockout session.
- **The Hermes boarding craft's pirate livery,** if the owner wants it. Hermes build (pinned:
  read it, never edit it).
- **The r9b k3 and k3b review frames** are held in the queue, for the render coordinator to
  release or cancel. Their next lit render uses `--volume-biased --volume-step-rate 4`.
- **The layered r10 lit renders** (plates `1d5ec7781`, wreck kit `33513e7f8`): every key frame is
  rendered at `421c526b6`, which has the derelict at 4.5 km, so S3 (k1b) and S4 (k2) are current;
  their layers are on Drive under `builds/asharu-layers/r10_*`. S12 (k6) is the old trails look and
  is re-queued on the passed burn (`asharu-r10b-k6-s12`, review lane: it waits for the gate).
  Blockout session.
- **The planet:** S12's far tile, and the plates over the rendered planet for S1a and S12. Plates
  session.
- **The Colossus's shield in S9:** the shielded build is needed if she raises it against the
  Mk.III (see "What the brawl asks"). Owner.

## Where it stands, and the next step

- **Posted:** round 6 (`review/cinematic-asharu-battle/2026-10-19_round6-colossus-mk3_windows`) is the latest run.
- **Round 7** (r7c) was queued from a flat inputs folder on the Windows worktree.
- **Round 8 (2026-10-20): the newest builds.** The Lasher and Kite (P) kit rebuilds
  (`0cb9d83`), the close-shot rebuilds of both Hounds, the Falcon (P) and the Shepherd (dark
  glazing, fewer lit lamps), the textured Wolfs, and the textured Manticore (P) final, which
  replaces her stand-in.
  Shots now leave from the bearing mount's muzzle, and missiles from a launcher when one bears. `--check` is clean: all 22 tracers and missiles bear. Queued as five
  single-frame review jobs (k3, k3b, k4, r1, r2) with `--run r8b`, frame resume and
  drive inputs pinned by sha256. Either machine may take a frame; both have the Starsector install.
- **Volumes are the cost of k3 and k3b** (timed 2026-10-22, k3b at 12% size and 1 sample,
  `volume_timing.py`): as rendered 20 s, every kit volume hidden 6 s, a coarser step rate alone
  31 s (Blender 5's unbiased volumes ignore it), biased volumes 12 s, and biased at step rate 4
  6 s, the same as no volumes at all. The r9b k3/k3b review stills ran over 4 h without
  finishing a frame. Their next lit render goes with `--volume-biased --volume-step-rate 4`,
  once the motion is accepted.
- **Render cost** of `--frames k3,k3b,k4,r1,r2 --samples 48`, extrapolated from quarter-size and
  half-size samples on the RTX 3080 (k3b was not sampled):
  - k3 (S6) is nearly all of it. The cook-off's volumes scale linearly with pixels times samples:
    35 s at quarter size and 4 samples, 573 s at half size and 16. That makes **about 1.5-2 h at
    full size**. It holds about 7.5 GB of the 3080's 10 GB at half size, so a full-size render
    may not fit beside anything else on the GPU.
  - k4, r1 and r2 take under a minute each, and k3b should be the same. Setting up the scene and
    each frame adds about 5 min.
  - **Windows: about 2 h. Mac: about 3 h**, from C1's measured rate there (about 200 s a frame,
    against 2-3 min on Windows), and not timed directly.
- **Held (owner, 2026-10-20): not ready for a production render.** The fight is reviewed
  as layout diagrams first: `python fight_layout.py` draws an overview and one sheet per key
  frame (the camera's frame as a schematic, plan and side views, every line of fire) from this
  script's own staging, with no render, into `build/asharu-battle/layout/`.
- **Restructured (owner, 2026-10-20)** as a spectacle: a back-and-forth brawl, four new combat
  key frames (k3c S6b, k3d S6c, k3e S6d, k4b S9b), all of whose shots bear, plus a voiced cast.
  About 2 minutes.
- **Next step:**
  1. Settle the staging on the layout sheets, including the four new shots (not in round 8).
  2. Review round 8 (`--what round8-new-builds`) when it lands; prune.
  3. S10 (k5) is not in that set. Re-render it only once the owner settles its camera.

## What the brawl asks of the builds and the effects

The game grounding and the cinematic treatment for every weapon, missile, shield and system are in
`asharu-effects.md`; how they get built (workstreams, tests, the session split) is in
`asharu-effects-plan.md`.

This is not an order for new hulls (the owner's rule stands). These are the states and effects the
new shots draw on:

- **Effects, for the VFX kit:**
  - shields that go up and down;
  - a flux glow on a shield under load;
  - an overload, with the shield popping in arcs;
  - a vent glow;
  - the Wolf's Displacer blink;
  - the Lasher's Ammo Feeder burst (a faster muzzle cadence).
  The shield kit already exists (front arcs, faction colours, one strength knob). Overload,
  venting and the blink are new.
- **Condition states:** two Hounds (P) are destroyed (S6b, S6d), as blasts and debris over the
  finished build in the blockout. Three are finished
  builds that would want a damaged state: the Falcon (P) hit (S9b), the lead Lasher burning
  (S6, S9b) and the convoy's Hound with her drives hit (S6d).
- **The Colossus's shield:** the game gives her a front shield, but the staged build
  (`colossus/139e8b8b6`) was chosen as the one without it. If she raises a shield against the
  Mk.III in S9, the shielded build is wanted instead.

## Open for the owner

- **S10:** with the real clamp, the Tarsus hangs under the Mk.III, and the high camera hides her (and the docked Kite (P)). Either move the camera lower and side-on, or add a close shot of the clamp.
- **The gunnery staging:**
  - the lead Lasher banking;
  - the Wolf (P)'s inverted gun run;
  - the Mk.III on her side for a broadside before she takes the Tarsus.
- **Open hull questions:**
  - the Wolf's size, 70 m;
- A short animated pass of S6, to judge the whole cook-off rather than one still.

- Asharu's size, the 250 km orbit and the low sun.
- The Colossus lost or deliberately dived.
- Whether one of the Lashers comes down too (the story says "several of her
  escorts" came down in the valley).
