# Heavy equipment progression

Status: DRAFT — the stock-frame baseline, learned mech patterns, internal fittings,
vehicles in the mech bay, heavy lift and the drop choice have shipped; vehicle
variants are planned direction.

Written: 2026-10-16
Updated: 2026-10-26 — drops are free, one machine each, paced by the ferry's lift queue. Earlier 2026-10-24 — vehicles: variants and kits (a sealed doll, a rack of bolt-on kits); the earlier crossfire, facing and missile-carrier readings stand below.

This is the campaign upgrade path for the company's machines: its mechs, the
vehicles it may drop, and the boats that have to lift both. Marines progress by
learning loadout cards (`progression-nouns.md`); machines progress by learning
**patterns** and fitting them one machine at a time. The two ladders share access
tiers and sources but not their unit of learning, because the player fits a mech
and never fits a marine.

Read `mechs-nouns.md` (chassis, sockets, Mech Lab, flux), `convoy-nouns.md`
(vehicles) and `air-nouns.md` (boats and fittings) before changing a story below.

## The shape of the ladder

1. **A stock frame is where every machine starts.** Each chassis ships with an
   industrial engine, factory plating and a factory vent array. Those are weaker than
   what the machine could be; today's pre-nerf numbers are roughly what a
   well-refit frame reaches, not what a fresh one has.
2. **A pattern is learned, then built.** Learning a pattern gives the company the
   right to fabricate that component, internal, chassis or vehicle forever.
   Building one still costs credits, a civilized port, a working berth and time, and the result
   is finite stock that can be lost.
3. **A fit must fit.** The player chooses what goes on each individual machine,
   bounded by socket footprint and by the frame's engine budget. Better equipment
   is larger or heavier, so the best of everything never fits on one frame.
4. **Weight class is the ladder.** Hound is the light mech, Warden the medium,
   Bulwark the heavy (MechWarrior's classes). A company starts with the light
   chassis and its two starter weapons only, and earns medium and heavy patterns
   through Advanced and Prestige access. Vehicles follow the same classes.
5. **A machine has to be lifted.** Every payload has a lift class, every carrier
   a lift rating. Heavier machines need better lift, and lift is itself an
   upgrade the company earns.

## Vocabulary

- **Stock frame.** The factory internals every chassis carries when fabricated or
  recovered: industrial engine, factory plating, factory vent array. It is a fitting
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
- **Internal.** A non-weapon fitting that changes the frame: engine, plating,
  capacitor bank, vent array, loader. The engine lives in the CORE socket and the rest share
  the auxiliary compartment (`mechs-nouns.md`, the auxiliary compartment).
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
  it never uses a mech chassis, socket or flux model.
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
  budget; a large capacitor bank takes the utility slot a faster loader wanted; an
  uprated engine costs more and runs hotter. Nothing multiplies structure,
  armor capacity and armor rating together (`mechs-nouns.md`, internal).
- **Chassis identity survives every fit.** A fully refit Hound is a better Hound,
  not a cheap Bulwark. Each chassis keeps its weapon bands and its weakness.
- **The fitting is frozen at launch.** Campaign validates and stores ids; the
  deployment spec carries them; battle owns flux, ammunition and damage and never
  reaches back into the campaign (`mechs-nouns.md`).
- **A payload flies in a real carrier.** Heavy drops move to company boats and
  committed ships, so a carrier lost is a carrier the company no longer has. A
  payload heavier than every available carrier's rating is not offered, and the
  briefing says why.
- **Balance is not parity** (owner, 2026-10-19). A more expensive or higher-tier
  machine should wreck a cheaper one: a Bulwark or a screened tank crushes a Hound
  or a stock tank. Read an `armorMatchup` row against the two machines' bills and
  tiers, not against an even split. A near-even row between unequal machines is
  the finding to flag, and a balance pass never retunes toward parity. A cheap
  machine beats a dear one only by being bought for it (an anti-armor fit against
  a stock tank) or by being used well (a flank), and a mechanic that narrows a gap
  cost or tier says should be wide is called out where it is measured.
- **Starter content stays useful.** the Hound's nose chaingun and SRM-5 on a stock
  Hound remain a fit worth taking into the first operations. The spare rack is
  the fragmentation SRM-5, and a new company knows both SRM-5 warheads.

## Stock frame (shipped)

All mechs were cut to the stock frame on 2026-10-16, enemy and employer mechs
included, on the three axes later internals will buy back:

| | Armor capacity | Move speed | Dissipation |
| --- | --- | --- | --- |
| Bulwark | 950 → 710 | 1.15 → 1.05 | 8 → 6 /s |
| Hound | 500 → 375 | 1.70 → 1.55 | 8 → 6 /s |
| Warden | 400 → 300 | 1.45 → 1.32 | 8 → 6 /s |

Structure, armor rating, flux capacity and every weapon are unchanged, so
durability order (Bulwark, Hound, Warden) and each chassis's weapon bands hold.

## Internals (shipped)

Plating restores capacity, engines restore speed, capacitor banks and vent arrays restore
dissipation, each along the chassis's own band (`mechs-nouns.md`, internal and engine
budget). The ladder, tuned on headless readings (below):

| Role | Common (stock) | Advanced | Prestige |
| --- | --- | --- | --- |
| Engine (CORE) | Industrial, output x1.00, 2x1 | Uprated, x1.25, half the speed band, 2x2, mass 1 | Military, x1.50, full band, 2x2, mass 3, -0.5 dissipation |
| Plating (frame) | Factory | Composite: half the armor band, 1 plating unit | Heavy: full band, 2.5 units |
| Capacitor bank (compartment) | Factory: 100 flux, 6/s, 2x1 | Double: 120, 7.5/s, 2x1, mass 3 | Military: 120, 8/s, 2x2, mass 5 |
| Vent array (compartment, stacks) | none | Auxiliary: +1/s, 2x1, mass 2 | — |
| Loader (compartment) | Standard: SRM 12 s, LRM 18 s, 2x1 | Accelerated: 9 s / 13.5 s, 2x1, mass 1 | — |

Rated output is Hound 20, Warden 39, Bulwark 50 (stock weapons weigh 16, 39 and
44; the Bulwark rose from 46 when its twin arm gun became a single gun on
each arm, two 2x2 guns weighing 4 more than the 2x3 twin); a plating unit is 3, 4 and 6. A full refit reaches the pre-cut armor and speed
and roughly the old dissipation: Bulwark 950 / 1.15 / 120 flux at 8.5/s, Warden 400 /
1.45 at 8/s, Hound 500 / 1.70 at 7/s. The trade is room: the Hound's two-cell
reserve cannot take the military capacitor bank and it has no utility socket, a faster loader
displaces the vent array, and a full plate and engine leave little output for the top
weapons.

**Why engines carry mass.** Every authored doll's CORE socket is 2x2, so an
engine's footprint never costs room; mass is the only price an engine can pay.
With both upgraded engines massless, the military engine cost nothing over the
uprated one and the best of everything fitted on the Hound (28.5 of 30) and the
Bulwark (68 of 69), breaking *tier costs room*. At mass 1 and 3 it fits on no
chassis (Hound 31.5 / 30, Warden 62 / 54, Bulwark 71 / 69), while a full
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
| Warden | 18 / 25 | 21 / 37 | 21 / 37 |

The Warden rows were read while a planted long-range support mech still
turned its back on a close threat (`mechs-nouns.md`, Long-Range Support). Now
that it keeps the threat in its arc, it lasts 29 s stock and 43 s refit against
rifles. The refit now sits just under a stock Hound's 44 s, so take more seeds
before making a frame call there.

Bulwark > Hound > Warden holds at every equal tier, under both fires and both
damage models, and a refit Hound stays far below a stock Bulwark. A stock Hound
keeps about three quarters of its pre-cut endurance and 72-82% of its damage
output, so the starter fit still earns its gantry. A full refit restores the
Bulwark and Warden to pre-cut endurance and the Hound to 0.85-0.97 of it, inside
a seed spread of ±10-20 s — use eight seeds for any call near 10%.

**The Hound's dissipation ceiling stays below the old 8/s.** Its weapons draw about
43 flux/s against 6-8/s of dissipation, so sustained fire scales almost linearly
with dissipation (a stock Hound firing for 60 s: 971 damage at 6/s, 1080 at 7/s,
1180 at 8/s). The uprated-engine Hound (composite plate, double capacitor bank, 120 flux at
7.5/s, 23 of 25 mass) already matches pre-cut damage per minute; the military
refit gives up 7-18% of that for full armor and speed. That trade is the one the
laws ask for, so the ceiling is 7.5/s on the uprated engine and 7/s on the
military one.

**The military capacitor bank holds 120, and its edge is dissipation.** The AI fires a weapon
only while the reservoir stays under 90%, locks out at 75% and releases at 40%,
and it tries the arms before the shoulders. So whether a released Bulwark fires
an SRM-15 salvo (42 flux) before its chainguns' next cycle (30 flux every
1.05 s) depends on whether the reservoir exceeds 2 x (72 - 1.05 x dissipation),
about 126 at 8.5/s. Above that line each release goes chaingun plus SRM, about
5% more damage against a mech. Below it, it goes chaingun plus chaingun, the
same mix the pre-cut frame fires. Readings are flat on either side of the line,
which is why an eight-seed study of 130 against 140 looked non-monotonic. At 140
a full-refit Bulwark out-gunned its pre-cut frame by 16-18%; at 120 it is 12-13%
over, at equal endurance (192 seeds, standard error about 1%). 120 stays under
the line for any dissipation up to 11.4/s, and it is the floor below which the
A prestige capacitor bank would hold less than the double bank. The remaining 12% is the
Prestige premium: dissipation 8.5 against the pre-cut 8.0, and a 120 reservoir
against 100. It still beats the double-bank refit by about 9%. Cutting its
dissipation to 7.5 would make it buy nothing, and a harsher military-engine penalty
would also hit the Hound.

**The Warden is rated 39.** The LRM-15 is long-range support's Advanced rack,
as the SRM-15 is the Bulwark's. At 34 the most fragile chassis had to give up its
double capacitor bank or its heavy plate to carry one. At 36 the factory heavy cannon,
LRM-5 and LRM-15 fit a full frame (400 armor, 8/s) with no headroom, so the tier
still costs room. The heavy cannon then gained 3 of build mass (below), and the
rating rose by exactly that, so the Warden's own fit is where it was: the stock
fit fills the industrial engine, and the full frame fits the military one with
1.5 to spare, short of the vent array's 2. The best of everything still does not fit,
and a refit Warden still out-plates no Hound.

