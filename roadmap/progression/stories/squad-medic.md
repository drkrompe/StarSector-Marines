# Squad medic — an officer specialty that designates one medic per squad

Status: IN PROGRESS — the specialty, the energy-pool treatment, the AI, keep-moving
treatment, battle and squad-card presentation, and the `squad-medic` scene are
in; the balance pass, the billet choice and the copy remain. Numbers are
starting points, not settled balance.

Written: 2026-10-14
Updated: 2026-10-23 — keep-moving shipped as the only movement mode (switch and planted path deleted); supplies and cooldown replaced by an energy pool with a start gate; open question 2 closed.
Updated: 2026-10-27 — the suits' field-aid kits now make their wearers squad medics on the same pool (owner); see Kit medics.

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
health, and armor, a little on every tick. Legality is re-checked every tick,
and the treatment is visibly in progress rather than an instant number.

**The budget is an energy pool** (owner, 2026-10-23), the Starcraft mana model:
the medic deploys with it full, it regenerates slowly and always, and a
treatment spends it per point of health or armor actually restored. There are
no supplies and no cooldown between treatments. The numbers are data, per rank,
in `data/marines/squad-medicine.json`; the arithmetic is `MedicEnergy`.

Starting numbers, for a 25 HP marine with 5 armor who takes 18 from one rifle hit:

| | Rank 1 | Rank 2 |
| --- | --- | --- |
| Pool | 30 | 40 |
| Regeneration | 0.2 / s (empty to full in 150 s) | 0.3 / s (133 s) |
| Energy per point restored | 1 | 1 |
| Minimum to start | 7.5 (a quarter of the pool) | 10 |
| Wound floor | 5% of health and armor together | 5% |
| Health restored | 2.5 / s | 3 / s |
| Armor restored | 1 / s | 1.5 / s |
| Reach | 1.5 cells | 1.5 cells |

Why those numbers: the old rank-one treatment restored up to 12.5 health and 5
armor (17.5 points; 22.5 at rank two), so a full pool covers about one and
three-quarter of them, or one badly hurt marine made whole (a 6 HP marine with a
broken plate is 24 points). The old supplies came back at one treatment per 90 s
(60 s at rank two), about 0.19 (0.38) points a second; the regeneration keeps
rank one at that pace and slows rank two a little, so a pool refills over a
couple of minutes of fighting — a Conquest's long middle, not one firefight.

- **Start gate and continue to empty** (owner, 2026-10-23). Spending
  continuously would let a medic stop fighting to dribble out each sliver of
  energy as it regenerates, so the rules are deliberately asymmetric. A
  treatment starts only when the pool holds at least the lesser of the energy
  that would make the patient whole and the minimum to start; once started, it
  spends until the patient is whole, the pool is empty, or reach is broken,
  and the gate is not checked again. A patient missing less than the wound
  floor is never started on. Patient choice is unchanged (below 70% health or
  50% armor), so in practice the floor is a backstop: it binds only if those
  thresholds are raised.
- Both restorations run at their own rates and are paid at the same price per
  point; when the pool cannot pay for a whole tick, both are scaled down
  together. An interrupted treatment keeps what landed and simply stops
  spending, so nothing is lost and nothing needs refunding.
- Everything is clamped to the patient's own maxima, and restoration is flat
  rather than a fraction, for the reason `FieldAidSpec` gives: a bandage is a
  bandage.
- **Regeneration** is a slow trickle back toward the cap and never above it.
  It is what makes the medic matter in a long Conquest without making them
  matter in a short firefight. A medic who could treat every few seconds
  forever is the "more effective hit points" the integral-system slate forbids.
- The pool belongs to the squad's medic billet, so a replacement medic picks up
  what is left rather than a fresh pool.
- **The one delay that remains** is after an abandoned approach (the patient
  moved past the leash, recovered, or the approach ran out of time): the medic
  waits three seconds before setting out again, so a patient they cannot reach
  is not chased afresh every tick. A finished or interrupted treatment has no
  delay; the start gate already keeps an empty pool from restarting.
- **Never the medic themselves.** This is the same rule as field aid.
- A patient qualifies below 70% health, or with armor below 50% of capacity
  (including broken). The worst-hurt qualifying squadmate wins, and health
  outranks armor at equal need.
- Squadmates only. Not other squads, not allied factions.

The treatment ends when the patient is healed to its maxima, when the pool is
empty, when either marine dies, or when the patient leaves reach. Breaking under fire
was considered and **not** adopted. The limits are supplies, recovery and
cooldown, plus the two costs below.

