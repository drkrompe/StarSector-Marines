# Asharu battle: the wreckfall

Status: DRAFT — blockout round 7 in code, not yet rendered (last posted run: round 6). Hull builds nearly all finished.
Written: 2026-10-19
Updated: 2026-10-20 — restructured as a spectacle (owner): a back-and-forth brawl turning on flux, four new combat shots (S6b, S6c, S6d, S9b), a voiced cast, about 2 minutes.

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
   break up a Buffalo Mk.II, and the convoy's Hound kills a Hound (P) at the
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
| Destroyed | Phaeton | Buffalo Mk.II B, Hound (P) A |
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
| Manticore (P) | long-range destroyer that breaks the screen | 91 m (0.59 m/u; 95 m across the pods) | 0.85 Colossus | hull in owner review; stand-in at its published size |
| Buffalo Mk.II x2 | destroyers | 87 m (est.) | 0.8 Colossus | stand-in |
| Wolf (P) x2 | fast frigates that cut the column | 70 m (size not settled) | 1.15 Hounds | **finished** |
| Hound (P) x2 | frigates | 61 m | the reference | **finished** (pirate state of the Hound) |
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

About 2 minutes (118 s): 34 s of setup and ambush, 60 s of fighting, 24 s of the fall. **K** marks a shot with a blockout key frame. Shot names with a letter (S6b) were added on 2026-10-20 and are renumbered once the list is settled.

| Shot | Length | Picture | Ships seen closest | Camera |
|---|---|---|---|---|
| S1 **K** | 8 s | Establishing: the convoy in loose column over the day side, the limb high in frame, the desert sliding beneath. Ordinary work. | the whole convoy, small | 35 mm, high off the north flank |
| S2 | 6 s | Alongside the Colossus. Morrow rides off her starboard quarter; a Lasher slides through the foreground. | Colossus, Morrow, Lasher | 28 mm, tracking alongside at 300 m |
| S3 **K** | 8 s | Running dark. Long lens back down at the derelict platform: dark, stripped, hulks in its bays; hold until it reads as junk. Then one hulk shows a running light, reactor glow behind a bay, a second, a third. Drives light and the first ships ease out. The audience sees the trap before the convoy does. | the platform, the Falcon (P) | 135 mm from the convoy's tail (longer, about 300 mm, is worth trying) |
| S4 **K** | 6 s | First contact: twelve hulls climbing out of the platform toward the column, drives lit, the desert behind them. | Falcon (P), Wolves, Hounds (P), Kites (P) | 35 mm, off their flank |
| S5 | 6 s | The opening: the Manticore (P) fires at long range, and rounds and missiles cross 3-4 km of space. | Manticore (P) | 85 mm along the line of fire |
| S6 **K** | 8 s | The screen breaks: the Phaeton tanker is struck in the first 2 s and cooks off through the rest, a Lasher burns, and the lead Lasher turns back into the fire and is hit. | lead Lasher, Phaeton, Lasher A | 35 mm, looking aft along the convoy |
| S6b **K** | 8 s | The escorts answer. Lasher B and the merchant Wolf turn back and roll onto their backs over the climbing raiders. A Buffalo Mk.II's armour soaks it (no shield), then the Ammo Feeder burst breaks her up. First blood to the convoy. | Lasher B, merchant Wolf, Buffalo Mk.II B | 50 mm, above and behind the escorts, looking down past them |
| S6c **K** | 8 s | The column is cut. Tracking alongside at about 300 m: the two Wolves (P) blink (Displacer) into the gap between the freighters, front shields up under the Mule's fire, then down to rake the Tarsus's drives. The merchant Wolf chases them through. | Wolves (P), Tarsus, merchant Wolf | 35 mm, tracking alongside the column |
| S6d **K** | 6 s | The rear guard. The convoy's Hound rolls over to face two Hounds (P) climbing at her. No shields on either side: armour, flares and guns. She kills the first; the second's fire takes her drives, and she falls back on the column trailing flares. | Hound, Hounds (P) | 35 mm, close behind the Hound |
| S7 **K** | 8 s | Boarding the Tarsus: drives shot out, fallen out of the column, Kites (P) alongside, a Wolf (P) covering; the convoy runs on ahead. | Tarsus, Kite (P), Wolf (P) | 35 mm, from behind the Tarsus |
| S8 | 6 s | Close on the Colossus's aft: the port tug pod is shot out, the drives stutter, debris. | Colossus | 50 mm, close |
| S9 **K** | 8 s | She will not be taken: the Colossus Mk.III closes from above; she fights with flares and both turrets and drives off a Kite; Morrow is under her keel holding her up. | Colossus, Colossus Mk.III, Morrow | 28 mm, 600 m off her port side |
| S9b **K** | 8 s | The lead's last run. Smoking since S6, the lead Lasher dives over the Falcon (P) on her back. The Falcon's broad front shield takes it and flares, then overloads in a burst of arcs, and the Lasher's guns walk down the flagship's open flank. The Falcon's broadside answers, and the Lasher tumbles away burning. | lead Lasher, Falcon (P) | 28 mm, close, just above the Lasher |
| S10 **K** | 8 s | The prize taken: the Tarsus clamped to the Colossus Mk.III; far below, the Colossus nose-down and losing her orbit, Morrow with her. | Falcon (P), Colossus Mk.III, Tarsus; Colossus and Morrow below | 35 mm, behind and above |
| S11 | 8 s | Entry: from just behind, plasma builds round both hulls; Morrow drifts apart from her. | Colossus, Morrow | 50 mm, chase |
| S12 **K** | 8 s | Two trails down the track toward the horizon and the canyon country. Hold. End. | (trails only) | 50 mm from the pirates' orbit |

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
| S11 | (both) | static; the plasma takes the channel |

