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

The game also gives Asharu a **derelict orbital platform**: "castoff
bulk-carrier segments welded between a honeycomb of emptied fuel tanks...
hangars stand empty but for a few slumped, stripped hulks... no one would
expect to find anything of value here". That is where the pirates wait.

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
above the derelict platform, which sits about 4 km lower and 10 km astern. A
lower orbit is a faster one, so a raider that lights its drives there catches up
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
in it (S1a at 10 s, S1 16 s, S4 40 s, S6 56 s, S6c 72 s, S7 86 s). Formation ships move;
authored combat poses, a ship on her gun run, and the powered-down pirates at the platform are
left as posed. Curved attack runs and breakaways for the pirates belong to the animated pass.

### Morrow's drones

Morrow is a drone tender, so her four Borers work wherever the story lets them. Each is placed
through ShipFx, so each blinks its beacon, pulses its lift jets and fires its mining laser on its
own. Placements:
- **S8-S9:** two Borers hold outboard of and under the Colossus's shot-out port pod, cutting the
  burning wreckage free with their lasers. A third lifts out of Morrow's bay (launch point 2,
  straight up, as the cradle check clears) to join them.
- At that range a Borer is about 9 px. It reads by its work lamps, its beacon and its beam.

`near()` places a craft in its parent's frame with its own attitude, so a drone stays on the
pod if the Colossus is re-posed.

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
- **It ends on the recording.** In S11 the plasma takes the channel: the picture breaks into the
  recording screen's static, the waveform flattens, the Colossus's and Morrow's channels drop out
  one at a time, and **END OF RECORDING**. S12, the two trails, plays after it in silence, or
  under the static: the last image is one no recording made.
- **When it plays (owner, 2026-10-20): when the player arrives at Asharu,** before the Salvage
  Rights briefing. It sets the scene. Vale's "three cycles back, two fleets fought it out in low
  orbit" then confirms something the player has already seen, rather than introducing it.
- **Nobody in the story recovers it.** The recording is the film's way of telling, not an object
  in the plot: no source, no device, nothing the player finds or keeps. It sets the scene, and
  the story never refers to it again.
- **What it may and may not reveal**, against the arc's reveal order (`shepherd-recovery-dialogue.md`):
  - it shows the wreckfall, the Colossus and her escorts coming down, which the briefing reveals
    next anyway;
  - it never touches the hold, its contents, the buyer, or Basin, which are withheld in the arc,
    some of them permanently;
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
|---|---|---|---|---|
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
|---|---|---|---|---|
| Falcon (P) | flagship, light cruiser | 174 m | 1.6 Colossus; 0.6 Onslaught | **finished** (accepted by the owner; faded red kept) |
| Colossus Mk.III | the prize ship: tries for the Colossus, takes the Tarsus | 110 m | the Colossus's twin | **finished** (accepted by the owner); grapples from stowed to clamped, fitted to the Tarsus |
| Manticore (P) | long-range destroyer that breaks the screen | 91 m (0.59 m/u; 95 m across the pods) | 0.85 Colossus | **finished** (textured; no shield yet) |
| Buffalo Mk.II | destroyer | 87 m (est.) | 0.8 Colossus | stand-in, kept distant |
| Wolf (P) x2 | fast frigates that cut the column | 70 m (size not settled) | 1.15 Hounds | **finished** |
| Hound (P) x3 | frigates | 61 m | the reference | **finished** (pirate state of the Hound) |
| Kite (P) x2 | boarding shuttles | 52 m | 0.9 Hound | **finished** |

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
- S6c: the Wolves (P) bank hard through the gap, 60-70 degrees, to keep their decks on the Tarsus.
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

