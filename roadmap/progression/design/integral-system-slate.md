# Integral system slate

Status: ACTIVE — direction for which patterns build a suit capability and what
it should be. Every capability named here is authored and running.

Written: 2026-08-28
Updated: 2026-10-27 — the breach family is three shared shield tiers; the firing brace and the sensor sweeps were cut for the first release; the missile pod left the slate to become the SRM-POD carried special.

Read `progression-nouns.md` for the standing rules an integral system must obey,
`equipment-lore-catalog.md` for each pattern's provenance and deliberate limits,
and `integral-armor-systems.md` for the concept and its remaining scope. The
individual faction guides under `roadmap/factions/design/` carry the
institutional context this doc compresses.

## Purpose

An integral system is provenance made mechanical. It is the sharpest tool the
equipment model has for keeping faction suits from becoming palette swaps —
and the easiest one to ruin, because the failure mode is not a bad system but
too many adequate ones.

This doc exists so that question is answered once, across the whole catalog,
rather than one story at a time. It says which traditions should build a suit
capability, what it should be, and what it must not become. It does not say how
any of them is implemented.

## The claim is the role, not the tier

Breaching is what an assault suit is *for*. The XIV's own catalog copy calls it
a breach and shock pattern; the Bulwark is built for League breach formations;
the foundry-breaker is named after the act. A capability for getting through the
door is not a bonus bolted onto those patterns, it is the thing that makes them
that role rather than "heavy armour" — so **every ASSAULT pattern carries a
personal shield, and no other pattern does.**

That is deliberately not a suit-tier rule. A future tier-IV scout or line suit
would still carry nothing, and a cheap assault pattern carries one. What a player learns is
"assault suits breach", not "expensive suits get a trick".

What decides whether a pattern carries one is what that suit is *for*, and
nothing else:

- **One family, one role.** The breach family exists because breaching is a role
  definition — an assault suit that could not get through a door would be a
  strange assault suit. The rest are *individual* systems on one named pattern,
  because a section's dressings are a thing particular suits
  are built for rather than things a whole role is defined by.
- **Do not give a faction a system so it has one.** `equipment-lore-catalog.md`
  establishes that absence is meaningful and that a faction-color clone is not a
  reason to add an item. A pattern with nothing distinctive to say should say
  nothing; the Church's Palatine and the League's Bastion are complete as
  tradeoffs and would be worse with a trick bolted on.
- **A shared system is shared outright.** Identical numbers under different
  names is the palette swap this catalog exists to prevent, so suits that carry
  the same shield carry the same system, defined once and referenced by id.

## The breach family

