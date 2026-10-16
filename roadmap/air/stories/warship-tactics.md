# Story — Warship tactics: fleet manoeuvres, spacing and a fight worth watching

Status: ACTIVE — design written; slice 1 (spacing and hull contact) in progress

Written: 2026-10-16

Read `air-nouns.md` (**Overhead ships**) and `warship-behaviours.md` (all Decided sections, especially ship against
ship, ships of the line and round two) first. Vocabulary (warship, mount, duel helm, standoff law, bow and broadside
ship, pack, charge, escort, screen line, airspace, duel footing, hull-fire hold, ledger) is theirs. This story adds a
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
| Attack run | bow ship | a line that passes the target at its standoff on one side, nose and hardpoints on the target while closing | the target is abaft the beam or the clearance is reached |
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

- **Hulls do not pass through each other.** Today they do: a hull has no contact with another hull at all. The rule
  is physical and applies to every pair of hulls over the battle, friend or foe: where two footprints overlap they are
  pushed apart along the line between them, the lighter (by hull mass) moving more, and their closing speed along
  that line is taken out. No damage is dealt: ramming is not modelled (open question).
- **Friendly hulls keep their distance.** A ship's helm keeps a clearance of its own beam from every friendly hull: its
  station is moved clear of a friend's, and its course is bent away from a friend it would close inside that
  clearance. This is what stops two hunters flying into the same air even before the group layer gives them different
  work.

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

Each slice re-runs the warship scene set and `greyhook-duel` and keeps their verdicts green.

## Open questions

1. Ramming: should a hull that drives into another hurt it, as the base game's collisions do?
2. Should a pack with a prize and no escorts split by sides automatically (pincer), or only when the prize is
   broadside (so one ship always sits on its unarmed quarter)?