| Shot | Length | Picture | Ships seen closest | Camera |
|---|---|---|---|---|
| S0 | 10 s | **The recording.** Black, then the playback screen: source, timestamp, place, the channels lighting as each speaks, a waveform. The S1 traffic plays over it as audio only. On the escort lead's "close up the column", cut to picture. | (none) | (a screen, not a camera) |
| S1a **K** | 6 s | **The view the player knows** (owner, 2026-10-21). The first picture after the recording looks straight down on the convoy, prograde up the frame: the ships small against the desert far below, the way the player has always seen them. Then the camera tips down and pulls into 3D for S1. | the core convoy, from above | 100 mm, straight down from 11 km |
| S1 **K** | 8 s | Establishing: the convoy in loose column over the day side, the limb high in frame, the desert sliding beneath. Ordinary work. | the whole convoy, small | 35 mm, high off the north flank |
| S2 | 6 s | Alongside the Colossus. Morrow rides off her starboard quarter; a Lasher slides through the foreground. | Colossus, Morrow, Lasher | 28 mm, tracking alongside at 300 m |
| S3 **K** | 8 s | Running dark. Long lens back down at the derelict platform: dark, stripped, hulks in its bays; hold until it reads as junk. Then one hulk shows a running light, reactor glow behind a bay, a second, a third. Drives light and the first ships ease out. The audience sees the trap before the convoy does. | the platform, the Falcon (P) | 135 mm from the convoy's tail (longer, about 300 mm, is worth trying) |
| S4 **K** | 6 s | First contact: twelve hulls climbing out of the platform toward the column, drives lit, the desert behind them. | Falcon (P), Wolves, Hounds (P), Kites (P) | 35 mm, off their flank |
| S5 | 6 s | The opening: the Manticore (P) fires at long range, and rounds and missiles cross 3-4 km of space. | Manticore (P) | 85 mm along the line of fire |
| S6 **K** | 8 s | The screen breaks: the Phaeton tanker is struck in the first 2 s and cooks off through the rest, a Lasher burns, and the lead Lasher turns back into the fire and is hit. | lead Lasher, Phaeton, Lasher A | 35 mm, looking aft along the convoy |
| S6b **K** | 8 s | The escorts answer. Lasher B and the merchant Wolf turn back and roll onto their backs over the climbing raiders. A Hound (P)'s bare armour soaks it (no shield), then the Ammo Feeder burst breaks her up. First blood to the convoy. | Lasher B, merchant Wolf, Hound (P) C | 50 mm, above and behind the escorts, looking down past them |
| S6c **K** | 8 s | The column is cut. Tracking alongside at about 300 m: the two Wolves (P) blink (Displacer) into the gap between the freighters, front shields up under the Mule's fire, then down to rake the Tarsus's drives. The merchant Wolf chases them through. | Wolves (P), Tarsus, merchant Wolf | 35 mm, tracking alongside the column |
| S6d **K** | 6 s | The rear guard. The convoy's Hound rolls over to face two Hounds (P) climbing at her. No shields on either side: armour, flares and guns. She kills the first; the second's fire takes her drives, and she falls back on the column trailing flares. | Hound, Hounds (P) | 35 mm, close behind the Hound |
| S7 **K** | 8 s | Boarding the Tarsus: drives shot out, fallen out of the column, Kites (P) alongside, a Wolf (P) covering; the convoy runs on ahead. | Tarsus, Kite (P), Wolf (P) | 35 mm, from behind the Tarsus |
| S8 | 6 s | Close on the Colossus's aft: the port tug pod is shot out, the drives stutter, debris. | Colossus | 50 mm, close |
| S9 **K** | 8 s | She will not be taken: the Colossus Mk.III closes from above; she fights with flares and both turrets and drives off a Kite; Morrow is under her keel holding her up. | Colossus, Colossus Mk.III, Morrow | 28 mm, 600 m off her port side |
| S9b **K** | 8 s | The lead's last run. Smoking since S6, the lead Lasher dives over the Falcon (P) on her back. The Falcon's broad front shield takes it and flares, then overloads in a burst of arcs, and the Lasher's guns walk down the flagship's open flank. The Falcon's broadside answers, and the Lasher tumbles away burning. | lead Lasher, Falcon (P) | 28 mm, close, just above the Lasher |
| S10 **K** | 8 s | The prize taken: the Tarsus clamped to the Colossus Mk.III; far below, the Colossus nose-down and losing her orbit, Morrow with her. | Falcon (P), Colossus Mk.III, Tarsus; Colossus and Morrow below | 35 mm, behind and above |
| S11 | 8 s | Entry: from just behind, plasma builds round both hulls; Morrow drifts apart from her. The radio breaks up, the picture breaks into the recording screen's static, the channels drop out, and END OF RECORDING. | Colossus, Morrow | 50 mm, chase |
| S12 **K** | 8 s | After the recording ends: two trails down the track toward the horizon and the canyon country, in silence. Hold. End. | (trails only) | 50 mm from the pirates' orbit |

## Radio traffic (draft)

The convoy and the pirates talk throughout, as captions with a speaker tag over the picture.
That is the arrival cinematic's caption track: `CinematicShot` captions name a `strings.json`
key and a speaker, and the player pages them (`CaptionPager`). No English is baked into the
frames.

It is **voiced**: an in-universe cast, which the game otherwise never has.

Rules:
- **A cast of voices, credited by ship and role.** Each voice is a person with a character: the
  Colossus's master is steady and tired, the escort lead is clipped, the rear Hound is young, and
  the pirate flag is unhurried. But nobody gets a personal name. The story keeps Morrow's
  original crew, and every other crew, unnamed (`shepherd-recovery-dialogue.md`): Basin never
  learns who they were, and the cinematic does not tell the player either. A caption tag reads
  COLOSSUS or ESCORT LEAD.
