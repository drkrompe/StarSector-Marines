package com.dillon.starsectormarines.tools.terrainauthoring;

import com.dillon.starsectormarines.battle.world.tiles.FixedGridTileDrawer;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetSlicer;
import com.dillon.starsectormarines.battle.world.tiles.TileDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.ops.battleview.GroundFeather;
import com.dillon.starsectormarines.ops.battleview.GroundFeather.Shape;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Sole producer of the ground feather sheet and its catalog
 * ({@link GroundFeather}): each feathering material's own art through every
 * shape's noise-edged mask in every edge-coloured noise field.
 *
 * <p>Re-run it ({@code gradlew roadMaterials}'s sibling, {@code groundFeather})
 * after changing a feathering material's art or the shape of the bleed; the
 * sheet is derived and nothing else writes it.
 */
public final class GroundFeatherBaker {

    /** Pixels a tile draws, and the stride with its one-texel border on each side. */
    static final int TILE_PX = 32, STRIDE = TILE_PX + 2;

    /** How far a bleed reaches on average, in cells; with the noise it stays under half a cell. */
    static final double REACH = .24;
    /** Half the width of the soft rim, in cells. */
    static final double RIM = .025;
    /** Noise octaves: lattice intervals per cell and amplitude in cells. */
    static final int[] OCTAVE_LATTICE = {2, 4, 9};
    static final double[] OCTAVE_AMPLITUDE = {.16, .05, .02};

    private GroundFeatherBaker() {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]);
        Path mod = root.resolve("mod");
        TileRegistry registry = new TileRegistry();
        for (String sheet : new String[]{"nature-tiles", "Floors_Tiles", "pavement"}) {
            registry.ingestSheet(new JSONObject(Files.readString(
                    mod.resolve("data/tilesets/" + sheet + ".tileset.json"))));
        }
        List<String> materials = GroundFeather.MATERIALS;
        int perMaterial = GroundFeather.tilesPerMaterial();
        int cols = GroundFeather.EDGE_CODES, rows = Shape.values().length;
        BufferedImage sheet = new BufferedImage(STRIDE * cols * 2,
                STRIDE * rows * ((materials.size() + 1) / 2), BufferedImage.TYPE_INT_ARGB);
        JSONArray blocks = new JSONArray();
        for (int m = 0; m < materials.size(); m++) {
            BufferedImage source = resample(sourceTile(registry, mod, materials.get(m)), TILE_PX);
            int originCol = (m % 2) * cols, originRow = (m / 2) * rows;
            JSONArray cells = new JSONArray();
            for (int index = 0; index < perMaterial; index++) {
                Shape shape = Shape.values()[index / cols];
                int code = index % cols;
                int col = originCol + index % cols, row = originRow + index / cols;
                paint(sheet, col * STRIDE, row * STRIDE, source, shape, code, m);
                cells.put(new JSONArray().put(col).put(row));
            }
            blocks.put(new JSONObject().put("id", GroundFeather.BLOCK_PREFIX + materials.get(m)).put("cells", cells));
        }
        Path asset = mod.resolve(GroundFeather.SHEET);
        Files.createDirectories(asset.getParent());
        ImageIO.write(sheet, "png", asset.toFile());
        Files.writeString(mod.resolve("data/tilesets/ground-feather.tileset.json"),
                new JSONObject().put("sheet", GroundFeather.SHEET).put("cellPx", STRIDE)
                        .put("blocks", blocks).toString(2) + "\n");
    }

    /** One tile with its border: the material's texels, at the mask's alpha. */
    private static void paint(BufferedImage sheet, int left, int top, BufferedImage source,
                              Shape shape, int code, int material) {
        for (int j = -1; j <= TILE_PX; j++) {
            for (int i = -1; i <= TILE_PX; i++) {
                // The border repeats the tile's own edge texel.
                int ci = Math.max(0, Math.min(TILE_PX - 1, i)), cj = Math.max(0, Math.min(TILE_PX - 1, j));
                double u = (ci + .5) / TILE_PX, v = 1 - (cj + .5) / TILE_PX;
                int alpha = (int) Math.round(255 * alpha(shape, code, u, v, material));
                sheet.setRGB(left + i + 1, top + j + 1, (source.getRGB(ci, cj) & 0xFFFFFF) | alpha << 24);
            }
        }
    }

    /**
     * The bleed's opacity at a point: one at the winner's cells, falling to
     * nothing a noisy distance out. {@code (u, v)} is the point within its
     * cell, north up; {@code code} the cell's {@link GroundFeather#edgeCode}.
     */
    static double alpha(Shape shape, int code, double u, double v, int material) {
        double distance = Double.MAX_VALUE;
        for (int[] cell : shape.sources()) {
            double dx = Math.max(0, Math.max(cell[0] - u, u - (cell[0] + 1)));
            double dy = Math.max(0, Math.max(cell[1] - v, v - (cell[1] + 1)));
            distance = Math.min(distance, Math.hypot(dx, dy));
        }
        double reach = REACH + noise(code, u, v, material);
        double t = Math.max(0, Math.min(1, (reach + RIM - distance) / (2 * RIM)));
        return t * t * (3 - 2 * t);
    }

    /**
     * Value noise over one cell, in cells. A lattice point on an edge takes its
     * value from that edge's colour alone, so the cell across the edge has the
     * same values there; the corners belong to no edge and are zero.
     */
    static double noise(int code, double u, double v, int material) {
        double sum = 0;
        for (int octave = 0; octave < OCTAVE_LATTICE.length; octave++) {
            int g = OCTAVE_LATTICE[octave];
            double x = u * g, y = v * g;
            int x0 = Math.min(g - 1, (int) Math.floor(x)), y0 = Math.min(g - 1, (int) Math.floor(y));
            double fx = fade(x - x0), fy = fade(y - y0);
            double a = lerp(lattice(code, g, x0, y0, material, octave), lattice(code, g, x0 + 1, y0, material, octave), fx);
            double b = lerp(lattice(code, g, x0, y0 + 1, material, octave), lattice(code, g, x0 + 1, y0 + 1, material, octave), fx);
            sum += OCTAVE_AMPLITUDE[octave] * lerp(a, b, fy);
        }
        return sum;
    }

    /** One lattice value; {@code (i, j)} counts from the south-west corner. */
    private static double lattice(int code, int g, int i, int j, int material, int octave) {
        boolean west = i == 0, east = i == g, south = j == 0, north = j == g;
        if ((west || east) && (south || north)) return 0;
        long salt = (material * 31L + octave) * 0x165667B19E3779F9L;
        if (north) return value(code & 1, 0, i, salt);
        if (south) return value(code >> 2 & 1, 0, i, salt);
        if (east) return value(code >> 1 & 1, 1, j, salt);
        if (west) return value(code >> 3 & 1, 1, j, salt);
        return value(code, 2 + i, j, salt);
    }

    private static double value(int a, int b, int c, long salt) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x94D049BB133111EBL ^ salt;
        h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32;
        return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    /** The first variant of a material, without the sheet's own edge texels. */
    private static BufferedImage sourceTile(TileRegistry registry, Path mod, String key) throws Exception {
        if (key.startsWith("kind:")) {
            String id = key.equals("kind:GRASS") ? TileManifest.pickNatureGrassTileId(0, 0)
                    : TileManifest.pickNatureDirtTileId(0, 0);
            TileDef tile = registry.tile(id);
            BufferedImage image = ImageIO.read(mod.resolve(tile.sheetPath).toFile());
            SpriteSheetFrames.Frame frame = SpriteSheetSlicer.slice(image).frames[tile.frame];
            int inset = FixedGridTileDrawer.GROUND_INSET_PX_LARGE;
            return image.getSubimage(frame.x + inset, frame.y + inset, frame.w - 2 * inset, frame.h - 2 * inset);
        }
        GridBlockDef block = registry.block(key);
        if (block == null) throw new IllegalStateException("No block " + key);
        BufferedImage image = ImageIO.read(mod.resolve(block.sheetPath).toFile());
        int[] cell = block.cells[0];
        int inset = block.cellPx >= TileManifest.TILE_SIZE
                ? FixedGridTileDrawer.GROUND_INSET_PX_LARGE : FixedGridTileDrawer.GROUND_INSET_PX_SMALL;
        return image.getSubimage(cell[0] * block.cellPx + inset, cell[1] * block.cellPx + inset,
                block.cellPx - 2 * inset, block.cellPx - 2 * inset);
    }

    /** Box-filtered to {@code size} square, so a larger tile is averaged rather than sampled. */
    private static BufferedImage resample(BufferedImage source, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        double sx = source.getWidth() / (double) size, sy = source.getHeight() / (double) size;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int x0 = (int) Math.floor(x * sx), x1 = Math.max(x0 + 1, (int) Math.ceil((x + 1) * sx));
                int y0 = (int) Math.floor(y * sy), y1 = Math.max(y0 + 1, (int) Math.ceil((y + 1) * sy));
                long r = 0, g = 0, b = 0, n = 0;
                for (int yy = y0; yy < Math.min(y1, source.getHeight()); yy++) {
                    for (int xx = x0; xx < Math.min(x1, source.getWidth()); xx++) {
                        int rgb = source.getRGB(xx, yy);
                        r += rgb >> 16 & 0xFF; g += rgb >> 8 & 0xFF; b += rgb & 0xFF; n++;
                    }
                }
                out.setRGB(x, y, 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n));
            }
        }
        return out;
    }
}
