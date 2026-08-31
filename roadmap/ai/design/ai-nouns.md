# AI nouns

Status: ACTIVE — AI owns squad planning, belief-derived contact pictures, local doctrine, faction-local influence, and unit execution; Mission Command owns strategic command duels and mission strategy adapters.

Written: 2026-08-23

Updated: 2026-08-31 — finished the goal ladder's floor: yielding an order is
now a state a squad can be in rather than an absence of one, so a squad that
declines its own assignment stands to instead of standing plan-less.

Earlier 2026-08-31 — gave the goal ladder a floor: relevance and workability
are separated, the ladder descends past a goal that cannot be planned, and
ambient engagement is now an occupant of the idle bucket rather than a promise.

Earlier 2026-08-31 — made an active player infantry context the exclusive
mission-tier plan, so specialist roles cannot railroad a squad; rescue pickup
infantry remain commandable while sealed shelter militia remain mission-owned.

Earlier 2026-08-31 — added the player-placed infantry defense area: a persistent
40-cell-diameter execution context whose local plan rotates bounded prepared
cover and firing positions against the squad's own changing contact picture.

Earlier 2026-08-31 — added the first contextual squad order: an uncaptured
Conquest compound reuses the ordinary secure-compound plan through territorial
completion, then hands execution back to mission command.

Earlier 2026-08-31 — extended the one-shot tactical movement override to a
selected Marine infantry squad, using its ordinary attack-move plan so fire-team
organization, moving fire, survival, and mission handback remain intact.

Earlier 2026-08-31 — added the exact-Mech tactical movement override: a
reachable one-shot destination below squad assignment, with moving fire,
survival suspension, and arrival handback.

Earlier 2026-08-30 — added the two onset moments and made battle Mech role
overrides and lance-wide coordination orders serialized local-plan interrupts
while preserving mission authority, beliefs, and per-member doctrine.

Earlier: 2026-08-29 — added the attack move and its cooperating group: an
order whose destination survives contact, and squads under one that split into
a squad fixing and a squad maneuvering rather than each forming its own line.
Also that day — an emplacement is never a shot of opportunity: a
move-only role still commits anti-hardened direct fire while the rest of the
special-equipment path stays behind its gate.
Earlier: belief now expires two ways: decay for a hostile
merely gone unobserved, immediate removal once its identity stops
resolving. Also that day — cohesion moved from the squad to the fire team: morale is
held, drained, and broken per team, a broken team peels to cover while its
siblings keep executing the squad's plan, and a squad-level morale reading is
now an aggregate that reads broken only when no composed team is left; added
the fire team as a decision layer and recorded the cohesion law. Also that day
— ambient work is now paced by the worker rather than by an authored clock: the
dwell begins on arrival, a full job is passed over for the next on the
rotation, and nothing free anywhere keeps somebody at the job they have.
Earlier: added the defense frontage and the standing-to garrison posture, which
give a held place's authored apertures to whichever side holds it and make
believed pressure, rather than local contact, the trigger for manning them;
recorded what an aperture may open onto and what an open-sided envelope yields;
added held layers, so a bounded few garrisons may share a perimeter and a
garrison that loses one envelope falls back to the next; made allocation picket
every threatened facing before massing on the hottest, with the reserve
yielding to that rule.

AI turns an assignment and what a squad has learned into coordinated movement,
posture, and fire intent. It is a decision system, not the authority for combat
resolution, map topology, campaign objectives, player control, or strategic
force allocation. Read `mission-command-nouns.md` for the shared commander
architecture and its per-mission designs.

## Decision layers

A **mission command** is the slow, faction-scoped strategic layer. It assigns
an `ObjectiveAssignment` to a squad: a bounded place or mission context to
serve. Command selects where a squad is useful; it does not author the squad's
local action sequence or make an individual unit fire.

A **squad plan** is the tactical answer to that assignment and the squad's
current knowledge. It chooses one goal and a short action sequence, then gives
members roles within each step. A squad without an assignment remains useful:
it falls through to ambient engagement rather than inventing a mission.

**Yielding an order is a state, not an absence of one.** A mission goal may
decline its own assignment deliberately — the zone it names turns out to be
already clear, or unreachable — which means *hand me back*, not *I have nothing
to do*. Those were once the same thing, because the ladder ended at the mission
tier: a squad that correctly declined its order held no goal, no plan, and
dropped the path it was walking, and could not recover on its own, since the
individual tier owns no movement. **A squad is never left plan-less.** What it
falls to is standing to: closing its scattered members up and holding, shots of
opportunity still available, until an ordinary replan trigger or a fresh
assignment moves it on. Standing to is a state rather than a task and therefore
never completes — a floor that reported completion would re-enter itself every
tick, which is the same churn plan stickiness exists to prevent.

**Ambient engagement** sits above standing to and closes on the squad's own
evidence: the freshest hostile it still believes in, or failing that the bearing
of something it recently heard. Both halves of the law bind. A squad holding
such a cue is never left standing — a composed squad with nothing to execute is
a worse failure than a wrong plan, because the individual tier owns no movement
of its own and cannot recover from it. A squad holding no cue at all advances on
nothing, because choosing a zone nobody assigned and going to take it is the
squad inventing the mission this clause exists to forbid. An anonymous bearing
is weaker evidence than a belief and expires far sooner than one: past that
window it leads to where a fight was rather than where one is. With no cue at
all it gives way to standing to rather than advancing on nothing, because
choosing a destination the squad has no evidence for is the mission-inventing
this clause forbids.

A **unit execution** is the per-tick realization of the assigned role. It can
move, hold, acquire a target, or author a legal fire intent, but it does not
silently replace the squad's plan. Combat systems remain responsible for
whether an authored shot lands and what it damages.

A **fire team** is the standing organizational element between the squad and
the individual: a small, stable billet group a marine belongs to for the whole
battle. It is the squad's maneuver element — coordinated actions assign whole
teams rather than scattering individuals — and it is the unit that holds
cohesion. A team's identity is its billet index, which never changes; the
grouping a maneuver uses may dissolve a team that has been shot below strength
and fold its survivors into a sibling, but that is a decision about who moves
together, not about who a marine is.

An executable-assignment change is a tactical-plan interrupt: the squad replans against
the new mission context immediately instead of finishing work authored for the
former assignment. Movement paths belong to the plan that authored them. A
planless unit drops that path, while a path may survive the instant a completed
step hands off to its sibling step so coordinated approach and engagement do
not erase one another's execution state.

Goal priority is categorical: MISSION authority outranks survival, survival
outranks engagement, and engagement outranks idle behavior. Relevance chooses
only among goals in the highest active category. A must-hold mission context
therefore cannot be displaced merely because an ordinary combat goal scores
more highly.

**Wanting a goal and being able to act on it are different questions, and only
the second one ends the search.** Relevance answers whether a goal is worth
wanting from what the squad knows; whether it can be worked toward from where
the squad stands is answered by planning it. A goal that wins its bucket and
then yields no reachable plan is therefore set aside and the next-best goal is
asked, down through the buckets to the idle floor — a declined goal does not
speak for the ones beneath it. Only when every goal has either scored zero or
declined is the squad genuinely idle. The categorical ordering is unaffected:
descent is reached by a goal proving unworkable, never by one scoring poorly.

Squad replanning remains serial unless a measured, explicit parallel contract
is introduced. Its state, goals, actions, and read-only view boundary may
support that extension, but parallel behavior is not implied by their shape.

An **ambient task assignment** is low-stakes authored world work for an existing
battle actor: resting at a berth, inspecting a console, maintaining machinery,
or using already-issued equipment at a practice station. The battle owns the
assignment, deterministic route intent, and temporary execution exclusion;
the actor retains its ordinary role and identity. Each route declares whether
any armed presence or only a hostile combatant interrupts it. An interrupted
actor leaves the route before ordinary unit dispatch and immediately resumes its
existing flee, guard, worker, or combat behavior. Ambient work never authors
damage, campaign recovery, inventory mutation, or mission authority.

**Standing down is a suspension, not a dismissal**, and the work is taken up
again once the disturbance has been gone a while. Leaving is the easy half and
was once the whole of it: a worker who yielded never came back, so a single
transient knock removed somebody from a map's life permanently. What interrupts
ambient work is almost always transient — an armed stranger walking past, a stray
round setting a fallback timer running — and on a home deck it is routinely the
crew's own equipment. Treating each of those as final turns a lively map into a
gradually accumulating set of statues; a seeded ship's-crew run left marines
standing in the butts of their own firing range for the rest of the voyage.
Resumption waits out a few seconds of quiet rather than firing the instant a
radius clears, because a threat that has stepped one cell away has not passed.

