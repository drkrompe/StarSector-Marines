# What a Conquest force spends its first three minutes doing

Status: PROPOSED — three findings off the 560x336 matrix, all measured, none of
them changed. One of them was built and replayed at full length and is written
down here as a negative result rather than shipped.

Written: 2026-09-01

Two readings wanted explaining: `reinforced-south` contests nothing for 5,610
ticks at `Standoff.CLOSE`, and `full-strength-west` never captures anything at
any standoff. Both were traced tick by tick out of the preserved commander
traces, and what came back was one geometry defect, one arrival cadence, and one
capture rule — pulling against each other hard enough that fixing the first
alone makes the battle worse. That last part was measured, not reasoned.

## The first three minutes are the walk, and the standoff is measured from the
## wrong side of the beachhead

The premise that "moving the beachhead in saved only 1,400 ticks, so distance is
not what dominates" inverts once you know how far the beachhead actually moved.
It moved **78 cells** — the berths went from y≈2 under `FAR` to y≈80 under
`CLOSE`, not from the map edge to 40 cells out. Marching is ~18 ticks a cell,
measured directly off squad 115's own track: (198,80) at t=376 to (237,212) at
t=3,451, 171 cells in 3,075 ticks. So `CLOSE` removed ~1,400 ticks of walk and
added 225 ticks of inbound flight — the first sortie lands at t=376 instead of
t=151, because the berths are 78 cells further from the edge the shuttles cross.
Observed saving: 1,380. There is no residual big enough to hide another
mechanism in.

`Standoff` promises cells rather than a fraction of the map, in its own words
"because doubling the map should not double the minutes before first contact".
`ApproachRegion.gapTo` measures the gap from the attacker band's
**objective-facing** side, while `PrecinctLandingAreaStage` scans berths inward
from its **approach-facing** side. A stated band is a third of the map, so the
band's own depth is an unstated addition to every standoff — and being a
fraction of the map, it doubled when the map did. Measured on the two matrix
fixtures at `CLOSE`, whose stated standoff is 40 cells:

| fixture | axis | stated band | claim edge | berths seated at | actual approach |
|---|---|---|---|---|---|
| reinforced-south | S→N | y 0..112 | y 229 | y 77..93 | **149** cells |
| full-strength-west | W→E | x 0..186 | x 416 | x 190..212 | **226** cells |

### The 5,610 ticks, by component (reinforced-south, CLOSE)

| component | ticks | share |
|---|---:|---:|
| first sortie's inbound flight — no marine on the map until t=376 | 376 | 7% |
| squad 117's march, from its berth at (358,80) to `ARMORY@472,206` — 240 cells at ~21 ticks a cell | 5,108 | 91% |
| form-up, first orders, contact | ~130, overlapped | 2% |
| arrival pacing | 0 — the first wave lands at t=376 | 0% |

Nothing else is in it. There were no command-unassigned squad-pulses, no
plan-less squads, and no target-zone latency on the first assignments: squad 115
was given `SECURE_COMPOUND` on the airbase on the very first tick it existed and
walked at it without a pause for 3,450 ticks.

Why that march is 240 cells and not 40 is two separate things. The **nearest**
compound, `AIRBASE@259,239`, is 159 axial cells from the berths: 109 of them the
band's own unstated depth, 40 the stated standoff, and 10 from the claim
boundary to the anchor. Squad 115 walked exactly that and stood on the airbase's
perimeter from t≈3,826. It could not contest it — see the capture rule below —
so the first compound anybody could dispute was `ARMORY@472,206`, another 214
cells east, and the 5,610 is squad 117's walk to that one.

## Measured and rejected: correcting the measurement alone is a worse battle

`ApproachRegion` was changed to read the gap from the side the force lands on,
with a slid region's leading side squeezed against the claim so a 40-cell
standoff produces a 40-cell-deep beachhead. The geometry came out exactly as
intended — southern berths at y≈189 against a claim edge at y=229, western ones
at x≈376 against x=416, both maps still generating and still seating more
arrival areas than their drop-zone count — and the matrix was replayed at full
length:

| fixture | | first contested | first held | captures | held | marine losses | defender losses | result |
|---|---|---:|---:|---:|---:|---:|---:|---|
| reinforced-south | shipped | 5,610 | 5,730 | 11 | **11** | **95** | 393 | **MARINE, t=13,680** |
| reinforced-south | corrected | **4,170** | **4,290** | 13 | 9 | 174 | 406 | timeout |
| full-strength-west | shipped | 15,570 | never | 0 | 0 | 265 | 159 | timeout |
| full-strength-west | corrected | **never** | never | 0 | 0 | 214 | 83 | timeout |

First contact moved in by 1,440 ticks, which is the correction doing exactly
what it claims. Everything else got worse, and the reason is the second finding
below: **the standoff and the ferry pull against each other.** Sliding the
beachhead inland shortens the walk once and lengthens every re-arm run forever.
The shipped cadence on reinforced-south is ~875 ticks a cycle; corrected it is
~1,600, and on full-strength-west it goes from ~1,450 to ~2,775. So the
corrected west landed 19 squads of 34 in the whole battle against 32 before, and
peaked at **49 live members in 5 squads** — three squads at a time set down on
the objective's doorstep and destroyed there, five of its eight lost squads
never making a basis point of approach progress. Reinforced-south takes thirteen
compounds instead of eleven and loses four of them again, which is precisely the
`STANDARD` failure mode `precincts.md` already records: arriving faster than the
force can consolidate is a different battle from arriving slowly.

The change was reverted. `Standoff`'s Javadoc now says what its number actually
measures, so the next reader is not misled, and the arithmetic is untouched.

