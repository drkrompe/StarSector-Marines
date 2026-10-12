# Warship behaviours — postures for normal fights and missions

Status: DRAFT

Written: 2026-10-11

Updated: 2026-10-11 — first design pass; nothing here is implemented. Slices 1 and 2 are `warship-posture-readout.md` and `warship-objective-fire.md`.

Read `air-nouns.md` (**Overhead ships**) first. Vocabulary (warship, mount, area order, director, point defence, flux) is that doc's. `warship-missiles.md` owns what a missile round does; `warship-on-station.md` owns the body, orders and acceptance scenes. This doc adds the layer that decides **what the ship is trying to do**, so that a capital in an ordinary fight or mission behaves like a ship with a job and not like a density-seeking turret.

## Why this doc exists

Today a directed warship fires at the densest hostile cluster in reach, forever, and nothing else about the battle changes what it does. Owner direction, verbatim:

> (1) is there an air fight to win - or has air superiority been achieved? If there is still a threat for the ship class in atmo on the map then it should likely continue with its default air to air behaviors and only take shots of opportunity when applicable. Flux provides a good reason for a ship not to waste its ordinance on ground targets and likewise to stay out of range of ground defenses when there is a threat also in the air as it needs to prepare for a potential brawl. There are support ships and carriers as well of course which change this behavior. (2) In the absence of an air target is the ship on the attacking or defense for the mission objective? If this is a defense mission its job may be to attempt to intercept enemy shuttles delivering ground forces or otherwise launching long range or close range assaults on positions.

Standing owner direction that this doc must not contradict: ships are overpowered by design; they are gated by role-play context and later by which fleet ships the player brings; no command power until general ship support exists; shields are a major flux draw (being built now). **A posture changes what the ship chooses to do and when, never how hard a gun hits.**

## What the simulation has today (honest inventory)

Read from `src/main` on 2026-10-11. This is the ground the proposal stands on, and it is thinner than the owner's framing assumes.

**Ship against aircraft: nothing.**

- A warship's area-fire mounts choose targets only from the unit roster's hostile ground bodies (`WarshipSystem.acquire`) or standing wall cells (structural bombardment). A warship's weapons cannot target an aircraft at all, and neither can its director.
- Point defence shoots hostile in-flight `Projectile`s that opt in and hostile `WarshipShell`s. It does not shoot aircraft.
- `WarshipSystem` and `Warship` do not appear anywhere under `air/`, `flyby/`, `combat/` (except the detonation observer), `turret/`, `weapon/` or `grenade/`.

**Aircraft against ships: nothing aimed, one incidental route.**

- No air craft, fighter or shuttle ever chooses a warship as a target. Fighters and strikes deliver `AirOrdnance` as ordinary detonations on the densest ground concentration.
- A hull is hurt only through `WarshipSystem.onDetonation`: any detonation by a hostile faction whose blast reaches the hull footprint, through the armour rule. So a bomb that happens to land near a hostile hull would chip it, and nothing ever aims a bomb there.
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
- Vanilla gives the shapes this needs: Frigate (Lasher, Tempest), Destroyer (Enforcer), Cruiser (Aurora, Heavy Cruiser Dominator), Battleship (Onslaught, Paragon, Pegasus), Battlecruiser (Conquest, Odyssey 2 bays), Light/Fast Carrier (Condor 2, Heron 3 bays, hint CARRIER), Carrier (Mora 3), Battlecarrier (Legion 4 bays, hints CARRIER and COMBAT), Phase Cruiser (Doom, shield PHASE).

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
| Hostile armed fighter / strike craft airborne | the craft carries ordnance the model can land near the hull, or (later) fire at it | craft yes (`getAirEntityIds`, `FighterProfile` armament); the *ability to hurt a ship* is not modelled |
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

