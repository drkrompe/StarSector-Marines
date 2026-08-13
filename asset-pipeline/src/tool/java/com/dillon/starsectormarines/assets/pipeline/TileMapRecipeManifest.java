package com.dillon.starsectormarines.assets.pipeline;

import com.dillon.starsectormarines.assets.tilemaps.TileMapDerivationKernel;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Schema for {@code tilemaps.json}, the per-sheet derivation-recipe manifest
 * read by {@link DeriveTileMapsTask}. Only sheets listed under {@code
 * "sheets"} get baked — this manifest IS the bake's inclusion list, not just
 * tuning knobs, so adding a new terrain sheet to the bake is a one-line JSON
 * edit, not a code change.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "defaults": {
 *     "heightPolarity": "BRIGHT_HIGH",
 *     "heightBlurRadius": 2,
 *     "lowPercentile": 0.02,
 *     "highPercentile": 0.98,
 *     "normalStrength": 2.5
 *   },
 *   "sheets": [
 *     { "tileset": "Water_tiles.tileset.json" },
 *     { "tileset": "urban-tileset-2.tileset.json",
 *       "sheetOverride": "graphics/tilesets/urban-tileset-2-spaceport-apron.png" }
 *   ]
 * }
 * }</pre>
 *
 * <p>{@code tileset} is a filename under {@code mod/data/tilesets/} — the
 * runtime schema ({@code TileRegistry.ingestSheet}) is read directly for
 * grid/slice layout ({@code cellPx} or {@code slice}), so this manifest only
 * carries knobs the runtime schema has no field for. {@code sheetOverride} is
 * for the rare case where the tileset json's own {@code "sheet"} field isn't
 * the PNG actually loaded at runtime (e.g. {@code urban-tileset-2.tileset.json}
 * names {@code urban-tileset-2-imagegen.png}, but {@code TileManifest.ROAD_SHEET}
 * loads {@code urban-tileset-2-spaceport-apron.png}) — bake against what the
 * game loads, not what the tileset json happens to name.
 *
 * <p>Any recipe field on a sheet entry overrides {@code "defaults"}; an unset
 * field (and an absent {@code "defaults"} block entirely) falls back to
 * {@link #FALLBACK_RECIPE}.
 */
public record TileMapRecipeManifest(RecipeValues defaults, List<SheetEntry> sheets) {

    private static final TileMapDerivationKernel.Recipe FALLBACK_RECIPE = new TileMapDerivationKernel.Recipe(
            TileMapDerivationKernel.HeightPolarity.BRIGHT_HIGH, 2, 0.02f, 0.98f, 2.5f);

    public record RecipeValues(
            String heightPolarity,
            Integer heightBlurRadius,
            Float lowPercentile,
            Float highPercentile,
            Float normalStrength) {
    }

    public record SheetEntry(
            String tileset,
            String sheetOverride,
            String heightPolarity,
            Integer heightBlurRadius,
            Float lowPercentile,
            Float highPercentile,
            Float normalStrength) {
    }

    public static TileMapRecipeManifest load(Path manifestPath) throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .build();
        return mapper.readValue(Files.readString(manifestPath), TileMapRecipeManifest.class);
    }

    /** Resolves {@code entry}'s recipe: entry override -&gt; {@code "defaults"} block -&gt; {@link #FALLBACK_RECIPE}. */
    public TileMapDerivationKernel.Recipe recipeFor(SheetEntry entry) {
        String polarity = firstNonNull(entry.heightPolarity(), defaults == null ? null : defaults.heightPolarity());
        Integer blurRadius = firstNonNull(entry.heightBlurRadius(), defaults == null ? null : defaults.heightBlurRadius());
        Float lowPct = firstNonNull(entry.lowPercentile(), defaults == null ? null : defaults.lowPercentile());
        Float highPct = firstNonNull(entry.highPercentile(), defaults == null ? null : defaults.highPercentile());
        Float strength = firstNonNull(entry.normalStrength(), defaults == null ? null : defaults.normalStrength());

        return new TileMapDerivationKernel.Recipe(
                polarity == null
                        ? FALLBACK_RECIPE.heightPolarity()
                        : TileMapDerivationKernel.HeightPolarity.valueOf(polarity),
                blurRadius == null ? FALLBACK_RECIPE.heightBlurRadius() : blurRadius,
                lowPct == null ? FALLBACK_RECIPE.lowPercentile() : lowPct,
                highPct == null ? FALLBACK_RECIPE.highPercentile() : highPct,
                strength == null ? FALLBACK_RECIPE.normalStrength() : strength);
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }
}
