# Squad medic — an officer specialty that designates one medic per squad

Status: PLANNED — design agreed in outline; numbers are starting points for
measurement, not settled balance.

Written: 2026-10-14
Updated: 2026-10-14 — medic holds fire while treating; planted vs keep-moving left to the balance pass.

Read `progression-nouns.md` (officer career, legibility law),
`combat-durability-nouns.md` (armor break, the no-regeneration rule this story
amends) and `integral-system-slate.md` (why field aid lands on somebody else)
before implementing.

## Problem

A squad has no way to recover damage mid-battle unless somebody is wearing a
support pattern that carries field aid (`armor.orderly`, `armor.corpsman`).
That ties medicine to an armor choice, and a squad that wants a medic has to give
up a line or assault suit to get one. Field aid is also instantaneous and
opportunistic: the carrier never goes to anybody, and a dressing lands as one
lump the moment someone happens to stand within reach.

The goal is a medic who **patches damage up over time**: worth having, never a
reason the squad cannot lose.

## Shape

**The capability comes from the officer, not the loadout.** A fourth officer
specialty, **Field Medicine** (two ranks, like the other three), designates one
marine in each of the officer's home squads as the squad medic. The medic keeps
their own primary, special and armor, so the player loses no loadout choice to
get one. It is visible in the officer dossier and frozen with deployment. This
satisfies the legibility law in the same way Tactical Cover does: it is a shown
command modifier, not a hidden per-squad bonus.

It costs a specialty point. Five lifetime points still cannot master all
the specialties, and now there are four of them, so taking Field Medicine is a
real trade against cover, recovery or salvage.

**Which marine is the medic** is decided by billet, not by quality. The medic
is a fixed non-leader billet. Seniority picks among equal-rank candidates, which
is the one decision `progression-nouns.md` lets the career record make. If the
medic dies, the next marine in billet order takes the role on the next replan, so
the squad does not lose medicine to a single casualty. Whether the player can
choose the billet is an open question (below).

**Naming.** The captain recruitment trait `Trait.FIELD_MEDIC` already exists. It
is a post-mission casualty reduction in `MissionResolver` and has no in-battle
effect. The specialty must not reuse that name in code or copy, or the dossier
will show two "field medics" that do different things. Working enum name:
`OfficerSpecialty.COMBAT_MEDICINE`, displayed as "Field Medicine". Settle the
copy against the trait's display string before shipping.

## The treatment

A **treatment** is a channel: the medic kneels beside a squadmate and restores
health, and armor, a little on every tick for its duration. This is the
cutter's shape (`CloseContactTactics.tickChannel`): legality is re-checked every
tick, and the channel is visibly in progress rather than an instant number.

Starting numbers, for a 25 HP marine who takes 18 from one rifle hit:

| | Rank 1 | Rank 2 |
| --- | --- | --- |
| Supplies carried | 3 | 4 |
| Supply recovery | 1 per 90 s, up to the cap | 1 per 60 s, up to the cap |
| Cooldown between treatments | 10 s | 8 s |
| Channel length | 5 s | 5 s |
| Health restored | 2.5 / s (12.5 per full treatment) | 3 / s (15) |
| Armor restored | 1 / s (5 per full treatment) | 1.5 / s (7.5) |
| Reach | 1.5 cells | 1.5 cells |

- A supply is spent when the channel **starts**. Restoration is applied per
  tick, so an interrupted treatment keeps what it already delivered and the
  supply is gone. That makes interruption cost something without becoming an
  exploit.
- Everything is clamped to the patient's own maxima, and restoration is flat
  rather than a fraction, for the reason `FieldAidSpec` gives: a bandage is a
  bandage.
- **Supply recovery** is a slow trickle back toward the cap and never above it.
  It is what makes the medic matter in a long Conquest without making them
  matter in a short firefight. A medic who could treat every few seconds
  forever is the "more effective hit points" the integral-system slate forbids.
- **Never the medic themselves.** This is the same rule as field aid.
- A patient qualifies below 70% health, or with armor below 50% of capacity
  (including broken). The worst-hurt qualifying squadmate wins, and health
  outranks armor at equal need.
- Squadmates only. Not other squads, not allied factions.

The channel ends when its time is up, when the patient is healed to its maxima,
when either marine dies, or when the patient leaves reach. Breaking under fire
was considered and **not** adopted. The limits are supplies, recovery and
cooldown, plus the two costs below.

**The medic holds fire while treating.** A treatment costs the squad that rifle
for its duration. This is decided, not a balance knob: hands on the patient are
the price of the treatment.

**Whether treatment stops movement is left to the balance pass**, so both modes
are built and selectable:

