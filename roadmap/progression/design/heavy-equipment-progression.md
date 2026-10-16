# Heavy equipment progression

Status: DRAFT — the stock-frame baseline, learned mech patterns, internal fittings,
vehicles in the mech bay, heavy lift and the drop choice have shipped; vehicle
variants are planned direction.

Written: 2026-10-16
Updated: 2026-10-16 — the drop choice shipped (a per-call pick from the bay, a cooldown, a mission lift budget); internals shipped.

This is the campaign upgrade path for the company's machines: its mechs, the
vehicles it may drop, and the boats that have to lift both. Marines progress by
learning loadout cards (`progression-nouns.md`); machines progress by learning
**patterns** and fitting them one machine at a time. The two ladders share access
tiers and sources but not their unit of learning, because the player fits a mech
and never fits a marine.

Read `mechs-nouns.md` (chassis, sockets, Mech Lab, heat), `convoy-nouns.md`
(vehicles) and `air-nouns.md` (boats and fittings) before changing a story below.

## The shape of the ladder

1. **A stock frame is where every machine starts.** Each chassis ships with an
   industrial engine, factory plating and a factory cooler. Those are weaker than
   what the machine could be; today's pre-nerf numbers are roughly what a
   well-refit frame reaches, not what a fresh one has.
2. **A pattern is learned, then built.** Learning a pattern gives the company the
   right to fabricate that component, internal, chassis or vehicle forever.
   Building one still spends materials, a working berth and time, and the result
   is finite stock that can be lost.
3. **A fit must fit.** The player chooses what goes on each individual machine,
   bounded by socket footprint and by the frame's engine budget. Better equipment
   is larger or heavier, so the best of everything never fits on one frame.
