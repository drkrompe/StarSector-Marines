# Warship behaviours — postures for normal fights and missions

Status: ACTIVE

Written: 2026-10-11

Updated: 2026-10-13 — owner decisions: the balance law is standing, strike sorties do not track a moving hull (decided), and shield weights now derive from each hull's vanilla shield arc and efficiency instead of a table.

Read `air-nouns.md` (**Overhead ships**) first. Vocabulary (warship, mount, area order, director, point defence, flux) is that doc's. `warship-missiles.md` owns what a missile round does; `warship-on-station.md` owns the body, orders and acceptance scenes. This doc adds the layer that decides **what the ship is trying to do**, so that a capital in an ordinary fight or mission behaves like a ship with a job and not like a density-seeking turret.

**Balance law (standing, owner, 2026-10-13).** Ship against ship in atmosphere keeps the base game's balance, taking numbers and hints from vanilla data rather than authored tables. Ground against ship is weaker (at least until planetary defences are built): a ship is hurt only by ordnance, through the armour rule. Ship against ground is stronger: in-universe these are essentially superweapons, and ships are overpowered against the ground by design. Posture and class weights never change any of this; they decide what a ship chooses, never how hard anything hits.

## Why this doc exists

Today a directed warship fires at the densest hostile cluster in reach, forever, and nothing else about the battle changes what it does. Owner direction, verbatim:

> (1) is there an air fight to win - or has air superiority been achieved? If there is still a threat for the ship class in atmo on the map then it should likely continue with its default air to air behaviors and only take shots of opportunity when applicable. Flux provides a good reason for a ship not to waste its ordinance on ground targets and likewise to stay out of range of ground defenses when there is a threat also in the air as it needs to prepare for a potential brawl. There are support ships and carriers as well of course which change this behavior. (2) In the absence of an air target is the ship on the attacking or defense for the mission objective? If this is a defense mission its job may be to attempt to intercept enemy shuttles delivering ground forces or otherwise launching long range or close range assaults on positions.

Standing owner direction that this doc must not contradict: ships are overpowered by design; they are gated by role-play context and later by which fleet ships the player brings; no command power until general ship support exists; shields are a major flux draw (being built now). **A posture changes what the ship chooses to do and when, never how hard a gun hits.**

## What the simulation has today (honest inventory)

Read from `src/main` on 2026-10-11. This is the ground the proposal stands on, and it is thinner than the owner's framing assumes.

**Ship against aircraft: built (slice 3), and opt-in by order or posture.**

- A warship's area-fire mounts choose targets only from the unit roster's hostile ground bodies (`WarshipSystem.acquire`) or standing wall cells (structural bombardment); its director never picks aircraft. Aircraft are a second customer of the same mounts, in `WarshipAirFire`, under the weapon rule of `WarshipAirRules` and an `AirFireOrder` (see Decision 1 and what shipped). Nothing changes for a ship under no air order and no air posture.
- Point defence shoots hostile in-flight `Projectile`s that opt in and hostile `WarshipShell`s; ordnance always outranks aircraft for a point-defence mount.
- `WarshipSystem` and `Warship` still do not appear anywhere under `air/`, `flyby/`, `turret/`, `weapon/` or `grenade/`; the one seam into the air layer is the aimed-damage entry point on the shared body damage route.

**Aircraft against ships: built (G3).**

- A hostile strike sortie may choose a hull as its target (`HullStrike`, asked by both `AirStrikeSystem` and `AirCoverSystem`). A hull is a candidate beside the densest ground concentration and takes precedence when eligible: hostile, not destroyed or departing, hurtable by the craft that would fly (`Warship.effectiveDamage > 0` for its `AirOrdnance`, so a beam that glances off the plate is never sent), and fewer than two of the side's craft already working it (the rest go to the ground). Shuttles and boats never attack a hull. The target is the hull's centre when the sortie is dispatched; a moving hull is not tracked.
- A hull is hurt only through `WarshipSystem.onDetonation`, as it always was: any detonation by a hostile faction whose blast reaches the hull footprint, shield first (as flux on the pool) then the armour rule. The attack run is the ordinary one, so aircraft weapons are unchanged and there is no second route.
- A warship is not a body. It is not in the unit roster, not an air entity, not in `EngagementService`, not in `getAirEntityIds`. Nothing can *target* it; it can only be *splashed*.

**Ship against ship: incidental only.**

- A hostile ship's shells are point-defence targets, and a shell that lands near a hostile hull damages it. A director never picks a hull as ground to fire on. Two hostile capitals ignore each other until a round happens to land.

**Ground against aircraft: defence posts only, as an area drain.**

- `AirSystem.tickAirThreat`: each airborne air entity takes `AA_DPS_PER_POST` (6 hp/s) for every hostile alive turret (`UnitType.isTurret`, via `EngagementService.reachesAltitude`) within `AA_THREAT_RADIUS_CELLS` (14 cells). Not projectiles, no line of sight, no lock. Infantry, mechs, vehicles and drones cannot engage aircraft. Altitude as a per-weapon capability is the stated extension point and is unauthored.
- That field applies to every air entity (shuttles, fighters, boats) and **never to a warship**. A defence post cannot hurt a capital. So "stay out of range of ground defences" has no cause in the simulation today.

**Enemy air that does exist.**