A **task point** is a battle-owned, single-occupant interaction site published by
a fixture or mission: a firing lane, berth, console, workbench, or defensive
post. Tasks claim a suitable point by group instead of assuming that an authored
coordinate is vacant. A claim remains with its actor until another point is
successfully acquired, the task leaves that kind of site, the assignment is
released, or the actor dies. This prevents two independent tasks from converging
on one station and gives ambient work, civilians, guards, and future mission
interactions one reservation law.

A **job site** is anywhere on a map that holds work: a ship's compartment, a
building interior, a walled yard. The ambient model asks exactly three things of
a place and this is all of them — an extent, which says which of the map's
authored jobs are here; a purpose, which says whose jobs those are; and an id,
which scopes the claim groups so somebody looking for a free bench is offered one
in the room they are standing in. Deliberately not a compartment: everything
downstream of a fixture is the same problem in a berthing space and in a market
square, and the only reason it began life tied to a ship is that a ship is where
it was needed first.

On a surface map a job site is **recovered rather than published**. Generation
already stamps a room purpose on every cell of every room it furnishes, and a
room is the contiguous run of cells carrying one — so the sites are read off the
map at battle setup and no generator has to remember to hand its rooms on. Read
off the purpose rather than off the building registry, which is the
nearer-looking answer and the wrong one: a building is a closed region of
interior found by flood fill, and a fortress vehicle shed comes out as one region
of which the bay is most but not all, the remainder being the thresholds its
doors were cut through. What a corridor is stays excluded — it is the space
between rooms, it runs the length of a building, and taken as a site it would
join everything at either end of it into one place with one purpose.

A **role** is what somebody is aboard to do, as the jobs they will work. It names
those jobs twice, because the same affordance is different work in different
rooms: stowage in a vehicle bay is the parts run and belongs to whoever works the
bay, while stowage in a berth is somebody's own locker and belongs to whoever
sleeps there. So a role has jobs it works **on watch**, at any site that is
somebody's workplace, and jobs it has **off watch**, only at its own quarters.
Collapsing the two into one list is not a simplification but a leak — with one,
the marines were duly offered a shift running the mech bay's stores.

**Idleness is chosen or it is a defect, and the two look identical.** An actor
standing still because their rotation had nowhere to send them and one sitting in
a lounge are the same picture; what separates them is whether the map gave the
second one somewhere to be. So the amenities are jobs like any other — a seat, a
mat, a washbasin — and doing nothing is a thing a map has to be able to *offer*
rather than a state a person falls into. A complement whose only options are a
bunk and a bench can be asleep or at work, and everything else it does is the
defect wearing the costume of rest.

**A room can be a workplace and an amenity at once, and one room is.** Everywhere
else the two are cleanly separate: a machinery space is worked and visited by
nobody, a lounge is visited and worked by nobody. The mess is both, for two
different populations, at the same moment — the ship's cooks are on watch in the
room the whole complement passes through to eat. Eating and cooking are therefore
different affordances rather than one, and only the second is duty: a compartment
that published only "somewhere to eat" gave a ship whose entire company ate three
meals a day that nobody made. Where a fitting draws that line on the deck is what
a serving counter is for.

That is also why **an amenity is on watch, not off**. The two lists divide by
*where*, not by *when*: an off-watch job can only be done in one's own quarters,
and an on-watch job is done in a room of its own. Reading "not work" as "off
watch" fenced every washroom, lounge and gymnasium behind the claim rule at once
— the rooms generated, published their work, and no member of the crew was
allowed to claim any of it. None of them are duty jobs, so none of them make a
room a posting; somewhere to wash is not a billet.

A **shift** is one role's work across one or more sites, and the loop each member
of it walks. It is the join between a role and a map, and it is derived rather
than authored: hand it the places somebody is posted and the work a generator
published, and it produces a route. A shift is emphatically **not a room**. A
marine's four jobs live in three compartments — they sleep and stow kit in their
own berthing, eat in the mess, and shoot on the range — so a per-room shift gave
each of those as a separate posting. What somebody does is a fact about them;
where they do it is a fact about the map.

A shift takes on only as many people as its posting holds, read **at the site it
is posted to** and never at the scarcest place it reaches — counting a ship-wide
firing range against a berthing posting would cap every berth on the hull at the
lane count and then send all of them at the same two lanes.

**A rotation is not a timetable.** A shift names the jobs and where they are; how
long the walk between two of them takes is what the actor's legs and the
pathfinder settle between them, and the dwell begins on arrival. Nothing budgets
the travel in advance, because nothing that could is in a position to know it: a
route sees the straight line between two stops and a deck is a spine with rooms
hung off it. The budget was tried and it fails in the expensive direction —
scheduled for an unhurried line across a whole ship while the pathfinder covers
the same ground several times faster, every worker arrives at their bench and
stands there waiting for the schedule to agree they have got there. Measured on
a manned deck that was four in five actor-samples of a transport's whole crew,
and nine in ten of a capital's: a ship where almost nobody was ever doing
anything, and where the fault was in the clock rather than in the work.

A dwell is measured against the **walk**, not against a watch. These were seconds
long while travel was a fiction the same clock invented, and stayed seconds long
once the walking became real — so a hand crossed a three-hundred-cell hull for
forty seconds, spent three at the bench, and set off again, and six in ten
actor-samples of a manned transport were somebody in a passage. A stint has to be
long enough to read as the thing the walk was for. They are still nothing like a
real watch: this is presentation, and what it owes is that a player looking at a
room for half a minute sees it worked rather than sees one person arrive.

Most jobs are somewhere you go. A **circuit** job is not: it is done at every
place the shift reaches that offers it, in one turn of the rotation. Two things
aboard work that way and both would collapse into their opposite if given a
single stop like everything else — **rounds**, which are made of the walk between
compartments rather than of any one of them, and a **defect list**, which is
wherever the defects happen to be. A round with one stop is somebody who walks to
the next room and stands in it; a repair round with one stop is a technician
tending the same fault forever.

A circuit is bounded rather than exhaustive, and it is never a **trade**. Rounds
and defects are deliberately everywhere — that is what makes them circuits — so
reading either as a station would make every compartment on the ship a posting
and put a watch in each of them; and a patrol of every compartment on a capital
is a rotation nobody completes, whose walker is permanently in a passage.

**A full job is passed over, not queued for.** Where the next job on the rotation
has no free place to do it, the actor takes the one after it; where nothing on
the rotation is free, they carry on with the job they are already doing for
another turn rather than walk off to stand outside a full room. A ship with three
firing points and six hundred marines is a fact about the ship, and the claim
service is what states it honestly. Six hundred marines motionless in the passage
outside the range is not that fact — it is a scheduling defect wearing it — and so
is one worker standing at a bench they have finished with.

That fallback is also what keeps a place occupied rather than merely held.
Because a claim is only given up when a replacement succeeds, somebody who finds
the whole map busy keeps the bench they are standing at instead of surrendering
it to wait in a corridor for it.

How many a posting holds is two different questions and the purpose decides
which. **A berthing holds its beds**: a bunkroom with nine racks and two lockers
quarters nine people who occasionally wait for a locker, and reading it as
quartering two empties a ship of three quarters of her complement to spare a
queue nobody would ever see. **A workplace holds what it can keep busy**, which
is its scarcest job: a bay with eight berths and one terminal cannot occupy eight
technicians on a rotation that includes the terminal, and pretending otherwise
puts seven of them in a line.

Where somebody is **based** is likewise not everywhere their loop reaches, nor
everywhere they work. A role is based where its **trade** is — the first of its
on-watch jobs, the one the rest of the list exists to serve — and a role with no
trade is based at its quarters. The mess it eats in and the lane it shoots on are
places it goes. Reading those as billets invents a population — a
galley with eight tables becomes quarters for eight marines who sleep nowhere and
are on no roster. So a caller deciding a **complement** asks where a role is
based first, while a caller deliberately **posting** somebody may station them
anywhere: a range detail is a real order, and a shift posted to a firing range is
a real shift.

Reading a role's *whole* duty list instead makes one room a station for every
trade that could lend a hand in it. Secondary jobs are shared by design — half
the trades aboard handle stores at some point — so a spares pocket publishing
stowage became a billet for the storekeeper, the technician, the machinist, the
medic, the armourer and the engine watch at once, and a hull that fills its
leftover corners with such pockets crewed a thousand engineers. A room may still
be several postings where several trades' own work is in it; a bay with a parts
run is genuinely the technician's and the storekeeper's.

Quarters are likewise a billet only for a role with **no** trade. A watch is
stationed at its work and berthed wherever there is a rack, so reading a bunkroom
as a posting counts a complement off furniture rather than off work, and counts
it once per trade. Sleeping and washing are reached either way, because posting
already sends every watch to the nearest place offering its off-watch jobs.

