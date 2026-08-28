# Firing-platform integral systems

> Powered bracing is in the XIV suit's description. What it currently braces
> against is a fifth of the wearer's movement speed, permanently, for nothing.

Status: PLANNED — needs a timed stance with a real movement cost.
Written: 2026-08-28

Read `progression-nouns.md`, `integral-system-slate.md`, and
`integral-armor-systems.md` before implementing. Depends on
`integral-system-use-policy.md`.

## Problem

Two state traditions are authored around holding ground and shooting, and
neither can express it. The Hegemony XIV battlesuit is a Domain-spec breach and
shock pattern with powered bracing; the Sindrian Lion's Mantle is prestige armor
built around oversized cooling and lavish plate support. Both currently pay for
those descriptions in movement speed and receive, in exchange, armour numbers.

That is the shape `progression-nouns.md` warns about: late equipment getting
thicker rather than more interesting. It is also a missed contrast. The shipped
breacher assist is a capability for *crossing* ground. A capability for *holding*
it is the natural opposite, and the two together would make heavy patterns feel
like different answers to the same problem instead of one answer at different
prices.

## Goal

A suit whose identity is standing and shooting can commit to doing so: a timed
stance that materially improves the wearer's fire and materially costs their
mobility while it runs.

## The cost is the design

A stance that only helps is a stat with extra steps. The interesting version is a
trade the player and the AI can both get wrong:

- **Committed.** While braced, the wearer is slow or stopped. Not "slightly
  slower" — slow enough that being braced in the wrong place is a mistake worth
  making.
- **Self-releasing.** The commitment ends on its own clock rather than sticking
  until something clears it. The standing preference here is for scored,
  self-releasing commitments over sticky binary gates, and a stance is exactly
  the kind of thing that becomes a sticky gate if nobody watches.
- **Readable by the enemy.** A braced marine is a stationary target and should
  look like one. This is the honest counterweight, and for the Lion's Mantle the
  lore asks for it explicitly: spectacle is supposed to be a liability.

## Standing rules

- **Never durability.** A brace must not add capacity, rating, or hit points,
  and must not become mitigation by another name — mitigation is
  `d5-timed-directional-mitigation.md`'s concept and a stance that quietly
  duplicates it is the forbidden thing wearing a third costume.
- **Accuracy is a legitimate timed effect; evasion is delicate.** Improving what
  the wearer hits is behavior. Reducing what hits the wearer is close enough to
  durability that it needs a specific argument, and probably belongs to
  mitigation instead.
- **The two patterns should not converge.** Hegemony bracing and Sindrian cooling
  are different ideas — steadier fire versus fire the suit could not otherwise
  sustain — and if they end up as the same effect at different numbers, only one
  of them should exist.

## Scope

- A stance effect on `IntegralSystemDef`, parse-validated, with the movement cost
  authored rather than implied.
- Movement composed through the same untouched-base recomputation the breacher
  assist uses, so a stance and a boost cannot compound or leave a remainder.
- Whichever of accuracy or weapon sustain the two patterns actually need, decided
  in this story from what the shipped infantry combat stats can express.
- An authored use policy for "hold this position and shoot", which is the second
  policy `integral-system-use-policy.md` needs to prove its dispatch.
- Presentation, following `integral-system-battle-presentation.md`. A braced
  marine that looks identical to a walking one hides the whole trade.

## Out of scope

- Suppression, morale, and area-denial mechanics.
- Any change to the shipped armour scalars of either pattern.
- Mech bracing, which is chassis behavior and belongs to `mechs-nouns.md`.
- Cover interaction beyond what a stationary marine already gets.

## Acceptance

- A braced marine measurably shoots better and measurably moves worse, both
  reported, and returns to exactly its issued values on expiry.
- Bracing while a movement system is running composes without compounding, and
  neither effect leaves a remainder when it ends.
- A stance released by its own clock, never by an external condition.
- The TTK harness reports the stance contribution separately, and the shipped
  arc bounds still hold with it live.

## Open questions

- Whether bracing should be interruptible — a braced marine taking heavy fire is
  a good candidate for breaking stance early, and is also the beginning of a
  reaction system that nothing else here has.
- Whether the Lion's Mantle's conspicuousness should be mechanical or purely
  visual. Mechanical is more honest to the lore and is a threat-weighting change
  with reach well beyond this story.
