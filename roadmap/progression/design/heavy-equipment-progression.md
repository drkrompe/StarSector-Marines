# Heavy equipment progression

Status: DRAFT — the stock-frame baseline, learned mech patterns, internal fittings,
vehicles in the mech bay, heavy lift and the drop choice have shipped; vehicle
variants are planned direction.

Written: 2026-10-16
Updated: 2026-10-18 — tuned on headless readings: engines carry mass, military sink 120, Sirocco rated 36, call cooldown 30 s, heavy cradle three lifts, APC bills cut.

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
budget). The ladder, tuned on headless readings (below):

| Role | Common (stock) | Advanced | Prestige |
| --- | --- | --- | --- |
| Engine (CORE) | Industrial, output x1.00, 2x1 | Uprated, x1.25, half the speed band, 2x2, mass 1 | Military, x1.50, full band, 2x2, mass 3, -0.5 cooling |
| Plating (frame) | Factory | Composite: half the armor band, 1 plating unit | Heavy: full band, 2.5 units |
| Heat sink (AMMO_RESERVE) | Factory: 100 heat, 6/s, 1x1 | Double: 120, 7.5/s, 2x1, mass 3 | Military: 120, 8/s, 3x1, mass 5 |
| Cooler (MINI_FAB) | none | Auxiliary: +1/s, mass 2 | — |

Rated output is Hound 20, Sirocco 36, Bulwark 46 (stock weapons weigh 16, 30 and
40); a plating unit is 3, 4 and 6. A full refit reaches the pre-cut armor and speed
and roughly the old cooling: Bulwark 950 / 1.15 / 120 heat at 8.5/s, Sirocco 400 /
1.45 at 8/s, Hound 500 / 1.70 at 7/s. The trade is room: the Hound's two-cell
reserve cannot take the military sink and it has no utility socket, a faster loader
displaces the cooler, and a full plate and engine leave little output for the top
weapons.

**Why engines carry mass.** Every authored doll's CORE socket is 2x2, so an
engine's footprint never costs room; mass is the only price an engine can pay.
With both upgraded engines massless, the military engine cost nothing over the
uprated one and the best of everything fitted on the Hound (28.5 of 30) and the
Bulwark (68 of 69), breaking *tier costs room*. At mass 1 and 3 it fits on no
chassis (Hound 31.5 / 30, Sirocco 62 / 54, Bulwark 71 / 69), while a full
internal refit around the factory weapons still fits all three (29.5 / 30,
48 / 54, 65 / 69). Heavy plate plus the military engine leave 19.5, 41 and 51
for weapons and the rest. `MechFitLawsTest` pins these laws; the whole fit space
is `generatedSpaceEvidence --tests '*MechFitSpaceEvidence'`.

**What the frames buy, measured.** `sceneEvidence -Pscene=mech-frame-endurance`
plants each chassis with its factory weapons in a stock, a best-buildable refit
and the pre-cut frame under fixed fire (ten rifles with one AMR; a stock
Bulwark), eight seeds, with `-Dbattle.mech.locationalDamage=false` as the
whole-body control. Mean seconds survived, rifles / Bulwark:

| | Stock | Refit | Pre-cut |
| --- | --- | --- | --- |
| Bulwark | 91 / 115 | 122 / 149 | 125 / 146 |
| Hound | 44 / 55 | 51 / 71 | 59 / 74 |
| Sirocco | 18 / 25 | 21 / 37 | 21 / 37 |

The Sirocco rows were read while a planted long-range support mech still
turned its back on a close threat (`mechs-nouns.md`, Long-Range Support). Now
that it keeps the threat in its arc, it lasts 29 s stock and 43 s refit against
rifles. The refit now sits just under a stock Hound's 44 s, so take more seeds
before making a frame call there.

Bulwark > Hound > Sirocco holds at every equal tier, under both fires and both
damage models, and a refit Hound stays far below a stock Bulwark. A stock Hound
keeps about three quarters of its pre-cut endurance and 72-82% of its damage
output, so the starter fit still earns its gantry. A full refit restores the
Bulwark and Sirocco to pre-cut endurance and the Hound to 0.85-0.97 of it, inside
a seed spread of ±10-20 s — use eight seeds for any call near 10%.

**The Hound's cooling ceiling stays below the old 8/s.** Its weapons draw about
43 heat/s against 6-8/s of cooling, so sustained fire scales almost linearly
with cooling (a stock Hound firing for 60 s: 971 damage at 6/s, 1080 at 7/s,
1180 at 8/s). The uprated-engine Hound (composite plate, double sink, 120 heat at
7.5/s, 23 of 25 mass) already matches pre-cut damage per minute; the military
refit gives up 7-18% of that for full armor and speed. That trade is the one the
laws ask for, so the ceiling is 7.5/s on the uprated engine and 7/s on the
military one.

