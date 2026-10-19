# Asharu battle: the wreckfall

Status: DRAFT — round 3 blockout (`battle_blockout.py`); pirate and convoy hull builds in progress.
Written: 2026-10-19
Updated: 2026-10-19 — round 3: the Enforcer is cut, a Lasher leads the escort, and the finished Kite (P), Hound (P) and independent Lasher and Hound replace their stand-ins.

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

## How the convoy is broken, and why the pirates do not follow

1. **Caught slow.** The convoy is committed to its descent burn: slowed, low,
   with nowhere to run.
2. **Ambush from behind and below.** The pirates light up inside the platform
   the convoy has just passed and climb out of it, closing from astern.
3. **The screen goes first.** The Manticore (P) and the Falcon (P) open at long
   range. The Phaeton tanker, at the convoy's tail, goes up. A Lasher burns.
   The lead Lasher turns back into the fire and is shot up.
4. **The tail is cut off.** The Tarsus, astern of the Colossus, has her drives
   shot out and falls out of the column. Kites (P) lie alongside her with the
   boarders. The rest of the convoy runs on ahead.
5. **The Colossus will not be taken.** The Colossus Mk.III, an armed Colossus
   like her, closes to take her too and shoots out her port tug pod. She fights
   back with flares and both turrets and drives off a Kite. Morrow, under her
   keel, holds her up with her own drives.
6. **She falls.** A crippled, slowed freighter in a 250 km orbit does not stay
   there. With one pod gone and the other failing, she loses her orbit, and
   Morrow goes down with her.
7. **The pirates take what they have.** The captured Tarsus is clamped to the
   Colossus Mk.III. A burning freighter heading into the atmosphere, with no
   way to get her back up, is not worth chasing. The rest of the convoy (Mule,
   Buffalos, Dram, the surviving escorts) gets away while the pirates are busy
   with their prize. Two plasma trails run north toward the canyon country.

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
| Tarsus | freighter: boarded and taken, the pirates' prize | 106 m (est.) | as long as the Colossus, much slimmer | **ordered** (seen close in S7 and S10) |
| Phaeton | the convoy's tanker, destroyed in the opening | 73 m (est.) | 1.2 Hounds, long and thin | **ordered** (mid-close in S6) |
| Mule | armed freighter; gets away | 104 m (est.) | about a Colossus | stand-in |
| Buffalo x2 | small freighters; get away | 58 m (est.) | a Hound | stand-in |
| Dram | small tanker; gets away | 48 m (est.) | 0.8 Hound | stand-in |
| Lasher x3 | escort lead and escort frigates | 90 m | 1.5 Hounds | **finished** (independent livery) |
| Wolf | point escort frigate | 86 m (est.) | 1.4 Hounds | from the Wolf (P) build, with a merchant livery |
| Hound | rear escort | 61 m | the reference | **finished** (independent livery) |

### Pirate raiders (11 hulls)

| Hull | Role | Length | Against known ships | Model |
|---|---|---|---|---|
| Falcon (P) | flagship, light cruiser | 159 m (est.) | 1.5 Colossus; half an Onslaught | ordered |
| Colossus Mk.III | the prize ship: tries for the Colossus, takes the Tarsus | 107 m (est.) | the Colossus's twin | ordered, from the Colossus geometry |
| Manticore (P) | long-range destroyer that breaks the screen | 96 m (est.) | 0.9 Colossus | ordered |
| Buffalo Mk.II x2 | destroyers | 87 m (est.) | 0.8 Colossus | stand-in |
| Wolf (P) x2 | fast frigates that cut the column | 86 m (est.) | 1.4 Hounds | ordered |
| Hound (P) x2 | frigates | 61 m | the reference | **finished** (pirate state of the Hound) |
| Kite (P) x2 | boarding shuttles | 52 m | 0.9 Hound | **finished** |

All the pirate hulls are vanilla `pirate_bp` skins or pirate hulls (Colossus
Mk.III), plus the Buffalo Mk.II, which pirate fleets field. Left out on purpose: the
phase frigates (a phase ambush is a different film from this one), and the
Venture Mk.II and Atlas Mk.II, which would make the raid read as a pirate
armada.

## Shot list

About 95 seconds. **K** marks a shot with a blockout key frame in the review run.

