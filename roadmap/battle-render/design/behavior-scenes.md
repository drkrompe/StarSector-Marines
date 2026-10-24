# Behavior scenes

Status: ACTIVE

Written: 2026-10-20

Updated: 2026-10-24 — `FiringLineScene` measures held triggers since the lane gate; the rules for writing a scene's verdicts moved here from `CLAUDE.md`

The individual behavior scenes (`FrontageScene` through `LateArrivalScene`): why each exists, what it recorded and the traps its instrument fell into. Ordinal references ("the second", "the fourth") count in the order scenes appear here.

## Writing a scene

**Prefer a scene over a mission harness** whenever the question is about one behaviour rather than a whole battle's
balance, and add another scene rather than widening one past what its name claims. `sceneEvidence` plays the
catalog for its verdicts (`evidence-tasks.md`); `createSnapshots` draws it.

**A scene's verdicts assert mechanics; outcomes are readings.** Features keep landing, and every one of them moves
who wins a fight: a verdict such as "the wall came down", "the company took the bunker" or "at least N defenders
died" goes red on an unrelated merge or a seed change without anything it protects having broken. Assert what the
machinery does: the plan gave the breach to squads that carry the kit, a breacher with the wall in reach and kit
ready spent it within a few seconds, a planted charge damaged the wall it was set on, nothing is fired at your own
side. Where the mechanic needs a precondition the fight may not supply (a breacher alive at the wall), make the
verdict conditional on it and say so in the detail. Wall-down ticks, rounds, kills, losses and who held what go in
the report's metrics. A scripted, deterministic effect (a placed barrage on a fixed target) may still be asserted;
an unscripted fight may not. `assault-muster` takes `-Dbattle.scene.assaultMuster.seed=<long>` so a verdict's
seed-stability can be shown rather than assumed.

## The scenes

`FrontageScene` is a small, purpose-built battle — one production-stamped
compound, a configurable number of defender garrisons and marine assault
squads, an approach edge — played headless in seconds. It exists because a
whole-mission harness is both slower and worse at answering a question about
one behavior: the scene found a real aperture-derivation bug in minutes that
two full Conquest runs had hidden entirely.

`AirfieldSortieScene` is the second: a garrison airfield, a shuttle on its
hardstand, and the crew that has to walk out to board it, recorded three ways —
unopposed, with a marine fire team on the walk, and with a fire team on the
apron burning the based aircraft where they stand. It exists because the
unit tests around embarkation each pin one link (the means sets the state, the
system takes a marine aboard, the gate rejects without a field) and none of them
can show the thing the change was for: that the crossing is a stretch of time
during which somebody can be shot.

**A scene's marines need a squad and a weapon, or they are scenery.** A unit
spawned from a bare `EntitySpec` carries no loadout and cannot fire, and target
acquisition runs off the squad, so an "ambush" of unarmed, unsquadded marines is
six people standing in a field watching a crew walk past. This scene recorded
exactly that for a while, and its under-fire loop reported a delivery that was
never actually contested. Seed a loadout and mint a squad, then check the
recording says what you think it says.

**A headless aircraft is the size somebody primed it to be.** Hull geometry
comes from the install's own `.ship` specs through `SettingsAPI`, and outside
the game there is no `SettingsAPI`, so `HullFootprintResolver` quietly falls
back to one flat length for every hull alike — a Wasp the size of a Valkyrie,
and with it a body radius, a drawn hull and a blast catch belonging to an
aircraft that exists nowhere. `InstalledHullSpecs.install()` is the one way to
prime it; the JUnit extension and `CreateSnapshotsCli` call it, and a scene
calls it itself so a scratch harness gets it too. Anything that *measures*
aircraft size should assert `HullFootprintResolver.isMeasured` rather than
trust the number, because the fallback is silent by design.

`RunwaySortieScene` is the fourth: a station with a strip, one fighter flying
the whole cycle off it — out of the shed, round the hangars, down to the
threshold, along the strip, out to the target, gun runs, home, down, and back
in — recorded unopposed and again with a fire team astride the taxiway. It
exists because the unit tests each pin one link (the phase hands to the next,
the route avoids the wall, the landing captures on the centreline) and none of
them can show the thing a strip is for: that the crossing is a stretch of time
somebody can be standing beside.

Building it found the fault that mattered most in the whole feature. Air could
be engaged only by defence posts and only while airborne, so the minute of open
ground the runway buys was a minute of complete safety and the trade was a
fiction. Evidence is worth building for what it turns up on the way.