4. **Weight class is the ladder.** Hound is the light mech, Sirocco the medium,
   Bulwark the heavy (MechWarrior's classes). A company starts with the light
   chassis and its two starter weapons only, and earns medium and heavy patterns
   through Advanced and Prestige access. Vehicles follow the same classes.
5. **A machine has to be lifted.** Every payload has a lift class, every carrier
   a lift rating. Heavier machines need better lift, and lift is itself an
   upgrade the company earns.

## Vocabulary

- **Stock frame.** The factory internals every chassis carries when fabricated or
  recovered: industrial engine, factory plating, factory cooler. It is a fitting
  like any other, so a stock frame is upgraded by replacing its parts, never by a
  hidden multiplier. Faction issue may field better internals; the stock frame is
  the floor for every side alike.
- **Pattern.** The permanent right to fabricate one item: a weapon component, an
  internal, a chassis or a vehicle. The mech counterpart of an equipment template.
  A company that owns a pattern can build as many as it can pay for.
- **Pattern card.** The cargo item that teaches one pattern, learned with a
  right-click like a loadout card. Where a loadout card defines a whole squad, a
  pattern card is one item, because the player composes each machine.
- **Assembly.** One built, physical item in the bay's stores or on a machine.
  Finite and losable (`MechBay.loseSpares`); a pattern is neither.
- **Internal.** A non-weapon fitting that changes the frame: engine, plating, heat
  sink, cooler, loader. Internals live in sockets the doll already reserves (the
  CORE, AMMO_RESERVE and MINI_FAB placeholders).
- **Engine budget.** An engine's output, against the mass of everything fitted.
  A fit over budget is refused atomically. Engine tier also sets the chassis's
  speed within its own band, so an engine cannot make a Bulwark a Hound.
- **Lift class.** What a payload asks of its carrier: Light, Medium, Heavy.
- **Lift rating.** What a carrier can take. A boat's rating comes from its
  pattern plus a lift fitting; a Valkyrie or a ship with Ground Support carries
  Heavy.
- **Weight class.** Light, Medium or Heavy; the access tier a chassis or vehicle
  pattern carries (Common, Advanced, Prestige) and the lift class it asks for.
- **Bay space.** One gantry in the mech bay. The company ship carries four, and
  each holds a mech or a vehicle. A vehicle sharing the bay is still not a mech:
  it never uses a mech chassis, socket or heat model.
- **Drop choice.** Calling armor picks which bay spaces come down on this call,
  instead of dropping a whole lance. A cooldown spaces the calls; lift bounds the
  total per mission.

## Laws

- **Learning is permanent; building is not.** A pattern is never revoked, never
  consumed and never lost with a ship. An assembly can be.
- **A pattern card is not a loadout card and not a blueprint.** It never touches
  the Marine Armory, a squad's issue, or vanilla hull/weapon/industry knowledge.
- **One access ladder.** Pattern cards carry the existing access tiers (Common,
  Advanced, Prestige, Restricted) and arrive through the same sources (market,
  licence, patron, recovery, story) under the same gates, warrants included. A
  card's tier is the tier of what it teaches.
- **Tier costs room.** A higher-tier weapon or internal has a larger footprint or
  more mass than the one it replaces in the same role. That is what makes a light
  chassis's sockets and engine a real limit rather than a cosmetic one.
- **Every upgrade gives something up.** Heavy plating costs mass and so speed or
  budget; a large heat sink takes the utility slot a faster loader wanted; an
  uprated engine costs materials and runs hotter. Nothing multiplies structure,
  armor capacity and armor rating together (`mechs-nouns.md`, internal).
- **Chassis identity survives every fit.** A fully refit Hound is a better Hound,
  not a cheap Bulwark. Each chassis keeps its weapon bands and its weakness.
- **The fitting is frozen at launch.** Campaign validates and stores ids; the
  deployment spec carries them; battle owns heat, ammunition and damage and never
  reaches back into the campaign (`mechs-nouns.md`).
- **A payload flies in a real carrier.** Heavy drops move to company boats and
  committed ships, so a carrier lost is a carrier the company no longer has. A
  payload heavier than every available carrier's rating is not offered, and the
  briefing says why.
- **Starter content stays useful.** Wayward's nose chaingun and SRM-5 on a stock
  Hound remain a fit worth taking into the first operations.

## Stock frame (shipped)

All mechs were cut to the stock frame on 2026-10-16, enemy and employer mechs
included, on the three axes later internals will buy back:

| | Armor capacity | Move speed | Cooling |
| --- | --- | --- | --- |
| Bulwark | 950 → 710 | 1.15 → 1.05 | 8 → 6 /s |
| Hound | 500 → 375 | 1.70 → 1.55 | 8 → 6 /s |
| Sirocco | 400 → 300 | 1.45 → 1.32 | 8 → 6 /s |

Structure, armor rating, heat capacity and every weapon are unchanged, so
durability order (Bulwark, Hound, Sirocco) and each chassis's weapon bands hold.

## Internals (shipped)

Plating restores capacity, engines restore speed, heat sinks and coolers restore
cooling, each along the chassis's own band (`mechs-nouns.md`, internal and engine
budget). The ladder, untuned and awaiting a mission read:

| Role | Common (stock) | Advanced | Prestige |
| --- | --- | --- | --- |
| Engine (CORE) | Industrial, output x1.00, 2x1 | Uprated, x1.25, half the speed band, 2x2 | Military, x1.50, full band, 2x2, -0.5 cooling |
| Plating (frame) | Factory | Composite: half the armor band, 1 plating unit | Heavy: full band, 2.5 units |
| Heat sink (AMMO_RESERVE) | Factory: 100 heat, 6/s, 1x1 | Double: 120, 7.5/s, 2x1, mass 3 | Military: 140, 8/s, 3x1, mass 5 |
| Cooler (MINI_FAB) | none | Auxiliary: +1/s, mass 2 | — |

Rated output is Hound 20, Sirocco 34, Bulwark 46 (stock weapons weigh 16, 30 and
40); a plating unit is 3, 4 and 6. A full refit reaches the pre-cut armor and speed
and roughly the old cooling: Bulwark 950 / 1.15 / 140 heat at 8.5/s, Sirocco 400 /
1.45 at 8/s, Hound 500 / 1.70 at 7/s. The trade is room: the Hound's two-cell
reserve cannot take the military sink and it has no utility socket, a faster loader
displaces the cooler, and a full plate and engine leave little output for the top
weapons.

