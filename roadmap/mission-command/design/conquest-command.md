# Conquest command

Status: ACTIVE — paired attacker and defender command is implemented; live convergence and reinforcement acceptance remain.

Written: 2026-08-27

Updated: 2026-09-02 — the lane chain is **on by default**. What was costing it
a held compound was neither the front gate nor the shape of the lanes but a map
defect: every lane was silently a rung short. With the ladder whole, the same
code takes more and holds at least as much as the fraction on both canonical
fixtures.

Earlier 2026-09-02 — neither the chain's front gate nor the map-side lane fan
is what wins the held compound back; both were measured at full length and
reverted, and the staging dormancy on the south turns out to be about where
that fixture's lanes are.

Earlier 2026-09-02 — a lane can be read as a chain of places taken in order;
built, measured both ways, and left off by default because it costs a held
compound. Earlier 2026-09-02 — the tracks now have places on them.

Earlier 2026-09-01 — marine capture allocation is bounded to the home track and
its neighbours, own track first, measured on a tree carrying the prosecution
fall-through fix; the far-track walk it used to authorize is refused and
published as its own reason.
Earlier 2026-09-01 — the defender's reserve is a share of its mobile pool and
each threatened track's response scales with the threat, so a large garrison
commits in proportion to itself. Earlier 2026-08-30 — a track with no believed
front now stages forward, bounded by its neighbours' lead, instead of standing
still waiting for a sighting only advancing can produce. Earlier: a compact
player-facing three-lane projection in the battle HUD; lane-stage standoff read
from the squad's own corridor so a front believed off that line does not
withhold orders.

Read `mission-command-nouns.md` for the shared architecture and
`conquest-nouns.md` for territory, compounds, supply, keep, and victory law.

Conquest is a directional territorial command duel. Three lateral **tracks**
organize a readable front across the map's traversal axis. A track is a sticky
coordination preference, not an ownership fence: squads may support a neighbor
when their home track has no useful work, and the whole mobile force may
converge for the culminating keep or final contested compound. The tracks have
places on them: the map seeds a lane of garrison outposts and strongpoints along
each track between the beachhead and the fortress, so an advancing track finds
compounds to take on the way (see `precincts.md`).

## The chain is the front; the track is the fence

**On by default: `battle.conquest.laneChain=false` puts the fraction back.**
The switch is kept as the control a balance run needs, not as a hedge. The
numbers are at the end of the section.

**A lane's state is ownership along its chain.** A lane is its ordered places
from the beachhead to the keep — the recorded links of `MapResult.lanes`, each
holding the compounds the packer stamped on its claimed ground. The lane's
**front** is the first place the marines do not hold; that place is the lane's
objective, everything behind it is ground already won, and everything beyond it
is behind something still standing. Progress is a chain index.

That replaces a forward fraction of the map, which was the right abstraction for
a biome-band map and is close to meaningless on one grown from places: a track
could read 0.8 advanced with its strongpoint still the defenders', and could not
read a place retaken at all, because a fraction cannot go backwards. The chain
can, and a lane whose strongpoint is retaken reads as a front coming back one
rung, in the trace and in the report.

**A compound is paired with the place it stands on by the generator, not by the
commander.** Each recorded link carries the extent of its precinct's claim, and
a compound belongs to the link whose claimed ground holds its anchor. Matching
them by distance at battle time would be a second answer to a question the
generator had already settled, and the two would disagree the first time either
moved. A compound on no lane's ground — a settlement's supply hub, the beachhead
— is off every chain and keeps the depth-latched reading the distant capture
allocation has always used; there is no ladder to place it on and refusing it
outright would strand it. A rung the map found no room for is stepped over
rather than blocking the lane behind a capture that can never happen.

**Staging follows the road, measured along it.** An advance-track order stages
on the recorded route toward the next link, under the same laws the front push
already had — friendly lead, safe stride, standoff behind the nearest believed
hostile on that road, and no backtracking — but each of them counted in route
cells instead of forward coordinate. The destination is a cell *of* the route,
walked back until one is walkable and reachable, so a stage is on the road by
construction rather than snapped toward it. A squad in contact gets the same
derivation without the standoff, as it did before. A lane with no route, no
place left to take, or a squad already past the next one falls through to the
axis derivation, which is also the whole of what a lane-less map gets.