**A complement is bounded by racks, not by fixtures.** What a room can keep busy
is a fact about its fittings and says nothing about whether the map can carry the
people to do it: three stowage points in a spares pocket are three more
storekeepers, and a Valkyrie fitted that way crewed two hundred and seventy-six
engineers onto ten bunks. The bound is read off the fill rather than off a stated
figure, because the racks are the thing a player can walk up and count, and hands
are taken on one posting at a time round the whole ship — filling each posting to
its own capacity in turn crews the compartments at the head of the list and
leaves the rest deserted.

**A posting outlives the people filling it, and belongs to whoever holds the
place.** A manning pass that hires once and walks away is enough for a building
nobody ever reaches and wrong for one somebody does: kill the crew and the
facility is finished for the battle, whoever ends up standing in it. So a
billet somebody died in is *empty* rather than gone — the room still has the
work and the watch bill still says how it is walked — and after a long wait
whoever holds the ground sends somebody to fill it.

Both halves of that are the point. Killing a crew buys the minutes it takes a
replacement to cross the map, which is a real result and not a permanent one; a
facility one fire team could switch off forever would be a prize nobody who took
it could use. And the side sent for is the side *holding* the building, read off
the same capture state the resource pools use — so a motor pool taken from the
defender turns out marine technicians, which is what makes a facility worth
garrisoning rather than only worth clearing. A worked room with no compound over
it is nobody's and is never refilled at all.

Replacements arrive **on foot at their own side's rear edge** and walk to the
work, which is the whole cost of the arrangement: a body in the open for a
minute, and a building not working while it crosses. Nothing about that walk is
special — the replacement is put on the map holding the billet's own rotation,
and the ambient service paths them to its first stop exactly as it would
somebody crossing a room.

**A worker stops for a round, not for a visitor.** Whether authored work yields
is a question about the actor, and there are two honest answers. A civilian
yields to anybody armed and a guard yields to an enemy in reach, because neither
has any business being there when trouble arrives. An armed trade with a job
does not: a technician who downed tools because somebody hostile came within
sight would spend a battle standing about in the one part of the map an attack
passes through, and the facility they were posted to would stop producing the
moment anybody looked at it — a whole rear going idle for a scout. So a works
crew keeps at it through a firefight going on around them, breaks off when a
round actually finds one of them, and goes back to it once nothing has for a few
seconds. In between they are an ordinary unit and behave like one.

Being hit is read as **structure lost since the last time the actor was asked**,
which is a tick's worth. A near miss leaves nothing on the target to read, so
they work through the rounds that miss and stop at the one that does not — a
limitation rather than a decision.

**A sidearm needs a squad, or it is a prop.** Target acquisition runs off the
squad, so an armed worker in none is a person holding a pistol and watching:
they take a round, are released to their own behaviour, and stand there. A works
crew is therefore mustered as a squad — one per trade, since the stores and the
gantries are different rotations — and that squad is claimed on the map's own
authority rather than left unowned. An unowned squad is one mission command may
take, and it would be right to: a squad it can see and has not been told is
somebody else's is a squad it can use. A shed produces nothing once its
technicians have been ordered to go and hold a road.

Whether somebody is **armed at all** is a decision of the force that posted
them rather than a fact about their trade. A merchant's engineer carries a
spanner and a garrison's carries a pistol, and they are the same technician; the
role says what the person is and the posting says what they were issued.

**A room with nothing in it is nobody's posting, and that is the answer rather
than a gap.** A technician's trade is servicing whatever is parked in a bay and
an empty bay publishes no servicing, so a shed with empty berths is a shed the
manning pass declines to staff — correctly, and invisibly, since what a player
sees is a furnished room with nobody in it. Anything that wants a room worked has
to give the room something to be worked *on*; that is a fact about the place, not
a knob on the manning.

A shift is worked by whoever a caller hands it, which need not be anonymous. The
same posting takes generated hands or named people carrying their own kit, and
nothing about being named changes the loop they walk — a roster the player has
been reading all game turns up in the berthing and goes to the mess like anybody
else. When named people fill a posting they fill it: the roster is the
population, and a complement pass must not top the room up with hands who are on
no muster roll.

Because none of this knows what map family it is on, it is equally the mechanism
for scripted mission flavour: civilians going about a town's business, a garrison
keeping a routine before it is disturbed, or an individual actor lifted out of
ordinary behaviour to act out a role for a while and returned to it afterwards.
The assignment already suspends and restores ordinary dispatch; what a mission
supplies is the role and the sites.

A shift derives where it works from what each place publishes, so **posting is
the only thing a caller decides**. Hand it a role, the place somebody is
stationed, and the map's sites, and it finds the nearest site offering each job
the posting does not. A table of which purpose serves which job would be a
second copy of the generator's decisions and would go stale the first time a
room started affording something new.

**Publishing a site is a statement, not an increment.** Shifts share sites — two
berthing spaces send their watches to the same mess — so each publishes it, and
saying the same thing twice must leave the board as it was. A genuinely clashing
id still fails loudly, because that is a different mistake.

**Putting somebody into the world is not the same as posing them.** The pose
sampler draws the leg between two stops as the straight line from one to the
other. That line is a presentation convenience which bypasses collision by
design; on a generated map it crosses walls. Placement therefore always goes to a
job — a member's own first one, since a shift hands each of its people a rotation
already turned to start on a different job — and never to a point between two. A
seeded sweep of ship decks found one shift in seventy standing inside a bulkhead
before this was separated out. What varies within a job is how far through it
somebody is, which is the route's phase, and it is enough to keep a watch coming
on from finishing its first job in unison and setting off down the passage
together.

That sweep was the standing check on the whole model, and it was deliberately
not a check on a picture: for a spread of hulls and seeds it asserted that every
stop stands on floor somebody can occupy and that consecutive stops are
connected by a path **the game's own pathfinder** will find. A route that fails
either is silent by nature — the clock keeps advancing and the actor keeps
repathing, and what a player sees is somebody walking into a wall all watch.

The sweep was deleted on 2026-08-28: it cost 36s of a 560s `:test` run for a
single test, and the owner judged the invariant not worth that. The property it
protected is unchanged and still what a route owes; nothing enforces it
automatically now, so a change to fitting placement or route derivation is worth
checking by hand.

Bounded embedded scenes may seek the same route sampler at an exact presentation
time without advancing combat; that pose-only operation is not physical
simulation. Live hosts use a route only to choose the next claimed destination,
then ordinary pathfinding, movement, occupancy, and separation determine the
actor's position. Task activity and live fire begin only after physical arrival.
Claims are resolved in deterministic roster order, and the task pose is
reasserted after ordinary appearance authoring. This shared mechanism makes
shipboard leisure and workshop activity useful proving grounds for civilians,
technicians, guards, and other map-authored workers without creating
presentation-only actor scripts.

## Mission-command integration

`mission-command-nouns.md` is canonical for the command duel, frames,
disclosure, pools, directives, arbiter, snapshots, traces, evidence, player
intervention, and per-mission adapter contract. The following material records
the AI-facing side of that seam: command supplies stable assignment context;
AI retains local planning and execution authority.

The normal battle baseline is **autonomous resolution**: every side with
strategic agency must be capable of pursuing its mission without the player
continually rescuing idle squads. The resulting **command duel** has one
perspective-specific mission strategy per side. The shared infrastructure is
symmetrical; objectives and behavior need not be. An attacker may search,
seize, plant, or escort while a defender guards, delays, intercepts, or
countercommits under the same knowledge and authority laws.

A **command frame** is the frozen input to one command pulse. It contains the
side's own force state, faction-local influence, legally disclosed objective
state, public topology, current directives, and frozen doctrine. It does not
offer unrestricted access to the opposing live world. Production mission
strategies receive only this frame and narrow topology queries, never a
`BattleView` or retained simulation reference. The frame-only
`AutonomousMissionCommand` contract is separate from the legacy live-view
`MissionCommand` contract. A perspective-specific, stateless mission disclosure
adapter is registered beside the strategy and is the sole authority that may
project objective facts into the frame. Both sides plan from their frames
before either side's new orders are committed, so commander dispatch order
cannot become knowledge or behavior leakage.

A **command pool** is the set of squads a strategy may allocate. Born
garrisons, payload guards, scripted actors, and reinforcement forces awaiting
handoff remain explicitly owned outside that pool. Ownership may exist without
a tactical assignment: reinforcement delivery claims its squad when the squad
is minted, even while the squad has no place to act. A **directive** combines
that ownership with an optional `ObjectiveAssignment`, issuing authority,
reason, issue tick, target meaning, and stability or lease state. A shared arbiter validates and
commits proposed directives after planning; mission strategies do not compete
through untracked writes to the squad assignment field. Every other assignment
writer—garrison, payload, reinforcement, scripted, and intervention
systems—must likewise register its ownership with the arbiter or perform an
explicit incumbent-checked handoff. A weaker or equal external claim cannot
displace another issuer. No direct-write escape hatch may bypass provenance.