- Hostile shuttles: defenders reinforce through `ShuttleMeans` (a `ReinforcementService` means) and defenders' airfields launch strike sorties through `AirStrikeSystem` (constructed for `Faction.DEFENDER`). A hostile shuttle in flight is a `ShuttleMission` (`state` in PENDING, LOADING, PAD_ASCENT, INCOMING, PAD_DESCENT, LANDED, DEPARTING; `lzX`/`lzY`, `marinesRemaining`, `payload`, faction via `world.airFaction`, position in `AirBody`). `ObjectiveFactionPresence` already walks `getAirEntityIds` this way to decide whether a side has inbound payload.
- Hostile fighters: `FlybyRoster` carries wings with a `side`, and `BattleSetup` installs the enemy-side roster from the defenders' `enemyFighterSupport`. `AirCoverSystem` flies them on `AirCorridor`s from off the map to strike the densest opposing ground concentration. There is no air-to-air: `air-nouns.md` lists wing composition, air-to-air and formation as future.
- There is **no query** for "hostile armed aircraft over the map" or "loaded hostile shuttles with an ETA". Both are a few lines over `getAirEntityIds`, but nothing publishes them.

**Hull class data: unread.**

- `Warship.fit` reads `width`, `height`, `center`, sprite and weapon slots from the `.ship`, and from `ship_data.csv` flux, speed, turn, hit points and armour. It does **not** read `hullSize`/designation, `hints` (the CARRIER hint), `fighter bays`, the ship system id, or the shield columns (the shields session reads the last as it lands). The only `hints` read is a weapon's.
- *(Read since the class-bias slice; see Classes below.)* Vanilla gives the shapes this needs: Frigate (Lasher, Tempest), Destroyer (Enforcer), Cruiser (Aurora, Heavy Cruiser Dominator), Battleship (Onslaught, Paragon, Pegasus), Battlecruiser (Conquest, Odyssey 2 bays), Light/Fast Carrier (Condor 2, Heron 3 bays, hint CARRIER), Carrier (Mora 3), Battlecarrier (Legion 4 bays, hints CARRIER and COMBAT), Phase Cruiser (Doom, shield PHASE).

**Mission and objective model.**

- Mission type is **not on the simulation**. `MissionType` (ASSAULT, SABOTAGE, RAID, EXTRACTION, CONQUEST) is setup-time only; the type is encoded by which commander `BattleSetup` installs (`AssaultCommand`, `RaidCommand`, a `*DefenderCommand` for the other side). Relief and counterattack are commander strategies and reinforcement triggers, not types.
- Sides: `Faction.MARINE` is always the attacker, `DEFENDER` the defender, `ALLY` rides with the marines. There is no "is this faction attacking" method; it is `MARINE.friendlyTo(f)`. A defender-side or neutral capital is a new case that only a mission can make.
- Objectives (`BattleView.getObjectives()`) carry `owningFaction()`, positions and completion; objective ground is also tactical nodes (`TacticalMap`, `TacticalNode` kinds COMMAND_POST, OBJECTIVE, BEACHHEAD, AIRBASE, `mustHold`) and compounds (`CompoundService`). Commanders issue squad-scoped `ObjectiveAssignment`s on a 2.5 s cadence. Nothing issues a ship anything but a scripted `ColdAccountIntervention` phase machine or the debug panel.

## The posture model

A **posture** is the ship's current job. It is a small sticky value, chosen by a **doctrine** from a snapshot of the battle, and it does exactly one thing: sets the parameters of the ordinary ship machinery that already exists (the director's areas and radius, an opportunity range, a flux ceiling, a station, a shield policy). It never fires a gun itself and it issues no order a mission could not write by hand.

The owner's two questions become one ordered decision:

```
0. Is the ship in trouble?            -> WITHDRAW        (self-preservation)
1. Is there an air fight to win?      -> AIR_SUPERIORITY (conserve, standoff, opportunity fire)
2. Else: what is the ship's stance on the objective?
     attacking                        -> SUPPORT         (fire where the assault is)
     defending                        -> INTERCEPT       (deny the drop and the assault)
     no stance, no objective          -> BOMBARD         (today's density director; the default)
```

Ship class (below) is **not a posture**. It biases the assessment and the parameters, so the posture list stays five long.

### Air-superiority assessment (question 1)

"Is there an air fight to win" is a score over **air threats to this ship class**, with hysteresis on it. It is deliberately a read-only snapshot (`AirThreatPicture`) over things the simulation already tracks, so the posture layer owes the air model nothing.

What counts, and what exists to read today:

| Candidate threat | Counts for a ship when | Readable today? |
| --- | --- | --- |
| Hostile warship | always, scaled by class and hull fraction | yes: `WarshipSystem.ships()` filtered by faction |
| Hostile armed fighter / strike craft airborne | the craft carries ordnance that gets through this hull's armour (built) | yes: `getAirEntityIds`, the craft's `AirOrdnance` against `Warship.effectiveDamage` |
| Hostile shuttle in flight | never a threat to the ship; it is an *objective of interception* (INTERCEPT) not an air-superiority one | yes |
| Hostile anti-air emplacement (defence post) | it can hurt the ship | **no**: posts drain aircraft only; see gap G5 |
| Own side's air (fighters, shuttles) | never a threat; counts as **cover to protect**, which raises the stakes of leaving the air fight | yes |

Class matters. A Frigate fears a fighter wing; a Battleship under a shield does not, but it does fear another capital. So the score is `sum(threat_i * weight(shipClass, threatKind))` with weights a small table, not a per-hull number. The same table is where "support ships and carriers change this behaviour" lives: a carrier's weight on hostile fighters is high and its weight on ground clutter is nearly zero; an escort's weight on anything that targets its lead is high.

