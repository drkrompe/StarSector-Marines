# Reinforcement

Status: ACTIVE — side-owned requests separate trigger, supply, means, delivery hint, objective, and ticket authority; the installed ladder is defender-side, with marine dispatch, force scale, and player readout as extension paths.

Written: 2026-08-23

Updated: 2026-08-28 — a delivered squad belongs to the authority that asked for it, whichever means carried it.

## Vocabulary

- A **reinforcement request** is a side-owned statement that a position needs more force. It carries a reason, intended scale, a delivery hint, and optionally a tactical objective. It is not itself a spawn or a delivery order.
- A **trigger** decides when to post a request. It owns the local condition that makes the request meaningful; it does not choose a vehicle, aircraft, or entry point.
- A **means** is a delivery capability. It decides whether it can serve the request on this battle and, when selected, creates the ordinary battle-native actors that perform the delivery.
- The **delivery hint** is where a means should look for a viable landing or entry area. Where it is *safe* to arrive is a further question, and only the command layer watching the front can answer it: a **delivery deployment policy** rear-shifts the hint behind the known hostile front, and every means that has to arrive somewhere asks it — a convoy for its entry and drop, a shuttle for its landing zone. Without it an air drop lands on the rally, which on a losing track is exactly where the enemy is. The **objective** is where the delivered squad belongs tactically. Keeping them separate lets a squad arrive safely behind the front and then re-man the contested position.
- A **supply gate** is the defender-held compound capability required by a means: BARRACKS supplies walk-ins, ARMORY supplies convoys, and shuttle drops need both a COMMAND_POST and — on a map that has one — the AIRBASE the aircraft actually fly from. A command post authorises a drop; an airfield is where the lift lives, so taking the field ends air delivery whoever still holds the headquarters. A map with no authored airfield keeps the command post as its only air gate, because a field cannot be a requirement on battles that were never given one. A contested compound still supplies; a marine-held compound does not.
- A **reinforcement ticket** is one unit of the `REINFORCEMENT` resource required for an ordinary dispatch. ARMORYs produce tickets while their side holds them. Tickets measure continuing field capacity, not a mission-scripted wave count.
- A **recapture target** is a once-manned defender tactical position that has become open while its biome slice remains contested. It is a Conquest-specific answer to *where should the next defender response go*, not a general replacement for all reinforcement triggers.
- A **counterattack** is a bounded defender reserve commitment. It earmarks tickets before the wave, telegraphs its intent, and then attempts a short burst of prepaid requests against a conceded biome slice. The earmark is a wager: delivery failure does not silently restore it after launch.
- A **side unit roster** is the small battle-side fallback used by player, story, and legacy payloads when no campaign target exists. It is not campaign-faction doctrine.
- A **ground roster profile** is the immutable, data-authored defender doctrine resolved from `TargetProfile.factionId()` once during battle setup. It chooses bulk/elite compatibility shells, weighted primary families, risk-banded equipment grades, concrete armor patterns, special issue, and optional heavy-support identities. Unknown faction ids resolve to Independent.

## Ownership and flow

Battle setup registers the applicable triggers and a priority-ordered means ladder. The reinforcement service owns that registry and the pending request queue; the reinforcement system polls triggers on its slow cadence, drains requests in FIFO order, and asks the means in order until one can fulfill a request.

Before ordinary dispatch, the system reserves one reinforcement ticket from the requesting side. A request that cannot yet pay remains pending for a later cadence. Each means reports `COMMITTED`, `REJECTED`, or `RETRYABLE`: only a committed actor or squad consumes the ordinary ticket, rejection falls through to the next provider, and retryable state refunds and requeues without trying a lower-priority means. If every means rejects, the ordinary ticket is refunded and the request is dropped as a map/supply diagnostic. Prepaid counterattack requests are different: their reserve was paid at muster, so dispatch neither spends again nor refunds an undeliverable launched request.

A shuttle means with an authored airfield **loads on the ground**: it holds the craft on a hardstand and marches an embarking squad out from the side's own rear edge to board it, so the delivery costs visible people crossing visible ground before it costs anything else. `air-nouns.md` owns that phase. The means still creates ordinary actors on both sides of it — an ordinary infantry squad walking to the field, an ordinary air sortie leaving it.

The crew is sent to **the ramp**, not to the airfield. An apron is a wide place and its tactical node anchors at the middle of one, so a crew given the node arrives on the field several cells from the aircraft — outside the reach the sortie loads from — and stands there until the sortie times out, while the next sortie marches four more out to join them. The destination is therefore the pad cell itself, and it is the same point the air layer measures boarding from, so where the crew was sent and where the crew is taken aboard cannot drift apart.

What they are given is an ordinary hold-this-place task, the kind a garrison gets. Waiting for a lift is not a bespoke behaviour and does not deserve one: a crew on that task walks to the pad, stands in a fire-team footprint that fits inside the boarding reach, breaks off to engage what it can see, and turns toward gunfire it can hear. The sortie contributes a destination; the infantry layer supplies the conduct. This is why an attacker who reaches the field finds ground crew fighting for it rather than passengers queueing.

**A delivered squad belongs to whoever asked for it**, whichever means carried it. The delivery policy names that authority, and every means claims its passengers with the claim the policy mints — with the objective attached when the policy owns one, so the squad lands already tasked with what it was sent for rather than owned but idle. Where there is no such authority to ask, the means keeps the squad itself, which is what an uncommanded battle means. Claiming passengers for the delivery arm instead is a trap worth naming: reinforcement outranks mission command, so a squad delivered that way is visible to the commander and permanently immovable by it — it holds the task it landed with for the rest of the battle no matter what happens elsewhere. Delivery is transport, not ownership.

