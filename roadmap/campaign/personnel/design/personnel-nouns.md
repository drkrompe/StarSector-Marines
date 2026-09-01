# Personnel nouns

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-09-01 — a new campaign begins without named line personnel;
Fleet Armory founding atomically converts a complete cargo bill into one full squad.

## Purpose

Personnel makes the player's force a continuing company rather than a count of
interchangeable marines. It preserves who serves, how they are organized, who
is available, and what a battle or posting changes. Battle simulation consumes
a frozen personnel commitment and returns facts; it does not invent, replace,
or reorganize campaign people.

## Organization

A **marine** is a named, persistent rank-and-file member. A **squad** is the
durable, player-facing formation of up to twelve marines. Each squad has three
derived four-marine **fire teams**, determined by squad billet order. A fire
team is an equipment, lift, and battle-maneuver unit; it is not a separately
selected campaign formation or a second persistent identity.

The company hierarchy is company, officer command, squad, fire team, marine.
The reserve pool is a holding area, never a deployable squad or officer command.
Enlisted leadership follows current squad membership, fitness, and experience;
it is re-derived rather than accumulated as a competing promotion track.

A new campaign establishes that empty reserve structure but grants no line squad.
**Squad founding** consumes one indivisible bill of twelve generic Marines, baseline
arms, supplies, and provisions, then creates twelve named members in one new line
formation. Insufficient cargo changes neither inventory nor personnel. Subsequent
vacancy filling remains reserves-first and consumes generic Marines one billet at a
time. Existing saves keep their roster; free complement bootstrap is fixture and
legacy tooling, not a campaign transition.

A squad may name one home officer. Home command is an organizational default,
not ownership of people or a record of temporary borrowing. Officer capacity is
in whole squads and does not shrink with casualties. A garrison remains a
single-officer posting even though a normal operation may be a task force.

## Availability and deployment

Named marines leave generic cargo only when enlisted. Enlistment and reserve
demobilization are the only ordinary boundary between generic cargo and named
personnel; a named casualty must never also remove anonymous cargo.

ACTIVE members are ready to deploy. WIA retain their identity and billet but
are unavailable until recovery; MIA and KIA do not fill a living billet. A
selection shortage and a whole-company shortage are different facts: selecting
the wrong available squads must not silently substitute another squad, while a
company shortage may offer cargo-backed recruitment.
Where that draw is offered, the base-game Marines icon identifies the cargo
input without becoming a new personnel or availability authority.

An **operation commander** is the single officer whose operation outcome is
currently settled. A **task force** is the deployment-time grouping derived
from the selected squads: a squad follows its fit home officer, while an
unassigned squad or one whose home officer is unfit falls to the operation
commander. Each represented officer is checked against that officer's squad
capacity. This lets a deliberately organized company scale without turning a
task force into a persisted second roster.

The selected squads and their named active members freeze at launch in stable
roster order. An explicit empty or unavailable selection fails closed rather
than falling back to the whole company. Employer personnel stay scenario-owned,
and debug fixtures are non-persistent: neither can gain player identity or
write campaign personnel outcomes.

## Outcome and recovery

A mission outcome is the only battle-to-personnel report. It records the
frozen commitment and individual fates, then personnel applies the deterministic
RTD, WIA, MIA, or KIA result once. Survivor career evidence and experience are
recorded from that report; recovery later returns eligible WIA to ACTIVE.

The operation commander currently receives officer experience, promotion, and
injury settlement. Other officers may lead task-force elements for deployment,
but their outcome settlement is not inferred from mutable home assignments.
Per-officer outcome work remains owned by `c13-the-task-force.md`.

## Named stationing

Named stationing binds whole available squads to one stationing contract and
keeps them out of ordinary deployment, reassignment, demobilization, and kit
mutation while away. Its living strength includes ACTIVE and WIA members; only
ACTIVE members consume a response battle seat. A response freezes the stationed
identities before battle, then applies named outcomes before stationing policy
recomputes strength or releases the detachment.

Completion, withdrawal, failed response, and extraction each settle named
bindings exactly once. The terminal rule preserves individual fate and never
creates cargo for named survivors. Legacy anonymous stationing is a distinct
historical authority: it retains its stored aggregate/cargo behavior and is
never auto-bound to the current named roster.

`contracts-nouns.md` owns the agreement, deadlines, and contract settlement
policy. Personnel owns the people those policies may bind, release, or report.

## Captains and discovery

A **captain candidate** is a frozen offer for one future captain from one stable
campaign source. Discovering the same source returns the same candidate;
acceptance is capacity-safe and exactly once; decline is final. Discovery may
observe eligible salvage without changing ordinary salvage rewards or taking
over another interaction's continuation. The source supplies the opportunity;
personnel owns candidate identity, resolution, and roster admission.

A captain's moral outlook is a one-time, mutually exclusive characterization
drawn only from attributable company history witnessed during that captain's
tenure. It is a readable diegetic trait, not a score, reward meter, combat
modifier, or inference about every company act. `moral-compass.md` owns the
hidden factual record and its attribution rules.

## Ownership and extension laws

Personnel owns persistent identities, organization, availability, candidate
admission, casualty/recovery lifecycle, and named stationing bindings.
`company-view-nouns.md` owns formation and company presentation, including the
task-force read model. `progression-nouns.md` owns combat quality, career
meaning, and armory/equipment progression. `contracts-nouns.md` owns
stationing terms and other agreements. `loot-nouns.md` owns recovery claims and
transfer into cargo.

- A new personnel transition must name its frozen authority, replay boundary,
  and legacy-save behavior before it mutates a roster identity.
- Campaign organization and battle realization remain separate: battle may
  report who was present, but it cannot author substitute campaign personnel.
- Derived fire-team membership and enlisted leadership must not become a second
  stored source of truth.
- A posting, candidate, or outcome must leave its owned people either still
  bound by explicit authority or safely available; no terminal path may strand
  a hidden personnel lock.
- New captain acquisition, trait, or social behavior requires a bounded story;
  tavern hiring, wages, dismissal, trading, and interpersonal simulation are
  not implied by the current roster model.