**The tracks stay, as the lateral fence.** Which lane a squad belongs to,
neighbour support, cohesion and the capture allocation's home-track bound are
all still track questions and are unchanged. What a track stopped being is the
measure of progress.

**One switch governs both sides**, or a run measures a half-changed battle:
with the chain off the attacker uses the forward fraction and the axis
derivation, and the defender's reinforcement layer buckets by front band. The
chain is map geometry and compound ownership — neutral referee facts either
side may read — so a shared switch is not shared belief.

**What it measures, and why it is on.** The canonical matrix, both ways from
one tree at 18,000 ticks:

| fixture | reading | captures | held | marine losses | defender losses | retargets |
|---|---|---:|---:|---:|---:|---:|
| reinforced-south | chain | 22 | 17 | 151 | 385 | 396 |
| reinforced-south | fraction | 20 | 17 | 189 | 346 | 277 |
| full-strength-west | chain | 12 | 6 | 300 | 430 | 315 |
| full-strength-west | fraction | 7 | 5 | 258 | 423 | 240 |

Held compounds is the outcome a Conquest is decided on, and the chain ties the
fraction on `reinforced-south` and gives one back on `full-strength-west` while
taking more on both — two more captures on the south for thirty-eight fewer
marines lost, five more on the west for forty-two more. That is what turned it
on.

**The bar did not move; the map did.** Read against the maps this project had a
day earlier, the same code cost `reinforced-south` a held compound of seventeen
and five captures of twenty-five, and three separate attempts to win that back
inside the commander all failed. None of them was the cause. Both canonical
fixtures were silently a rung short of the nine their three lanes owe — always
the middle lane's, because it is the shortest and is crowded first — for two
seeding reasons that had nothing to do with whether there was room, and a third
that only a bent path could show. `precincts.md` holds the rules; the point
here is the method:

| fixture | map | reading | captures | held |
|---|---|---|---:|---:|
| reinforced-south | 8 of 9 rungs | chain | 20 | 16 |
| reinforced-south | 8 of 9 rungs | fraction | 25 | 17 |
| reinforced-south | 9 of 9 rungs | chain | 22 | **17** |
| reinforced-south | 9 of 9 rungs | fraction | 20 | 17 |
| full-strength-west | 8 of 9 rungs | chain | 13 | 7 |
| full-strength-west | 8 of 9 rungs | fraction | 12 | 7 |
| full-strength-west | 9 of 9 rungs | chain | 12 | **6** |
| full-strength-west | 9 of 9 rungs | fraction | 7 | 5 |

**A reading that walks a ladder cannot be judged against a ladder with a rung
missing.** The whole ladder puts one more walled compound on each map —
`reinforced-south` goes from 23 to 24 and `full-strength-west` from 24 to 25 —
and both readings take fewer of them and lose fewer marines doing it. The
fraction falls hardest, from 25 captures to 20 on the south and from 12 to 7 on
the west, while the chain rises on the south and holds on the west. That is the
shape of a reading that walks the chain in order meeting a chain that is now
complete. The
absolute held figure on the west is one lower than the best number this project
ever recorded there, and it is the wrong comparison: it was measured on a
different map, and a commit-to-commit reading measures every other difference
between the two trees at the same time. The decision is chain against fraction
on one tree, and on that reading the chain is ahead or level everywhere.

**Three attempts on the capture gate, all measured, none of them the cause.**
Recorded so the next attempt does not re-derive them. The obvious reading of the
first measurement was that the gate refuses compounds standing behind an
un-taken outpost and that opening it would give the held compound back:

