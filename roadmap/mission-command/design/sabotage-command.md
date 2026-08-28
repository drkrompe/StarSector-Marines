# Sabotage command

Status: SHIPPED — paired named-site attacker and defender command, diagnostics, and deterministic evidence are in production.

Written: 2026-08-27

Read `mission-command-nouns.md` for the shared architecture. Sabotage objective
systems retain charge location, planting, progress, completion, and victory
authority.

Sabotage is a named-site logistics and security command duel. Production
missions construct exactly three stable reachable **named sites**. Identity is
preserved through construction, frames, directives, snapshots, diagnostics,
traces, and fixture replay; it is never inferred from list order or label.

## Marine command

A **site task group** combines the site's individual planter or kit-retriever
duty with squad-level security. A planter/retriever squad receives an
assignmentless mission-command ownership claim so command records provenance
without competing with the individual task. Equipment recovery chooses the
retriever and promotes a successful carrier back to planter.

Security spreads across reachable unfinished sites before concentrating.
Affinity survives ordinary pulses and may break on completion, loss,
unreachability, or special-task handoff. Own force, route cost, and believed
pressure may bias surplus reinforcement; absence of belief is never clearance.
The picture explains active planter, assigned recovery, unsupported dropped
kit, awaiting planter, or completion for each site.

## Defender command

Defenders know their installation identities, geometry, zones, and completion,
but not Marine task identity, kit state, exact plant progress, or inferred
hostile cells. A legal settled plant raises an identity-free installation alarm
that remains latched for a bounded interval after interruption. Belief-derived
pressure is separate evidence.

Born garrisons stay externally owned. Starting patrols cover unfinished sites
before doubling and retain a held reserve when force size permits. Alarm or
believed pressure consumes only available reserve, gives threatened sites one
responder before a second, and never strips routine guard elsewhere.
`DEFEND_SITE` uses distinct reachable perimeter rallies so responders do not
collapse into the plant cell.

## Picture and evidence

Each perspective publishes site phase, affinity/coverage, pressure or alarm,
reserve, role, directive reason, and rally. Defender traces omit attacker-only
task facts. Exact charge progress may be legally disclosed to Marines and
recorded by the neutral referee, but never becomes defender knowledge.

Headless evidence measures coverage, planter/recovery transitions, response,
directive churn, progress, casualties, duration, and terminal or timeout
outcome. The shipped implementation stories are recorded in `shipped.md`.
