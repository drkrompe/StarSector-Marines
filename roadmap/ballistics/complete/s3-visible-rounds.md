# S3 — stylized visible rounds

> **Shipped 2026-08-19** on `codex/ballistics-s3`, commit `ebc3023d`.
> Tracer-colored primaries now derive `ShotFx.Bolt`; `ShotRenderService`
> grows a tinted streak from the muzzle on the real shot clock; the shared
> white-base texture loads through `BattleSprites`; focused kinematics,
> collection, derivation, and asset-contract tests are green. Full suite:
> 1507 tests.
>
> **Landed vs. planned deviations:**
> - `FIELD_RIFLE` was added after this contract and already carries the same
>   explicit shell sprite as the SMG, so both stay on `ShotFx.Sprite`; pulse
>   rifle, DMR, and drone pulse use the new bolt body.
> - A critique pass found impact timing was inferred independently from
>   `projectileSpritePath` in both `BattleScreen` and the hybrid-combat
>   `GroundSimPresentation`. Bolts would have traveled while their impact FX
>   spawned at launch. `ShotFx.travels()` is now the shared presentation
>   semantic: `Sprite` and `Bolt` defer impacts to arrival; full-line `Tracer`
>   impacts remain immediate.
> - The asset used Codex's built-in image-generation workflow rather than the
>   originally named CLI/model. A targeted grayscale correction plus
>   deterministic crop/downscale/desaturation produced the final 64×256 PNG;
>   its dimensions, alpha, non-empty content, and exact grayscale neutrality
>   are regression-tested.

Original contract below, kept for the record.

---

> Primaries stop rendering as full static tracer lines and become traveling
> bolts: a short, bright, weapon-colored streak that flies from muzzle to
> the resolved endpoint on the round's real flight clock. The sim side is
> already done (S1 gave every shot a velocity-derived `lifetime`); this
> story is presentation only.

Parent design: [`../overview.md`](../overview.md) §7 (sim/visual split).
Depends on S2's `roundVelocity` values for pacing (a 45 c/s SMG round is
the showcase).

## Why now

S1/S2 made a round's flight physically real — but the renderer still draws
the entire line at once, so a miss that flies on and clips a marine three
cells behind the target reads as an instant hit from nowhere. Once the
round is *visible in flight*, friendly-fire lanes, misses-fly-on, and slow
vs. fast weapons all become legible without a single sim change.

## Design

### New `ShotFx` body: `Bolt`

```java
/** Traveling streak: a tinted bolt sprite flying the shot line on its flight clock. */
public record Bolt(Color color, float lengthCells) implements Body {}
```

- `ShotFx.derivePrimary` maps tracer-armed primaries (PULSE_RIFLE, DMR,
  DRONE_PULSE) to `Bolt(w.tracerColor, perWeaponLength)` instead of
  `Tracer`. SMG keeps its shell `Sprite` (already a traveling body).
- `Tracer` (full-line) **stays** as the body for mech fallback and
  no-source shots — S4 retires it when mech/turret direct fire unifies.
  The `Tracer` sweep in `ShotRenderService` is untouched.

Consumers keep keying on the effect, never the carrier (per the composition
rule): a future weapon opts into `Bolt` by carrying a tracer color.

### Bolt sweep in `ShotRenderService`

Third sweep alongside tracers/sprites. Per bolt shot:

- `progress = 1 − lifetime / lifetimeMax` (same linear clock the sprite
  sweep uses; no boost ramp — bullets don't accelerate).
- Head = `from + (to − from) × progress`; tail = head pulled back
  `min(lengthCells, progress × shotLen)` along the line (the streak grows
  out of the muzzle rather than pre-spawning at full length).
- Draw the **bolt sprite** stretched head-to-tail, rotated to the line's
  bearing, tinted `bolt.color()` (white-base asset × per-weapon RGB), alpha
  `alphaMult` with a short fade-in over the first ~10% of flight.
- Impact FX / audio dispatch is untouched — it already keys off shot
  expiry, which is the arrival tick.

### The bolt asset (codex-generated)

`mod/graphics/fx/round_bolt.png` → in-game path
`graphics/fx/round_bolt.png`. Generated via the `codex` CLI
(gpt-5.6-luna): a **white/grayscale elongated energy bolt on a transparent
background, vertical, bright hot core with a soft glow falloff, tapered
tail fading to transparent, ~64×256 px, tip at the top**. White base so the
per-weapon `java.awt.Color` tint owns the hue (same trick as the objective
icons). Vertical orientation matches the existing sprite-sweep bearing
convention (`bearingDeg` = atan2 − 90).

Loading: `BattleSprites` loads it path-keyed into `projectileSpriteByPath`
in `ensureMarineSecondarySprites`'s primary pass (a `Bolt` resolves its
sprite by the shared constant path — one loaded texture, every bolt weapon
shares it).

### Per-weapon bolt lengths (initial feel numbers)

| Weapon | lengthCells |
|---|---|
| PULSE_RIFLE | 1.0 |
| DMR | 1.8 (railgun slug — long, fast, thin) |
| DRONE_PULSE | 0.8 |

At S2 velocities a pulse round crosses a 24-cell max-range shot in ~0.44s —
a clearly readable streak, not a blink.

## Non-goals

- Muzzle flash / shell ejection — separate FX polish, not this story.
- Touching the mech/turret sprite pipeline or the `Tracer` fallback (S4).
- Light-path stamping changes (the existing tracer light pass keys off
  `defaultTracerColor`; bolts reuse the weapon color as-is).

## Tests

- `ShotFx` derivation pinning: primaries with a tracer color derive `Bolt`
  (color + length), SMG still derives `Sprite`, mech fallback still
  `Tracer` — extend the existing F2 pinning test.
- Bolt kinematics (pure math, no GL): head/tail positions at progress 0 /
  mid / 1; tail clamp near the muzzle; fade-in window.
- Render smoke: `ShotRenderService.collect` with a `Bolt` shot emits one
  sprite draw between from/to (DrawList inspection, headless).

## Files touched (expected)

- `ops/battleview/ShotFx.java` — `Bolt` body + derivation change.
- `ops/battleview/ShotRenderService.java` — bolt sweep.
- `ops/battleview/BattleSprites.java` — bolt sprite load.
- `mod/graphics/fx/round_bolt.png` — new generated asset.
- Tests: `ShotFxTest` (or wherever the F2 pinning test lives) + a bolt
  kinematics test.
