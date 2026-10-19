# Heavy equipment progression

Status: DRAFT — the stock-frame baseline, learned mech patterns, internal fittings,
vehicles in the mech bay, heavy lift and the drop choice have shipped; vehicle
variants are planned direction.

Written: 2026-10-16
Updated: 2026-10-18 — missile racks split into anti-armor and fragmentation warheads (readings below); the Bulwark's other arms are balanced as equipment (bastion build mass 3 and heat 28, pulse laser heat 26; the chaingun is the anti-infantry arm and stays at 30); the heavy cannon before them (build mass 3, heat 34, Sirocco rated 39); earlier: engines carry mass, military sink 120, call cooldown 30 s, heavy cradle three lifts, APC bills cut.

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
  Hound remain a fit worth taking into the first operations. The spare rack is
  the fragmentation SRM-5, and a new company knows both SRM-5 warheads.

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

Rated output is Hound 20, Sirocco 39, Bulwark 46 (stock weapons weigh 16, 39 and
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

**The Sirocco is rated 39.** The LRM-15 is long-range support's Advanced rack,
as the SRM-15 is the Bulwark's. At 34 the most fragile chassis had to give up its
double sink or its heavy plate to carry one. At 36 the factory heavy cannon,
LRM-5 and LRM-15 fit a full frame (400 armor, 8/s) with no headroom, so the tier
still costs room. The heavy cannon then gained 3 of build mass (below), and the
rating rose by exactly that, so the Sirocco's own fit is where it was: the stock
fit fills the industrial engine, and the full frame fits the military one with
1.5 to spare, short of the cooler's 2. The best of everything still does not fit,
and a refit Sirocco still out-plates no Hound.

## The heavy cannon is equipment, not the Sirocco's gun

The heavy cannon is the Sirocco's factory arm, but its 3x2 ballistic footprint
fits any arms socket that takes 3x2, so it is balanced by what carrying it
costs, never by the fragility of the chassis that ships with it. Today that is
the Sirocco and the Bulwark, whose 3x2 omni arms take it; the Hound's 2x2 nose
cannot, at any quarter-turn. Its contact hit (100 on the body struck, over a 45
splash) was tuned against mech armor and is not a lever here.

**A Sirocco dying in about 11 s to one heavy cannon in the open is accepted**
(owner, 2026-10-18). The Sirocco is long-range support, not a front-liner: it is
meant to be fragile under direct fire, and a Sirocco mirror fight is short
(12-13 s in `armorMatchup`, both before and after the changes below).

**The cannon cost its carrier nothing.** It weighed 14, two more than the
factory chaingun, and a full refit Bulwark has exactly 2 to spare, so the best
Bulwark frame carried it at no loss. It also runs cool: 26 heat per 2.5 s round
is 10 heat/s, against the chaingun's 29. A cool arm leaves the reservoir to the
SRM-15s, which did 60-78% of a cannon Bulwark's damage at the 17-18-cell range
the duel band holds. So a cannon on a **stock** Bulwark beat a **fully refit**
chaingun Bulwark 70% of the time: an Advanced weapon outweighing a whole internal
refit.

**What it costs now:**

- **Build mass 3** (`MechFrameBudget`; mass 17). A full refit Bulwark with the
  cannon and two SRM-15s gives up its cooler. The Sirocco's rating took the same
  3, so the Sirocco pays nothing for its own gun. Mass alone did not move a fight:
  at +3 and at +6 (cooler and sink tier given up) the refit cannon Bulwark still
  beat a refit chaingun Bulwark 83-88%. Every authored doll's internals are cheap
  next to the reservoir the cannon left free, which is why heat was needed too.
  +6 was also rejected because the Sirocco's rating would have to rise by 6, and
  the military engine would then hand it a free cooler.
- **Heat 34 per round** (was 26; 13.6 heat/s at its cycle). The cannon now
  competes with the SRM-15s for the reservoir. On a stock frame (100 heat, 6/s)
  that is the cost that bites; on a 120 reservoir at 7.5/s less so. At 42 the
  stock-frame cannon Bulwark lost to everything but a stock chaingun, a weapon
  not worth carrying; 34 is where it trades even with the chaingun refit's
  frame. A short Sirocco fight lives off its reservoir, so heat barely touches
  the Sirocco (mirror 13 s either way).
- **Range stays 26**, already the shortest mech arm (chaingun 30, pulse laser 32,
  bastion 34). Cycle time is unchanged.

Readings (`armorMatchup`, explicit fits, open / hull-down / street, eight seeds,
both roles; win share, and mean open-ground seconds from first hit). Controls
are `-Dbattle.mech.heavyCannonBuildMass=0 -Dbattle.mech.heavyCannonHeat=26`.

| Fight | Before | After |
| --- | --- | --- |
| Cannon Bulwark (stock frame) vs refit chaingun Bulwark | 0.70, 69 s | 0.31, 83 s |
| Cannon + SRM-15 + SRM-5 (stock frame) vs refit chaingun Bulwark | 0.48 | 0.21 |
| Refit cannon vs refit chaingun Bulwark | 0.90, 56 s | 0.78, 71 s |
| Refit cannon vs refit bastion Bulwark | 0.48 | 0.32 |
| Refit cannon vs refit pulse-laser Bulwark | 0.73 | 0.73 |
| Refit cannon vs stock chaingun Bulwark | 1.00, 44 s | 1.00, 59 s |
| Refit cannon Bulwark vs breakthrough / heavy-gun tank | 0.00 / 0.00 | 0.00 / 0.01 |
| Stock / refit Sirocco vs refit cannon Bulwark | 0.06 / 0.04 | 0.06 / 0.06 |
| Sirocco mirror, stock and refit | 13 s / 13 s | 13 s / 13 s |
| Overall across the eleven fits: refit cannon / refit bastion Bulwark | 0.67 / 0.65 | 0.65 / 0.69 |
| Overall: stock-frame cannon Bulwark / refit chaingun Bulwark | 0.44 / 0.39 | 0.35 / 0.47 |

The cannon is still a good Bulwark arm: refit around it, it beats the factory
chaingun refit and the Prestige pulse laser. It is no longer the best one, it
costs a cooler and a hotter reservoir to carry, and on a stock frame it no longer
outweighs a refit. The heavy-cannon Sirocco is not a counter to anything heavy:
it loses to every refit Bulwark and to both tanks, and beats only a stock
chaingun Bulwark (stock Sirocco 0.39, refit 0.77; was 0.59 and 0.88).

Still open from these readings:

- **Every tank beats every Bulwark fit**, 93-95% overall, never losing to a
  Bulwark in more than one fight in the 96 here.
- **The chaingun Bulwark is the anti-infantry Bulwark** (owner, 2026-10-18).
  Its armor-duel share is low by design, so it is judged against infantry,
  never by how it fares against machines. Its anti-armor answer is its SRM
  shoulders, as LRMs are the Sirocco's. What these readings did expose is that
  the chaingun's heat starves those shoulders (next section).

The bastion and the pulse laser are balanced in the next section.

## The Bulwark's other arms are equipment too

The same readings showed two more of the Bulwark's 3x2 arms out of order. The
bastion autocannons had the cannon's old problem: mass 14 and about 9 heat/s,
so the best refit Bulwark arm cost nothing to carry, and on a **stock** frame it
beat a refit chaingun Bulwark 56% of the time. The Prestige pulse laser, at 38
heat per 2.1 s, lost to both Advanced arms on the same frame, 0.00 against the
bastion. They were tuned in the cannon's order: mass first, then
heat. Range and cycle were not needed. Each lever was read alone against the
others' authored values before they were combined.

- **Bastion: build mass 3** (mass 17), as the cannon's, for its feed. A full
  refit Bulwark carrying it gives up its cooler. Alone it barely moves a fight
  (refit bastion against refit cannon 0.58 to 0.46; stock frame against refit
  chaingun unchanged at 0.56, because a stock frame has the room anyway).
  The Sirocco's rating does not absorb it: the bastion is not its gun.
- **Bastion: heat 28** (was 24; 10.8 heat/s). This is the lever that bites on a
  stock frame. Alone it takes the stock-frame bastion against a refit chaingun
  from 0.56 to 0.29. At 32 the refit bastion fell behind the refit cannon and the
  pulse laser by a wide margin (0.31 against each), so 28 it is.
- **Pulse laser: heat 26** (was 38; 12.4 heat/s). The Prestige arm already pays
  in mass: at 16 it fills a full refit Bulwark's engine exactly (69 of 69). At 30
  it drew level with the cannon (0.42 each way) but still lost to the bastion
  (0.33 against 0.50). At 26 it edges both. A pulse laser on a stock frame loses
  to every refit Advanced arm (0.00-0.02), so a Prestige arm does not outweigh
  an internal refit either.

**The chaingun stays at 30, and heat is the wrong lever for its fault.** The
chaingun's 29 heat/s does starve the Bulwark's SRM-15s, its anti-armor role:
against machines the shoulders deal only 14-16% of a chaingun Bulwark's damage.
Heat 24 was tried. On a refit frame it lifts that share to 25%, but on a stock
frame it falls to 10%. A reservoir releases at 40% and admits a salvo only under
90%, so a 42-heat SRM after a chaingun burst needs a reservoir of
2 x (burst + 42): 132 at heat 24, beyond any stock frame. And the AI tries the
arms first, so the chaingun takes every release. Against ten rifles and an AMR
(`mech-frame-endurance`, 48 seeds) heat 24 also raised a stock chaingun
Bulwark's damage per minute by 26% (1136 to 1435) and a refit's by 15%. Every
neutral enemy Bulwark fights on a stock frame, so that would have been a quarter
more fire at marines while fixing nothing for the shoulders. The fault is the
firing order: against armor, the anti-armor mounts should get the reservoir
first. That is weapon-selection behavior, not equipment, and it is left open
below.

Readings (`armorMatchup`, fourteen explicit fits: the cannon section's eleven
plus a stock-frame bastion, a stock-frame pulse laser and a refit linear cannon;
open / hull-down / street, eight seeds, both roles). A share is the wins over
fights played in both roles; overall is the mean over the eleven fits the cannon
section used. The controls are `-Dbattle.mech.buildMass.bastion-autocannons=0
-Dbattle.mech.heat.bastion-autocannons=24 -Dbattle.mech.heat.dual-pulse-lasers=38`.
The control run reproduces the before column fight for fight. Any arm's heat,
cycle or build mass takes the same
`-Dbattle.mech.<heat|cycle|buildMass>.<id without component.mech->` switch.

| Fight | Before | After |
| --- | --- | --- |
| Bastion (stock frame) vs refit chaingun / refit cannon Bulwark | 0.56 / 0.04 | 0.29 / 0.04 |
| Refit bastion vs refit cannon / refit chaingun | 0.58 / 0.67 | 0.38 / 0.65 |
| Refit pulse laser vs refit bastion / refit cannon | 0.00 / 0.19 | 0.52 / 0.44 |
| Overall: refit pulse / cannon / bastion Bulwark | 0.53 / 0.61 / 0.62 | 0.61 / 0.61 / 0.55 |
| Overall: stock-frame bastion / pulse Bulwark | 0.39 / 0.20 | 0.31 / 0.37 |
| Heat-limited seconds per fight: refit bastion / pulse | 13 / 36 | 18 / 20 |
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
  Siroccos clearly). Its trouble is not carriage. Its penetration of 8 against the
  Bulwark's 18 halves every round. And the duel band its SRM-15s pull it into
  keeps it at 17 cells, where those launchers latch the reservoir about 60 s of
  a 75 s fight. Read with the chaingun at 24, heat 14 moved it to 0.36. Heat 16 with the cycle halved to 1.4 s
  reached only 0.44, level with the chaingun refit, a Common arm. A weapon that needs
  its fire rate doubled is a damage question, not an equipment one, so it was
  left as authored.
