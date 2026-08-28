# Shoulder micro-missile pod

> The second first system. The breacher assist proves a suit can change how its
> wearer moves; this proves a suit can put something downrange on its own.

Status: PLANNED — blocked on a micro-missile delivery mechanism in the weapon
catalog.
Written: 2026-08-28

Read `progression-nouns.md`, `integral-armor-systems.md`,
`moddable-weapons-nouns.md`, `mechs-nouns.md`, and `equipment-lore-catalog.md`
before implementing.

## Problem

`integral-armor-systems.md` named two first systems because they exercise
opposite halves of the model, and only one of them shipped. The consequences of
having built just the movement half are concrete:

- **The ammunition resource mode has no carrier.** `SpecialResourceMode` carries
  both AMMUNITION and COOLDOWN, the parser validates both, the component stores
  an ammunition count, and every shipped system is cooldown-gated. A validated
  path with no live user is a path that quietly rots.
- **Nothing delivers a payload from a non-hand mount.** Every shipped infantry
  payload comes off a weapon in a marine's hands or a carried special item. A
  suit that shoots on its own is a different shape and the design should find
  out where it strains before authoring four of them.
- **The weapon catalog has no micro-missile.** There is a rocket launcher as a
  carried special and nothing smaller. The delivery mechanism the story assumed
  was already anticipated does not in fact exist.

## Goal

A micro-missile delivery mechanism in the weapon catalog, and a shoulder pod
integral system that fires a small salvo of them from finite onboard ammunition.

## Standing rules

- **Infantry authority, not a mech mount.** A shoulder pod on a battlesuit is
  still a one-person infantry billet using infantry cover, pathing, targeting,
  and casualty rules. The visual similarity to a mech missile rack is not a
  licence to reach for chassis, mounts, lances, or support delivery — those stay
  with `mechs-nouns.md`. This is the rule most at risk here and the one to state
  in review.
- **It does not spend the carried special item.** A marine with a pod still has
  their grenades. This is the same rule the breacher assist follows and it
  matters more here, because a pod is the first system that will look to a
  player like a second special item.
- **Ordinary friendly-fire discipline.** The explosive specials already have
  authored collateral behavior and a pod does not get an exemption for being
  part of the suit.
- **Finite, and it stays finite.** No mid-battle resupply. The ammunition mode
  exists to make a capability something the player spends rather than cycles.

## Lore

`equipment-lore-catalog.md` puts micro-missile support in Hegemony, League, and
Tri-Tachyon traditions, which is three plausible homes and no obvious single
one. The pattern that carries the first pod should be chosen for what the
tradition says about *how* it fires — a Hegemony shock pattern's salvo and a
Tri-Tachyon predictive one are different weapons wearing the same name — rather
than for filling a faction grid slot. That choice belongs in
`integral-system-slate.md`.

## Scope

- A micro-missile delivery mechanism authored through the ordinary weapon
  catalog, with its own flight, contrail, and impact treatment rather than a
  generic projectile substitution.
- A pod effect on `IntegralSystemDef`, ammunition-gated, validated at parse time
  against its resource mode the way the breacher assist is validated against
  cooldown.
- Firing from the suit rather than from the marine's hands: origin, arc, and
  whatever targeting authority a self-firing mount needs.
- The Armory copy path, which already handles ammunition-gated systems in its
  clock and has never had one to render.
- An authored use policy, following `integral-system-use-policy.md`.

## Out of scope

- Guided or seeking behavior beyond what the shipped ballistics model expresses.
- Any mech mount, rack, or lance.
- Resupply, reload, or between-mission ammunition economy.

## Acceptance

- A pod-carrying pattern issues, deploys, fires its salvo, runs dry, and stays
  dry for the rest of the battle.
- The billet's carried special item is unaffected throughout.
- Collateral behaves as the shipped explosive specials do, with friendly-fire
  damage recorded as its own quantity and not netted away.
- The Armory shows the remaining uses before issue, through the ammunition path
  that already exists.
- A marine carrying a pod is still resolved as infantry everywhere it matters:
  cover, pathing, casualty, and telemetry.

## Open questions

- Whether the pod fires at the wearer's current target or picks its own. Picking
  its own is more interesting and is also how a capability starts quietly
  becoming a second soldier.