The selected means creates normal battle actors rather than a reinforcement-specific simulation path. A convoy follows the vehicle/delivery model owned by `convoy-nouns.md`; a shuttle follows the air transport model owned by `air-nouns.md`; a walk-in creates an ordinary infantry squad. Each defender means receives the same battle-frozen ground roster as initial allocation: convoys and walk-ins draw bulk issue, while shuttle drops draw elite issue. Once delivered, the squad enters the normal roster, commander, and tactical-assignment flow.

For Conquest, recapture-target recomputation runs before reinforcement dispatch. The frontline trigger chooses the defender-rear-most contested biome slice with an open target, rotates through that slice's targets, gives the request a rear-shifted delivery hint, and assigns the target as its objective. At convoy dispatch, defender command may refine that hint into a safe band behind its latest known hostile front without changing the objective. The delivered convoy squad is minted under `conquest-defender` with a node hold or lost-zone clear assignment; a shuttle drop is minted the same way, less the lost-zone fallback, since a shuttle resolves its objective to a node or to nothing. Marking a target dispatched prevents duplicate waves while an answer is in flight. Terminal rejection releases that reservation immediately, while a later in-flight or assignment failure retains the bounded timeout recovery path.

Zone loss remains a parallel trigger: when marines take a previously defender-held objective, it posts a defender request that uses the lost zone as both delivery hint and tactical objective. This fallback operates alongside either the Conquest frontline trigger or the non-Conquest garrison trigger.

The installed means ladder is defender-only: convoy, then shuttle, then walk-in. It is ordered for readable, capability-shaped delivery, not because a request is intrinsically a truck or a shuttle. Each means rejects a request once its corresponding defender supply chain is gone, so captures visibly degrade the available response from mechanized delivery to air delivery to infantry, then exhaust it.

## Standing laws

1. Why reinforcement is needed and how it arrives are separate authorities. A trigger posts intent; a means proves and performs delivery.
2. Delivery location and tactical objective are separate. A safe entry point must never be mistaken for the position the squad is meant to defend or retake.
3. Means are selected by feasibility and priority, not by request reason. Reasons preserve gameplay meaning and reporting context; they do not become a hidden behavior switch.
4. A means must create ordinary battle actors that use their owning domain's lifecycle. Reinforcement orchestration never becomes a parallel unit, air, vehicle, or commander model.
5. Supply gates live at means feasibility. Triggers may avoid obvious noise, but they must not duplicate the authoritative question of whether a delivery capability remains.
6. Ordinary dispatch reserves payment before the attempt and consumes it only on `COMMITTED`; rejected providers fall through, retryable attempts requeue, and total rejection refunds. A prepaid counterattack is an intentional exception: its reserve is committed at muster and remains at risk after launch.
7. A target may not stay suppressed merely because a delivery pipeline failed. Dispatch state is provisional until an assigned live squad closes the loop, with a bounded recovery path for lost deliveries.
8. Faction identity is data on every request, but symmetric behavior is not implied by the type. The installed ladder is defender-side; marine-side triggers, supply interpretation, and means eligibility require explicit authority.
9. The frozen ground roster chooses defender unit tier and equipment/protection identity. It does not decide force quantity, support eligibility, delivery feasibility, AI, objectives, or the player's campaign roster.

## Boundaries and extension paths

`conquest-nouns.md` owns compound capture, ownership state, and the conquest win condition. Reinforcement reads compound availability as supply; it does not own capture progression. `convoy-nouns.md` owns vehicle routing, movement, and deboarding. `air-nouns.md` owns shuttle bodies and sorties. The commander/AI layer owns what a delivered squad does after its objective assignment, while reinforcement only supplies the initial tactical intent.

The existing request shape is deliberately wider than the current delivery set. Marine-side reinforcement can reuse it only with explicit marine-held supply, legitimate marine triggers, and a policy for player authorization and readout. Scripted mission timings and commander-initiated requests are likewise trigger extensions, not new delivery systems.

Request strength currently expresses desired scale, not a complete force, cost, and pacing contract. The installed ladder therefore uses one small, single-response baseline. Multi-squad, multi-vehicle, and multi-shuttle scale must be introduced as one coordinated contract rather than letting each means drift independently. A player-side ticket/inbound readout must project the same resource and in-flight reality that the simulation uses rather than invent a UI budget.

`GroundRosterRegistry` loads the built-in JSON catalog after weapon and special
equipment catalogs, validates every referenced primary, special, armor,
infantry shell, and mech identity, and fails startup on malformed built-in
content. `BattleSetup` resolves exactly one profile from the target faction id,
stores it on the simulation, and passes it to initial allocation, heavy-support
selection, convoys, shuttles, and walk-ins. Armor ids describe semantic roles
such as scout, combat, line, and heavy; current palette-named enum handles and
art are compatibility presentation, not faction truth.

Encounter risk selects a profile's grade, armor, and special-issue eligibility
tables, while mission/tier authority retains force count, elite ratio, heavy
armor admission, support budget, tickets, and means ordering. The roster never
turns faction labels such as corporate, elite, fanatic, or outlaw into damage,
targeting, morale, self-detonation, or equipment-use behavior when the
corresponding item is absent. `target-faction-ground-rosters.md` retains the
remaining content expansion, merged-submod override, deterministic fixture,
and live faction-read acceptance work.