| opening | south captures / held | west captures / held |
|---|---|---|
| none — the chain as it stands | 20 / 16 | 13 / 7 |
| a lane opens a rung whenever the allocation can pair nobody | 17 / 16 | 8 / 5 |
| the same, never opening the objective, and only for a squad with no front work | 20 / 16 | 9 / 6 |
| the same, and only onto rungs the depth latch has also reached | 20 / 16 | 12 / 7 |

Held never moved off 16 on the south whatever was offered. Offering the whole
ladder *lowers* captures, because opening by chain position alone hands the
allocation the fortress — a thousand secure squad-pulses went into the keep out
of order on each fixture, and the west's front push fell from a third of its
orders to a twenty-fifth. Bounding that back to rungs the friendly line has come
level with recovers the west exactly to its unopened numbers and makes the
opening inert on the south, where every uncommitted squad has front work and the
"nobody left over" trigger never fires. **Held compounds is a question about
which places the chain never reaches, not about how many it is willing to offer
at once** — and the place it was never reaching was the rung the map had not
seated.

**The map-side answer was tried too, and it is not the one either.** If a
fixture's outer lanes begin two hundred cells from the only ground the force
stands on, the natural fix is to give every derived lane the same two ends — the
landing place and the keep — and spread them only in between. `lane-fan.md`
built exactly that, and the fanned map holds 14 on the south and 3 on the west
with the chain on; an isolation run against the same tree with lane membership
handed back to the plain lateral thirds still reads 12 and 4, so the fanned map
carried the loss and no lane reading over it recovered any of it. It was
reverted. What it was worth was the three seeding defects it turned up on the
way, which are what actually paid.

**Route staging is still where it was.** An advance-track order is derived only
for a squad the front push has no believed defender zone for, and on
`reinforced-south` there is nearly always one: single-figure squad-pulses of
nearly four thousand reach the staging derivation there, against hundreds on
`full-strength-west`. That is a property of where that fixture's lanes are —
two of its three run across the advance rather than along it — rather than of
the fixture. Do not tune staging against the south.

**"Nothing stays held" was overstated.** Only two distinct compounds on the
south and two on the west ever changed hands back; a lane's front returns to
rung 0 because those particular rung-0 places flip, not because the map does. It
is a genuine tug-of-war over a couple of outposts rather than a failure to hold
ground.

## Marine command

The attacker balances front pressure with deliberate compound capture. Unknown
occupancy permits a measured probe, not a declaration of clearance. Fresh
distant capture allocations preserve squads already committed or adjacent, use
squads without useful front work first, and retain at least one executable
front squad while actionable resistance exists when force size permits.

**Capture allocation obeys the same track law the front push does: the home
track and its neighbours, own track first.** A track is a coordination
preference rather than an ownership fence, and support means a neighbour — the
far side of the map is not one on any reading of it. So a fresh detachment is
paired only with a compound at most one track from the squad's home, and takes
a *neighbour's* compound only while its own track has nothing worth doing:
neither a defender zone the front push would send it to, nor an uncaptured
compound of its own it could take. Its own track's compound it may always take.
Nearest-pair ordering is unchanged among whatever survives that bound, so the
allocation is narrowed rather than reordered. A squad already holding a capture
keeps it and a squad standing at a compound commits to it whatever track it is
on — those are about ground already reached, not about detaching somebody to
walk.

Without the bound the capture allocation was the one map-global thing in a
command built out of tracks, ranking every uncaptured compound against every
uncommitted squad on straight-line distance alone. The observed cost was a squad
walking the width of the map to a compound two tracks from where it was born,
its own track left to a fraction of its strength facing known contacts and the
receiving track already crowded, with the preserve pass then keeping that order
for the rest of the battle. The walk itself is a squad out of the fight, and the
track it leaves does not advance while it is gone.

