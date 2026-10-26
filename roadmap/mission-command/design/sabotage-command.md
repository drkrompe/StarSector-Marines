# Sabotage command

Status: SHIPPED — paired named-site attacker and defender command, diagnostics, and deterministic evidence are in production.

Written: 2026-08-27

Updated: 2026-08-29 — named sites now use compact world beacons with progress
emphasis only during an uninterrupted plant.

Read `mission-command-nouns.md` for the shared architecture. Sabotage objective
systems retain charge location, planting, progress, completion, and victory
authority.

Sabotage is a named-site logistics and security command duel. Production
missions construct exactly three stable reachable **named sites**. Identity is
preserved through construction, frames, directives, snapshots, diagnostics,
traces, and fixture replay; it is never inferred from list order or label.

Authored sabotage construction may state installation sprawl and whether the
defender has a continuing reinforcement layer. Those are separate inputs,
frozen into the replay fixture. Unstated inputs retain the legacy settlement
map and ordinary reinforcement behavior; a finite garrison does not refill
through generated workshops, based aircraft or off-map deliveries.

An authored mission states its own three sites (`SabotageSiteLayout.authored`):
stable ids and cells from its map, with a name per site, never selected from
generated points of interest. What a site's completion changes beyond victory
is the mission's adapter to carry out, reading the site by its id (Blind Watch:
`BlindWatchSequence`).

## Marine command

A **site task group** combines the site's individual planter or kit-retriever
duty with squad-level security. A planter/retriever squad receives an
assignmentless mission-command ownership claim so command records provenance
without competing with the individual task. Equipment recovery chooses the
retriever and promotes a successful carrier back to planter.

The first participating squad member entering the site's zone hands the approach
to the planting cordon, matching zone entry's arrival rule. A leader still
outside does not restart a finished entry/clear loop. The planting slot belongs
to a carrier of that exact unfinished site's kit; a finished or different-site
planter in the same campaign squad cannot take it.

The **plant** is the kit squad's job, not the carrier's alone. While a squad
holds a site's kit (a live planter of that site carries it), the charge is worked
whenever the carrier or any live infantry squad mate stands within
`ChargeSiteObjective.PLANT_RADIUS_CELLS` of the site cell with a structural line
to it, settled or not; mechs and vehicles never plant. No eligible body in reach
resets progress to zero. When the carrier falls the kit drops and the squad no
longer holds it, so a mate already beside the charge does not keep the channel;
the retriever who picks the kit up becomes the site's planter and their squad
plants again. `planterOnSite` in facts, HUD and traces means "being worked".

A site on **open ground** (in the map's open exterior, the zone that dwarfs every
other, `ZoneGraph.exteriorZoneId`) has no room to clear or doorways to hold: its
security squads defend the site's own cell (`DEFEND_SITE`) instead of clearing
the zone, and the planting cordon is a ring of posts round the charge instead of
the exterior's doorways, which are every door on the map.

Security spreads across reachable unfinished sites before concentrating.
Affinity survives ordinary pulses and may break on completion, loss,
unreachability, or special-task handoff. Own force, route cost, and believed
pressure may bias surplus reinforcement; absence of belief is never clearance.
The picture explains active planter, assigned recovery, unsupported dropped
kit, awaiting planter, or completion for each site.

## Defender command

Defenders know their installation identities, geometry, zones, and completion,
but not Marine task identity, kit state, exact plant progress, or inferred
hostile cells. A charge being worked raises an identity-free installation alarm
that remains latched for a bounded interval after interruption. Belief-derived
pressure is separate evidence.

Born garrisons stay externally owned. Starting patrols cover unfinished sites
before doubling and retain a held reserve when force size permits. Alarm or
believed pressure consumes only available reserve, gives threatened sites one
responder before a second, and never strips routine guard elsewhere.
`DEFEND_SITE` uses distinct reachable perimeter rallies so responders do not
collapse into the plant cell. Under believed pressure the reserve, and a site post pressed hard (at most a
third of the posts at once) for a building beside its site, man the map's
garrisonables (`garrisonables.md`, mission defenders, the duty bound); every
other post fights from the open.

## Picture and evidence

Each perspective publishes site phase, affinity/coverage, pressure or alarm,
reserve, role, directive reason, and rally. Defender traces omit attacker-only
task facts. Exact charge progress may be legally disclosed to Marines and
recorded by the neutral referee, but never becomes defender knowledge.

Headless evidence measures coverage, planter/recovery transitions, response,
directive churn, progress, casualties, duration, and terminal or timeout
outcome. The shipped implementation stories are recorded in `shipped.md`.

The player-facing world marker derives its compact `S1`/`S2`/`S3` label from
the stable site identity rather than objective list order. An idle site is a
subdued amber beacon, an uninterrupted plant adds the bright progress arc and
percentage, and a completed site settles into a quiet armed state. Presentation
does not expose planter identity or grant the defender exact progress knowledge.
