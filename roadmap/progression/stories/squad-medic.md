# Squad medic — an officer specialty that designates one medic per squad

Status: IN PROGRESS — the specialty, treatment, AI, both movement modes,
battle and squad-card presentation, and the `squad-medic` scene are in; the
balance pass and the billet choice remain. Numbers are starting points, not
settled balance.

Written: 2026-10-14
Updated: 2026-10-14 — presentation landed and folded into the noun docs; remaining scope is balance, billet choice and copy.

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

**Which marine is the medic** is decided by billet, not by quality: the first
marine in the middle fire team other than the leader, in roster order, or the first non-leader when the squad has no
middle team. The middle team is preferred so the medic stands near everybody.
If the medic dies, the next marine by the same rule takes the role on the next
tick, so the squad does not lose medicine to a single casualty. Whether the
player can choose the billet is an open question (below).

**Naming.** The captain recruitment trait `Trait.FIELD_MEDIC` already exists. It
is a post-mission casualty reduction in `MissionResolver` and has no in-battle
effect. The specialty is `OfficerSpecialty.FIELD_MEDICINE`, shown as "Field
Medicine" under training, while the trait appears under Background. The two
names sit close in the dossier; settle the copy before release (open question).

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

- **Planted.** The patient holds from the moment they are chosen, so the medic
  can reach them, and both hold for the channel. This is the
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
- Re-plating from zero is counted on the squad's medic (`armorReplated`). The
  armor-break result the durability model computes has no consumer yet, so
  there is nothing downstream to un-react; when morale, AI or presentation start
  reading the break, they must read re-plating the same way rather than infer
  either from capacity. The HP-threshold morale proxy must not be extended to
  infer it.
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

1. **Player-chosen medic billet.** The fixed billet is legible. A per-squad
   choice in the company view is more flexible but is new UI.
2. **Planted or keep moving.** Both are built behind
   `battle.infantry.medicKeepMoving` (per simulation via
   `SquadMedicSystem.setKeepMoving`; planted by default). The balance pass
   decides the default, and whether "keep moving" becomes a player-facing option
   (squad stance or per-order choice) rather than only a tuning control.
3. **Specialty copy** against the `FIELD_MEDIC` trait, above.

## Evidence so far

`gradlew.bat sceneEvidence -Pscene=squad-medic` and
`createSnapshots -Psnapshot=squad-medic` (1920x1080). Eight armored marines
march 50 cells under an attack move with four of them hurt, then shoot at a
tough target past the objective, which fires back. One seed, so read it as a
first look rather than balance:

| loop | reached order (tick) | treatments | restored | deaths | medic rounds while treating |
| --- | --- | --- | --- | --- | --- |
| control | 959 | — | — | 2 | — |
| planted | 977 | 2 of 2 | 23.6 health, 5 armor, 1 re-plated | 3 | 0 |
| keep-moving | 947 | 2 of 3 (1 interrupted) | 25.2 health, 10 armor, 2 re-plated | 2 | 0 |

Planted costs the march about 0.6 s here; keep-moving costs nothing. In both
medic loops the medic was killed once by the target and replaced. A patient
walking out of reach mid-treatment is covered by `SquadMedicSystemTest`, since
nothing in the shipped game slows one marine against another to stage it.

## Remaining acceptance

- A balance pass across seeds and against real opposition, settling the
  movement default and the per-rank numbers.
- Live play: train the specialty on an officer, deploy their squad, and see a
  treatment in the real game.