**What this means for the standoff table.** The default was chosen from a table
in which `CLOSE` meant 149 cells on the southern fixture and 226 on the western
one. Correcting the measurement re-points every constant at once — `CLOSE`
becomes 40, `STANDARD` 80, and `FAR` becomes 229/416 — and no constant in the
enum then reproduces the geometry the matrix actually picked. Fixing this
properly is: correct `gapTo`, and re-choose the default against a rebuilt
three-setting table, probably alongside the cadence below rather than before it.
That is the owner's documented procedure — "change this against fresh matrix
evidence, not a feeling about distance" — and this document is the fresh
evidence that the naive form of it is not the answer.

## Finding, not fixed: the arrival cadence does not scale with the force

*Answered 2026-09-02, and only half the way this expected. The cadence half is
fixed: marine shuttles descend from orbit onto a point off their own berth, so
the round trip is a constant and does not move with the standoff — measured 537
ticks at `CLOSE` and at `FAR` alike. The scaling half was built, measured and
switched off: sizing the lift from the committed seats lands the whole western
force early, ends the plateau below, and takes **fewer** compounds than the
three-pair ferry does. `conquest-nouns.md` owns both and carries the table.*

Squads land three at a time, one per drop zone, on a fixed cycle. Measured
across five full runs, that cycle is
**~460 ticks plus ~5.3 ticks per cell the beachhead has been slid inland** —
the Aeroshuttles fly the slide twice on every round trip:

| run | berths at | cycle | last squad lands |
|---|---|---:|---:|
| reinforced-south FAR | y≈2 | ~465 | t≈2,550 (17 squads) |
| reinforced-south CLOSE | y≈80 | ~875 | t≈5,200 (17 squads) |
| full-strength-west CLOSE | x≈195 | ~1,450 | **t=17,551** (34 squads) |
| reinforced-south, corrected | y≈189 | ~1,600 | — |
| full-strength-west, corrected | x≈376 | ~2,775 | never; 19 of 34 land at all |

Doubling the committed force doubles the number of cycles, so full-strength-west
lands its last three squads with 449 ticks of the battle left. The consequence
is visible directly in the shipped run: **alive squads plateau at 12–15 from
t≈6,000 to the end**, and alive members at ~130, out of 34 squads and 408
marines committed. Arrivals exactly replace losses. The force is committed in
penny packets into a 226-cell approach and destroyed at the far end of it — 265
marines lost against 159 defenders, 21 of 34 squads wiped, every marine death
between x=400 and x=529, 16 of 47 secure-travel episodes ending in squad loss 18
to 101 cells short of their target. That is the whole of why the larger force is
the one that fails while reinforced-south wins on the same map: south finishes
landing at 29% of the battle and west at 97%.

**Full Strength authors 34 squads through the same three pairs Reinforced uses
for 17**, and the obvious reading of that — scale the pairs with the seats and
the same force lands in the same share of the battle whatever its size — was
implemented and is wrong. It delivers precisely what it promises: the plateau
above is gone, 356 live marines assemble instead of ~130, and the west takes 10
compounds and holds 4 where the ferry flying the same descent takes 11 and holds
7. The penny packets were never the reason the west fails; the approach is, and
a force delivered into it faster is destroyed in it faster. `conquest-nouns.md`
owns the arrival policy, the descent that did help, and the derivation that is
kept switchable for the next attempt at that approach.

## Finding, not fixed: a compound whose capture zone is the outdoors is a
## different victory condition from the other ten

Every enclosed compound on these maps resolves a capture zone of a few hundred
cells holding 18–32 defenders. Both airbases resolve `captureZoneId` **0** and
report **246–284** defenders standing in it at tick zero, rising to 265.
`conquest-nouns.md` already records why an open compound's room is the outdoors,
and scopes occupancy to the footprint as well, which is what stops it reading as
permanently contested; what that does not change is who is counted present. An
airbase therefore flips only once essentially every defender who is outdoors
anywhere has been killed.

What it costs, measured:

- reinforced-south took its airbase at t=13,560 — **last of eleven**, 4,530
  ticks after the tenth, with the terminal at 13,680. Its "longest observed
  capture gap: 5,730 ticks" is that wait.
- full-strength-west has **two** of them, and neither ever moved. Its three
  observed capture-zone cohorts spent 17,347 ticks MIXED with a peak capture
  progress of 2,500 basis points and a longest marine-only run of 65 ticks.
- The nearest compound to the beachhead is an airbase on both fixtures, so the
  first squad ashore is always sent at the one objective it cannot take, and the
  force's first useful work is somewhere else entirely — which is where most of
  the 5,610 above actually went.

This is a rule about what holding a compound means for a compound with no walls,
and Conquest requires every compound to flip. It is the owner's to decide:
scoping presence to the footprint for counting as well as for occupancy, or
giving an open compound its own capture radius, are both real answers and both
change what a Conquest is.

## What was ruled out

Checked against the traces rather than assumed, and not implicated in either
finding:

- **Command coverage.** Zero command-unassigned squad-pulses and zero
  squad-ticks on both shipped fixtures, all causes zero. Every alive squad
  carried a directive on every pulse.
- **Assembly.** `SquadFormUpSystem` releases within one cycle gap; squads reach
  twelve members ~150 ticks after first appearing. No squad waited out its
  60-second timeout.
- **Track allocation.** Both fixtures published a peak track share of 10,000
  basis points with squads spread across all three tracks; west's 34 squads were
  not starving a track.
- **Retarget churn.** West's 298 retargets under `CLOSE` are 18 secure-travel
  retargets across 47 episodes, all "objective changed" — the commander
  reassigning as compounds move, not thrashing.
- **Reachability.** Zero unreachable and zero no-actionable assignments on both.
  311 of 321 west assignment episodes made at least a cell of marker progress.
  Squads could path where they were sent; they were killed on the way.
