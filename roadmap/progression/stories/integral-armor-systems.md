# Integral armor systems

> Late equipment should get *fun*, not thick. A breacher that boosts through a
> doorway behind a shield is a different soldier; a breacher with more hit
> points is the same soldier taking longer to kill.

Status: PLANNED — direction agreed, no implementation.
Written: 2026-08-27

Read `progression-nouns.md`, `combat-durability-nouns.md`, `mechs-nouns.md`, and
`equipment-lore-catalog.md` before implementing. Coordinates with
`powered-assault-armor-roles.md`, which owns armor roles and provenance.

## Problem

An armor pattern is four passive scalars: pool, rating, move speed, incoming
accuracy. That is enough to express a tradeoff — the shipped catalog genuinely
does, with tier-1 fatigues faster and harder to hit than the tier-4 walking tank
that pays a fifth of its speed for plate — but it is not enough to make a
late-game suit *interesting*. Every lever it has is a number that makes a
firefight last longer.

The measured arc does not need help. An endgame marine kills an opening one in
1.30s while the reverse takes 19.04s, and only about 2.6x of that ~15x asymmetry
comes from target armor; the rest is shooter capability. So there is no gap that
more protection should close, and widening pool would buy the arc nothing except
the bullet-sponge feel the design is trying to avoid.

The setting already supplies the better answer. Faction lore carries
micro-missile support across Hegemony, League, and Tri-Tachyon traditions, the
weapon model already anticipates micro-missile as a delivery mechanism, and the
battle compositor already renders shoulder pods.

## Goal

An armor pattern may declare **one integral system**: an authored capability the
suit itself carries and can use in battle. Late-game suits become distinct
because of what they can *do*.

## Standing rules

- **Capability, never durability.** An integral system must express itself as
  behavior — movement, protection with a duration, a delivered payload,
  perception. A system whose whole effect is "more effective HP" is the thing
  this story exists to avoid and must be rejected in review.
- **It does not consume the carried special-equipment billet.** A billet still
  carries at most one special item; a suit's integral system is part of the
  suit. A heavy suit must not cost a marine their grenades, or players will
  read the heavy role as a downgrade.
- **Infantry authority, not a mech mount.** A shoulder pod on a battlesuit is
  still a one-person infantry billet using infantry cover, pathing, targeting,
  and casualty rules. `mechs-nouns.md` keeps chassis, mounts, lances, and
  support delivery. The visual similarity is not a licence to cross that line.
- **Reuse the existing activation vocabulary.** Special equipment already has
  authored activation, AI policy, resource mode, and use pose. An integral
  system is a second carrier for that vocabulary, not a second vocabulary.
- **Presentation stays downstream.** A system may drive a pose and a shoulder
  pod, but appearance never becomes simulation input.

## First systems

Two, chosen because they exercise opposite halves of the model:

- **Breacher assist** (ASSAULT role) — a short movement boost paired with a
  brief directional shield, on a cooldown. Answers "how does a heavy suit get
  through the doorway it exists to get through" without touching pool. Needs a
  bounded temporary-protection concept that `combat-durability-nouns.md` has to
  own rather than a second damage path.
- **Shoulder micro-missile pod** — a small salvo with finite ammunition,
  authored through the existing weapon catalog as a micro-missile delivery
  mechanism. Exercises payload delivery from a non-hand mount and the
  friendly-fire discipline already applied to other explosive specials.

## Out of scope

- New armor tiers or any change to the pool/rating curve.
- Mech mounts, lances, and support delivery.
- Reworking the carried special-equipment slot.
- Defender adoption, which follows once the player-side model is proven.

## Acceptance

- An armor pattern with an integral system issues, persists, and materializes
  through the ordinary Armory transaction, and still leaves the billet's carried
  special item free.
- The breacher's temporary protection has an explicit duration and expiry, and
  is visible in telemetry as its own mitigation rather than as inflated pool.
- The measured arc asymmetry does not grow through armor pool: the
  `TtkReportTest` band-versus-grade and arc bounds still hold, and the armor
  contribution to the arc is reported alongside them.
- A suit's integral system is legible in the Armory before issue, per the
  standing visibility law — a player must be able to see what a suit does.

## Open questions

- **Is the integral system a property of the pattern or of the role?** Per
  pattern is more expressive and matches how provenance already works; per role
  is fewer moving parts. Leaning per pattern, with most patterns declaring none.
- **Does a temporary shield belong to durability or to a status model?**
  `combat-durability-nouns.md` owns mitigation, but a timed, directional,
  self-expiring effect is closer to the transient-opacity model smoke already
  uses. Decide before authoring the breacher.