- **Flux is spoken.** Shield and flux calls are the kind of thing a player recognises from the
  game ("shields at ninety", "she's venting, now", "overload"). They also carry the
  back-and-forth.
- **Nobody names the cargo**, the owner or the buyer, and nobody gives a reason the convoy
  matters. The pirates want what is in the holds and never say what it is.
- **Working voices.** Short, procedural, a little tired, and frightened only once it goes bad.
  This is ordinary traffic and an ordinary raid.
- **Silence is a beat.** S3 has no convoy traffic, and S11-S12 lose the radio to plasma and
  then to distance.

| Shot | Speaker | Line |
|---|---|---|
| S1 | Colossus | Convoy, Colossus. Window opens in forty. All ships ease to two-thirds. |
| S1 | Escort lead | Lead copies. Escorts, close up the column. |
| S2 | Morrow | Morrow, standing off your starboard quarter. Drones stowed. |
| S3 | (pirate channel, low) | Hold. ... Hold. ... Light them. |
| S4 | Rear escort | Lead, rear. Drives behind us. Low, off that old station. A lot of them. |
| S4 | Escort lead | Say again. |
| S4 | Rear escort | Twelve. Climbing. |
| S5 | Escort lead | Escorts, come about. Convoy, full burn, now. |
| S5 | Colossus | We're committed to the window. We can't outrun them from here. |
| S6 | Phaeton | Phaeton, we're hit, we're venting, we're — |
| S6 | Escort lead | Lead turning back. Cover the tail. |
| S6b | Lasher B | Two, rolling in. Wolf, take her left. |
| S6b | Merchant Wolf | She's got no shield. Hit her. |
| S6b | Lasher B | Feeders on. ... She's breaking up. One down. |
| S6c | Mule | Two frigates in the gap! They're in the column! |
| S6c | Merchant Wolf | Shields up, they blinked, where — on the Tarsus, they're on the Tarsus! |
| S6c | Tarsus | Taking fire aft, flux at ninety, I can't hold the shield — |
| S6d | Rear escort | Two on me. ... Got one. Got one! |
| S6d | Rear escort | Drives hit. Falling back on the column. Flares out. |
| S7 | Pirate (Kite) | Tarsus. Open your locks and you all walk away. |
| S7 | Tarsus | Drives are gone. They're alongside. Boarders, boarders — |
| S8 | Colossus | Port pod's gone. Morrow, get under us. |
| S9 | Morrow | Under you. Taking the weight. |
| S9 | Pirate (Mk.III) | Colossus. Heave to. You're next. |
| S9b | Escort lead | Lead. One more pass. On the flagship. |
| S9b | Pirate (Falcon) | Shield up. ... Shield's overloading — |
| S9b | Escort lead | She's open. She's open! |
| S9b | Pirate (Falcon) | Broadside. Burn that frigate off my hull. |
| S10 | Pirate (Falcon) | Let her go. She's going down anyway. Take the Tarsus. |
| S10 | Colossus | We're losing the orbit. Morrow, break off. |
| S10 | Morrow | Staying with you. |
| S11 | (both) | static; the plasma takes the channel; END OF RECORDING |

Open for the owner:
- Morrow's "Staying with you" makes her going down with the Colossus a choice. The plan
  currently draws both ships as lost, not dived. Keep the line, or drop it so the fall stays
  ambiguous.
- The pirate lines: with a voiced cast, I'd keep the pirates heard. The Falcon's overload call
  in S9b is the payoff of the brawl.

## On-screen place and time

The recording screen (S0) states where and when, before anything is seen:

> **ASHARU** Corvus · low orbit
> the recording's own date, three cycles before the player's arrival

- **The timestamp is in the game's own date format.** The player computes it at run time as the
  date of the player's arrival minus three cycles, written in the game's own date format, so it
  is right in every save.
- **The channels:** one row per speaker (COLOSSUS, ESCORT LEAD, MORROW and the rest), lighting
  as each speaks. The caption tags in the picture use the same names, so the screen teaches the
  cast before the picture starts.

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
- **Layers** (owner, 2026-10-22; `--layers`). A key frame renders as three view layers into one
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
  - Collections carry the split: `Stage | ships`, `Stage | effects`, `Backdrop`. A kit object
    inside a hull copy, or a loose object at the scene root, fails the frame (`ASHARU layers leak`).

To check a scene without rendering, run `python battle_blockout.py --check`; `python fight_layout.py` draws the same staging as diagrams. It prints where every ship lands in every key frame, and whether each shot can bear (see Gunnery). `--frames k3b,k5` renders only those frames; a frame already finished in the run folder is skipped, so a stopped job resumes (`--run <name>` fixes the folder across days, `--rerender` forces every frame); and `--skip-render --post <machine> --what <slug>` posts a run to `review/cinematic-asharu-battle/`.

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
