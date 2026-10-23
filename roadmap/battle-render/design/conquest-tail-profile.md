# Conquest tail profile (`profileConquestTail`)

Status: ACTIVE

Written: 2026-10-20

Full switch and report catalogue for `gradlew.bat profileConquestTail`, moved verbatim from `CLAUDE.md` (Build & deploy). Read it when running or interpreting the tail profile.

- `gradlew.bat profileConquestTail` → opt-in 30 Hz-paced, continuous Conquest
tail pass through the production async-route and parallel-unit scheduler.
It runs the checked-in full-strength fixture for 1200 ticks by default,
excludes the first 300 from timing, and writes
`build/reports/performance/conquest-tail/summary.json`. Each retained worst
tick includes phase wall times, inner behavior/action/search counters, squad
replan triggers and slowest squads, unit-worker timings and slowest units,
plus async-route queue/activity deltas;
the report also retains the worst tick for each phase. It includes summed
tick wall time, per-phase totals, overlapping inner-operation totals, and
progressive convoy clearance/cost cell evaluations, node expansions, and
searches started per retained tick and summed across the run, plus
navigation-mesh refresh shape, setup-pending changes, and per-refresh
cover/seam/whole-snapshot assembly times so cumulative cost and rare topology
spikes can be read together. Add `-Dbattle.tail.jfr=true` for a JFR
execution-sample recording and one event per measured tick in the same output directory;
JFR adds overhead, so use the ordinary run for timing magnitude and the
recording for code-path attribution. Add
`-Dbattle.tail.workerTimeline=true` together with JFR to retain every unit
callback's worker identity, last entered GOAP action, nanoTime interval and
thread-CPU delta for dispatches lasting at least 10 ms. Includes early-return
callbacks. Separate dispatch markers identify stream-root completion and host
wakeup: the latest callback is not assumed to be the join's completion. Callback
events are emitted by the host after join; use their explicit timestamps and
worker identity, not the JFR emission timestamp/thread. This diagnostic adds
per-callback CPU reads, 1 ms stack sampling, and zero-threshold monitor,
class-loading and compilation events;
do not use it for ordinary performance comparisons. Add
`-Dbattle.tail.convoyUncachedStages=true` to measure isolated terrain-cost,
APC-clearance, connectivity-label construction, and a frozen progressive
route-input snapshot on the same grid after the timed replay; this does not
depend on a convoy winning dispatch and does not change measured ticks. Use
`-Dbattle.fixture.path=<absolute path>` to replay construction inputs from a
live `tick_profile_spike_*.json.data` or its `.fixture.json.data` sibling,
and `-Dbattle.tail.totalTicks=N`, `-Dbattle.tail.warmupTicks=N`,
`-Dbattle.tail.paceMillis=N`, or `-Dbattle.tail.outputDir=<absolute path>`
for targeted runs. Convoy admission reporting retains maximum snapshot
count/time, post-admission queued/prepared counts and oldest unconsumed
request age across all measured ticks. Each convoy means admits at most one
proof per tick and holds at most four prepared jobs, including finished
results. `CONVOY_PROOF_ADMITTED` includes admission-time refusal;
queued/prepared sums are request-ticks, not unique requests. Oldest-age sums
are not latency; use the reported maximum. Age survives topology restarts
and may originate before warmup. READY/FAILED count completed searches, not
delivered vehicles. `CONVOY_PROOF_TIMED_OUT` records refusal after 900 ticks
of preparation age (including queueing/restarts); timeout retirement shares
the one-per-tick admission allowance to avoid burst fallback dispatches.
The test fork prefers the game's bundled Java 17 when
present; `-Dbattle.tail.javaExecutable=<absolute path>` overrides it. The
live dump is a tick-zero construction fixture, not
an in-flight state snapshot, so replay need not reproduce the exact spike.
Playback does not normally activate player powers. Add
`-Dbattle.tail.mechSupportActivations=2` to request real mech-support sorties
from the fixture's frozen payload, using normal charge, readiness and landing
checks. `-Dbattle.tail.mechSupportFirstTick=N` defaults to warmup + 1;
`-Dbattle.tail.mechSupportIntervalTicks=N` defaults to 240. The report distinguishes
requests, committed charge debits, carrier delivery and observed live mechs by
faction, chassis and doctrine. Requested coverage fails after writing evidence
if activations did not commit or no delivered support mech was observed;
partial delivery is reported, not treated as complete delivery. A zero-input
run can still contain construction/defender mechs. `MECH_DOCTRINE_*` times
doctrine execution, nested inside the GOAP action; it excludes planning and
reflexes and does not prove coverage of roles absent from the payload.
Mech overwatch and medium-range positioning read navigation's
revision-cached raw-cell connectivity rather than flooding the map per
selection, preserving candidate order and separate body-clearance proof.
Casualty routing costs are published as block multipliers and read at cell
indices without expanding them across the map.
`-Dbattle.pathfinding.asyncDefendSite=false` keeps defended-site travel on
synchronous A* while leaving track rallies asynchronous, for same-build
coverage controls. Slow flat-search samples include member, squad, stable
action identity, and route reason; action totals span all measured ticks.
Flank selection skips candidates whose admissible score bound cannot improve
the incumbent, preserving the selected cell and tie order; actual proofs are
labelled `FLANK_SNAP` in slow-search samples. Tail reports retain immutable
post-tick goal, assignment, objective footprint, zone extent, and
member/target positions alongside slow searches. This context is not a
query-time snapshot. Each flank proof stops when its minimum frontier cost
exceeds every route allowed by the detour-step limit; accepted route ordering
is unchanged. Difficult flank selections may build one local,
expansion-capped minimum-step flood after four candidate proofs; it rejects
impossible detour budgets, leaving unknown candidates and winner selection to
ordinary A*. `FLANK_STEP_FIELD/EXPANDED/REJECT` separates this work from saved
searches. Each selection shares 8192 node expansions across candidate A* and
the step gate, without triggering cold whole-map connectivity labeling.
Exhaustion keeps a verified incumbent or uses the origin refusal; it may
change the tactical choice. This is not a shared squad/tick allowance or a
wall-time guarantee. `FLANK_SELECTION_EXPANDED` counts both kinds of work, and
`FLANK_SELECTION_LIMIT` counts selections using the entire allowance
(including success on the last node). `FLANK_SELECTION` counts all fresh
selections; `FLANK_SELECTION_LIMIT_REFUSAL` isolates those exhausting the
allowance and returning the origin rather than an incumbent. Parallel
attack-move callers may still duplicate selection. Allocation and scratch
reset are not charged as node expansions. ReinforceContact retains a positive
waypoint choice across routine replans for 300–360 ticks, with four-cell
anchor-displacement and intent/terrain/plan invalidation.
`FLANK_PLAN_REUSE/SELECT` records reuse and fresh choices. This does not
extend the separate worker-side AttackMove memo's lifetime.
`GROUND_*` and `VEHICLE_*` counters separate ground stages, local trajectory
heuristic/lattice work, and synchronous recovery searches from convoy
dispatch proofs. These nested elapsed times overlap; expansion, failed-plan,
recovery-attempt, and heuristic-storage-cell counters are count-only.
`-Dbattle.vehicle.rejectInvalidPlannerStart=false` restores local heuristic
setup for a padded starting footprint that already rejects every successor.
Invalid-start counters distinguish actual-chassis versus padding-only failure
and bounds versus terrain/closed-edge failure. Both arms classify the input;
the default skips the already-doomed heuristic and lattice work.
`-Dbattle.vehicle.reuseFailedRecovery=false` repeats identical failed bounded
recovery queries. The default reuses only completed failures against the same
frozen routing inputs and exact request; changed requests and control resets
require a fresh answer. Recovery failure/reuse counters separate actual work
from reuse. Neither control changes physical clearance or terminal policy.
Tail reports
also include squad-field fallback searches, with their miss cause, expanded
nodes, and unsigned destination occupancy (including path reservations).
Shared squad preparation publishes exact pending intents when it defers one
and admits older waits first; pending objective travel does not mutate or
advance an old path. Failed, uncovered, mismatched, and intentionally
unprepared queries retain their ordinary fallbacks. Tail reports separate
pending requests/calls, admissions, successful resumptions, and wait age.
Covered, same-goal fields tolerate soft cost changes until the first replan
at age 300–360 ticks; a route-local multiplier increase of 0.25 against
original build costs asks for earlier refresh. `SQUAD_ROUTE_COST_CHECK/REUSE`
separates validation from avoided builds. Seed counts are separate from flat
member A* counts. A one-member request retains its exact seed directly, with
only its cells covered. `SQUAD_PATH_FIELD_DIRECT` measures conversion/storage;
corridor/settled cell counts then describe stored seed coverage, not reverse
expansions. `SQUAD_ROUTE_UNCOVERED_FALLBACK/EXPANDED` measures off-field
fallback work for both singleton and shared fields, so narrower coverage
cannot hide searches displaced into member execution. Construction resumes
seed, region/corridor, reverse, sorting and publication work across ticks
using four reusable scratch slots, starting at most four builds a tick under
fixed allowances: 8192 work units a tick, 4096 a slice and 524288 a request,
with a 120-tick backoff once a request reaches its cap. Work units charge
visited entries/nodes and bounded heap operations, not nanoseconds; array
allocation and VM scheduling are not preemptible. Total-limit backoff remains
pending (no member fallback), survives routine epoch churn, and is released
by materially changed routing context. New goals/actions/topology cancel active
jobs; replans may adopt the same geometric proof under their new exact pending
identity without renewing age/work. Soft-cost publications do not restart it.
Current-start drift outside a finished
field restarts under the same total allowance.
`SQUAD_ROUTE_WORK/YIELD/LIMIT/CANCEL` and query-time `slowSquadRouteWork`
samples expose action, goal, stage/status, slice work and expansions, lifetime
work/age, and current/last/max seed expansions. Slice counts are additive;
lifetime and per-seed cumulative samples are not. `SQUAD_PATH_FIELD_BUILD`
measures slices; completed build counts remain separate. Inspect wait and
cancellation alongside frame timings:
pending means postponed objective travel, not successful movement.
Corridor membership reads the mesh's existing cell-to-region index; it no
longer stamps selected rectangles into a second per-cell mask. Coverage and
route search are unchanged, while those redundant writes no longer consume
the work allowance.
`-Dbattle.tail.replanCpu=true` adds opt-in current-host-thread CPU around
actual infantry replans and their goal-selection envelopes. Tail samples
keep disabled/unsupported/incomplete CPU readings as -1; selection is nested
within replanning and must not be added to it. Individual goal probes remain
wall-only. Wall minus CPU is a scheduling/runtime clue, not proof of lock
contention. `ALERT_*` buckets split squad alert into noise-mailbox drain,
belief maintenance, awareness, noise detection, incoming fire and final
publication; candidate and LOS counts describe visited entries and actual
visibility calls, not cache misses or ray steps.
Squad awareness gathers through the hostile-combatant projection before live
range and visibility checks. Candidate counts measure
returned entries, not every primitive entry visited inside the spatial index.
Flat A* omits the unavoidable additive crowding toll of a fixed goal during
search; intermediate crowding and terrain costs still rank routes.
Tail reports
include counts at 30/40/50 ms,
and exclude warmup from async-route cumulative deltas. Pass
`-Dbattle.tail.sourceRevision=<commit>` to retain an explicit source revision;
omission records `unknown`, not a guessed checkout. Lifetime latency maxima
remain explicitly warmup-inclusive.
Configurable orders such as DefendSite and DefendTrack are reported separately.
`FIRING_INDIVIDUAL_*` inner counters expose firing-position candidate and ray
work (count-only counters have zero time).
Unconstrained firing positions prove reachability against the grid's
topology-revision component cache rather than a discarded A* route, without
changing movement rules. Capture posts are spread within the compound footprint,
maintaining nearest-selected distances incrementally. `HOLD_POSITION` records total picker
time, `HOLD_POSITION_CELL` inspected cells, and `HOLD_POSITION_FALLBACK` the
exceptional nearest-zone-cell fallback when no local intersection exists.
Goal selection evaluates a lower priority category only after every goal above
it has failed to supply a plan. Breach eligibility walks the squad's own members
and hostile-combatant buckets, not the whole roster. Execution firing positions
are retained: one chosen cell is validated and kept for 300–360 ticks unless intent or legality changes; eligibility
probes and negative/vantage-only answers are not retained. `FIRING_RETAIN_*`
counters distinguish validation time, reuse, and fresh searches.
JFR tail recordings explicitly capture 1 ms monitor/park waits, full safepoint
lifecycle, GC pause and VM-operation events, and a `UnitDispatch` event around
parallel submission/join. Its `wallNanos` is the real elapsed interval; the
outer `awaitWorkers` metric includes useful execution as well as waiting.
Target-ring expansion stops at the sight/known-contact bound.
`TARGET_SCAN_VISIT/RING/RAY` count its candidates, completed rings, and
visibility checks. Distant beliefs extend the bound; unknown enemies cannot become eligible merely
because no visible winner was found nearby.
Room-target selection is one distance-pruned pass that keeps both the nearest
visible and the nearest room enemy. `CLEAR_ZONE_TARGET_SELECT/VISIT/RAY` report
selection wall time, roster visits, and LOS checks. It preserves exact room scope and first-dense-roster ties without a visibility-radius cap.
Route-threat publication holds a dedicated once-per-tick monitor rather than
the broad squad monitor, preserving complete current-tick decisions and hysteresis.
Rally route requests are member-owned intents published after join;
`RALLY_REQUEST_PREPARE/COMMIT` separates host work from worker dispatch, and
route poll/cancel/no-op counters quantify the contention surface.
Commander topology snapshots report disjoint `COMMANDER_TOPOLOGY_GRID_COPY`,
`ZONE_COPY`, `CELL_COMPONENTS`, `ZONE_COMPONENTS`, and `PUBLICATION` wall stages
nested under the existing rebuild/lookup timers. `MAP_CELLS`, `ZONE_CELLS`,
and `ZONES` are count-only inputs. `-Dbattle.profile.commandTopologyCpu=true`
adds current-host-thread CPU for the whole freeze; a zero sample count means
disabled or unavailable, not zero CPU. Tail reports retain every measured-tick
rebuild, including those outside the worst-tick samples; revision context is
observed after the tick.
Command-frame freeze separates `COMMANDER_FRAME_ASSIGNMENTS`, `SQUADS`,
`INFLUENCE` and Conquest `FACTS` wall stages, nested under FRAME. They do not
include every frame operation. The same `commandTopologyCpu` switch adds
`COMMANDER_FRAME_CPU` around the whole frame stage, including the initial
assignment snapshot and all perspectives, not topology or strategy planning.
CPU and wall are overlapping readings, not additive work.
`-Dbattle.profile.commandFrameDetail=true` additionally accumulates disjoint
per-row `COMMANDER_FRAME_SQUAD_ROSTER`, `MEMBERS`, `CONTACT` and `PUBLICATION`
wall slices within SQUADS; publication includes final sorting. Sample counts
are perspective freezes, while `SQUAD_ROWS`, `MEMBER_INPUTS` and `BELIEF_INPUTS`
report input volumes. Belief input is retained list size, not contacts visited
before a short-circuit. Tail reports retain every measured command-frame
sample, including pulses outside the worst whole ticks. Detailed timing is
off by default and does not change the frozen frame contents.
Commander and asynchronous route snapshots are private navigation-only copies
that store flags and edges without allocating unused cover, wall-health, smoke
and barrier-object arrays; the generic full-copy API remains available for
consumers needing full grid storage.
`-Dbattle.tail.influenceResolution=true` adds an opt-in paired 8/16-cell influence
experiment after the timed replay and JFR recording. Both resolutions use
identical frozen geometry and current faction-local sources, with attenuation
scaled by block width relative to the 8-cell reference. It measures graph and
field costs and spatial differences, not commander decisions or combat outcome.
Precise movement, casualty routing costs, and the production 8-cell resolution
remain unchanged. Graph connectivity and block-value aliasing are different:
disconnected rooms within one block may share its displayed scalar value
without becoming connected in propagation.
During member updates projectile readers share an update-start hazard view and
launches are collected as intents before projectile physics; fresh
launches remain visible to reservation checks. `PROJECTILE_PUBLICATION_*`
records host preparation and collection rather than hiding moved work.
Grenade avoidance considers only imminent travel before impact: a safe unit pauses its path,
while an exposed unit seeks a bounded local escape and reuses it while the
hazard, terrain and owned path remain compatible. `GRENADE_PATH_HOLD` and
`GRENADE_ESCAPE_SEARCH/EXPANDED/REUSE/NO_ROUTE` distinguish decisions and
work. Escape search uses fixed local scratch rather than full-map route
preparation; its work ceiling is not a whole-reflex or wall-clock guarantee.
Projectile enumeration and observation remain separate work. Guard-post route samples label
fallback, engagement, investigation, quiet patrol and disengagement so a
shared action name cannot hide which obligation requested an expensive path.
District PatrolRoute samples likewise label quiet waypoints separately from
investigation; a RoutinePatrol goal alone does not establish the branch.
Quiet guard waypoint routing bounds optional infantry patrol proofs to 1024
expansions and the existing detour envelope, holding a squad dwell after
refusal. Required home, investigation and engagement routes are unbounded.
`GUARD_PATROL_SEARCH/EXPANDED/REFUSAL/BACKOFF` distinguish proof and refusal;
BACKOFF includes ordinary quiet dwell, not only refusal reuse. Quiet district
waypoints follow the same optional proof and dwell policy, with separate
`DISTRICT_PATROL_SEARCH/EXPANDED/REFUSAL/BACKOFF` counters. Random district
waypoints are not a mandatory authored room tour; GarrisonPatrol remains
outside both optional policies. These are per-member
search ceilings, not per-squad, whole-tick, allocation or wall-time guarantees.
Clearance-job cancellation follows immutable body identity: only mech path
clears enter the async clearance coordinator. Infantry path set/clear/request
cannot own those proofs and bypasses its monitor. Mech clears still cancel
unpublished proofs even without a loadout; snapshot preparation and admission
among actual clearance owners remain synchronized and are separate work.
`-Dbattle.vehicle.progressiveRecovery=false` restores whole-proof frozen-field
vehicle recovery. By default each vehicle retains its recovery frontier across
ticks, admits at most one new A* attempt per tick, and expands at most
`battle.vehicle.recoveryExpansionsPerTick` nodes (default 2048). Pending is not
failure; incompatible ownership, terrain, cell or bearing cancels the job.
`VEHICLE_RECOVERY_SETUP` measures new frontier allocation inside SEARCH;
PENDING/CANCEL and CLEARANCE_CELL/COST_CELL expose progress and invalidation.
This is per vehicle, not a fleet-wide or wall-time bound: allocation, completed
path refinement and ordinary tracking remain separate sources of work.
Timings are machine-local diagnostic evidence, never a portable test gate.
`-Dbattle.unitUpdate.parallelism=N` selects an explicit unit-worker count for
scheduling controls; omission retains the production processor-count policy.
The tail report separates sampled per-unit wall time from summed worker CPU
time across the dispatch. CPU excludes time off-CPU, not just useful work;
worker scheduling overhead is included, and `cpuMeasuredThreads` reports
counter availability. Both sums overlap across workers and are not tick wall
time. Use the custom tick event's `wallNanos` for JFR wall comparisons: on the
bundled Intel Java 17 running through Rosetta, JFR event duration was measured
at about 0.4 times `nanoTime`/real elapsed time. Event overlaps remain useful,
but uncalibrated JFR durations there are not milliseconds of wall time.
This focused tail pass does not replace the proposed fixture performance
matrix and its cross-run compatibility checks.
