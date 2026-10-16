# Story — Atmospheric frigate duel

Status: ACTIVE — slices 1-2 built (hulls as targets for direct fire, stray rounds, falling wreckage, skins; duel helm and variant flux fittings); slice 3 (missiles at hulls) next

Written: 2026-10-16

Updated: 2026-10-16 — slice 2 built (duel helm, variant capacitors and vents) with its readings; slice 1 readings refreshed for the fittings; open questions left to the owner.

Read `air-nouns.md` (**Overhead ships**, **Standing laws**), `warship-behaviours.md`
(the posture model, Classes, Decided: escort, gap G4), `ship-support.md` and
`warship-missiles.md` first. Vocabulary (warship, mount, hull, shield, flux, posture,
doctrine, escort, station, stay, departure, declared threat) is those docs'. This story
adds no parallel noun for a "space combat" mode: a duel is warships over the battle
whose mounts have a second kind of target, a hostile hull.

## Problem

Chapter 5 of the a-small-war arc ("The duel overhead") needs two Wolf (H) frigates
to fight two Hound (P) frigates escorting the Greyhook salvage rig as it climbs out
from the pier, over the company's ground fight. The engine cannot do it: a director
never picks a hull, a hull is not a target for a ship's mounts (gap G4), and Decision
5 put ships fighting ships out of scope. Two hostile frigates on station today ignore
each other until a round meant for the ground happens to land near one.

The chapter's own laws, which this story must deliver as engine behaviour rather than
script:

- Nothing is scripted. A Wolf can be crippled or break off; a Hound can be shot down
  onto the basin; the rig can escape. The debrief reports what happened.
- The Wolfs fight ships only (the Hounds and the rig), never the ground garrison, and
  answer no calls for fire. Stray fire and falling wreckage are real on the ground, in
  both directions.
- Two frigates, one finite visit; they leave when the engagement ends.
- The player cannot order them; they are not a support power.
- Ground victory never depends on the duel.

## The matchup is vanilla's (balance law)

Ship against ship keeps the base game's balance, read from vanilla data
(`warship-behaviours.md`, balance law). The figures that give the duel its shape are
the hull table's, not authored:

| Hull | Hit points | Armour | Shield | Guns that may fire at a hull |
| --- | --- | --- | --- | --- |
| Wolf (H) (`wolf_hegemony` skin of `wolf`, variant `wolf_hegemony_Assault`) | 1500 | 150 | FRONT, 150 degrees, efficiency 0.7 | Pulse Laser (medium hardpoint), Ion Cannon (turret); Sabot pods (missiles, slice 3); never its PD lasers |
| Hound (P) (`hound_d_pirates` skin of `hound`, variant `hound_d_pirates_Standard`) | 2000 | 400 | none | Chaingun (medium hardpoint); never its Light Dual MG, which is point defence |
| Salvage Rig (`crig`, the Greyhook) | 2000 | 200 | none | none |

Speed, acceleration and turn rate are the table's too: the Wolf is the faster and
handier hull, the Hound the tougher and unshielded one. A pulse bolt (100 energy,
penetration 10) against a Hound's 400 armour puts most of itself into the hull under
the mod's armour rule; a chaingun burst into a Wolf's raised front shield becomes
flux at 0.7. The Wolfs must keep their shields facing and their flux down; the Hounds
must get round the shield. That contrast is the drama, and it comes from the data.

## Model (what this story adds to the nouns)

**A hostile hull is a target for a warship's mounts** (closes G4 for direct fire). It
is a third customer of the same mounts beside ground fire and air fire: a mount takes
a hull when its weapon may fire at a ship, the ship's **ship-fire order** allows it,
the hull is in the weapon's reach and the slot's arc, the mount has ordnance and flux
lets the burst start. Which weapons may fire at a ship follows the base game, as the
air rule does: point-defence weapons (`PD` without `PD_ALSO`) never do; everything
else may, missiles once slice 3 gives a missile a hull to fly at. A lance keeps its
ground sweep and is not a ship weapon in slice 1. The order mirrors `AirFireOrder`:
`OFF` (the default in slice 1, so no shipped mission changes), `ALL` (a mission or
script) and `AUTO` (follows the posture once slice 4 decides it).