- **The chaingun takes every heat release from the shoulders.** Against armor
  the SRM-15s should be offered the reservoir before the anti-infantry arm, on
  stock frames as well as refit ones. Judge a fix by the shoulders' share of
  damage against machines, and leave the chaingun's fire against infantry where
  it is (`mech-frame-endurance`, rifles).
- **Every tank beats every Bulwark fit**, as above.

Direction, not yet a story: missile racks are the mech's anti-armor weapons, and
each likely wants an anti-armor and a fragmentation variant, so a rack fitted
for infantry work stops competing with the arm built for it.

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

Readings were taken at `536ae4d80`, before the bastion and pulse-laser heat changes
above landed.

**Against men** (`sceneEvidence -Pscene=mech-versus-infantry`, four seeds per
loop, planted stock mechs, a rifle section in the open). Seconds to kill the
whole section:

| Loop | Variant | Single-warhead control |
| --- | --- | --- |
| Chaingun Bulwark, twin SRM-15 Frag, 12 men at 15 cells | 35.3 s | — |
| Chaingun Bulwark, twin SRM-15 AT, same | 40.8 s (racks hold) | 37.4 s |
| Sirocco, twin LRM-5 Frag, 12 men at 30 cells | 13.6 s | — |
| Sirocco, twin LRM-5 AT, same | never (racks hold, cannon out of reach) | 13.6 s |