- **Inbound drop denial.** Watch hostile loaded shuttles (`ShuttleMission` in INCOMING or PAD_DESCENT with payload) and their landing zones. The ship cannot shoot an aircraft (G2), so what it can do today is **deny the landing zone**: order an area on the LZ timed so the shell's fall time lands it on touchdown or deboarding. Rounds already have a telegraphed fall; the timing is `ETA - flight time`. That is a ground-effect intercept, and it is honest about being one.
- **Which shuttle, by value.** Rank candidate drops by `payload value / time to touchdown`: seated marines (`marinesRemaining`), a mech or vehicle payload, whether the drop is the objective's only approach, and how close the LZ is to a held compound. A drop with campaign-named soldiers is worth more than a generated one, but whether the *ship* should know that is a fairness question (questions for the owner). Ties go to the nearer.
- **Window.** From first INCOMING to the squad leaving the pad. After the squad is out it is ordinary ground, and the director's density pick takes over with an objective bias towards the defended node.
- **Assaults on positions.** Long-range assault (an attacker battery or hostile ship working a defended compound) is a threat to the *objective*; counter-fire against it needs ships as targets (G4). Until then the ship simply covers the defended ground (SUPPORT with the stance flipped: bias to *defended* nodes and the approaches to them).
- If a hostile shuttle is the only air in the battle and a flak-capable weapon exists (G2), INTERCEPT is where the ship shoots it down before touchdown; the passengers are lost, which is the point and the cost.

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

Class is read from the hull: `hullSize` and designation from the `.ship`/`ship_data.csv`, `hints` (CARRIER), `fighter bays`, and the shield type. A small `ShipClass` (GUN_CAPITAL, CARRIER, ESCORT, SUPPORT, PHASE) is derived, never authored per hull. It changes weights and parameters:

- **Carrier.** Its main contribution is its wings. Existing air cover already flies a player's committed carriers' wings in on a corridor (`FlybyRoster`, `AirCoverSystem`; origin is a corridor, not the deck). A carrier on station therefore does not launch from its hull in v1: it is the *reason* a wing exists, stays back (standoff weight high), conserves flux, and treats hostile fighters as the dominant threat. A deck-launch origin (a third origin beside berth and corridor) is deferred; `air-nouns.md` is explicit that origin is a property of a sortie.
- **Support ship** (Mora, tender-style hulls where `hints` or designation says so): no ground bombardment at all in its default; contributes point defence, spotting (`WarshipSpotting`) and later repair/jam effects. Its posture set is {AIR_SUPERIORITY-like, WITHDRAW}.
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
| G2 | Ship weapons cannot target aircraft | The owner's air-to-air "default behaviours" have no behaviour | Extend point defence (flak, vulcan, PD lasers, `ANTI_FTR` missiles) to air entities through the shared `AirTargetService` damage route; do not add a second air registry |
| G3 | Aircraft cannot hurt a ship | "A threat for the ship class" is empty | Strike craft choosing a hull as a target through a narrow ship-damage route; not by making ships entities yet |
| G4 | Ships are not targets | Escorts, ship-vs-ship, counter-battery, air superiority between capitals | Make a hull a target class for ship mounts (circle, then the authored polygon the nouns already require) |
| G5 | Ground defences cannot hurt a ship, so there is no hazard to stand off from | "Stay out of range of ground defences" is fiction until it has a cause | Model it as the same kind of area drain `tickAirThreat` uses, from anti-air/anti-armour emplacements, so the posture's standoff has something to read; owner decision first |
| G6 | Hull class, bays, hints, shield type unread | Class bias (carrier, escort) | Read the existing columns in `Warship.fit`; no new data |
| G7 | No query for hostile loaded shuttles with ETA | INTERCEPT | A few lines over `getAirEntityIds`; belongs with the threat picture |

Recommended order: **G0 and the threat picture (slice 1)**, then **G1 (slice 2)**, then **G7 + LZ-denial INTERCEPT**, then **WITHDRAW**, then **G6 class bias**, and only then the three that change the simulation's rules: **G2** (ship against air), **G3** (air against ship) and **G5** (ground hazard against ship), each behind its own owner decision. G4 (ships as targets) is the largest and gates escorts and ship-vs-ship. The doctrine layer is useful from slice 1 without any of G2 to G5; those turn on its air-superiority half rather than being prerequisites for it.

## Evidence plan

Each lands as a `BehaviorScene` next to the existing warship scenes (`onslaught-flux`, `warship-point-defence`, `onslaught-district`), with a control loop that has no doctrine, and verdicts that fail rather than a GIF that cannot.

- `warship-posture` (slice 1): the same Onslaught over the same block of defenders with (a) no doctrine, (b) a declared air threat raised and later cleared, (c) the same threat with a hostile warship present. Verdicts: posture transitions at the right moments, **no flapping** (transition count bound), flux ceiling lower under AIR_SUPERIORITY, ground rounds fewer and shorter-range, and BOMBARD recovered with its original rate after the threat clears.
- `warship-objective-fire` (slice 2): two hostile clusters, one by the objective; marines near the farther one. Verdicts: attacker picks the objective-side cluster, avoids the cluster with friendlies inside it, accepts it only when danger-close is authorised, and the control (BOMBARD) picks the densest regardless.
- `warship-intercept` (slice 3): a defender ship against a marine drop, three loops: no ship, ship with density director, ship with LZ denial. Verdicts: marines landed alive, first LZ round before touchdown, no round on a friendly shuttle.
- `warship-withdraw` (slice 4): a ship driven below the entry hull fraction and again by an overload with a threat; it leaves, returns on the exit condition, and does not flap.
- Each is also a `sceneEvidence` row, so a regression fails the run; the snapshot suite plays the same run for the picture.

