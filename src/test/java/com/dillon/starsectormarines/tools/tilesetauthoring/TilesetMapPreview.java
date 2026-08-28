package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a generated map twice — as it ships, and with a sheet's art standing in
 * for content it might replace — so an alternative can be judged where it will
 * actually be seen.
 *
 * <p>A piece read off a contact sheet tells you almost nothing. Whether a wall
 * reads at a distance, whether a crate disappears against the deck, whether a
 * candidate is better than what it replaces are questions about a map, and
 * answering them used to mean exporting over shipped content and starting the
 * game.
 *
 * <p><b>The substitution is made in the pixels, not in the catalog.</b> The
 * headless battle sprites address a fixed set of sheets, so an authored sheet at
 * a new path cannot be drawn at all; and re-pointing ids would change which
 * content the generator selects, which is the one thing a comparison must hold
 * still. Instead a copy of the shipped sheet is made with the candidate art
 * painted into the displaced id's own cells, and the renderer is pointed at that
 * copy first. Same registry, same ids, same seed, same map — only the art
 * differs, which is exactly the question being asked.
 *
 * <p>Nothing is written into {@code mod/}: the modified sheets live in a
 * temporary root that takes precedence over the shipped one for the duration of
 * the render, and are deleted after it.
 */
public final class TilesetMapPreview {

    private TilesetMapPreview() {}

    /** Cells the preview map is generated at. Large enough to show streets and compounds. */
    public static final int DEFAULT_GRID = 80;
    /** Pixels per cell. 12 keeps a whole map inside a panel while the art still reads. */
    public static final int DEFAULT_CELL_PX = 12;

    /**
     * One candidate: a rectangle of the authored atlas, standing in for the
     * cells a shipped id occupies on its own sheet.
     *
     * @param shippedId the doodad or block id whose art is being replaced
     * @param atlasCol the candidate's column in the authored atlas
     * @param atlasRow the candidate's row in the authored atlas
     * @param cellsX how many authored cells wide the candidate is
     * @param cellsY how many authored cells tall the candidate is
     */
    public record Substitution(String shippedId, int atlasCol, int atlasRow,
                               int cellsX, int cellsY) {}

    /** The same seed rendered with and without the substitutions, and what was skipped. */
    public record Result(BufferedImage baseline, BufferedImage substituted, List<String> notes) {}

    /**
     * Render the comparison.
     *
     * @param projectRoot repository root; {@code mod/} under it is the shipped content
     * @param atlas the authored sheet packed in memory, as {@link TilesetExport#atlas} makes it
     * @param authoredCellPx cell size of that atlas
     */
    public static Result render(Path projectRoot, BufferedImage atlas, int authoredCellPx,
                                List<Substitution> substitutions,
                                long seed, int gridCells, int cellPx) throws Exception {
        Path modRoot = projectRoot.resolve("mod").toAbsolutePath().normalize();
        List<String> notes = new ArrayList<>();

        installShippedCatalogsIfAbsent(modRoot);
        // Generation reads the installed registry, and the registry is identical
        // for both passes, so one map serves both. That is the point: the only
        // difference between the two images is paint.
        MapResult map = new BspCityGenerator().generate(gridCells, gridCells, seed);

        BufferedImage baseline = new HeadlessBattleMapRenderer(modRoot).render(map, seed, cellPx);
        if (substitutions.isEmpty()) {
            notes.add("No candidate is bound to a shipped id, so both maps are the baseline.");
            return new Result(baseline, baseline, notes);
        }

        Path previewRoot = Files.createTempDirectory("tileset-map-preview");
        try {
            int painted = paintSheets(modRoot, previewRoot, atlas, authoredCellPx,
                    substitutions, notes);
            if (painted == 0) return new Result(baseline, baseline, notes);
            BufferedImage substituted = new HeadlessBattleMapRenderer(List.of(previewRoot, modRoot))
                    .render(map, seed, cellPx);
            return new Result(baseline, substituted, notes);
        } finally {
            deleteTree(previewRoot);
        }
    }