Directive **stability** and authority **leases** are distinct clocks. Stability
is an inclusive minimum hold on a mission assignment, long enough for a squad
to execute a useful plan before ordinary command scoring may retarget it; an
objective completing, its target becoming unreachable, its command context
expiring, squad loss, explicit handoff, or higher authority may end that hold
early. A lease instead bounds temporary external authority such as a future
player intervention. Repeating the same assignment renews neither clock.

Form-up is an execution suspension, not an assignment writer or ownership
transfer. The authoritative directive remains inspectable and may be updated
while a tagged campaign squad assembles; tactical goals consume a derived
executable assignment that is null until the manifest arrives or the timeout
expires. Selected-squad and dump diagnostics show both the authoritative order
and the READY/SUSPENDED execution state.

A **commander snapshot** is the immutable explanation published after commit.
It names the perspective, strategy, phase, command pool, reserves, objective or
group summaries, and each squad's directive and reason. Mission-specific
pictures extend this envelope with tracks, sectors, sites, corridors, or
branches. Selected-squad presentation, dumps, and headless traces consume the
published snapshot rather than reverse-engineering command intent. A squad
that remains unassigned or a proposal that validation rejects still receives
an explicit reason.

A **command trace** is an opt-in battle-long diagnostic record of those published
snapshots. Its perspective stream contains only one side's post-commit command
facts and is deduplicated at command-pulse cadence. Authoritative compound
transitions, casualties, duration, and outcome belong to a separately labelled
neutral referee stream. Referee evidence may evaluate the command duel but is
never fed back into either commander. Canonical field and event ordering makes
the same trace usable by a live dump, deterministic fixture comparison, and
later aggregate analysis.

Trace capture records when a published snapshot was observed as well as the
snapshot's own command tick. Pausing and resuming capture creates explicit
observation windows; analysis never integrates reserve, assignment, or pressure
state across an invisible gap. A bounded headless run ends with a labelled
timeout rather than an invented outcome. Only a forced-serial run is canonical
for byte-stability evidence while unit behaviors share seeded random streams.

The first Conquest evidence metrics are descriptive rather than balance gates.
They report accepted assignment retargets and reissues, command-unassigned and
explicitly unreachable squad intervals, reserve squad-time, published defender
contact-to-reserve-mobilization latency, published track concentration,
compound captures and losses, capture allocations deferred for actionable
front resistance, combatant casualties, duration, and terminal or timeout
outcome. They name only what the trace proves: command-unassigned is
not synonymous with physical inactivity. A bounded run with compounds but no
observed ownership gain is labelled territorial progress stalled; a long
capture gap remains evidence to inspect rather than an automatic tuning order.

Physical progress preserves the same knowledge split. A Conquest perspective
snapshot may publish frozen positions, current zones, local-contact state, and
execution suspension for its own squads, then compare those facts with the
commander's published target marker and target zone. Marker-distance closure is
command-pulse-inferred approach evidence because a representative zone marker
is not a route or exact destination. Target-zone observation is a stronger
sampled arrival fact, but it is not exact dwell between command pulses. Exact
marine and defender presence in a compound's capture zone is
authoritative objective evidence and therefore belongs only to the neutral
referee stream. Reports keep adjacent assault commitment, capture-zone entry,
mixed presence, and sustained marine-only presence distinct. Form-up, dead
squads, retargets, and trace gaps censor travel intervals rather than becoming
fabricated inactivity or entry latency.

The player is an **intervention authority**, not a replacement for a competent
baseline commander. Existing force selection and command powers are the first
intervention families. A later direct command may bias priority or lease a
legal rally, reserve commitment, focus, or fallback for a bounded duration. It
cannot manufacture hostile knowledge, bypass objective law, seize an
externally owned squad, or write an assignment with no provenance. Zero-input
outcomes, objective progress, idle combat power, response time, order churn,
casualties, and duration are therefore first-class balance evidence. Activation
pacing, concurrent leases, and their cost authority are bounded as well, so
reissuing a request cannot turn a temporary intervention into permanent manual
control.

## Knowledge and contact

A **belief** is a squad's own evidence about a hostile identity and location.
Direct line of sight records a full-confidence identity-backed contact. Heard
shots and detonations may create lower-confidence, inexact evidence; indirect
fire does not disclose a launcher identity merely by being heard. Beliefs
expire two ways: one about a hostile that merely went unobserved decays and
disappears over the belief lifetime, while one whose identity no longer
resolves to something actionable is dropped outright at the next tick. An
unknown hostile's live position is never promoted into a squad tactical
fact.

A **contact picture** is the immutable, once-per-tick local interpretation of
that squad's beliefs. It gives one answer for the tactical axis, threat sector,
fresh direct motion when evidence supports it, confidence-weighted hostile
presence, known friendly presence, local balance, and selected doctrine.
Presentation and diagnostics consume that published picture; they do not
reconstruct it from hidden world state.

Contact locality follows the squad's live member footprint rather than only
its centroid. A dispersed fireteam therefore retains a contact local to that
element even when sibling teams pull the centroid away. Immediate friendly
strength is measured around the primary contact, so a remote sibling element
does not make an isolated fireteam's local balance appear favorable. A
remembered identity is actionable planner contact only while it still resolves
to a live hostile combatant; a dead or released identity cannot satisfy target,
line-of-sight, range, or identified contact-reinforcement facts, and is
therefore not kept. Belief tracks identities rather than vacated ground: a
squad holds no position memory of a hostile that has died, so the consumers
that read belief without re-checking liveness — break-contact threat choice,
smoke and frag anchors, threat density — cannot steer it off a corpse. An
anonymous current audible bearing remains a valid investigation cue without
inventing a hostile identity.

Direct contact, alert and morale transitions, casualties, and hostile incoming
fire are tactical interrupts. Any one fire team crossing its break or clear
threshold is such an interrupt, because the squad's role assignment has to be
rebuilt around a team that just peeled or just rejoined. The periodic replan remains the convergence path.
Reacquiring contact after a no-contact tick is an interrupt; merely seeing an
additional hostile during the same contact episode is not. A remembered
contact can guide awareness and acquisition, but advancing squads may hold on
lost direct contact only for a short reaction window rather than for the full
belief lifetime.

Each faction's commander has a separate **influence snapshot**. It aggregates
only its own squads' beliefs and honest friendly combatant presence into
topology-aware tactical fields. The snapshot itself is read-only. A consumer
may act on it only when a mission-specific command contract authorizes that
action; diagnostics and heatmaps do not acquire assignment authority merely by
reading the field.

A mission command may publish a mission-specific command picture that combines
its authored assignments with honest influence-derived metrics. Conquest's
front snapshot is the first such picture: its metrics explain track progress
and pressure, while the command's explicit compound and neighboring-track laws
remain the authority for reassignment. The presence of a generic influence
field does not make Conquest tracks a universal commander abstraction.
Conquest defender command is the first opposing-faction consumer: defender
reports raise only a coarse threatened track/band and can mobilize a bounded
starting patrol reserve. The briefing grants a rally context, not the source
squad's hostile identity or exact reported position.

Conquest attacker capture allocation distinguishes lack of a defender belief
from positive clearance. It preserves legal capture orders already underway
and commits squads that have reached a compound threshold, but globally bounds
fresh distant departures while front resistance remains actionable. Squads
without executable front work depart first; at least one executable actionable
squad remains when possible. The published directive keeps the reason for its
actual order and separately exposes whether a distant capture was deferred for
front resistance, so selected-squad UI, dumps, and traces explain both facts.

## Assault command integration (summary)

`assault-command.md` is canonical for Assault sector search, defender area
security, reserve policy, command pictures, and evidence. These laws remain
relevant here because squad planning consumes `SWEEP_SECTOR` and `DEFEND_AREA`
assignments without acquiring strategic ownership.

Assault is a two-dimensional search and security problem, not a directional
front. Both perspectives share one stable rectangular sector partition as
public geometry, but their command pictures remain separate. Marines own search
coverage, sweep legs, active or suspected contact sectors, and bounded rechecks.
Defenders own authored strongpoints, coarse security areas, routine coverage,
and a bounded mobile reserve. Neither perspective reads the other side's live
hidden occupancy to make command decisions.

