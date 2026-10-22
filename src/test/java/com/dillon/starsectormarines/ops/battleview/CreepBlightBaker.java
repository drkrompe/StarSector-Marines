package com.dillon.starsectormarines.ops.battleview;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Bakes the trees hive creep overgrows (`colony-breach-nouns.md`, Creep): the
 * shipped {@code forest-blight} sheet and its tileset, derived from the canopy
 * and damaged-canopy sheets with the creep's own surface ({@link CreepSurface})
 * and palette ({@link CreepPalette#HIVE}), so a crust on a crown is the crust on
 * the ground.
 *
 * <p>A tree is taken in four stages, as a wall is: veins reaching in over the
 * crown from its rim while the leaves start to turn; a crust rolling in from the
 * rim with its beaded lip while the leaves brown; then one of two ends.
 * <em>Withered</em> (stages {@code blight-3}, {@code blight-4}): the crown
 * thins to the damaged sheet's limbs, its leaves dead and falling, and the crust
 * climbs the trunk and out along the lower limbs until a dead tree of grey-brown
 * limbs stands crusted from the trunk up, a few dead leaves hanging on. <em>Shrouded</em> ({@code shroud-3}, {@code shroud-4}): the crust
 * keeps rolling in until it has wrapped the whole crown, which keeps its
 * silhouette as a plated mound. Every stage exists for the intact and the
 * war-damaged crown.
 *
 * <p>Run with {@code gradlew.bat bakeCreepBlight}; it rewrites
 * {@code mod/graphics/doodads/forest-blight.png} and
 * {@code mod/data/tilesets/forest-blight.tileset.json}.
 */
public final class CreepBlightBaker {

    /** The canopy and damaged sheets share this layout: one slot of the output sheet each. */
    static final int SLOT_W = 896, SLOT_H = 704, CELL = 64;
    /** Slots across the output sheet. */
    static final int SLOTS_ACROSS = 4;

    /** One output slot: which source sheet and which stage. */
    record Slot(String suffix, boolean damaged, String style, int stage) { }

    static final List<Slot> SLOTS = List.of(
            new Slot("-blight-1", false, "withered", 1), new Slot("-blight-2", false, "withered", 2),
            new Slot("-blight-3", false, "withered", 3), new Slot("-blight-4", false, "withered", 4),
            new Slot("-damaged-blight-1", true, "withered", 1), new Slot("-damaged-blight-2", true, "withered", 2),
            new Slot("-damaged-blight-3", true, "withered", 3), new Slot("-damaged-blight-4", true, "withered", 4),
            new Slot("-shroud-3", false, "shrouded", 3), new Slot("-shroud-4", false, "shrouded", 4),
            new Slot("-damaged-shroud-3", true, "shrouded", 3), new Slot("-damaged-shroud-4", true, "shrouded", 4));

    private CreepBlightBaker() { }

    public static void main(String[] args) throws Exception {
        Path mod = Path.of(args.length > 0 ? args[0] : "mod");
        BufferedImage canopies = ImageIO.read(mod.resolve("graphics/doodads/forest-canopies.png").toFile());
        BufferedImage damaged = ImageIO.read(mod.resolve("graphics/doodads/forest-damaged.png").toFile());
        JSONObject tileset = new JSONObject(Files.readString(mod.resolve("data/tilesets/forest-canopies.tileset.json")));
        BufferedImage sheet = bake(canopies, damaged, tileset.getJSONArray("doodads"), CreepPalette.HIVE);
        File png = mod.resolve("graphics/doodads/forest-blight.png").toFile();
        ImageIO.write(sheet, "png", png);
        Files.writeString(mod.resolve("data/tilesets/forest-blight.tileset.json"),
                tileset(tileset.getJSONArray("doodads")).toString(2) + "\n", StandardCharsets.UTF_8);
        System.out.println("[creep-blight] wrote " + png + " (" + sheet.getWidth() + "x" + sheet.getHeight() + ")");
    }

    /** The whole output sheet. */
    static BufferedImage bake(BufferedImage canopies, BufferedImage damaged, JSONArray doodads, CreepPalette pal) throws Exception {
        int rows = (SLOTS.size() + SLOTS_ACROSS - 1) / SLOTS_ACROSS;
        BufferedImage out = new BufferedImage(SLOT_W * SLOTS_ACROSS, SLOT_H * rows, BufferedImage.TYPE_INT_ARGB);
        for (int s = 0; s < SLOTS.size(); s++) {
            Slot slot = SLOTS.get(s);
            int ox = (s % SLOTS_ACROSS) * SLOT_W, oy = (s / SLOTS_ACROSS) * SLOT_H;
            for (int i = 0; i < doodads.length(); i++) {
                JSONObject d = doodads.getJSONObject(i);
                int x = d.getInt("col") * CELL, y = d.getInt("row") * CELL;
                JSONArray fp = d.getJSONArray("footprintCells");
                int w = fp.getInt(0) * CELL, h = fp.getInt(1) * CELL;
                Frame healthy = new Frame(canopies, x, y, w, h), broken = new Frame(damaged, x, y, w, h);
                int[] px = stage(slot.damaged() ? broken : healthy, broken, slot.style(), slot.stage(), pal);
                out.setRGB(ox + x, oy + y, w, h, px, 0, w);
            }
        }
        return out;
    }

    /** The tileset: every canopy's stages, footprint, cover and fog surface as its source has them. */
    static JSONObject tileset(JSONArray doodads) throws Exception {
        JSONArray cells = new JSONArray(), defs = new JSONArray();
        for (int s = 0; s < SLOTS.size(); s++) {
            Slot slot = SLOTS.get(s);
            int colOffset = (s % SLOTS_ACROSS) * (SLOT_W / CELL), rowOffset = (s / SLOTS_ACROSS) * (SLOT_H / CELL);
            for (int i = 0; i < doodads.length(); i++) {
                JSONObject src = doodads.getJSONObject(i);
                String id = src.getString("id") + slot.suffix();
                JSONObject def = new JSONObject(src.toString());
                def.put("id", id);
                def.put("col", src.getInt("col") + colOffset);
                def.put("row", src.getInt("row") + rowOffset);
                defs.put(def);
                cells.put(new JSONObject().put("name", id).put("col", def.getInt("col")).put("row", def.getInt("row")));
            }
        }
        return new JSONObject().put("cellPx", CELL).put("sheet", "graphics/doodads/forest-blight.png")
                .put("cells", cells).put("doodads", defs);
    }

    // ---- one frame -----------------------------------------------------------

    /** One tree's pixels, with the fields the stages read. */
    static final class Frame {
        final int w, h;
        final float[] r, g, b, a, lum;
        /** Distance in from the silhouette, 0 at the rim and 1 at the deepest point. */
        final float[] rim;
        float deepest;

        Frame(BufferedImage sheet, int x0, int y0, int w, int h) {
            this.w = w;
            this.h = h;
            int n = w * h;
            r = new float[n];
            g = new float[n];
            b = new float[n];
            a = new float[n];
            lum = new float[n];
            int[] argb = sheet.getRGB(x0, y0, w, h, null, 0, w);
            for (int i = 0; i < n; i++) {
                int c = argb[i];
                a[i] = (c >>> 24) / 255f;
                r[i] = ((c >> 16) & 0xFF) / 255f;
                g[i] = ((c >> 8) & 0xFF) / 255f;
                b[i] = (c & 0xFF) / 255f;
                lum[i] = 0.3f * r[i] + 0.59f * g[i] + 0.11f * b[i];
            }
            rim = rimDistance();
        }

        private float[] rimDistance() {
            int n = w * h;
            float[] d = new float[n];
            float big = 1e6f;
            for (int i = 0; i < n; i++) d[i] = a[i] < 0.5f ? 0f : big;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if (d[i] == 0f) continue;
                    float v = Math.min(at(d, x - 1, y) + 1f, at(d, x, y - 1) + 1f);
                    v = Math.min(v, Math.min(at(d, x - 1, y - 1), at(d, x + 1, y - 1)) + 1.414f);
                    d[i] = Math.min(d[i], v);
                }
            }
            for (int y = h - 1; y >= 0; y--) {
                for (int x = w - 1; x >= 0; x--) {
                    int i = y * w + x;
                    if (d[i] == 0f) continue;
                    float v = Math.min(at(d, x + 1, y) + 1f, at(d, x, y + 1) + 1f);
                    v = Math.min(v, Math.min(at(d, x + 1, y + 1), at(d, x - 1, y + 1)) + 1.414f);
                    d[i] = Math.min(d[i], v);
                }
            }
            deepest = 1f;
            for (float v : d) deepest = Math.max(deepest, v);
            for (int i = 0; i < n; i++) d[i] /= deepest;
            return d;
        }

        private float at(float[] d, int x, int y) {
            return x < 0 || y < 0 || x >= w || y >= h ? 0f : d[y * w + x];
        }

        /** Whether a pixel is foliage rather than bark: green over red and blue, with some colour. */
        boolean leaf(int i) {
            float max = Math.max(r[i], Math.max(g[i], b[i])), min = Math.min(r[i], Math.min(g[i], b[i]));
            return g[i] - Math.max(r[i], b[i]) > 0.02f && (max - min) > 0.08f;
        }
    }

    /**
     * One stage's pixels. {@code base} is the crown the stage is drawn on; {@code limbs} the damaged crown whose
     * exposed limbs a withering tree is reduced to.
     */
    static int[] stage(Frame base, Frame limbs, String style, int stage, CreepPalette pal) {
        boolean withered = "withered".equals(style) && stage >= 3;
        Frame f = withered ? limbs : base;
        int[] out = new int[f.w * f.h];
        float[] surf = new float[4], noise = new float[4], fine = new float[4];
        float cx = f.w * 0.5f, cy = f.h * 0.5f, radius = Math.min(f.w, f.h) * 0.5f;
        for (int y = 0; y < f.h; y++) {
            for (int x = 0; x < f.w; x++) {
                int i = y * f.w + x;
                if (f.a[i] <= 0.01f) continue;
                float cellX = x / (float) CELL, cellY = y / (float) CELL;
                CreepNoise.sample(cellX * 2.6f + 7.1f, cellY * 2.6f + 2.3f, noise);
                CreepNoise.sample(cellX * 6f + 1.9f, cellY * 6f + 4.4f, fine);
                float lobes = (noise[0] - 0.5f) * 0.55f + (fine[0] - 0.5f) * 0.15f;
                float rr = (float) Math.hypot(x + 0.5f - cx, y + 0.5f - cy) / radius;
                float alpha = f.a[i];
                float[] col = {f.r[i], f.g[i], f.b[i]};
                boolean leaf = f.leaf(i);
                // How far the leaves have turned, and the crust's extent: positive inside it, in pixels.
                float wilt, crust;
                float veinReach;
                switch (stage) {
                    case 1 -> {
                        wilt = 0.22f;
                        crust = -1f;
                        veinReach = 0.38f + lobes;
                    }
                    case 2 -> {
                        wilt = 0.55f;
                        crust = (0.16f + lobes * 0.8f - f.rim[i]) * f.deepest;
                        veinReach = 0.75f + lobes;
                    }
                    default -> {
                        if (withered) {
                            wilt = stage == 3 ? 0.85f : 1f;
                            float reach = stage == 3 ? 0.32f : 0.58f;
                            crust = leaf ? -1f : (reach + lobes - rr) * radius;
                            veinReach = stage == 3 ? 0.6f : 1.2f;
                        } else {
                            wilt = 0.85f;
                            float reach = stage == 3 ? 0.58f : 1.3f;
                            crust = (reach + lobes * 0.6f - f.rim[i]) * f.deepest;
                            veinReach = 1.2f;
                        }
                    }
                }
                if (withered && leaf) {
                    // Dead leaves fall: a few in three hang on, one in eight at the last.
                    float keep = stage == 3 ? 0.5f : 0.76f;
                    if (fine[1] < keep) continue;
                }
                if (leaf) dry(col, f.lum[i], wilt);
                else if (withered) husk(col, f.lum[i], stage == 3 ? 0.4f : 0.85f);
                // Veins reach in from the rim (or out along the limbs on a withered tree) ahead of the crust.
                CreepSurface.sample(cellX * CreepShading.WALL_VEIN_SCALE, cellY * CreepShading.WALL_VEIN_SCALE,
                        CreepShading.WALL_VEIN_SCALE, surf);
                float along = withered ? rr : f.rim[i];
                float veinIn = withered ? CreepShading.smoothstep(veinReach, veinReach - 0.1f, along)
                        : CreepShading.smoothstep(veinReach, veinReach - 0.12f, along);
                float vein = CreepShading.clamp01(surf[2] * 1.6f) * veinIn;
                if (vein > 0f) {
                    for (int c = 0; c < 3; c++) {
                        float raised = CreepShading.mix(pal.flesh()[c], pal.rim()[c], 0.35f) * (0.75f + 0.5f * f.lum[i]);
                        col[c] = CreepShading.mix(col[c], raised, 0.85f * vein);
                    }
                }
                if (crust > -1.5f) {
                    float cover = CreepShading.clamp01(crust + 0.5f);
                    if (cover > 0f) {
                        float[] c2 = crustColour(cellX, cellY, crust, f.lum[i], noise[0], pal, surf);
                        for (int c = 0; c < 3; c++) col[c] = CreepShading.mix(col[c], c2[c], cover);
                    }
                }
                out[i] = argb(alpha, col);
            }
        }
        return out;
    }

    /** Leaves turning: toward straw and then a dull brown as {@code t} runs to one. */
    static void dry(float[] col, float lum, float t) {
        float l = 0.25f + 0.85f * lum;
        float[] straw = {l * 1.08f, l * 0.94f, l * 0.6f};
        float[] brown = {l * 0.82f, l * 0.66f, l * 0.48f};
        float[] target = new float[3];
        for (int c = 0; c < 3; c++) target[c] = CreepShading.mix(straw[c], brown[c], CreepShading.smoothstep(0.5f, 1f, t));
        for (int c = 0; c < 3; c++) col[c] = CreepShading.mix(col[c], target[c], Math.min(1f, t * 1.15f));
    }

    /** Bark dying to a dry grey. */
    static void husk(float[] col, float lum, float t) {
        float l = 0.12f + 0.7f * lum;
        float[] grey = {l * 0.78f, l * 0.7f, l * 0.62f};
        for (int c = 0; c < 3; c++) col[c] = CreepShading.mix(col[c], grey[c], t);
    }

    /**
     * The crust at a point {@code inside} pixels in from its edge: membrane and plates from the creep's surface,
     * veins, a beaded lip lighter toward the rim colour a few pixels in, and a dark outline at the edge itself;
     * shaded by the crown under it so the lobes still read.
     */
    static float[] crustColour(float cellX, float cellY, float inside, float lum, float cloud, CreepPalette pal,
                               float[] surf) {
        CreepSurface.sample(cellX, cellY, 1f, surf);
        float plate = surf[0], tone = surf[1], vein = surf[2], blister = surf[3];
        float plated = CreepShading.smoothstep(3f, 9f, inside) * CreepShading.smoothstep(0.25f, 0.55f, cloud + 0.2f);
        float seam = CreepShading.smoothstep(0.14f, 0f, plate) * plated;
        float lip = CreepShading.bump(inside, 3.5f, 2.5f) * (0.6f + 0.4f * blister);
        float outline = CreepShading.smoothstep(1.2f, 0f, inside);
        float light = 0.42f + 1.0f * lum;
        float[] out = new float[3];
        for (int c = 0; c < 3; c++) {
            float membrane = pal.base()[c] * (0.8f + 0.25f * cloud) * (0.88f + 0.3f * blister);
            float plateCol = pal.flesh()[c] * (0.82f + 0.36f * tone);
            float alb = CreepShading.mix(membrane, plateCol, plated * (1f - seam));
            alb = CreepShading.mix(alb, pal.base()[c] * 0.6f, seam * 0.5f);
            alb = CreepShading.mix(alb, pal.rim()[c], 0.55f * lip);
            alb = CreepShading.mix(alb, pal.vein()[c], 0.6f * vein * CreepShading.smoothstep(2f, 8f, inside));
            out[c] = alb * light * (1f - 0.45f * outline);
        }
        return out;
    }

    private static int argb(float alpha, float[] col) {
        int a = Math.round(CreepShading.clamp01(alpha) * 255f);
        int r = Math.round(CreepShading.clamp01(col[0]) * 255f);
        int g = Math.round(CreepShading.clamp01(col[1]) * 255f);
        int b = Math.round(CreepShading.clamp01(col[2]) * 255f);
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** The staged frames of one canopy side by side, for review: base, then each stage. */
    static List<BufferedImage> strip(BufferedImage canopies, BufferedImage damaged, JSONObject doodad, String style) throws Exception {
        int x = doodad.getInt("col") * CELL, y = doodad.getInt("row") * CELL;
        JSONArray fp = doodad.getJSONArray("footprintCells");
        int w = fp.getInt(0) * CELL, h = fp.getInt(1) * CELL;
        Frame healthy = new Frame(canopies, x, y, w, h), broken = new Frame(damaged, x, y, w, h);
        List<BufferedImage> frames = new ArrayList<>();
        frames.add(canopies.getSubimage(x, y, w, h));
        for (int stage = 1; stage <= 4; stage++) {
            int[] px = stage(healthy, broken, style, stage, CreepPalette.HIVE);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, w, h, px, 0, w);
            frames.add(img);
        }
        return frames;
    }
}