## Lift classes

| Class | Payloads | Carriers |
| --- | --- | --- |
| Light | Hound, heavy APC | Bus-class boats (Buffalo, Tarsus, Mule, Nebula) with a lift cradle |
| Medium | Sirocco, escort APC, militia technical | Bus-class boats with a heavy cradle; Valkyrie |
| Heavy | Bulwark, breakthrough tank | Valkyrie; a committed ship with Ground Support |

A lift fitting is a third boat fitting slot beside plating and drive. It trades
seats for a lift rating: a cradled boat carries one machine or a reduced section,
never both at full count. Small boats (Hermes, Kite, Aeroshuttle) never lift a
machine. The progression is that a company reaches Light drops through its own
boats early, and Heavy only through a Valkyrie or a ship refit — the expensive,
late step.

## Vehicles: variants, not fittings

Mechs are the customization system; vehicles are a short chain of fixed
variants. A second fitting system would double the lab, the save model and the
balance surface while making the two kinds the same decision in different art.
The split gives the player a real choice instead:

- **A mech is flexible and demanding.** Sockets, heat, an engine budget, a build
  to plan and parts to lose.
- **A vehicle is cheap, sturdy and good at one job.** No heat, no sockets, no
  build. It costs less material and lift for the armor it brings, and it does
  things no mech does: an APC carries marines.

Progression on a vehicle line is learning a better **variant**, each its own
pattern card at its own tier, then fabricating it and retiring the old one. The
decision is which variant fits this mission's lift and role, never how to tune
one. Lines and their first variants:

| Line | Variants (light → heavy, or sidegrade) |
| --- | --- |
| Technical | Militia technical — Common filler for a small lift budget |
| APC | Heavy APC (troop carrier) → up-armored APC (more armor, slower) → fire-support APC (a mortar or autocannon instead of seats) |
| Tank | Breakthrough tank → heavy-gun tank, or a dozer-blade breaching sidegrade |

Faction variants (a Hegemony APC, a Tri-Tachyon tank) arrive as Prestige or
Restricted cards weighted by faction, as faction mech weapons are.

One later, optional depth step: a single **overhaul** per vehicle (an up-armor
or engine kit bought with materials, one decision, no socket grid), reusing the
internals vocabulary without the doll.

## Stories

| Story | Owns |
| --- | --- |
| Mech patterns (shipped; `mechs-nouns.md`) | Learned patterns replace the starter-pattern gate; pattern cards through every existing source. |
| Mech internals (shipped; `mechs-nouns.md`) | Engine, plating, heat sink and cooler internals; engine budget; tier-scaled footprints; per-loadout heat. |
| Heavy lift (shipped; `air-nouns.md`, Lift) | Lift class and rating; the boat lift slot; drops flown by real company carriers. |
| Vehicles in the bay (shipped; `mechs-nouns.md`) | Vehicle patterns; a bay space holds a mech or a vehicle; losses write back. |
| Armor drop selection (shipped; `mechs-nouns.md`, armor drop) | Pick which bay spaces come down per call; cooldown between calls; lift caps armor per mission. |
| Vehicle variant ladder (shipped; `mechs-nouns.md`) | Fixed vehicle variants per line, each a pattern card; faction variants; no vehicle fitting. |
| Tank variant art (shipped; `convoy-nouns.md`) | Art for the heavy-gun tank and dozer breacher; both offered. |
| `dozer-breach.md` | The dozer's blade clears ground other vehicles can follow. |

Order: pattern cards first (they license existing weapons and chassis at once),
then internals, then lift; vehicles in the bay and the drop choice follow lift.

## Open questions

- The internal ladder is untuned. A mission read should settle masses, outputs and
  the Hound's lower cooling ceiling; Castle's introduction of each internal waits on
  that, and needs no recorded dialogue until the rules settle.
- Should an over-budget fit be refused, or allowed at a speed penalty? Refusal is
  the recommendation: it is legible in the lab and needs no new movement rule.
- Vehicle fitting (turret swaps) is out of scope until a vehicle doll exists;
  `mech-lab-data-authored-dolls.md` is the shared seam.
