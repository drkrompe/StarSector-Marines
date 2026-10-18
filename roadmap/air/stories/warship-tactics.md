# Story — Warship tactics: fleet manoeuvres, spacing and a fight worth watching

Status: ACTIVE — slices 1 (spacing and soft hull contact), 2 (group roles and manoeuvres) and 3 (hull-fight tempo D1, mount repair D2) built; slice 4 (equal fights resolve: standoff, pressing) and slice 5 (fights at range, to the death) built

Written: 2026-10-16
Updated: 2026-10-18 — slice 5: hulls fight at 2.5x the range, duels end in a kill (no voluntary withdrawal); measured

Read `air-nouns.md` (**Overhead ships**) and `warship-behaviours.md` (all Decided sections, especially ship against
ship, ships of the line and round two) first. Vocabulary (warship, mount, duel helm, standoff law, bow and broadside
ship, pack, charge, escort, screen line, edge pressure, duel footing, hull-fire hold, ledger) is theirs. This story adds a
layer above the single-ship duel helm, a rule that hulls do not pass through each other, and a calibration of how long
a hull lasts.

## The goal: a fleet action, seen from above

Owner, after watching the Black Sluice duel: "lol why are these ships so physically weak? Also the 'wolf pack' tactics
are just them both closing in on the same target and 'hitting into each other' - we really need to work on our
behaviors here." And then: "I think in general I'm looking for a more cinematic fight here. Direct flight onto a target
and a close air brawl reads like simple arrive behaviors to me".

The standing vision is lumbering giants: ships of the line or a WW2 fleet action. Heavy hulls trade broadsides, or
bow-fight where their guns point forward; escorts screen in line; a fight lasts a minute and a half to three minutes,
and in it either side can lose ships. What a viewer should read from above is **deliberate manoeuvre**: ships that
form up, turn into line, run past each other, open the range, come about and come again, a pack that splits so one
ship draws the escorts while the other swings round the flank, escorts that put themselves in line between the threat
and the ship they guard, a capital turning slowly to unmask a fresh broadside. Distance breathes: closing, passing and
opening again, not a constant knot at close range.

What the duel does today is the opposite. Every duelling ship flies one rule every tick: put a station at the standoff
range on the target's beam or quietest quarter and arrive there. Two hunters on one target compute the same quarter
and fly into the same air; once the escorts are gone the pack is two ships seeking one point. Nothing lasts longer
than a tick, so nothing reads as an intent.

**The manoeuvre is the unit of behaviour.** A ship holds a manoeuvre for tens of seconds: an intent, planned against
its heavy handling, with a course to fly, how its guns are meant to bear, and the conditions that end it. The per-tick
helm executes the manoeuvre; it does not choose the behaviour. Nothing is scripted: which manoeuvre a ship takes, and
when it ends, is read off the situation (relative position and course, where the ship's guns bear, its plate and flux,
its role in the group), and who wins is the fight's own doing.

## What was measured (2026-10-16, before any change)

