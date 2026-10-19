# Asharu battle: the wreckfall

Status: DRAFT — shot plan and first blockout (`battle_blockout.py`), waiting on the owner.
Written: 2026-10-19

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
  ordinary traffic. The pirates want the cargo, and the cinematic never shows
  what it is.
- **Tone** (`shepherd-recovery-dialogue.md`): a small story, grounded and
  low-tech. This is an ordinary raid on an ordinary convoy that went badly for
  everyone, not a great battle. Nobody wins: the pirates lose their prize to
  the atmosphere.

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

## How the convoy is broken

1. **Caught slow.** The convoy is committed to its descent burn: slowed, low,
   with nowhere to run.
2. **Ambush from behind and below.** The pirates light up inside the platform
   the convoy has just passed and climb out of it, closing from astern.
3. **The screen goes first.** The Manticore (P) and the Falcon (P) open at long
   range on the escorts. The Enforcer turns back to meet them and is shot up.
   One Lasher is destroyed. The Hound, at the rear, breaks away.
4. **The freighters run.** The Colossus throttles up and fires flares, and her
   twin autocannons engage. Wolves (P) and Hounds (P) cut across the column,
   and Kites (P) close with boarders.
5. **They shoot out her drive.** The pirates want her whole. They take out her
   port tug pod to stop her running. The Colossus Mk.III, an armed Colossus
   like her, comes alongside to take her.
6. **She falls.** A crippled, slowed freighter in a 250 km orbit does not stay
   there. With one pod gone and the other failing, she loses her orbit. Morrow,
   which has been under her keel holding her up with her own drives, stays with
   her and goes down with her.
7. **Nobody gets the prize.** The pirates break off above an empty sky. Two
   plasma trails run north toward the canyon country.

Whether the Colossus's crew dive deliberately to deny the prize, or simply lose
her, is left open (owner's call). The plan draws it as losing her, because the
story never gives anyone a heroic choice.

## Fleets

Sizes from the finished models where they exist. Unbuilt hulls are sized by
`ship_scale.py` option C from the game sprite's plan area (`roster.py`, about
+-8% in length until each is built and measured). For scale: Hound 61 m,
Colossus 108 m, Onslaught 301 m.

### Merchant convoy (7 hulls)

| Hull | Role | Length | Against known ships | Model |
|---|---|---|---|---|
| Colossus | the principal, heavy freighter | 108 m | the reference | **finished** |
| Shepherd "Morrow" | drone tender, the convoy's repair ship | 67 m | a little longer than a Hound | **finished** (Ko Combine livery) |
| Tarsus | second freighter | 106 m (est.) | as long as the Colossus, much slimmer | stand-in |
| Enforcer | escort lead, destroyer | 95 m (est.) | 0.9 Colossus | stand-in |
| Lasher x2 | escort frigates | 90 m | 1.5 Hounds | **finished** (Hegemony material is a placeholder; needs an independent livery) |
| Hound | rear escort | 61 m | the reference | **finished** (Hegemony auxiliary livery; an independent repaint is a texture job) |

### Pirate raiders (12 hulls)

| Hull | Role | Length | Against known ships | Model |
|---|---|---|---|---|
| Falcon (P) | flagship, light cruiser | 159 m (est.) | 1.5 Colossus; half an Onslaught | stand-in |
| Colossus Mk.III | the prize ship: an armed Colossus come to take the cargo | 107 m (est.) | the Colossus's twin | stand-in; **build from the Colossus geometry** |
| Manticore (P) | long-range destroyer that breaks the screen | 96 m (est.) | 0.9 Colossus | stand-in |
| Enforcer (P) | destroyer | 95 m (est.) | 0.9 Colossus | stand-in; **one Enforcer build serves both fleets** |
| Buffalo Mk.II x2 | destroyers | 87 m (est.) | 0.8 Colossus | stand-in |
| Wolf (P) x2 | fast frigates that cut the column | 86 m (est.) | 1.4 Hounds | stand-in |
| Hound (P) x2 | frigates | 61 m | the reference | **finished Hound geometry**; pirate state not built (tinted in the blockout) |
| Kite (P) x2 | boarding shuttles | 55 m (est.) | 0.9 Hound | stand-in |

All the pirate hulls are vanilla `pirate_bp` skins or pirate hulls (Colossus
Mk.III), plus the Buffalo Mk.II, which pirate fleets field. Left out on purpose: the
phase frigates (Afflictor, Shade and Gremlin (P)), because a phase ambush is
a different film from this one; and the Venture Mk.II and Atlas Mk.II, which
would make the raid read as a pirate armada. Talons (finished) are available
if the shot wants fighters.

## Shot list

About 90 seconds. **K** marks a shot with a blockout key frame in the review run.