**The medic holds fire while treating.** A treatment costs the squad that rifle
for its duration. This is decided, not a balance knob: hands on the patient are
the price of the treatment.

**Treatment never stops the squad** (owner, 2026-10-23). Neither marine is held:
the patient carries on with whatever the squad is doing, and the medic follows
and keeps treating while the patient is within reach, so a squad on the move is
not slowed by its own medicine. Reach failing is the usual way a treatment ends
early. A planted mode, holding both marines for the treatment, was built and
measured against this and deleted: it cost the march time and bought nothing the
energy pool does not already limit.

## Kit medics

The suits' field-aid kits (owner, 2026-10-27: "swap this to the 'medic' system
commanders provide. If commander has skill this is improved ... these should
work on Mana system"; then "they feed and also add a weaker form of the role to
the squad if the commander doesn't have the skill unlocked ... it might be on
more units in the squad"). The old instant dressings out of a fixed count are
gone.

- **A kit makes its wearer a squad medic** with every rule above: treatment over
  time, the energy pool, the start gate, keep-moving, holding fire, re-plating.
- **Without Field Medicine** a kit treats with its own weaker medicine, authored
  per kit in `squad-medicine.json` under `kits`, keyed by the integral system id.
  Starting numbers: MED-KIT (`system.parish-aid`) pool 20, 0.1 / s, 2 health and
  0.5 armor a second, treats below 60% health; MED-KIT-Trauma
  (`system.corpsman-kit`) pool 25, 0.15 / s, 2.5 and 0.75, below 70%. Both sit
  under rank one.
- **With Field Medicine** every kit in the officer's squads treats at the
  officer's rank, and the officer's own billet medic is still designated.
- **Several per squad.** A kit is equipment, so every wearer is a medic with
  their own pool. The kit dies with its wearer and is not passed on. The
  officer's billet is chosen as before, so the squad card stays right at
  deployment; if it lands on a kit wearer they are one medic, at the officer's
  rank, and when it passes on it skips marines who are already a kit's medic.
- **Two medics never set out for the same patient**: a squadmate another medic
  is approaching or treating is passed over for the next worst hurt.
- The kit's catalog entry declares `resource: "energy"` and nothing else of its
  own; the retired per-dressing keys are refused by name.

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
- The treatment never moves or holds the patient; only the medic follows, so
  the `FiringLineScene` one-mover rule holds.

## Out of scope

- Reviving the dead, or a downed state. `integral-system-slate.md` explains why
  treating the wounded needs no new concept and handling bodies does.
- Defender medics. Officer specialties are a player-company concept. A defender
  equivalent would arrive as authored formation data, not this specialty.
- Campaign-side medicine (the WIA clock). That is Recovery Planning's job.

## Open questions

1. **Player-chosen medic billet.** The fixed billet is legible. A per-squad
   choice in the company view is more flexible but is new UI.
2. ~~Planted or keep moving.~~ Closed 2026-10-23: keep moving is the only
   behaviour, and not a player option.
3. **Specialty copy** against the `FIELD_MEDIC` trait, above.
4. **Kit wearers on the squad card.** The card marks only the officer's billet;
   kit wearers show their kit on the suit line but carry no card badge. Marking
   them needs the card to resolve each billet's issued armour.

## Evidence so far

`gradlew.bat sceneEvidence -Pscene=squad-medic` and
`createSnapshots -Psnapshot=squad-medic` (1920x1080). Eight armored marines
march 50 cells under an attack move with four of them hurt, then shoot at a
tough target past the objective, which fires back. One seed, so read it as a
first look rather than balance:

| loop | reached order (tick) | treatments | restored | deaths | medic rounds while treating |
| --- | --- | --- | --- | --- | --- |
| control | 959 | — | — | 2 | — |
| medic (energy pool, rank 1) | 944 | 4 started: 1 made whole, 1 interrupted, 1 ran the pool dry | 32.5 health, 6.9 armor, 2 re-plated | 3 | 0 |

The march is not slowed. The medic was killed twice by the target and replaced,
and the pool ended the minute at 2.5 of 30. A patient
walking out of reach mid-treatment is covered by `SquadMedicSystemTest`, since
nothing in the shipped game slows one marine against another to stage it.

## Remaining acceptance

- A balance pass across seeds and against real opposition, settling the
  movement default, the per-rank numbers and the per-kit numbers, with
  several kit wearers in one squad.
- Live play: train the specialty on an officer, deploy their squad, and see a
  treatment in the real game.
