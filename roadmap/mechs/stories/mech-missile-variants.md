# Mech missile variants: anti-armor and fragmentation

Status: PROPOSED — awaiting the owner's OK on the design before implementation.

Written: 2026-10-18

Read `mechs-nouns.md` (sockets, heat, missile replenisher, duel range),
`combat-durability-nouns.md` (penetration, contact and area payloads) and
`heavy-equipment-progression.md` (tier costs room, every upgrade gives something
up) first.

## Why

Today each rack size is one weapon: every SRM rocket is 49.5 area damage at
penetration 14 over a 1.3-cell blast, and every LRM rocket 81 at penetration 16
over 2.0 cells. That is a very good anti-infantry weapon (penetration 14 beats
every infantry rating, 0-9, and every power suit, 10-14) and a mediocre
anti-armor one. Armor efficiency `0.10 + 0.90 * pen / rating`:

| Target (rating) | SRM (pen 14) | LRM (pen 16) |
| --- | --- | --- |
| Infantry, power suits, Sirocco, Hound (≤14) | 1.00 | 1.00 |
| Bulwark, heavy APC (18) | 0.80 | 0.90 |
| Up-armored / escort APC (24) | 0.63 | 0.70 |
| Tanks (36-40) | 0.40-0.45 | 0.46-0.50 |

So the racks are doing the chaingun's job as well as their own, and against
tanks, the armor they ought to answer, they are weakest. That is part of why
every tank beats every Bulwark fit (93-95%, `heavy-equipment-progression.md`).
The owner's direction (2026-10-18): a mech's arm guns are its anti-infantry
weapons and its SRM/LRM racks its anti-armor, with separate anti-armor and
fragmentation missile variants.

## The two variants

Same rack sizes, same sockets, same footprints, same cycle, heat, ammunition and
replenisher. What differs is the warhead.

**Anti-armor (AT).** A tandem shaped charge. Most of the damage is a **contact**
payload, delivered only to the body the rocket physically strikes, at high
penetration. A small, weak splash is left for a near miss. Against a soft target
it is a waste, so the AI does not fire it at one (below).

**Fragmentation (Frag).** No contact payload. All of its damage is **area**
damage at low penetration over a wider blast than today's. It kills men in the
open and in trenches, strips little plate, and its near misses still count.

Starting numbers, per rocket, for headless tuning (not final):

| | Contact (dmg / pen) | Area (dmg / pen / radius) | Wall damage |
| --- | --- | --- | --- |
| SRM today | — | 49.5 / 14 / 1.3 | 25 |
| SRM AT | 66 / 30 | 10 / 6 / 0.6 | 25 |
| SRM Frag | — | 32 / 4 / 1.8 | 10 |
| LRM today | — | 81 / 16 / 2.0 | 40 |
| LRM AT | 95 / 30 | 15 / 6 / 1.0 | 40 |
| LRM Frag | — | 50 / 4 / 2.8 | 15 |

The trade is the one the laws ask for. AT gives up the blast, so a miss does
almost nothing and it is useless against men. Frag gives up penetration, so
against a Bulwark (rating 18) it removes plate at 0.30 efficiency and against a
tank at 0.19. Neither is a tier upgrade over the other.

The tuning targets, read against the control run:

- An AT rack does at least today's damage to every mech and vehicle, and
  noticeably more to tanks, without making a Bulwark beat a tank outright. Closing
  part of the tank gap is wanted; reversing it is not.
- A Frag rack kills infantry at least as fast as today's rack.
- A Frag rack against armor is clearly worse than an AT rack: carrying the wrong
  one has to be a real mistake.

## What the AI fires at what

**Target gating.** An AT rack fires only at a hardened target:
`TacticalScoring.isHardened`, the same gate infantry rockets use (mechs, ground
vehicles, turrets, drone hubs). It holds fire at infantry, power suits included.
A Frag rack fires at anything, armor too, because a Frag-only mech facing armor
should still shoot. Holding an AT rack costs nothing: it keeps its packs, and
the reservoir goes to the arms, so a chaingun Bulwark facing only infantry fires
its chainguns more often than today.

**Target choice.** Gating alone leaves the AT rack idle whenever a man is nearer
than the tank, because a mech aims its whole torso at one target and today that
is the nearest one. So target choice becomes damage-aware: beyond the 10-cell
close-threat radius (which still wins, as self-defence), a mech aims at the
engageable target its loaded, ready mounts can hurt most per second. That is
computed with the shared durability calculation, which the durability laws
already require for AI prediction, and with gating applied, so an AT rack adds
nothing against a man and a chaingun adds little against a tank. A chaingun
Bulwark with AT racks then turns to a tank when its racks are loaded, and back
to the infantry while they replenish. The existing retention rules still keep a
usable target stable, so it does not flick between targets every tick.