The canonical matrix says the bound costs nothing either fixture is decided on:
the reinforced fixture captures 7 and holds 2 either way, and the full-strength
one captures 3 against 2 with none held either way, surviving to 12315 ticks
where the control falls at 10812. **What it now mostly buys is that the
pathology cannot return.** With the bound off this tree barely commits a
far-track pairing anyway — one across both fixtures — because the prosecution
fall-through fix removed the squad that produced them: a squad under
hold-and-prosecute with no firing cell inside its leash used to freeze for as
long as the contact stayed visible, and a frozen squad is exactly the
uncommitted, work-free squad the distant fill reaches for. The first measurement
of this switch was taken before that fix and read the opposite — 6 captures and
2 held against 12 and 11 — which is an artifact of the freeze rather than a
trade, and should not be re-derived.

A squad refused by the bound and left with nothing else publishes its own
assignment reason, because a far-track refusal and an empty map are otherwise
the same sentence.

The two convergence phases are map-global for a reason no measurement can move:
once the keep or one contested compound is the whole remaining objective, there
is no other front to hold.

When belief shows open-ground resistance but no discrete room is assignable,
an advance-track order stages behind the hostile frontier, bounded by friendly
lead, safe stride, reachability, and no-backtracking law. The frontier that
sets a squad's standoff is read from the corridor along that squad's own line
of advance rather than from the full width of its track: a track spans tens of
cells laterally, and a contact at its far edge is not in front of the squad. A
front believed in the track but not in that corridor drops the standoff and
lets the remaining bounds size the step — knowing the lane is contested
elsewhere is a reason to advance in step with the friendly line, never a reason
to stand still. Local contact then hands execution to squad doctrine.

A track with **no believed front at all** stages too, and this is the harder
half of the law. Refusing to was a deadlock rather than caution: a track nobody
advances through is a track nobody sights anything in, so the belief that would
authorize the advance can only be produced by the advance itself, and the
compound-capture path cannot break the cycle because distant detachments are
gated on a front that the same stall is what stops moving. Measured at ten
squads and ninety-eight marines standing still for an entire battle in the one
lateral band that happened to hold no defenders, never firing a shot, while the
commander correctly reported no actionable track target at every pulse.

The objection that refusal was making — an own-force push on no intelligence
must not become a lone flank walking off ahead of the force that would have to
support it — is answered by a bound instead. An unscouted track may lead its
*neighbouring* tracks' friendly lead by the same margin a believed one may lead
its own, so the line advances abreast and a track that has run out ahead holds
until the rest come up. The relaxation is confined to staging: a squad already
in contact is not stuck, and an exterior contact the commander has not yet
placed stays ambient rather than becoming a fabricated forward order. A blind
advance is published under its own reason, and does not count as the actionable
front work that holds squads back from distant captures — staging blind is what
a squad does precisely when there is none.

When only the canonical keep remains, all available assault squads converge
across track boundaries. If the keep is held and one earlier compound is the
sole contested objective, the capture quota stays deliberate while other
squads receive bounded cross-track clear support. Ordinary track allocation
returns when the front reopens.

## Defender command

The defender mobilizes only from defender influence. A legal contact produces
a coarse threatened track and band, never an enemy identity or exact cell.
Starting patrols form the mobile pool; born garrisons retain their posts.
Threatened tracks receive one responder before concentration, home and adjacent
tracks are preferred, and part of the free pool remains in reserve when the pool
permits it. Expired reports release only defender-command-owned responses.

**The reserve is a share of the mobile pool, not a count, and the response a
track receives scales with what is believed to be in it.** A fixed one-squad
reserve and a fixed two responders per track read as caution at a starting
force of a few patrols and as abdication at sixty: three threatened tracks
drawing six squads while the rest hold their home tracks is a tenth of a
defence committed while the base is taken compound by compound. So a quarter of
the pool is held back above a floor of one squad, and each threatened track may
draw its own fraction of the remaining budget — its share of the believed
contacts summed across every active threat, floored at the old fixed cap so a
small pool behaves exactly as it did. Counted contacts weight that share rather
than the diffused pressure field, which is sampled at influence-block centres
and measures the block grid as much as the front. Concentration remains
strictly second: every threatened track still receives its first responder
before any track receives a second.

This is a bound on how much force answers a threat, never a relaxation of
belief honesty. The share is computed from the same coarse threatened-track
picture, so a larger response is a larger response to a report — not a finer
one, and never an enemy identity or an exact cell.