## The heavy cannon is equipment, not the Warden's gun

The heavy cannon is the Warden's factory arm, but its 3x2 ballistic footprint
fits any arms socket that takes 3x2, so it is balanced by what carrying it
costs, never by the fragility of the chassis that ships with it. Today that is
the Warden alone. The Hound's 2x2 nose cannot take it at any quarter-turn, and
since the Bulwark's one arm socket became a 2x2 socket on each arm (each arm
carries one single gun, not a twin), neither can the Bulwark; it is also a
centreline gun, which a side arm never takes. The arm-mount readings below
predate that split. Its contact hit (100 on the body struck, over a 45
splash) was tuned against mech armor and is not a lever here.

**A Warden dying in about 11 s to one heavy cannon in the open is accepted**
(owner, 2026-10-18). The Warden is long-range support, not a front-liner: it is
meant to be fragile under direct fire, and a Warden mirror fight is short
(12-13 s in `armorMatchup`, both before and after the changes below).

**The cannon cost its carrier nothing.** It weighed 14, two more than the
factory chaingun, and a full refit Bulwark has exactly 2 to spare, so the best
Bulwark frame carried it at no loss. It also runs cool: 26 flux per 2.5 s round
is 10 flux/s, against the chaingun's 29. A cool arm leaves the reservoir to the
SRM-15s, which did 60-78% of a cannon Bulwark's damage at the 17-18-cell range
the duel band holds. So a cannon on a **stock** Bulwark beat a **fully refit**
chaingun Bulwark 70% of the time: an Advanced weapon outweighing a whole internal
refit.

**What it costs now:**

- **Build mass 3** (`MechFrameBudget`; mass 17). A full refit Bulwark with the
  cannon and two SRM-15s gives up its vent array. The Warden's rating took the same
  3, so the Warden pays nothing for its own gun. Mass alone did not move a fight:
  at +3 and at +6 (vent array and capacitor-bank tier given up) the refit cannon Bulwark still
  beat a refit chaingun Bulwark 83-88%. Every authored doll's internals are cheap
  next to the reservoir the cannon left free, which is why flux was needed too.
  +6 was also rejected because the Warden's rating would have to rise by 6, and
  the military engine would then hand it a free vent array.
- **Flux 34 per round** (was 26; 13.6 flux/s at its cycle). The cannon now
  competes with the SRM-15s for the reservoir. On a stock frame (100 flux, 6/s)
  that is the cost that bites; on a 120 reservoir at 7.5/s less so. At 42 the
  stock-frame cannon Bulwark lost to everything but a stock chaingun, a weapon
  not worth carrying; 34 is where it trades even with the chaingun refit's
  frame. A short Warden fight lives off its reservoir, so flux barely touches
  the Warden (mirror 13 s either way).
- **Range stays 26**, already the shortest mech arm (chaingun 30, pulse laser 32,
  bastion 34). Cycle time is unchanged.

Readings (`armorMatchup`, explicit fits, open / hull-down / street, eight seeds,
both roles; win share, and mean open-ground seconds from first hit). Controls
are `-Dbattle.mech.heavyCannonBuildMass=0 -Dbattle.mech.heavyCannonFlux=26`.

| Fight | Before | After |
| --- | --- | --- |
| Cannon Bulwark (stock frame) vs refit chaingun Bulwark | 0.70, 69 s | 0.31, 83 s |
| Cannon + SRM-15 + SRM-5 (stock frame) vs refit chaingun Bulwark | 0.48 | 0.21 |
| Refit cannon vs refit chaingun Bulwark | 0.90, 56 s | 0.78, 71 s |
| Refit cannon vs refit bastion Bulwark | 0.48 | 0.32 |
| Refit cannon vs refit pulse-laser Bulwark | 0.73 | 0.73 |
| Refit cannon vs stock chaingun Bulwark | 1.00, 44 s | 1.00, 59 s |
| Refit cannon Bulwark vs breakthrough / heavy-gun tank | 0.00 / 0.00 | 0.00 / 0.01 |
| Stock / refit Warden vs refit cannon Bulwark | 0.06 / 0.04 | 0.06 / 0.06 |
| Warden mirror, stock and refit | 13 s / 13 s | 13 s / 13 s |
| Overall across the eleven fits: refit cannon / refit bastion Bulwark | 0.67 / 0.65 | 0.65 / 0.69 |
| Overall: stock-frame cannon Bulwark / refit chaingun Bulwark | 0.44 / 0.39 | 0.35 / 0.47 |

The cannon is still a good Bulwark arm: refit around it, it beats the factory
chaingun refit and the Prestige pulse laser. It is no longer the best one, it
costs a vent array and a fuller reservoir to carry, and on a stock frame it no longer
outweighs a refit. The heavy-cannon Warden is not a counter to anything heavy:
it loses to every refit Bulwark and to both tanks, and beats only a stock
chaingun Bulwark (stock Warden 0.39, refit 0.77; was 0.59 and 0.88).

Still open from these readings:

- **Every tank beat every Bulwark fit**; retuned below (*A tank against the
  heavy mech*).
- **The chaingun Bulwark is the anti-infantry Bulwark** (owner, 2026-10-18).
  Its armor-duel share is low by design, so it is judged against infantry,
  never by how it fares against machines. Its anti-armor answer is its SRM
  shoulders, as LRMs are the Warden's. What these readings did expose is that
  the chaingun's flux starved those shoulders. That is now fixed by the firing
  order, not by flux (next section).

The bastion and the pulse laser are balanced in the next section.

## The Bulwark's other arms are equipment too

The same readings showed two more of the Bulwark's 3x2 arms out of order. The
bastion autocannons had the cannon's old problem: mass 14 and about 9 flux/s,
so the best refit Bulwark arm cost nothing to carry, and on a **stock** frame it
beat a refit chaingun Bulwark 56% of the time. The Prestige pulse laser, at 38
flux per 2.1 s, lost to both Advanced arms on the same frame, 0.00 against the
bastion. They were tuned in the cannon's order: mass first, then
flux. Range and cycle were not needed. Each lever was read alone against the
others' authored values before they were combined.

- **Bastion: build mass 3** (mass 17), as the cannon's, for its feed. A full
  refit Bulwark carrying it gives up its vent array. Alone it barely moves a fight
  (refit bastion against refit cannon 0.58 to 0.46; stock frame against refit
  chaingun unchanged at 0.56, because a stock frame has the room anyway).
  The Warden's rating does not absorb it: the bastion is not its gun.
- **Bastion: flux 28** (was 24; 10.8 flux/s). This is the lever that bites on a
  stock frame. Alone it takes the stock-frame bastion against a refit chaingun
  from 0.56 to 0.29. At 32 the refit bastion fell behind the refit cannon and the
  pulse laser by a wide margin (0.31 against each), so 28 it is.
- **Pulse laser: flux 26** (was 38; 12.4 flux/s). The Prestige arm already pays
  in mass: at 16 it fills a full refit Bulwark's engine exactly (69 of 69). At 30
  it drew level with the cannon (0.42 each way) but still lost to the bastion
  (0.33 against 0.50). At 26 it edges both. A pulse laser on a stock frame loses
  to every refit Advanced arm (0.00-0.02), so a Prestige arm does not outweigh
  an internal refit either.

**The chaingun stays at 30, and flux is the wrong lever for its fault.** The
chaingun's 29 flux/s does starve the Bulwark's SRM-15s, its anti-armor role:
against machines the shoulders deal only 14-16% of a chaingun Bulwark's damage.
Flux 24 was tried. On a refit frame it lifts that share to 25%, but on a stock
frame it falls to 10%. A reservoir releases at 40% and admits a salvo only under
90%, so a 42-flux SRM after a chaingun burst needs a reservoir of
2 x (burst + 42): 132 at flux 24, beyond any stock frame. And the AI tries the
arms first, so the chaingun takes every release. Against ten rifles and an AMR
(`mech-frame-endurance`, 48 seeds) flux 24 also raised a stock chaingun
Bulwark's damage per minute by 26% (1136 to 1435) and a refit's by 15%. Every
neutral enemy Bulwark fights on a stock frame, so that would have been a quarter
more fire at marines while fixing nothing for the shoulders. The fault was the
firing order.

**Against a machine, the anti-armor mounts fire first.** An autonomous mech now
tries its mounts in order of armor efficiency against the target it is shooting,
when that target is a mech or a vehicle, and in slot order otherwise
(`mechs-nouns.md`, Flux reservoir). Against infantry nothing changes. A
chaingun, pulse laser or linear cannon now yields the reservoir to the SRM-15s;
a heavy cannon fires before them. With the anti-armor warheads (penetration 30,
Missile warheads below) a bastion now yields to them too, and a fragmentation
rack fires after every arm gun. The table was read on the single-warhead SRM
(penetration 14), when the bastion tied the racks; the Missile warheads readings
are taken with this rule on. Readings, against
`-Dbattle.mech.armorFireOrder=false` (the control reproduces the matrix below
fight for fight):

| Reading | Control | Rule |
| --- | --- | --- |
| SRM share of damage against machines: stock / refit chaingun Bulwark | 0.15 / 0.18 | 0.42 / 0.30 |
| Damage dealt per fight: stock / refit chaingun Bulwark | 433 / 780 | 513 / 810 |
| SRM share: stock / refit pulse-laser Bulwark | 0.07 / 0.22 | 0.32 / 0.24 |
| SRM share: heavy cannon, bastion, linear cannon Bulwarks | unchanged | unchanged |
| Overall win share: refit / stock chaingun Bulwark | 0.43 / 0.09 | 0.43 / 0.12 |
| Damage per minute against ten rifles and an AMR, every chassis and frame | — | identical |
| Stock chaingun Bulwark vs stock Hound (`armorMatchup`, 16 seeds) | 0.79 | 0.92 |
| Endurance under a hostile stock Bulwark: stock Bulwark / stock Hound / refit Hound | 127 / 56 / 74 s | 80 / 42 / 46 s |

