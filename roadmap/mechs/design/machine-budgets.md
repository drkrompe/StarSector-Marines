# Machine budgets: mount size, flux, plate and shield by weight and family

Status: ACTIVE — the model for sizing mounts and setting a line's flux, plate and shield (owner's picks,
2026-11-10). Every number set by it is first-pass and UNTUNED, for the owner's playtest.

Written: 2026-11-10

Read `mech-lines.md` (weight class by tech family) and `mechs-nouns.md` (mounts, the flux pool, internals) first. The
footprint law is `vehicle-card-sizes.md`'s: every weapon is two columns wide, its rows are its size class, and the
footprint is the only cap on its power.

## The four budgets

A line's weight class and tech family (`mech-lines.md`) set four budgets. Each is a band a line's authored numbers
should land in, not a formula the game computes; no rule checks a line against them (`tech-families.md`, law 4).

### 1. Weapon rows: what a mount may hold

A mount is a size class: light 1x2, medium 2x2, heavy 3x2. A weight class caps both its largest mount and its total
weapon rows:

| Weight | Largest mount | Weapon rows | Today |
| --- | --- | --- | --- |
| Light | 2x2 | about 4 | Hound 4, crawler 2 |
| Medium | one 3x2 | about 7 | Warden 9 (three 3x2 mounts) |
| Heavy | two 3x2 | about 10 | Bulwark 10 |

A vehicle carries one gun card sized by its hull class (small 1x2, medium 2x2, heavy 3x2), so a hull fields far fewer
rows than a mech of its weight. That is the trade between them: a mech is a weapons platform, a hull is plate, speed
and seats.

### 2. Power per row: what a size class buys

Each size class has a band on two axes, damage a second against heavy plate (rating 18) and against soft targets. A
part reaches its class's band on at least one axis and exceeds the next class's band on neither. Proposed bands:

| Class | Against plate | Against soft targets |
| --- | --- | --- |
| 1x2 | up to 10 | 20 to 40 |
| 2x2 | 15 to 25 | 40 to 70 |
| 3x2 | 30 to 45 | 60 to 100 |

Missile racks sit outside the bands: they are bound by ammunition and their loader, not by a rate of fire.

### 3. Flux: how hot a line runs

The stock fit's sustained draw, its guns' flux a second at full fire with racks left out, over what the machine
sheds, is a family's temperament: low-tech runs hot at about 2.5x, midline about 2x, high-tech cool at about 1.25x
with a larger reservoir. A capacitor bank is a part every machine shares, so a chassis meets its band with a plant of
its own beside the bank, as the laser hulls' plant does today (`HullReservoir.plantDissipation`).

### 4. Plate and shield: how a line takes damage

Plate rating rises with weight and falls with tech: low-tech about 14 light, 15 medium, 18 heavy; high-tech about 8,
10 and 12. A high-tech shield replaces plate rather than adding to it: its plate plus a full shield (the pool over the
shield's flux per blocked point) comes to about the plate of the low-tech machine of its weight. A shield's strength
is its flux per blocked point: high-tech 0.6 to 0.75, midline 1.0.

Armour and shields counter different weapons. Plate turns low-penetration fire (lasers, chainguns, light guns) and
loses to high penetration. A shield is blind to penetration: it pays for every point alike, so it turns the big
armour-piercing hits plate cannot and loses to volume. Missiles are the burst that breaks it: a salvo's contact
damage can fill a pool at once (five SRM contacts at 66 is about 250 flux on the crawler's shield), so racks are
high-tech's counter, and point defence is theirs. Measure what a shield is charged for a contact hit before tuning
on it.

## Decided (owner, 2026-11-10)

- **Mounts are capped by weight class.** The Warden's shoulders are 2x2, so it carries seven weapon rows. Its stock
  guns are then the heaviest its mounts take, so its top fit is its full internal refit.
- **The crawler keeps its 2x2 front socket**: any light or medium part, so it can still hurt a machine.
- **Outliers are retuned to their row's band**, never moved to a new footprint. The AC-6 fires 10-damage rounds at
  3 a second and the AC-6 (QS) at 4.5; the Thermal Lance cycles in 7 seconds; the PL-2 fires five pulses a trigger
  (30 flux); the PL-3 six pulses of 20 at penetration 9; the LL-3 cycles in 5 seconds; the DC-4 (QS) in 3.5 with a
  60-damage contact at penetration 14.
- **A chassis carries a plant** to meet its family's flux band (`mechs-nouns.md`, frame values): the Bulwark +6 a
  second, the crawler +3 and 50 more reservoir. The others already sit in their band.
- **A high-tech shield replaces plate.** The heavy skimmer's plate is 350 at rating 12; the crawler's shield blocks
  at 0.75 flux a point, so its 150 pool blocks 200.

- **Missiles may break a shield in one salvo** (owner: "this might be fine"). Missiles are the burst weapon, and
  point defence is their weakness where a laser has none, so a rack overloading a shield is their answer to high
  tech, not a fault to cap.

## Open

- The midline row's numbers, when its lines are designed.
