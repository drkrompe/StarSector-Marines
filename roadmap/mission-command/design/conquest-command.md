# Conquest command

Status: ACTIVE — paired attacker and defender command is implemented; live convergence and reinforcement acceptance remain.

Written: 2026-08-27

Updated: 2026-08-28 — defined explicit secure-travel exits separately from
contact, active-path, and quiet-travel context.

Read `mission-command-nouns.md` for the shared architecture and
`conquest-nouns.md` for territory, compounds, supply, keep, and victory law.

Conquest is a directional territorial command duel. Three lateral **tracks**
organize a readable front across the map's traversal axis. A track is a sticky
coordination preference, not an ownership fence: squads may support a neighbor
when their home track has no useful work, and the whole mobile force may
converge for the culminating keep or final contested compound.

## Marine command

The attacker balances front pressure with deliberate compound capture. Unknown
occupancy permits a measured probe, not a declaration of clearance. Fresh
distant capture allocations preserve squads already committed or adjacent, use
squads without useful front work first, and retain at least one executable
front squad while actionable resistance exists when force size permits.

When belief shows open-ground resistance but no discrete room is assignable,
an advance-track order stages behind the hostile frontier, bounded by friendly
lead, safe stride, reachability, and no-backtracking law. Local contact then
hands execution to squad doctrine.

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
tracks are preferred, and at least one free patrol remains in reserve when the
pool permits it. Expired reports release only defender-command-owned responses.

Explicit hold, recapture, and relief tasks outrank soft track response. A
Conquest convoy enters through the strict defender rear edge, uses a frozen
behind-front deployment hint, and hands its passenger squad directly to
`conquest-defender` with the request's relief objective. Shuttle and walk-in
forces keep reinforcement ownership until another explicit mission policy
defines their handoff.

## Picture and evidence

The perspective front picture publishes phase, track extents, friendly body and
lead, believed-hostile frontier and pressure, preferred/effective track,
reserve, assignment reason, and exact commander target or labelled zone marker.
It includes frozen own-squad position, leader zone, local contact, execution
suspension, active-path count, and the count of own members in the squad's
assigned target zone. Exact whole-zone occupancy, capture progress, and
ownership transitions remain neutral referee facts.

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
