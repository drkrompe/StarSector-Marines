# Reinforcement

Status: ACTIVE — side-owned requests separate trigger, supply, means, delivery hint, objective, and ticket authority; the installed ladder is defender-side, with marine dispatch, force scale, player readout, and atomic delivery outcomes as extension paths.

Written: 2026-08-23

Updated: 2026-08-24 — retained durable request/delivery boundaries and named the target-faction roster extension.

## Vocabulary

- A **reinforcement request** is a side-owned statement that a position needs more force. It carries a reason, intended scale, a delivery hint, and optionally a tactical objective. It is not itself a spawn or a delivery order.
- A **trigger** decides when to post a request. It owns the local condition that makes the request meaningful; it does not choose a vehicle, aircraft, or entry point.
- A **means** is a delivery capability. It decides whether it can serve the request on this battle and, when selected, creates the ordinary battle-native actors that perform the delivery.
- The **delivery hint** is where a means should look for a viable landing or entry area. The **objective** is where the delivered squad belongs tactically. Keeping them separate lets a squad arrive safely behind the front and then re-man the contested position.
- A **supply gate** is the defender-held compound capability required by a means: BARRACKS supplies walk-ins, ARMORY supplies convoys, and COMMAND_POST supplies shuttle drops. A contested compound still supplies; a marine-held compound does not.
- A **reinforcement ticket** is one unit of the `REINFORCEMENT` resource required for an ordinary dispatch. ARMORYs produce tickets while their side holds them. Tickets measure continuing field capacity, not a mission-scripted wave count.
- A **recapture target** is a once-manned defender tactical position that has become open while its biome slice remains contested. It is a Conquest-specific answer to *where should the next defender response go*, not a general replacement for all reinforcement triggers.
- A **counterattack** is a bounded defender reserve commitment. It earmarks tickets before the wave, telegraphs its intent, and then attempts a short burst of prepaid requests against a conceded biome slice. The earmark is a wager: delivery failure does not silently restore it after launch.
- A **faction unit roster** maps a side's bulk infantry, elite infantry, and optional mech tier to concrete unit types. It owns thematic unit selection; a means owns delivery.

## Ownership and flow

Battle setup registers the applicable triggers and a priority-ordered means ladder. The reinforcement service owns that registry and the pending request queue; the reinforcement system polls triggers on its slow cadence, drains requests in FIFO order, and asks the means in order until one can fulfill a request.

Before ordinary dispatch, the system spends one reinforcement ticket from the requesting side. A request that cannot yet pay remains pending for a later cadence. A request with no feasible means refunds its ordinary ticket and is dropped as a map/supply diagnostic. Prepaid counterattack requests are different: their reserve was paid at muster, so dispatch neither spends again nor refunds an undeliverable launched request.

The selected means creates normal battle actors rather than a reinforcement-specific simulation path. A convoy follows the vehicle/delivery model owned by `convoy-nouns.md`; a shuttle follows the air transport model owned by `air-nouns.md`; a walk-in creates an ordinary infantry squad. Once delivered, the squad enters the normal roster, commander, and tactical-assignment flow.

For Conquest, recapture-target recomputation runs before reinforcement dispatch. The frontline trigger chooses the defender-rear-most contested biome slice with an open target, rotates through that slice's targets, gives the request a rear-shifted delivery hint, and assigns the target as its objective. Marking a target dispatched prevents duplicate waves while an answer is in flight; an assignment or delivery failure eventually re-opens it rather than permanently suppressing the position.

Zone loss remains a parallel trigger: when marines take a previously defender-held objective, it posts a defender request that uses the lost zone as both delivery hint and tactical objective. This fallback operates alongside either the Conquest frontline trigger or the non-Conquest garrison trigger.

The installed means ladder is defender-only: convoy, then shuttle, then walk-in. It is ordered for readable, capability-shaped delivery, not because a request is intrinsically a truck or a shuttle. Each means rejects a request once its corresponding defender supply chain is gone, so captures visibly degrade the available response from mechanized delivery to air delivery to infantry, then exhaust it.

## Standing laws

1. Why reinforcement is needed and how it arrives are separate authorities. A trigger posts intent; a means proves and performs delivery.
2. Delivery location and tactical objective are separate. A safe entry point must never be mistaken for the position the squad is meant to defend or retake.
3. Means are selected by feasibility and priority, not by request reason. Reasons preserve gameplay meaning and reporting context; they do not become a hidden behavior switch.
4. A means must create ordinary battle actors that use their owning domain's lifecycle. Reinforcement orchestration never becomes a parallel unit, air, vehicle, or commander model.
5. Supply gates live at means feasibility. Triggers may avoid obvious noise, but they must not duplicate the authoritative question of whether a delivery capability remains.
6. Ordinary dispatch is paid before commitment and refunds only when no means can deliver. A prepaid counterattack is an intentional exception: its reserve is committed at muster and remains at risk after launch.
7. A target may not stay suppressed merely because a delivery pipeline failed. Dispatch state is provisional until an assigned live squad closes the loop, with a bounded recovery path for lost deliveries.
8. Faction identity is data on every request, but symmetric behavior is not implied by the type. The installed ladder is defender-side; marine-side triggers, supply interpretation, and means eligibility require explicit authority.
9. The faction roster chooses unit tier and visual/stat identity. It does not decide force quantity, delivery feasibility, or the player's campaign roster.

## Boundaries and extension paths

`conquest-nouns.md` owns compound capture, ownership state, and the conquest win condition. Reinforcement reads compound availability as supply; it does not own capture progression. `convoy-nouns.md` owns vehicle routing, movement, and deboarding. `air-nouns.md` owns shuttle bodies and sorties. The commander/AI layer owns what a delivered squad does after its objective assignment, while reinforcement only supplies the initial tactical intent.

The existing request shape is deliberately wider than the current delivery set. Marine-side reinforcement can reuse it only with explicit marine-held supply, legitimate marine triggers, and a policy for player authorization and readout. Scripted mission timings and commander-initiated requests are likewise trigger extensions, not new delivery systems.

Request strength currently expresses desired scale, not a complete force, cost, and pacing contract. The installed ladder therefore uses one small, single-response baseline. Multi-squad, multi-vehicle, and multi-shuttle scale must be introduced as one coordinated contract rather than letting each means drift independently. A player-side ticket/inbound readout must project the same resource and in-flight reality that the simulation uses rather than invent a UI budget.

The current faction roster is keyed by battle side and therefore gives every
defender the same thematic unit identity. `target-faction-ground-rosters.md`
owns the extension that resolves one target-faction ground roster at launch and
reuses it for initial defenders, garrisons, convoys, shuttles, and walk-ins.
That roster may choose equipment and unit identity; it still may not choose
force quantity, delivery, mission command, or player-owned personnel.