**Duel range.** `MechDuelBand` reads expected damage per second while leaving
penetration out on purpose. A Frag rack would then count at full weight against
armor it barely scratches. The band reads a Frag rack at its armor efficiency
against the target, and an AT rack at zero against a soft target. This is the
one place where penetration enters the band. The reason is that the variant
exists to make penetration decide the matchup.

**Long-range support.** LRM indirect fire is unchanged. An LRM Frag rack is
artillery against men; an LRM AT rack is indirect fire against armor, at the
existing no-line-of-sight accuracy penalty.

## Patterns, tier and mass

- The existing SRM-5, SRM-15, LRM-5 and LRM-15 patterns **become the AT
  variants**, keeping their ids, tiers (SRM-5 and LRM-5 Common, SRM-15 and LRM-15
  Advanced), bills and mass. Saves keep what they own.
- Four new patterns: **SRM-5 Frag** and **LRM-5 Frag** (Common), **SRM-15 Frag**
  and **LRM-15 Frag** (Advanced). Each has the same footprint, tier and mass as
  its AT counterpart. Mass is footprint plus tier (`MechFrameBudget`), so a
  sidegrade costs the same room. Bills are slightly lower: there is no shaped
  charge, so a little less rare metal.
- Same sources, cards and gates as their AT counterparts. Pirate, Luddic and
  Independent markets weight the Frag cards up; Hegemony and Tri-Tachyon weight
  the AT cards up.
- The Mech Lab hover sheet shows both payloads and the target gate ("Fires at
  armor only").

## Faction and production fits

The owner's direction makes today's racks the AT ones, so production hardware is
unchanged: the neutral Bulwark is chainguns (anti-infantry) with twin AT SRM-15s
(anti-armor). Two fits need a decision:

1. **The Sirocco.** Its heavy cannon is already its anti-armor arm. AT LRMs leave
   it with almost nothing to fire at infantry beyond the cannon's splash. The
   proposal is that the Sirocco ships **Frag LRMs**, making it the line's
   artillery against men, with the cannon as its answer to armor.
2. **The starter Hound.** Its nose chaingun and SRM-5 face mostly infantry in the
   first operations. With an AT SRM-5, the rack is idle there. The proposal is
   that the company **starts owning both SRM-5 patterns** and the Wayward Hound
   arrives with the Frag one, so the starter fit still earns its gantry (the
   "starter content stays useful" law). The player learns the AT trade-off when
   the first armor shows up.

## Wall damage and breaching

AT keeps today's wall damage: a shaped charge opens a wall. Frag drops to about
40%. If the breach planner credits missile racks as breachers, it reads wall
damage per variant rather than per rack size.

## Evidence plan

All headless, each against its own control run.
`-Dbattle.mech.missileVariants=false` restores today's payloads on the AT ids
and nearest-target choice. `-Dbattle.mech.damageAwareTargeting=false` isolates
the target-choice change.

- **Against armor:** `armorMatchup`, eight seeds, all three scenarios, both
  roles. Sides: chaingun Bulwark with AT SRM-15s, the same with Frag SRM-15s, the
  stock and refit Hound with each SRM-5, and the Sirocco with each LRM fit.
  Opponents: each other, the breakthrough and heavy-gun tanks, the escort and
  up-armored APCs.
- **Against infantry:** `loadoutMatchup` fights infantry against infantry and
  cannot field a mech, so this needs a new `BehaviorScene`,
  `mech-versus-infantry`. One mech against a twelve-marine starter section, in
  the open and in a trench line, plus a mixed loop: a tank behind an infantry
  screen.
  - Verdicts on mechanics only: an AT rack fires no rounds at soft targets; a
    Frag rack fires at infantry; in the mixed loop, a mech with loaded AT racks
    aims at the tank at least once beyond close-threat range.
  - Readings, not verdicts: marines killed, time to break the section, and
    damage by mount.
- **Unit tests** (each under a second): the gate predicate, the damage-aware
  target score on plain inputs, the duel band's efficiency read, pattern mass and
  tier parity, and that the weapon definitions parse.

Done means: both variants are shipped and the readings are recorded in
`heavy-equipment-progression.md` and the mechs noun doc. The story is then folded
into `mechs-nouns.md` (mounts, targeting, duel range) and
`combat-durability-nouns.md` (a worked contact/area split), and deleted.

## Open questions for the owner

- Do the Sirocco ship with Frag LRMs and the starter Hound with a Frag SRM-5?
  (Proposed yes to both, above.)
- Damage-aware target choice changes every mech's behaviour, not only missile
  carriers. Is that acceptable, behind its control switch? The alternative is to
  gate the AT racks only and accept that they sit idle in mixed fights.
