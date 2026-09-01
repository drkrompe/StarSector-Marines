# Mission tier nouns

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-09-01 — separated total commitment from concurrent field presence for covert operations.

Mission difficulty is described by two independent axes: **operation tier**
states how large and consequential the work is, while **risk** states how far
that work may deviate from its expected pressure. A mission is the combination
of its type, tier, and risk; no one axis may silently stand in for the other.

The force a mission recommends is likewise two different facts. **Committed
force** is every squad assigned to the operation, including reserves.
**Field presence** is the number of organizational squads that may be alive on
the map or inbound at once. Most missions have unrestricted presence. A covert
mission may cap it without pretending the operation is lower-tier or discarding
the player's reserve commitment.

## Operation tier

An **operation tier** is the campaign-scale demand of a mission. The current
ladder is First Contract, Established, Veteran, Reinforced, and Full Strength.
It is deliberately the same vocabulary used to describe the company the player
brings, so a briefing can compare demanded and fielded capability without
turning a colour label into a force estimate.

Tier owns the base spatial and force commitment: map scale, expected lift,
base defender count, and the mission's place in the campaign arc. A mission
type may set a tier floor; a type below that floor is not a valid offer. This
keeps a conquest a late-game operation while allowing other types to occur at
more than one scale.

Tier establishes the baseline rather than rubber-banding a battle. Once a
mission is made, its map and base infantry force do not grow merely because the
player contributes more lift.

The ordinary-operation recommendation baseline is one, three, six, seventeen,
and thirty-four squads for First Contract through Full Strength. Mission type
may raise that baseline when its tactical shape genuinely demands more:
Reinforced and Full Strength Conquest recommend forty-two and eighty-four squads
respectively. The ordinary recommendation is advisory for generated one-shot
work: such a mission may launch with one ready four-marine fire team and must
report both that hard minimum and its recommendation. Authored story/event/debug
work, stationing, and Conquest retain their authored gates rather than inheriting
this exception.

## Field presence

A **field-presence policy** is mission-owned. It does not belong to mission type
globally: one Sabotage may be a one-squad infiltration while another is an overt
demolition assault, and an Extraction may be either a discreet recovery or a
large civilian evacuation. The current production defaults use one active squad
for Sabotage and two for Raid's target-seizure shape; authored missions may
override either policy.

The limit counts persistent campaign squad identity, not marines, fire teams,
shuttles, or battle-created squad fragments. Every lift carrying members of an
already-admitted squad may complete. A lift carrying a different squad remains
off-map while every slot is occupied. Inbound lifts reserve their squad's slot,
so staggered transports cannot burst through the cap together.

Committed squads beyond the active limit are reserves. A reserve becomes
eligible when an admitted squad has no surviving members and no members still
inbound. A later extraction system may release a squad deliberately through the
same boundary; the current battle lifecycle only releases losses. Replacement
alert and reward consequences are follow-up balance rules, not reasons to leave
the concurrency limit unenforced.

## Risk

**Risk** expresses variance within a tier: force pressure, defender quality,
elite composition, patrol strength, equipment, and rare-recovery eligibility.
It may nudge the tier's base force, but a high-risk operation at one tier must
remain recognizably smaller than the next tier's operation. Risk is not a map
size, company-size, or campaign-progression label.

## Mission type and force composition

**Mission type** provides the tactical shape of a mission: a type-specific
defender weight, a separate lift weight, and its tier floor. Tier answers how
large the operation is; type answers how defender population and transport
demand express that scale. Defender composition then uses risk for qualitative
pressure, such as elite and heavy-support candidates.

For ordinary mission types, the actual attacker force may cap optional
high-impact defender support after the tier baseline is established. This is a
support-eligibility gate, not whole encounter scaling. Conquest is the explicit
exception: it is a late-game authored siege whose infantry population, mechs,
fighter wings, and static weapons are not reduced to match the force the player
chooses or can afford to field. Its three simultaneous lanes and sustained
ferry battle also author a separate assault-force expression: Full Strength is
1,008 named marines delivered as 168 six-seat sorties through six reusable
shuttles. Defender weight does not silently calculate that lift; both values are
accepted together for the mission.

## Offer, reward, and compatibility boundaries

Production contract policy chooses and persists tier when the offer row is
created. Ordinary Tier 1, Tier 2, and Tier 3 patrons author First Contract,
Established, and Veteran work respectively; Planetary Assault has a Reinforced
floor, and stationing has no operation tier. Mission materialization consumes
that stored value, while legacy rows may derive the same mapping from their
patron rank. Risk remains the uncertainty within the stored scale. Reward value
must likewise acknowledge tier so that a larger operation does not pay like a
smaller one with the same risk.

The offer and briefing surfaces expose quantity and quality separately. They
name the operation tier and recommended squads, while the briefing compares
selected ready personnel against the four-person minimum and recommendation and
summarizes their visible issued experience bands against a coarse risk-derived
opposition expectation. Presentation does not promote the recommendation into
a launch gate or hide an understrength choice behind generated filler.

Some legacy factories and headless entry points only know risk. Their
risk-to-tier default is a temporary compatibility boundary, never an authority
for production campaign scale. Authored story and event missions may state a
specific tier directly. `tiered-rewards.md` and
`retire-risk-tier-bridges.md` close the remaining authority gaps.

## Validation

Tier curves are balance hypotheses, not a promise that every rung is correct.
The force-ratio play surface compares a chosen tier, risk, demanded lift, and
the force actually fielded. `tier-force-ratio-acceptance.md` is the live
acceptance boundary; it may tune values without collapsing tier back into risk.

Mission tiers own scale vocabulary and contracts own offer eligibility and
terms; battle setup owns how the authored mission becomes a battle; loot owns
the settled recovery decision. Completed establishment of the split is recorded
in `shipped.md`.