The chaingun Bulwark's duel share hardly moves, as its role says it should not.
What moves is what it does to machines: its shoulders now do a third or more of
its damage to them. The cost lands on whatever a chaingun Bulwark shoots. Every
neutral enemy Bulwark carries one, and a planted mech under one now lasts about
a third less. A stock Hound, the starter machine, loses its first mount at 3 s
instead of 7 under the heavier opening salvo. A stock Hound already lost to a
stock Bulwark (0.06 before, 0.00 now), so the starter fit's place is unchanged:
it is for the first operations, not for facing heavy armor. The Hound's own fire
barely changes (SRM share 0.08), because it mostly fights beyond its SRM-5's
18-cell reach.

Readings (`armorMatchup`, fourteen explicit fits: the cannon section's eleven
plus a stock-frame bastion, a stock-frame pulse laser and a refit linear cannon;
open / hull-down / street, eight seeds, both roles). A share is the wins over
fights played in both roles; overall is the mean over the eleven fits the cannon
section used. The controls are `-Dbattle.mech.buildMass.bastion-autocannons=0
-Dbattle.mech.flux.bastion-autocannons=24 -Dbattle.mech.flux.dual-pulse-lasers=38`
(ids of the twin guns these readings were taken with; the single guns that
replaced them are `bastion-autocannon` and `pulse-laser`, at half each).
The control run reproduces the before column fight for fight. Any arm's flux,
cycle or build mass takes the same
`-Dbattle.mech.<flux|cycle|buildMass>.<id without component.mech->` switch.

| Fight | Before | After |
| --- | --- | --- |
| Bastion (stock frame) vs refit chaingun / refit cannon Bulwark | 0.56 / 0.04 | 0.29 / 0.04 |
| Refit bastion vs refit cannon / refit chaingun | 0.58 / 0.67 | 0.38 / 0.65 |
| Refit pulse laser vs refit bastion / refit cannon | 0.00 / 0.19 | 0.52 / 0.44 |
| Overall: refit pulse / cannon / bastion Bulwark | 0.53 / 0.61 / 0.62 | 0.61 / 0.61 / 0.55 |
| Overall: stock-frame bastion / pulse Bulwark | 0.39 / 0.20 | 0.31 / 0.37 |
| Flux-limited seconds per fight: refit bastion / pulse | 13 / 36 | 18 / 20 |
| Any Bulwark fit vs breakthrough / heavy-gun tank | 0.00 | 0.00 |

On a full refit the Prestige pulse laser now narrowly leads, and the two
Advanced guns follow, the cannon ahead of the bastion head to head. The
chaingun Bulwark reads as before (refit 0.43 overall), which is its intended
place in a fight between machines. Enemy Tri-Tachyon and Hegemony Bulwarks field
the pulse laser and the bastion, so their fire against marines moves too.
Mission evidence has not been re-read for that.

Still open:

- **The linear cannon is the weakest Advanced arm** (refit 0.34 overall before
  this change, 0.32 after; it beats only the stock chaingun frame and the
  Wardens clearly). Its trouble is not carriage. Its penetration of 8 against the
  Bulwark's 18 halves every round. And the duel band its SRM-15s pull it into
  keeps it at 17 cells, where those launchers latch the reservoir about 60 s of
  a 75 s fight. Read with the chaingun at 24, flux 14 moved it to 0.36. Flux 16 with the cycle halved to 1.4 s
  reached only 0.44, level with the chaingun refit, a Common arm. A weapon that needs
  its fire rate doubled is a damage question, not an equipment one, so it was
  left as authored.
- **Every tank beat every Bulwark fit**; see *A tank against the heavy mech*.

Direction, not yet a story: missile racks are the mech's anti-armor weapons, and
each likely wants an anti-armor and a fragmentation variant, so a rack fitted
for infantry work stops competing with the arm built for it.

## A tank against the heavy mech

Both tanks and the Bulwark are Heavy weight, Prestige and Heavy lift, so they
compete for one gantry and one carrier. The identity the owner set
(2026-10-18) is a split, not parity: **a tank wins in the open at range**,
where its gun and plate shine; **a Bulwark wins where flexibility counts**:
broken ground and streets, close range, and with its missiles. Overall the
target is roughly even, with clear splits by ground.

**Why every tank beat every Bulwark (2 of 480 fights to the mech).** The tank
was better on every axis that decides a duel, and the mech's flux stacked on top:

- *Plate against penetration.* A tank's rating was 36 (heavy gun 40) against
  mech arms of penetration 5 (chaingun) to 18 (heavy cannon contact), so mech
  rounds stripped its armor at 22-55% of their damage. The tank's contact hit
  (penetration 28, heavy gun 40) meets the Bulwark's 18 at full damage.
- *Pool.* 1,950 structure and armor capacity on both tanks, against 1,260 on a
  stock Bulwark and 1,500 refit. A vehicle has one pool and no sections to
  lose; it dies only when the whole pool is gone.
- *The gun against a mech section.* A twin-cannon salvo landed two 150 contact
  hits every 5.25 s, and the heavy gun 260 every 7 s: about 45 damage a second
  on a Bulwark. Bulwark against Bulwark trades about 20.
- *Flux.* A tank has none. A Bulwark's sustained fire is its dissipation, about
  8.5 flux/s, so against armor it delivered 2-9 damage a second in the open and
  7-14 in streets, flux-limited for most of a street fight.
- *Reach and kiting.* The tank guns reach 36 and 44 against mech arms of 26-34
  and SRMs of 18, so the duel band (`mechs-nouns.md`, Duel range) holds the
  tank just outside the mech's reach. It backed off at half its top speed, 1.55
  and 1.3 cells/s, faster than a Bulwark walks (1.05), so the mech could never
  close. In the open an attacking Bulwark fired 10 rounds a fight.
- *Cover.* It is the mech's alone. A hull takes no terrain cover, while the
  section rows hide a Bulwark's legs behind a berm. Cover only helps a mech that
  can shoot back, though, so it did not matter while the tank stood out of
  reach. Turret traverse (55 and 40 degrees/s) never mattered in a duel either.

**What changed.**

| | Breakthrough | Heavy gun |
| --- | --- | --- |
| Structure / armor capacity | 1100 / 850 → 750 / 550 | 1050 / 900 → 700 / 600 |
| Armor rating | 36 → 18 | 40 → 20 |
| Contact hit (penetration unchanged) | 150 → 75 | 260 → 135 |
| Gun reach | 36 | 44 → 40 |
| Reverse speed | 1.55 → 0.93 cells/s | 1.3 → 0.78 cells/s |

- **Rating 18** is the Bulwark's own rating. The tank keeps its plate by bulk,
  not hardness, so a mech's missiles (SRM 14, LRM 16) and the heavy cannon bite
  it at 80-100% while the chaingun (5) still chips it at 35%. That is the
  Bulwark's division of labour: the chaingun is for infantry, the shoulders for
  armor. Rating 22 was tried: LRM fits barely moved (0.91 → 0.85) and the core
  fits fell from 0.43 to 0.39 (lever readings here predate the arm balance).
- **Pool 1,300**, between a stock Bulwark's 1,260 and a refit one's 1,500, so
  neither side out-lasts the other by bulk alone. Armor per credit falls to
  1.20x (breakthrough) and 1.09x (heavy gun) the Bulwark's, still above 1 as the
  vehicle rule asks, and per lift a tank still beats a stock Bulwark.
