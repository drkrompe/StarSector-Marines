# Firing-platform integral systems

> Assault suits cross the room. Nothing in the catalog is built to hold one.

Status: PLANNED — needs a timed stance with a real movement cost.
Written: 2026-08-28
Updated: 2026-08-28 — reallocated from the XIV and Lion's Mantle to the line role, after the assault heavies took the breach family.

Read `progression-nouns.md`, `integral-system-slate.md`, and
`integral-armor-systems.md` before implementing. Depends on
`integral-system-use-policy.md`.

## Problem

Every assault pattern now carries a breaching assist, and the whole family is
about *getting somewhere*: a window of speed behind a screen, spent crossing
ground that is expensive to cross. That is one half of how infantry armour
earns its keep and the catalog has no expression of the other half.

Two line traditions are authored around standing still and shooting, and neither
can express it. The Hegemony's Legionary is standardized, pressure-sealed, and
built for campaign repair — a suit for being somewhere a long time. The Diktat's
Furnace line pairs a deep pool with ordinary resistance, real weight, and a
conspicuous profile, which describes a suit that expects to be shot at while it
works. Both currently differ from their peers only in the four scalars.

The split that falls out of this is worth stating because it reads straight off
the role name: **assault crosses, line holds.** The earlier plan put bracing on
the XIV, which was defensible when the XIV had nothing else, and is now
redundant — it would give one pattern both halves and leave the line role with
neither.

## Goal

A suit whose identity is standing and shooting can commit to doing so: a timed
stance that materially improves the wearer's fire and materially costs their
mobility while it runs.

## Pick one pattern, not both

Two line suits with near-identical braces would be the palette swap the catalog
exists to prevent, so this story authors a brace on **one** pattern and leaves
the other alone — not because a slot is scarce, but because the second one
would have nothing of its own to say.

The Legionary is the better fit for bracing as hardware — it is the pattern with
standardized fittings and a repair culture, and a brace is a mechanism that gets
serviced. The Furnace line is the better fit for the *fantasy* — a Sindrian state
suit planting itself in a doorway and refusing to move is a stronger image, and
its conspicuousness gives the stance an authored drawback the Legionary lacks.
Deciding between them belongs in this story, on the strength of what the shipped
infantry combat stats can actually express, not in the slate.

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
  look like one. This is the honest counterweight, and the Furnace line's lore
  asks for it: a conspicuous target profile is already part of what that suit is.

## Standing rules

- **Never durability.** A brace must not add capacity, rating, or hit points,
  and must not become mitigation by another name — mitigation is
  `combat-durability-nouns.md`'s concept and a stance that quietly duplicates it
  is the forbidden thing wearing a third costume.
- **Accuracy is a legitimate timed effect; evasion is delicate.** Improving what
  the wearer hits is behavior. Reducing what hits the wearer is close enough to
  durability that it needs a specific argument, and probably belongs to
  mitigation instead.
- **It must not read as a second breaching assist.** The breach family already
  owns timed movement. A stance that mostly makes you faster afterwards, or that
  is really a repositioning tool, is a seventh member of the wrong family.
  Committing to a spot is the entire distinction.

## Scope

- A stance effect on `IntegralSystemDef`, parse-validated, with the movement cost
  authored rather than implied.
- Movement composed through the same untouched-base recomputation the breacher
  assist uses, so a stance and a boost cannot compound or leave a remainder.
- Whichever of accuracy or weapon sustain the chosen pattern actually needs, decided
  in this story from what the shipped infantry combat stats can express.
- An authored use policy for "hold this position and shoot", which is the second
  policy `integral-system-use-policy.md` needs to prove its dispatch.
- Presentation, following `integral-system-battle-presentation.md`. A braced
  marine that looks identical to a walking one hides the whole trade.

## Out of scope

- Suppression, morale, and area-denial mechanics.
- Any change to the shipped armour scalars of either pattern, and any change to
  the breach family the assault heavies now carry.
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
- Whether a braced marine should be mechanically easier to hit, or only look it.
  Mechanical is more honest to the Furnace line's lore and is a threat-weighting
  change with reach well beyond this story.
