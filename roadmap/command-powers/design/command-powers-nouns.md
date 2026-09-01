# Command Powers

Status: ACTIVE — fleet-sourced availability, pre-battle commitment, and simulation-owned activation form the standing command-power model.

Written: 2026-08-23

Updated: 2026-09-01 — the compact battle tray identifies its campaign-backed
Supplies pool with the base-game commodity icon resolved from the live spec.

## Vocabulary

- A **command power** is a player-invoked battlefield ability with an immutable identity, targeting contract, pacing, and at-use costs. It is not an AI order or a passive fleet modifier.
- **Availability** is the roster of powers this mission could bring. It answers *which tools exist*.
- **Capacity** is the command deck's limit on how much of that available roster may be brought. It answers *how many tools fit*; it must not unlock powers by itself.
- A **command deck** is the slotted subset of available powers, selected before launch and revalidated at the launch boundary.
- A **detachment** is the frozen, battle-facing support contribution resolved at mission acceptance. It combines player commitments with employer offerings without carrying campaign fleet objects into the simulation.
- A **power source** is either a committed player fleet member whose hull or fitting contributes a capability, or an employer offer recorded on the mission. Several sources may contribute the same power; the battle roster is deduplicated by power identity.
- **Commitment** is the player's pre-battle decision to bring a support asset. Transport, fighter-cover, and power-source membership are all detachment inputs, even when their battlefield contributions differ.
- **Targeting** is a view-layer interaction state. **Activation** is the simulation request-and-commit lifecycle: a targeted request is queued, validated, paid for, resolved, and then paced by cooldown and charges.

## Ownership and flow

The campaign fleet and mission own source facts. The power catalog interprets committed fleet members and employer-offered identifiers into an available, deduplicated roster. The briefing owns temporary commitment and deck-selection interaction; it passes the selected sources and power identifiers to the mission-launch boundary.

Mission launch resolves that input once into a detachment, filters it through the command deck, and injects only the resulting capability lists into battle. The battle therefore receives powers, shuttle delivery, and fighter cover as battle-native support rather than as a live campaign fleet dependency.

In battle, the command-power service owns command points, cooldowns, charges, pending activations, and transient power effects. The UI may arm a targeting mode and enqueue a request, but only the simulation commits it after checking roster membership, affordability, charges, cooldown, and target validity. A committed power spends its current at-use resources, begins pacing, then enacts its effect. Recon's temporary reveal is simulation state projected through the existing fog presentation seam; it is not a separate fog authority.

The in-battle roster is presented as one bounded bottom-center MLX tray, not a
free-growing menu. A compact command-resource block and at most the five
budgeted power cards expose ready, cooldown, affordability, supply, charge, and
spent states. Arming a card adds only a narrow targeting instruction strip.
The resource block's Supplies icon is presentation metadata from Starsector's
commodity registry; the command-power resource adapter remains the authority for
the displayed quantity and any activation spend.
The retained tray owns card hierarchy and selection input; a separate
world-layer targeting panel owns the cursor reticle, target validity, RMB
cancellation, and the next map click. That separation keeps targeting a
view-layer state and preserves the simulation-owned activation boundary.
The world preview draws the power's declared footprint as a translucent field,
brackets the exact snapped cell, and labels the placement `VALID` or `BLOCKED`.
An invalid click remains armed and never spends resources; the visual result is
still downstream of the power's own target predicate and radius contract.

The current catalog demonstrates five distinct expressions of the same contract: recon, mech support, emergency resupply, orbital barrage, and marine insertion. A capability may become a direct battlefield power or scale one; ship-only survival flavor does neither and is not a command-power source.

## Briefing and commitment contract

The full-canvas briefing is the single canonical pre-battle commitment surface.
The mission dossier remains a read-only summary that hands the selected mission
to briefing; it does not maintain a second detachment editor or launch a battle
directly. Briefing owns salvage negotiation, captain selection, player support
commitments, employer support presentation, command-deck selection, and the
final launch decision.

Player and employer contributions remain visually and semantically distinct.
Each eligible player transport, fighter carrier, and power-source member may be
committed independently. Eligible fighter carriers begin committed so the
no-edit path preserves ordinary support, while withholding one removes only
that carrier's wings. Employer shuttles, fighter cover, and offered powers are
read-only contract contributions. The launch boundary combines those sources
only after the player has made their choices.

