# Catalog provider live acceptance

These fixtures verify the boundary that headless registry tests cannot cover:
Starsector's real enabled-mod discovery and provider-scoped resource loading.

`gradlew.bat prepareCatalogSmoke` stages the additive fixture without starting
the game. Use `-PcatalogSmokeMode=collision` for the duplicate-id variant.
`gradlew.bat catalogSmokeLive` explicitly starts the installed Starsector
executable, redirects mods, saves, screenshots, and logs under
`build/catalog-smoke/`, captures the relevant application-load lines, and
terminates only the process tree it launched.

## Accepted result

Verified once on 2026-08-25 with Starsector `0.98a-RC8`. The game resolved the
fixture priority order as:

1. `catalog_smoke_alpha`
2. `catalog_smoke_beta`
3. `starsector_marines`

The additive fixture reached:

```text
TileRegistry: loaded 29 sliced tiles from 9 contributed tilesets
```

The collision fixture stopped application load with both exact providers and
paths in the diagnostic:

```text
TileRegistry: duplicate id 'catalog-smoke.alpha-ground': first declared by mod 'catalog_smoke_alpha' at 'data/catalog-smoke/provider.tileset.json', then by mod 'catalog_smoke_beta' at 'data/catalog-smoke/provider.tileset.json'
```

Another live launch is unnecessary unless Starsector version compatibility or
the enabled-mod discovery/resource-loading boundary changes.