Setup-authored Assault garrisons retain `GARRISON` authority. Production setup
reserves at least one complete patrol squad when roster size permits, captures
only starting `PATROL` squads for `assault-defender`, and leaves reinforcement
ownership external. Routine mobile squads spread across reachable areas before
doubling. Held reserves receive real `DEFEND_AREA` readiness orders rather than
falling back into ambient patrol; a legal report can mobilize one responder per
threatened area before known hostile strength exceeding known local friendly
strength authorizes bounded counter-concentration. An engaged readiness squad
may exchange roles with a free routine squad so the published reserve remains
dispatchable without reducing the minimum routine-coverage floor.

A defender **area report** is aggregated only from the defender influence
snapshot. Fresh direct evidence marks the area active; older or indirect
evidence marks it suspected. Report disappearance is not treated as hidden
death knowledge: each identity remains latched through the expiry implied by
its last disclosed confidence. Response orders use a snapped walkable
strongpoint or area rally and never the contact's exact cell. When the report
expires, the responder returns to its readiness area; routine security and
externally owned garrisons are not stripped.
An indirect relocation cannot transfer a still-fresh direct report's authority,
freshness, or expiry into another area; only equally authoritative direct
evidence may relocate that active report before its direct window closes.

`DEFEND_AREA` is the Assault-specific cell assignment. It names defender-owned
area geometry, composes with local contact doctrine once a squad acquires its
own belief, and is distinct from Conquest `DEFEND_TRACK` and Sabotage
`DEFEND_SITE`. Strongpoint anchors may be walls or mounts, so command facts
publish both stable authored anchors and deterministic walkable rally cells.

The selected-squad panel, map overlay, squad dump, and perspective trace expose
the same Assault attacker or defender picture. The shared `commanderEvidence`
runner selects the paired zero-input fixture with `-Pmission=assault`; fixture
and maximum-tick overrides remain arguments rather than mission-specific Gradle
tasks. Canonical evidence uses forced-serial duplicate replays and requires
byte-identical traces from both perspectives.

## Sabotage command integration (summary)

`sabotage-command.md` is canonical for named sites, task groups, planter
logistics, site security, alarms, command pictures, and evidence. These laws
remain relevant here only at the assignment-to-local-plan boundary.

A **named site** is a mission-authored objective whose stable identity survives
construction, command frames, directives, snapshots, diagnostics, traces, and
fixture replay. Marine Sabotage constructs exactly three distinct reachable
charge sites. Site identity is not inferred from list position or a display
label, and the command layer never authors the site's location, progress,
completion, or victory effect.

A **site task group** is the command context around one unfinished named site.
It combines unit-owned planting capability with commander-owned security and
reinforcement context. A planter or kit retriever keeps its individual task and
its squad receives an assignmentless mission-command claim; that claim records
provenance without inventing a competing tactical destination. Charge planting
remains objective authority. Equipment recovery remains responsible for
choosing an individual retriever and promoting a successful carrier back to
planter.

Marine Sabotage spreads available security across reachable unfinished sites
before concentrating surplus squads and preserves useful site affinity across
ordinary command pulses. Route cost, own-force state, and faction-local believed
pressure may bias reinforcement. Missing hostile belief is not proof that a
site is clear. Completion, loss, unreachable context, or a special-task handoff
may break affinity, but redistribution releases only attacker-command-owned
squads.

Each site publishes why it currently has or lacks planting capability:
an active planter, assigned kit recovery, unsupported dropped-kit recovery,
awaiting a planter, or completion. An active unclaimed kit is therefore visible
as a mission logistics problem rather than disappearing between planter loss
and retriever selection. A squad cannot accumulate different-site planter or
retriever duties.

Exact charge progress and completion are legally disclosed Marine mission facts,
not hostile beliefs. The Marine perspective snapshot labels them as objective
state; an independent neutral referee stream records authoritative transitions
for outcome analysis. The latter is evaluation evidence and is never fed back
into command. Bounded forced-serial Sabotage evidence compares duplicate replay
bytes and reports site coverage, role and recovery transitions, directive churn,
progress, casualties, duration, and terminal or timeout outcome without treating
a timeout as a defender victory or a single seed as a balance target.

Defender Sabotage uses a separate disclosure and snapshot. Defenders legally
know the stable identity, authored geometry, zone, and completion state of their
own installations, but never receive Marine planter or retriever identity, kit
state, exact plant progress, or an inferred hostile cell through that channel.
The charge objective owns an identity-free installation alarm: a legal settled
plant raises it, interruption leaves it latched for a deterministic bounded
interval, and completion suppresses it. Defender beliefs remain a separate
faction-local input and may bias response without becoming alarm evidence.

Setup-authored garrisons retain `GARRISON` authority. Setup reserves enough of
the Sabotage roster to form mobile patrol squads for routine named-site coverage
and, when force size permits, a held reserve. Reinforcement, recapture, payload,
scripted, and intervention ownership remains external until an explicit
handoff. Routine security covers reachable unfinished sites before doubling;
alarm and believed-threat response consumes only otherwise available reserve,
offers one responder to each threatened site before adding a second, and never
strips another site's routine guard. `DEFEND_SITE` orders use distinct reachable
perimeter rallies, with the plant cell only as a final geometry fallback, so
multiple squads do not collapse into one indoor brick.

Completion and expired evidence release or retask only defender-site-command
assignments. Defender phase, reserve, site alarm, coverage, pressure, role,
reason, and rally are visible in selected-squad UI, map overlays, squad dumps,
and the perspective trace. The defender trace block omits all attacker-only task
facts; authoritative charge progress remains in the neutral referee stream.

## Doctrine and maneuver

The contact picture selects a sticky local doctrine: **ADVANCE**, **HOLD**, or
**DISENGAGE**. Hysteresis prevents a small score fluctuation from changing a
squad's posture every tick. Advancing squads may press, establish a contact
line, or withdraw from unfavorable local pressure; defending squads protect
their assigned ground unless they are overmatched and not under a must-hold
authority. An explicitly authored must-hold position can make its last infantry
survivor hold rather than take an ordinary structural fallback. That authority
belongs only to the squad's current defensive post or an executable hold order;
a must-hold node named as the destination of an approach or capture assignment
does not turn the advancing squad into its garrison before arrival. Morale
survival behavior remains an independent higher-priority safety boundary
elsewhere, and applies to the broken fire team rather than to the squad
holding the ground.

An advancing HOLD also publishes a contact initiative: **RECEIVE** or
**PROSECUTE**. A defending or overmatched squad, an approaching enemy, or a
useful firing line tells the squad to receive the contact from its current
ground. A non-approaching direct contact that only part of the squad can engage
tells it to prosecute: members with legal fire hold and shoot while the others
use the shared track to establish bounded firing positions. Prosecution never
authorizes an unbounded chase or abandonment of the mission route; if no legal
position exists inside the maneuver leash, the member continues its assigned
advance.

Prosecution is nonetheless the one commitment that leaves the destination out
of the reckoning entirely: its firing positions are leashed to the squad's own
centre rather than to the route, so the ordered cell is not routed to while it
lasts, and release is the contact picture's decision rather than the order's.
That is the right latitude for an assignment the squad chose the shape of, and
the wrong latitude for a destination handed down from outside it, so **a
directly ordered attack move declines contact prosecution.** It still fights
what it meets — the route-threat commitment, the firing line, and shots of
opportunity are unaffected, and an advancing HOLD that is receiving rather than
prosecuting still plants the squad where it stands. What it will not do is
decide on its own to walk off the ordered bearing after a contact somewhere
else.

While an advancing squad is committed to either kind of contact, a legal
shooter plants and fires from a stanced posture instead of continuing the
mission route. A successful shot may author the ordinary cooldown-staggered
move to strictly better directional cover; subsequent mission-action ticks
preserve that short cover move but continue to suppress the objective route.
This lets a receiving line improve accidental contact positions between bursts
without turning RECEIVE into a pursuit or moving the whole squad in lockstep.

**An emplacement is never a shot of opportunity.** A move-only coordinated
role — the moving half of a bound, a squad crossing to its assigned room —
withholds ordinary opportunity fire, because a passing shot must not divert an
element that was told to move. Anti-hardened direct fire is exempt from that
rule in both directions: a turret, drone hub, or heavy chassis in range is the
reason the advance is in trouble rather than a distraction from it, and the
carrier of the only weapon that meaningfully hurts it is precisely the marine
the suppression used to silence. A screen at one of the two **onset moments**
below is exempt on the same grounds. Everything else stays behind the gate:
satchel, frag, mines and close-contact tools all spend a squad resource or
freeze their carrier for something that is a diversion at that instant.

### The onset moments

Most of what a marine does about an enemy happens inside an engagement that has
already begun — there is a target, rounds have been exchanged, and the question
is how to fight it. Two situations sit before that, and until they were named
nothing could answer them.