**A scene's opposition can be too good.** Six marines beside the taxiway killed
the fighter in under four seconds beside its own shed, which is not a crossing
under fire — it is a recording of a machine dying at home. Three take about half
its hull over the run. Equally, a platoon left in the open advances: the target
walked to the airfield and shot two parked fighters before the field's first
sortie was due, so it is dug in *and* most of the map away, because a gun run
breaches the pit it is attacking and the survivors walk out.

`KillingGroundScene` is the third: two mirror-image lanes to one objective, a
squad destroyed in one of them, and the next squad sent up to choose again. It
exists because the Conquest matrix cannot answer a question about one behaviour
— a seven-thousand-tick swing there was credited to a squad behaviour and turned
out to be another session's reinforcement work arriving on a merge.

**A control loop is part of the instrument, not a luxury.** The scene records
the same map with nothing having happened in either lane, because "the squad
went west" means nothing until you know which way it goes when west is
unremarkable.

**Remove whatever would answer the question for the wrong reason.** A squad
already routes away from enemies it can see, so an ambush left standing sends
the next squad down the other lane for a reason that has nothing to do with
memory — the first version of this scene recorded exactly that and would have
credited a casualty layer with an avoidance the game already had. The killers
are despawned once they have done their work, leaving two lanes identical in
everything a router can observe except that one is full of dead marines.

**A scene needs both sides on the map even when only one of them acts.** The
simulation returns immediately once a side is absent, so a scene with a lone
faction never advances a tick — and one whose only enemies die mid-recording
freezes at that instant with the clock stopped. Every airfield loop keeps a
single distant marine nobody can reach for exactly this reason.

That is not always enough. A side present only as *structures* can still be a
decided battle, and a decided battle stops ticking everything, aircraft
included — the airfield raid loop, whose defenders are three parked hulls,
recorded six marines standing perfectly still for its whole length until the
scene called `setMissionCompletionEnabled(false)`. A scene is about one
behaviour and not about who wins; turn the terminal check off.

The scene is reached through its snapshot suite —
`gradlew.bat createSnapshots -Psnapshot=frontage-scene` — which plays it and
records the animated evidence. It carried a JUnit harness and a JSON report as
well; both were deleted on 2026-08-28 because playing the scene twelve times
cost 93s of a 560s `:test` run, and the owner judged the invariants not worth
that. A scene is still the right instrument for a question about one behavior;
reach for it from a snapshot suite or a scratch harness rather than from the
default suite.

`YieldFreezeScene` is the fifth: one marine squad, one `CLEAR_ZONE` order, and
three rooms in a row. It exists because a mission goal may decline its own order
deliberately — `ClearAssignedZoneGoal` yields when the assigned zone turns out to
hold no live enemy, so the commander can reassign — and beneath a yielded
mission goal the ladder was empty. The squad got no goal at all: a null plan, and
members that drop their paths by design.

**Plan-less ticks are the reading, not distance.** A squad holding position
deliberately does not move either, so distance cannot tell a considered halt from
an absence of orders. What separates them is whether the squad holds a plan at
all, and a fix here should drive plan-less ticks to zero *without* necessarily
moving the squad one cell — a squad that wanders off looking for work has been
given the mission-inventing behaviour the noun doc forbids.

**A distant enemy is an attractor, not a bystander.** `BreachToEngage` falls back
to an omniscient nearest-enemy scan for squads that have not ticked targeting
yet, so the lone far-off defender every scene keeps alive to stop the simulation
terminating will be walked to if it can be reached. The first version of this
scene left a door in the far wall and recorded both loops crossing the whole map
to it — near-identical distances, and nothing whatever about the yield. Seal that
room: the goal's own reachability gate then rules the defender out.

What it records now that the floor exists: both loops sit at 1 plan-less tick of
1801 — tick zero, before the first replan — and the yielded loop still covers
0.0 cells. That pairing is the whole acceptance. Plan-less at zero says the
squad is no longer unable to act; distance still at zero says it did not answer
that by wandering off to find work it was never given.

**The same question of the other dispatcher gets a different answer.** The scene
also runs the pair as a mech lance, and a lance under the identical yielded order
never loses its goal: `MechAssignedObjectiveGoal` does not stand down on a clear
zone the way its infantry counterpart does, and beneath it the mech engagement
floor is both always relevant and always plannable, so that ladder cannot reach
an idle bucket at all. A floor goal added to `MECH_GOALS` today would be code
that cannot run. That safety is an accident of the action library rather than a
guarantee — give a doctrine action a precondition and the mech ladder silently
acquires the defect the infantry one was cured of — so it is pinned by
`MechLadderHasAFloorTest` rather than left to be rediscovered.