- **Contact hit halved.** The tank still out-damages a flux-bound mech (about
  24 damage a second on a Bulwark against the mech's 12-30), but no longer
  takes half a Bulwark in a salvo. Tank against tank stays a quick fight: 52-55 s
  from first hit, against 45-47 before.
- **Reverse at 0.3 of top speed** (was 0.5 for every hull; `BicycleBody` now
  takes it per hull, and the other hulls keep half). A tracked hull backing up is
  slower than a Bulwark walking, so a Bulwark that closes can catch it. This is
  the lever that gives the open ground its biggest move (with the stat changes
  above, open share 0.12 at half speed and 0.31 at 0.3); at 0.35 the breakthrough tank still backed up at 1.085, just
  faster than the Bulwark's walk. `-Dbattle.vehicle.tankReverseFraction=.5` is the
  control.
- **Heavy gun reach 40**, the LRM's reach. At 44 it out-reached every mech
  weapon by ten cells, and broken ground could not hide a Bulwark from it (mech
  hull-down share 0.07).

**Readings** (`armorMatchup`, explicit fits, open / hull-down / street, eight
seeds, both roles; Bulwark win share, a timeout read by integrity left). Both
columns are on the same tree, with the Bulwark arms balanced as equipment, the
anti-armor fire order and the missile warheads (*Missile warheads*, below) in
place; *before* is that tree with the tanks as they were. Fits: stock;
refit chaingun; refit heavy cannon, bastion and pulse arms.

| | Before | After |
| --- | --- | --- |
| All five fits | 0.01 | 0.54 |
| Four refit fits | 0.01 | 0.64 |
| By ground: open / hull-down / street | 0.00 / 0.00 / 0.03 | 0.37 / 0.45 / 0.81 |
| Hull-down with the Bulwark holding the berm | 0.00 | 0.59 |
| Against breakthrough / heavy gun | 0.00 / 0.02 | 0.56 / 0.53 |
| Fight range under 20 / 20-30 / over 30 cells | 0.04 / 0.00 / 0.00 | 0.78 / 0.53 / 0.08 |
| Stock / refit chaingun Bulwark | 0.00 / 0.01 | 0.18 / 0.48 |
| Refit heavy cannon / bastion / pulse | 0.01 / 0.02 / 0.01 | 0.57 / 0.79 / 0.70 |
| Refit Bulwark with two AT LRM-15 / bastion and two AT LRM-15 | 0.12 / 0.15 | 0.89 / 0.91 |
| Hound, stock and refit | 0.00 | 0.00 / 0.01 |
| Warden (Frag LRM-5s), stock and refit | 0.00 | 0.00 |
| Tank against tank, seconds from first hit | 45-47 | 52-55 |
| Tank against a Bulwark, seconds from first hit | 32-39 | 52-62 |

The split holds by range: beyond 30 cells the tank wins 92% of the time, and
under 20 the mech wins 78%. Read by fit and ground, the heavy cannon is the
cleanest example of the identity: 0.31 in the open, 0.47 hull-down, 0.94 in the
street; the factory fit goes 0.03 / 0.06 / 0.44. The rows that do not fit the
identity yet are owned elsewhere, and are reported here rather than tuned:

- **LRM fits beat tanks everywhere, the open included** (two AT LRM-15s 0.89).
  The racks match the heavy gun's reach, out-reach the breakthrough tank, and
  their contact warhead (penetration 30) meets plate it defeats at full damage.
  That is the missiles' anti-armor role, but stronger than "the tank wins in the
  open" allows. The rack-side levers were measured and none of them fixes it;
  the screened tank's top-attack active protection answers it (*The AT LRM against a tank*, below).
- **The energy and autocannon arms win in the open** (bastion 0.72, pulse 0.56
  there). Their reach (34, 32) and penetration (14, 9) now meet a plate they
  bite, and the breakthrough tank stands off only when it out-reaches its target
  by 4 cells. If the open is to stay the tank's against these arms, the next
  lever is the breakthrough tank's reach, not its plate.
- **Streets lean hard to the mech (0.81)**, more than the target's spread. The
  street holds fights at 17 cells, inside the AT SRM's reach, so the warheads did
  the work there. A tank in a street is the wrong tool, so this is accepted for now.
- The heavy-gun tank still holds hull-down ground (Bulwark 0.24), where the map's
  ruins leave forty-cell sight lines open.

**A tank that loses its gun.** The identity says a Bulwark beats a tank that has
lost its main gun. That case exists now: a tank's turret is a section of its
hull (`convoy-nouns.md`, the turret section). It takes a fifth of the hull's
hits and holds a quarter of its pools, and at zero the gun is gone while the
hull lives on unarmed. The readings below use the same matchups as the table
above (five fits, three grounds, eight seeds, both roles), first with
`-Dbattle.vehicle.turretDamage=false` and then with the rule.

| | Without gun loss | With gun loss |
| --- | --- | --- |
| All five fits | 0.54 | 0.59 |
| By ground: open / hull-down / street | 0.37 / 0.45 / 0.81 | 0.45 / 0.49 / 0.83 |
| Stock / refit chaingun Bulwark | 0.18 / 0.48 | 0.23 / 0.51 |
| Refit heavy cannon / bastion / pulse | 0.57 / 0.79 / 0.70 | 0.67 / 0.82 / 0.72 |
| Against breakthrough / heavy gun | 0.56 / 0.53 | 0.61 / 0.57 |
| Fight range under 20 / 20-30 / over 30 cells | 0.79 / 0.53 / 0.08 | 0.81 / 0.59 / 0.13 |
| Fights in which the tank lost its gun | — | 49 of 480, all won by the Bulwark |
| Mean seconds from first hit | 57 | 54 |

- **The gun goes in about one fight in ten, and only at mech range.** All 49
  losses came in fights settled under 30 cells. None came beyond 30, where the
  tank's stand-off keeps most mech rounds off it. A tank that loses its gun
  has never won.
- **About half of the +0.05 is not gun loss.** On the mech section law the
  turret's unspent pool dies with the hull, so a tank dies a little sooner: 54 s
  against 57. With a pool share well above the hit share (0.2 / 0.3) the gun
  went in only 14 fights, and the overall share still rose to 0.59. That cost is
  the section model's, the same one that puts a mech's kill time at 0.88 of its
  whole-body control. It is accepted rather than offset by a hidden pool bonus.
- **Earlier shares took the open from the tank.** At a quarter of the hits on
  0.18 of the pools, the tank lost its gun in 58% of fights and the Bulwark won
  0.64 in the open. Other settings tried (Bulwark share, open share, fights with
  the gun lost): 0.2 / 0.2 gave 0.61, 0.49, 29%. 0.15 / 0.2 gave 0.58, 0.43,
  12%. 0.25 / 0.3 gave 0.59, 0.46, 12%. Above 0.15 hits, the pool share is the
  lever for how often the gun goes.
- The open is still the tank's (0.45). If the gun-loss cost should be won back
  there, the breakthrough tank's reach remains the lever named above, not its
  pool.
- **A tank that has lost its gun withdraws** (`convoy-nouns.md`, the turret
  section): out of every threat's reach, then it holds. Read against
  `-Dbattle.vehicle.disarmedWithdraw=false` (refit chaingun and AT LRM-15
  Bulwarks against both tanks, three grounds, eight seeds, both roles), the
  Bulwark's share moves only where a gun went, 0.63 to 0.61 over 192 fights.
  An attacking tank with no gun now usually escapes: 4 of the 25 gun-loss fights
  turned from Bulwark wins into stalemates, and the tank kept 6-39% of its
  integrity. A defending tank runs and lives longer (up to 2410 against 1750
  ticks) but still dies, because the attacking Bulwark hunts it. Among tanks the
  same holds: four hull-down heavy-gun fights against the breakthrough tank
  ended with the gunless defender alive out of reach.
- **Withdrawing nose on was measured and rejected** (`convoy-nouns.md`, what
  presents the front). Backing out of the nearest threat's reach before routing
  keeps the front plate toward it at 0.3 of top speed. Over the same three
  tanks against both Bulwark fits, three grounds, eight seeds, an attacking tank
  that lost its gun escaped 2 of 28 such fights backing, against 8 of 28 driving
  its route; arcs off, 3 of 33 against 5. A defending tank that lost its gun died
  in all 23 either way, 5 seconds sooner backing. Three times the time in reach
  costs more than the rear plate saves.

**The AT LRM against a tank** (2026-10-19). A refit Bulwark with two AT
LRM-15s beats the tanks 0.81: 0.78 against the breakthrough tank, 0.83 against
the heavy gun, and 1.00 against the breakthrough tank in the open and hull-down.
Bastion arms with the same racks take that to 0.94. The figures are
`armorMatchup`, three grounds, eight seeds, both roles, with a timeout read by
integrity. The same fit also beats a refit chaingun Bulwark 0.77, so it is the
strongest armor dueller there is, and the tank rows reflect that rather than
being a tank-only gap.

- **Why.** Penetration does not separate the targets: a Bulwark and a tank
  both rate 18, and their pools are 1260 and 1300, so a warhead treats them
  alike. Fire rate does: one AT LRM-15 delivers 5 x 95 contact every 9 s, about
  1.7 times a tank's whole main gun, and the Bulwark carries two. An indirect
  round rolls its hit at launch, with no range falloff and nothing for the
  target's size or motion, and lands about 80% of rolls on a hull. It opens at
  40 cells, outside the breakthrough tank's 36. So the tank dies in 11-18 s
  having fired 6-8 rounds. Against a chaingun Bulwark the tank's one edge is
  stand-off reach, and the rack's reach takes that away. The report's
  damage-by-weapon column had shown the racks at 15% of the damage, weighting a
  hit by its 15-point blast. It now counts a contact warhead at its payload.
- **What did not work.** These are fit-against-tanks / fit-against-the-refit-
  chaingun-Bulwark readings, from 0.81 / 0.77:

  | Lever (AT LRM-15 only unless noted) | Against tanks | Against refit Bulwark |
  | --- | --- | --- |
  | Two salvos loaded instead of three | 0.81 | 0.73 |
  | Contact damage 70 / 55 (shared with the AT LRM-5) | 0.80 / 0.77 | 0.60 / 0.56 |
  | Scatter 2.5 / 3.5 | 0.79 / 0.80 | 0.75 / 0.75 |
  | Cycle 13 s / 18 s | 0.81 / 0.80 | 0.75 / 0.73 |
  | Flux per salvo 60 / 70 / 80 / 90 | 0.82 / 0.80 / 0.74 / 0.77 | 0.69 / 0.79 / 0.56 / 0.42 |
  | Reach 34 / 32 (shared) | 0.89 / 0.73 | 0.67 / 0.67 |

  The magazine levers miss because the refit's replenisher reloads and the
  first two volleys already kill. Every damage-side lever moves the mech rows
  two to four times as far as the tank rows. Reach is erratic, and at 32 the
  bastion fit beats the breakthrough tank 1.00. The tank rows are
  overdetermined: halving the racks still wins.
- **The answer is a rung, not a stock buff** (owner, 2026-10-19). A cheap tank
  losing to a dedicated anti-armor fit is by design; the counter is something a
  company buys. The **screened tank** is the breakthrough tank with active
  protection (`convoy-nouns.md`, the screened tank carries active protection), a
  Prestige sidegrade on the tank line at Heavy lift. Its bill is the breakthrough
  tank's plus machinery and rare metals for the launchers and their sensor
  (125 supplies, 115 machinery, 240 metals, 44 rare metals), and the launcher
  boxes cost it a little speed (2.9 against 3.1). Its system has reach 8, a
  0.1 s cycle, a 0.7 roll and 30 engagements, and takes only arcing rounds
  coming down on its own hull. It stays a rung rather than a fitting: one fixed
  system with one tuning gives the player the same choice a fitting would (pay
  for it or not), without a socket, a save field or a balance surface per hull,
  which is what *Vehicles: variants and kits* keeps out of the kit rack. The stock tanks carry
  none. Readings are Bulwark-side shares, three grounds, eight seeds, both roles,
  a timeout read by integrity; the breakthrough tank is the screened tank's
  control:

  | Fit | Breakthrough | Heavy gun | Screened |
  | --- | --- | --- | --- |
  | Refit, two AT LRM-15s | 0.78 | 0.83 | 0.66 (open 0.81, hull-down 0.88, street 0.28) |
  | Bastion arms, two AT LRM-15s | 0.89 | 0.98 | 0.82 |
  | Stock Bulwark | 0.21 | 0.25 | 0.21 (street 0.50) |
  | Refit chaingun Bulwark | 0.50 | 0.52 | 0.48 |
  | Hound / Warden, stock and refit | 0.00-0.04 | 0.00-0.02 | 0.00 |

  It stops about 10 rounds a fight against the LRM fit. The stock Bulwark's
  street rows do not move, because its SRMs fly flat. The bastion fit stays high
  on its arms (bastion alone 0.79): that is the reach-34 arm's case above, not
  the rack's. Against other tanks the screened tank duels as the breakthrough
  tank does. Its sheet is the breakthrough hull with an ImageGen turret carrying the
  fit where it can be seen: a launcher box on each rear flank, a sensor panel
  on each cheek (`art-source/vehicles/tank-screened`).
- **Why top-attack only.** The first prototype took any interceptable round
  near the hull, and it taxed the SRM Bulwarks harder than the LRM fit. At the
  same 30-round setting the stock Bulwark fell 0.23 to 0.08 (street 0.50 to
  0.22) and the refit 0.51 to 0.35, against the LRM fit's 0.81 to 0.69. A close
  SRM salvo is as easy to catch as a falling LRM. Settings tried for the
  any-round rule, LRM fit / stock / refit: reach 8, cycle 0.1, roll 0.6 and 10
  rounds gave 0.77 / 0.11 / 0.48; with 20 rounds, 0.73 / 0.08 / 0.42. A
  reach-4, 0.3 s mount engaged about 3 rounds a fight and changed nothing.

## Facing: front, side and rear plate

A hull's plate depends on the arc a hit arrives on (`convoy-nouns.md`, a hull's
plate depends on the arc), so a machine caught off its front pays for it. The
shared hull profile is front 1.0 rating / 1.1 plate, side 0.9 / 0.9, rear
0.75 / 0.7. The tank line is 1.0 / 1.15, 1.0 / 0.95 and 0.6 / 0.6. The dozer is
the tank line without the glacis bonus, and the SRM technical the hull profile
without its front bonus. `-Dbattle.vehicle.armorArcs=false` is the control.