A **close-quarters opening** is a hostile the marine can presently see, near
enough that the bearing it arrived on is known and whatever is going to be done
has to be done now. A **singling-out** is somebody having taken this marine as
their target, which is knowable before the first round lands and is the whole
of what a marine at the far end of a long open lane has to go on.

They are deliberately *situations rather than places*. The tempting formulation
is the doorway — hold at the threshold, screen the corner, go through together —
and it cannot be built on, because a place under assault stops having doorways
at roughly the moment it starts mattering: frontage is derived from an envelope,
and a breach merges inside with outside, so the aperture set does not degrade
but vanishes. A hostile ten cells away is the same fact in a room, in a breach,
in a treeline and in a trench.

Both belong to the equipment vocabulary's standing law — *name the moment, let
the carried capability answer it*. Neither says what to spend, so a screen
answers them today and anything authored later answers them for free, on either
side of the battle.

**A singling-out reads the enemy's own state, and that is legal only because of
what answers it.** The standing rule is that a marine acts on what a marine can
know, which is why the dead-ground policy tests the wearer's own sight rather
than the player's reveal bitmap. A bearing on somebody nobody has seen is the
kind of thing that rule exists to refuse. It is admissible here because the
responder is worn: a receiver that tells its wearer they have been painted is
ordinary hardware. Read from a plain infantry behaviour it would be
clairvoyance, and the distinction is not decorative.

### An onset belongs to the squad

An onset happens to one person and is not personal. The marine who comes round
a corner into somebody is the squad's first indication that the ground ahead is
not what it thought, and left personal it is answered personally — that marine
screens or goes to ground while the others walk past the same bearing one at a
time, which is the failure the cooperation layer exists to remove. So a squad
publishes at most one onset per tick: what a member is looking at, how far off
it is, and which of the two moments it is.

This is kept distinct from the squad's **contact picture**, which is an
aggregate over believed contacts and moves at its own pace. The picture's
unhurriedness is deliberate and useful everywhere else; an onset is the instant
one marine's own eyes changed the situation. A squad whose picture has not
caught up is grouped with its cooperating siblings on its onset instead, which
is the same honesty rule rather than an exception to it — a member looking at a
hostile right now is a stronger claim to have seen it than an aggregate still
being assembled.

**A contact drill — halting an advance because of an onset — is built, tested,
and off.** The reasoning is sound and the measurement disagreed with it: against
a control on the same tree, halting for a contact within knife range cost the
reinforced-south fixture a capture and a compound and killed fewer defenders,
and made full-strength-west fourteen percent longer. Squads that stop for
whatever is nearest arrive later, and in a mission decided partly on a clock,
later is fewer objectives. The gap is the trigger rather than the idea: range
alone says nothing about what the contact is, whether it can be safely walked
past, or whether somebody is already answering it. It stays behind
`battle.squad.contactDrill` for the next attempt at that trigger.

Two laws survived the attempt and are worth keeping whatever the trigger
becomes. **A forced commit must not outlive its cause**: setting the commit flag
every tick a contact stands there defeats the hysteresis underneath it, so an
enemy who breaks off still pins the squad — a latch with no exit rather than a
drill. Range is what releases it. And **the odds are a real answer, not an
oversight**: a squad that outnumbers one weak contact presses past it with
moving fire on purpose, and a layer that overrides that turns every straggler
into a halt.

### The attack move and its cooperating group

An **attack move** is an order to reach a place and destroy what is met on the
way. It is distinguished from a staging order by what it does with contact: a
staging order's job is to reach a line and stop, so it hands a squad in contact
to ordinary engagement and is finished; an attack move's destination has to
survive the fight, because the engagement goals carry no memory of it. A squad
that gives up its objective on the first remembered hostile fights, wins, and
then stands where it stopped.

So an attack move keeps its MISSION relevance through contact and owns the
fighting itself, on the same bounded-advance law an assigned room crossing
uses: commit when the route threat earns it, fight from firing positions
leashed to the route, resume when the threat releases. Morale remains the
escape — a squad with no composed team left releases the order like any other
mission goal.

Advancing under contact is one behaviour, not a property of the kind of place
being advanced on. A squad crossing to an assigned room and a squad attacking
toward a bare cell commit on the same threat score, bound by fire team the same
way, and hold the same open-ground echelon; only the arrival test differs. The
destination is therefore a parameter of the advance rather than a fact about
the action, and a new order that moves a squad toward contact inherits the
behaviour instead of restating a thinner version of it.

**A squad arrives as a body, not as its fastest member.** A plan step is
shared by the whole squad, and the first member to report the step done
completes it for everybody — after which every other member finds the plan
complete and skips its movement for that tick. So an arrival test that asks
only about the member being polled does not merely finish early: it stops the
squad, once per tick, for as long as one marine stands on the objective while
the rest are still walking. The squad stutters in place at roughly half speed
and the picture from outside is a movement bug rather than a completion bug.
An attack move is therefore finished when every live member assigned to the
step has closed to a squad-sized radius of the destination, deliberately
looser than one member's own arrival radius, because this is a footprint
check for a body of people and it must tolerate an ordinary trailing member
without pinning the order on a straggler. A member that arrives first holds
its ground and keeps firing; standing idle would move the same freeze down a
scale rather than remove it.

**A marine may improve its firing position within the footprint of the order
it was given, and no further.** An advance that has not committed to a route
contact can still be looking straight at a target it cannot reach — a contact
far enough off the axis of advance to have correctly earned no commit is
still a contact every rifle in the squad has a clear shot at and no rifle can
hit. Closing that last gap is bounded by anchoring the firing-position search
on the order's own destination cell. The anchor is the whole of the design:
anchored on the member instead, the leash re-anchors every tick as the member
moves and the bound becomes an unbounded creep toward the enemy — a slow
charge that abandons the order and breaks the front — wearing a
firing-position search as a disguise. A fixed anchor bounds the total
excursion to that leash from the cell the squad was actually sent to, however
long it spends trying, and a target too far off the destination for any cell
inside the leash to reach is simply left to the ordinary route. That
asymmetry is the feature. The improvement is confined to orders that carry no
target zone: the firing-position search scores walkability and leash distance
and knows nothing of zones or portals, and a room is routinely smaller across
than the leash, so on a crossing to a named room the better shot could sit in
the wrong room or past a portal the squad has not been told to cross.

**Squads under one attack move are a maneuver group, the way fire teams inside
a squad are.** Left to themselves, several squads converging on one position
each reach the same correct-in-isolation conclusion — form a line and shoot —
and the result is three frontal lines and nobody moving. The remedy is the
pattern a squad already runs internally, one level up: one squad holds the
enemy's attention while another moves. A squad is to its cooperating group what
a fire team is to its squad, and the vocabulary is deliberately shared so the
two read alike.

The group is formed by **shared believed contact**, never by proximity. Two
squads standing near each other looking at different enemies are not
cooperating and must not be told they are; two squads further apart that have
both identified the same hostile are. Belief is what keeps it honest, since a
squad that has never seen the enemy can contribute nothing to a group formed
around it.

Within a group, the squad with the best firing line **fixes** and the others
**maneuver** onto a bearing off the fixing squad's axis, on the side they
already stand — crossing that axis would walk a squad through friendly fire.
Two refusals matter as much as the assignment. When nobody in the group can yet
shoot the shared contact, no role is issued at all: sending a squad around the
flank of an enemy no one is holding is worse than both of them closing. And
when the ground will not support a flank, the maneuvering squad advances on its
own objective rather than standing still.

A role chooses the manner of an order, never its destination, and carries no
authority: command still owns where a squad is going and local doctrine still
owns whether it advances, holds, or breaks. Role selection is deterministic to
the tie-break, because the commander evidence harness replays each fixture
twice and compares byte for byte.

Fireteams are the infantry maneuver unit. They can receive distinct roles in a
shared squad step: a recoverable ambush can displace the exposed team while a
sibling covers, and a committed advance can bound rather than send every
member forward together. Normal movement may preserve a readable team
footprint, but doorways, constrained navigation, authored posts, and a live
contact-bound maneuver override decorative formation pressure. Acquisition
may retain a legal target through near-equal alternatives so reflex delay and
visual facing do not chatter.

A casualty rebind replaces, rather than extends, every retained plan step's
role map. A fire team below viable strength dissolves into the nearest sibling,
and no old slot may keep dead members or duplicate its survivors. Any active
bound is restarted from the new partition even when a sticky mission plan keeps
the same target zone and destination; geometric plan continuity is not team
continuity.