**A control that reproduces the defect measures nothing.** The control's defender
first stood on the doorway's own sight line, so the squad shot it down the
corridor without ever crossing, the zone went clear, and the control yielded and
froze exactly like the case it was meant to contrast with. Moved off that line it
crosses properly — and then falls into the same hole once it finishes, which is
the more useful recording of the two.

`SwarmOverkillScene` is the sixth: one squad, open ground, and twenty
`SWARM_RUNNER`s closing on it. It exists because the Conquest matrix cannot ask
its question at all — every defender there is a marine or an emplacement, and
this needs an enemy whose durability is smaller than one shooter's output. A
runner carries 20 structure against a rifle's 18, so a second rifle on the same
runner is very nearly a wasted shot while the rest of the rush closes
unanswered.

**Both sides carry a mission goal, and without it there is no fight to record.**
Eight marines meeting twenty runners is an unfavourable local balance and the
doctrine reading it is correct to disengage; the rush breaks too once it has
taken casualties. The first recording was both sides withdrawing in opposite
directions for twenty seconds — a difference between two targeting arms that
could not possibly show up, because neither side was firing. A mission goal
outranks survival by bucket, which is what pins them in the fight the scene is
named for.

**What it records is a negative, and that is the point.** Damage-aware crowding
changes nothing here — cleared at tick 450 with the squad intact, either way —
because per-body crowding was already about right for a target one rifle kills:
the committed share is 0.9, so the two agree. The case that was actually broken
is the opposite one, a chassis many rifles deep, and Conquest is where that
shows. The scene is kept as the control proving the swarm case does not regress,
and as the instrument for the next attempt at it.

`PlayerOrderScene` is the seventh: one squad, a commander's `ATTACK_MOVE` to the
far east edge, and one player click at (40, 6) — well off that axis, so a squad
merely continuing east cannot be mistaken for one obeying. It asks whether a
squad takes a direct order at once, holds it, and hands back to its mission when
done, and it is the acceptance for the order-path rebuild. Three loops: the
click, the same world with nobody clicking, and the click into a two-marine
picket's fire. It is the first scene on the `BehaviorScene` instrument, so it
plays under `sceneEvidence` for its verdicts and under
`createSnapshots -Psnapshot=player-order` for the same run's frames.

**Its first run found the order path taking an order and never giving it
back.** The first two promises held: the click was accepted on tick 61 — the
first tick that can see it — the squad was planning under it on that same tick,
and there was not one plan-less tick in 899. The last two did not. The squad
walked to the ground it was pointed at, arrived around tick 640, and stayed
there with the player's order still standing for the rest of the battle; played
out to three thousand ticks it was the identical centroid to two decimal
places, and the commander's mission was never resumed. The contested loop
parked the same way, so it was not about the fight; the control held its
mission goal for all 899 ticks, so it was not about the map.

**Two rules for one arrival was one too many.** The release tested the squad
centroid against `AttackMove.ARRIVAL_RADIUS` (2 cells); the action completes
when somebody is inside that radius *and* everybody is inside its 5-cell
`SQUAD_ARRIVAL_RADIUS` footprint, and an arrived member then holds its ground
on purpose so one marine cannot complete a step the squad shares. Six people
stopped by the action's rule settle with a centroid a little under three cells
out — satisfying the action, missing the release by most of a cell — and
nothing moves them again, because the order does not expire and outranks the
mission it masks while it stands. The release now asks
`AttackMove.squadHasArrived`, the action's own completion rule, and the scene
records the handback at tick 600 with the mission replanned on that same tick
and the squad 15.7 cells further east by the end; under fire it hands back at
590. Every verdict passes, and the bar was not moved to get there.

`FiringLineScene` is the eighth: six marines in column in a corridor, four
defenders at the end of it, and one question — does a marine with his own man in
front of him step out of the lane instead of shooting through him? It is the
acceptance for `LaneSidestep`, the third measured attempt at a fault the
decision side had never asked about at all: every line test bottoms out in the
navigation grid, which holds terrain and no units, while the ballistic model has
always given a friendly met before the target about a third of a chance of
catching the round. Two earlier attempts answered it by switching target and
both lost Conquest captures; this one steps aside instead.

**A corridor three cells wide answers the question by removing the option.**
That was the first version, and it looks right — a file down the middle with a
lane either side of it. It recorded not one sidestep in nine hundred ticks. A
firing lane was then a cell and a half of half-width, so leaving one took more
than a cell of lateral movement, and three cells offer exactly one. The reading — no
sidesteps, identical friendly fire in both loops — was indistinguishable from a
reflex that did not work. Five cells is the narrowest passage in which stepping
aside is a move that exists.

