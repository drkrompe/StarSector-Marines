# Integral armor systems

> Late equipment should get *fun*, not thick. A breacher that boosts through a
> doorway behind a shield is a different soldier; a breacher with more hit
> points is the same soldier taking longer to kill.

Status: PLANNED — direction agreed, both open questions decided, no implementation.
Written: 2026-08-27
Updated: 2026-08-27 — systems are per pattern; the breacher shield is mitigation and the room reacts.

Read `progression-nouns.md`, `combat-durability-nouns.md`, `mechs-nouns.md`, and
`equipment-lore-catalog.md` before implementing. Coordinates with
`powered-assault-armor-roles.md`, which owns armor roles and provenance.

## Problem

An armor pattern is four passive scalars: capacity, rating, move speed, incoming
accuracy. That is enough to express a tradeoff — the shipped catalog genuinely
does, with tier-1 fatigues faster and harder to hit than the tier-4 walking tank
that pays a fifth of its speed for plate — but it is not enough to make a
late-game suit *interesting*. Every lever it has is a number that makes a
firefight last longer.

The measured arc does not need help. An endgame marine kills an opening one in
1.30s while the reverse takes 19.04s, and only about 2.6x of that ~15x asymmetry
comes from target armor; the rest is shooter capability. So there is no gap that
more protection should close, and widening capacity would buy the arc nothing
except the bullet-sponge feel the design is trying to avoid.

The setting already supplies the better answer. Faction lore carries
micro-missile support across Hegemony, League, and Tri-Tachyon traditions, the
weapon model already anticipates micro-missile as a delivery mechanism, and the
battle compositor already renders shoulder pods.

## Goal

An armor pattern may declare **one integral system**: an authored capability the
suit itself carries and can use in battle. Late-game suits become distinct
because of what they can *do*.

It is declared **per pattern**, not per role. A role says what job a suit is for;
which concrete suit you recovered is what should surprise you. Per pattern also
matches how provenance already works, and it lets two heavy suits from different
traditions feel unlike each other without inventing a fourth role. Most patterns
declare none — a system is a reason to want a particular suit, and everything
having one would flatten that back out.

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

- **Breacher assist** (ASSAULT patterns) — a short movement boost paired with a
  brief directional shield, on a cooldown. Answers "how does a heavy suit get
  through the doorway it exists to get through" without touching capacity. The
  shield is **mitigation**, owned by `combat-durability-nouns.md` as bounded
  directional resistance with an explicit duration — not a second damage path,
  and not extra capacity wearing a costume.
- **Shoulder micro-missile pod** — a small salvo with finite ammunition,
  authored through the existing weapon catalog as a micro-missile delivery
  mechanism. Exercises payload delivery from a non-hand mount and the
  friendly-fire discipline already applied to other explosive specials.

## The room reacts

**The breacher is seen, and the defenders fight it.** The alternative — a suit
that effectively goes invisible while the room fails to respond — was considered
and rejected.

Three reasons, in order of weight:

1. **It is the wrong fantasy.** A breacher announces itself. The door comes in
   and the thing that walks through is not stopping; that is the whole appeal.
   Not-being-seen is the scout's fantasy, and the scout pattern already owns it
   through evasion rather than through a perception trick.
2. **A non-reacting room reads as broken.** Defenders who notice nothing are
   indistinguishable from defenders whose AI failed. The player cannot tell
   cleverness from a bug, so the moment lands as suspicion rather than triumph.
3. **It keeps the fight lethal and shared.** A timed shield is a clock, not an
   off-switch: the breacher is still being shot, and someone has to suppress the
   room while the clock runs. That makes the entry a squad moment instead of a
   solo trick, and it preserves the standing preference for scored, self-releasing
   commitments over sticky binary gates.

Concealment is not lost by this, only relocated: `powered-assault-armor-roles.md`
already flags it as needing an explicit shared perception contract, and it
belongs to the light/recon role when that contract exists.

## Out of scope

- New armor tiers or any change to the capacity/rating curve.
- Concealment and perception, which the recon role owns.
- Mech mounts, lances, and support delivery.
- Reworking the carried special-equipment slot.
- Defender adoption, which follows once the player-side model is proven.

## Acceptance

- An armor pattern with an integral system issues, persists, and materializes
  through the ordinary Armory transaction, and still leaves the billet's carried
  special item free.
- The breacher's temporary protection has an explicit duration and expiry, and
  is visible in telemetry as its own mitigation rather than as inflated capacity.
- The measured arc asymmetry does not grow through armor capacity: the
  `TtkReportTest` band-versus-grade and arc bounds still hold, and the armor
  contribution to the arc is reported alongside them.
- A suit's integral system is legible in the Armory before issue, per the
  standing visibility law — a player must be able to see what a suit does.

## Acceptance, continued

- Defenders in the breached room register and engage the breacher on the
  ordinary threat path. No code path suppresses their reaction while a shield
  is up.

## Open questions

- Whether a pattern's integral system should be visible on defender formations
  before contact, or only once used. Reading a hostile suit's capability at a
  distance is powerful; discovering it the hard way is memorable. Defender
  adoption is out of scope here, so this can wait for it.