**A firing position is somewhere the marine can actually get to.** The
pickers score walkability, leash distance, weapon range and line of fire, and
none of them asks whether a path exists — a cell with a clear shot from the
far side of a sealed wall is an ordinary answer from them. Every leash in the
advance bounds *straight-line* distance from an anchor while the member has to
*walk*, and a building makes those two numbers diverge without limit: a
position three cells from its anchor and seven from the marine can be a
thirty-nine cell march around the obstacle between them, which is not a
bounded improvement but the objective abandoned for as long as the march
takes. So travel is bounded in its own right, and a position that cannot be
reached at all is refused rather than walked at. This holds wherever a leash
does — the committed firing line, a hold ring, a patrol leash, an area radius
— because it is a property of leashes rather than of any one order.

**Whether the detour bound applies is the caller's choice, and the distinction
is not decoration.** It is meaningful only for a cell picked as an
improvement: somewhere marginally better to shoot from than where the member
already stands. A member returning to its post, or walking to a noise it
heard, is going where it belongs, and the long way round is the right way when
it is the only way. Binding those two together would turn "there is a building
in between" into "do not go home".

**The two refusals are different facts and get different answers.** No path at
all means the commitment cannot be prosecuted by walking, so the member holds
and fights from where it stands — which is what a committed member does on its
firing line anyway. A path that merely costs more than it is worth leaves a
member who can still move perfectly well, so it carries on toward the
objective. Collapsing the two into one answer sends a squad that has decided
to fight marching straight past the enemy, and the canonical matrix charged
several extra squads for it.

What neither answer may be is the old one. A committed member that returned on
an unreachable position set an empty path and moved nobody, and because the
repath throttle is stamped only on a non-empty assignment the throttle never
engaged: the same search ran again next tick, and a search toward an
unreachable cell exhausts the whole reachable component before failing. The
freeze cost a full-component search per member per tick.

**That freeze was accidentally load-bearing, which is worth knowing before
removing one.** A frozen member stays out of the ground it was frozen short
of, so the canonical fixtures had been quietly banking a caution nobody
designed. Ending it is still right — a squad that stops for no reason is a
bug, and this one was an expensive bug — but the measured cost is squads lost
on approach where they used to stand still, and the gain is pressing that had
not been happening. Which of those a mission wants is doctrine, not a defect.

A perceived contact and a usable firing line are distinct. Perception may use
the cached projected-cell line of sight, while a ground direct-fire decision
must validate the member's true point against the intended target's true point.
Firing-line coverage, opportunistic acquisition, and firing-position selection
all consume that stronger result; a cell-visible but physically occluded squad
therefore maneuvers instead of publishing coverage and repeatedly shooting a
wall.

In a coordinated flank, the fixing element does not remain passively parked
once direct contact establishes the enemy line. It moves to reachable firing
or vantage positions and holds there while the maneuver element advances. An
unreachable maneuver waypoint completes the maneuver attempt and hands control
back to ordinary contact doctrine; it must not trap the squad in an endless
approach/replan loop.

Assigned defense stays bounded to its authored place. A compound garrison
re-clears and patrols eligible rooms inside the compound footprint; a live
turret post patrols its authored local box. A map-wide exterior flood is not a
room-clear objective, and removing the live post context returns its squad to
an ordinary local hold. Whether a wiped garrison is replaced or a defender
force is strategically recommitted belongs to mission command, not the local
guard plan.

When a tactical place supplies authored stand positions, initial allocation
and structural fallback assign those cells as member homes before deriving
nearby cover cells. The ordinary hold returns an idle member to that home, so a
bunker window is a durable defensive post rather than spawn-time decoration.
Invalid or unavailable authored cells fall back to the bounded nearby picker;
they do not make the entire garrison undeployable.

### Cohesion

**Morale is held by the fire team, and the fire team is what breaks.** Cohesion
drains toward the team of the marine a round found — a hit, a kill, or a near
miss charged to his own billet group — and recovers on that team's own account
once nothing has shot at it recently. A team that breaks peels to cover on its
own while its composed siblings keep executing the squad's plan; the squad
plans around the team that peeled and takes it back when its morale clears.
Nothing about a squad is a cohesion quantity: a squad-level morale reading is
an aggregate over its teams, and a squad counts as broken only when it has no
composed team left.

A team's recovery ceiling is its own alive-over-original strength, so casualties
bite where they were taken. This is deliberately harsher than a squad-wide
ceiling: losing one marine costs a four-man team a quarter of its ceiling, and
the survivor of a team is brittle even in a squad that is otherwise intact.
Cohesion is a property of the men who were actually under that fire.

Breaking is not the same as being ordered out. A broken team withdraws on
emergent reality, below the squad's plan and without consuming its authority —
the same override tier by which an individual's immediate survival preempts a
squad, and a squad preempts command. The squad-level survival goal remains only
for the tail case in which every team has broken, where its work is to release
the mission goal a finished squad is no longer holding.

Mechs carry no fire-team organization. Their cohesion stays per chassis with a
tougher model — damage-threshold drain rather than per-hit, stricter break and
clear points — and aggregates to their squad by majority.

### Frontage and standing to

A **defense frontage** is the set of **apertures** in a held place's outer
envelope — the openings through which its interior can be seen into, shot
through, or entered — each paired with the interior **stance** cell that covers
it. An aperture is either an **entrance**, which admits bodies, or a **window**,
which admits only sight and fire. Frontage is derived from live map state
rather than authored separately: the generators already stamp firing apertures
into perimeter walls and building shells, and the derivation reads them.

Inside is a held zone set, not a footprint rectangle. An opening is frontage
only when it separates held ground from unheld ground, so a window between two
interior rooms is not frontage and a perimeter wall is. An aperture must also
open onto real ground: a doorway is its own zone and therefore not held, which
would otherwise make the ordinary floor on either side of every internal
threshold read as an opening and let phantom apertures compete with the real
perimeter for a garrison's posts. The derivation is scoped by which footprint
it is asked about, so the same rule produces a compound's perimeter, one
building's shell inside that compound, and an isolated post's own envelope. A
layered defense is the consequence of applying one rule at two scopes rather
than a separate behavior.

A held place has **layers**: the compound's perimeter is one envelope and each
building's shell inside it is another. A bounded few of a compound's garrisons
may man its perimeter, taken in node priority order; the rest hold their own
buildings. The bound is the point — a compound whose every garrison stood on the
outer wall would have nothing holding the buildings, so the assault that got
through the wall, which is the assault that matters, would walk into an empty
base. Squads sharing one envelope partition it: each takes the most threatened
apertures no other squad is already manning, so reinforcing a wall widens the
frontage held instead of stacking two squads on the same few windows.

A squad holds the outermost layer it is eligible for and that is not breached.
When that layer is entered it **falls back** to the next envelope in rather than
standing down — the perimeter holder turns around and mans its own building's
shell against whatever is now in the courtyard. Only when its innermost envelope
is breached does standing to end and the room-clearing behaviors take over. This
is what makes the defense reactive rather than a single line that either holds or
is gone.

**Standing to** is the garrison posture between quiet patrol and the indoor
fight. It distributes members onto stances covering the apertures under the
greatest believed pressure, holding part of the squad back as an interior
reserve so a single threatened facing cannot strip the rest of the envelope.

Allocation **pickets before it masses**: every threatened facing receives a post
before any facing receives a second one, and the remainder go in plain threat
order. The interior reserve yields to that rule rather than outranking it — a
squad too small to picket every side it believes is threatened gives up reserve
bodies until it can. The reserve exists so one facing cannot strip the envelope;
it must never itself be the reason a wall stands empty.

Because posts are chosen at replan time and belief moves continuously, a side
that becomes threatened is uncovered until its garrison next replans. The
guarantee is that the gap closes within a replan period, not that it never
opens; a defense that re-aimed every tick would be reading the map rather than
its own reports. Ranking apertures by pressure alone is right for one approach and wrong
for two — the field ranks a whole wall above another, so a squad filling from
one global list puts everybody on whichever side reads hotter and leaves the
other approach with nobody facing it. A picket on each threatened approach and
the weight on the dangerous one is the reading a defender should make; it costs
the hot side one post per other threatened facing, which is the trade being
made deliberately.
Its trigger is the squad's own faction influence, which is aggregated from
believed contacts and propagated through navigable topology: a forward
element's contact mans the wall facing it, and a garrison that has personally
seen nothing still reacts. Because it is belief rather than truth, a feint can
pull a garrison and an unobserved approach arrives against a quiet patrol.
Both are intended texture, not gaps.

Frontage belongs to whoever holds the place, not to whoever is defending the
mission. An attacker holding a captured compound stands to on the same envelope
against the counter-attack.