Instruments: `sceneEvidence -Pscene=greyhook-duel` (the production Black Sluice duel, five seeds, now also reading
friendly spacing, hit share, Hound mounts and each Wolf's targets) and the new `hull-durability` scene (one free duel per
class matchup, read against the hulls' own manoeuvre clocks).

**Black Sluice, five seeds (15305, 16205, 16206, 20261016, 31337).**

- Both Hounds fall 50 to 52 seconds after the order, 8 to 10 seconds after the first hit; the second follows 17
  seconds later. The Hounds fire 0, 0, 84, 1 and 20 rounds: on three seeds a Hound never fires at all.
- The Hounds lose their only anti-ship gun (the bow chaingun hardpoint) on 4 of 5 seeds; where the gun is lost the
  share of in-reach time it bears on its target is 0%.
- 89 to 96% of the rounds fired between hulls land on a hull.
- **Friendly hulls overlap on every seed**: minimum friendly gap -29 to -37 cells (a Wolf is 26 cells long) and
  372 to 3,485 ticks (12 to 116 seconds) with two friendly hulls inside each other. Nothing stops it: hulls have no
  contact with each other at all, friend or foe (the free Enforcer mirror closes to -7.5, the Hound mirror to -12.8).
- Each Wolf's target, as a share of its time over the fight: one Wolf spent 59, 100, 0, 60 and 43% on the Greyhook
  while the other spent 23, 61, 90, 70 and 61% on her. On every seed both Wolfs are on the same hull for long stretches,
  from the same quarter, which is the "hitting into each other" the owner saw.

**Class matchups (`hull-durability`, one free duel each, kill time from the first hit).**

| Matchup | Kill time | Hit share | Paper kill (base game) | Victim's 90° turn | Victim crosses own length |
| --- | --- | --- | --- | --- | --- |
| Wolf (Rusk) mirror | 22 s | 100% | 6.6 s | 13.7 s | 24.8 s |
| Wolf vs Hound (P) | 8.8 s, Hound fired 0 rounds | 81% | 12.7 s | 11.9 s | 18.9 s |
| Hound mirror | unresolved at 300 s, each gun lost 0.6 s after the first hit, hulls overlapped | 100% | 9.8 s | 11.9 s | 18.9 s |
| Venture (Greyhook) vs Wolf | 32.5 s (Wolf) | 96% | 10 s | 13.7 s | 24.8 s |
| Enforcer mirror | 117 s | 97% | 66 s | 17.7 s | 48 s |
| Eagle mirror | unresolved (73% each) after 172 s of fire | 99% | 68 s | 23.4 s | 74 s |
| Onslaught mirror | unresolved (66/72%), first hit only at 219 s | 85% | 28 s | 41.5 s | 190 s |

## Durability: the diagnosis

The hulls are not weak by the base game's numbers: a Wolf's or Hound's hit points and armour are its table row, and the
paper kill times above are the base game's own (an Antimatter Blaster is a frigate killer there too). What is broken is
**time**: the hull fight runs on the base game's weapon clock while everything about manoeuvre was slowed many times
over. Candidate by candidate:

1. **Hit probability.** 85 to 100% of rounds land. A round is aimed at the hull's lead point with perfect knowledge of
   its velocity, the body is drawn at six times the density while reach between hulls grew three times, and most
   energy weapons have no cone at all, so a round that is fired lands. In the base game a frigate survives by moving
   before the round is fired; at atmospheric handling a frigate's acceleration moves it a fraction of a cell during a
   round's flight, so nothing a model of evasion could honestly add would change much. Contributes, but is not the
   lever.
2. **The standoff range.** Three of the two hulls' mean lengths puts the Wolfs at 66 to 74 cells, inside the pulse
   laser's 81 and the Hound's chaingun's 61 for closing runs: everything is in reach almost always. This decides where
   the fight is, not how long it lasts. Not the lever.
3. **Plating breach and mount loss.** This is why the Hounds fight back with nothing. A frigate's grid is the minimum
   six rows; its bow section gives way once a quarter of the hull's hit points has gone through it (500 for a Hound),
   and a single Harpoon pair or Antimatter Blaster hit carries more than that. The bow breaches, the chaingun on it is
   knocked out for the battle, and the Hound becomes a target. In the base game the gun would be disabled and repaired
   in seconds. A real defect for small hulls with one gun; a calibration item (D2 below).
4. **The shield and flux cycle.** Works: the Wolfs absorb 2,700 to 7,000 on shields without overloading in the duel,
   Eagles absorb 7,800, Enforcers 19,000. Shields lengthen the shielded fights correctly; the unshielded Hound is
   where it bites. Not the lever.
5. **Damage types and penetration.** Already the base game's (round two). Not the lever.
6. **Charge-up cadence and finishers.** Already fixed in round two (the pulse laser fired six times too often; Harpoons
   are held for a hull worn to three fifths). Both are working; the Wolf's Antimatter Blaster fires every ten seconds as
   the base game says.
7. **Hit points against base-game damage per second at these ranges.** The paper kill times are the base game's: 7 to
   13 seconds for a frigate. The free duels run 0.7 to 3.3 times their paper kill time.

**The cause is the clock.** Atmospheric handling slowed every hull's manoeuvre (speed compressed toward a battleship's
and halved, turn compressed and cut to three fifths, spool floors of six and four seconds), and hull reach grew three
times while a round's speed stayed on the shared density, so a round takes three times as long to cross the gap
between hulls as it does in the base game. The guns kept the base game's cadence. Measured against the hulls' own
clocks, a Wolf mirror is over in less than two right-angle turns and before either ship has crossed its own length; a
Hound dies to a Wolf before it has turned through ninety degrees. In the base game a frigate duel is worth many
manoeuvres; here it is worth none, so no manoeuvre layer could ever be seen to matter, and the fight reads as two
ships seeking each other until one stops.