**A round at a hull is a real round that can miss.** It is aimed at where the hull
will be (its velocity over the round's flight), thrown off by the weapon's own cone
as a hash of who fired what (never the shared random stream), and flies as an
ordinary shell drawn by the existing renderer. On arrival it **hits whatever hostile
hull its blast reaches**, through the existing law: shield first (as flux, inside the
arc), then the armour rule. A hull that has moved, or a round the cone threw wide,
misses, and **a miss carries on and falls to the ground** beyond the hull as an
ordinary detonation: stray fire is real on the ground for both sides. Direct-fire
rounds between hulls are not point-defence targets (in the base game point defence
engages missiles and fighters, not bolts and slugs); a missile at a hull still is.

**A hull taken to nothing falls.** It no longer vanishes in a heavy blast: it bursts,
loses lift, keeps its momentum and tumbles down over a few seconds trailing fire, then
strikes the ground as a crash (blast, crush along its skid, and a wreck that stays),
through the same crash machinery a downed aircraft uses. The crash hurts whoever is
under it, whichever side. Its guns, shield and point defence die with it at the burst.
This applies to every warship, not only frigates: a downed capital over a fort is now
wreckage on the fort.

**A ship duelling a hull points its guns at it.** A hardpoint fires where the nose
points, so a ship with a hull target and no ground area to bring guns to bear on
turns to the heading that brings the most firepower to bear on that hull (the
existing best-heading search), re-chosen as the target moves.

**A skin is its own hull.** A vanilla skin (`hulls/skins/*.skin`, e.g. `wolf_hegemony`)
is fitted from its base hull's spec and table row with the skin's own sprite, so a
Wolf (H) looks like one.

Later slices add the duel helm (manoeuvre), missiles at hulls, the doctrine and pack
roles, the rig and its escorts, the finite visit, authority, and the ledger.

## Scope

In: the engine capability Chapter 5 needs, its evidence, and the mission-facing seam a
battle setup uses to declare the duel. Out: the chapter itself (the a-small-war
stories own it), the Black Sluice map (the Agreus map work owns it), any campaign
writeback for the Wolfs (they are not the company's ships), carriers and fighters in
the duel (the chapter rejected them), hard flux, damage-type multipliers, EMP and the
ion cannon's arcs (not modelled; the ion cannon fires as an energy projectile).

## Constraints

- Balance law: no damage, hit chance or durability is authored per hull or nerfed for
  the chapter. Posture and roles choose; they never scale.
- Nothing scripted: no outcome, kill, escape or timing is forced. A scene may set up a
  battered hull or a position, never an event.
- Determinism: inputs read in stable order, ties by id, every random-looking choice a
  pure hash; two plays of a scene agree.
- No shipped mission or scene changes behaviour until it opts in, except the falling
  wreck, which is a standing law for every warship and is recorded as such.
- Tests stay unit tests under a second; whole-duel questions are a `BehaviorScene`
  under `sceneEvidence` with a `BehaviorSceneSnapshotSuite` for the 1920x1080 picture.

## Plan

1. **Hulls as targets for direct fire, stray rounds, falling wreckage** (built).
   `WarshipShipFire` beside `WarshipAirFire`; `ShipFireOrder`; blast-reach hit on
   arrival, a miss carries on to the ground; direct-fire rounds between hulls are not
   point-defence targets; best heading on the hull target; skin fitting; the falling
   wreck through the crash machinery, drawn as it falls. Evidence: scene
   `frigate-duel` with loops `duel` (two Wolf (H) against two Hound (P) on stations
   within reach, bystanders of both sides under the line), `control` (ship fire off:
   no round at a hull, no hull lost) and `wreck` (a Hound already battered over a
   ground squad, so a fall is observed). Verdicts on mechanics; who lost what is a
   reading.
2. **Duel helm** (built). While a ship has a hull target and no mission station that pins it,
   it manoeuvres: closes to the reach of its best ship-capable guns, keeps the target
   in its hardpoint arc, keeps a front shield toward the threat, and stays inside the
   map. A fast, handy hull (Wolf) holds range and slides across the target's nose; a
   hull quicker in a straight line (Hound) charges. Derived from the hull's own speed,
   acceleration and turn rate; no per-hull script. The fitting also reads the
   variant's flux capacitors and vents (base-game figures), which the stationary duel
   showed the Wolfs' shields need. Verdicts: range closes to reach, the target spends
   most of the engagement inside the hardpoint arc.
3. **Missiles at hulls.** A Sabot (or any missile) fired at a hull flies its own path
   to the led point; a multi-warhead missile splits short of the hull and its warheads
   converge on it; the target's point defence may shoot them.
4. **Doctrine for duels.** `ShipFireOrder.AUTO` engages hostile hulls under air
   superiority (hostile hulls already raise it); the existing ground-fire permission
   `HOLD` keeps a hull off the ground entirely (the Wolfs); withdraw breaks a crippled
   hull off through the ordinary departure. **Pack roles**: a pair hunting one charge
   splits, the nearer running the charge down while the other pins its escorts,
   re-decided as the fight changes. **Escort of a civilian lead**: an escort may
   follow an equal-size support hull (the rig), and chooses between interposing and
   saving itself by the existing withdraw rule. Re-read every shipped warship scene
   before AUTO engages hulls by default.
5. **The rig climbs out.** A civilian hull leaving from the ground along a route to the
   map edge, climbing as departure does, hittable until it is gone; escaping is
   physically departing. Its escorts take it as their lead.
6. **One finite visit, nobody's to command.** A duel ship arrives through the cloud
   deck, leaves through the ordinary departure once no hostile hull has been over the
   battle for a dwell, and is not selectable by remote ship command, direct control
   or any power: an allied hull belongs to its mission, not the player.
7. **The duel ledger.** Per hull: escaped, departed, broke off, or destroyed (when and
   where it fell); ground casualties by side from stray rounds and from wreckage. A
   read-only record for a debrief; nothing on the ground objective reads it.
8. **The whole duel.** The `frigate-duel` scene grown to the chapter's shape: ground
   below a pier, the rig leaving with its two Hounds, the two Wolfs arriving, a ground
   fight beneath, and a control without the duel showing the ground objective
   unaffected. The mission seam is handed to the chapter's builders; the chapter
   itself is not built here.

## Slice 1 as built: what the scene reads

`frigate-duel` (`sceneEvidence -Pscene=frigate-duel`; 1920x1080 GIFs, whole map and
close, under `createSnapshots -Psnapshot=frigate-duel`) plays four loops of 1500 ticks
with no order but `ShipFireOrder.ALL` and stations. Every verdict passes; the
readings are findings, not targets:

- `duel` (two against two, stations 22 cells apart): 167 rounds at hulls (pulse laser
  63, ion cannon 44, chaingun 60), none from a point-defence weapon and none shot
  down; 143 reached a hull and 24 came down on the ground. Both Hounds were lost (the
  first at tick 416) and both fell 4.5 cells from where they burst; the Wolfs lost
  2820 of 3000 hull. Their shields took 13 hits and their flux peaked at 91%, where
  the automatic shield drops, so most chaingun rounds reached the plate.
- `crossing` (one Hound flying back and forth 26 cells off): 4 of 32 Wolf rounds
  missed as it turned and came down beyond it; the Hound's chaingun never reached;
  it fell 15 cells along its track onto its own side's bystanders, six hurt.
- `control` (ship fire off): no round at a hull, no hull lost.
- `wreck` (a Hound set up at a tenth of its hull over its own squad): it burst at tick
  71, struck three seconds later and hurt three of the squad under it.

Findings for the next slices:

- **A stationary slugging match favours the Hound.** Held on stations, the Wolfs
  neither hold range nor keep their shields facing, and they drop their shields at
  90% flux. That is what slice 2 (the duel helm) is for; it is not tuned here.
- **Variant flux capacitors and vents are not read.** `wolf_hegemony_Assault` carries
  seven capacitors and ten vents, which in the base game add capacity and dissipation;
  the fitting reads only the hull table's flux. Reading them is vanilla data under the
  balance law, and belongs with slice 2 (added to its scope).
- **Bolts read as long bars.** The shell renderer draws a pulse-laser bolt as a bar
  most of the gap long at the close view; a presentation question for the duel's
  look, not a mechanic.

## Slice 2 as built: what the scenes read

`frigate-duel-helm` (`sceneEvidence -Pscene=frigate-duel-helm`; whole-map GIF under
`createSnapshots -Psnapshot=frigate-duel-helm`) starts two Wolf (H) and two Hound (P)
eighty cells apart under `ShipFireOrder.ALL` and leaves them to it for 1800 ticks;
`held` is the same with the helm off. The duel scene `frigate-duel` turns the helm
off so it stays the stationary evidence.

- **The helm.** A ship with a hull target flies to a station on a circle about it:
  midway between the two reaches when it outranges the target by two cells or more
  (the Wolf: pulse laser 27 cells against the chaingun's 20), sliding 25 degrees round
  the target's nose each re-choice; 0.6 of its own reach when it does not (the Hound
  closes). The reach is that of the ship's strongest anti-ship mount. Speed, turn and
  braking are the hull's own; the helm only moves the station, and the mission station
  comes back once no hull target remains. A piloted, departing, low-passing or
  remotely ordered ship is not steered by it. Notice range for a ship with the helm is
  the whole map.
- **Variant fittings** are read from the variant (`getNumFluxCapacitors` /
  `getNumFluxVents`, or the stock `.variant`): 200 capacity per capacitor, 10
  dissipation per vent, as the base game. They apply only while the ship's fire order
  is not `OFF`, so every shipped ground-bombardment scene keeps the flux it was tuned
  against (`onslaught-flux` failed its "slower than ungoverned" verdict when they
  applied always). Wolf (H) Assault: 3650 capacity; Hound (P) Standard: 2550.
- **Two aiming faults found by the helm, not by the stationary duel.** The hull was
  turned at the target's centre while the guns fired at the lead point, and the
  heading sweep in 5-degree steps could not land a hardpoint's 5-degree arc; the
  Hounds never fired. The heading now aims at the strongest mount's lead point and
  tries each anti-ship mount's exact heading beside the sweep.
- `helm`: 131 rounds at hulls (chaingun 16, ion 57, pulse 58), 114 on hull, 17 strayed;
  closest approach 12.7 cells; mean engaged range Wolf 18.9, Hound 16.5. Both Hounds
  lost (first at tick 179), no Wolf lost (1858 hull taken); Wolf flux peaked at 71%
  and no shield hit was taken, since the Hounds' fire arrived from outside the front
  arc while the Wolfs slid. The strongest gun was on the target 79% (Wolf) and 87%
  (Hound) of engaged time. Once the Hounds were down the Wolfs flew back to their
  mission stations.
- `held`: no round and no motion.
- `frigate-duel` with the fittings: `duel` 144 rounds, 133 on hull, 11 strayed, both
  Hounds and one Wolf lost, Wolf shield hits 22, flux peak 90%; `crossing` 6 of 34
  strayed, crash hurt 6; `wreck` crash hurt 3.
- All 35 ship and tender scenes (135 loops) pass with the slice in.

## Acceptance

- Two hostile frigates fire on each other with their real hull weapons; point defence
  does not fire at a hull; a FRONT shield absorbs from inside its arc as flux and the
  armour rule takes the rest; missed rounds fall on the ground and hurt whoever is
  there.
- A destroyed hull falls before it strikes, and its crash hurts and marks the ground.
- Ships manoeuvre to fight; the Wolf/Hound contrast in speed and shields shows in the
  readings without an authored number.
- The Wolfs never fire at the ground garrison; nobody can order them; they arrive and
  leave once; the rig can escape and the debrief can say so.
- No shipped scene's verdicts change except where the falling wreck is the point.

## Open questions for the owner

1. Decision 5 ("ships fighting ships is out of scope for now") is lifted by the
   chapter's need. Is that general (any hostile hulls duel under `AUTO` once slice 4
   lands) or only where a mission declares it? **Left for the owner.** Until it is
   answered the shipping default is unchanged: hull fire stays opt-in per mission
   (`ShipFireOrder.OFF` by default); the duel scenes and Chapter 5 set it explicitly.
2. Should a falling capital's crash scale with hull size beyond the aircraft crash's
   cap? Slice 1 reuses the aircraft crash as it stands (radius capped at eight cells).
