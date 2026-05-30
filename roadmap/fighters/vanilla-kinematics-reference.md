# Vanilla fighter kinematics — reference data

> **Offline design reference only.** These numbers were scraped from the
> read-only game install (`starsector-core/data/hulls/`) for tuning the scale
> factor and sanity-checking feel. They are **not** a runtime data path — at
> runtime, read kinematics off `ShipHullSpecAPI.getEngineSpec()` (see
> [`overview.md`](overview.md) § "Where the data lives").
>
> Source: Starsector 0.98a-RC8, `ship_data.csv` + `wing_data.csv`. Re-scrape if
> the game version changes.

## Units

| Field | Unit |
| --- | --- |
| max speed | su/sec |
| acceleration / deceleration | su/sec² |
| max turn rate | deg/sec |
| turn acceleration | deg/sec² |
| mass | (collision/ramming momentum only — not used in steering) |

`su` = Starsector unit ≈ 1 pixel at default zoom.

## Fighters & drones (`ship_data.csv`)

Sorted fast → slow. "wing num" = fighters per wing (from `wing_data.csv`).

| Hull | role | spd | accel | decel | turnRate | turnAcc | mass | wing num |
| --- | --- | --: | --: | --: | --: | --: | --: | --: |
| thunder | heavy interceptor | 450 | 1000 | 750 | 225 | 500 | 30 | 2 |
| spark | interceptor drone (Remnant) | 335 | 500 | 400 | 90 | 270 | 15 | 5 |
| talon | interceptor | 325 | 400 | 300 | 150 | 300 | 10 | 4 |
| wasp | interceptor drone | 325 | 500 | 450 | 180 | 360 | 10 | 6 |
| gladius | heavy fighter | 300 | 400 | 300 | 150 | 300 | 40 | — |
| drone_terminator | PD support drone | 300 | 400 | 400 | 200 | 400 | 5 | 2 |
| claw | fighter | 275 | 350 | 300 | 120 | 240 | 20 | — |
| broadsword | heavy fighter | 200 | 400 | 350 | 90 | 180 | 30 | 3 |
| longbow | missile fighter | 200 | 400 | 300 | 90 | 180 | 20 | — |
| dagger | torpedo bomber | 175 | 350 | 250 | 90 | 180 | 10 | 3 |
| flash | bomber | 170 | 240 | 200 | 40 | 120 | 30 | — |
| xyphos | support fighter | 160 | 300 | 250 | 90 | 180 | 30 | — |
| cobra | bomber | 160 | 200 | 200 | 30 | 120 | 40 | — |
| piranha | bomber | 150 | 200 | 200 | 40 | 120 | 30 | 3 |
| perdition | bomber | 140 | 150 | 150 | 30 | 90 | 15 | — |
| warthog | heavy fighter | 130 | 180 | 150 | 100 | 200 | 40 | — |
| trident | heavy bomber | 130 | 150 | 125 | 30 | 90 | 50 | 2 |
| mining_drone | civilian | 125 | 75 | 75 | 45 | 90 | 50 | 5 |
| drone_borer | mining drone | 100 | 200 | 200 | 180 | 360 | 5 | — |

## Feel patterns (the takeaways)

- **Role separates by turn rate, not speed.** Interceptors turn 150–225
  (turnAcc 300–500); bombers turn 30–90 (turnAcc 90–180). That contrast *is*
  the gameplay — interceptors win dogfights, bombers need escort. Preserve
  these ratios when scaling.
- **accel ≈ 1.5–3× maxSpeed** across the board → fighters reach top speed in
  well under a second; they feel snappy, not inertial. (thunder: 1000 accel
  vs 450 speed ≈ 0.45s to max.)
- **decel ≈ 0.7–0.9× accel** → slight drift on stop, not a hard brake.
- **mass (5–50)** is collision/ramming only; ignore for the mover unless
  fighters collide.

## Wing-level fields (`wing_data.csv`)

Relevant columns (after the quoted `tags` field, which contains commas — parse
with a real CSV reader, not naive split):

`id, variant, tags, tier, rarity, fleet pts, op cost, formation, range,
attackRunRange, attackPositionOffset, num, role, role desc, refit, base value`

- **formation** — `V` (most fighters), `CLAW`, `BOX` (drones). Drives the
  wing's spatial arrangement around its leader.
- **range** — leash distance from the carrier before returning.
- **attackRunRange** — how close the wing closes on an attack run; bombers run
  long, interceptors stay in close.
- **num** — fighters per wing (see table above).
- **role** — `INTERCEPTOR` / `FIGHTER` / `BOMBER` / `SUPPORT`.

## `.ship` geometry (example: talon)

The `.ship` file carries no kinematics — only geometry. Fields we care about
for render/FX (already scraped at runtime by `EngineSlotResolver`):

- `collisionRadius` (talon: 16) — circumscribes the bounds.
- `engineSlots[]` — `location` / `angle` / `length` / `width` / `style` per
  thruster (talon: two `LOW_TECH` engines at the rear). Drives engine-glow FX.
- `bounds[]` — collision polygon (pixel coords; convert via the
  [[vanilla_ship_spec_scraping]] convention).
- `weaponSlots[]`, `shieldRadius` / `shieldCenter`.