Every assault suit carries a **personal shield**: a short window in which the
wearer speeds up and an authored screen refuses its fraction across its arc
(`combat-durability-nouns.md` owns what a screen is). For the first release the
family is **three shared tiers**, not one system per suit. Each tier is defined
once in the armour catalog's top-level `integralSystems` and each suit names its
tier by `ref`; a suit may lay its own carried hardware over the tier (the Specter
frames' shoulder emitter) and nothing else.

| Tier | System | Grade | Arc | Soak | Window / recovery | Shove | Carried by |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Low | `system.shield-lt` | Surplus | 90° | 4 | 2.5 s / 24 s | x1.15 | `riot-shell`, `foundry-breaker`, `boarding-jack` |
| Mid | `system.shield-mt` | Service | 120° | 7 | 3.0 s / 18 s | x1.25 | `specter-recovered`, `vigil`, `bulwark-heavy` |
| High | `system.shield-ht` | Milspec | 150° | 18 | 4.0 s / 22 s | x1.30 | `heavy`, `lions-mantle`, `reliquary-heavy`, `specter-heavy` |

Each tier's numbers were taken near the median of the per-suit systems its
members carried before, with the tiers kept strictly ordered in arc and soak.
The suit still differs from its tier-mates in everything the pattern owns —
capacity, rating, handling, profile — so the tier says how good the shield is
and the suit says what is wearing it.

**A capability ladders by grade.** Within one effect, every system of a grade is
worth more than every system of the grade below: Surplus, Service, Milspec for
the shields, and the same rule for the medics. What "worth
more" means cannot be a single authored number, because a screen and a shove
spend a budget on different axes: it is the screen over the arc it actually
covers, plus the shove priced in soak-equivalents, scaled by how much of the
time the system is up. `ArmorCatalogShapeTest` owns that measure and the rule.
It is a comparison index for the catalog's shape and never a balance model: the
game reads none of it.

**A medic's value lands on somebody else.** Field aid is the only effect in the
slate whose whole contribution goes to another marine. A field-aid kit makes its
wearer a squad medic (`squad-medic.md`): the same treatment over time, the same
regenerating pool and the same rules as the medic an officer's Field Medicine
promotes. The reach is short enough that the fiction stays a marine kneeling next
to another marine rather than a turret with a healing beam, and a medic may bind
their own wounds only by stopping and holding fire for it, from the same pool
and under the same gates as treating anyone, so self-treatment is never free. The kit's numbers are its entry in
`squad-medicine.json`, a weaker form than the officer's first rank. Because it
is equipment, several marines in one squad may carry one, each with their own
pool, and two medics never set out for the same patient. A squad with a kit
anywhere in it relies on its kit medics: the officer's Field Medicine promotes a
medic only in a squad that carries none.

## Individual systems

Not a family. Each is one capability on one named pattern, because that pattern
is the one built for it.

Two are shipped:

| Pattern | Tradition | System | The character it buys |
| --- | --- | --- | --- |
| `armor.orderly` | Luddic Church | **Parish aid** (MED-KIT) | A stretcher party that stayed on the field. Makes its wearer a squad medic with the smallest pool and slowest refill, and a threshold that will not start on a scratch — the cheapest possible version of somebody stopping to help. |
| `armor.corpsman` | Hegemony | **Corpsman kit** (MED-KIT-Trauma) | The same act performed by somebody taught how and carrying more to do it with. A bigger, faster-refilling pool, quicker hands, and worth starting on a marine merely hurt rather than nearly finished. |

The shoulder micro-missile pod was once a system here, on the Aegis composite,
the Packframe and both Arbalests. It is now a carried special, the SRM-POD
(`progression-nouns.md`, Shoulder pod): a pod that fires while the wearer keeps
the rifle up is a billet's ordnance choice rather than something only four suits
could offer, and those suits are plain armour. The slate therefore holds the
shields and the medics.

The medic is the same reasoning applied to the role `role-and-access.md` adds,
and it shipped by looking at the wrong end of the problem first.

It was parked because a marine here is alive or dead with nothing in between, so
recovering casualties needs a concept the simulation does not have. That framing
was the blocker, and it was avoidable: **the wounded are already there.** Every
battle has troopers walking around at a third of their health, and a system that
treats *them* needs nothing new at all. Handling bodies can wait for the concept
that would make it possible; patching people up could not, and never needed to.

Worth keeping as a general lesson: a capability parked behind a missing concept
is worth re-reading for the version of itself that does not need it.

Everything else in the catalog carries nothing, on purpose. The Church's
Gilead line suit, the League's Bastion, the pirate Blackforge Mk2, and every tier-I and
tier-II pattern are complete as tradeoffs and gain nothing from a trick.

## What each of these must not become

The lore catalog's deliberate limits carry straight through, and the ones most
likely to be violated by a *capability* rather than by a stat are:

- **Concealment is not a systems answer.** `powered-assault-armor-roles.md`
  places concealment with the light/recon role behind an explicit shared
  perception contract. `integral-armor-systems.md` already argued the breacher case at
  length — a room that fails to react is indistinguishable from a room whose AI
  broke — and the same reasoning applies to any suit that would go quiet.
- **A shield is not damage reduction.** It adds no capacity and no rating. What
  it buys is a timed, arc-limited screen — a window with a cost, not a second
  helping of armour.
- **League modularity is not self-repair.** Repairability is the Bulwark's
  identity, and the obvious mechanical reading — restore something mid-battle -
  is durability, which is forbidden.

## Where this leaves the board

`integral-armor-systems.md` owns the shipped concept and its remaining scope.
Directional mitigation and field aid have both landed, so every authored
system now expresses the whole of what it was written to be; every system
authors the moment it is spent at, and defenders wear the ones their patterns
declare (`progression-nouns.md`), and a running system is legible on the field:
its treatment is drawn from authored appearance data and, when it raises a
screen, that screen spans the arc it actually protects (`progression-nouns.md`,
presentation law). A new effect on this slate inherits that treatment by
describing itself rather than by being added to a list.

Nothing on this slate is waiting on simulation work any more. A pattern not
listed here is not in a queue — it simply has nothing a capability would add.
