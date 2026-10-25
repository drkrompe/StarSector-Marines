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
import java.util.Random;

/**
 * Bakes the trees hive creep overgrows (`colony-breach-nouns.md`, Creep): the
 * shipped {@code forest-blight} sheet and its tileset, derived from the canopy
 * and damaged-canopy sheets with the creep's own surface ({@link CreepSurface})
 * and palette ({@link CreepPalette#HIVE}), so a crust on a crown is the crust on
 * the ground.
 *
 * <p>A tree is taken in four stages, as a wall is: veins reaching in over the
 * crown from its rim while the leaves start to turn; a crust rolling in from the
 * rim with its beaded lip while the leaves brown; the crown withering, its dead
 * leaves falling from a bare skeleton; and the dead tree, a skeleton of bleached,
 * bone-pale limbs forking out to the crown's own outline, crusted at the trunk
 * with veins running out along the limbs. The skeleton is grown here rather than
 * taken from the damaged sheet, whose few broken limbs vanish at play zoom, and
 * a dark outline and a soft ground shadow stand it up off the creep. Every stage
 * exists for the intact and the war-damaged crown.
 *
 * <p>A dead tree felled lies as dead wood ({@link #felled}): the destroyed
 * sheet's stump and fallen limbs, bleached bone-pale and bare of leaves, the
 * stump crusted and veins running out along the limbs. It is a sheet of its own,
 * {@code forest-blight-felled}, so the staged sheet's texture keeps its size.
 *
 * <p>Run with {@code gradlew.bat bakeCreepBlight}; it rewrites
 * {@code mod/graphics/doodads/forest-blight.png},
 * {@code mod/graphics/doodads/forest-blight-felled.png} and their tilesets.
 */
public final class CreepBlightBaker {

    /** The canopy and damaged sheets share this layout: one slot of the output sheet each. */
    static final int SLOT_W = 896, SLOT_H = 704, CELL = 64;
    /** Slots across the output sheet. */
    static final int SLOTS_ACROSS = 4;

    /** One output slot: which source sheet and which stage. */
    record Slot(String suffix, boolean damaged, int stage) { }

    static final List<Slot> SLOTS = List.of(
            new Slot("-blight-1", false, 1), new Slot("-blight-2", false, 2),
            new Slot("-blight-3", false, 3), new Slot("-blight-4", false, 4),
            new Slot("-damaged-blight-1", true, 1), new Slot("-damaged-blight-2", true, 2),
            new Slot("-damaged-blight-3", true, 3), new Slot("-damaged-blight-4", true, 4));

    private CreepBlightBaker() { }

    /** How many limbs a dead tree grows and how far they fork: {@code sparse} (the default) or {@code dense} ({@code -Pskeleton=dense}). */
    static boolean dense;

    public static void main(String[] args) throws Exception {
        Path mod = Path.of(args.length > 0 ? args[0] : "mod");
        dense = args.length > 1 && "dense".equals(args[1]);
        BufferedImage canopies = ImageIO.read(mod.resolve("graphics/doodads/forest-canopies.png").toFile());
        BufferedImage damaged = ImageIO.read(mod.resolve("graphics/doodads/forest-damaged.png").toFile());
        JSONObject tileset = new JSONObject(Files.readString(mod.resolve("data/tilesets/forest-canopies.tileset.json")));
        BufferedImage sheet = bake(canopies, damaged, tileset.getJSONArray("doodads"), CreepPalette.HIVE);
        File png = mod.resolve("graphics/doodads/forest-blight.png").toFile();
        ImageIO.write(sheet, "png", png);
        Files.writeString(mod.resolve("data/tilesets/forest-blight.tileset.json"),
                tileset(tileset.getJSONArray("doodads")).toString(2) + "\n", StandardCharsets.UTF_8);
        System.out.println("[creep-blight] wrote " + png + " (" + sheet.getWidth() + "x" + sheet.getHeight() + ")");

        BufferedImage destroyed = ImageIO.read(mod.resolve("graphics/doodads/forest-destroyed.png").toFile());
        JSONArray fallen = new JSONObject(Files.readString(mod.resolve("data/tilesets/forest-destroyed.tileset.json")))
                .getJSONArray("doodads");
        File felledPng = mod.resolve("graphics/doodads/" + FELLED_SHEET + ".png").toFile();
        ImageIO.write(felledSheet(destroyed, fallen, CreepPalette.HIVE), "png", felledPng);
        Files.writeString(mod.resolve("data/tilesets/" + FELLED_SHEET + ".tileset.json"),
                felledTileset(fallen).toString(2) + "\n", StandardCharsets.UTF_8);
        System.out.println("[creep-blight] wrote " + felledPng);
    }

