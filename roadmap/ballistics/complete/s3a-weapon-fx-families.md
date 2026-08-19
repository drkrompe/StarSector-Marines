# S3a — weapon FX families

> **Shipped 2026-08-19** on `session/ballistics-fx-families`, commit
> `9c8a642f`. Pulse, rail, and drone fire now share the traveling-bolt sweep
> while selecting distinct texture/length/width recipes. The DMR reuses
> vanilla's own blue-white gauss shell as a rail needle; drone pulse reuses
> the nearly neutral small flechette as a compact cyan dart; pulse rifle keeps
> S3's custom tintable bolt. No new bitmap shipped and no sim, balance, impact,
> or audio behavior changed. Full suite: 1513 tests.
>
> **Landed vs. planned deviations:** none. A scaled visual check confirmed the
> three silhouettes remain distinct at the configured ground-cell dimensions.

Original contract below, kept for the record.

---

> Follow S3's shared visible-round bolt with a small reuse-first visual
> language: pulse rifle, railgun, and drone pulse keep one traveling-body
> pipeline while gaining distinct silhouettes.

Parent design: [`../overview.md`](../overview.md) §7 (sim/visual split).
Depends on shipped S3 (`../complete/s3-visible-rounds.md`).

## Why

Color and cadence already distinguish the primary weapons, but the three
energy/rail entries currently stretch the same texture. At battle zoom that
makes the DMR and drone pulse read as differently colored pulse rifles. Shape
should reinforce the weapon family before the player has to parse hue.

## Reuse-first asset audit

The installed Starsector asset catalog is the first source for projectile and
FX art. Reference suitable vanilla textures by game-relative path; do not copy
them into the mod. Generate a custom bitmap only when the base game has no
suitable addressable silhouette.

The base game's energy-shot `length`, `width`, `coreColor`, and `fringeColor`
fields are procedural `.proj` declarations, not standalone textures our battle
renderer can load. The audit therefore selected their addressable companion
sprites and generic FX textures:

| Family | Texture | Treatment |
|---|---|---|
| Pulse rifle | `graphics/fx/round_bolt.png` (mod) | Keep S3's soft green, 1.0-cell bolt. |
| Railgun / DMR | `graphics/missiles/shell_gauss_cannon.png` (vanilla) | Stretch the railgun's own blue-white projectile into a thin 1.8-cell needle. |
| Drone pulse | `graphics/missiles/flechette_sml.png` (vanilla) | Tint the nearly neutral sprite cyan as a compact 0.65-cell dart. |

Field rifle and LMG remain warm physical shell sprites. Rockets, missiles,
grenades, and turret ammunition retain their existing sprite/trail/arc recipes.

## Design

Generalize `ShotFx.Bolt` into a reusable visual recipe:

```java
record Bolt(String spritePath, Color color,
            float lengthCells, float widthCells) implements Body {}
```

`ShotRenderService` resolves each bolt's path from the shared projectile cache
and sizes width independently from texture aspect. `BattleSprites` loads the
distinct set of bolt paths derived by `ShotFx`; consumers continue to key on
effects rather than weapon carriers.

## Tests

- Pin each primary's body family, texture path, tint, length, and width.
- Pin the distinct bolt-path set loaded by `BattleSprites`.
- Exercise headless collection for every bolt family and assert its configured
  texture is requested.
- Retain S3's pulse-bolt PNG contract and kinematics coverage.

## Non-goals

- New generated assets.
- Unique art for every individual weapon.
- Muzzle-flash, impact-profile, audio, simulation, or balance changes.
- Reworking heavy projectile, rocket, grenade, or turret FX.