- **The harness set hulls down side-on, and fixing that moved the street rows.**
  `armorMatchup` teleported attacking hulls to 0° and defending hulls to 180°.
  In the battle's frame (0° is +Y) that is north and south, across a fight that
  runs west to east. It now sets both sides down facing each other, as
  production drops do. Arcs then move the head-on rows hardly at all. The
  re-baseline is the facing alone: on the old facing with arcs off, the readings
  above come back exactly (LRM refit 0.78 / 0.66). On the street a tank set down
  side-on fumbled its approach and closed to about 21 cells, where its twin
  cannon wins. Pointed down the street, it stops at its duel band at about 31.6
  cells, inside the LRM's reach. Bulwark-side shares, three grounds, eight seeds,
  both roles, timeout read by integrity, arcs on / off:

  | Fit | Breakthrough | Heavy gun | Screened |
  | --- | --- | --- | --- |
  | Refit, two AT LRM-15s | 1.00 / 0.98 | 0.94 / 0.92 | 0.85 / 0.85 |
  | Bastion arms, two AT LRM-15s | 1.00 / 1.00 | 1.00 / 1.00 | 0.94 / 0.94 |
  | Stock Bulwark | 0.17 / 0.15 | 0.27 / 0.29 | 0.17 / 0.19 |
  | Refit chaingun Bulwark | 0.54 / 0.54 | 0.62 / 0.62 | 0.54 / 0.56 |

  On the old facing, arcs moved the same rows by 0.05 or less (LRM refit 0.83 /
  0.65 against 0.78 / 0.66). The screened tank stays the rung: against the LRM
  fit it holds 0.15 where a breakthrough tank holds none. Its margin is smaller
  than the old street rows made it look.
- **Caught off its front.** `-PdefenderFacing=90|180` turns the defenders from
  facing the attackers when they are set down. Attacker win share, eight seeds,
  three grounds, arcs off / on:

  | Attacker vs tank | Facing the attacker | Side-on | Rear on |
  | --- | --- | --- | --- |
  | Escort APC vs breakthrough | 0.04 / 0.08 | 0.12 / 0.42 | 0.08 / 0.46 |
  | Escort APC vs screened | 0.12 / 0.08 | 0.08 / 0.38 | 0.12 / 0.04 |
  | Breakthrough vs breakthrough | 0.38 / 0.42 | 0.54 / 0.58 | 0.62 / 0.71 |
  | Refit Bulwark vs breakthrough | 0.50 / 0.46 | 0.33 / 0.42 | 0.58 / 0.54 |
  | Stock Bulwark vs screened | 0.00 / 0.00 | 0.04 / 0.04 | 0.17 / 0.25 |

  These are the retuned profiles (below). The retune moved tank-on-tank side-on
  (0.62 to 0.58; against the screened tank 0.58 to 0.54) and two rear-on mech
  rows by one fight in eight; the escort APC's flank gains are untouched. Head-on,
  the tanks beat the escort APC 0.88-0.96. The rows move where the defender keeps
  its facing while it is shot. Against a mech it does not keep it long: a
  duelling tank drives to its band nose first and backs off a closing mech
  nose-on, which puts its front back on the attacker, so the mech rows hardly
  move. The escort APC rows were a tank that never turned; *Keeping the front*
  below is the driving that answers them.
- **The ladder, and the two rows retuned.** Over the default ladder (4 seeds,
  every pairing both ways, facing fixed in both arms), the first profiles moved
  four pairings past noise, confirmed at 8 seeds. Read against bill and tier
  (*Balance is not parity*), two of them narrowed gaps that should stay wide, and
  the owner had them retuned with per-type profiles, keeping the flanking gains:
  - **The dozer beat its own line's cannon tanks on the street**, 0.12 to 0.62
    (heavy-gun 0.00 to 0.62). It was not the dozer's front: removing its glacis
    bonus, then thinning its own flanks, moved nothing, because every hit in that
    fight lands broadside both ways. The tank's 0.85 side plate let the dozer's
    penetration-4 MG out-trade the cannon in a long, close fight. A tank's flank
    is now 1.0 rating / 0.95 plate (0.9 still gave the dozer half its street
    fights), and the dozer is the tank line's profile without the glacis bonus.
    Street, dozer attacking, arcs on / off: breakthrough 2/8 / 1/8, heavy-gun
    0/8 / 0/8, screened 2/8 / 1/8.
  - **The SRM technical (38/27/72/6) took a third of its fights from the
    fire-support APC (52/40/115/11).** Its welded sheet lost the hull profile's
    front bonus, and it is back to 0/24, as with arcs off.
  - Running the right way and kept: the heavy APC against the militia technical
    goes 0.17 to 0.38.

  A dozer attacking a tank on open ground or hull-down wins with arcs off as well
  (1.00); that is the best armor per credit in the game (*Vehicles: variants, not
  fittings*) and not a facing finding.
- **Keeping the front** (`convoy-nouns.md`, what presents the front). A tank
  set down off its front used to lose that way for the whole fight. In band it
  never turned. When the APC closed inside its band floor, the retreat read the
  APC as just behind the beam (about 115° off the nose), drove away from it rear
  on, and stuck against the map edge. Two changes answer it. A hull holding in
  band turns onto a target more than 35° off its nose. The retreat backs with
  opposite lock out to 120° off the nose instead of only to the beam. Same table,
  same seeds, old driving / new, arcs on:

  | Attacker vs tank | Facing the attacker | Side-on | Rear on |
  | --- | --- | --- | --- |
  | Escort APC vs breakthrough | 0.08 / 0.08 | 0.42 / 0.12 | 0.46 / 0.08 |
  | Escort APC vs screened | 0.08 / 0.08 | 0.38 / 0.04 | 0.04 / 0.08 |
  | Breakthrough vs breakthrough | 0.42 / 0.42 | 0.58 / 0.50 | 0.71 / 0.71 |
  | Refit Bulwark vs breakthrough | 0.46 / 0.46 | 0.42 / 0.42 | 0.54 / 0.54 |
  | Stock Bulwark vs screened | 0.00 / 0.00 | 0.04 / 0.04 | 0.25 / 0.08 |

  Head-on, nothing moves. With arcs off the escort APC rows barely move (0.12 to
  0.12 side-on, 0.08 to 0.12 rear on), so the turn buys front plate, not a
  better fight. A flanked tank that turns holds the APC off as it does head-on.
  Flanking still pays where the tank cannot turn in time, and against a mech it
  never paid much.
- **The ladder, old driving / new** (4 seeds, every pairing both ways, on the
  retuned profiles): 181 of 207 pairings are identical, and so is every
  open-ground fight. 13 pairings move
  by 0.25 or more, on the street and hull-down grounds, nearly all of them
  between light hulls: the militia technical, the fire-support and escort APCs.
  Fights there turn into stalemates (street stalemates 216 to 227 of 828) or
  out of them, all seeds together, because the turn changes where a hull stands.
  The traced cause is older than the turn. A hull that holds in band without
  landing hits never moved: two hulls nose-on at 21 cells, in sight and in reach,
  sat out the fight. Sight is read cell centre to cell centre; the rounds fly
  from the turret, and the defender stood in the lee of the last building front,
  whose corner took them. That holding rule was the lever, not the turn.
- **A stalled hold moves** (`convoy-nouns.md`, what presents the front). A hull
  that has held in band for three volleys (at least ten seconds) without its
  target losing integrity drives to another in-band firing position, three cells
  or more away, with room to drive onto and a lane the turret's own line-of-fire
  test passes along the centre line and 0.6 cells either side. In the traced
  fight the escort APC crosses eight cells to a clear lane and finishes the
  militia technical (a 1,930-tick stalemate becomes a win at 1,854). Ladder,
  4 seeds, rule off / on: 586 of 621 pairings identical, and every one on open
  ground but one. 12 pairings move by 0.25 or more, all on the street but one
  hull-down row (Warden against the militia technical, one fight in four).
  Street fights left undecided (stalemate or timeout) go 227 to 141 of 828.
  Most of them are fights that never began: the escort APC against a Hound, a
  breakthrough tank or a screened tank timed out with no damage on either side,
  and now ends about as it does on open ground (Hound 0.00 to 1.00, both tanks
  0.00 to 0.50). Light hulls that stalemated now finish each other, 0.00 to
  1.00: the escort APC against the militia technical and the heavy,
  up-armored and fire-support APCs, the heavy APC against the fire-support APC,
  and the dozer against the militia technical. The facing
  table, 8 seeds, three grounds, rule off / on (the off arm is the table above
  exactly):

  | Attacker vs tank | Facing the attacker | Side-on | Rear on |
  | --- | --- | --- | --- |
  | Escort APC vs breakthrough | 0.08 / 0.17 | 0.12 / 0.12 | 0.08 / 0.08 |
  | Escort APC vs screened | 0.08 / 0.17 | 0.04 / 0.04 | 0.08 / 0.08 |
  | Breakthrough vs breakthrough | 0.42 / 0.42 | 0.50 / 0.50 | 0.71 / 0.71 |
  | Refit Bulwark vs breakthrough | 0.46 / 0.46 | 0.42 / 0.42 | 0.54 / 0.54 |
  | Stock Bulwark vs screened | 0.00 / 0.00 | 0.04 / 0.04 | 0.08 / 0.08 |

  Only the head-on escort APC rows move, and only by the street fights that now
  start; undecided fights across the table go 61 to 13 of 576. The facing gains
  are untouched. `-Dbattle.vehicle.repositionStalled=false` is the control.