The command deck currently has a fixed budget and defaults to a stable
catalog-order fill that fits. Slot weights and at-use costs are shown as
different constraints: deck weight limits what is brought, while command
points, supplies, cooldowns, and charges govern what can be fired during the
battle. Launch filters the selected identifiers against the freshly resolved
available roster and budget, so stale or tampered selections cannot bypass
commitment.

## Current power families

- **Recon Ping** projects a temporary circular vision source through the
  existing fog seam. Sensor/survey fittings and the Apogee are player sources;
  an employer may offer the same capability.
- **Mech Support** calls a shootable heavy transport that physically unloads a
  mech lance through the ordinary air-delivery and landed-roster paths.
  Valkyries and ground-support fittings are production sources. The production
  power remains Bulwark-only; mixed specialist lances are explicitly debug
  iteration support.
- **Emergency Resupply** calls a vulnerable utility shuttle that leaves a
  finite persistent supply cache. Tarsus and Atlas hulls are its player sources.
- **Orbital Barrage** targets a zone, presents a warning, then resolves a heavy
  delayed blast with intentional friendly fire and structural interaction. It
  consumes supplies when fired and is limited per battle. Onslaught, Invictus,
  and ground-support fittings are player sources; the warning window is the
  extension seam for future counter-battery play.
- **Marine Insertion** sends a physical manned Valkyrie from the nearest map
  edge to a safe scored landing zone near the requested cell, then unloads a
  full infantry squad into the ordinary roster and commander flow. It is
  Valkyrie-only; selectable landing geography, air-defense pressure, and
  alternate craft classes are deferred shared-delivery concerns rather than
  properties of this power.

## Laws

1. Fleet acquisition determines availability; command progression determines capacity. Do not turn a larger deck, CP pool, or cooldown curve into an alternate unlock path.
2. Production powers come only from the committed player sources and the mission's employer offer. Debug grants are not a production authority.
3. Commitment precedes slotting. Holding back a source must remove its contributions before the deck is selected, and one source may contribute more than one capability.
4. A deck can slot only available powers and must satisfy its budget at selection and again at launch. Unknown, stale, or over-budget selections never enter the battle roster.
5. The battle's power roster is frozen at launch and contains no campaign fleet objects. Battle code must not rescan or mutate the campaign fleet to discover a power.
6. UI targeting never spends or resolves a power directly. The simulation owns target validation, payment, cooldown/charge updates, and effect resolution in one activation lifecycle.
7. Current in-battle pacing is command points plus per-power cooldowns and charges. Current campaign consumption is supplies paid at activation where a power declares it; slotting itself is not a second resource charge.
8. A fleet capability must pass the projection lens: it either becomes a plausible ground-op ability or materially scales one. Hull-survival flavor alone is not sufficient.
9. Committed physical support and its possible risk are distinct from the power abstraction. Crew loss, CR attrition, asset retirement, air-defense contesting, and catastrophic loss require their own delivered battlefield/campaign bridge; they are not implied by selecting a card today.

## Boundaries and extension points

`ship-hullmod-survey.md` is the enduring flavor and projection evidence for future source mappings; it is not the runtime catalog or a promise to implement every candidate. The catalog remains the executable source-to-capability authority.

Command progression may change deck capacity, command-point pacing, and
cooldown curves, but it never creates fleet-derived availability. Future
reach geography may add named landing zones, air-defense contesting, and
craft-specific delivery risk on the shared insertion/delivery seam.

Map-authored orbital-defense state may constrain a power at target-validation
time without removing its frozen source or deck identity. A local shield may
deny only covered barrage targets, while a surface-to-orbit battery may hold
new barrage commitments at standoff until ground forces neutralize its
fire-control site. `shielded-fire-support-zones.md` and
`orbital-battery-fire-support-lock.md` own those distinct extensions; neither
may spend resources on a denied request or invent campaign fleet damage.

A forward operating base, if introduced, is a territory- and spoils-gated
reach object. It may anchor delivery but is neither a command power nor a
second campaign authority.

Command powers complement the AI commander rather than replacing it. They reuse the battle's fog, combat, air, reinforcement, and campaign-resource seams while leaving those systems authoritative for their own state.
