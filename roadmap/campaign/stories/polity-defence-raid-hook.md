# Polity defence: meeting a vanilla raid on the ground

Status: PLANNED

Written: 2026-09-02

Updated: 2026-09-02 — rebased on `stationed-ground-defence.md`, which owns the
raid readers, the ending handle, and the absent settlement; added the posting.

Read `meta-progression.md` (the company-and-polity boundary and the vanilla
seam), `stationed-ground-defence.md` (the mechanisms this reuses),
`contracts-nouns.md`, `mission-tier-nouns.md`, and
`campaign-battle-bridge-nouns.md` before implementing this story.

## Goal

When a vanilla raid comes for one of the player's own colonies, offer a second
way to stop it: meet the landing on the ground with the company's own
detachment, and if the landing is destroyed, send the raid home. Today the
player's only answer is the space battle. Vanilla resolves the ground half as
a strength ratio that already counts marines sitting in colony storage; this
story replaces that number with a battle when the player is there, and with
a posted detachment's strength when they are not.

Under the growth rule in `meta-progression.md`:

- **Produces** Capability, modestly — ordinary recovery from the landed force.
- **Consumes** Reach: a posting binds a detachment to one world with no
  retainer, and the fought case needs the company there in the window.
- **First reachable** at Stage 2. It needs a colony, which vanilla gates.
- **Counter-pressure**: no fee, no Standing, and real casualties — from a
  fight when the player is present and from a formula when they are not. A
  lost defence leaves vanilla's raid to land exactly as it would have.

## Scope

Both vanilla raid shapes, through the readers `stationed-ground-defence.md`
builds: fleet groups and raid intel alike. Punitive expeditions against a
colony industry are in; a resisted Hegemony inspection and a blockade are
not, for the reasons that story gives.

The fought case and the absent case. The fought case needs the player at the
market while the raid is in its payload phase and the market has not yet been
raided by it; once a fleet has performed its raid the offer is gone. The
absent case needs a posting.

## Shape

**A posting is stationing without a patron.** The player posts a detachment
to a polity market from that market's Marine Ops dialog. It binds the
detachment and captain the way a stationing term does, has no retainer and no
end date, and is released at the same market. It earns nothing: no MRB
credibility, no house relationship, no settlement consequence. It reuses the
stationing response and settlement machinery as a row whose employer is
nobody, which is the only way the polity can hold a detachment without
becoming a client. A posted detachment contributes to the market's
ground-defence stat exactly as a Garrison one does.

**The offer is a query, not a record.** For the fought case without a posting,
nothing about a pending threat is persisted: when Marine Ops opens on a
player-owned market it asks the threat readers for live raids that still allow
this market and have not raided it. Battles are transient
([[battle_transient_no_save_load]] in project memory) and the vanilla intel
object already persists the raid. With a posting, the trigger arms a defence
on the posting row the way it does on a Garrison, and the response is the
ordinary one. Either way the outcome is persisted once per raid and market.

**The battle is the Garrison defence's battle.** `GarrisonDefenseMissionFactory`
already fights a stationing defence as an Assault-shaped mission — the raiders
have landed, destroy their ground force — through `StationingResponseLaunch`
and settles it through `GarrisonDefenseResolution`. The polity defence is that
shape with the polity as the protected party: the target profile is the
colony's own market through `TargetProfileResolver`, the defender-faction
override names the raiding faction, and the operation tier is read from the
raid's ground strength through the mission-tier recommendation rather than
from a patron's demand. A colony is a real market, so its map is never
Neutral. The detachment is the posted one when there is a posting and a
briefing-time selection when there is not. Once `allied-faction.md` has
shipped, the colony's own allied garrison stands beside the company, kitted
from `polity-ground-doctrine.md`; until then the colony's troops are absent
from the ground, as a patron's are from a Garrison defence today.

**A win ends the raid; a loss does nothing.** The ending handle from
`stationed-ground-defence.md` — abort for a fleet group, force-fail for raid
intel — is the only write to vanilla. A loss writes nothing and the raid
lands through vanilla's own path. An unanswered defence on a posting settles
from vanilla's result with the same grading as a Garrison.

**Casualties and recovery settle as for any operation.** The mission outcome
runs through `MissionResolver` into the ordinary personnel and loot
authorities, with the same recovery entitlement a Garrison stationing response
settles with.

## Slices

1. **Posting.** The row kind with no employer, the post and release actions
   in the polity market's Marine Ops dialog, the binding, and the stat
   contribution through the Garrison mechanism. Tests pin that a posting
   never produces a credibility, relationship, or payout fact.
2. **Threat query and offer.** The dialog-time query over the shared readers
   for the no-posting case, and a pure predicate deciding whether a threat is
   fightable at a market. The predicate is unit-tested; the adapter is not.
3. **Mission factory.** `PolityDefenceMissionFactory` from a threat: the
   Assault shape, the override, the tier mapping. Tests pin that tier is
   monotonic in raid strength and that the override is applied.
4. **Launch route.** One entry in the Marine Ops dialog on a player-owned
   market while a threat is live, routing through `MarineOpsContext` to the
   briefing the way `StationingResponseLaunch` does.
5. **Resolution.** `PolityDefenceResolution` consumes the outcome: a win ends
   the raid through the handle and writes the record; a loss writes the
   record only; exactly-once under repeated resolution.
6. **Acceptance instrument.** A debug command that spawns a fleet-group raid
   and a pirate-style raid intel against the player's colony with a short prep
   and travel, in the shape `debug-political-contract-completion.md` uses for
   chains.
7. **Live acceptance.** Fight one raid to a win and confirm the fleets turn
   for home and the pirate respite lands; lose one and confirm the raid
   resolves exactly as vanilla would have; leave a posting to face one alone
   and read the settlement. Measure the window between a fleet's arrival in
   orbit and its raid, since the fought offer's usefulness depends on it.

## Acceptance

- A player-owned market under a live raid of either shape offers the defence
  from the Marine Ops dialog; the same market with no live raid, or one
  already raided, does not.
- A won defence ends the raid through vanilla's own path, exactly once.
- A lost defence changes nothing in vanilla.
- A posting binds its detachment, contributes to the stat, arms on a raid,
  settles from vanilla's result when unanswered, and produces no Standing or
  payout fact at any point.
- The operation's tier rises with the raid's ground strength and its map is
  generated from the colony's own market.

## Out of scope

- MRB scrutiny of a company that owns worlds (`meta-progression.md`).
- Chronicle or comms-officer presentation beyond the ordinary mission outcome.
- Infrastructure on the polity; a posting is personnel, not a building.

## Open questions

- One raid may target several markets in a system. Ending it at one market
  frees the others; ending only when the fought market was its last unraided
  target is more exact and less legible. Decide from the live pass.
- Whether a posting should cost upkeep beyond the company's ordinary wages, so
  that Reach spent on the polity is felt in Pressure. Leaning: not until scale
  inefficiency exists to put it in.
