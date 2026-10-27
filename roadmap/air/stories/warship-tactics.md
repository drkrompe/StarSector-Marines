# Story — Warship tactics: fleet manoeuvres, spacing and a fight worth watching

Status: ACTIVE — slices 1 (spacing and soft hull contact), 2 (group roles and manoeuvres) and 3 (hull-fight tempo D1, mount repair D2) built; slice 4 (equal fights resolve: standoff, pressing), slice 5 (fights at range, to the death), slice 6 (one-on-one duels flown for position), slice 7 (the pack relay) and slice 8 (missile behaviour) built

Written: 2026-10-16
Updated: 2026-10-19 — a missile budget per opening and the Greyhook's missile racks; rounds between hulls meet the first hull on their way, with a clear-line fire check; slice 8 missile behaviour; a mismatched pack tanks with its sturdier hull and a ship goes for the hull its guns can hurt; Black Sluice is fought by an Enforcer and a Wolf

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
| Wolf (Dormer) mirror | 22 s | 100% | 6.6 s | 13.7 s | 24.8 s |
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
  escorts), **flank** (go round to the prize), **screen** (an escort interposing), **tank** or **striker** of a
  relay (slice 7), or **free** (a lone ship).
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
| Hold aggro | a relay's tank | nearer than its partners, on the target's battery side, nose and shield on, firing only while the hard flux its shield holds stays under half its pool | the pack swaps |
| Strike | a relay's striker | on the target's quarter at the standoff, round from the tank, off its shield arc where it can, at full fire | it runs high (a vent) or takes the target over (a swap) |
| Vent | a relay ship off duty | nose-on, holding fire, while its partner closes to take over; then away past the target's reach (inside its edge margin, on the course with the most room), shield down | its pool is low: a strike |

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
for a hundred-odd ticks while a Wolf on the same course goes round it. A circuit a mission flies for its own hull is
a manoeuvre of the same kind, owned by its mission rather than by the group (the Greyhook flew one until she was made to fight to the finish).

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
The Hounds are not saved by the clock: the Dormer Wolf's Antimatter Blaster (2,400 energy in one shot) and its two
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