A frontage exists only while the envelope in question does, and the envelope is
whatever is actually closed rather than whatever was drawn. Where map generation leaves a
wall open on purpose — a reserved road centreline crossing a compound is the
standing case — the ground inside joins the ground outside, and the frontage
found at compound scope is the member buildings' shells instead of the
perimeter. That degradation to the inner layer is the correct reading of an
open-sided compound, not a failure to find the wall. A breach is likewise not a
new entrance: opening a hole merges the interior with the ground outside, so
held and unheld stop being distinguishable and the frontage dissolves. An enemy inside a layer ends that layer, and the squad
falls back through the ones remaining to it. Both eventually hand the fight to
the room-clearing and choke-point behaviors, which is the correct answer —
posting people at intact windows while the building is being entered elsewhere
is the failure these boundaries exist to prevent. A live local contact still
outranks standing to.

The shared planner does not make all actors tactically identical. Infantry,
mech, and drone groups use distinct goal/action libraries for their different
movement and combat constraints. A mech role is doctrine supplied by the Mech
domain, not a replacement for chassis or loadout identity; a battle lance may
form while moving without merging unrelated squads. `company-view-nouns.md`
owns fireteam membership and company organization, while AI consumes that
organization for maneuver.

Mission command still chooses the assignment and its legal destination. Each
live mech applies its own effective role as the manner of serving that
assignment: Brawler closes, Frontline Support attaches and leads from the
threat-facing side, Long-Range Support preserves standoff and a credible
screen, and Balanced uses a general direct-fire band. A shared lance plan may
coordinate those members but may not choose one role on behalf of all of them.

For Long-Range Support, a credible screen is a broad allied front materially
between the firing cell and the currently engageable perceived enemy. Every
friendly body stays clear of the projectile ray so the firing cell is
rear-oblique rather than a friendly-fire lane; the named screen is only the
representative anchor. If any mission-legal screened cell exists it outranks
every unscreened cell; cover and travel cost rank cells inside each class.
`CLEAR_ZONE` and `SECURE_COMPOUND` include a bounded tactical perimeter only
while that perceived enemy remains inside the assigned zone. The perimeter
serves the same commanded fight and is not authority to pursue a different
contact, drift across the map, or relax exact-cell and withdrawal orders.

The lance owns one coordination order beneath that assignment. Form on Lead
keeps the moving members in the shared role-slotted formation, applies the
ordinary Brawler lead bound, and recalls a separated Brawler to its live lance
lead when combat posture would otherwise let it hold alone. Free Reign removes
those generic cohesion inputs from every member so their individual doctrines
may author divergent movement. It does not make a second mission plan: every
member still consumes the lance's assignment and squad belief, and
doctrine-specific support or screen geometry continues to apply.

A legal player role or lance-order request is a serialized command-phase
interrupt, not a new source of authority. Applying it clears only movement and
cached positions owned by the changed layer and asks the squad to replan
immediately while retaining assignment, contact picture, morale, and combat
state. The resulting execution therefore acts only on facts the squad could
already use and remains subordinate to survival, rescue, and mission laws.
Rejecting an enemy, non-mech, rescue payload, or vanished identity is part of
that authority boundary. A role request is exact-member state; a lance-order
request is shared by the selected member's current battle squad.

A legal player tactical move request uses that same serialized boundary but
does not invalidate or replace the shared squad plan. It resolves the clicked
ground to the nearest walkable cell in the selected Mech's connected navigation
component and temporarily intercepts only that member's locomotion. Target
refresh and installed-weapon fire continue during the walk. Reaching the cell
removes the override before ordinary unit execution continues on the same tick;
broken-morale survival suspends it, while a hard withdrawal invalidates it. The
request therefore supplies neither a squad assignment nor a hostile target and
never changes the knowledge available to the squad.

A selected Marine infantry squad may receive the same one-shot destination at
the squad decision layer. The accepted cell temporarily becomes its executable
attack-move context while the authoritative mission directive remains stored
and inspectable underneath. That reuses ordinary fire-team roles, contact
doctrine, route costing, special-equipment gates, and moving fire rather than
inventing a parallel manual movement loop. A broken team still peels on its own;
an entirely broken squad or form-up state suspends execution without erasing the
request. Arrival clears the tactical context before the ordinary mission replan
on that command tick, and a hard withdrawal cancels it. While the context is
active it is the squad's only MISSION-tier plan: unit-level planting, garrison,
last-stand, and other authored mission goals cannot win goal arbitration over
it, though cohesion survival may still suspend execution. Its destination also
outranks contact prosecution, which is the one contact commitment that would
otherwise discard it — see the doctrine section. A rescue pickup
perimeter is a mission duty rather than a control lock, so its Marine infantry
may be redirected and returns to the current rescue directive on release.
Enemy, Mech, drone, shelter-militia, non-soldier, wiped, and stale squad
selections are refused.

That same world request is contextual when its cell lies inside an uncaptured
Conquest compound. The compound's authored node remains the stable target while
its live capture-zone id may rebind after topology changes. Execution becomes
the existing `SECURE_COMPOUND` plan, so the squad approaches through ordinary
route costing and fire-team contact behavior, commits through the final room
threshold, clears the capture room, and holds it. Reaching the marker or capture
cell does not finish the order; only the compound state reaching `MARINE_HELD`
does. A captured compound is ordinary ground again, and an unreachable
contextual target is refused without clearing a still-effective player order.

A selected Marine infantry squad may instead receive a persistent **defend
area** context through a deliberate two-step command. Its clicked center snaps
to connected walkable ground and owns a circular twenty-cell radius. The
authoritative mission directive remains stored underneath, while the local
MISSION plan keeps every prepared position, firing-position search, and target
response inside the circle. It reads the squad's own contact picture: as a
believed threat bearing moves, the squad rotates a spaced firing line and
claims directional cover facing that report; direct targets are engaged from
bounded cover without losing moving fire. Broken fire teams still peel under
cohesion law, and a new player order or hard withdrawal supersedes the defense.

## Mission, space, and feature boundaries

Mission commands issue assignment context for the mission they serve. Their
shared contract and strategy catalog live in `mission-command-nouns.md`.
Zones,
portals, tactical nodes, and compound footprints are tactical places supplied
by map generation and battle setup; AI may reason over them but does not author
their geometry. `mapgen-nouns.md` owns that spatial substrate.

Command geometry follows mission meaning. The enduring strategies live in
`conquest-command.md`, `sabotage-command.md`, `assault-command.md`,
`raid-command.md`, and `extraction-command.md`. A useful geometry may be reused
as an implementation primitive, but one mission's ownership and convergence
laws do not silently become another mission's doctrine.

Not every opposing force needs a conventional squad commander. A swarm,
security network, or scripted hazard may use an inspectable mission director
with its own legal information and force ownership. Shared command
observability does not require fabricating beliefs, reserves, or human-style
intent for an actor whose mission fiction does not support them. RAID and
generic Extraction likewise require authoritative objective and outcome laws
before AI can invent meaningful command geometry for them.

`conquest-nouns.md` owns territorial capture, compound state, garrison
entitlement, and supply consequences. AI may assign a squad to approach, clear,
or hold a Conquest context, but it does not decide ownership transfer or
reinforcement delivery. `reinforcement-nouns.md` owns reinforcement requests
and delivery. Other mission domains own their objective and outcome laws;
their commanders adapt those laws into assignment context.

`mechs-nouns.md` owns chassis, loadout, role identity, and production
composition. `combat-durability-nouns.md`, `ballistics-nouns.md`, and
`moddable-weapons-nouns.md` own damage, physical shot resolution, and weapon
authority. AI chooses intended behavior within those contracts and cannot
override their physical outcomes. `battle-render-nouns.md` owns rendering;
debug presentation observes AI facts without becoming a sensor or a second
decision authority. A mission-specific debug picture selects one perspective
at a time and projects only its published geometry, beliefs, and authored
targets; viewing the other side is an explicit debug perspective change, not a
merged tactical truth.

## Strategic-extension boundaries

A commander influence snapshot is faction-local, belief-honest, immutable, and
read-only as data. A frontline, bulge, or breakthrough analysis may derive only
from that snapshot and must never expose hidden world state. Conquest defender
mobilization has explicit mission authority to translate its own snapshot into
a coarse rally assignment; this does not grant that authority to other
missions or diagnostic consumers.

Any consumer that reallocates squads, creates reserves, or briefs subordinates
requires its own mission-specific authority contract. Diagnostic analysis does
not itself authorize action. Mechanical suppression, richer cross-squad contact
sharing, recon doctrine, and dynamic mech reassignment are separate extensions
and must preserve the same knowledge and ownership laws.

Target-faction doctrine is likewise a bounded mission-command extension, not a
new planner or a source of hidden knowledge. `target-faction-command-doctrine.md`
may bias legal assignment, reserve, recapture, and local posture choices from a
frozen battle-facing profile; it may not change objectives, force composition,
combat resolution, or the belief facts available to a squad.