**The standing firing line holds no pursuit target, which is the other thing it
found.** A planted squad runs `OverwatchPosture`, which deliberately sets no
`COMBAT_TARGET_ID` and leaves the shooting to the dispatcher's opportunity pass.
A reflex gated on the pursuit target alone could therefore never fire in the
case it was written for. The registered threat is the persistent answer — the
firing system will not let a round go until it matches — and asking for it is
what made the scene say anything at all.

**A reflex that hands the tick back owes two things, and the scene pins both.**
The step-aside authors a path and declines, so the assigned step fires the
marine and walks him — which is only safe while *exactly one* caller moves him,
and a marine advanced twice simply arrives in half the ticks with every other
reading looking right. `one-mover-per-tick` measures the busiest sidestep tick
against one tick of his own travel. The other obligation is that a step which
plants a marine who can fire from where he stands throws the authored move
away on the tick it was made: measured, nine sidesteps with three covering any
ground. `sidesteps-complete` counts moves that *went somewhere*, because the
weaker form of it — "at least one moved" — passed on the broken version, as did
the friendly-fire and damage figures. Three plant sites now consult
`LaneSidestep.isStepping`.

**Since the lane gate it measures a held trigger, not friendly fire.** The gate
holds a trained shooter's trigger while a friend is in his lane, so the control
no longer shoots its own men and the old instrument read nought in both loops.
`control-holds-in-lane` and `sidestep-reduces-lane-holds` count the ticks the
gate held a marine for a friend in the lane: over the eight seeds the control
is held 1,127 ticks and the subject 558, both land the same 800 HP on the
enemy, nobody is hit by his own side, and all 39 sidesteps cover ground.

What it first recorded, before the gate: the control put 5.9 HP into its own
men and the subject none, both loops landed the identical 100.0 HP on the
enemy, and all six marines were alive at the end of both. Five sidesteps carried the whole difference, and
every one covered ground. Conquest is where it is decided, and two shapes of the
same step were measured there. Consuming the tick cost reinforced-south ten of
its fourteen captures; handing it back recovers almost all of that — 12 captures
and 11 held against the control's 14 and 9, with 56 more defenders killed. It
ships **on**, behind `battle.infantry.laneSidestep`, because held compounds are
the outcome a Conquest is decided on and captures is throughput: 12 taken and 11
kept beats 14 taken and 9 kept, and it holds more on both fixtures. The scene's
control loop sets the toggle false for itself.

`ProsecutionHoldScene` is the ninth: one squad ordered to take a room sixty
cells east, and one stationary enemy thirty-two cells off that axis which not
one marine can reach. It asks whether a squad prosecuting a contact it has no
firing position against gets on with the order it was given, and it is the
acceptance for `battle.squad.prosecutionFallThrough`.

**It was written from two squad dumps of a live battle rather than from a
theory.** Twelve marines under `CLEAR_ZONE`, executing `EnterZone`, stood on
their landing pad for a hundred and eighty consecutive frames — nobody moving,
nobody holding a path, every member settled — under HOLD / ADVANCING /
PROSECUTE against a defender thirty cells away with zero engageable members and
a force ratio of 0.18 in their favour. Prosecution leashes its firing-position
search to the squad centroid at twelve cells; the contact was further off than
that plus weapon reach; the search returned nothing; and nothing was read as
"no path", whose answer is to plant. `contactHoldIsFresh` is true while any
contact is observed, so the picture that caused the freeze was renewed by it.

What it records: the control makes **0.0 cells** of eastward progress in nine
hundred ticks with 898 of its 899 contact ticks reading PROSECUTE, which is the
live dump reproduced exactly; the subject makes 46.0 cells with the identical
picture and not one plan-less tick; and the third loop, with the contact moved
to twenty cells where a firing position does exist, still puts 25 HP into it and
covers 6.3 cells to the subject's 15.6 over the first three hundred ticks — so
the fix did not delete prosecution, it bounded it.

**Two ways the world was wrong before it was right, both silent.** A hole left
in a wall as ordinary floor is a gap and not a portal: the detector merged the
room into the field, the order named the zone the squad was already standing
in, the tactical axis pointed at the middle of the map, and the contact came out
FRONT rather than flank — so the doctrine was ADVANCE and there was no
prosecution to measure at all. `SceneBuilder.doorway` is the marked-cell
version. And the room's own occupant becomes the picture's primary as the squad
closes on it, which is correct and which halved the measured prosecution share;
every reading here is gated on the primary being the off-axis body the scene is
named for.