The Frag rack kills men at least as fast as the old rack did, and an AT rack holds
fire at them every time (no salvo begun at a soft target in any loop). On a stock
chaingun Bulwark the rack barely matters against men either way: it starts about
one salvo per fight, because the chaingun's heat starves it (below). On the
Sirocco the rack is the whole answer to men, which is why the Sirocco ships Frag
LRMs.

**The mixed fight.** An AT Bulwark faces six men at 13 cells with a breakthrough
tank behind them at 15. With damage-aware target choice, the torso holds the tank
for 335 ticks while a rack is loaded and puts 16 rack rounds into it. With
nearest-target choice it puts none. The tank still kills the stock Bulwark in
every play.

**Against armor** (`armorMatchup`, twelve sides against each other, eight seeds,
three grounds, both roles; win share against the single-warhead control with
nearest-target choice):

| Side | Variant | Control |
| --- | --- | --- |
| Stock Bulwark (AT SRM-15s) | 0.36 | 0.34 |
| Stock Bulwark, Frag SRM-15s | 0.34 | 0.32 |
| Refit Bulwark (AT) | 0.69 | 0.68 |
| Refit Bulwark, Frag | 0.56 | 0.59 |
| Hound (AT SRM-5) | 0.15 | 0.13 |
| Hound, Frag SRM-5 | 0.10 | 0.10 |
| Sirocco, AT LRM-5s | 0.43 | 0.40 |
| Sirocco, Frag LRM-5s (the factory fit) | 0.21 | 0.19 |

