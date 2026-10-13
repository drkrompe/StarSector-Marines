# Campaign loot nouns

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-10-13 — authored recovery sites keep Relief's first exploration find inside its defended compound.

Campaign loot turns a victorious operation's already-negotiated salvage right
into one visible choice: which recovered items to claim, carry, or fence. It
does not decide the contract's salvage percentage or rewrite campaign cargo
rules.

## Entitlement, recovery, and claim

An **entitlement** is the percentage of recovery value the contract grants the
player. Contract negotiation owns that percentage. Mission resolution freezes
the winning operation's entitlement together with the mission facts and any
recovery modifiers; later UI or fleet changes cannot rewrite that decision.

A **recovery manifest** is the deterministic, immutable pool generated from
those frozen facts. Its total value is the haul that exists; its **selection
budget** is the entitlement's share of that haul. The player claims complete
negotiated stacks within the budget; secured field finds cost no claim budget.
A manifest is a session-local recovery record, not a
new source of truth for mission or cargo state.

Determinism protects the decision: canonical candidate ordering plus the frozen
request means reopening the picker cannot reroll a different haul. Mutable
selection intentionally remains separate from the immutable manifest.

## Optional field caches

A **field cache** is a finite exploration pickup, separate from arbitrary scenery
or employer stock. Battle launch places up to three caches on existing clear,
reachable ground away from landing zones and tactical centers. Placement uses
its own seeded random stream, never carves terrain, and permits fewer caches
when legal sites are scarce. These are optional detours, not objectives or
automatic commander assignments.

A mission may author a resource's recovery area and preferred spot. That find
uses the nearest clear, reachable cell inside its area, overriding generic
landing/objective separation without carving ground or adding another bundle.
An authored area with no valid cell is a setup error, rather than an invitation
to move the find elsewhere. Relief keeps its metals find in the defended inner
yard between the gate and relay so it is available during the assault, before
the finite enemy force is eliminated. Its other material finds retain optional
exploration placement.

An unknown cache remains hidden by the player's actual fog of war. Discovery
leaves a remembered marker. Company infantry must remain within three cells,
with unobstructed walking reach, for five uninterrupted seconds to secure it.
Aircraft, mechs, allies and sight alone cannot collect it. Nearby hostile
combatants with line of fire contest recovery; leaving, losing visibility or
being contested resets progress. Securing is terminal and cannot award twice.

Pickup art uses open recovery skids with amber lifting rails and exposed metal,
machinery or supply bundles. This shared silhouette is reserved for collectible
finds; ordinary storage crates remain scenery. The artwork supplies no cover,
collision or reward authority, and the existing marker reports recovery state.

Current bundles are 40 metals, 8 heavy machinery, and 12 supplies. The battle
owns only identities, quantities and recovery state. Resolution freezes secured
finds into the outcome; victory permits their claim, defeat/withdrawal leaves
them behind, and debug missions never grant campaign cargo. Secured means tagged
for post-victory recovery, not physically carried by a soldier or an extraction
shuttle. These materials are campaign commodities, not battlefield ammunition.

Field finds enter the existing manifest preselected, retaining their real value
and cargo footprint but costing zero contract claim budget. They neither raise
the negotiated budget nor refund budget when deselected. The normal capacity
preview, overflow fencing and exactly-once settlement apply, even when contract
entitlement is zero. The commodity catalog remains the authority for prices,
icons and cargo use. Equipment caches are a future, explicitly gated content
extension; current caches contain only these three materials.

`createSnapshots -Psnapshot=field-salvage` checks placement on the two opening
mission factories and Thunder Run geometry, then records controlled discovery,
collection, contest and secured states plus the real salvage row projection at
1920x1080, 100% scale. This is bounded interaction evidence, not a full campaign
playthrough or economy balance verdict.

## Recovery catalog

The **catalog** adapts currently available vanilla or modded content into
API-free recovery candidates. A candidate declares its kind, identity, value,
quantity range, cargo footprint, presentation data, and relative likelihood;
the roller then selects without replacement. Current categories are standard
commodities, eligible weapons, and context-gated AI cores. The catalog may
flavor probability from the target's faction and industry, but it does not
pretend to be a record of individual battle drops.

Rare recovery remains a catalog concern with explicit mission gates. AI cores
are available only to the eligible strike contexts and risk tiers; ordinary
operations cannot surface them. Blueprints and other special recovery require
their own future authority and gate before joining the catalog.

## Recovery modifiers

A **recovery modifier** expands the manifest's pool before the claim budget is
calculated; it never changes the negotiated entitlement percentage. Captain
expertise can also bias one high-value draw, while player-fleet recovery
hardware contributes a capped fleet bonus. These inputs are resolved at mission
completion and become part of the recovery request, so the displayed haul is
stable even if the fleet changes while Results is open.

## Settlement

A **settlement** is the explicit conversion of selected stacks into fleet
cargo and fence credits. Before confirmation, a capacity preview classifies
each selected stack against its proper cargo, fuel, or personnel bucket. A
stack can split: the fitting quantity is carried and only the overflow is
fenced at the standing discounted value. Selection remains voluntary: choosing
nothing forfeits the unclaimed recovery.

Confirmation is an exactly-once boundary. The campaign adapter applies the
plan to vanilla cargo and credits, records the receipt, and closes the recovery
flow so repeat input cannot mint another claim. Cargo capacity is observed at
preview/confirmation time; the loot system does not own the fleet's capacity.

## Flow and boundaries

1. Contract terms establish entitlement; battle resolution freezes outcome
   facts and recovery modifiers on victory.
2. Recovery generation produces one manifest for the resolved operation.
3. Results summarizes the right to claim; the picker owns only selection.
4. Settlement previews capacity, then transfers/fences exactly once after
   confirmation or closes on forfeit.

`loot-nouns.md` owns these laws. Contract terms belong to the contracts
feature, vanilla cargo remains cargo authority, and presentation belongs to the
Marine Ops screens. The only current implementation follow-up is
`loot-in-game-acceptance.md`; code-complete slices are recorded in
`shipped.md`.

`intact-installation-recovery.md` owns the planned extension that lets frozen
installation outcomes gate site-specific candidates. Intact capture may make
valuable stock, advanced components, or schematics eligible; destruction may
leave only scrap. This never changes negotiated entitlement, grants an
employer-owned asset automatically, or lets loot infer battle state after the
outcome is frozen.
