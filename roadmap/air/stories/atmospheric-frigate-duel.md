# Story — Atmospheric frigate duel

Status: ACTIVE — slices 1-5 built (hulls as targets for direct fire, stray rounds, falling wreckage, skins; duel helm and variant flux fittings; missiles at hulls; doctrine for duels except `AUTO`; the rig's climb-out and vanilla missile magazines); slice 6 (one finite visit) next

Written: 2026-10-16

Updated: 2026-10-16 — slice 5 built (climb-out, escorts follow a climbing lead, vanilla missile magazines under hull fire) with its readings; open question 3 (penetration scale between hulls) added.

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
3. **Missiles at hulls** (built). A Sabot (or any missile) fired at a hull flies its own path
   to the led point; a multi-warhead missile splits short of the hull and its warheads
   converge on it; the target's point defence may shoot them.
4. **Doctrine for duels** (built, except `AUTO`). `ShipFireOrder.AUTO` engages hostile hulls under air
   superiority (hostile hulls already raise it); the existing ground-fire permission
   `HOLD` keeps a hull off the ground entirely (the Wolfs); withdraw breaks a crippled
   hull off through the ordinary departure. **Pack roles**: a pair hunting one charge
   splits, the nearer running the charge down while the other pins its escorts,
   re-decided as the fight changes. **Escort of a civilian lead**: an escort may
   follow an equal-size support hull (the rig), and chooses between interposing and
   saving itself by the existing withdraw rule. Re-read every shipped warship scene
   before AUTO engages hulls by default.
5. **The rig climbs out** (built). A civilian hull leaving from the ground along a route to the
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

## Slice 3 as built: what the scene reads

`frigate-missiles` (`sceneEvidence -Pscene=frigate-missiles`; whole-map and close GIFs
under `createSnapshots -Psnapshot=frigate-missiles`) holds a Wolf (H) on station 38
cells from its target, outside every gun's reach on both sides and inside the Sabot's
54, for 1500 ticks. Loops: `sabot` (a Hound (P) on station), `crossing` (the Hound
flying back and forth 16 cells either side of the Wolf's bow), `harpoon` (a Brawler
(Elite) firing Harpoons at the Wolf, whose PD lasers may answer; its heavy mauler
reaches too) and `control` (ship fire off).

- **The rule.** A missile may fire at a hull, except a standoff missile, whose beam is
  a ground payload. A magazine is not a sustained gun, so the hull's heading and the
  duel helm's reach stay the guns' alone. A guided missile launches inside its slot's
  arc widened by 20 degrees, because it turns onto the hull after launch. It flies its
  own path to where the hull will be after the missile's own flight time (for a
  carrier, the flight to the split plus the warheads' flight). It is not direct fire,
  so the target's point defence may shoot it. A Sabot's carrier splits at the spec's
  split range short of the hull, and its five warheads are aimed **when it splits**,
  at the hull as it is then, led by their own flight. They are spread evenly across
  the spec's 20-degree arc with a hashed jitter, so the hull's size and the arc decide
  how many strike. A warhead that reaches no hull carries on to the ground. A carrier
  whose hull is gone by the split sends its warheads where it was going.
- `sabot`: 10 Sabots, 4 splits at a live hull, worst split centre 0.47 cells from the
  hull's led centre. 12 warheads on the hull, which took it (2000) without a gun; 38
  strayed to the ground, 30 of them from carriers whose hull was already falling.
- `crossing`: 6 Sabots, 4 live splits (worst 0.38 cells); 12 of the 20 live warheads
  struck the moving Hound, and it fell.
- `harpoon`: both Harpoons were shot down by the Wolf's PD lasers (2 engagements, 2
  intercepts); the Wolf's Sabots took the Brawler; Wolf shield hits 6 (heavy mauler).
- `control`: no missile, no round.
- With missiles in, `frigate-duel` `duel` reads 102 rounds (12 Sabots), both Hounds
  lost and Wolfs 120 hull down; `frigate-duel-helm` `helm` 109 rounds (16 Sabots),
  closest approach 16.2, both Hounds lost, Wolfs 2395 hull down. All 36 ship and
  tender scenes (139 loops) pass.
- **A finding for the look.** The Sabot's split flash is the carrier's spec visual
  radius, very large at the close view; a presentation question, not a mechanic.

## Slice 4 as built: what the scene reads