    /**
     * Copy each affected shipped sheet into {@code previewRoot} with the
     * candidate art painted over the cells its id occupies.
     *
     * @return how many substitutions were actually applied
     */
    private static int paintSheets(Path modRoot, Path previewRoot, BufferedImage atlas,
                                   int authoredCellPx, List<Substitution> substitutions,
                                   List<String> notes) throws Exception {
        TileRegistry registry = TileRegistry.installed();
        Map<String, BufferedImage> sheets = new LinkedHashMap<>();
        int painted = 0;

        for (Substitution substitution : substitutions) {
            Target target = locate(registry, substitution.shippedId());
            if (target == null) {
                notes.add(substitution.shippedId()
                        + " is not a doodad or block in the shipped catalog, so it was skipped.");
                continue;
            }
            BufferedImage sheet = sheets.get(target.sheetPath());
            if (sheet == null) {
                Path source = modRoot.resolve(target.sheetPath().replace('/', java.io.File.separatorChar));
                BufferedImage read = ImageIO.read(source.toFile());
                if (read == null) {
                    notes.add("Could not read " + target.sheetPath() + "; "
                            + substitution.shippedId() + " was skipped.");
                    continue;
                }
                sheet = copy(read);
                sheets.put(target.sheetPath(), sheet);
            }
            paint(sheet, target, atlas, authoredCellPx, substitution);
            painted++;
        }

        for (Map.Entry<String, BufferedImage> entry : sheets.entrySet()) {
            Path out = previewRoot.resolve(entry.getKey().replace('/', java.io.File.separatorChar));
            Files.createDirectories(out.getParent());
            ImageIO.write(entry.getValue(), "png", out.toFile());
        }
        return painted;
    }

    /** Where a shipped id's art lives: its sheet, its cell origin, and how many cells it covers. */
    record Target(String sheetPath, int cellPx, int col, int row, int cellsX, int cellsY) {}

    static Target locate(TileRegistry registry, String shippedId) {
        if (registry == null) return null;
        DoodadDef doodad = registry.doodad(shippedId);
        if (doodad != null) {
            return new Target(doodad.sheetPath, doodad.sourceCellPx, doodad.col, doodad.row,
                    doodad.footprintCellsX, doodad.footprintCellsY);
        }
        GridBlockDef block = registry.block(shippedId);
        if (block != null && !block.isVariantPool()) {
            // A block owns the whole patch its layout addresses from the origin.
            int span = block.layout.span();
            return new Target(block.sheetPath, block.cellPx, block.originCol, block.originRow,
                    span, span);
        }
        return null;
    }

    /**
     * Paint the candidate over the target's cells.
     *
     * <p>Stretched to the target's footprint rather than placed at its drawn
     * size, for the same reason the exporter stretches: the footprint is a
     * statement about how much deck the thing covers, and a replacement has to
     * cover the same ground as what it replaces or the comparison is not one.
     */
    private static void paint(BufferedImage sheet, Target target, BufferedImage atlas,
                              int authoredCellPx, Substitution substitution) {
        int sx = substitution.atlasCol() * authoredCellPx;
        int sy = substitution.atlasRow() * authoredCellPx;
        int sw = Math.max(1, substitution.cellsX() * authoredCellPx);
        int sh = Math.max(1, substitution.cellsY() * authoredCellPx);
        sw = Math.min(sw, Math.max(1, atlas.getWidth() - sx));
        sh = Math.min(sh, Math.max(1, atlas.getHeight() - sy));

        int dx = target.col() * target.cellPx();
        int dy = target.row() * target.cellPx();
        int dw = target.cellsX() * target.cellPx();
        int dh = target.cellsY() * target.cellPx();

        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // Replace rather than blend: the shipped art underneath must not show
        // through a candidate with transparent margins.
        g.setComposite(java.awt.AlphaComposite.Src);
        g.drawImage(atlas.getSubimage(sx, sy, sw, sh), dx, dy, dw, dh, null);
        g.dispose();
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage out = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Src);
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return out;
    }

    /**
     * The workbench is not a JUnit run and not the game, so nothing has installed
     * the shipped catalogs for it. The scene renderer installs them itself when
     * they are absent; doing it here too keeps generation and rendering agreed
     * on one registry rather than depending on construction order.
     */
    private static void installShippedCatalogsIfAbsent(Path modRoot) throws Exception {
        if (TileRegistry.installed() != null) return;
        TileRegistry tiles = new TileRegistry();
        for (String path : TileRegistry.BUILTIN_TILESETS) {
            tiles.ingestSheet(new org.json.JSONObject(Files.readString(modRoot.resolve(path))));
        }
        tiles.validateReferences();
        TileRegistry.install(tiles);
    }

    private static void deleteTree(Path root) {
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // A leftover temp directory is not worth failing a preview over.
                }
            });
        } catch (Exception ignored) {
            // Same.
        }
    }
}