A Frag row's control fires the same Frag rack; its difference is its opponents'
racks and the target choice. AT racks hold or slightly improve every mech's armor
results, and the Frag
Sirocco is half the anti-armor machine the AT one is. Where SRMs reach a tank
(the street, at 17 cells) the AT warhead takes more off it than the old rack did:
a refit Bulwark leaves a heavy-gun tank 0.78 of its integrity against 0.86, and a
Hound leaves a breakthrough tank 0.90 against 0.93. AT rockets hit about a third
of the time against about half for the old area rack, because a contact warhead
needs the body. Win shares against tanks stay at zero.

**Why SRMs barely decide a mech fight**, warhead or not: SRM reach is 18 cells.
In the open, mech duels settle at 19-22 cells (the outer part of the chaingun's
30-cell band), and tanks fight from 29-39 cells. So SRMs deal 1-6% of a Bulwark's
damage in most matchups, in both runs. The tank gap is a reach-and-band question,
not a warhead one. The warhead decides only what happens once a rack is in range.

Still open from these readings:

- **SRM reach against the duel band.** A Bulwark's SRMs rarely reach anything
  armored outside a street. Either the duel band should close to SRM range when
  the racks are loaded and the target is armor, or the SRM's reach should grow.
- **The chaingun starves the racks against men too.** A stock chaingun Bulwark
  starts about one rack salvo per fight against a rifle section, whatever the
  warhead.

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
| Technical (field rig) | Militia technical — Common filler for a small lift budget |
| APC | Heavy APC (troop carrier) → up-armored APC (more armor, slower) → fire-support APC (a mortar or autocannon instead of seats) |
| Tank | Breakthrough tank → heavy-gun tank, or a dozer-blade breaching sidegrade |
| Supply (field rig) | Supply pickup — a carried resupply cache, served while halted (Light lift) |
| Spotter (field rig) | Spotter pickup — a sight mast that reports to fog and commander (Light lift) |
| Hover (Tri-Tachyon) | Hover scout (sight and a light emitter, Light lift) → drone skimmer (tethered drones, no gun, Medium lift) |

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
spotter pickup's sight, the supply pickup's stock) is held only to costing less than
its class mech. The measure is
bulk only and ignores armor rating, so it is not effective durability. The
dozer is the best armor per credit in the game, and raising its bill past the
Bulwark's would break this rule, so it stays as it is.

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
| Light roles (shipped; `convoy-nouns.md`, light roles) | Supply pickup through the resupply authority; spotter pickup through fog and commander contacts; both field rigs. |

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
- Vehicle fitting (turret swaps) is out of scope until a vehicle doll exists;
  `mech-lab-data-authored-dolls.md` is the shared seam.