**Where a relief goes is the chain's answer too, and symmetric to the
attacker's.** A defender position standing on a lane is bucketed by that place
rather than by the front band it happens to fall in, and is worth reinforcing
only while its place is the lane's front — the one the marines are coming for —
or the place they most recently took, which is what to retake. Everything
further back on the lane is behind the fighting and everything further forward
is not being attacked yet. A ring around the objective says how deep a position
is and nothing about which lane is contested; the chain says exactly that.
Which place changed hands last is a claim about order rather than a snapshot, so
the reinforcement recompute remembers what it saw. A position on no lane keeps
the band reading, which is what a settlement's guard post has and should have.

Explicit hold, recapture, and relief tasks outrank soft track response. A
Conquest convoy enters through the strict defender rear edge, uses a frozen
behind-front deployment hint, and hands its passenger squad directly to
`conquest-defender` with the request's relief objective. Shuttle and walk-in
forces keep reinforcement ownership until another explicit mission policy
defines their handoff.

## Picture and evidence

The perspective front picture publishes phase, track extents, friendly body and
lead, believed-hostile frontier and pressure, preferred/effective track,
reserve, the response budget and the per-track responder cap it was split into,
assignment reason, and exact commander target or labelled zone marker.
It includes frozen own-squad position, leader zone, local contact, execution
suspension, active-path count, and the count of own members in the squad's
assigned target zone. Exact whole-zone occupancy, capture progress, and
ownership transitions remain neutral referee facts.

The player-facing **lane brief** is the compact Marine-perspective projection of
that picture. It appears only for Conquest and names the command phase, command
pool and reserve, then keeps Alpha, Bravo, and Charlie visible as three stable
rows. Each row reports the effective squad and live-member commitment, the
dominant assignment kind, and either known contact, an active compound objective,
or simple on-line status. It is an explanation of published orders, not another
sensor: no-contact means no commander report, and the brief never consults
neutral occupancy, capture progress, or the defender snapshot.

**The Conquest diagnostics are keyed to places as well as to tracks.** Every
published order carries the lane and rung it is about — its own compound's place
for a capture, its lane's front for a staging or attack order — and every track
state carries its ladder: how many places it has, how many the marines hold, and
which rung is the front. The analysis reports, per lane, places held of places
and how many times the front moved each way, and per place, the squad-pulses
spent taking it, staging toward it, or withheld from it for front work. A track
index alone cannot separate a squad standing off the outpost it is taking from
one walking past it to the strongpoint behind, and a report about a battle
measured in places held has to be able to name them. The chain-keyed rows are
empty for a mission with no lanes and with the chain reading off, where there
are no places to key to.

The review frame marks each lane's front alongside its numbered links, so the
picture and the report say the same thing about which rung a lane has got to.
It derives the chain from the same recorded lanes and compound records the
commander does rather than reading the commander's own, which is the ordinary
neutral-observer rule.

Conquest evidence measures assignment churn, response latency, reserve time,
track concentration, target closure, target-zone arrival, capture-zone
presence, captures/losses, casualties, and terminal or timeout result. A
secure-compound trip ends once on target entry, retarget, release, squad loss,
execution suspension, observation gap, timeout, or terminal result. Its local
contact, active-path, and quiet-travel observations remain overlapping context,
so a lethal contact-bound approach cannot be mislabeled as unexplained idle.
The current acceptance work is tracked by
`front-command-and-keep-convergence.md`,
`defender-track-mobilization.md`, and
`defender-convoy-deployment-and-handoff.md`.

Derived evidence further separates a retarget to another compound, a changed
capture marker, and a replacement assignment. For squads lost during a secure
trip it records the final living pulse's objective distance and fraction of the
approach completed, plus whether that pulse showed local contact, only track
belief, no published contact, or missing track context. These are pulse-level
observations rather than exact casualty coordinates. The current trace does
not publish normalized own-squad forward progress, so reports do not pretend
to place a loss behind or beyond the normalized hostile front.