Open for the owner:
- Morrow's "Staying with you" makes her going down with the Colossus a choice. The plan
  currently draws both ships as lost, not dived. Keep the line, or drop it so the fall stays
  ambiguous.
- The pirate lines: with a voiced cast, I'd keep the pirates heard. The Falcon's overload call
  in S9b is the payoff of the brawl.

## On-screen place and time

S1 opens on a place card, so the audience knows where and when they are before anything happens:

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
- **Not decided:** where the cinematic plays in the game (the Shepherd-recovery arc's opening, a
  codex entry, or a recovered recording Basin found in the Colossus). That decides whether the
  card says "three cycles ago" or carries a recording's own timestamp.

The crash against the canyon wall is a separate shot. It needs both ships'
deferred pass 5 states (Colossus crashed; Morrow grounded and damaged).

## Hull builds

No further hulls are to be ordered from this blockout unless the owner asks
(owner, 2026-10-19). The Enforcer was cut, and a finished Lasher leads the escort.
The Mule, Buffalo, Dram and Buffalo Mk.II stay stand-ins, because they are only
seen at a distance.

| Hull | Build (Drive `builds/...`) | Copy into `build/asharu-battle/inputs/` as | State |
|---|---|---|---|
| Colossus | `colossus/139e8b8b6` | `colossus.blend`, `colossus-manifest.json` | finished |
| Shepherd "Morrow" | `shepherd/5860eae2b` | `shepherd.blend`, `shepherd-manifest.json` | finished |
| Lasher x3 | `lasher-independent/7805d3b` (`lasher.blend`) | `lasher-independent.blend`, `lasher-independent-manifest.json` | finished; awaiting the owner's review |
| Hound | `hound-independent/1e91821` | `hound-independent-clean.blend`, `hound-independent-manifest.json` | **accepted** |
| Hound (P) x2 | `hound-p/33114de` | `hound-p-pirate.blend`, `hound-p-manifest.json` | **accepted** |
| Kite (P) x2 | `kite-p/a48f8757b` | `kite-p.blend`, `kite-p-manifest.json` | **accepted** |
| Falcon (P) | `falcon-p/0044413fc` | `falcon-p.blend`, `falcon-p-manifest.json` | **accepted** |
| Colossus Mk.III | `colossus-mk3/005c3b11b` | `colossus-mk3.blend`, `colossus-mk3-manifest.json` | **accepted** |
| Phaeton | `phaeton/e9ab52297` `intact/` and `destroyed/` | `phaeton.blend` + `phaeton-manifest.json`; `phaeton-destroyed.blend` + `phaeton-destroyed-manifest.json` | **accepted** (both states, including the eight-blast cook-off) |
| Tarsus | `tarsus/2e7d396fc` `clean/` and `boarded/` (both `tarsus.blend`) | `tarsus.blend` + `tarsus-manifest.json`; `tarsus-boarded.blend` + `tarsus-boarded-manifest.json` | **accepted** (owner, 2026-10-20) |
| Wolf (P) x2, merchant Wolf | `wolf-p/f60641f63`, `wolf/f60641f63` | `wolf-p.blend`, `wolf-p-manifest.json`; `wolf.blend`, `wolf-manifest.json` | finished; size not settled (70 m); a turret clash check may update the build |
| Manticore (P) | none yet: the hull is in the owner's review | none: a stand-in at its published 91 m (0.59 m/u) | effects pass and Drive build come after acceptance |

Each manifest is the build folder's `manifest.json`, renamed as shown. When a build changes, update its row here and its `roster.FINISHED` entry.

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

To check a scene without rendering, run `python battle_blockout.py --check`; `python fight_layout.py` draws the same staging as diagrams. It prints where every ship lands in every key frame, and whether each shot can bear (see Gunnery). `--frames k3b,k5` renders only those frames; a frame already finished in the run folder is skipped, so a stopped job resumes (`--run <name>` fixes the folder across days, `--rerender` forces every frame); and `--skip-render --post <machine> --what <slug>` posts a run to `review/cinematic-asharu-battle/`.

## Where it stands, and the next step

- **Posted:** round 6 (`review/cinematic-asharu-battle/2026-10-19_round6-colossus-mk3_windows`) is the latest run.
- **Round 7 is in code but not rendered.** It brings:
  - the accepted Phaeton cook-off in S6;
  - the rolls that let the guns bear, in S6, S7 and S8-S9;
  - the reshaped Wolf and the round 2 Tarsus;
  - the Manticore (P) at 91 m.
- **Staged on Windows (2026-10-20):**
  - Every build in the table is copied into `build/asharu-battle/inputs/`. The Colossus is
    `colossus/139e8b8b6` (coordinator's call: the newest build; Asharu needs no front shields).
  - `--check` is clean: all 11 tracers and missiles bear. No staging change was needed.
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
  1. Settle the staging on the layout sheets, including the four new shots.
  2. Then render the set above, by night, through the owner.
  3. Post with `--what round7-gunnery-cookoff`, then prune.
  4. S10 (k5) is not in that set. Re-render it only once the owner settles its camera.

## What the brawl asks of the builds and the effects

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
- **Condition states:** two are stand-ins, a destroyed Buffalo Mk.II (S6b) and a destroyed
  Hound (P) (S6d), and are blasts and debris over the slab in the blockout. Three are finished
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
  - the Manticore (P) hull review.
- A short animated pass of S6, to judge the whole cook-off rather than one still.

- Asharu's size, the 250 km orbit and the low sun.
- The Colossus lost or deliberately dived.
- Whether one of the Lashers comes down too (the story says "several of her
  escorts" came down in the valley).