- **What was tried and taken out.** Each was read on the same table:
  - *A forward turn toward a too-close target behind the hull*, instead of
    driving away from it. In the street both tanks parked out of each other's
    sight: every street tank duel and every tank attack on the street became a
    stalemate. A target more than 120° off the nose is still driven away from.
  - *Re-facing in band without looking.* On the hull-down ground a rear-set
    tank's forward turn drove it out from behind its berm (the refit Bulwark's
    share 0.58 to 0.79, the same with arcs off), and on the street light hulls
    backed round corners or out of reach and the ladder moved 31 pairings. A
    hull a parapet screens now makes only the backing turn. Every turn is
    rehearsed on a copy of the hull's body and made only if it ends square on in
    sight, and a backing turn stops at its band's far edge less slack.
  - *Screening every turn* (forward and backing alike) left the side-on tank
    unturned, and the escort APC went back to 0.46.
  - *Turning only after a hit off the front.* It answered rear on (0.12) but not
    side-on (0.46): the APC's first hits land after it has closed inside the
    tank's band, so the in-band turn never starts and the retreat decides.
  - *Handing a forward turn over to backing for its last stretch.* At a tank's
    reverse speed (13° a second at full lock) a rear-on turn then takes longer
    than its rehearsal allows, so it was never made. A turn that starts forward
    stays forward, a full-lock circle that does not close on the target.
  - *A gunless tank backing out of reach nose on* before it drives its route:
    measured and rejected. See the withdrawal readings above.
- **One front, and a turn that costs** (`convoy-nouns.md`, what presents the
  front). The owner's brief: no arbitrary slowness, and flanking should pay. A
  hull now faces the worst threat it knows of (harm into its plate), turns only
  toward what it or a spotter sees, and its gun shakes while its heading turns,
  by the share of its turret's traverse the yaw takes. Nothing slows the turn
  itself: the bicycle hull's pivot is already physical.
  - *Two angles.* `-PattackerSpread` fans the attackers around the defender
    (open ground) and `-PattackerRadius` sets them the same distance out, so a
    narrow fan is the head-on control. At 20 cells, 12 seeds, almost every pair
    beats a lone tank from any angle. Two Bulwarks, two escort APCs, two militia
    technicals, an escort APC with a tank: all 1.00. They read in time and in
    what is left of them: two Bulwarks kill in 38.9 s with 0.67 of their
    integrity left at a 30° fan, 18.3 s with 0.86 from opposite sides; two
    technicals 42.7 s / 0.27 against 20.6 s / 0.63. The pair a lone tank can
    beat is two fire-support APCs:

    | Two fire-support APCs vs | 30° fan | 90° | 180° (opposite sides) | 180°, arcs off |
    | --- | --- | --- | --- | --- |
    | Breakthrough tank | 0.33 | 0.42 | 0.50 | 0.17 |
    | Screened tank | 0.17 | 0.33 | 0.50 | 0.17 |

    The gain is the side plate (arcs off it is gone), and the tank's turn does
    not take it back: with the turn off, 0.42 / 0.42 from opposite sides. At
    90° the worst-threat rule costs the pair one or two fights in twelve
    against the nearest-target control (0.50 / 0.50).
  - *The lone flanker*, all three grounds, 8 seeds, before / after (the
    before arm is the shipped turn with `faceWorstThreat` and `pivotAccuracy`
    off), arcs on:

    | Attacker vs tank | Facing the attacker | Side-on | Rear on |
    | --- | --- | --- | --- |
    | Escort APC vs breakthrough | 0.21 / 0.12 | 0.12 / 0.00 | 0.08 / 0.12 |
    | Escort APC vs screened | 0.21 / 0.25 | 0.04 / 0.08 | 0.08 / 0.12 |
    | Breakthrough vs breakthrough | 0.42 / 0.46 | 0.54 / 0.58 | 0.71 / 0.71 |
    | Refit Bulwark vs breakthrough | 0.46 / 0.46 | 0.42 / 0.58 | 0.54 / 0.58 |
    | Stock Bulwark vs screened | 0.00 / 0.00 | 0.04 / 0.04 | 0.08 / 0.08 |

    With one threat the worst is the nearest, so every move here is the shaken
    gun, and it shakes both ways. A tank turning onto a refit Bulwark on its
    flank misses more, and the Bulwark's side-on share rises 0.42 to 0.58. The
    escort APC drives curves of its own while it closes and shoots, and its
    side-on row falls to 0.00 (three fights in twenty-four). A lone light hull
    still does not out-flank a tank's turn: flanking pays with a second
    machine, against a mech, or rear on. Head-on the tanks beat the escort APC
    0.88 and 0.75 (0.79 before; one fight in 24).
  - *The ladder* (4 seeds, every pairing both ways): 450 of 621 pairings
    identical, 25 move by one fight in four, in both directions and mostly
    between hulls, which every turn now shakes. Undecided fights 221 to 222.
  - Controls, each on its own: `-Dbattle.vehicle.faceWorstThreat=false`,
    `-Dbattle.vehicle.faceKnownOnly=false` (omniscient; on these grounds every
    machine is in sight, so it moves nothing here), and
    `-Dbattle.vehicle.pivotAccuracy=false`.