**A scene whose geometry is measured in weapon reach cannot use a rolled kit.**
The default roll hands out carbines and one pulse rifle, ten cells apart, so at
this distance five marines have no firing position and the sixth does — five men
standing still beside one walking off north, which is neither of the behaviours
being compared. `SceneBuilder.primary` issues one named weapon to every seat.

**A scene answers rather than merely records.** A `BehaviorScene` returns one
`SceneReport` per loop — its verdicts and the readings they were judged from —
and registers through `META-INF/services` so `sceneEvidence` and a snapshot
suite reach the same catalog. `sceneEvidence` plays it with no renderer and
writes the verdicts under `build/reports/scenes/`; a `BehaviorSceneSnapshotSuite`
subclass plays it once *with* a renderer, so the picture and the verdict come
from the same run and cannot disagree.

`LateArrivalScene` is the tenth: a campaign squad whose form-up has already
timed out, four marines landing at its abandoned landing zone thirty-one cells
behind it, and one question — does a late arrival cross to its squad without
picking a fight of its own? It is the acceptance for `SquadRejoin`.

**The uncontested control very nearly ties, and that is the finding rather than
a disappointment.** Four marines dropped that far back are inside cohesion again
at tick 836 without the state and 799 with it. The cohesion pull has been there
all along and does most of this work on an empty map; if that were the whole
measurement the state would not be worth shipping.

**What it ships for is the contested pair.** Two armed defenders eight cells off
the crossing, and the control *stops*: 420 of its 1,528 crossing ticks —
fourteen seconds — standing still trading fire with a picket it was never sent
to fight, reaching its squad at tick 906. The subject stands still on **none** of
its 849, fires 114 rounds while walking, and is back inside cohesion at 755.
Neither loop strays more than a couple of cells off the axis, so the fault was
never that a late arrival walks *toward* the enemy: it halts, where it stands,
for as long as the enemy lives. `kept-crossing` is what measures that, and no
lateral distance could have seen it — the first version of this scene measured
only how far off the axis they went, and both loops answered 1.9 cells.

**Two traps in the instrument, both of which reported a firefight as silence.**
Fire intent is authored and consumed inside the advance, so `fireTargetId` reads
zero from every observer and the first contested loop recorded 411 ticks under
the picket's guns with not one round fired; the shot events of the previous
advance are the seam that says what actually left a barrel. And the picket has
to be **placed at the lift rather than at the start**: the main body walks the
same axis on its way east and kills it before the second lift is in the air,
which is `AirfieldSortieScene`'s scenery mistake arriving from the other
direction.

`HighGroundScene` (`high-ground`) puts six marines on the overlook test map's
low ground and orders them onto the plateau past its west ramp, held by one
defender on the rim above the ramp and one back from the edge
(`elevation-nouns.md`). Its verdicts are mechanics: nobody steps between tiers
except through a ramp cell, nobody stands on a face, every marine on top had
stood on the ramp, and while every marine is below the defender back from the
rim is never in their fog under the skyline rule, unless it has just fired
down, and once a shot down exposes it they see it. Who wins and when the first
marine stands on top are readings, with path length and search time onto the
top and past the plateau against the same meadow with no plateau.

`FiringDownScene` (`firing-down`) stands one defender two cells back from the
overlook's west rim and four marines on the low ground in its reach, all
holding. Its verdicts: the holder is never in the marines' fog before its first
shot down, no marine round is fired at it while it is hidden, and on every
tick a shot down has exposed it (past the fog's three-tick recast) the marines
see it. `-Dbattle.elevation.firingReveal=false` is its control: there the
holder kills the squad unanswered, and with the reveal the squad answers it.

**The instrument nearly blamed the rule for a roster reshuffle.** The first
version read the defender's per-unit fog visibility, which is keyed by roster
slot: on the tick the rim defender died the slots moved, and the hidden
defender read its dead neighbour's "visible" for one tick. The roster now
tells the fog when a release moves a unit's slot (`UnitRosterService.onSlotMoved`),
so the visibility moves with the unit; the scene still reads whether the
defender's cell is revealed, which is the question it asks. Chasing it turned up a real
leak on the way: a marine suit's sensor sweep hands the fog a wall-read radius,
and the cast took any read radius as airborne, so a sweep at the cliff foot saw
the whole plateau. A ground sweep now reads past higher ground only within its
radius.
