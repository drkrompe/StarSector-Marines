# Locust ground AA art

First art pass for a ground emplacement based on the vanilla Locust SRM
Launcher. The intended family also includes the Swarmer SRM Launcher and
Proximity Charge Launcher; those two art passes are still pending.

## Deliverables

- `locust-mount.master.png`: built-in ImageGen up-res interpretation of the
  vanilla turret, retaining the upward-facing four-channel launcher identity.
- `ground-base.master.png`: separate stationary foundation, up-resed from the
  project's Ridgewatch base. This is new ground-emplacement art, not a vanilla
  Locust layer.
- `layers/locust-mount.png` and `layers/ground-base.png`: 512 x 512 RGBA exports,
  both with the authored assembly pivot at (256, 256). Draw base then mount;
  only the mount rotates. Forward is image up.
- `review.png`: 1920 x 1080 art contact sheet at 100% scale, with the original,
  independent layers, assembled view and 64/96/128-pixel size studies.
- The two `*.prompt.txt` files preserve the exact built-in ImageGen prompts.

These are art-review assets. No runtime catalog entries, combat stats, world
footprint, destruction states or firing origins are authored by this pass.
ImageGen resolves low-resolution pixels into new detail; it does not recover
an original high-resolution source. The silhouette is recognizably derived
from Locust but is not pixel-exact, and vanilla muzzle offsets must be checked
against this art when the weapon is integrated.

## Reproduce the exports

From the repository root in PowerShell:

```powershell
./art-source/defences/locust-aa/export.ps1
```

The exporter retains both complete master frames and downsamples them without
trimming, recentering, recoloring or painting. It checks square dimensions,
real transparency, nonempty visible bounds and clear visible borders, then
writes `export-metadata.json` and the review sheet. Generated masters arrived
at 1254 x 1254 despite the requested 1024 square canvas. The base has faint
alpha=1 residue in some empty pixels; alpha is preserved, and the boundary
check rejects alpha >= 8. Visible bounds are measured at alpha >= 32.

## Source provenance and remaining family

The reference install is Starsector 0.98a-RC8 at
`C:/Program Files (x86)/Fractal Softworks/Starsector/starsector-core`.
Vanilla art belongs to Fractal Softworks. The retained Locust sprite in
`sources/` is an unmodified copy; the weapon spec has only trailing whitespace
and line endings normalized for Git. `sources/ridge-aa-base.png` preserves
the project's existing foundation used as the second edit target.

| Planned ground weapon | Vanilla weapon id | Turret sprite under `graphics/weapons/` | Art state |
| --- | --- | --- | --- |
| Locust SRM Launcher | `locust` | `locust_turret.png` | First up-res pass retained here |
| Swarmer SRM Launcher | `swarmer` | `swarmer_srm_launcher_turret_base.png` | Pending |
| Proximity Charge Launcher | `phasecl` | `phase_charge_launcher_turret_base.png` | Pending |

All three installed `.wpn` definitions specify one turret sprite and an
alternative hardpoint sprite. Hardpoint and turret images are alternative
mounting views, not composited layers; none declares a separate recoil or
glow image. Ground foundations are therefore authored independently. Keep
each future launcher visually distinct, and decide its gameplay from its own
weapon behavior rather than inheriting Locust's treatment by name.

User-supplied design references:
[Locust](https://starsector.wiki.gg/wiki/Locust_SRM_Launcher),
[Swarmer](https://starsector.wiki.gg/wiki/Swarmer_SRM_Launcher), and
[Proximity Charge](https://starsector.wiki.gg/wiki/Proximity_Charge_Launcher).
The pages returned HTTP 403 during this pass; asset and layer findings above
come from the installed game files.