### The calibration (designed, not padded)

Ship against ship keeps the base game's balance (balance law): every ratio between hulls and weapons stays the base
game's. So the calibration is not a table of hit points; it is one **hull-fight tempo** that slows the guns' clock
between hulls by the same factor the atmosphere slowed the rounds:

- **D1, the hull-fight tempo.** Everything a hull fight spends per second runs at `1 / HULL_TEMPO`: a gun's cycle
  between hulls (charge, cooldown, burst gap), the missile ripple, and on the duel footing the flux pool's dissipation
  and shield upkeep (with overload lasting `HULL_TEMPO` times longer). A round's damage, an armour reduction, a shield's
  efficiency and a hull's hit points are untouched, so every matchup keeps the base game's outcome and only takes
  longer. The value is derived, not tuned: **the round's own pace**. Hull reach is the game's range read at
  `HULL_REACH_MULTIPLIER` (three) times the shared density while a round flies at the shared density, so every round
  between hulls is three times as long in flight as in the base game; at a tempo of the same three, the number of
  rounds a gun has in the air at once, and the share of a fight a round spends crossing, are the base game's. Ground
  fire, air fire and point defence against aircraft are not hull fire and keep their clocks.
  `-Dbattle.warship.hullTempo=1` is the control.
- **D2, a mount is silenced, not destroyed, by the first breach of a small hull.** A gun knocked out by its cell's
  breach is out for the base game's repair time scaled by the tempo, and comes back unless the hull section under it
  has been holed through a second time. Rejected alternatives: raising `SECTION_BREACH_SHARE` (softens plating for
  every hull to fix one class) and exempting frigates (per-class authoring).
- **Targets for the calibration**, read as kill time over the victim's ninety-degree turn: at least three turns for a
  frigate duel and an outcome inside the observation window for frigates and destroyers; a capital mirror may run to
  the window's end. A fleet action such as Black Sluice should last 90 to 180 seconds from the first hit, with losses
  possible on both sides across the seeds.

## Tactics: a layer above the helm

### Vocabulary (fits `air-nouns.md`)

- **Manoeuvre** — a ship's current intent in a hull fight, held for tens of seconds: a kind, the hull it is about, the
  side or bearing it works, and its own end conditions. It sets the course, the speed and how the guns are meant to
  bear; the helm flies it. A manoeuvre is chosen from the situation and never scripted.
- **Group** — the ships of one side fighting hulls together, read each second. Escorts belong to their charge's group.
- **Role** — what a ship is for in its group this minute: **lead** or **second** of a line, **pin** (engage the
  escorts), **flank** (go round to the prize), **screen** (an escort interposing), or **free** (a lone ship).
- **Target distribution** — who fights whom, decided for the group, not each ship for itself. Two ships on one hull is
  a decision (focus fire on a hull worn under a third, or the last hull left), and then they work it from different
  sides.
- **Line** — two or more broadside ships of a group on one target sail in line ahead: the second follows the lead's
  wake at a fixed interval, so both broadsides bear on one run.

### The manoeuvres

All are general: which applies follows from where a ship's guns bear (`WarshipDuelHelm.bowFighter`), its role, the
range and bearing to its target, its plate and its flux. No hull is named.