- **Located plates** (`convoy-nouns.md`, a hull holds its damage where it
  lands). A hull's armor sits in four plates and a drive, with the front plate
  holding the old one pool and the others on top. Shipped as is on the owner's
  word (2026-10-23), knowing what it does to a free tank in a pincer:
  - *A held flank dies much faster.* With the defending hull's duel driving off
    (`-Dbattle.vehicle.duelRange=false`), so it keeps the facing it was set down
    with, two Hounds kill it from the rear in about 15 s (the one-pool control
    25-30 s), from the side in 33 s (46-48 s), and head-on near parity (57-65 s
    against 52-67 s), as the sizing intends.
  - *A free tank between two attackers outlasts the one pool.* It turns to one
    attacker and reverses out of the pincer, backing past the other, so both end
    on its front; in every traced fight its rear plate was never hit. The side
    plates stripped while it turned are spent on top of the front's whole pool
    (about 780 armor chewed against the control's 550). Two Hounds from opposite
    sides at 20 cells, 32 seeds, Hound wins located / control: breakthrough tank
    23 / 29 (TTK 60.8 / 36.9 s), screened 23 / 28 (63.5 / 39.4 s), burst laser
    tank 16 / 30 (41.4 / 29.5 s). Head-on (the same pair on one approach) they
    win 2 / 3, 6 / 2 and 13 / 7, so the pincer is still worth having against the
    cannon tanks and is worth almost nothing against the laser tank. Two
    Bulwarks, or a Hound and a Bulwark, still win every fight, 20-65% slower.
    What moved against two Hounds is how often the tank kills both (breakthrough 9 / 1 of 32, laser 15 / 1), not how often it gets away.
  - *The single-machine ladder hardly moves* (2 seeds, every pairing both ways):
    of 612 vehicle rows 72 move by a fight, in both directions; median TTK
    ratios 1.00-1.04 by ground; mechs attacking hulls win 86 of 216 fights
    against 94.
  - *Rejected by the owner (2026-10-23): side plates as windows onto the front
    pool* (a flank hit spends the facing plate and the same armor from the
    front, so nothing is stranded). It would keep head-on exact and end the free
    pincer's stranded armor, at the cost that a left-only tank no longer keeps
    its front whole. The owner's reason: armor is local, and a pincer pays
    through attention, not shared armor: one attacker draws the tank's front
    and fire head-on while the other shoots the weaker side without taking
    return fire. The pincer's payoff belongs in what the tank attends to.
  - *Rejected alternatives.* Holding the pincer by behaviour (attackers that re-establish
    a flank against a moving hull, a tank that does not reverse into its second
    attacker) is real work in both duel rules. Thinner flanks (plates 1 / .25 /
    .25 / .15) took the free pincer from about 60 s to 53-54 s, head-on
    unchanged; the drive pool made no measurable difference; a mech stepping to
    an in-band cell on a weaker face (`MechDuelRange`) was within noise, because
    the tank keeps moving and the step is stale by arrival. None was taken.
- **Crossfire** (`convoy-nouns.md`, a crew watches one way). The owner's
  direction for the pincer: it pays through attention, not shared armor.
  - *What a tank already did.* Its gun keeps the machine it locked until that
    machine dies or leaves reach or sight, and never swung to a Hound at six
    cells off its rear; the hull keeps its front and its retreat on the machine
    it duels. Both Hounds start inside the tank's band, so it backs off the
    first nose on (the too-close retreat, not the worst-threat front, which is
    only read in band), and backing straight away from one Hound runs it past
    the other: inside about twenty seconds both stand on its front. The
    turret traverses on its own at 55° a second (twin cannon) or 110° (burst
    laser). Nothing rattled a crew fired on from two sides.
  - *The rule.* Hits from two machines at least 90° apart, each within ten
    seconds, and the gun fires at a moving shooter's accuracy. At four seconds
    it almost never held: a Hound fires in volleys several seconds apart while
    it sheds flux. Seven seconds took the laser row to 20 / 12.
  - *Two Hounds, 20 cells, 32 seeds*, Hound wins / tank wins, mean / median
    TTK of decided fights in seconds, control (`-Dbattle.vehicle.crossfire=false`) then crossfire:

    | Tank | Pincer, control | Pincer, crossfire | One approach (both arms) |
    | --- | --- | --- | --- |
    | Burst laser | 16 / 15, 41.4 / 40.3 | 25 / 7, 50.4 / 50.3 | 13 / 18, 41.5 / 40.8 |
    | Breakthrough | 23 / 9, 60.7 / 58.0 | 23 / 9, 55.0 / 59.3 | 2 / 21, 67.2 / 63.7 |
    | Screened | 23 / 7, 63.5 / 59.8 | 22 / 9, 61.1 / 63.2 | 6 / 17, 67.7 / 65.4 |

    The pincer now beats the single approach against all three; the laser tank,
    which killed the first Hound before the second mattered, no longer shrugs
    it off. The cannon tanks were already beaten by it and stay so.
  - *The default ladder* (2 seeds, every pairing both ways): all 1,410 fights
    identical to the control, as the rule requires two machines.
- **The mech torso** has arcs too (`mechs-nouns.md`, the torso's plate depends
  on its arc): front as the chassis rating stands, sides 0.95 / 0.85, rear
  0.75 / 0.6, with `-Dbattle.mech.armorArcs=false` the control. Over the mech and
  mixed ladders (8 seeds, both roles), 202 of 291 rows are identical to the
  control and no pairing moves past noise. Two-on-one mech fights (`2x` Hound,
  Warden and Bulwark, Hound+Warden, two breakthrough tanks against a Bulwark,
  a refit Bulwark and a Warden) win exactly as often, and only a few kill a
  second or two sooner. A mech's torso follows the machine it is shooting, and
  two attackers on one approach both stay inside its ninety-degree front.
  Flanking changes a mech-versus-mech outcome only when the player puts a
  machine behind the torso. The harness never does.

## Missile warheads

Each rack size has an anti-armor and a fragmentation warhead (`mechs-nouns.md`,
Missile warheads). The Frag rack is a sidegrade at the same tier, footprint and
mass, so neither warhead is an upgrade; what it gives up is the other target.
Per rocket:

| | Contact (dmg / pen) | Area (dmg / pen / radius) | Wall |
| --- | --- | --- | --- |
| SRM before the split | — | 49.5 / 14 / 1.3 | 25 |
| SRM AT | 66 / 30 | 10 / 6 / 0.6 | 25 |
| SRM Frag | — | 32 / 4 / 1.8 | 10 |
| LRM before the split | — | 81 / 16 / 2.0 | 40 |
| LRM AT | 95 / 30 | 15 / 6 / 1.0 | 40 |
| LRM Frag | — | 50 / 4 / 2.8 | 15 |

Controls: `-Dbattle.mech.missileVariants=false` (the single-warhead rack on the
AT ids) and `-Dbattle.mech.damageAwareTargeting=false` (nearest-target choice).

Readings were taken on top of the bastion and pulse-laser flux changes above and
of the armor fire order (against a machine, the reservoir goes to the mounts that
best defeat its armor first, so AT racks fire before the arms and Frag racks after).

**Against men** (`sceneEvidence -Pscene=mech-versus-infantry`, four seeds per
loop, planted stock mechs, a rifle section in the open). Seconds to kill the
whole section:

| Loop | Variant | Single-warhead control |
| --- | --- | --- |
| Chaingun Bulwark, twin SRM-15 Frag, 12 men at 15 cells | 35.3 s | — |
| Chaingun Bulwark, twin SRM-15 AT, same | 40.8 s (racks hold) | 37.4 s |
| Warden, twin LRM-5 Frag, 12 men at 30 cells | 13.6 s | — |
| Warden, twin LRM-5 AT, same | never (racks hold, cannon out of reach) | 13.6 s |

The Frag rack kills men at least as fast as the old rack did, and an AT rack holds
fire at them every time (no salvo begun at a soft target in any loop). On a stock
chaingun Bulwark the rack barely matters against men either way: it starts about
one salvo per fight, because the chaingun's flux starves it (below). On the
Warden the rack is the whole answer to men, which is why the Warden ships Frag
LRMs.

**The mixed fight.** An AT Bulwark faces six men at 13 cells with a breakthrough
tank behind them at 15. With damage-aware target choice, the torso holds the tank
for 336 ticks while a rack is loaded and puts 32 rack rounds into it, killing 15
of the 24 men over four plays. With nearest-target choice it puts no rack round
into the tank and kills all 24 men. The tank kills the stock Bulwark in every
play either way, so this loop shows the choice working, not that it wins.

**Against armor** (`armorMatchup`, twelve sides against each other, eight seeds,
three grounds, both roles; win share against the single-warhead control with
nearest-target choice):

| Side | Variant | Control |
| --- | --- | --- |
| Stock Bulwark (AT SRM-15s) | 0.53 | 0.47 |
| Stock Bulwark, Frag SRM-15s | 0.34 | 0.31 |
| Refit Bulwark (AT) | 0.69 | 0.70 |
| Refit Bulwark, Frag | 0.51 | 0.55 |
| Hound (AT SRM-5) | 0.15 | 0.13 |
| Hound, Frag SRM-5 | 0.09 | 0.09 |
| Warden, AT LRM-5s | 0.43 | 0.40 |
| Warden, Frag LRM-5s (the factory fit) | 0.21 | 0.19 |

A Frag row's control fires the same Frag rack; its difference is its opponents'
racks and the target choice. AT racks hold or improve every mech's armor results,
most for the stock Bulwark, and the Frag Warden is half the anti-armor machine
the AT one is. Where SRMs reach a tank (the street, at 17 cells) the AT warhead
takes clearly more off it than the old rack did: a refit Bulwark leaves a
breakthrough tank 0.74 of its integrity against 0.88 and a heavy-gun tank 0.70
against 0.85, and a Hound leaves them 0.90 and 0.91 against 0.93 and 0.95. AT rockets hit about a third
of the time against about half for the old area rack, because a contact warhead
needs the body. Win shares against tanks stayed at zero until the tanks were
retuned (*A tank against the heavy mech*).

**Why SRMs barely decide a mech fight**, warhead or not: SRM reach is 18 cells.
In the open, mech duels settle at 19-22 cells (the outer part of the chaingun's
30-cell band), and tanks fight from 29-39 cells. So SRMs deal 1-6% of a Bulwark's
damage in most matchups, in both runs. The tank gap is a reach-and-band question,
not a warhead one. The warhead decides only what happens once a rack is in range.

**What actually kept the SRMs out** (re-read on the retuned tanks, 8 seeds, both
roles, three grounds). Against both tanks the band already chooses to close to
the racks (9.7-16.2 cells). In the open the tank holds its own stand-off beyond
the Bulwark's 30-cell reach (31.5 cells and more) and backs away at 0.93 cells/s
while the Bulwark walks at 1.05, so it never gets there. With tank reverse near
zero (control, `-Dbattle.vehicle.tankReverseFraction=0.01`) the Bulwark closes to
17 cells and the refit fit beats a breakthrough tank 8/8 on open ground, against
3/8. The open-ground tank kiting the SRM range is accepted (owner, 2026-10-18):
there the SRM is not the anti-tank weapon; the LRM-15 and the bastion are. Once
a fight drains both magazines, the band rightly walks the mech back out. A longer
SRM reach was rejected, because catching a tank on its stand-off would take more
than 32 cells, which makes the SRM an LRM.

The other gap was between equal machines. Two AT Bulwarks trade alike at every
distance, so the band (which never closed on a tie) held them at 21-27 cells and
their racks dealt 4% of the damage. **An anti-armor rack's band now closes on a
tie** (`-Dbattle.mech.closeOnAntiArmorTie=false` is the control). Variant against
control, open / hull-down / street:

| Matchup | Fight range | Mean TTK s | Timeouts | Rack share of damage |
|---|---|---|---|---|
| stock vs stock Bulwark | 13.5 / 13.6 / 14.2 against 21.2 / 21.8 / 21.8 | 57 / 68 / 54 against 96 / 112 / 83 | 0 against 8 (of 24) | 37% against 4% |
| stock vs refit Bulwark | 15 against 19-22 | 48-53 against 70-91 | 0 against 0 | 34% against 6% |
| refit vs refit Bulwark | 14-16 against 21-22 | 55-72 against 74-105 | 0 against 4 | 16% against 4% |
| Hound vs Hound | 13-19 against 19-23 | 57-70 against 69-82 | 0 against 0 | 6% against 2% |

Win shares hardly move: stock Bulwark 0.58 overall against 0.56, refit 0.78
against 0.77, and every other fit is unchanged. Nothing changes where a trade is
not a tie: a Hound still does not walk into a Bulwark's two racks, a Frag Bulwark
does not close on an AT one, Long-Range Support never closes, and fights against
tanks and APCs read identically. `mech-duel-range` now asks the equal Bulwarks to
close to the racks' band without reaching contact (median 13.5 cells, nothing
inside 10), and asks for the old duel band under the control. `mech-versus-infantry`
passes in both arms.

Still open from these readings:

- **The chaingun starves the racks against men too.** A stock chaingun Bulwark
  starts about one rack salvo per fight against a rifle section, whatever the
  warhead.

## Lift classes

| Class | Payloads | Carriers |
| --- | --- | --- |
| Light | Hound, heavy APC | Bus-class boats (Buffalo, Tarsus, Mule, Nebula) with a lift cradle |
| Medium | Warden, escort APC, militia technical | Bus-class boats with a heavy cradle; Valkyrie |
| Heavy | Bulwark, breakthrough tank | Valkyrie; a committed ship with Ground Support |

A lift fitting is a third boat fitting slot beside plating and drive. It trades
seats for a lift rating: a cradled boat carries one machine or a reduced section,
never both at full count. Small boats (Hermes, Kite, Aeroshuttle) never lift a
machine. The progression is that a company reaches Light drops through its own
boats early, and Heavy only through a Valkyrie or a ship refit — the expensive,
late step.

**Lift per mission and the lift queue.** A light-cradled company boat flies
two machine lifts a mission, a heavy-cradled one three, and the synthetic heavy
carrier lifts the whole bay. The heavy cradle used to fly two as well, which made
it a worse buy than the light one: it costs about 2.3 times the bill, and a
Warden brings less armor (550 structure plus capacity) than a Hound (725), so
the better cradle put less on the ground. Best bay per tier, structure plus
armor capacity:

| Tier | Machines | Armor on the ground |
| --- | --- | --- |
| Starter, light cradle | 2 | 1450 (two Hounds) |
| Mid, heavy cradle | 3 | 3120 (three escort APCs); 2175 mechs only |
| Late, Valkyrie | 4 | 9600 (four dozers); 5040 (four Bulwarks) |

The late tier is capped by the four gantries, not by lift. Drops are free and
one machine each: what paces them is the ferry. Every drop waits on the lift queue
(`air-nouns.md`, Lift) behind the opening squad loads and earlier drops, and flies
only when a ferry craft whose rating admits it is free, so a machine ordered at the
start comes down after the company has landed. The 30 s call cooldown that paced the
old lance calls is gone with them. Better boats still mean more armor on
the ground, never faster drops.

## Vehicles: variants and kits