Losses are unchanged at every seed and the fight runs longer. The first Hound still goes to the Dormer Wolf's alpha; it
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
  neither hull-limit nor threat withdrawal applies.
  Mission-ordered withdrawals are separate and still apply; a hull that holds the field (the Black Sluice duel's) is
  never withdrawn by its doctrine at all.
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

## Slice 6: one-on-one duels flown for position

After slice 5 every equal mirror ended in a kill, but it read as a static mirror-image standoff: two ships nose to nose,
shields up, trading. The share of time under fire from outside the front-shield arc was 0% in every one-on-one, and
nobody ever got round anybody. The owner wants ships getting onto the beam, a defender's counter, ships that circle and
jockey, and hulls that each fly to their strengths.

**Why nobody gets round by speed.** At the slice 5 ranges a defender can always pivot faster than an equal can circle
it. A Wolf circling at 150 cells moves round its target at about half a degree a second, while it turns at nine. An
Onslaught at 300 cells moves round at a tenth of a degree against two and a half. Momentum and turn rate are respected,
so no honest orbit outruns a nose. Position is therefore won by choices, read off the two hulls: when a ship turns its
cover or its battery away, and who turns in first.

**What a duel flies.** A duel is one hunter against one hull. A pack, a line and an escorted charge keep their own
choreography, and Black Sluice is not a duel.

- **Approach for the quarter.** A bow ship steers for its target's quiet quarter on its own course
  (`WarshipDuelHelm.bowStation`), rather than at its nose. Two such ships work round one another before either fires.
- **Orbit.** Between runs a front-shielded ship circles under way. It holds its target off the bow at the widest angle
  its shield still covers (a margin inside the arc) at which at least three tenths of its bow battery still bears. The
  angle bends out when the ship is short of the range (never past its cover) and in when it is long. A Wolf holds its
  target at about sixty degrees, where its turret still reaches; an Onslaught holds it where its side batteries do. An
  Eagle's battery is almost entirely in its bow, so circling would silence it: it keeps its nose on in the standoff
  drift instead. Its 270-degree shield is its strength, and almost nothing gets round it.
- **Crossing the T.** A ship is across its target's T when its main battery bears and the target's does not. From an
  orbit it turns in first when it is ahead of the target on flux and hull (`WarshipTactics.advantage`). On any target
  that has turned its cover or its battery away, it holds its run (a **cross**, flown as a run).
- **The counter.** An orbiting ship that has been turned in on, with the target's battery on it and its own not, turns
  in to answer within its own turn rate.
- **The flux race and the break.** Two ships equally full hold their shields on each other and the race decides. A ship
  at its limit that is not losing circles off to vent with its shield still on. The ship whose shield is going
  (overloaded, or 92% full and ten points deeper than its target) **breaks**: it holds a fixed course well off its
  target and opens at full way rather than backing at a quarter of it. The course is as far round as its shield arc
  covers, from 110 to 150 degrees. It comes about the short way once it has vented, is out of reach, or is run down with
  its pool half empty. Its quarter is what the other ship crosses.
- **An overload does not end the duel.** An overloaded ship's guns hold no target, but it keeps its manoeuvre and the
  doctrine still counts it as duelling. Before this change an overload dropped the hull target and the duel with it, and
  the doctrine withdrew the ship as "overloaded under threat".
- **Shieldless passes.** A bow ship without a front shield keeps run, extend and come-about. In a duel it comes back
  early and the short way: to answer a target that has turned in on it, or, when ahead, to cross it.

`-Dbattle.warship.duelManoeuvres=false` is the control. `FrigateDuelHelmScene`'s strongest-gun verdict skips ticks on
which a manoeuvre holds its own course (an extend, a break, an orbit, a come-about). The manoeuvre layer owns the heading
on those ticks, so they say nothing about whether the helm lays the strongest gun.

**`warship-mirrors` verdicts added.** These are mechanics, read against the control:

- **A ship gets round.** At least one side holds a crossing position for 2% of the ticks the two are in reach. A
  crossing position is guns bearing from outside the other's shield arc, or main battery on the other while the other's
  is not on it. The control fails this on nine of twelve loops; its Eagle standoff passes at 2.6-2.8%.
- **Ships keep way on.** Mean speed while in reach is at least 30% of top speed. This does not discriminate: the
  control's standoff drift moves at 35-55%. It stands as a floor against parked hulls.
- **An orbit keeps the shield on the target** for 85% of orbit ticks.

Both sides getting round is a reading, not a verdict. Between equals the first ship to get round usually keeps the
advantage, and that is the fight's own doing.

Readings. A crossing position is off cover or across the T. Distance flown is counted while the ships are in reach.
Exposed is the slice 4 reading: time targeted in reach with the shooter outside the front-shield arc.

| loop | kill after first hit (s) | control (s) | exposed W/E % | crossing position W/E % | crossings W/E | flown W/E (cells) | least gap (cells) | hit share |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| wolf-11 | 116.6 | 85.4 | 0 / 0 | 21.1 / 10.9 | 4 / 4 | 96 / 102 | 32.9 | 97% |
| wolf-23 | 127.6 | 85.4 | 13 / 0 | 16.8 / 23.0 | 3 / 6 | 115 / 110 | 55.0 | 96% |
| wolf-37 | 128.7 | 85.3 | 0 / 0 | 1.1 / 13.7 | 1 / 1 | 150 / 130 | 10.6 | 96% |
| hound-11 | 149.8 | 214.6 | - | 3.3 / 0.4 | 4 / 1 | 167 / 174 | 29.7 | 98% |
| hound-23 | 145.8 | 222.7 | - | 1.9 / 9.6 | 2 / 3 | 179 / 178 | 14.0 | 99% |
| hound-37 | 215.0 | 222.8 | - | 5.6 / 0.4 | 1 / 2 | 273 / 251 | 12.8 | 98% |
| eagle-11 | 872.3 | 768.9 | 0 / 1 | 10.0 / 2.4 | 2 / 1 | 276 / 292 | 74.3 | 100% |
| eagle-23 | 835.5 | 828.6 | 1 / 0 | 2.6 / 10.4 | 1 / 2 | 291 / 265 | 69.1 | 100% |
| eagle-37 | 945.0 | 814.2 | 2 / 1 | 9.6 / 16.0 | 2 / 3 | 341 / 337 | 45.5 | 100% |
| onslaught-11 | 757.4 | 401.3 | 0 / 0 | 7.6 / 12.6 | 12 / 23 | 356 / 354 | 1.8 | 96% |
| onslaught-23 | 427.5 | 390.3 | 0 / 0 | 12.7 / 14.6 | 11 / 8 | 211 / 218 | 4.5 | 95% |
| onslaught-37 | 532.6 | 392.7 | 0 / 0 | 15.5 / 18.8 | 16 / 16 | 257 / 254 | 2.1 | 96% |

In the control every Wolf, Hound and Onslaught loop reads 0-0.6% crossing position each way, and the Onslaughts fly
about 95 cells in reach against 211-356. The Wolf and Onslaught fights take longer, because turn-ins and breaks are time
not spent nose to nose; an Onslaught mirror runs up to twice the control. The Hound fights are shorter. Every loop is
still decided by a kill inside the window, with the three-turn floor kept.

Exposure on the shield-arc reading stays near zero: the defender's counter works, and what gets crossed is the battery,
not the shield, except once a shield has gone. The Onslaughts now jockey close: their least gap is 2-5 cells against the
control's 110, inside the contact tolerance and never overlapping. The warship scene set (85 loops, including
`warship-tactics`, `hull-durability`, the frigate scenes, `greyhook-duel`, `hostile-ship-support`, `ship-escort` and
`hull-broadside`) stays green.

**What the review GIFs show** (1920x1080, through the duel camera):

- **Wolf mirror.** It no longer parks. The two ships come in offset, circle, turn in and break, and the bearing between
  them swings through the fight.
- **Onslaught mirror.** It moves and offsets more than it did, but at a capital's pace, and through the camera it still
  reads largely as two ships facing off at close range.
- **Eagle mirror.** It is still a standoff: nose on and sliding, as its battery and shield dictate.

Getting round an equal at these ranges is real but brief, and is mostly a matter of who turns in first.

## Slice 7: the pack relay

The owner: "Maybe that's the pack tactics - we have one frigate aggro and focus on flux maintain while the other
attacks, then when they get high flux they swap roles and dump flux by re-positioning to catch aggro". Before this
slice two bow ships on one hull took opposite sides of it and flew the same runs and standoffs, both shields up, both
taking fire; against a bigger hull both pools filled and both ships died.