**The military sink holds 120, and its edge is cooling.** The AI fires a weapon
only while the reservoir stays under 90%, locks out at 75% and releases at 40%,
and it tries the arms before the shoulders. So whether a released Bulwark fires
an SRM-15 salvo (42 heat) before its chainguns' next cycle (30 heat every
1.05 s) depends on whether the reservoir exceeds 2 x (72 - 1.05 x cooling),
about 126 at 8.5/s. Above that line each release goes chaingun plus SRM, about
5% more damage against a mech. Below it, it goes chaingun plus chaingun, the
same mix the pre-cut frame fires. Readings are flat on either side of the line,
which is why an eight-seed study of 130 against 140 looked non-monotonic. At 140
a full-refit Bulwark out-gunned its pre-cut frame by 16-18%; at 120 it is 12-13%
over, at equal endurance (192 seeds, standard error about 1%). 120 stays under
the line for any cooling up to 11.4/s, and it is the floor below which the
Prestige sink would hold less than the double sink. The remaining 12% is the
Prestige premium: cooling 8.5 against the pre-cut 8.0, and a 120 reservoir
against 100. It still beats the double-sink refit by about 9%. Cutting its
cooling to 7.5 would make it buy nothing, and a harsher military-engine penalty
would also hit the Hound.

**The Sirocco is rated 36.** The LRM-15 is long-range support's Advanced rack,
as the SRM-15 is the Bulwark's. At 34 the most fragile chassis had to give up its
double sink or its heavy plate to carry one. At 36 the factory heavy cannon,
LRM-5 and LRM-15 fit a full frame (400 armor, 8/s) with no headroom, so the tier
still costs room. The best of everything still does not fit (62 of 54), and the
refit frame is unchanged, so a refit Sirocco still out-plates no Hound.

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

**Lift per mission and the call cooldown.** A light-cradled company boat flies
two machine lifts a mission, a heavy-cradled one three, and the synthetic heavy
carrier lifts the whole bay. The heavy cradle used to fly two as well, which made
it a worse buy than the light one: it costs about 2.3 times the bill, and a
Sirocco brings less armor (550 structure plus capacity) than a Hound (725), so
the better cradle put less on the ground. Best bay per tier, structure plus
armor capacity:

| Tier | Machines | Armor on the ground |
| --- | --- | --- |
| Starter, light cradle | 2 | 1450 (two Hounds) |
| Mid, heavy cradle | 3 | 3120 (three escort APCs); 2175 mechs only |
| Late, Valkyrie | 4 | 9600 (four dozers); 5040 (four Bulwarks) |

The late tier is capped by the four gantries, not by lift. Calls are spaced
30 s apart (`-Dbattle.power.armorDropCooldownSeconds=N` is the control). The
pace is set by `sceneEvidence -Pscene=armor-drop-pace`, which calls as soon as the
picker allows. A carrier's round trip is about 10.7 s plus 0.27 s per cell of
landing depth: 18 s at 30 cells, 35 s at 80, 58 s at 150. Decided zero-input
missions run 55-193 s. At 45 s, a second split call landed mid-fight and a third
only at the end, and a Valkyrie fed one machine at a time had three of four down
by 120 s. At 30 s, all four are down by 98-116 s. A cradled boat landing deeper
than about 80 cells is paced by its own round trip at either setting, so the
cooldown matters only for shallow drops and a heavy carrier fed piecemeal.
Better boats still mean more armor on the ground, never faster calls.

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
| Supply | Supply truck — a carried resupply cache, served while halted (Light lift) |
| Scout | Scout car — a sight mast that reports to fog and commander (Light lift) |

**A vehicle brings at least its class mech's armor per credit.** At base
commodity prices (`gradlew.bat armorEconomyEvidence`), structure plus armor
capacity per thousand credits against the class mech: heavy APC 1.00x the Hound;
technical 2.56x, escort 2.38x, up-armored 1.99x and fire-support 1.23x the
Sirocco; breakthrough 1.80x, heavy-gun 1.63x and dozer 2.04x the Bulwark. The
heavy APC was 0.73x, a worse buy than the mech it shares a lift class with, and
was cut to 38/28/88/3 (supplies, machinery, metals, rare metals). Its variants
followed: up-armored 52/40/140/8 (1.51x the base bill for 1.63x the armor) and
fire-support 52/40/115/11 (the base hull plus a mortar-class weapon's price).
Per lift, every vehicle ties or beats its class mech except the heavy APC (380
against the Hound's 725) and the fire-support APC (380 against the Sirocco's
550). Those two pay in plate for their seats and their mortar. A role hull
bought for its job rather than its plate (the fire-support APC's mortar, the
scout car's sight, the supply truck's stock) is held only to costing less than
its class mech. The measure is
bulk only and ignores armor rating, so it is not effective durability. The
dozer is the best armor per credit in the game, and raising its bill past the
Bulwark's would break this rule, so it stays as it is.

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
| Dozer breach (shipped; `convoy-nouns.md`) | The dozer's blade clears ground other vehicles can follow. |
| Light roles (shipped; `convoy-nouns.md`, light roles) | Supply truck through the resupply authority; scout car through fog and commander contacts. |

Order: pattern cards first (they license existing weapons and chassis at once),
then internals, then lift; vehicles in the bay and the drop choice follow lift.

## Open questions

- A Sirocco now dies to one heavy cannon in about 11 s in the open (the
  cannon's contact hit, `combat-durability-nouns.md`), so a Sirocco mirror fight
  is short. Armor-vs-armor matchup scenes should read whether that is acceptable.
- With the threat in reach, a long-range support mech's fighting withdrawal
  still turns a little back and forth against a firing line whose members take
  turns as its target.
- Engine speed bands are reported, not measured: every endurance reading so far
  is a planted mech.
- Castle's introduction of each internal needs no recorded dialogue until a live
  pass confirms the ladder.
- Vehicle fitting (turret swaps) is out of scope until a vehicle doll exists;
  `mech-lab-data-authored-dolls.md` is the shared seam.