Mechs are the customization system; vehicles are a short chain of fixed
variants. A second fitting system would double the lab, the save model and the
balance surface while making the two kinds the same decision in different art.
So a vehicle's bay is sealed (the engine core, gun card and armor point were opened to the mech's own engine,
weapon and plating parts, owner, 2026-10-28; `vehicle-shared-parts.md`). What a company may add is a
**kit** (owner, 2026-10-24; `mechs-nouns.md`, vehicle kit): a few bolt-on parts in
a fixed rack, each a number changed at the drop and nothing else, so a kit is
a small top-up to the hull rather than a second build. A change that needs a
system of its own (active protection, a blade, a mortar) stays a variant.
The split gives the player a real choice instead:

- **A mech is flexible and demanding.** Sockets, flux, an engine budget, a build
  to plan and parts to lose.
- **A vehicle is cheap, sturdy and good at one job.** No chassis reservoir (each gun
  builds its own flux, nominal on the plain guns), no sockets, no
  build beyond a kit or two. It costs less material and lift for the armor it brings, and it does
  things no mech does: an APC carries marines.

Progression on a vehicle line is learning a better **variant**, each its own
pattern card at its own tier, then fabricating it and retiring the old one. The
decision is which variant fits this mission's lift and role, never how to tune
one. Lines and their first variants:

| Line | Variants (light → heavy, or sidegrade) |
| --- | --- |
| Technical (field rig) | Militia technical — Advanced filler for a small lift budget; SRM technical — Common rocket rig, an ambusher of armor (Light lift) |
| APC | Heavy APC (troop carrier) → up-armored APC (more armor, slower) → fire-support APC (a mortar instead of seats, spotted indirect fire on anything, steady) or LRM carrier (an LRM-15 anti-armor rack instead of seats, spotted indirect fire) |
| Tank | Breakthrough tank → a screened tank (active protection against top-attack rounds), a laser tank (a ship's burst laser: infantry and drones, not plate), or a dozer-blade breaching sidegrade |
| Supply (field rig) | Supply pickup — a carried resupply cache, served while halted (Light lift) |
| Spotter (field rig) | Spotter pickup — a sight mast that reports to fog and commander (Light lift) |
| Hover (Tri-Tachyon) | Hover scout (sight and a light emitter, Light lift) → drone skimmer (tethered drones, no gun, Medium lift) |

**A vehicle brings at least its class mech's armor per credit.** At its
price (`gradlew.bat armorEconomyEvidence`), structure plus armor
capacity per thousand credits against the class mech: heavy APC 1.00x the Hound;
technical 2.56x, escort 2.38x, up-armored 1.99x and fire-support 1.23x the
Warden; breakthrough 1.20x, heavy-gun 1.09x and dozer 2.04x the Bulwark (the
tanks were 1.80x and 1.63x before their plate was retuned against the Bulwark). The
heavy APC was 0.73x, a worse buy than the mech it shares a lift class with, and
was cut to 38/28/88/3 (supplies, machinery, metals, rare metals). Its variants
followed: up-armored 52/40/140/8 (1.51x the base bill for 1.63x the armor) and
fire-support 52/40/115/11 (the base hull plus a mortar-class weapon's price).
Per lift, every vehicle ties or beats its class mech except the heavy APC (380
against the Hound's 725) and the fire-support APC (380 against the Warden's
550). Those two pay in plate for their seats and their mortar. A role hull
bought for its job rather than its plate (the fire-support APC's mortar, the
spotter pickup's sight, the supply pickup's stock, a missile carrier's racks) is held
only to costing less than its class mech. A missile carrier's bill is its hull's
plus the bills of the mech racks it carries: the LRM carrier is the heavy APC's
38/28/88/3 plus two LRM-15s (82/48/148/13), the SRM technical a stripped pickup
plus an SRM-5 (38/27/72/6). The measure is
bulk only and ignores armor rating, so it is not effective durability. The
dozer is the best armor per credit in the game, and raising its bill past the
Bulwark's would break this rule, so it stays as it is.

**The missile carriers in a duel** (`armorMatchup`, one machine a side, four
seeds, three grounds, both roles; no spotter, so the LRM carrier fires only on its
own sight). Wins out of 24 and the opponent's integrity left:

| Carrier against | Breakthrough | Heavy-gun | Hound | Warden | Bulwark | Bulwark refit |
| --- | --- | --- | --- | --- | --- | --- |
| LRM carrier | 0 (0.53-0.87) | 0 (0.27-0.84) | 20 | 16 | 17 | 10 |
| SRM technical | 0 (0.77-1.00) | 0 (0.75-1.00) | 1 | 1 | 0 (1.00) | 0 (1.00) |

The LRM carrier out-shoots every mech from 26-34 cells (a ten-round anti-armor
salvo every nine seconds) but cannot stand off a tank: 300 points of hull die in
7-17 s once a tank closes, and a carrier reversing at half speed cannot open the
range on a tank driving at it, so its tank fights settle at 25-35 cells, inside the
cannon's 36. It takes a quarter to half of a tank's integrity before it dies. The
SRM technical never wins a stand-up fight: in the open every machine out-reaches
its 18 cells and it dies in 3-15 s, which is why it is an ambusher; its acceptance
is the ambush, not the duel. On the street every short-reach attacking hull (the
militia technical and heavy APC too) times out under the instrument's steering, so
those rows read nothing. Not tuned: the duel numbers are readings.

**The laser tank against men and machines.** A ship mounts a burst laser as point
defence because it barely marks proper plate; at ground scale almost nothing is
proper plate. The tank's identity is the split between the two, and it is held by
cadence rather than by a damage type: a four-pulse burst at penetration 3 every
0.55 s, each pulse adding flux the gun dissipates far slower than it builds, so it
overloads and vents for seconds at a time in a long fight (`convoy-nouns.md`, The
laser tanks). A section dies inside one charge-up; a tank does not.
Readings against the breakthrough tank's twin cannon (`tank-versus-infantry`, four
seeds, one planted tank and a twelve-strong section eighteen cells off; `armorMatchup`,
four seeds, three grounds):

| | Twin cannon | Burst laser |
| --- | --- | --- |
| Line section (10/8 plate) broken | 19.8 s | 12.1 s |
| Power-suit section (60/14) | 45 of 48 killed, 1 of 4 broken | 31 of 48 killed, none broken |
| Attacking a breakthrough tank, wins of 4 (open, hull-down, street) | 2, 2, 2 | 0, 0, 1 |
| Attacking a Hound / heavy APC, wins of 4 per ground | 4 / 4 | 4 / 4 |

Without the bank, the same burst at penetration 3 took every street fight and every
open-ground attack from the cannon tank: the armor pool is the only thing penetration
discounts, and once it is stripped the structure under it takes every hit in full.
Power suits are the laser's hard case on purpose.

The gun also reaches the air. Under a hostile Mudskipper on station overhead
(`tank-versus-air`, three seeds), the laser tank brought the craft down in every play,
1.5 s after its first air round on average. The cannon tank never fired at it. With
eight riflemen in reach as well, it split 33 air rounds against 48 at the ground and
still downed the craft, 4.0 s after its first air round. A Mudskipper's 60 points are
one burst at 85% accuracy, where a dual-flak position (36 at 40% every 0.8 s) needs
about three seconds. That makes the laser tank the deadliest thing on the ground to
light air, and the lever, if it is too much, is the weapon's accuracy against the
air. Not tuned beyond this: readings.

Faction variants (a Hegemony APC, a Tri-Tachyon tank) arrive as Prestige or
Restricted cards weighted by faction, as faction mech weapons are.

**Anything built on a truck is a field rig, not military issue.** A pickup hull
is what a company or an outfit welds together from a civilian chassis; pirates
and independents weight those cards up and no navy does, and their fabrication
provenance says field rig. A military faction is known for purpose-built hulls:
Hegemony's are heavy plate, and Tri-Tachyon's are hover and drone coded: the hover
scout and the drone skimmer. Both are role hulls held only to costing less than their
class mech, and their bills lean on rare metals for fans, sensors and drone control.

One later, optional depth step: a single **overhaul** per vehicle (an up-armor
or engine kit bought for credits, one decision, no socket grid), reusing the
internals vocabulary without the doll.

## Stories

| Story | Owns |
| --- | --- |
| Mech patterns (shipped; `mechs-nouns.md`) | Learned patterns replace the starter-pattern gate; pattern cards through every existing source. |
| Mech internals (shipped; `mechs-nouns.md`) | Engine, plating, capacitor bank and vent array internals; engine budget; tier-scaled footprints; per-loadout flux. |
| Heavy lift (shipped; `air-nouns.md`, Lift) | Lift class and rating; the boat lift slot; drops flown by real company carriers. |
| Vehicles in the bay (shipped; `mechs-nouns.md`) | Vehicle patterns; a bay space holds a mech or a vehicle; losses write back. |
| Armor drop selection (shipped; `mechs-nouns.md`, armor drop) | Pick which bay spaces come down per call; cooldown between calls; lift caps armor per mission. |
| Vehicle variant ladder (shipped; `mechs-nouns.md`) | Fixed vehicle variants per line, each a pattern card; faction variants; no vehicle fitting. |
| Tank variant art (shipped; `convoy-nouns.md`) | Art for the dozer breacher, offered. The heavy-gun tank was removed (owner, 2026-10-22). |
| Dozer breach (shipped; `convoy-nouns.md`) | The dozer's blade clears ground other vehicles can follow. |
| Light roles (shipped; `convoy-nouns.md`, light roles) | Supply pickup through the resupply authority; spotter pickup through fog and commander contacts; both field rigs. |
| Missile carriers (shipped; `convoy-nouns.md`, the missile carriers) | LRM carrier (spotted indirect anti-armor fire, stand-off) and SRM technical (ambusher field rig); both hold fire at men. |

Order: pattern cards first (they license existing weapons and chassis at once),
then internals, then lift; vehicles in the bay and the drop choice follow lift.

## Open questions

- With the threat in reach, a long-range support mech's fighting withdrawal
  still turns a little back and forth against a firing line whose members take
  turns as its target.
- Engine speed bands are reported, not measured: every endurance reading so far
  is a planted mech.
- Castle's introduction of each internal needs no recorded dialogue until a live
  pass confirms the ladder.
- Vehicle weapon swaps are in, typed: the gun card takes the mech weapons of its own type and size, and the bay stays sealed.
  Whether a kit should cost lift (appliqué plate on a Light hull) is unmeasured.