**What a target picks, and the change it needed.** A lone hull choosing between two hunters took the cheapest by range
over its reach, minus a keeping bonus of half a reach. That never responds to a ship turning away, so a tank that broke
off to vent stayed the target's target (measured with the relay on and this rule unchanged: the venting Wolf was the
Venture's target for 75% of its vent). The change is narrow and is the target's own: a ship **set upon by two or more
hulls** whose hull has stopped being a threat to it (less than half its battery laid on it, or out of its own reach)
chooses again with threat weighed (`WarshipTactics.menace`: the share of a hull's battery laid on the chooser from
inside its reach, a hull closing from just outside counting for up to half, and the turn to answer it), keeping only
half its keeping bonus. Scoping it to a ship set upon matters: applied to every chooser it had Wolfs in an even 2v2
drop a worn Hound for one facing them (`frigate-visit` stopped resolving), and a Hound's hardpoint left its target
more often (`frigate-duel-helm`). `-Dbattle.warship.threatTargeting=false` is the control.

**The relay** (`WarshipTactics.relay`, once a second, after target distribution): two or more bow ships on one hull,
not a line of broadside ships and not an escorted charge (those keep line, and flank and pin). One is the **tank**:
the target's own target if it has one in the pack, else the one with most flux to spare. It holds aggro: nearer than
its partners (three quarters of the standoff), on the bearing where the target's battery bears, weighed heavily
against flying round, so it closes where it is; nose and shield on; it starts no salvo past half its pool unless the
target is overloaded. The rest are **strikers**: the target's quarter at the standoff, round from the tank (a
bearing inside ninety degrees of it costs up to the target's full battery) and off its shield arc where it can, and,
not being the target's target, firing on to the pressing ceiling. **The swap**: after twelve seconds in role, a tank
at half its pool hands over to a striker under two fifths and inside its own reach. The new tank closes; the old
holds fire and backs off nose-on (the standoff's vent drift) until its partner is nearer the target, in reach and
with its battery on, then turns away, runs to past the target's reach and holds there with its shield down until
its pool is under three tenths, and comes back as a striker. A tank at 85% with nobody fresh breaks off anyway, as a
duel's break does; a striker at three quarters vents while the tank holds.

**How it fits the standing laws.** Momentum and turning are untouched: every station is flown by the ordinary helm,
along the nose. Soft contact, avoidance and edge pressure act as on any manoeuvre. The front-shield law gets one
exception, decided here: the old tank turns its stern in reach only once its partner is nearer and laid on the
target, so the target's choice has somewhere better to go. Fights to the death are unchanged: a vent leaves the
fight's reach but not the fight. A one-on-one stays a duel.

Measured on `warship-tactics`: two of Dormer's Wolfs against a Venture (the Greyhook's fit) and against an Enforcer,
each from three openings (the target's start swung north, centre, south), each against a control loop with the relay
off. Verdicts (all pass): the pack swaps; once turned away, the venting ship is the target's target for at most half
its vent (28-42%); and the pack spends less of the fight within a tenth of overload than the control. The handover
before the turn is reported separately: the old tank is the target's target for nearly all of it, by design.

| loop | swaps (per min) | vent targeted | pack near overload | pack overloads | Wolfs as target's target (s) | outcome, from first hit | damage to Wolfs / to target |
| --- | --- | --- | --- | --- | --- | --- | --- |
| venture, control | 0 | - | 1.1% | 0 | 237 / 340 | both Wolfs lost, 471s, Venture 99% | 15,000 / 21,275 |
| venture, relay | 13 (0.99) | 29% | 0.3% | 0 | 440 / 460 | unresolved at 790s, Wolfs 7% / 15%, Venture 99% | 37,274 / 22,987 |
| venture north, control | 0 | - | 1.0% | 0 | 421 / 308 | both lost, 610s | 22,000 / 22,900 |
| venture north, relay | 12 (0.92) | 28% | 0.6% | 0 | 598 / 292 | both lost, 780s, Venture 98% | 29,800 / 29,269 |
| venture south, control | 0 | - | 1.9% | 0 | 273 / 304 | both lost, 459s | 12,900 / 20,958 |
| venture south, relay | 12 (0.95) | 42% | 1.3% | 0 | 348 / 522 | both lost, 754s, Venture 99% | 40,200 / 19,374 |
| enforcer, control | 0 | - | 2.0% | 0 | 133 / 44 | both lost, 95s, Enforcer 100% | 13,390 / 200 |
| enforcer, relay | 5 (0.45) | 30% | 1.3% | 1 | 451 / 288 | both lost, 664s, Enforcer 24% | 25,175 / 30,212 |
| enforcer north, control | 0 | - | 3.4% | 1 | 246 / 225 | both lost, 373s, Enforcer 92% | 21,340 / 18,072 |
| enforcer north, relay | 6 (0.58) | 34% | 0.4% | 0 | 474 / 225 | **Enforcer destroyed**, 619s, Wolfs 35% / 44% | 23,058 / 37,250 |
| enforcer south, control | 0 | - | 1.5% | 0 | 557 / 175 | both lost, 641s, Enforcer 86% | 20,890 / 26,452 |
| enforcer south, relay | 4 (0.41) | 37% | 0.4% | 0 | 184 / 482 | **Enforcer destroyed**, 585s, Wolfs 18% / 96% | 19,490 / 37,700 |

Damage counts shield and hull. The relay's one overload against the Enforcer is the opening: the Enforcer outranges a
Wolf (270 cells against 203), so the first tank absorbs its whole approach alone with no partner in reach to take
over. Against the Enforcer the relay turns three losses into two wins and a near thing; against the Venture it keeps
both Wolfs alive about half as long again and wins nothing, because a Venture's omni shield takes the strikers' fire
as well as the tank's and vents between swaps (it ends at 98-99% hull every time). Outcomes swing with small
changes, so they are readings: while the threat rule was being shaped, the centre opening's control was lost in 95
seconds under one version of it and won in 512 under another.

**In the Black Sluice duel the relay turns a lost fight into an unresolved one, and that fails its verdict.** Measured
on `greyhook-duel` after the Black Sluice rebalance (Hounds gone, Dormer's Wolfs refit), five seeds, relay on against
`-Dbattle.warship.packRelay=false`:

| seed | control: decided, Greyhook hull, Wolfs down at | relay: at 897s, Greyhook / Wolf hull | damage to Greyhook side / to Wolfs (control; relay) | hull damage to Greyhook / to Wolfs (control; relay) |
| --- | --- | --- | --- | --- |
| 15305 | 838s, 67%, 503s and 838s | unresolved, 72% / 89%, 63% | 45,327 / 16,700; 51,643 / 28,916 | 3,252 / 3,000; 2,843 / 716 |
| 16205 | 725s, 90%, 500s and 725s | unresolved, 96% / 90%, 62% | 36,282 / 15,500; 46,827 / 22,028 | 982 / 3,000; 427 / 728 |
| 16206 | 676s, 93%, 349s and 676s | unresolved, 95% / 75%, 93% | 28,329 / 17,200; 49,537 / 23,485 | 654 / 3,000; 537 / 485 |
| 20261016 | 662s, 95%, 490s and 662s | unresolved, 70% / 92%, 53% | 33,124 / 17,200; 44,059 / 34,415 | 524 / 3,000; 2,959 / 815 |
| 31337 | 555s, 93%, 431s and 555s | unresolved, 97% / 91%, 93% | 25,953 / 16,000; 52,646 / 18,637 | 678 / 3,000; 346 / 237 |

The control passes all fifteen verdicts on every seed: the Greyhook kills both Wolfs. With the relay no ship is lost
on any seed and `the-duel-is-decided-by-destruction` fails on all five: both sides are flying at the end of the
window. The pack does more damage than the control (44-53 thousand against 26-45), nearly all of it into the omni
shield, and takes a fraction of the hull damage. The Greyhook's hull wears down only late and only on some seeds.
Two levers were tried and dropped, because neither decided a seed:
- a striker venting where it stands rather than breaking off. It also failed `venting-ship-not-targeted` on one
  `warship-tactics` opening and `pack-flux-below-overload` on another;
- a tank firing deeper into a target losing the flux war: to the ordinary ceiling, then to 0.7 of its pool. The
  Greyhook ended lower (41-94%) and one seed lost a Wolf, but nothing was decided, and each version failed
  `pack-flux-below-overload` on one `warship-tactics` opening.

What the duel is for is a balance question that belongs to the mission, not the tactic: whether the Wolfs fighting
as a relay should be able to outlast the Greyhook.

**Re-measured on hard flux (shields hold hard flux only, guns leave the drop level): the duel is decided, but past
its window.** Same five seeds, window 45,000 ticks (1,500 s), and again with the window lifted to 108,000 ticks as a
reading only (the scene's bound is unchanged). Greyhook hull and Wolf hull are at the end; blocked is rounds each Wolf's
shield took.

| seed | control (`packRelay=false`, `threatTargeting=false`): decided, Greyhook, blocked | relay at 1,500 s: Greyhook / Wolfs, blocked | relay, longer window: decided, Greyhook |
| --- | --- | --- | --- |
| 15305 | Greyhook, 1,201s, 84%, 219 / 176 | unresolved, 65% / 52%, 22%, 185 / 223 | Greyhook, 1,804s, 30% |
| 16205 | Greyhook, 1,267s, 88%, 160 / 128 | **Greyhook, 1,420s**, 82%, 209 / 256 | same |
| 16206 | Greyhook, 903s, 95%, 137 / 121 | unresolved, 71% / 0%, 38%, 235 / 258 | Greyhook, 1,573s, 71% |
| 20261016 | Greyhook, 1,003s, 77%, 129 / 206 | unresolved, 43% / 19%, 33%, 214 / 252 | Greyhook, 1,611s, 39% |
| 31337 | Greyhook, 823s, 96%, 121 / 112 | unresolved, 68% / 73%, 51%, 147 / 223 | Greyhook, 1,768s, 45% |

The relay no longer stalemates: it lengthens the fight by about half (Wolf block share 69-84% against 58-75%) and the
Greyhook still wins every seed, but four of five end after the window, so `the-duel-is-decided-by-destruction` fails
on them. `warship-tactics` also fails `pack-flux-below-overload` on the two southern openings (Venture: 1.0% against
the control's 0.7%; Enforcer: one overload against none). With the relay off and threat targeting still on, seed 15305
fails `held-hulls-stay-out-of-their-edge-margin` (a Wolf 27.9 cells into its 38.9-cell margin for 15,066 ticks).
One lever was tried and dropped: the tank's fire ceiling counting only the hard flux its shield holds (gun flux vents
behind the shield). It turns the southern `warship-tactics` openings green and makes the duel close (Greyhook 14-56%
at 1,500 s; with the longer window the Wolfs win 15305 and the Greyhook ends 5-56% elsewhere, at 1,570-1,908 s), but
decides nothing inside the window and has three seeds fail the edge margin, the venting Wolfs holding past the
Greyhook's reach close to their own edge.

### The two fixes, and Black Sluice with Enforcers

Both fixes are general, with no Black Sluice case in them.

- **A tank's fire ceiling counts the hard flux its shield holds.** A tank starts no salvo once its hard flux (what
  its shield's absorbed hits put in the pool) reaches half the pool, unless the target is overloaded. Its own guns'
  flux is soft and vents behind its shield, so it no longer counts. The ordinary limits (the soft limit, and the
  shield's drop level) still apply to the whole pool.
- **A venting ship vents inside its edge margin.** The bug: a venting ship ran straight away from the target until
  it was past the target's reach. On a small map, when that course met the edge margin first, the ship never got
  out of reach, so it held its course into the edge, pinned against the pressure. Now it takes the course with the
  most room toward its hold. That is straight away if the map allows, otherwise up to a right angle off it, in
  fifteen-degree steps, each step off costing a little against the room it buys. It takes the course again if the
  target moves and the course runs short. It runs to the hold, settled inside its margin. Where the map cannot fit
  the full distance it holds short of it, nose back toward the fight (`WarshipTactics.ventCourse`, `ventRoom`).

`warship-tactics`: all sixteen loops pass. Before the fixes the two southern openings failed
`pack-flux-below-overload`. Relay against control (`packRelay=false`), Wolf pair against each target:

| opening | swaps (per min) | vent targeted | pack near overload | outcome from first hit, relay / control | target hull at end, relay / control |
| --- | --- | --- | --- | --- | --- |
| venture | 8 (0.62) | 39% | 0.6% / 1.2% | both Wolfs lost 773s / 612s | 95% / 99% |
| venture north | 16 (1.22) | 36% | 0.5% / 1.0% | one Wolf lost, unresolved at 784s / both lost 710s | 98% / 100% |
| venture south | 6 (0.54) | 40% | 0.5% / 0.7% | both lost 663s / 417s | 93% / 99% |
| enforcer | 7 (0.68) | 45% | 0.9% / 5.0% | both lost 618s / 112s | 72% / 100% |
| enforcer north | 6 (0.57) | 35% | 0.6% / 2.6% | **Enforcer destroyed** 628s / both lost 103s | 0% / 100% |
| enforcer south | 1 (0.22) | 36% | 0.6% / 0.9% | both lost 271s / 658s | 100% / 88% |

**The old Wolf duel's edge failure is gone.** It was replayed as a diagnostic: the Wolfs in their refit
(`-Dbattle.scene.greyhookDuel.variant=wolf_hegemony:wolf_hegemony_Rusk`), five seeds, 1,500 s. With the relay, no
seed fails `held-hulls-stay-out-of-their-edge-margin`; before the fixes three did. The relay Wolfs now wear the
Greyhook to 0-24% hull by the window's end on four seeds and kill her on one (20261016, 1,226 s). They make 23 to
26 swaps a fight. With the relay off and threat targeting on, seed 15305 also stays clear of its margin now, and the
Greyhook wins all five seeds at 774 to 1,280 s.

**Black Sluice is now fought by two Enforcers** (`a-small-war.md`; `warship-behaviours.md`, the Greyhook fights
alone). `greyhook-duel`, five seeds, relay on, all fifteen verdicts pass:

| seed | first hit after the order | Greyhook down, after first hit | swaps / vents | Greyhook breaches / mounts out | Enforcer hull |
| --- | --- | --- | --- | --- | --- |
| 15305 | 75.0s | 221s | 1 / 1 | 37 / 1 | 100% / 100% |
| 16205 | 75.4s | 272s | 1 / 2 | 36 / 1 | 100% / 100% |
| 16206 | 84.5s | 334s | 1 / 2 | 44 / 2 | 100% / 100% |
| 20261016 | 75.6s | 253s | 1 / 1 | 37 / 0 | 100% / 100% |
| 31337 | 75.4s | 272s | 1 / 2 | 20 / 0 | 100% / 100% |

The control, with `packRelay=false`, takes 658 to 940 s and fails `friendly-hulls-do-not-overlap` on 16205. With
threat targeting off as well, two seeds are unresolved at 1,500 s. The relay is what brings the fight inside minutes.
The Greyhook never takes an Enforcer's hull: her kinetic Heavy Autocannons glance off its plate.

### A mismatched pack, and Black Sluice with an Enforcer and a Wolf

Dormer's pair at Black Sluice became one Enforcer and one Wolf (`a-small-war.md`; `warship-behaviours.md`, Dormer's
Enforcer and Wolf), which showed two places the relay and the target choice assumed hulls of a kind. Both fixes are
general.

- **The tank is the sturdier hull.** A pack with a hull at least half again as sturdy as each partner (remaining hull
  over the share of a middling round its armour lets through, plus what its shield's pool absorbs;
  `WarshipTactics.sturdiness`) gives it the target first, lets a lighter partner relieve it only when its shield is
  going, and hands the target back to it from a lighter tank as soon as it is fresh. Before, the Wolf tanked whenever
  the Greyhook happened to look at it, 20 to 45 percent of the fight; now the Enforcer holds her eye throughout.
- **A ship goes for the hull its guns can hurt.** With two or more hulls to choose from, the share of a chooser's
  anti-ship gun battery a hull's armour turns away costs up to two reaches of range (`WarshipTactics.bite`;
  `-Dbattle.warship.biteTargeting=false` is the control). The Greyhook's autocannons glance off the Enforcer, so she
  turns on the Wolf 93 to 98 percent of the time; with the control she is on the Enforcer 62 to 81 percent of it and
  two of five seeds are still undecided at the twenty-five-minute bound.

`WarshipPackRelayTest` pins both. The warship scene set and `greyhook-duel` (166 loops) stay green. `greyhook-duel`,
eight seeds, production refits (the Enforcer in `RUSK_REFIT`, the Wolf as fitted, the Greyhook in `GREYHOOK_REFIT`):

| seed | first hit after the order | Greyhook down, after first hit | Wolf | Greyhook breaches / mounts out | Wolf vents | Enforcer hull |
| --- | --- | --- | --- | --- | --- | --- |
| 15305 | 66s | 472s | 45% | 51 / 0 | 4 | 100% |
| 16205 | 66s | 623s | 49% | 43 / 0 | 6 | 100% |
| 16206 | 66s | 527s | 50% | 31 / 1 | 4 | 100% |
| 20261016 | 66s | 428s | down at 474s after the order, 20s before her | 47 / 0 | 5 | 100% |
| 31337 | 66s | 556s | 9% | 37 / 0 | 5 | 100% |
| 1 | 66s | 471s | 63% | 36 / 0 | 4 | 100% |
| 2 | 66s | 522s | 40% | 47 / 0 | 5 | 100% |
| 777 | 66s | 448s | 62% | 40 / 0 | 3 | 100% |

The relay makes no swaps: the refitted Enforcer never has to break off. The fight is longer than the three to six
minutes asked for. She fires 94 to 187 rounds to their 772 to 1,188, in bursts between shield lockouts.

## Slice 8: missile behaviour

The owner wants missile exchanges between hulls to be tactical and fun to watch. Before this slice a missile
launched the moment its target was inside the ship's gun reach, and the only choice in it was the finisher's hold for
a worn or overloaded hull: every rack rippled off at first contact, into a raised shield as readily as into a hole.
And point defence barely touched a missile fired at a hull (`warship-missile-exchange` control, before the point
defence change: 1 to 7 engagements a fight, 95 to 100 percent of missiles and warheads landing), so a missile was free
damage.

**Windows by role (`MissileWindow`).** A launcher's role comes from the weapon table's `primaryRoleStr` and the spec:
torpedo, finisher, anti-shield, saturation (a carrier, or a torpedo launcher with five or more rounds) or general. A
window is read off the target at launch from where the launcher stands: overload, shield down or locked out, flank
(outside the shield's arc), high flux (70 percent), worn (the finisher hull share), strike (a relay striker while the
target's eye is on the tank) and salvo (a packmate launched into an opening at it in the last 2.5 hull-fight seconds);
a hull with no shield, and a raised shield, are standing conditions rather than openings. Each role launches only into
the windows it can use (`air-nouns.md`, Overhead ships). A launch into an opening opens a salvo for the side's other
launchers on that target. `-Dbattle.warship.missileWindows=false` is the control.

**Flight.** A hull missile leaves along the barrel's bearing in a launch arc (up to 40 degrees off its line for a guided
missile), weaves, and flies its last quarter straight in; a heavy body is pointed (12 degrees), barely weaves and runs
in straight over its last 45 percent. Timing and landing are unchanged. `-Dbattle.warship.missileFlight=false`.

**Point defence in the hull fight's geometry.** A hull missile flies 7.5 times the base game's distance at 2.5 times
its speed; point defence still reached at the shared density and cycled at the base game's rate, so its umbrella was a
sliver of a hull a missile crossed in a tick or two. Against a missile or warhead aimed at a hull, a mount now reaches at
its hull reach, judges the shot at the base game's distance and speed, and cycles on the hull fight's clock, so it has
the base game's shots at a missile crossing it (`-Dbattle.warship.pdHullReach=false`). A round aimed at the mount's own
hull is weighed as half as far off (`-Dbattle.warship.pdThreatFirst=false`). No flares or evasive turns were built: no
fit flown carries a flare system, and a hull at atmospheric handling cannot move a meaningful share of its length in a
missile's flight.

**Magazines.** The duel footing's magazines now refill where the base game's do (`ammo/sec` in its `reload size`, on the
hull fight's clock, never past the magazine); a Dominator's Pilums are the case in the scene.

**Evidence: `warship-missile-exchange`.** Two matchups, each against its control: `fleet` (Enforcer Balanced, Shrike
Attack, Vigilance Strike against Medusa Starting, Hammerhead Balanced, Vigilance Strike) and `pack` (two Enforcer
Balanced flying the relay on a Dominator Support). Verdicts: every gated launch went into a window its role can use;
torpedo, finisher and saturation launches go into an opening more often than the control's (or both all but always);
the pack's launchers join salvos or strike while the tank holds aggro; point defence engages missiles where they were
sent at a hull carrying it; and no magazine ever holds or spends more than it carries and has reloaded.

Readings (500 seconds a loop; a launch is a volley; "openers" are torpedo, finisher and saturation launches; hit and
intercepted are shares of single missiles and warheads that reached an end):

| loop | launches by window | openers into an opening | joined a salvo or strike | PD engagements / kills | hit / intercepted | reloads | outcome at 500 s |
| --- | --- | --- | --- | --- | --- | --- | --- |
| fleet, control | shield-down 15, shield-up 9, worn 2, high-flux 1 | 89% of 18 | 7 | 75 / 24 | 35% / 65% | 0 | west all lost (488 s), east none |
| fleet | shield-down 14, shield-up 5, high-flux 2, salvo 2 | 100% of 15 | 7 | 57 / 19 | 54% / 46% | 0 | west two lost, east none |
| pack, control | shield-up 12, shield-down 7, high-flux 6 | 52% of 25 | 7 | 2 / 1 | 98% / 2% | 34 | none lost |
| pack | shield-down 19, high-flux 10 | 100% of 29 | 17 | 11 / 3 | 96% / 4% | 34 | none lost |

The shield-up launches are Sabots, which is their window. In the pack's control the Dominator rippled its Pilums into
the Enforcers' raised shields from the start; with windows it waits for a shield to drop or a pool to run high, and the
Enforcers' Harpoons and Reapers go in together (17 launches into a packmate's salvo or a striker's moment against 7).
Before point defence took the hull geometry, the same control fights saw 1 to 7 engagements and 95 to 98 percent of
missiles land; the Enforcers carry no point defence, so the Pilums still land on them. The pack against a Dominator is
a slow fight that neither side decides in 500 seconds.

**Elsewhere in the warship set.** Every other warship scene passes except two `warship-tactics` relay loops,
`wolfs-vs-enforcer-north` and `wolfs-vs-enforcer`, which now fail `venting-ship-not-targeted` (100% of a 30.8 s vent
and 54% of a 94.5 s one). Nothing in the relay changed. The changes shift a sensitive fight: with all four
`battle.warship.*` switches off both loops pass, `pdHullReach=false` alone clears north, and `missileWindows=false`
alone clears centre. On north the striker starts its vent about 210 cells from the Enforcer, inside its 270 reach.
At hull tempo it covers about a cell a second toward its hold at 310, so its pool is drained before it is out. The
Enforcer keeps the venting Wolf as its assigned target throughout, although its fresh tank closes to 200 cells.
Whether a lone target should turn to the tank, or a striker should start its vent nearer reach's edge, is a
relay question left open here. In `frigate-missiles` the Wolf's PD lasers stop
all four Harpoons the Brawler sends at it. Black Sluice (`greyhook-duel`, five seeds) passes all its verdicts: the
Greyhook falls 174 to 293 seconds after the first hit (221 to 334 before), neither Enforcer loses hull.

### Restraint per opening, and the Greyhook's missile racks

A window said when a launch could go, never how many. At Black Sluice (seed 15305) the Enforcer put all eight volleys
into one 16-second shield lockout, one every 2.27 s (the tube ripple at hull tempo), the Wolf put five Harpoons into
one stretch, no launch joined a salvo, and the Wolf's first Harpoon went into "shield down" at Greyhook flux 0.02: her
automatic shield simply was not up yet at rest.

- **A shield lowered at rest is no opening** (`MissileWindow.Kind.SHIELD_IDLE`). An automatic shield rises for the
  first hostile round on its way (`WarshipSystem.sensesThreat` reads in-flight shells), so a torpedo or finisher sent
  at a resting shield meets a raised one. Only a shield locked out by a full pool, or held down by hand, reads as
  down. A Sabot may go into a resting shield, which its flight raises.
- **An opening is shared by a side and has a budget per ship** (`MissileOpenings`). Forced openings (overload, a
  shield that cannot rise) last while the target cannot defend itself; standing ones (flank, high flux, worn, strike,
  a salvo with no opening of its own) last 8 hull-fight seconds. Overload and worn are decisive, and a forced or
  decisive window during a lesser opening starts a new one. A ship spends at most a pair of volleys into one, or a
  quarter of its remaining gated volleys if more; a torpedo or finisher goes into a lesser opening only while the
  ship still keeps a pair back for a decisive one. Packmates spend into the same opening, so the salvo is the pack's.
  General racks and launches into a standing non-opening (Sabot on a raised shield, torpedo on a shieldless hull)
  are paced only by the ripple. `-Dbattle.warship.missileRestraint=false` is the control.
- **The Greyhook carries a Harpoon and a Sabot rack** (owner, 2026-10-19: "refit her with vanilla missile pods in WS
  004/007 and drop the PD claim"). The two PD lasers there sat in fixed-forward hardpoints (arc 5) and engaged once
  in a whole fight. One of each, as a salvage captain would keep: the Sabot works a shield toward lockout, the Harpoon
  is the finisher for it; both are vanilla small racks (4 OP each), so she stands at 63 of the Venture's 115 OP. Her
  Sabots outreach both sides' guns and open the fight about 41 s after the order, before Dormer is in gun range, so the
  duel's radio now hears Dormer closing when the first hull fire comes first; the run, timed from the first hull fire,
  comes about 20 s sooner.

`warship-missile-exchange`, restraint against its control (500 s; all verdicts green, the new
`missiles-keep-to-the-opening-budget` included):

| loop | launches by window, control / restraint | openers into an opening | openings | PD engagements / kills | lost W / E |
| --- | --- | --- | --- | --- | --- |
| fleet | shield-down 15, shield-up 7, high-flux 1, overload 1 / shield-down 5, shield-idle 5 (Sabots), shield-up 4, worn 4 | 100% / 100% | — / 5 | 29 / 8 against 42 / 12 | 2 / 2 against 3 / 0 |
| pack | shield-down 21, high-flux 10, overload 2 / high-flux 14, shield-down 7 | 100% / 100% | — / 8 | 31 / 7 against 0 / 0 | none / none |

In the fleet each ship spends a pair at a time (the Hammerhead 2, 2, 2 into three openings, two of them decisive). In
the pack the Enforcers launch nothing: every launch they made before went into the Dominator's resting front shield,
which their own missiles would have raised; it never locks out in 500 s. The Dominator's Pilums keep their budget
(3, 2, 1, 3, 3, 3, 3, 3 against 6 to 8).

`greyhook-duel`, five seeds, restraint and refit (merged with the turret-laying change; control in brackets is
`missileRestraint=false` with the same refit):

| seed | launches per opening | missiles fired / PD engagements / PD kills | outcome |
| --- | --- | --- | --- |
| 15305 | Enforcer 2,2,2 | 9 / 9 / 3 (13 / 23 / 5) | Wolf down 196 s; she escapes 267 s after the order, 73% hull (58%) |
| 16205 | Greyhook 1; Enforcer 2,2,2; Wolf 1 | 11 / 14 / 4 (14 / 23 / 6) | Wolf down 164 s; escapes 262 s, 74% (49%) |
| 16206 | Greyhook 1; Enforcer 2,2 | 8 / 14 / 4 (14 / 25 / 5) | Wolf down 139 s; escapes 275 s, 89% (60%) |
| 20261016 | Enforcer 2,2,2; Wolf 2,2,2 (last decisive) | 15 / 9 / 3 (17 / 23 / 5) | escapes 264 s, 45% (40%), Wolf at 22% |
| 31337 | Enforcer 2,2,2; Wolf 2 | 11 / 9 / 3 (17 / 23 / 5) | escapes 259 s, 60% (47%), Wolf at 32% |

The Enforcer's Reapers are kept as its reserve and no opening is decisive enough to spend them. Every Greyhook missile
that dies is the Wolf's lasers' work: she launches 3 to 4, and 3 to 4 are shot down. On main she escapes on all five
seeds too (33 to 73% hull, 293 to 310 s), so the escape-or-crash mix of the earlier duel is now all escape; that is a
reading for the duel's tuning, not something restraint changed. The warship set (170 loops) is 168 green, red only on
`wolfs-vs-enforcer-north` and `wolfs-vs-venture-south` (`venting-ship-not-targeted`), both red on main; the
`warship-mirrors` Eagle loops that are red on main are green here.

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
6. **One-on-one duels flown for position.** Approach for the quarter, orbit, cross the T, counter, break;
   `warship-mirrors`, with `-Dbattle.warship.duelManoeuvres=false` as the control.
7. **The pack relay.** Tank, striker, swap and vent, the sturdier hull as tank, threat-aware target choice for a ship
   set upon, and a chooser going for the hull its guns can hurt;
   `warship-tactics`' Wolf-pair loops against their controls, `-Dbattle.warship.packRelay=false` and
   `-Dbattle.warship.threatTargeting=false`.
8. **Missile behaviour.** Launch windows by role and salvos, the hull run's flight shape, point defence in the hull
   fight's geometry and threat-first, magazine reloads; `warship-missile-exchange` against its controls,
   `-Dbattle.warship.missileWindows=false`, `missileFlight`, `pdHullReach` and `pdThreatFirst`.

Each slice re-runs the warship scene set and `greyhook-duel` and keeps their verdicts green.

## In-flight collision (2026-10-19)

A round between hulls used to resolve only at its aim point, on hostile hulls its blast reached: nothing between
stopped it, and a friend was never hit. It now meets the first hull on its way (`air-nouns.md`, **Overhead ships**),
and a mount holds a round whose line crosses a friend. `-Dbattle.warship.inFlightCollision=false` and
`-Dbattle.warship.clearLineFire=false` are the controls; `warship-screen` is the acceptance.

`warship-screen` (40 seconds, held stations): with collision on, the escort on the line took all 56 of the hits from
rounds aimed at its charge and was worn to 3% of its hull, and the charge was not touched. In the control the charge
took 55 and 1,841 round-ticks were spent inside the escort's footprint. A friend held across part of the line took 16
of 56 hits (29%) without the clear-line check and 0 of 40 with it, while the shooter still put 25 hits on its target.

The warship scene set and `greyhook-duel` (170 loops): 168 green against the control's 165, of which the three red
in the control are `warship-screen` loops that demand collision. `wolfs-vs-enforcer-north`'s `venting-ship-not-targeted`
is red in both. `wolfs-vs-venture`'s `pack-flux-below-overload` goes red with the clear-line check: the relay is near overload for 1.3% of the
fight against its relay-off control's 0.8%, with no overloads in either. The Wolfs hold 20 mount-seconds there, and
the verdict is green with the check off. A raised shield is asked of where a round meets the plate as well as of its
bubble, since a drawn hull reaches past its bubble: without that, `hull-broadside`'s front shield took nothing from
ahead.

| reading (on / control) | Black Sluice, five seeds | `pack-vs-escorted` | `line-of-battle` |
| --- | --- | --- | --- |
| decided, seconds after the order | 382–597 / 400–585 | 730 / 489 | unresolved / unresolved |
| friendly fire, share of hull hits | 0.1–2.6% / 0 | 0 / 0 | 0.6% / 0 |
| rounds stopped by a hull they were not fired at | 2–28 / 0 | 0 / 0 | 60 / 0 |
| hits on escorts | 0 / 0 | 290 / 160 | — |
| mount-seconds held for a friend in the line | 31–538 (Dormer's pair) / 0 | 669 / 0 | 260 / 0 |
| Dormer ships lost | 0, 1, 0, 1, 1 / none | — | — |

At Black Sluice only Dormer's pair holds fire: an Enforcer and a Wolf on one hull lay lines across each other. The
check reads the line at the moment of firing, so a friend moving into a round's path in flight, a round carrying on
past its target and a guided missile's weave can all still find a friend; which of them makes up the 0.1-2.6% is not
yet measured. Dormer's hull damage taken roughly
doubles (1,417–1,500 against 696–1,089) and he now loses a ship on three seeds of five; the Greyhook still falls on every
seed. In `pack-vs-escorted` the escorts take the pin's fire on their plate and both die where the control lost none.
A screen's charge loses the guns that bear through it, and no helm station moves to reopen a line: that is the open
question below.


## Open questions

1. Ramming: should a hull that drives into another hurt it, as the base game's collisions do?
2. Should a helm reopen a fouled line (a striker sliding off the tank's line, an escort standing a little off its
   charge's main battery), or is a held gun the honest price of the formation?
3. Should a pack with a prize and no escorts split by sides automatically (pincer), or only when the prize is
   broadside (so one ship always sits on its unarmed quarter)?