| Manoeuvre | Who flies it | Course and guns | Ends when |
| --- | --- | --- | --- |
| Approach | anyone out of reach | toward the side of the target its role works, closing on a converging (oblique) course, in formation with its group | inside its reach by a margin |
| Attack run | bow ship | a line that passes the target at its standoff on one side, nose and hardpoints on the target while closing | its own pool fills, or it is close and spent and its target is not near its limit, or a time cap: a standoff (front shield) or an extend (omni shield or none) |
| Standoff | front-shielded bow ship | astern and to one side round the target, nose, guns and shield kept on it; past the target's reach while venting | vented, or (not venting) out at its open range, its target near its limit, or a time cap: another run |
| Broadside run | broadside ship | parallel to the target's course at the standoff range, the engaged broadside laid abeam | a run's length has passed, or the engaged side's plate is worn |
| Extend | anyone after a run | straight on, opening the range | out past the target's reach, or a time cap |
| Come about | anyone after an extend | a slow turn back toward the target through the open side, the fresh broadside unmasking | the guns bear again: a new run |
| Flank | the flank role | wide round the prize's escorts to the prize's far quarter | the prize is in reach from that quarter: an attack or broadside run |
| Screen | an escort | in line between its charge and the nearest threat, parallel to the charge's course | the threat leaves or the charge is lost |
| Line ahead | second of a line | in the lead's wake at a fixed interval, matching its course | the lead is lost or the line breaks |

A ship in a manoeuvre travels **along its nose**: a hull fighting hulls can drift sideways and astern only at a
fraction of its speed, so its motion reads as a ship's course rather than a hover that strafes. The duel helm's
station and heading laws stay the source of range and bearing; a manoeuvre sets the course they are flown on.

### Group decisions

Once a second, per side: the hunters are those fighting hulls with their duel helm (not escorts). Then:

1. **A charge with escorts and two or more hunters:** the hunter better placed to go round (the one farther round the
   prize from the escorts, ties to the faster) flies **flank** on the prize; the others **pin** the escorts, each the
   escort nearest to it. Kept with hysteresis, as the pack is today.
2. **Otherwise, target distribution:** each hunter takes the hull that is cheapest for it (range over reach, the turn
   to bring guns to bear) plus a penalty for each friend already on it, so a second ship goes to another hull while
   one remains. Focus fire is chosen where a hull is worn under a third or is the last left, and then the two take
   opposite sides of it.
3. **Broadside ships on one target form a line**: the nearer is lead, the other takes line ahead.
4. **Escorts screen**: in line between the charge and the threat.

### Spacing and contact (slice 1)

- **Warships do not overlap; fighters may.** Contact applies to every pair of hulls frigate-sized and up, friend or
  foe, measured on their real footprints (the hull rectangle at flight presence, at each hull's own heading, by
  separating axes, not a circle). Fighters, bombers, drones and boats (vanilla `hullSize` FIGHTER) take no part and
  fly over a capital freely; falling wrecks, hulls in the cloud deck, on a berth or in a low hover are out of contact.
- **Contact is soft.** Where two footprints overlap, each is given a separating acceleration along the line between
  their centres, growing with the depth of the overlap (capped), plus a damping of the speed they are still closing
  at; the parting speed never exceeds half the slower yielding hull's top speed. Way is shared by area, the smaller
  hull giving more. Momentum is respected: the hulls slide apart over seconds, no hard stop and no teleport. Only an
  overlap deeper than a quarter of the narrower beam is also corrected in position, as a backstop. No damage is dealt:
  ramming is not modelled (open question).