Because the model today cannot make air hurt a ship, the first real assessment is honest about being nearly empty: the only live threats are hostile ships, plus a *declared* threat a mission or a scene can raise (`ship.declareAirThreat`, for scripted and campaign contexts the owner described: the fleet being busy with hostile fleets). That is enough to make the posture selectable, observable and tested before any new air system exists, and it means the doctrine layer does not wait for the gap work below.

**Air superiority is achieved** when the score has stayed below the exit threshold for a dwell time. It is not achieved by the threat leaving the *sky* for one tick (a wing between sorties, a corridor craft off the map between passes): a rearm interval is a pause in the fight.

### Objective role (question 2)

The default stance is **side**: `MARINE` and `ALLY` attack, `DEFENDER` defends. That is the only stance the simulation can answer without new data. It is wrong for relief and counterattack missions (where the company's side defends or the defender attacks), so the stance is an explicit **mission-supplied override** (`ship.stance(...)`), exactly like the existing scripted orders. See the questions for the owner.

Where the objective is: objective positions from `BattleView.getObjectives()` and the tactical map (`mustHold` nodes, the command post, the landing zone for the defender). The attack/defend question is therefore answered once at launch by whoever fits the ship, and re-read from the live objective set for the *where*.

## Behaviours of each posture

### AIR_SUPERIORITY

The ship is preparing for, or in, a fight with something that can hurt it. Its guns are for that fight.

- **Conserve flux.** The soft stop-starting threshold drops from the shipped 85/55 to a lower pair (initially 50/30), so the pool is mostly empty when the brawl arrives. Overload is never reached on ground fire alone. This is a rhythm lever, not a damage change.
- **Shots of opportunity only.** Ground targets are engaged only inside a short `opportunityRange` (initially a fraction of the longest reach, favouring the secondary battery and point defence) and only singly: the director holds at most one area, small, and never swings the heading to bring guns to bear on ground. The ship does not turn its broadside away from the air threat axis to shell infantry.
- **Standoff.** The station is chosen on the far side of the ship's own force from the hostile threat's expected approach, outside the hazard radius of any hostile emplacement *that can actually hurt it* (empty set today; a real input once G5 exists). Until then standoff is from hostile ships only.
- **Shield posture.** Shield ready, not up, with a larger raise margin than in other postures. The shield session owns raise/lower mechanics; the posture only sets the policy. Shield upkeep and absorption already draw on the flux pool, which is why conserving flux and shield readiness are the same lever.
- **Point defence stays on**, independent of posture (standing rule).
- **Never abandons cover.** If own aircraft or boats are in the air and the ship is the only thing between them and a threat, withdrawing is a heavier decision than for a lone ship (see WITHDRAW).

### INTERCEPT (defender with no air to win)

A defending ship's job is to stop the assault arriving, then to hurt it where it lands.

- **Inbound drop denial.** Watch hostile loaded shuttles (`ShuttleMission` in INCOMING or PAD_DESCENT with payload) and their landing zones. What a ship can always do is **deny the landing zone**: order an area on the LZ timed so the shell's fall time lands it on touchdown or deboarding. Rounds already have a telegraphed fall; the timing is `ETA - flight time`. That is a ground-effect intercept, and it is honest about being one. Since slice 3 the ship also shoots the boat in flight with the weapons the air rule allows; denial stays for what cannot shoot air (a ship whose air order is off, a boat out of reach of every weapon that may fire on it, and the guns the rule keeps silent).
- **Which shuttle, by value.** Rank candidate drops by `payload value / time to touchdown`: seated marines (`marinesRemaining`), a mech or vehicle payload, whether the drop is the objective's only approach, and how close the LZ is to a held compound. A drop with campaign-named soldiers is worth more than a generated one, but whether the *ship* should know that is a fairness question (questions for the owner). Ties go to the nearer.
- **Window.** From first INCOMING to the squad leaving the pad. After the squad is out it is ordinary ground, and the director's density pick takes over with an objective bias towards the defended node.
- **Assaults on positions.** Long-range assault (an attacker battery or hostile ship working a defended compound) is a threat to the *objective*; counter-fire against it needs ships as targets (G4). Until then the ship simply covers the defended ground (SUPPORT with the stance flipped: bias to *defended* nodes and the approaches to them).
- INTERCEPT is where the ship shoots a loaded hostile shuttle down before touchdown (built); the passengers are lost with it, which is the point and the cost. It engages loaded boats only: an armed fighter is the air-superiority posture's business.

### SUPPORT (attacker, or defender covering held ground, with no air to win)

The shipped director with two corrections, both from the owner's direction.

- **Prefer where the objective is.** Candidate areas are scored by hostile density plus an *objective weight*: proximity to the assault's current objective node (attacker), to the defended node and its approaches (defender), to a squad that has asked for support. The pick is still an ordinary `FireOnArea`.
- **Avoid avoidable friendly fire.** Friendly fire exists and is the point of a superweapon, but the director must not fire into its own people when another choice is equally good. A candidate is penalised for each friendly body inside the area plus the blast margin, and refused when friendlies exceed a share of hostiles; shells already in flight from friendly ships count as hazard too. If *every* candidate is dirty the ship holds that area rather than picking the least bad one, unless a mission has authorised danger-close (an explicit flag). The owner accepts friendly fire as a fact of the weapon; this is the behaviour being able to avoid what is avoidable, not a rule that makes it safe.
- **Fire support rhythm.** Standard flux ceiling (85/55 less the shield reserve), up to three areas, heading chosen to bear.

### BOMBARD

Exactly the current director with no objective bias and no friendly weighting. It is the default for a ship with no stance and no objective (cinematics, debug, a scripted flyby), and the control loop for every posture scene. Nothing shipped changes behaviour when a ship has no doctrine.

### WITHDRAW (disengage and self-preservation)

- **Entry:** hull fraction below 0.35, or overloaded with a live threat, or shield down and a threat inside its reach. **Exit:** hull above 0.5 *and* flux below 40% *and* no threat inside reach, held for a dwell. Entry and exit differ so it does not flap, and a withdrawing ship that is *not* threatened by anything much (the owner's overpowered ships) rarely enters it.
- **Action:** hold fire on ground, point defence on, station away from the threat along the line from the threat's centroid through the ship, to the map edge if need be; a mission-bounded stay (`stayFor`) still ends the way it does today. Whether the ship ever leaves the battle for good is a mission choice.
- A ship protecting its own air cover or a landing it has committed to is slower to withdraw. That asymmetry is a parameter on the entry threshold, not a new state.

### Classes: carriers, support ships, escorts

**Built: the class and its threat weights (`ShipClass`, `AirThreatWeights`).** `Warship.fit` derives a `ShipClass` once from the hull's `hullSize` (`.ship`) and the `designation`, `hints`, `fighter bays` and `shield type` columns of `ship_data.csv`. A hull with no table row keeps `ShipClass.DEFAULT`, a combat capital, which is the row the threat weights were tuned against, so nothing changes for a ship fitted without data.

- **Size:** FRIGATE, DESTROYER, CRUISER, CAPITAL (a fighter-size hull reads as a frigate; an unknown size reads as a capital).
- **Role:** SUPPORT if the hints say `CIVILIAN` or the designation names a tender (the Shepherd carries `CARRIER` and one bay but is a builder, so tender wins); else CARRIER if the hints say `CARRIER` or the hull has three or more bays; else COMBAT. One or two bays without the hint (Venture, Odyssey) is a gun ship with a wing; a `COMBAT` hint beside `CARRIER` (Legion, Mora, Drover) does not change the role.
- **Shield type** (NONE, FRONT, OMNI, PHASE) scales the armed-craft weight (see below).

The weight table scales the base weights in `AirThreatPicture` (hostile ship 1.0 at full hull, armed craft 0.5 each capped at 2.0 before the multiplier, declared strength as stated). Declared threats and hostile ships count the same for every class; only armed craft differ:

| Class | declared | hostile ship | armed craft |
| --- | --- | --- | --- |
| Combat capital (the identity row) | 1.0 | 1.0 | 1.0 |
| Combat cruiser | 1.0 | 1.0 | 1.25 |
| Combat destroyer | 1.0 | 1.0 | 1.5 |
| Combat frigate | 1.0 | 1.0 | 2.0 |
| Carrier, any size | 1.0 | 1.0 | 2.0 |
| Support, any size | 1.0 | 1.0 | 1.5 |

**The hull's own shield scales the armed-craft column again (built, owner decision):** the factor is `1 - 0.4 * coverage * absorption`, read from the hull's vanilla `shield arc` and `shield efficiency` rather than a table by type. Coverage is arc over 360. Absorption is `min(1.25, 1/efficiency) / 1.25`, so vanilla's ordinary efficiency of 1 earns 0.8 and 0.8-or-better earns full marks. No shield is exactly 1.0 (the identity); a blank arc or efficiency column takes the shield model's own default (front 180, omni 360, efficiency 1). Examples: Onslaught (front, 180, 1.0) 0.84; Paragon (omni, 360, 0.6) 0.60; Aurora (omni, 180, 0.6) 0.80; Conquest (omni, 90, 1.4) 0.94. **Phase hulls** carry no arc or efficiency in vanilla, only a cloak that is all-round and total but cannot be held while the hull fires, so a phase hull counts for half of a full screen (0.80); the half is the one authored number left, standing for that exclusivity. Declared threats and hostile ships are not scaled. This is a perception weight; it multiplies no damage.

So one armed craft (0.5) reaches the air-superiority entry level (1.0) for a frigate or any carrier, and does not for a capital. The score has no ground-clutter term, so a carrier's near-zero weight on ground is already true of every class. A class changes the threat score, and a carrier additionally biases its flux pair and standoff margin (Decided below); no damage parameter reads it.

The rest of the original class intent (support ships, escort) is **not built** and is listed here as it was drafted, to be revisited with the open questions below. It changes weights and parameters:

- **Carrier** (flux and standoff biases built, see Decided below). Its main contribution is its wings. Existing air cover already flies a player's committed carriers' wings in on a corridor (`FlybyRoster`, `AirCoverSystem`; origin is a corridor, not the deck). A carrier on station therefore does not launch from its hull in v1: it is the *reason* a wing exists, stays back (standoff weight high), conserves flux, and treats hostile fighters as the dominant threat. A deck-launch origin (a third origin beside berth and corridor) is deferred; `air-nouns.md` is explicit that origin is a property of a sortie.
- **Support ship** (decided, deferred: see Decided below) (Mora, and the Shepherd as a drone tender, whose own model is `tender-ship.md`; tender-style hulls where `hints` or designation says so): no ground bombardment at all in its default; contributes point defence, spotting (`WarshipSpotting`) and later repair/jam effects. Its posture set is {AIR_SUPERIORITY-like, WITHDRAW}.
- **Escort** (frigate, destroyer): follows a lead ship's station at an offset, point-defence priority, interposes between the threat and the lead. Needs the lead-ship relation and ships as bodies; v2.
- **Phase**: a phase hull has its own cloak behaviour; out of scope until a phase ship is fitted.

## Selection, cadence, determinism

- **Where it lives.** A small `WarshipDoctrine` in `battle/warship/` beside `WarshipDirector`: `assess(PostureInputs) -> Posture` is a pure function of a snapshot; `WarshipSystem.tick` calls it at a fixed cadence before `direct` and applies the posture's `PostureProfile` (director radius and area count, opportunity range, flux soft pair, station policy). The posture is a readable field on `Warship` (`posture()`, `postureReason()`, seconds in posture) for the HUD line and for scenes. Missions can pin one (`ship.posture(...)`), which disables the doctrine for that ship.
- **Cadence.** Once per second (the director already looks for ground once a second; the commander cadence is 2.5 s), staggered by ship index. Reading the picture is O(air entities + ships); it never runs per tick.
- **Hysteresis.** A minimum dwell per posture (initially 8 s), separate enter and exit thresholds on the air score (initially 2:1), and an irreversible-for-N-seconds latch on WITHDRAW. The shape is borrowed from the squad contact doctrine (ADVANCE/HOLD/DISENGAGE, sticky with hysteresis) rather than invented.
- **Determinism.** Inputs are read in a stable order (ships by add order, aircraft by sorted id), tie-breaks are by id, and the doctrine owns no random source. Anything that picks among equal ground still uses the system's seeded random, as the director's mount deal does.
- **Reuse of existing doctrine machinery: the shape, not the machinery.** The mech doctrine is a frozen, player-chosen role (`MechRole`) executed by per-member GOAP goals; it has no cadence or hysteresis. The infantry doctrine is a sticky enum on a squad contact picture. A ship is one decision-maker with five states and no multi-step plans, so GOAP buys nothing and drags in the planner, goal ladder and replan interrupts. What is reused: an enum, an effective-value lookup that a mission can override, and a mailbox-free override field. If escorts or carrier cycles ever need multi-step plans, revisit GOAP then.
- **Relation to command.** Mission commands issue squad-scoped assignments and have no ship hook. A ship's stance and pinned posture come from whoever fits it (the mission launch seam, a script like `ColdAccountIntervention`, the debug panel). A commander that *requests ship support* from an assault (`SquadDirectiveControl`) is a command-power concern and stays out until general ship support exists.

## Gaps this exposes, and the build order

| # | Gap | Why it matters | Notes |
| --- | --- | --- | --- |
| G0 | No posture state, readout, or snapshot of threats | Nothing here is observable | Slice 1 |
| G1 | Director ignores objectives and friendlies | Owner's attack/defend and friendly-fire asks | Slice 2; reads existing roster, objectives, tactical map |
| G2 | ~~Ship weapons cannot target aircraft~~ | **Built (slice 3).** | `WarshipAirFire` and `WarshipAirRules`, damage through the shared body damage route's aimed entry; no second air registry |
| G3 | ~~Aircraft cannot hurt a ship~~ | **Built.** | `HullStrike` in both dispatchers; damage rides the existing detonation route, `Warship` still not an entity |
| G4 | Ships are not targets | Escorts, ship-vs-ship, counter-battery, air superiority between capitals | Make a hull a target class for ship mounts (circle, then the authored polygon the nouns already require) |
| G5 | Ground defences cannot hurt a ship, so there is no hazard to stand off from | "Stay out of range of ground defences" is fiction until it has a cause | Model it as the same kind of area drain `tickAirThreat` uses, from anti-air/anti-armour emplacements, so the posture's standoff has something to read; owner decision first |
| G6 | ~~Hull class, bays, hints, shield type unread~~ | **Built (weights).** Parameter biases and escort remain, see Classes and its open questions | `ShipClass` read in `Warship.fit`; `AirThreatWeights` |
| G7 | No query for hostile loaded shuttles with ETA | INTERCEPT | A few lines over `getAirEntityIds`; belongs with the threat picture |

Recommended order: **G0 and the threat picture (slice 1)**, then **G1 (slice 2)**, then **G7 + LZ-denial INTERCEPT**, then **WITHDRAW**, then **G6 class bias**, and only then the rules that change the simulation: **G2** (ship against air, built), **G3** (air against ship) and **G5** (ground hazard against ship), each behind its own owner decision. G4 (ships as targets) is the largest and gates escorts and ship-vs-ship. The doctrine layer is useful from slice 1 without any of G2 to G5; those turn on its air-superiority half rather than being prerequisites for it.

## Evidence plan

Each lands as a `BehaviorScene` next to the existing warship scenes (`onslaught-flux`, `warship-point-defence`, `onslaught-district`), with a control loop that has no doctrine, and verdicts that fail rather than a GIF that cannot.

- `warship-posture` (built): the same Onslaught over the same block of defenders with (a) no doctrine, (b) a declared threat raised and later cleared, (c) a hostile warship present then destroyed, (d) a declared capital hazard over the station, (e) the hull knocked under the limit. Verdicts: posture changes within a pass of the cause, returns after the quiet dwell, two transitions and no more, flux held below the control, flux of area fire clearly lower, every round inside the opportunity range, the rate recovered, standoff kept and the station regained, withdraw departs and stops firing, and a second play changes posture on the same ticks.
- `warship-support-fire` (built): an objective knot, a denser decoy and a densest knot with the company's own people in it. Verdicts: the control takes the densest and kills its own; the attacker takes the objective first, kills no friendly, leaves the dirty knot alone, holds fire and says why; danger-close fires into the dirty knot (the flag the only difference); a stance flip from ATTACK to DEFEND moves the fire from the objective to the defended ground.
- `warship-intercept` (built): a defender ship against a marine drop, no ship / density director / landing denial, plus the defenders' own boat landing within reach. Verdicts: the boat lands, the first zone round lands with it (fired before touchdown), far fewer marines survive five seconds after touchdown, the touchdown estimate holds within a second, no round near the defenders' own boat. Whether a detonation hurts a boat on its pad was observed, not asserted: the boat's hit points were untouched.
- `warship-air-strike` (built): a defenders' bomber wing against an Enforcer (omni shield, armour 900): no wing; shield held down with air fire off; shield on automatic; the doctrine hull; and beam fighters against an Onslaught. Verdicts: craft are sent at the hull with at most two on it at once; the bare hull loses hit points and the shield none; the shielded hull absorbs the landings as flux and loses less; the doctrine hull takes air superiority with armed craft as the reason and its flak downs the wing; beam fighters fly at the ground, are not sent at the hull, are not counted as a threat and leave the hull untouched.
- `warship-vs-air` (built): an Onslaught against a lone hostile transport, a fighter wing and a doctrine-driven air fight; and a defending ship against an inbound marine boat. Controls: the same ship under no air order. Verdicts: the boat is shot down in flight and its passengers lost where the control lands them; point-defence weapons stay silent against a transport; the large assault guns (measured by rounds per weapon id) stay silent against fighters while flak, anti-fighter and medium guns fire and kill some; air-superiority is entered, fires no assault round at aircraft, and over the ticks it held the posture fires fewer ground rounds, peaks lower in flux and leaves a hostile ground block untouched where the same ship pinned to bombard destroys it; intercept shoots the boat while the control with air fire off lands it.
- Open: a withdraw that returns is ruled out by decision 10; ships as targets remain unbuilt. Ship class weights are pinned by unit tests (`ShipClassTest`, `AirThreatWeightsTest`), not by a scene: the only question is arithmetic over a snapshot.
- Each is also a `sceneEvidence` row, so a regression fails the run; the snapshot suite plays the same run for the picture.

## Risks and rejected alternatives

- **Rejected: GOAP for ships.** See above. A planner for a five-state chooser is machinery without a problem.
- **Rejected: making the ship an ECS entity now.** It would let it be shot, but it drags the shared body surface, spatial admission and a hull polygon through the whole combat stack for a behaviour layer that works without it (the `air-nouns.md` argument for ordnance-only damage stands). Do G3/G4 narrowly first.
- **Rejected: a posture selected by mission script only.** That is what exists. The point is a default that responds to the fight, with scripts as an override.
- **Rejected: nerfing for balance.** Postures change rhythm and choice. Anything that reads as "the ship does less damage" is a flux or a targeting decision, never a multiplier, per owner direction.
- **Risk: a standoff with no cause.** G5 absent, the standoff term reads only hostile ships. Do not paper over it with an invented ground threat in slice 1.
- **Risk: air superiority that is vacuously always achieved.** Until G2 to G5, only declared threats and hostile ships keep it alive. The readout must say *why* (`postureReason`), so a battle where the ship ignores an enemy fighter wing is legible as a missing rule and not a misbehaving ship.
- **Risk: flapping.** Mitigated by dwell, asymmetric thresholds and a scene verdict on transition count.
- **Risk: INTERCEPT as ground-fire-on-an-LZ may read as "the ship shoots shuttles".** It only works where the shell and the shuttle meet; whether an aircraft on its pad is hurt by a detonation is not established (`warship-missiles.md` question 5). The ship now also shoots the boat in flight (slice 3), which makes this the second line, not the only one.
- **Risk: a ship that does not look up.** A single armed craft scores 0.5 and the air-superiority entry is 1.0, so a lone fighter overhead a ship under a doctrine and no air order is not engaged (only an explicit `AirFireOrder.ALL`, a declared threat, or a second craft changes that). Not changed in slice 3 so that no ship's behaviour changed under it; a candidate follow-up.
- **Risk: friendly-fire avoidance silently starving the ship** in a crowded close fight. The scene verdict counts rounds held for friendlies, and danger-close exists for missions that want the mess.

## Decisions

Owner direction of 2026-10-11 on the questions this doc was drafted around. These are standing direction; the rest of the doc is written to them.

1. **Ship weapons against aircraft are decided by the weapon, not the ship (built, slice 3).** As in the base game, large assault and strike weapons do not fire at fighters ("too fast"); anti-fighter, point-defence and medium weapons do; a transport shuttle is slow and large, so any non-PD weapon may fire on it. The rule is derived from the weapon table's `hints` and the size of the slot the weapon sits in, in `WarshipAirRules`: a mount is **flak-capable** when its weapon carries `ANTI_FTR` or `PD_ALSO`, or is a medium-or-smaller non-`STRIKE` ballistic, energy or missile mount; **point defence** when it carries `PD` without `PD_ALSO`; **assault-only** when it carries `STRIKE`, or is large and carries neither `ANTI_FTR` nor a `PD` hint. `STRIKE` outranks every other hint. Flak-capable mounts may engage any hostile aircraft; point-defence mounts only fighters; assault-only mounts only a transport. Calls made building it, not in the owner's text:
   - **A pure point-defence weapon does not fire at a transport.** The base game's PD weapons shoot fighters and missiles and never ships, and a shuttle is a ship; "any non-PD weapon may fire on it" is read the same way. The weapons that do carry both (`PD_ALSO`, the Devastator) are flak-capable and fire at everything.
   - **Slot size, not weapon size.** A mount is large because its slot is; hulls and loadouts the player will bring read the same way.
   - **The air layer has two target classes, fighter and transport.** A bomber is a fighter hull carrying ordnance; the sim does not distinguish further, so neither does the rule. Every non-fighter airframe (shuttle, fitted boat) is a transport.
   - **Ships do not shoot aircraft unless told or at an air posture** (`AirFireOrder`: `AUTO` by default, `OFF`, `ALL`). `AUTO` follows the posture: air superiority engages any hostile aircraft in reach (armed craft first, then loaded boats, then the rest), intercept engages loaded boats, every other posture and a ship with no doctrine engages none. `ALL` is an explicit order for a script or a mission.
2. **Ground defence turrets do not threaten a capital.** Only large planetary defences, which are not modelled, will. Until then the standoff behaviour has exactly one input: a **declared capital hazard** (`WarshipSystem.declareCapitalHazard`, a mission-declared zone owned by a faction). It does no damage; it is a declared input to a posture that keeps its distance, and nothing else feeds standoff.
3. **Attacker or defender is the mission's stance, and it can change mid-mission.** The target of this engine is ordinary RTS campaign missions: take the objective, then defend it from attack waves. A ship carries a `MissionStance` (ATTACK, DEFEND, HOLD), settable at any time by a mission, a script or a test; its doctrine reads it at every pass. It is never derived from faction side. HOLD (the default) is "no objective job": the ship stays BOMBARD.
4. **Friendly fire is avoided, and the weapons stay devastating.** The ship is not nerfed and the player and allies do not survive the equivalent of a MOAB on their own position. A weighted director rejects any area whose blast (the weapon's damage radius plus a safety margin) would reach a friendly body or declared protected ground, scoring areas by hostiles minus a heavy weight on friendlies in the blast. Where every candidate is dirty the ship holds fire and says so, unless the mission sets **danger-close**, in which case it fires but still prefers fewer friendlies. A manual order (debug F6, a script) is never refused.
5. **Ships fighting ships in a ground battle is out of scope for now.** A later extension (needs hulls as targets, gap G4).
6. **Intercept denies the landing zone first.** It fires timed area fire at the zone of an inbound hostile drop so the round lands at touchdown; it shoots shuttles in flight only where the weapon rules of decision 1 allow, later. Passengers are lost if a shuttle goes down.
7. **No favouring of named-campaign-soldier drops.** The ship ranks drops by what an observer sees (seated people, a machine payload, time to touchdown).
8. **Carriers launch from the hull on the map later.** Today they use the existing corridor.
9. **Posture is automatic from the snapshot; a mission can pin it; a player command is a later extension.**
10. **Withdraw is for the rest of the battle.** The ship leaves through the existing departure behaviour (`stayFor(0)`, `Warship.depart`). Campaign consequences of a ship leaving are deferred.
11. **Faction default-posture tuning comes later, not now.**

## What shipped, and the calls made building it

Slice 1 (posture and readout), slice 2 (objective-aware, friendly-aware fire, stance, intercept by landing denial) and slice 3 (ship weapons against aircraft) are built. What the code does, beyond the design above:

**Slice 3: ship against air.**

- **Mounts are shared, not duplicated.** `WarshipAirFire` gives every mount a turn at the air before its ground or ordnance logic. A mount takes an aircraft only if its weapon may engage that class, the ship's `AirFireOrder`/posture allows the target, it is not mid-ground-burst, it has ordnance and, for non-point-defence weapons, the flux pool lets a burst start (the posture's soft limit applies, so air superiority's 50/30 pair governs air fire too). Flux is paid when the barrel is on the target, so a mount that never gets onto it spends nothing. A point-defence mount with ordnance to shoot always shoots ordnance first.
- **Rounds are real shells; the roll is a hash.** A projectile flies as an ordinary `WarshipShell` to the point the craft is led to (its velocity times the flight time, solved twice), drawn by the existing renderer; a beam pulses at once with a tracer. Whether a round hits is decided at fire time by a pure hash of mount, shot number, craft and side against the chance: the point-defence rule (an aircraft is fast ordnance crossing at its cross-range speed, falling to half at full reach, penalised by the weapon's cone) times 0.6 for a fighter, which jinks. A miss passes a few body radii wide. A hit round lands its damage on arrival; a round shot down by a hostile ship's point defence never lands. Beams deal `damage per second` over the 0.35 s shortest engagement. Nothing draws from the shared random stream.
- **Aircraft take damage through one new door.** The damage route refused any airborne body (a bullet fired at the ground must never reach a machine in the air). `BodyDamageResolver.resolveAimed` and `BattleSimulation.applyAimedDamage` admit an aimed hit on a present craft and apply the same armour and structure law, ending in the same `AirSystem` shoot-down (cook-off, wreck, runway released, berth written off). No second health model. Rounds already committed to kill a craft count against it, so a battery moves on to another instead of emptying itself into one.
- **A downed boat loses its passengers by that shoot-down.** `AirSystem.shootDown` writes an `AirLoss` with the seats still aboard and sets the craft GONE, and nobody deboards: the scene measures six aboard, none near the landing zone. The loss's cause string reads "ground fire", the one label that route has; a warship's kill is not told apart in the ledger today.
- **Ships that are not told to look up do not.** `AirFireOrder.AUTO` with no doctrine is today's behaviour; the intercept scene pins `OFF` so it keeps measuring the landing denial alone.
- **Not built:** a fighter that survives a strike with a damaged hull is simply dead or alive (one hit usually kills: ship rounds carry hundreds of damage against tens of structure), warheads and carriers are straight shells and not missile flights, and area-weapon blasts do not splash a second craft.

- **Stance changes bypass the dwell.** A stance is an order, not sensing noise, so a flip ATTACK to DEFEND takes effect at the next pass. The dwell and 2:1 thresholds guard only the air fight, which is the sensed input.
- **Air score.** Declared strength, plus each hostile warship at 1.0 scaled from half to full by its hull, plus 0.5 per hostile armed strike craft in the air (capped at 2). Enter at 1.0, leave under 0.5 held for 8 s. Armed craft are the air layer's strike sorties (committed wings included, since they fly as sorties) whose ordnance can hurt this ship's armour.
- **Withdraw entry.** Hull under 35% (25% while the ship's own aircraft are in the air), or hull under 60% while threatened and overloaded or shield-down. The narrower overload rule is a judgement call: an overload lasts seconds, so it alone must not end a capital's battle.
- **AIR_SUPERIORITY conserves by choosing, not by weakening.** The flux soft pair drops to 50/30, the director holds one half-size area, never swings the heading, fires only inside 40% of the longest reach, and only from mounts whose burst costs under 8% of the pool (the secondary battery; lances are silent). A mount whose aim has drifted past that range because the hull moved abandons its burst.
- **Standoff** holds for the air-superiority posture only: the station moves to just outside any declared hazard hostile to the ship and returns to the mission's station when the posture ends.
- **Weighted director.** Score is `hostiles * (1 + 2 * proximity) - 8 * friendly weight`, proximity being closeness (0 to 1, fading over 40 cells) to the nearest objective point. Objective points come from friendly squads' assignments (the same `ObjectiveAssignment` commanders and the player write), then the tactical map's hostile-held objective and command-post nodes for an attacker, or friendly `mustHold` nodes for a defender, plus any ground the mission declares on the ship. Shells already in flight from friendly ships are not counted as hazard; this is not built.
- **Intercept by landing denial.** An inbound hostile loaded boat (INCOMING, PAD_DESCENT or LANDED with people aboard) is ranked by seated people (a machine payload counts as six) per second to touchdown. Its zone becomes a held area, aimed at open ground rather than at a unit, from the moment a round fired now would land at touchdown until everyone is out of the boat. A zone with friendlies in the blast and margin is skipped unless danger-close.

- **Intercept timing is per gun.** The zone is taken up to ten seconds before the slowest gun's first round could land at touchdown, so the hull can swing and the turrets traverse; each barrel then waits on the zone until a round fired now would land as the boat touches down. The touchdown estimate is the boat's remaining run plus half a second, or 0.9 s in its settle; both are measured, not derived, and the scene asserts the estimate holds within a second. A boat that is not visible until it is inbound cannot be pre-aimed on earlier than that, so a ship that must swing a long way is late. The scene starts the defender already lying on a bearing heading.

## Decided: ship class parameter biases

Owner decisions of 2026-10-12.

**Carriers hang back and save flux in a fight (built).** "Should carriers hang back and save flux in a fight? Yes, they are carriers." The bias is on the posture's parameters (`PostureProfile.of(posture, shipClass)`); the class is still not a posture and nothing changes how hard anything hits. A `CARRIER` hull gets:

| Posture | Flux soft / resume | Standoff |
| --- | --- | --- |
| AIR_SUPERIORITY | 40 / 25 (combat hull 50 / 30) | on, margin x3 |
| SUPPORT, INTERCEPT | 60 / 40 (combat hull 85 / 55) | on, margin x3 |
| BOMBARD (no fight to save for) | 85 / 55 | on, margin x3 |
| WITHDRAW | 85 / 55 | as the posture (off) |

Standoff is the existing machinery (`WarshipSystem.standoffStation`, declared capital hazards hostile to the ship): a carrier keeps 15 cells (3 x the standard 5) plus half its hull beyond a hazard's radius, in every posture but withdraw, where a combat hull keeps 5 and only in air superiority. Least invasive mechanism: no new station logic, one scale on a margin that already exists, and it inherits decision 2 (the hazard is the only input). It does not leave the battle and point defence is not a profile parameter, so it stays on. Hostile ships and hostile density do not yet push the station (the standoff has only ever read declared hazards); a carrier hanging back from a fight without a declared hazard is not built, because that needs ships as targets or a threat axis (G4).

**Support hulls do not hunt ground (decided, deferred).** A `SUPPORT` hull (CIVILIAN hint or tender designation) does not run the BOMBARD density director: it keeps point defence and returns fire on what threatens it, but does not seek ground targets. Not built: no mission puts a gunned support hull over a battle yet (the Shepherd tender carries no guns), so there is nothing to observe. Build it with the first such hull, as `maxAreas` 0 outside point defence for the SUPPORT role.

**Shield figures count in the threat weights (built).** An unshielded hull fears aircraft most; a wider, more efficient shield fears them less (see the factor above). Built once aircraft could hurt ships (G3). An exposed flank is not modelled beyond the arc's share.

**Escort is wanted (decided, deferred).** A role with a lead-ship relation that follows the lead at an offset and intercepts threats aimed at it. Not built: it needs ships as targets (G4); G3 now supplies something that aims at a lead. Until then a frigate or destroyer is a small combat ship.

## Open questions: ship class

1. **The multipliers and carrier numbers are a first table, not tuned.** They were chosen to keep the capital row unchanged and to make one armed craft enough to turn a frigate or carrier to the air fight. Armed craft remain only the air layer's strike sorties.
2. **Aircraft against ships: all three calls are made.** (a) *Decided (owner):* a hull always outranks the ground for an eligible sortie, with no mission or posture input, so a side with a hostile capital overhead sends its first two craft at it. That is right by threat: the most dangerous thing on the field to a ship's side is a ship. (b) *Decided (owner, 2026-10-13):* a sortie is aimed at the hull's position at dispatch and does not follow a moving hull; moving off station is fair counterplay. (c) *Decided (owner, 2026-10-13):* shield weights take their hints from the base game's own shield figures, not an authored table (see above).