| Shot | Length | Picture | Ships seen closest | Camera |
|---|---|---|---|---|
| S1 **K** | 8 s | Establishing: the convoy in column over the day side, the limb high in frame, the desert sliding beneath. Ordinary work. | Colossus, Morrow, Tarsus, Lasher | 35 mm, high behind the column, slow drift forward |
| S2 | 6 s | Alongside the Colossus. Morrow rides off her starboard quarter; a Lasher slides through the foreground. | Colossus, Morrow, Lasher | 28 mm, tracking alongside at 300 m |
| S3 | 5 s | Long lens back down at the derelict platform below: dark, stripped. Then drive lights come on in its hangars. | the platform | 135 mm from the convoy's tail |
| S4 **K** | 6 s | First contact: twelve hulls climbing out of the platform toward the column, drives lit, the desert behind them. | Falcon (P), Wolves, Hounds (P), Kites (P) | 35 mm, off their flank |
| S5 | 6 s | The opening: the Manticore (P) fires at long range, and rounds cross 3-4 km of space. A Lasher is hit. | Manticore (P), Lasher | 85 mm along the line of fire |
| S6 **K** | 8 s | The screen breaks: the Enforcer turns back into the fire and is hit; Lasher A goes up; the freighters keep running. | Enforcer, Lasher A, Colossus | 30 mm, looking aft along the convoy |
| S7 | 8 s | The freighters run: the Colossus throttles up and fires flares, her turrets track; Wolves cut across, Kites close. | Colossus, Wolf (P), Kite (P) | 35 mm, handheld-feel follow |
| S8 | 6 s | Close on the Colossus's aft: the port tug pod is shot out, the drives stutter, debris. | Colossus | 50 mm, close |
| S9 **K** | 8 s | The prize: the Colossus Mk.III closes from above to take her; Kites (P) carry the boarders; Morrow is under her keel, drives lit, holding her up. | Colossus, Colossus Mk.III, Morrow, Kite (P) | 28 mm, 600 m off her port side |
| S10 **K** | 10 s | The fall: over the Falcon (P)'s shoulder, the Colossus nose-down and losing her orbit, Morrow still with her, the pirates breaking off above. | Falcon (P), Colossus, Morrow, Colossus Mk.III | 35 mm, behind and above |
| S11 | 10 s | Entry: from just behind, plasma builds round both hulls; Morrow drifts apart from her. | Colossus, Morrow | 50 mm, chase |
| S12 **K** | 10 s | Two trails down the track toward the horizon and the canyon country. Hold. End. | (trails only) | 50 mm from the pirates' orbit |

The crash against the canyon wall is a separate shot. It needs both ships'
deferred pass 5 states (Colossus crashed; Morrow grounded and damaged).

## Pirate hull build list (proposal; the owner decides)

In priority order: ships seen closest and longest first. Each hull gets its own
ship-model-pipeline session.

1. **Colossus Mk.III**: seen closest of all the pirates (S9, S10), right beside
   the finished Colossus. It is the same hull with pirate armour and guns, so it
   starts from the Colossus geometry. This is the cheapest and most valuable
   build.
2. **Kite (P)**: the boarding shuttles, close in S7 and S9 and in frame for
   long stretches. Small and simple.
3. **Wolf (P)**: the fast frigates that cut the column, close in S6-S9.
4. **Falcon (P)**: the flagship, framed large in the foreground of S10 and
   mid-distance in S4.
5. **Hound (P)**: a pirate state of the finished Hound (texture and damage),
   not a new hull. Mid-distance in S4 and S7.
6. **Manticore (P)**: opens the fight in S5, at mid-to-long range.
7. **Enforcer / Enforcer (P)**: one build with two liveries. The merchant
   Enforcer is close in S6, so this one serves both fleets.
8. **Buffalo Mk.II**: background only; it can stay a stand-in.

Merchant side, not pirate but needed: the **Enforcer** (above), a **Tarsus**
(background, can stay a stand-in), and independent liveries for the
**Lashers** and the **Hound**.

## Blockout

`battle_blockout.py` builds the scene in Blender and renders the key frames, two
roster sheets (from above and broadside, every hull at true relative size) and
the orbit diagram. Hulls come from `build/asharu-battle/inputs/` (ignored): the
finished Colossus and Shepherd builds (`colossus-cinematic`,
`shepherd-cinematic`), the Lasher build on Drive (`builds/lasher/4e2ed50`) and
the skinned Hound. A stand-in is the sprite's silhouette raised into a lens
shape and painted with the sprite. It reads as the right hull at the right size
and nothing more. Effects (tracers, blasts, drive plumes, entry plasma) are
placeholders for the VFX kit.

`python battle_blockout.py --check` prints where every ship lands in every key
frame without rendering.

## Open for the owner

- Pirates as the attackers, and the two rosters above.
- Asharu's size, the 250 km orbit and the low sun.
- The Colossus lost or deliberately dived.
- Whether one of the Lashers comes down too (the story says "several of her
  escorts" came down in the valley).
- The build list order.
