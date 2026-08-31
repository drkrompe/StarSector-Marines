# Squad range quorum

Status: DRAFT

Written: 2026-08-31

## The defect

`WorldStateBuilder.evalInRangeOfTarget` is an any-member predicate: it reads
true the moment one squadmate is within its own `attackRange` of an actionable
believed contact. A squad-148 dump showed the consequence — ten marines, one
anti-materiel rifle (range 38) and nine rifles (14 to 32), all 28 to 33 cells
from the only observed contact. `IN_RANGE_OF_TARGET` read true for the squad on
the strength of the one long gun, while nine marines never submitted a fire
intent in 1525 ticks. A squad-level fact assembled by asking whether *any*
member satisfies it lets one specialist speak for a body of people.

## Why the obvious fix is not the fix

A half-quorum over live members was implemented and measured against the
canonical Conquest matrix, controlled: same tree, same seeds, identical fixture
hashes, with and without the change.

| | base | A+C only | A+B+C |
|---|---|---|---|
| `full-strength-west` captures | 2 | 4 | 1 |
| `full-strength-west` secure-compound episodes | 18 | 35 | 13 |
| `full-strength-west` compound assault commitments | 5 | 6 | 1 |
| `reinforced-south` duration ticks | 17652 | 7927 | 15314 |

The quorum cost `full-strength-west` three quarters of its captures and nearly
doubled `reinforced-south`'s battle length. The mechanism is the planner: the
predicate reaches it only through `EliminateEnemiesGoal`, where a false reading
inserts `ApproachPosture` ahead of `EngagePosture`. A quorum therefore keeps a
squad approaching for longer before it is allowed to decide it is fighting, and
on these fixtures that traded away the assault.

Note what this means about the diagnosis: the quorum is a real correctness fault
and it is **not** the fault that produced the observed behaviour. Squad 148 was
on `AttackMove [MISSION]`, which never consults the predicate.

## What a real fix has to do

Separate the two things the predicate is being asked. "Can this squad bring
fire on that contact" and "should this squad stop approaching" are not the same
question, and the any-member reading is only wrong for the first. Candidates:

- a quorum for the *reporting* fact, with `EngagePosture`'s precondition left on
  the any-member reading, so the planner's approach/engage decision is unchanged;
- a quorum whose fraction is derived from the squad's own weapon mix rather than
  a flat half, so a marksman-heavy squad is not held to a rifle squad's standard;
- leaving the predicate alone and fixing the *consumer* that actually cares.

Whichever is chosen must be measured on both canonical fixtures before it lands.
A change that improves the squad's self-picture and costs the assault is not an
improvement.

## Acceptance

- `IN_RANGE_OF_TARGET` no longer reads true off a lone long-range member.
- `full-strength-west` captures and secure-compound episodes at or above the
  A+C numbers above; `reinforced-south` duration not materially longer.
- A unit test pinning the quorum arithmetic and the small-squad rounding case.
