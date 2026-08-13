package com.dillon.starsectormarines.assets.pipeline;

import com.dillon.starsectormarines.assets.tilemaps.AtlasTileMapDeriver;
import com.dillon.starsectormarines.assets.tilemaps.TileMapDerivationKernel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Build-time task that bakes {@code <sheet>_height.png} / {@code
 * <sheet>_normal.png} next to each terrain tile-sheet albedo listed in the
 * recipe manifest ({@link TileMapRecipeManifest}, {@code
 * src/tool/resources/tilemaps/tilemaps.json}). Reads the sheet's grid/slice
 * layout straight from its runtime {@code .tileset.json} (the same metadata
 * {@code TileRegistry} parses) and runs it through {@link AtlasTileMapDeriver}.
 *
 * <p>Usage: {@code java DeriveTileMapsTask <tilesetDir> <modDir> <recipeManifestPath>}.
 * {@code tilesetDir} is {@code mod/data/tilesets/} (holds the {@code
 * .tileset.json} files); {@code modDir} is {@code mod/} (the root the
 * tileset's {@code "sheet"} path is relative to; outputs land next to the
 * source PNG under {@code mod/graphics/}).
 *
 * <p>Incremental: a sheet is skipped if both outputs exist and are newer than
 * the source PNG, its tileset json, and the recipe manifest — independent of
 * Gradle's own UP-TO-DATE check, so a bare re-run of the derivation is
 * byte-identical (same untouched files) and a forced re-derivation (delete
 * the outputs) is deterministic (same kernel, same inputs).
 */
public final class DeriveTileMapsTask {

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Usage: DeriveTileMapsTask <tilesetDir> <modDir> <recipeManifestPath>");
            System.exit(1);
        }

        Path tilesetDir = Path.of(args[0]);
        Path modDir = Path.of(args[1]);
        Path recipeManifestPath = Path.of(args[2]);

        if (!Files.isDirectory(tilesetDir)) {
            System.err.println("Tileset directory does not exist: " + tilesetDir);
            System.exit(1);
        }
        if (!Files.isRegularFile(recipeManifestPath)) {
            System.err.println("Recipe manifest not found: " + recipeManifestPath);
            System.exit(1);
        }

        TileMapRecipeManifest manifest = TileMapRecipeManifest.load(recipeManifestPath);
        long manifestMtime = Files.getLastModifiedTime(recipeManifestPath).toMillis();
        JsonMapper jsonMapper = JsonMapper.builder().build();

        int baked = 0;
        int skipped = 0;
        int failed = 0;

        System.out.printf("Found %d sheet(s) in %s%n", manifest.sheets().size(), recipeManifestPath.getFileName());

        for (TileMapRecipeManifest.SheetEntry entry : manifest.sheets()) {
            Path tilesetJsonPath = tilesetDir.resolve(entry.tileset());
            try {
                if (!Files.isRegularFile(tilesetJsonPath)) {
                    System.err.printf("  [%s] FAILED: tileset json not found: %s%n", entry.tileset(), tilesetJsonPath);
                    failed++;
                    continue;
                }
                JsonNode root = jsonMapper.readTree(Files.readString(tilesetJsonPath));
                String sheetRelPath = entry.sheetOverride() != null
                        ? entry.sheetOverride()
                        : root.path("sheet").asText(null);
                if (sheetRelPath == null) {
                    System.err.printf("  [%s] FAILED: tileset json has no 'sheet' field%n", entry.tileset());
                    failed++;
                    continue;
                }
                Path sheetPath = modDir.resolve(sheetRelPath);
                if (!Files.isRegularFile(sheetPath)) {
                    System.err.printf("  [%s] FAILED: sheet PNG not found: %s%n", entry.tileset(), sheetPath);
                    failed++;
                    continue;
                }

                String baseName = sheetPath.getFileName().toString().replaceFirst("\\.png$", "");
                Path heightOut = sheetPath.resolveSibling(baseName + "_height.png");
                Path normalOut = sheetPath.resolveSibling(baseName + "_normal.png");

                long newestInput = Math.max(manifestMtime, Math.max(
                        Files.getLastModifiedTime(sheetPath).toMillis(),
                        Files.getLastModifiedTime(tilesetJsonPath).toMillis()));
                if (Files.exists(heightOut) && Files.exists(normalOut)
                        && Files.getLastModifiedTime(heightOut).toMillis() >= newestInput
                        && Files.getLastModifiedTime(normalOut).toMillis() >= newestInput) {
                    skipped++;
                    continue;
                }

                BufferedImage albedo = ImageIO.read(sheetPath.toFile());
                if (albedo == null) {
                    System.err.printf("  [%s] FAILED: could not decode %s%n", entry.tileset(), sheetPath);
                    failed++;
                    continue;
                }
                List<AtlasTileMapDeriver.Cell> cells = resolveCells(root, albedo);
                if (cells.isEmpty()) {
                    System.err.printf("  [%s] FAILED: no cells resolved from grid/slice metadata%n", entry.tileset());
                    failed++;
                    continue;
                }

                TileMapDerivationKernel.Recipe recipe = manifest.recipeFor(entry);
                TileMapDerivationKernel.Result result = AtlasTileMapDeriver.derive(albedo, cells, recipe);

                ImageIO.write(result.height(), "png", heightOut.toFile());
                ImageIO.write(result.normal(), "png", normalOut.toFile());

                System.out.printf("  [%s] %s -> %s + %s (%d cells)%n",
                        entry.tileset(), sheetRelPath, heightOut.getFileName(), normalOut.getFileName(), cells.size());
                baked++;
            } catch (Exception e) {
                System.out.printf("  [%s] ERROR: %s%n", entry.tileset(), e.getMessage());
                failed++;
            }
        }

        System.out.printf("%nDone! %d baked, %d failed, %d up-to-date%n", baked, failed, skipped);
        if (failed > 0) System.exit(1);
    }

    /**
     * Reads the sheet's own grid ({@code cellPx}) or auto-strip ({@code
     * slice}) metadata from its {@code .tileset.json} and resolves the atlas
     * cell rects — the same partition the runtime addresses tiles by.
     */
    private static List<AtlasTileMapDeriver.Cell> resolveCells(JsonNode tilesetRoot, BufferedImage albedo) {
        JsonNode sliceNode = tilesetRoot.get("slice");
        if (sliceNode != null) {
            int alphaThreshold = sliceNode.path("alphaThreshold").asInt(16);
            int minGap = sliceNode.path("minGap").asInt(4);
            return AtlasTileMapDeriver.sliceCells(albedo, alphaThreshold, minGap);
        }
        int cellPx = tilesetRoot.path("cellPx").asInt(0);
        if (cellPx <= 0) {
            throw new IllegalStateException("tileset json has neither 'slice' nor a positive 'cellPx'");
        }
        return AtlasTileMapDeriver.gridCells(albedo, cellPx);
    }
}