| Shot | Length | Picture | Ships seen closest | Camera |
|---|---|---|---|---|
| S1 **K** | 8 s | Establishing: the convoy in loose column over the day side, the limb high in frame, the desert sliding beneath. Ordinary work. | the whole convoy, small | 35 mm, high off the north flank |
| S2 | 6 s | Alongside the Colossus. Morrow rides off her starboard quarter; a Lasher slides through the foreground. | Colossus, Morrow, Lasher | 28 mm, tracking alongside at 300 m |
| S3 | 5 s | Long lens back down at the derelict platform below: dark, stripped. Then drive lights come on in its hangars. | the platform | 135 mm from the convoy's tail |
| S4 **K** | 6 s | First contact: twelve hulls climbing out of the platform toward the column, drives lit, the desert behind them. | Falcon (P), Wolves, Hounds (P), Kites (P) | 35 mm, off their flank |
| S5 | 6 s | The opening: the Manticore (P) fires at long range, and rounds and missiles cross 3-4 km of space. | Manticore (P) | 85 mm along the line of fire |
| S6 **K** | 8 s | The screen breaks: the Phaeton tanker goes up, a Lasher burns, and the lead Lasher turns back into the fire and is hit. | lead Lasher, Phaeton, Lasher A | 35 mm, looking aft along the convoy |
| S7 **K** | 8 s | Boarding the Tarsus: drives shot out, fallen out of the column, Kites (P) alongside, a Wolf (P) covering; the convoy runs on ahead. | Tarsus, Kite (P), Wolf (P) | 35 mm, from behind the Tarsus |
| S8 | 6 s | Close on the Colossus's aft: the port tug pod is shot out, the drives stutter, debris. | Colossus | 50 mm, close |
| S9 **K** | 8 s | She will not be taken: the Colossus Mk.III closes from above; she fights with flares and both turrets and drives off a Kite; Morrow is under her keel holding her up. | Colossus, Colossus Mk.III, Morrow | 28 mm, 600 m off her port side |
| S10 **K** | 10 s | The prize taken: the Tarsus clamped to the Colossus Mk.III; far below, the Colossus nose-down and losing her orbit, Morrow with her. | Falcon (P), Colossus Mk.III, Tarsus; Colossus and Morrow below | 35 mm, behind and above |
| S11 | 10 s | Entry: from just behind, plasma builds round both hulls; Morrow drifts apart from her. | Colossus, Morrow | 50 mm, chase |
| S12 **K** | 10 s | Two trails down the track toward the horizon and the canyon country. Hold. End. | (trails only) | 50 mm from the pirates' orbit |

The crash against the canyon wall is a separate shot. It needs both ships'
deferred pass 5 states (Colossus crashed; Morrow grounded and damaged).

## Hull builds

Orders go to the coordinating session, one per hull (owner, 2026-10-19). The
owner started orders for the Colossus Mk.III, Kite (P), Wolf (P), Falcon (P),
Hound (P) state, Manticore (P) and Enforcer. The Enforcer was cut (owner,
2026-10-19): a finished Lasher leads the escort instead, and the pirates field
no Enforcer. The Kite (P) and Hound (P) are finished, as are the independent
liveries of the Lasher and Hound. The Buffalo Mk.II stays a stand-in. Round 2
added two convoy hulls that are seen close:

- **Tarsus**: the pirates' prize, close in S7 and S10. Detailed, textured and
  with effects, plus a boarded condition (drives shot out, scorched).
- **Phaeton**: the tanker destroyed in S6, at mid-close range. Detailed and
  textured, plus a destroyed condition.

The Mule, Buffalo and Dram are distant and stay stand-ins. The merchant Wolf
takes the Wolf (P) build with a merchant livery. No further hulls are to be
ordered from this blockout without the owner asking.

## Blockout

`battle_blockout.py` builds the scene in Blender and renders the key frames, two
roster sheets (from above and broadside, every hull at true relative size) and
the orbit diagram. Hulls come from `build/asharu-battle/inputs/` (ignored): the
finished Colossus and Shepherd builds (`colossus-cinematic`,
`shepherd-cinematic`) and the Drive builds `kite-p/a48f8757b`, `hound-p/33114de`,
`lasher-independent/7805d3b` and `hound-independent/1e91821`, each placed
through its manifest. A stand-in is the sprite's silhouette raised into a lens
shape and painted with the sprite. It reads as the right hull at the right size
and nothing more. Effects (tracers, blasts, drive plumes, entry plasma) are
placeholders for the VFX kit.

`python battle_blockout.py --check` prints where every ship lands in every key
frame without rendering; `--frames k3b,k5` renders some frames only.

## Open for the owner

- Asharu's size, the 250 km orbit and the low sun.
- The Colossus lost or deliberately dived.
- Whether one of the Lashers comes down too (the story says "several of her
  escorts" came down in the valley).
