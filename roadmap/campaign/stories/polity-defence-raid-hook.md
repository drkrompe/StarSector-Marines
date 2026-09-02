# Polity defence: meeting a vanilla raid on the ground

Status: PLANNED

Written: 2026-09-02

Read `meta-progression.md` (the company-and-polity boundary and the vanilla
seam), `contracts-nouns.md` (the stationing response this reuses),
`mission-tier-nouns.md`, and `campaign-battle-bridge-nouns.md` before
implementing this story.

## Goal

When a vanilla raid comes for one of the player's own colonies, offer a second
way to stop it: meet the landing on the ground with the company's own
detachment, and if the landing is destroyed, send the raid home. Today the
player's only answer is the space battle. Vanilla resolves the ground half as
a strength ratio that already counts marines sitting in colony storage; this
story replaces that number with a battle when the player is there to fight it.

Under the growth rule in `meta-progression.md`:

- **Produces** Capability, modestly — ordinary recovery from the landed force.
- **Consumes** Reach: the company has to be at the colony, in the window, with
  a detachment it is willing to spend.
- **First reachable** at Stage 2. It needs a colony, which vanilla gates.
- **Counter-pressure**: no fee, no Standing, and real casualties. A lost defence
  leaves vanilla's raid to land exactly as it would have.

## Scope

The **fleet-group raid** shape only: a `GenericRaidFGI` whose payload is an
`FGRaidAction` against a system holding a player-owned market. That is what
pirate and Pather hostile activity, the Persean League and Diktat expeditions,
and the Tri-Tachyon mercenary attacks all use in 0.98a. The older `RaidIntel`
stages, punitive expeditions, Hegemony inspections, and blockades are separate
machinery and are out of scope.

The **player-present** case only. The offer exists while the raid group is in
its payload phase and the market it targets has not yet been raided by it.
Once a fleet has performed its raid, the offer is gone and the raid stands. A
raid group has days of warning built in — a prep phase, then travel — so the
window is the ordinary one an intel notice gives; missing it is the ordinary
cost of not being there.

## Shape

**The offer is a query, not a record.** Nothing about a pending threat is
persisted. When the Marine Ops dialog opens on a player-owned market, it asks
the sector for live raid groups whose payload is in this market's system, still
allow this market, and have not raided it. Battles are transient
([[battle_transient_no_save_load]] in project memory) and the vanilla intel
object already persists the raid, so a second record of it would only drift.
What is persisted is the outcome: one polity-defence record per raid group and
market, keyed by the group's memory key where it has one and its intel identity
otherwise, so a fought defence is written exactly once.

**The battle is the Garrison defence's battle.** `GarrisonDefenseMissionFactory`
already fights a stationing defence as an Assault-shaped mission — the raiders
have landed, destroy their ground force — through `StationingResponseLaunch`
and settles it through `GarrisonDefenseResolution`. The polity defence is that
shape with the polity as the protected party: the target profile is the
colony's own market through `TargetProfileResolver`, the defender-faction
override names the raiding faction, and the operation tier is read from the
raid group's ground strength through the mission-tier recommendation rather
than from a patron's demand. A colony is a real market, so its map is never
Neutral.

**A win aborts the group; a loss does nothing.** `FleetGroupIntel.abort`
expires the route, marks the payload finished, gives every fleet a return
assignment, and notifies the group's listener — for a pirate raid that is the
hostile-activity factor, whose abort handler grants the piracy respite. That
is how vanilla itself sends a beaten raid home, and it is the only write this
story makes to vanilla. A loss writes nothing: the raid proceeds through
vanilla's own path. There is no partial result.

**Casualties and recovery settle as for any operation.** The mission outcome
runs through `MissionResolver` into the ordinary personnel and loot
authorities, with the same recovery entitlement a Garrison stationing response
settles with. No MRB credibility, no house relationship, no contract
settlement, no patron reward.

## Slices

1. **Threat snapshot and window predicate.** A plain `RaidThreat` value —
   system, targeted markets, payload live, raided count, ground strength,
   faction, group key — built by one thin adapter over the fleet-group API,
   and a pure predicate deciding whether a threat is fightable at a given
   market. The predicate is unit-tested; the adapter is not.
2. **Mission factory.** `PolityDefenceMissionFactory` from a threat: the
   Assault shape, the override, the tier mapping. Tests pin that tier is
   monotonic in raid strength and that the override is applied.
3. **Launch route.** One entry in the Marine Ops dialog on a player-owned
   market while a threat is live, routing through `MarineOpsContext` to the
   briefing the way `StationingResponseLaunch` does. One route, so the
   dialog and any later popup are provably the same operation.
4. **Resolution.** `PolityDefenceResolution` consumes the outcome: a win aborts
   the group through an abortable seam the tests can fake, and writes the
   record; a loss writes the record only. The record is exactly-once under
   repeated resolution.
5. **Acceptance instrument.** A debug command that spawns a `GenericRaidFGI`
   against the player's colony with a short prep and travel, in the shape
   `debug-political-contract-completion.md` uses for chains, so the live pass
   does not wait on the hostile-activity meter.
6. **Live acceptance.** Fight one pirate raid to a win and confirm the fleets
   turn for home and the respite lands; lose one and confirm the raid resolves
   exactly as vanilla would have. Measure the window: the tick at which a
   fleet performs its raid relative to its arrival in orbit, since the offer's
   usefulness depends on that gap.

## Acceptance

- A player-owned market under a live fleet-group raid offers the defence from
  the Marine Ops dialog; the same market with no live raid, or one already
  raided by the group, does not.
- A won defence aborts the raid group: its fleets take return assignments, and
  a pirate raid's abort listener fires. The group is not aborted twice.
- A lost defence changes nothing in vanilla: stability, industries, and the
  raid's own bookkeeping are exactly what an unfought raid produces.
- Casualties, recovery, and the polity-defence record settle exactly once, and
  no MRB, house, contract, or patron fact changes.
- The operation's tier rises with the raid's ground strength and its map is
  generated from the colony's own market.

## Out of scope

- The absent-player case: a detachment stationed on the polity contributing to
  the `GROUND_DEFENSES_MOD` stat so autoresolve feels it. That needs a
  stationing shape with no patron, which is a `contracts-nouns.md` question
  first.
- Raids in the `RaidIntel` shape, punitive expeditions, inspections, blockades.
- MRB scrutiny of a company that owns worlds (`meta-progression.md`).
- Any Chronicle or comms-officer presentation beyond what the ordinary mission
  outcome already produces.

## Open questions

- One group may target several markets in a system. A win at one market
  aborts the whole group, which frees the others; the alternative — abort only
  when the fought market was its last unraided target — is more exact and
  less legible. Decide from the live pass.
- The fleet group holds one listener slot and the hostile-activity factor owns
  it. The abort path does not need the slot; if a later slice needs to observe
  the raid rather than end it, that is the first obstacle.
- Whether the raid faction's landed force should be sized from the group's
  ground strength directly or from the tier it maps to. Tier keeps the
  battle on the mission-tier envelope; direct sizing is truer to the fleet.