`frigate-doctrine` (`sceneEvidence -Pscene=frigate-doctrine`; GIFs under
`createSnapshots -Psnapshot=frigate-doctrine`) sets a Salvage Rig (`crig`, held on
station) with two Hound (P) escorts (an explicit escort relation) against two Wolf (H)
starting 94 cells west. Between them is a hostile garrison of 24. Every hull runs the
ordinary warship doctrine under `ShipFireOrder.ALL`. The Wolfs carry a ground director
and ground fire `HOLD`. Loops: `pack`, `withdraw` (every Hound starts at 40% hull) and
`ground-allowed` (the `pack` fight with the Wolfs' ground fire at `ALL`). 1800 ticks.

- **`AUTO` is not built.** Whether hostile hulls duel by default is open question 1,
  left for the owner. Hull fire stays opt-in by mission (`ShipFireOrder.OFF` by
  default), and nothing in this slice changes a shipped mission's default.
- **Pack roles.** When two or more ships of a side hunt with the duel helm (not
  escorting) and a hostile hull is being escorted (the *charge*), the hunter nearest
  the charge runs it down and each other hunter pins the charge's escort nearest to
  itself. This is re-decided every tick. The runner keeps the role until another hunter
  is nearer the charge by 6 cells. A lone hunter fights the nearest hull, and so does a
  pack once the escorts are gone.
- **Escorts interpose.** An escort with a live lead holds its ring on the threat side
  (hostile hulls already set the threat axis) and fights from there. The duel helm does
  not steer it.
- **Ground fire `HOLD`** keeps a hull off the ground entirely; nothing new was needed.
  The slice adds a per-ship ground-shot count so a scene can show it.
- **Withdraw** is the existing doctrine rule: under 35% hull a ship's posture becomes
  `WITHDRAW` and it leaves through the ordinary departure, firing nothing new. A
  departing hull is still a hostile hull, and it can be shot as it goes.
- **The Salvage Rig's spec file is `constructionrig.ship`.** Fitting looked hull files
  up by id and could not fit it. The few vanilla hulls whose file is not named by id now
  resolve through `WarshipSkins.hullFile`.
- `pack`: in all 335 ticks where both Wolfs hunted while the rig and an escort lived,
  one Wolf was on the rig (the nearer) and the other on an escort. The escorts stood on
  the Wolfs' side of the rig in all 374 settled escort-ticks. The Wolfs fired 0 ground
  shots and passed directly over the garrison; all 24 garrison troops lived. 84 rounds
  and 28 missiles went at hulls. The rig and both Hounds were lost (one Hound after
  breaking off), and the Wolfs lost 240 hull. They then flew back to their mission
  stations.
- `withdraw`: 2 hulls crossed the withdraw line alive, and both were leaving within 75
  ticks; 0 shots came from hulls already leaving.
- `ground-allowed`: the same fight, with 46 Wolf ground shots and the garrison wiped
  out. This is the control showing the hold is what spares it.
- All 37 ship and tender scenes (142 loops) pass.
- **A finding for the look.** Two hunters whose stations both lie near the rig can
  pass through each other on the way in. Hulls do not separate from friendly hulls;
  this is a presentation question for slice 8, not a mechanic here.

## Slice 5 as built: what the scene reads

`frigate-climb-out` (`sceneEvidence -Pscene=frigate-climb-out`; GIFs under
`createSnapshots -Psnapshot=frigate-climb-out`) sets a Salvage Rig with two Hound (P)
escorts at mid-map. At tick 60 the rig is sent out along a two-leg route over the east
edge. Loops: `escape` (nobody hunting), `hunted` (two Wolf (H) from the west, under ship
fire with the duel helm) and `held` (the control: the rig is not sent). 2400 ticks.

- **The climb-out** (`Warship.climbOut`) is a departure: no order, nothing new fired.
  It is flown as a through-route at the hull's own speed, with the last leg carried on
  past the edge, so escaping is the whole hull carried off the map by its own
  propulsion (`physicallyDeparted`), never the departure timeout. The hull stays a hull
  until it is gone: it can be hit, and its escorts keep it as their lead. The climb is
  drawn by the share of the route flown, not by the six-second departure clock, so a
  slow hull is still low while it is still near (the shadow, haze and wake read it from
  `climbSeconds`).
- **Escorts.** A lead climbing out remains a live lead, so its escorts hold their ring
  around it on the way out. An escort whose lead has escaped follows it out the way it
  went. Any other departure of the lead ends the relation, as before.
- **Missiles carry their vanilla magazines under hull fire.** Missiles had been
  unlimited unless a mission called `limitOrdnanceToWeaponAmmo`. A ship whose fire
  order allows hulls now loads each missile mount's weapon-table magazine (the Wolf's
  two Sabot racks hold 3 each), the same gate as the flux fittings. Ground bombardment
  keeps its tuned figures. The slice 3 and 4 readings above were taken with bottomless
  racks. With magazines, `frigate-missiles` `sabot` fires 6 Sabots (4 live splits, 12
  warheads on the hull, which still takes the Hound) and `frigate-doctrine` `pack`
  fires 12 missiles and 68 rounds. The verdicts are unchanged.
- `escape`: the rig was physically gone at tick 1100. In 2080 escort-ticks of its climb
  the Hounds never lost it as lead, and both followed it over the edge. At half the
  route flown the climb read 0.51.
- `hunted`: the Wolfs reached the climbing rig, and it lost all 2000 hull while
  climbing. It fell at tick 230, in reach of a Wolf for 41 ticks. Both Hounds were lost
  too.
- `held`: nobody left.
- All 38 ship and tender scenes (145 loops) pass.

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
3. **Damage between hulls uses the ground penetration scale.** The armour rule takes
   penetration from the weapon's damage type at the figures tuned for ground fire
   (kinetic 12, energy 10, high explosive 8). Against hull plate of 150 to 400, armour
   barely matters, and kinetic beats high explosive. The base game's damage-type
   multipliers (kinetic weak against armour and strong against shields, high explosive
   the reverse) are not modelled between hulls. So the Sabot, an anti-shield weapon in
   the base game, takes an unshielded Hound, and the rig dies in two seconds. Should
   hull against hull read the vanilla multipliers (a contained change to
   `WarshipSystem.strikeHulls` and the shield absorb)? It would change every duel
   reading, and any shipped scene where ground batteries hit hulls.