## Risks and rejected alternatives

- **Rejected: GOAP for ships.** See above. A planner for a five-state chooser is machinery without a problem.
- **Rejected: making the ship an ECS entity now.** It would let it be shot, but it drags the shared body surface, spatial admission and a hull polygon through the whole combat stack for a behaviour layer that works without it (the `air-nouns.md` argument for ordnance-only damage stands). Do G3/G4 narrowly first.
- **Rejected: a posture selected by mission script only.** That is what exists. The point is a default that responds to the fight, with scripts as an override.
- **Rejected: nerfing for balance.** Postures change rhythm and choice. Anything that reads as "the ship does less damage" is a flux or a targeting decision, never a multiplier, per owner direction.
- **Risk: a standoff with no cause.** G5 absent, the standoff term reads only hostile ships. Do not paper over it with an invented ground threat in slice 1.
- **Risk: air superiority that is vacuously always achieved.** Until G2 to G5, only declared threats and hostile ships keep it alive. The readout must say *why* (`postureReason`), so a battle where the ship ignores an enemy fighter wing is legible as a missing rule and not a misbehaving ship.
- **Risk: flapping.** Mitigated by dwell, asymmetric thresholds and a scene verdict on transition count.
- **Risk: INTERCEPT as ground-fire-on-an-LZ may read as "the ship shoots shuttles".** It only works where the shell and the shuttle meet; whether an aircraft on its pad is hurt by a detonation is not established (`warship-missiles.md` question 5). Verify before promising the owner's intercept.
- **Risk: friendly-fire avoidance silently starving the ship** in a crowded close fight. The scene verdict counts rounds held for friendlies, and danger-close exists for missions that want the mess.

## Questions for the owner

Decisions the code cannot answer.

1. **Should a warship's weapons ever target aircraft?** Flak, vulcans and PD lasers on fighters; `ANTI_FTR` missiles; the main guns never? And the converse: should aircraft ever hurt a capital (bomb and strafe runs aimed at a hull)? Without both, the owner's air-superiority branch has nothing to react to beyond declared threats and hostile ships.
2. **Do ground defences threaten a warship?** If a defence post can drain a hull like it drains a shuttle, "stay out of their range" is real and capital ships need a counter story. If not, a standoff from ground is flavour only. Which ground weapons, if any, are anti-capital?
3. **Is attacker/defender the faction's side, or the mission's stance?** Relief and counterattack missions flip it. Proposal: side by default, mission override. Is that right, or should a stance be derived from objective ownership?
4. **How hard should friendly-fire avoidance be?** Penalty, hard refusal, or refusal with a danger-close override a mission can grant. Does a ship ever deliberately fire into its own marines to save an objective?
5. **Do ships fight ships in a ground battle?** An enemy capital over the target is the cleanest air-superiority story and needs hulls as targets (G4). Is that wanted, or are hostile capitals only ever ground-fire platforms?
6. **Intercept: deny the landing, or shoot the shuttle?** Passengers are lost when a shuttle goes down. Is that an acceptable result for a defender's ship against the player's drop, and are the player's own shuttles ever at risk from a hostile capital's flak?
7. **What may a defending ship know?** Should it prioritise a drop carrying named campaign soldiers, or only what an observer would see (payload size, hull)?
8. **Carriers:** wings arrive on a corridor today. Should a carrier on station eventually launch from its deck on the map, or is the corridor the permanent origin? Is the carrier a first-wave fleet ship, or does it wait for general ship support?
9. **Who sets posture?** Fully automatic with a readout (this doc's assumption), or a mission/campaign pin only, or a future player command? Direction so far is no command power until ship support exists.
10. **Withdrawal:** does a withdrawing ship leave for good, or reposition and return? Does a ship leaving cost the mission anything (story beat, objective, fee)?
11. **Faction doctrine:** should different factions' ships have different default weights (a Hegemony battleship versus a pirate hull), in the way target-faction command doctrine exists for ground commanders, or is class enough?
