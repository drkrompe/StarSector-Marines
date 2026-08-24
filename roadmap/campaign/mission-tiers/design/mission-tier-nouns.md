# Mission tier nouns

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-08-24 — separated mission lift expression from defender intensity and made Conquest an authored siege rather than an attacker-balanced encounter.

Mission difficulty is described by two independent axes: **operation tier**
states how large and consequential the work is, while **risk** states how far
that work may deviate from its expected pressure. A mission is the combination
of its type, tier, and risk; no one axis may silently stand in for the other.

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
chooses or can afford to field. Its elevated defender weight also does not
silently raise required lift; those are independent mission expressions.

## Offer, reward, and compatibility boundaries

Production offer policy must choose a tier from campaign authority such as the
patron, target, and player standing, then select risk as the uncertainty within
that scale. Reward value must likewise acknowledge tier so that a larger
operation does not pay like a smaller one with the same risk.

Some legacy factories and headless entry points only know risk. Their
risk-to-tier default is a temporary compatibility boundary, never an authority
for production campaign scale. Authored story and event missions may state a
specific tier directly. `tiered-production-offers.md`, `tiered-rewards.md`,
and `retire-risk-tier-bridges.md` close the remaining authority gaps.

## Validation

Tier curves are balance hypotheses, not a promise that every rung is correct.
The force-ratio play surface compares a chosen tier, risk, demanded lift, and
the force actually fielded. `tier-force-ratio-acceptance.md` is the live
acceptance boundary; it may tune values without collapsing tier back into risk.

Mission tiers own scale vocabulary and contracts own offer eligibility and
terms; battle setup owns how the authored mission becomes a battle; loot owns
the settled recovery decision. Completed establishment of the split is recorded
in `shipped.md`.
