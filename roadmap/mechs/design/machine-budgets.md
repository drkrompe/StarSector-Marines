# Machine budgets: mount size, flux, plate and shield by weight and family

Status: DRAFT — a proposed model for sizing mounts and setting a line's flux, plate and shield; the owner's picks on
the mech line picks page decide each open rule.

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
armour-piercing hits plate cannot and loses to volume. A missile salvo's contact damage would fill a stock pool in one
salvo if the shield is charged for it (five SRM contacts at 66 is about 200 flux at 0.6, against a stock pool of 100),
which would make missiles high-tech's hard counter; measure what a shield is charged for a contact hit before tuning
on it.

## Where the shipped lines miss

- **Warden**: nine weapon rows on a medium, three 3x2 mounts; the heavy has ten.
- **Bulwark**: its twin chainguns draw 5x the 6 a second every mech sheds, twice the low-tech band.
- **Crawler**: 1.9x on the stock bank with its shield's upkeep, hotter than a high-tech line should run.
- **Heavy skimmer**: nearly the Onager's plate (480 at 16) and a shield on top, which its laser plant lets block
  about 32 damage a second while the gun rests.
- **Over their size class**: the AC-6 (a 2x2 that out-hits the 3x2 HC-20 against plate by 2x), the AC-6 (QS) and the
  Thermal Lance (a 1x2 above every 2x2 laser against plate).
- **Under their size class**: the PL-2, the PL-3 (a 3x2 weaker against plate than the 2x2 PL-2 (AP)), the LL-3 and
  the DC-4 (QS).

## Open

- Whether to cap mounts by weight class (the Warden's shoulders to 2x2).
- The crawler's front socket: 2x2, or 1x2 for a drone screen.
- How to fix the weapon ladder's outliers: retune their numbers, or move them to the footprint their power earns.
- Whether a chassis gets a plant to meet its family's flux band.
- Whether a high-tech shield replaces plate (the heavy skimmer's plate cut) or sits on top of it.
- Whether a missile's contact damage should overload a shield in one salvo.