    /** The felled dead trees' sheet, and the suffix on a destroyed tree's id naming its dead-wood twin. */
    static final String FELLED_SHEET = "forest-blight-felled", FELLED_SUFFIX = "-blight";

    /** The felled sheet: every destroyed tree as dead wood, where the destroyed sheet has it. */
    static BufferedImage felledSheet(BufferedImage destroyed, JSONArray doodads, CreepPalette pal) throws Exception {
        BufferedImage out = new BufferedImage(destroyed.getWidth(), destroyed.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < doodads.length(); i++) {
            JSONObject d = doodads.getJSONObject(i);
            int x = d.getInt("col") * CELL, y = d.getInt("row") * CELL;
            JSONArray fp = d.getJSONArray("footprintCells");
            int w = fp.getInt(0) * CELL, h = fp.getInt(1) * CELL;
            out.setRGB(x, y, w, h, felled(new Frame(destroyed, x, y, w, h), pal), 0, w);
        }
        return out;
    }

    /** The felled sheet's tileset: each destroyed tree's footprint, cover and placement, renamed. */
    static JSONObject felledTileset(JSONArray doodads) throws Exception {
        JSONArray cells = new JSONArray(), defs = new JSONArray();
        for (int i = 0; i < doodads.length(); i++) {
            JSONObject def = new JSONObject(doodads.getJSONObject(i).toString());
            String id = def.getString("id") + FELLED_SUFFIX;
            def.put("id", id);
            defs.put(def);
            cells.put(new JSONObject().put("name", id).put("col", def.getInt("col")).put("row", def.getInt("row")));
        }
        return new JSONObject().put("cellPx", CELL).put("sheet", "graphics/doodads/" + FELLED_SHEET + ".png")
                .put("cells", cells).put("doodads", defs);
    }

    /** How far, as a share of the footprint's half-size, the crust reaches out from the stump. */
    static final float FELLED_CRUST = 0.13f;
    /** A felled tree lies on the ground: its shadow is thrown this much of a standing dead tree's. */
    static final float FELLED_SHADOW = 0.35f;