- **Planted.** Medic and patient both hold position for the channel. This is the
  cheaper and more vulnerable treatment: two marines stationary for five
  seconds. A squad advancing under orders leaves them behind for that time and
  the cohesion pull brings them back afterwards.
- **Keep moving.** Neither marine stops. The medic follows the patient and keeps
  treating while the patient is within reach, so a squad on the move is not
  slowed by its own medicine. Reach failing is the usual way it ends.

A battle switch picks the mode for measurement. Whether "keep moving" then
becomes a player-facing option, such as a squad stance or per-order choice, is
decided from that measurement (open question 2).

## Re-plating broken armor

Treatment may restore armor from zero. That amends two standing statements in
`combat-durability-nouns.md`: "no armor regeneration", and "armor reaching zero
stays an armor break".

- The model keeps exactly one place where armor goes up: a treatment. There is
  still no passive regeneration and no self-repair, and the "League modularity is
  not self-repair" rule in the slate holds. A mitigation still never restores a
  capacity.
- Re-plating from zero emits an **armor restored** fact. It is the counterpart
  of armor break and is consumed the same way: morale, AI and presentation read
  the fact, never infer it from capacity. The HP-threshold morale proxy that
  `combat-durability-nouns.md` tolerates must not be extended to infer this.
- Armor break stays a one-time *transition*, not a one-time *life event*. A
  re-plated marine whose armor is shot away again emits a second break. Every
  break consumer must be checked for a "has broken once" assumption.
- Armor rating is unchanged. A re-plated suit is the same suit with capacity
  back, not a better one.

The noun-doc amendment ships in the same commit as the behaviour.

## AI

The medic works on a **short leash**. While not channelling and with a supply
ready, they choose the worst-hurt qualifying squadmate within a small radius
(starting point: squad cohesion distance), walk to them and treat them. The
medic does not go for a patient while their own position is taking heavy
incoming fire. That reuses the existing incoming-pressure reading, not a new one.

This must stay inside the yield and goal rules the scenes have already enforced:

- Treatment is a squad-support behaviour beneath mission goals, not a goal that
  outranks the order the squad was given. A squad under `CLEAR_ZONE` still
  clears the zone; the medic peels off only within the leash.
- A medic with nobody to treat does not invent work (`YieldFreezeScene`'s
  lesson). Plan-less ticks and wandering are both regressions.
- In planted mode a held marine is held by exactly one caller per tick. The
  treatment owns the hold and hands it back when the channel ends, so the
  `FiringLineScene` one-mover rule still holds. In keep-moving mode the
  treatment never moves the patient; only the medic follows.

## Out of scope

- Reviving the dead, or a downed state. `integral-system-slate.md` explains why
  treating the wounded needs no new concept and handling bodies does.
- Defender medics. Officer specialties are a player-company concept. A defender
  equivalent would arrive as authored formation data, not this specialty.
- Changing the armor-built field aid on `armor.orderly` / `armor.corpsman`. Both
  stay as they are. A squad with a corpsman suit and a designated medic has two
  sources of medicine, which is acceptable because both are finite. Revisit only
  if measurement says otherwise.
- Campaign-side medicine (the WIA clock). That is Recovery Planning's job.

## Open questions

1. **Player-chosen medic billet.** A fixed billet is simplest and legible. A
   per-squad choice in the company view is more flexible but is new UI.
2. **Planted or keep moving.** Both are built behind a switch. The balance pass
   decides the default, and whether "keep moving" becomes a player-facing option
   (squad stance or per-order choice) rather than only a tuning control.
3. **Presentation.** A use pose and treatment FX for the medic, a mark on the
   patient, and the designated medic legible on the field and on the squad card
   (`company-view-nouns.md` already uses an amber medical mark for *wounded*;
   the medic needs a distinct mark).

## Acceptance

- Unit tests on the treatment itself: per-tick restoration, clamping, supply
  spent at start, recovery to cap, cooldown, the never-self rule, patient
  selection order, and armor-restored emitted exactly once per zero-to-positive
  transition.
- A `BehaviorScene` (`squad-medic`) with verdicts under `sceneEvidence` and
  frames under `createSnapshots` at 1920x1080 / 100%: a wounded squad with a
  designated medic against an identical squad with the specialty off, the same
  medic squad advancing under an order in planted and in keep-moving mode, and a
  loop where the patient walks out of reach mid-channel. Readings: health and
  armor restored, treatments started/completed/interrupted, rounds the medic
  fired while channelling (must be zero), plan-less ticks, medic distance from
  squad centroid, and time for the squad to reach its order in each mode.
- The armor-restored fact is consumed by every current armor-break consumer, or
  each one is shown to be correct without it.
- Dossier shows the specialty's exact effects per rank; the squad card shows
  which marine is the medic.
- `combat-durability-nouns.md` and `progression-nouns.md` updated in the shipping
  commit.