- **Avoidance is the behaviour, contact the backstop.** A ship fighting hulls, escorting or leaving bends its course
  away from any hull, friend or foe, that it would close inside the screen line's gap (four cells), looking ahead as
  far as the closest approach or its own station, whichever is sooner, and never less than twice the time the hull
  takes to reach its top speed, so a heavy ship starts giving way in time. A ship flying a manoeuvre turns its bow
  onto the clear course instead of crabbing sideways. Its station is moved clear of a friend's position and station.
  Within a formation (an escort's screen) the gap kept is two cells, and an escort carries its charge's way as well as
  closing on its slot, so it holds its place instead of trailing into the charge's side.
- **A hull its mission holds at rest does not yield.** Contact moves hulls their own helm is flying (fighting a hull,
  escorting, leaving, piloted, passing low) and any hull under way at more than a quarter of its top speed. A hull at
  rest on a mission station gives no way, and two such hulls a mission has laid over each other are left there:
  several evidence scenes stage a capital beside another as a picket or a fixed target.

Measured with slices 1 and 2 (`warship-tactics`, `greyhook-duel`; `-Dbattle.warship.hullContact=false` and
`-Dbattle.warship.tactics=false` are the controls). Over Black Sluice, across the five duel seeds:

| | before | slices 1 and 2 |
| --- | --- | --- |
| least friendly gap (cells) | -29 to -37 | +2.9 to +4.8 |
| ticks with two friends overlapping | 372 to 3,485 | 0 |
| fight, first hit to end (s) | 51 to 181 | 155 to 169 |
| losses (Hounds / Wolfs) | 1-2 / 0-2 | 1 / 1 on every seed |
| damage to the Greyhook's side / to the Wolfs | 4,100-24,334 / 1,800-20,410 | 9,862-12,446 / 7,835-12,427 |
| Wolf targets | both on one hull for long stretches | one Wolf pins the Hounds (38-62% on each), the other flanks to the Greyhook (59-63%) |

The pack against the escorted Dominator holds different hulls on every escorted tick; manoeuvres last 20 to 57 seconds
on average and each hunter's range to its target opens by a quarter or more and closes again. The line of battle keeps
its second in the lead's wake. `fighter-over-capital` is the exemption's control: a Broadsword lies over the Onslaught
for a hundred-odd ticks while a Wolf on the same course goes round it. The Greyhook's fighting withdrawal circuit is
a manoeuvre of the same kind, owned by its mission rather than by the group.

`frigate-climb-out` was re-laid: at flight presence six its Hounds were staged inside the rig and on a map too short
to hold them abreast it. A hull on its lift run obstructs (its escorts keep clear of it) but does not yield: a Hound
circling over the berthed Greyhook was found inside her when her lift ended.

### Slice 3 measured: the hull-fight tempo

D1 is built at its derived value of three (`WarshipScale.HULL_TEMPO`; `-Dbattle.warship.hullTempo=1` is the control).
Read with the manoeuvres of slice 2 in place:

| | tempo 1 | tempo 2 | tempo 3 (shipped) |
| --- | --- | --- | --- |
| Wolf kills Hound (victim's 90-degree turn 11.9s) | 9.0s | 11.9s | 57.8s |
| Venture kills Wolf (victim's turn 13.7s) | 21.6s | 35.6s | 51.7s |
| Enforcer mirror (turn 17.7s) | 78.2s | 82.2s | 131.6s |
| Wolf mirror, Hound mirror, Eagle mirror, Onslaught mirror | unresolved | unresolved | unresolved |
| Black Sluice fight, first hit to end | 155-169s | 151-183s | 110-175s |
| Black Sluice losses (Hounds / Wolfs) | 1 / 1 every seed | 1 / 0-1 | 1 / 0-1 |
| first Hound down after the first hit | 8.4-9.1s | 7.8-13.1s | 8.3-13.1s |

The asymmetric frigate and destroyer matchups now take at least three of the victim's turns, which is the target.
The Hounds are not saved by the clock: the Rusk Wolf's Antimatter Blaster (2,400 energy in one shot) and its two
Harpoons (750 each) against a Hound's 2,000 hull and 400 armour is the base game's alpha, and a tempo changes how often
that blow lands, not what it does. Mirrors of shielded equals do not resolve in the window at any tempo: with
manoeuvres a ship fires only on its runs, extends and comes about on time, and an equal's shield dissipates between
passes. That is the finding to take forward, not a reason to pad hit points.

D2 (a mount silenced, not destroyed, by the first breach) was deferred here and built with slice 4, when the
Hound mirror showed both ships losing their one gun for good and standing off at 58% hull to the window's end.

## Slice 4: equal fights resolve

Measured before the change, every mirror of equal hulls ran to the window's end (above). Four faults were found, not
one, and each was answered by a mechanic rather than a number on a hull:

- **A front-shielded ship turned its back.** Extend and come about turned a Wolf's shield away from the Wolf it was
  fighting. A front-shielded bow ship now ends a run in a **standoff**: a drift astern and to one side (thirty
  degrees round the target, sixty while venting) with nose, guns and shield kept on the target, opening past the
  target's reach only once its own pool has filled. An omni-shielded or shieldless bow ship still comes about
  (`-Dbattle.warship.unshieldedComeAbout=false` is the control). `WarshipDuelHelm.bowStation` also prefers a station
  outside the target's shield arc, so a ship that can get round it does; a defender's nose follows within its slow turn.
- **Equal pools never broke.** A ship stopped starting fire at its soft limit while its equal did the same, so neither
  shield ever dropped. Now a ship **presses** past its own order, up to 80% of its pool (under its own shield's 90%
  drop), when its target is overloaded, or is shield-down and deeper in flux than the shooter, or is at least 60% full
  and ten points deeper than the shooter. Two ships with their shields both locked out do not trade hull at the
  limit: the one further gone breaks off to vent. `-Dbattle.warship.pressing=false` is the control.
- **An Eagle fired one of its three hardpoints.** A hull is drawn twice as large against its guns' reach as in the base
  game, so a hardpoint toward a beam saw a target dead ahead five to fifteen degrees off its line, and one set aft lost
  reach to the hull's length; the heavy needlers never fired. A hardpoint now sights and measures its reach from its
  mount pulled in toward the hull's origin by `HULL_REACH_SHARE`, which restores the base game's angles, and lays
  within its own arc; the helm's heading choice uses the same point. `-Dbattle.warship.hardpointSight=false` is the
  control.
- **No one broke off when beaten.** Doctrine withdrew a ship under 75% hull whose foe had twenty points more. This
  was withdrawn by slice 5: the owner wants duels fought to the death, not decided by a break-off.

D2 is built at the base game's ten seconds of repair, times the tempo; a repaired mount whose holed plate is hit again
is lost. A hit on a hole now counts as a hit on that cell (it used to leave the cell's last-hit time unchanged, so a
second hit over a hole was invisible).

The `warship-mirrors` scene is the evidence: one-on-one duels of Wolf, Hound, Eagle and Onslaught at three seeds, each
side's start drawn independently, doctrine and flux limits on, an observation window of 600 seconds. Its verdicts
are mechanics: the fight is decided (a kill or a withdrawal), not by a single blow (no faster than three of the
loser's ninety-degree turns from the first hit), a standoff keeps the shield on the target (85% of standoff ticks),
the range breathes, and hulls never pass through each other. Readings, after the change:

| Mirror | seed | decided after first hit | how | damage taken west / east | least gap (cells) | three-turn floor |
| --- | --- | --- | --- | --- | --- | --- |
| Wolf | 11 / 23 / 37 | 54.1s / 54.0s / 54.0s | east withdrew (losing) | 3692 / 2808, 3365 / 2808, 3899 / 2808 | 17.8 / 17.6 / 17.5 | 41.0s |
| Hound | 11 / 23 / 37 | 78.6s / 79.8s / 78.5s | east withdrew | 2000 / 2000, 2000 / 2000, 1790 / 2000 | 11.4 / 11.5 / 11.9 | 35.7s |
| Eagle | 11 / 23 / 37 | 391.1s / 327.6s / 391.9s | withdrawal (west, east, west) | 45441 / 47002, 38338 / 32802, 55134 / 38771 | -1.5 / -1.1 / 9.3 | 70.1s |
| Onslaught | 11 / 23 / 37 | 136.4s / 133.8s / 133.8s | withdrawal (east, east, west) | 25057 / 25146, 23580 / 23871, 26028 / 24239 | 5.9 / 5.9 / 5.9 | 124.5s |

Damage counts hull and shield. A negative gap is soft contact inside the pair's tolerance. Before the change none of
the twelve was decided. The mirrors are still nearly even: a Hound mirror trades both hulls away and the withdrawal
comes at the end of it, and a Wolf duel is decided the same way at all three seeds. In a one-on-one duel a front
shield is never outflanked (the share of time under fire with the shooter outside the arc is 0% for every
front-shielded ship), because the defender's nose follows its only attacker; in the `warship-tactics` pack the
escorted Dominator spends all of its time under fire with a shooter outside its arc, which is the flank paying.

Black Sluice, the same five seeds (before the change in brackets):

| seed | first hit to end | damage Greyhook side / Wolfs | Hounds / Wolfs lost | first Hound down after the first hit |
| --- | --- | --- | --- | --- |
| 15305 | 165.3s (121.9s) | 6650 / 6980 (3650 / 8724) | 1 / 1 (1 / 1) | 12.2s (10.8s) |
| 16205 | 154.3s (110.5s) | 5300 / 5375 (5712 / 5956) | 1 / 1 (1 / 1) | 9.2s (13.1s) |
| 16206 | 171.1s (174.7s) | 6361 / 5126 (4750 / 3544) | 1 / 0 (1 / 0) | 8.2s (8.3s) |
| 20261016 | 172.1s (173.7s) | 4622 / 3955 (4668 / 3394) | 1 / 0 (1 / 0) | 8.0s (10.3s) |
| 31337 | 177.6s (162.7s) | 6000 / 4293 (3550 / 4675) | 1 / 0 (1 / 0) | 9.6s (9.8s) |

Losses are unchanged at every seed and the fight runs longer. The first Hound still goes to the Rusk Wolf's alpha; it
fell earlier at two seeds and later at one. The pressing and come-about controls leave the shipped run exactly as it
is, and the hardpoint-sight control moves the first-Hound times by about a second either way, so the difference is
mostly the Wolfs keeping their bows on in the standoff rather than turning away between runs.

## Slice 5: fights at range, to the death

The owner judged the slice 4 fights too close (hulls fighting nose to nose) and dull to watch: duels kept ending with
one ship leaving. Two changes, each with a control:

- **2.5x the fighting distance.** `WarshipScale.HULL_RANGE` (2.5, `-Dbattle.warship.hullRange`) multiplies hull reach,
  so the reach multiplier is now 7.5. The standoff cap is three hull lengths times the hull range. The tempo is no
  longer derived from the multiplier: it stays presence times share (three), because rounds at a hull fly
  `hullRoundSpeed`, the hull range times their speed, so flight time and the share of a fight spent crossing do not
  change. Missiles at a hull fly their spec magnified the same way (speeds, split range and warhead speed). Their
  motors no longer give out short of a hull they can reach, and point defence gets the spec's seconds. A split's
  warhead fan stays centred on its aim, so the jitter shuffles warheads within the fan instead of walking the whole
  fan off the hull. Fighters, point defence against craft and boats are unchanged: hull reach applies only to fire
  at hulls.
- **Battery reach.** The standoff and the tactics range now key off the reach at which three fifths of a ship's
  anti-ship firepower bears (`WarshipDuelHelm.batteryReach`), not its longest gun. At the new range an Eagle stood off
  at its gravitons' reach with its needlers out of range, and stalemated. Hardpoint sight still pulls in by
  `HULL_REACH_SHARE`, not by the hull range: pulling in by more broke the Eagle's hardpoints at the new range.
- **Flux footing in a hull fight.** Air superiority's 50/30 flux pair held a fighting ship's guns silent. While a
  ship fights a hull its flux runs on the duel footing (`-Dbattle.warship.hullFightFooting=false` is the control).
- **Fights to the death.** The losing-exchange withdrawal is gone, and while a ship has a live hostile hull to fight,
  neither hull-limit nor threat withdrawal applies (`-Dbattle.warship.hullFightWithdrawal=true` is the control).
  Mission-ordered withdrawals are separate and still apply. The *Greyhook*'s lift-off belongs to the mission, not
  the doctrine.
- **Hulls coming down or flying in are unseen.** At the new range a hull still descending through the cloud deck, or
  flying in with its centre still past the map's edge, could be fired on before it was in sight. It is not a hostile
  hull until it has come down or crossed the edge, and a ship still past the edge does not fire at hulls either.
- **An extend runs longer.** An extend may last the hull range times its old cap, because opening a range 2.5x longer
  takes that much longer at the same speed. A flank keeps its old cap: a longer one held a flanker on a fixed radius
  for most of a fight.

The `warship-mirrors` verdict is now "decided by a kill", with the three-turn floor kept. Start separations grew with
reach in `warship-mirrors`, `warship-tactics` and `hull-durability`. `frigate-duel` lays its lines forty cells times
the hull range apart, so a crossing Hound still throws the Wolfs' lead off. In `frigate-doctrine`, the withdraw loop
asserts that a crippled hull fights on while a hostile hull lives. The pack loop drops its nearer-runner clause:
under group tactics the flanker is chosen by bearing, and the nearer-runner rule belongs to the tactics-off control.
`warship-tactics` names, but does not judge, a ship shot down mid-fight on the range-breathes verdict.

| Mirror | decided after first hit (seeds 11 / 23 / 37) | three-turn floor | range held on target (slice 4) | least gap (slice 4) |
| --- | --- | --- | --- | --- |
| Wolf | 49.0s / 49.3s / 49.4s, kill | 41.0s | 136-205 (44-84) | 110 (17.5) |
| Hound | 69.2s / 65.6s / 65.5s, kill | 35.7s | 59-149 (40-83) | 26-28 (11.5) |
| Eagle | 286.5s / 276.4s / 276.9s, kill | 70.1s | 180-245 (65-125) | 90 (-1.5 to 9.3) |
| Onslaught | 141.9s / 141.5s / 130.6s, kill | 124.5s | 275-349 (110-147) | 171-175 (5.9) |

Hit share is unchanged by the faster rounds. In Greyhook it is 93-96% (94-98% before); in `warship-tactics`,
line-of-battle 90% (85%), mirror 100% (97%) and pack 89% (89%). In `hull-durability`, Eagle 1330/1355 rounds reach a
hull (980/992) and Onslaught 533/594 (315/343).

Black Sluice is a regression reading only. Its edges are now flown, with the declared airspace gone:

| seed | first hit | first Hound down | first Wolf down | Hounds / Wolfs lost | Greyhook hull, off the map at |
| --- | --- | --- | --- | --- | --- |
| 15305 | 11.5s | 13.8s | 40.9s | 1 / 2 | 99%, 312.5s |
| 16205 | 11.6s | 13.8s | 41.5s | 1 / 2 | 99%, 322.7s |
| 16206 | 11.6s | 13.0s | 42.9s | 1 / 2 | 99%, 313.7s |
| 20261016 | 11.5s | 14.4s | 40.8s | 1 / 2 | 99%, 312.3s |
| 31337 | 11.5s | 14.4s | 40.9s | 1 / 2 | 99%, 312.3s |

The 300x232 map is only about 1.5 Wolf reaches across, so it cannot hold the fight at the new range. Everything is in
reach as soon as the Wolfs cross the edge: the first Hound falls two or three seconds after the first hit, and the
second Wolf dies before it has cleared its entry band. The surviving Hound screens its charge with no hull to fight.
It then drifts away from its screen station onto the south edge, where the backstop holds it, until its charge leaves.
That drift belongs to the escort helm, not to this slice.

## Slices

1. **Spacing and hull contact.** Contact separation between hulls, friendly clearance in the helm. Evidence: the
   `warship-tactics` scene's loops, verdict that no two hulls ever overlap and no friendly pair closes inside the
   clearance after the first contact. `-Dbattle.warship.hullContact=false` is the control.
2. **Group roles, target distribution and manoeuvres.** The group pass and the manoeuvre machine, travel along the
   nose. Evidence: `warship-tactics` loops (a two-ship pack against an escorted capital, a line-of-battle pair, a
   mirror match), verdicts on mechanics (the pack holds different targets while escorts live, manoeuvres last tens of
   seconds and change, range breathes, the line keeps its interval); readings on outcomes. `-Dbattle.warship.tactics=false`
   is the control.
3. **Durability calibration.** D1 and D2, with `hull-durability` and Black Sluice read against the targets above.
4. **Equal fights resolve.** Standoff, pressing, hardpoint sight; `warship-mirrors`.
5. **Fights at range, to the death.** Hull range, battery reach, hull-fight flux footing, no voluntary withdrawal in a
   hull fight.

Each slice re-runs the warship scene set and `greyhook-duel` and keeps their verdicts green.

## Open questions

1. Ramming: should a hull that drives into another hurt it, as the base game's collisions do?
2. Should a pack with a prize and no escorts split by sides automatically (pincer), or only when the prize is
   broadside (so one ship always sits on its unarmed quarter)?