    /**
     * A destroyed tree, dead: its stump and fallen limbs bleached bone-pale with the bark's own shading kept as
     * grain, its leaves gone, the stump crusted over and veins running out along the limbs, outlined over a low
     * shadow.
     */
    static int[] felled(Frame f, CreepPalette pal) {
        int w = f.w, h = f.h, n = w * h;
        float cx = w * 0.5f, cy = h * 0.5f, radius = Math.min(w, h) * 0.5f;
        float[] r = new float[n], g = new float[n], b = new float[n], a = new float[n];
        float[] noise = new float[4], fine = new float[4], surf = new float[4];
        // The wood: closed over the holes its leaves leave, then opened so the twigs' leafy scraps do not speckle.
        boolean[] wood = new boolean[n];
        for (int i = 0; i < n; i++) wood[i] = f.a[i] >= 0.5f && !f.leaf(i);
        wood = shrink(grow(wood, w, h, 1), w, h, 1);
        wood = grow(shrink(wood, w, h, 1), w, h, 1);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (!wood[i] || f.a[i] <= 0.01f) continue;
                float cellX = x / (float) CELL, cellY = y / (float) CELL;
                CreepNoise.sample(cellX * 2.6f + 7.1f, cellY * 2.6f + 2.3f, noise);
                CreepNoise.sample(cellX * 6f + 1.9f, cellY * 6f + 4.4f, fine);
                float rr = (float) Math.hypot(x + 0.5f - cx, y + 0.5f - cy) / radius;
                float l = CreepShading.clamp01(0.5f + 0.75f * f.lum[i]) * (0.9f + 0.2f * fine[0]);
                float[] col = new float[3];
                for (int c = 0; c < 3; c++) col[c] = BONE[c] * l;
                CreepSurface.sample(cellX * CreepShading.WALL_VEIN_SCALE, cellY * CreepShading.WALL_VEIN_SCALE,
                        CreepShading.WALL_VEIN_SCALE, surf);
                float vein = CreepShading.clamp01(surf[2] * 1.6f) * CreepShading.smoothstep(0.75f, 0.2f, rr);
                for (int c = 0; c < 3; c++) {
                    float raised = CreepShading.mix(pal.flesh()[c], pal.rim()[c], 0.35f) * (0.6f + 0.4f * l);
                    col[c] = CreepShading.mix(col[c], raised, 0.8f * vein);
                }
                float crust = (FELLED_CRUST + (noise[0] - 0.5f) * 0.12f - rr) * radius;
                float cover = CreepShading.clamp01(crust + 0.5f);
                if (cover > 0f) {
                    float[] c2 = crustColour(cellX, cellY, crust, 0.35f + 0.4f * l, noise[0], pal, surf);
                    for (int c = 0; c < 3; c++) col[c] = CreepShading.mix(col[c], c2[c], cover);
                }
                r[i] = col[0];
                g[i] = col[1];
                b[i] = col[2];
                a[i] = f.a[i];
            }
        }
        return standUp(r, g, b, a, a.clone(), w, h, FELLED_SHADOW);
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
                long seed = d.getString("id").hashCode() * 0x9E3779B97F4A7C15L + (slot.damaged() ? 1 : 0);
                int[] px = slot.stage() >= 3 ? dead(healthy, broken, slot.damaged(), slot.stage(), seed, pal)
                        : stage(slot.damaged() ? broken : healthy, slot.stage(), pal);
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

    /** One of the first two stages' pixels, drawn on the crown {@code f}: veins, then a crust from the rim. */
    static int[] stage(Frame f, int stage, CreepPalette pal) {
        int[] out = new int[f.w * f.h];
        float[] surf = new float[4], noise = new float[4], fine = new float[4];
        for (int y = 0; y < f.h; y++) {
            for (int x = 0; x < f.w; x++) {
                int i = y * f.w + x;
                if (f.a[i] <= 0.01f) continue;
                float cellX = x / (float) CELL, cellY = y / (float) CELL;
                CreepNoise.sample(cellX * 2.6f + 7.1f, cellY * 2.6f + 2.3f, noise);
                CreepNoise.sample(cellX * 6f + 1.9f, cellY * 6f + 4.4f, fine);
                float lobes = (noise[0] - 0.5f) * 0.55f + (fine[0] - 0.5f) * 0.15f;
                float alpha = f.a[i];
                float[] col = {f.r[i], f.g[i], f.b[i]};
                // How far the leaves have turned, and the crust's extent: positive inside it, in pixels.
                float wilt, crust;
                float veinReach;
                switch (stage) {
                    case 1 -> {
                        wilt = 0.22f;
                        crust = -1f;
                        veinReach = 0.38f + lobes;
                    }
                    default -> {
                        wilt = 0.55f;
                        crust = (0.16f + lobes * 0.8f - f.rim[i]) * f.deepest;
                        veinReach = 0.75f + lobes;
                    }
                }
                if (f.leaf(i)) dry(col, f.lum[i], wilt);
                // Veins reach in from the rim ahead of the crust.
                CreepSurface.sample(cellX * CreepShading.WALL_VEIN_SCALE, cellY * CreepShading.WALL_VEIN_SCALE,
                        CreepShading.WALL_VEIN_SCALE, surf);
                float veinIn = CreepShading.smoothstep(veinReach, veinReach - 0.12f, f.rim[i]);
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

    /** Dead wood's colour: bleached to a pale bone grey that reads over the dark creep. */
    static final float[] BONE = {0.86f, 0.83f, 0.76f};
    /** The outline round a dead tree, and the shadow it casts on the creep. */
    static final float[] OUTLINE = {0.10f, 0.08f, 0.10f}, SHADOW = {0.05f, 0.03f, 0.06f};
    /** Outline width and shadow throw and softness, in sheet pixels (64 a cell). */
    static final float OUTLINE_PX = 2.5f, SHADOW_DX = 9f, SHADOW_DY = 12f;
    static final int SHADOW_BLUR = 5;
    static final float SHADOW_ALPHA = 0.5f;

    /**
     * The last two stages: a skeleton of bare, bone-pale limbs forking out from the trunk to the crown's own outline,
     * crusted at the trunk with veins running out along the limbs, under the dead leaves still hanging on (half of
     * them withering, a few at the last), drawn with a dark outline over a soft shadow on the ground.
     */
    static int[] dead(Frame healthy, Frame broken, boolean damaged, int stage, long seed, CreepPalette pal) {
        int w = healthy.w, h = healthy.h, n = w * h;
        float cx = w * 0.5f, cy = h * 0.5f, radius = Math.min(w, h) * 0.5f;
        float[] wood = new float[n], light = new float[n];
        // A war-damaged tree keeps the damaged sheet's own limbs and their breaks: brown bark, opened so the
        // bark-coloured scraps among its leaves do not count as wood. An intact tree's limbs are all grown.
        boolean[] bark = new boolean[n];
        for (int i = 0; i < n && damaged; i++) {
            bark[i] = broken.a[i] >= 0.5f && !broken.leaf(i) && broken.r[i] > broken.g[i] + 0.02f;
        }
        bark = grow(shrink(bark, w, h, LIMB_OPEN), w, h, LIMB_OPEN);
        for (int i = 0; i < n; i++) {
            if (!bark[i]) continue;
            wood[i] = broken.a[i];
            light[i] = 0.55f + 0.6f * broken.lum[i];
        }
        // Grown limbs, out along the crown's reach in every direction. A damaged crown keeps only its own stubs.
        Frame reachOf = damaged ? broken : healthy;
        Random rng = new Random(seed);
        int mains = dense ? 9 + rng.nextInt(3) : 6 + rng.nextInt(3);
        double start = rng.nextDouble() * Math.PI * 2;
        float base = Math.max(3.5f, radius * 0.075f);
        for (int k = 0; k < mains; k++) {
            double angle = start + k * Math.PI * 2 / mains + (rng.nextDouble() - 0.5) * 0.5;
            float reach = reach(reachOf, cx, cy, angle);
            if (reach < radius * 0.25f) continue;
            limb(wood, light, w, h, cx, cy, angle, reach * (dense ? 0.42f : 0.5f), base, dense ? 6 : 4, rng, reach, cx, cy);
        }
        // Leaves still hanging on, dead and dry.
        float[] noise = new float[4], fine = new float[4], surf = new float[4];
        float keep = stage == 3 ? 0.45f : 0.97f;
        float[] r = new float[n], g = new float[n], b = new float[n], a = new float[n];
        // What the outline goes round: the whole crown while it withers, the bare wood once it is dead.
        float[] outlined = new float[n];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float cellX = x / (float) CELL, cellY = y / (float) CELL;
                CreepNoise.sample(cellX * 2.6f + 7.1f, cellY * 2.6f + 2.3f, noise);
                CreepNoise.sample(cellX * 6f + 1.9f, cellY * 6f + 4.4f, fine);
                float rr = (float) Math.hypot(x + 0.5f - cx, y + 0.5f - cy) / radius;
                float[] col = new float[3];
                float alpha = 0f;
                if (wood[i] > 0f) {
                    float grain = 0.88f + 0.24f * fine[0];
                    float l = CreepShading.clamp01(light[i]) * grain;
                    for (int c = 0; c < 3; c++) col[c] = BONE[c] * l;
                    // Crust up the trunk and the bases of the limbs, veins out along them.
                    float lobes = (noise[0] - 0.5f) * 0.3f;
                    float crust = ((stage == 3 ? 0.12f : 0.2f) + lobes - rr) * radius;
                    CreepSurface.sample(cellX * CreepShading.WALL_VEIN_SCALE, cellY * CreepShading.WALL_VEIN_SCALE,
                            CreepShading.WALL_VEIN_SCALE, surf);
                    float vein = CreepShading.clamp01(surf[2] * 1.6f)
                            * CreepShading.smoothstep(stage == 3 ? 0.45f : 0.7f, 0.15f, rr);
                    for (int c = 0; c < 3; c++) {
                        float raised = CreepShading.mix(pal.flesh()[c], pal.rim()[c], 0.35f) * (0.6f + 0.4f * l);
                        col[c] = CreepShading.mix(col[c], raised, 0.8f * vein);
                    }
                    float cover = CreepShading.clamp01(crust + 0.5f);
                    if (cover > 0f) {
                        float[] c2 = crustColour(cellX, cellY, crust, 0.35f + 0.4f * l, noise[0], pal, surf);
                        for (int c = 0; c < 3; c++) col[c] = CreepShading.mix(col[c], c2[c], cover);
                    }
                    alpha = wood[i];
                }
                outlined[i] = alpha;
                if (broken.a[i] > 0.01f && broken.leaf(i) && fine[1] >= keep) {
                    float[] leaf = {broken.r[i], broken.g[i], broken.b[i]};
                    dry(leaf, broken.lum[i], stage == 3 ? 0.85f : 1f);
                    float la = broken.a[i];
                    for (int c = 0; c < 3; c++) col[c] = CreepShading.mix(col[c] * alpha, leaf[c], la) / Math.max(1e-4f, la + alpha * (1 - la));
                    alpha = la + alpha * (1 - la);
                    if (stage == 3) outlined[i] = alpha;
                }
                r[i] = col[0];
                g[i] = col[1];
                b[i] = col[2];
                a[i] = alpha;
            }
        }
        return standUp(r, g, b, a, outlined, w, h, 1f);
    }

    /** Radius, in sheet pixels, of the opening that keeps only the damaged sheet's limbs. */
    static final int LIMB_OPEN = 2;

    /** A mask with every pixel within {@code r} of a clear one cleared. */
    static boolean[] shrink(boolean[] m, int w, int h, int r) {
        boolean[] out = new boolean[m.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean all = m[y * w + x];
                for (int dy = -r; dy <= r && all; dy++) {
                    for (int dx = -r; dx <= r && all; dx++) {
                        int sx = x + dx, sy = y + dy;
                        all = sx >= 0 && sy >= 0 && sx < w && sy < h && m[sy * w + sx];
                    }
                }
                out[y * w + x] = all;
            }
        }
        return out;
    }

    /** A mask with every pixel within {@code r} of a set one set. */
    static boolean[] grow(boolean[] m, int w, int h, int r) {
        boolean[] out = new boolean[m.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!m[y * w + x]) continue;
                for (int dy = -r; dy <= r; dy++) {
                    for (int dx = -r; dx <= r; dx++) {
                        int sx = x + dx, sy = y + dy;
                        if (sx >= 0 && sy >= 0 && sx < w && sy < h) out[sy * w + sx] = true;
                    }
                }
            }
        }
        return out;
    }

    /** How far the crown reaches from its centre along {@code angle}, in pixels: to its last opaque pixel. */
    static float reach(Frame f, float cx, float cy, double angle) {
        float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle), last = 0f;
        for (float d = 0; ; d += 1f) {
            int x = Math.round(cx + dx * d), y = Math.round(cy + dy * d);
            if (x < 0 || y < 0 || x >= f.w || y >= f.h) break;
            if (f.a[y * f.w + x] > 0.3f) last = d;
        }
        return last;
    }

    /**
     * One limb from ({@code x0}, {@code y0}) along {@code angle}, tapering from {@code width}; it forks in two (and
     * now and then three) until {@code depth} runs out, the forks never reaching past the crown's reach.
     */
    static void limb(float[] wood, float[] light, int w, int h, float x0, float y0, double angle, float length,
                     float width, int depth, Random rng, float reach, float cx, float cy) {
        // A limb bends once along its length.
        double bend = (rng.nextDouble() - 0.5) * 0.45;
        float mx = x0 + (float) Math.cos(angle) * length * 0.5f, my = y0 + (float) Math.sin(angle) * length * 0.5f;
        double angle2 = angle + bend;
        float x1 = mx + (float) Math.cos(angle2) * length * 0.5f, y1 = my + (float) Math.sin(angle2) * length * 0.5f;
        float mid = width * 0.82f, end = width * 0.66f;
        segment(wood, light, w, h, x0, y0, mx, my, width, mid);
        segment(wood, light, w, h, mx, my, x1, y1, mid, end);
        if (depth <= 0 || end < (dense ? 1.2f : 1.6f)) return;
        int forks = rng.nextInt(4) == 0 ? 3 : 2;
        for (int k = 0; k < forks; k++) {
            double spread = 0.32 + rng.nextDouble() * 0.3;
            double a = angle2 + (forks == 3 ? (k - 1) * spread : (k == 0 ? -spread : spread)) + (rng.nextDouble() - 0.5) * 0.15;
            float left = reach - (float) Math.hypot(x1 - cx, y1 - cy);
            float next = Math.min(length * (0.6f + 0.15f * rng.nextFloat()), Math.max(0f, left) * 0.95f + 2f);
            if (next < 3f) continue;
            limb(wood, light, w, h, x1, y1, a, next, end, depth - 1, rng, reach, cx, cy);
        }
    }

    /** A tapered round stroke, lit as a cylinder with the light from the upper left. */
    static void segment(float[] wood, float[] light, int w, int h, float x0, float y0, float x1, float y1,
                        float w0, float w1) {
        float dx = x1 - x0, dy = y1 - y0, len2 = Math.max(1e-4f, dx * dx + dy * dy), len = (float) Math.sqrt(len2);
        float pad = Math.max(w0, w1) * 0.5f + 1.5f;
        int xa = Math.max(0, (int) Math.floor(Math.min(x0, x1) - pad)), xb = Math.min(w - 1, (int) Math.ceil(Math.max(x0, x1) + pad));
        int ya = Math.max(0, (int) Math.floor(Math.min(y0, y1) - pad)), yb = Math.min(h - 1, (int) Math.ceil(Math.max(y0, y1) + pad));
        // The light comes from the upper left: the side of the limb facing it is the lit one.
        float nx = -dy / len, ny = dx / len, facing = -(nx * -0.7071f + ny * -0.7071f);
        for (int y = ya; y <= yb; y++) {
            for (int x = xa; x <= xb; x++) {
                float px = x + 0.5f - x0, py = y + 0.5f - y0;
                float t = CreepShading.clamp01((px * dx + py * dy) / len2);
                float ex = px - dx * t, ey = py - dy * t;
                float half = (w0 + (w1 - w0) * t) * 0.5f;
                float d = (float) Math.sqrt(ex * ex + ey * ey);
                float cover = CreepShading.clamp01(half - d + 0.5f);
                if (cover <= 0f) continue;
                int i = y * w + x;
                if (cover <= wood[i]) continue;
                float across = half > 0f ? CreepShading.clamp01(d / half) : 0f;
                float side = Math.signum(ex * nx + ey * ny) * across * Math.signum(facing == 0 ? 1 : facing);
                float lit = 0.62f + 0.38f * (float) Math.sqrt(Math.max(0f, 1f - across * across)) + 0.18f * side;
                wood[i] = cover;
                light[i] = lit;
            }
        }
    }

    /**
     * Puts a soft shadow under the silhouette, thrown {@code throwScale} of a standing tree's, and a dark outline
     * round {@code outlined}, and packs the pixels.
     */
    static int[] standUp(float[] r, float[] g, float[] b, float[] a, float[] outlined, int w, int h, float throwScale) {
        int n = w * h;
        // Outline: the silhouette grown by OUTLINE_PX.
        int reach = (int) Math.ceil(OUTLINE_PX);
        float[] outline = new float[n];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float best = 0f;
                for (int dy = -reach; dy <= reach; dy++) {
                    for (int dx = -reach; dx <= reach; dx++) {
                        int sx = x + dx, sy = y + dy;
                        if (sx < 0 || sy < 0 || sx >= w || sy >= h) continue;
                        float fall = CreepShading.clamp01(OUTLINE_PX + 0.5f - (float) Math.sqrt(dx * dx + dy * dy));
                        best = Math.max(best, outlined[sy * w + sx] * fall);
                    }
                }
                outline[y * w + x] = best;
            }
        }
        // Shadow: the outlined silhouette thrown down and to the right, softened.
        float[] thrown = new float[n];
        int sx = Math.round(SHADOW_DX * throwScale), sy = Math.round(SHADOW_DY * throwScale);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int fx = x - sx, fy = y - sy;
                if (fx >= 0 && fy >= 0 && fx < w && fy < h) thrown[y * w + x] = outline[fy * w + fx];
            }
        }
        float[] shadow = blur(thrown, w, h, SHADOW_BLUR);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            float sa = shadow[i] * SHADOW_ALPHA, oa = outline[i] * 0.9f, ta = a[i];
            // Shadow, then outline, then the tree, each over the last.
            float ar = SHADOW[0] * sa, ag = SHADOW[1] * sa, ab = SHADOW[2] * sa, aa = sa;
            ar = OUTLINE[0] * oa + ar * (1 - oa);
            ag = OUTLINE[1] * oa + ag * (1 - oa);
            ab = OUTLINE[2] * oa + ab * (1 - oa);
            aa = oa + aa * (1 - oa);
            ar = r[i] * ta + ar * (1 - ta);
            ag = g[i] * ta + ag * (1 - ta);
            ab = b[i] * ta + ab * (1 - ta);
            aa = ta + aa * (1 - ta);
            out[i] = aa <= 0f ? 0 : argb(aa, new float[]{ar / aa, ag / aa, ab / aa});
        }
        return out;
    }

    /** A separable box blur of radius {@code r}, run twice. */
    static float[] blur(float[] src, int w, int h, int r) {
        float[] a = src.clone(), tmp = new float[src.length];
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float sum = 0f;
                    for (int d = -r; d <= r; d++) sum += a[y * w + Math.min(w - 1, Math.max(0, x + d))];
                    tmp[y * w + x] = sum / (2 * r + 1);
                }
            }
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float sum = 0f;
                    for (int d = -r; d <= r; d++) sum += tmp[Math.min(h - 1, Math.max(0, y + d)) * w + x];
                    a[y * w + x] = sum / (2 * r + 1);
                }
            }
        }
        return a;
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
    static List<BufferedImage> strip(BufferedImage canopies, BufferedImage damaged, JSONObject doodad) throws Exception {
        int x = doodad.getInt("col") * CELL, y = doodad.getInt("row") * CELL;
        JSONArray fp = doodad.getJSONArray("footprintCells");
        int w = fp.getInt(0) * CELL, h = fp.getInt(1) * CELL;
        Frame healthy = new Frame(canopies, x, y, w, h), broken = new Frame(damaged, x, y, w, h);
        List<BufferedImage> frames = new ArrayList<>();
        frames.add(canopies.getSubimage(x, y, w, h));
        for (int stage = 1; stage <= 4; stage++) {
            long seed = doodad.getString("id").hashCode() * 0x9E3779B97F4A7C15L;
            int[] px = stage >= 3 ? dead(healthy, broken, false, stage, seed, CreepPalette.HIVE)
                    : stage(healthy, stage, CreepPalette.HIVE);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, w, h, px, 0, w);
            frames.add(img);
        }
        return frames;
    }
}
