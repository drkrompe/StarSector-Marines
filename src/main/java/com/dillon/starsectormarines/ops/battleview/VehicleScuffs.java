package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.vehicle.HullLocations;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Worn plate drawn on a live ground hull ({@code combat-durability-nouns.md}): which scrape, scorch and gouge marks a
 * hull shows, as a pure function of its plates' armour. Nothing is recorded per hit, so a re-plated side comes back
 * clean and a review frame draws what the screen does.
 *
 * <p>Each plate owns {@link #SLOTS} slots, each with a position, turn, kind and wear threshold hashed from the
 * body's id, the plate and the slot. A slot shows once the plate's wear passes its threshold and never moves after,
 * so marks build up in place as a side is stripped. A slot's position is one of its plate's {@link Mask} points:
 * opaque hull far enough from any transparent pixel that the turned decal cannot hang off the silhouette.
 */
final class VehicleScuffs {

    private static final Logger LOG = Global.getLogger(VehicleScuffs.class);

    /**
     * The scuff sheet: one {@link #SHEET_CELL} px cell per mark, a row per stage (scrape, scorch, gouge) and a
     * column per variant ({@code art-source/vehicles/scuffs/}).
     */
    static final String SHEET = "graphics/battle/vehicles/scuffs.png";
    static final int SHEET_CELL = 128;

    /** Marks a fully stripped plate shows. */
    static final int SLOTS = 9;
    /** Wear at which the last slot has appeared: a plate this far gone shows every mark. */
    static final float FULL_WEAR = 0.9f;
    /** Wear over which a mark fades in once past its threshold. */
    static final float FADE = 0.05f;
    /** A mark's side as a share of the hull's narrower extent. */
    static final float DECAL_SHARE = 0.3f;
    /**
     * How far a mark's paint reaches from its centre, as a share of its drawn side: a turned mark stays inside this.
     * The scuff sheet keeps every mark inside the middle of its cell (its widest reaches 0.52).
     */
    static final float MARK_REACH = 0.53f;
    /** Variants of each stage on the scuff sheet: its columns. */
    static final int VARIANTS = 4;
    /** Alpha at or above which a pixel counts as hull. */
    static final int OPAQUE_ALPHA = 230;
    /**
     * Where a slot's stage changes, as a share of the region's slots (taken at the slot's middle): scrapes and pocks
     * below {@link #SCORCH_FROM}, scorch below {@link #GOUGE_FROM}, gouges and dents beyond. Nine slots give four,
     * three and two.
     */
    static final float SCORCH_FROM = 4f / 9f;
    static final float GOUGE_FROM = 7f / 9f;

    /** The stages, which are the scuff sheet's rows. */
    static final int SCRAPE = 0;
    static final int SCORCH = 1;
    static final int GOUGE = 2;

    private static final HullLocations.Plate[] PLATES = HullLocations.Plate.values();
    /** Per vehicle line: its hull frame's mask and its turret frame's; an entry is null where there is none. */
    private static final Map<VehicleType, Mask[]> MASKS = new EnumMap<>(VehicleType.class);
    /** Part-sprite masks by image path and region scheme; a null value is an image with no room for a mark. */
    private static final Map<String, Mask> PART_MASKS = new HashMap<>();

    private VehicleScuffs() {}

    /** Receives one mark: its centre in source pixels of the hull frame, its kind, turn and opacity. */
    interface Sink {
        void mark(float x, float y, int kind, int variant, float spinDegrees, float alpha);
    }

    /**
     * Where marks may sit on one frame: per region, the source-pixel points ({@code x, y} pairs) whose distance from
     * the nearest transparent pixel covers a mark's half-diagonal. A hull frame's regions are its plates, in
     * {@link HullLocations.Plate} order; a turret frame is one region.
     */
    static final class Mask {
        final int frameW;
        final int frameH;
        /** A mark's side in source pixels. */
        final float decalPx;
        final short[][] points;

        Mask(int frameW, int frameH, float decalPx, short[][] points) {
            this.frameW = frameW;
            this.frameH = frameH;
            this.decalPx = decalPx;
            this.points = points;
        }

        int count(int region) {
            return points[region].length / 2;
        }
    }

    /**
     * The mask of the whole image at {@code path}, {@code width x height} logical pixels, for a part sprite drawn on
     * its own (a mech's torso, arm, pod or foot): north-authored, so the image's top is the part's front. Built
     * once per process on first ask; part images are small, so that costs a fraction of a millisecond. Null when
     * the image cannot be read or has no room for a mark.
     */
    static Mask partMask(BattleSprites sprites, String path, int width, int height, boolean byPlate) {
        return partMask(sprites, path, width, height, byPlate, DECAL_SHARE);
    }

    /** {@link #partMask(BattleSprites, String, int, int, boolean)} for marks {@code decalShare} of the narrower extent. */
    static Mask partMask(BattleSprites sprites, String path, int width, int height, boolean byPlate,
                         float decalShare) {
        if (path == null) return null;
        String key = path + (byPlate ? "#plates" : "#whole") + (decalShare == DECAL_SHARE ? "" : "@" + decalShare);
        synchronized (PART_MASKS) {
            if (PART_MASKS.containsKey(key)) return PART_MASKS.get(key);
            Mask mask = null;
            try {
                BufferedImage image = sprites.readPixels(path);
                if (image != null) {
                    mask = build(image, new SpriteSheetFrames.Frame(0, 0, Math.min(width, image.getWidth()),
                            Math.min(height, image.getHeight())), 0f, byPlate, decalShare);
                    if (mask.decalPx <= 0f) mask = null;
                }
            } catch (Exception e) {
                LOG.warn("scuffs: could not read " + path + " — " + e.getMessage());
            }
            PART_MASKS.put(key, mask);
            return mask;
        }
    }

    /** {@code type}'s hull frame mask, or null; see {@link #masks}. */
    static Mask hullMask(BattleSprites sprites, VehicleType type, SpriteSheetFrames frames) {
        return masks(sprites, type, frames)[0];
    }

    /** {@code type}'s turret frame mask, or null when its turret is not a frame of its sheet; see {@link #masks}. */
    static Mask turretMask(BattleSprites sprites, VehicleType type, SpriteSheetFrames frames) {
        return masks(sprites, type, frames)[1];
    }

    /**
     * {@code type}'s hull and turret masks, built once per process from the sheet's pixels; an entry is null when
     * the sheet cannot be read or has no such frame. {@link BattleSprites#loadConvoySprite} asks for them when the
     * sheet loads, so no frame pays.
     */
    static Mask[] masks(BattleSprites sprites, VehicleType type, SpriteSheetFrames frames) {
        synchronized (MASKS) {
            Mask[] masks = MASKS.get(type);
            if (masks == null) {
                masks = new Mask[2];
                try {
                    BufferedImage sheet = sprites.readPixels(type.spritePath);
                    if (sheet != null) {
                        if (type.spriteFrame >= 0 && type.spriteFrame < frames.frames.length) {
                            masks[0] = build(sheet, frames.frames[type.spriteFrame], type.spriteFacingOffsetDeg, true);
                        }
                        if (type.turretFrame >= 0 && type.turretFrame < frames.frames.length) {
                            masks[1] = build(sheet, frames.frames[type.turretFrame], 0f, false);
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("scuffs: could not read " + type.spritePath + " — " + e.getMessage());
                }
                for (int i = 0; i < masks.length; i++) {
                    if (masks[i] != null && masks[i].decalPx <= 0f) masks[i] = null;
                }
                MASKS.put(type, masks);
            }
            return masks;
        }
    }

    /**
     * Reduces frame {@code f} of {@code sheet} to a mask. The frame is cut into square cells; a cell is hull when
     * every pixel sampled in it is opaque, and its inset is its chessboard distance to the nearest cell that is not
     * (the frame's border counting as not), less half a cell. The hull's own frame is the sprite's turned by
     * {@code facingOffsetDeg}: +x the hull's right, +y its nose. With {@code byPlate}, a point's region is the plate
     * whose part of the hull's opaque extent it falls in, split along the diagonals; without, there is one region.
     */
    static Mask build(BufferedImage sheet, SpriteSheetFrames.Frame f, float facingOffsetDeg, boolean byPlate) {
        return build(sheet, f, facingOffsetDeg, byPlate, DECAL_SHARE);
    }

    /** {@link #build(BufferedImage, SpriteSheetFrames.Frame, float, boolean)} for marks {@code decalShare} across. */
    static Mask build(BufferedImage sheet, SpriteSheetFrames.Frame f, float facingOffsetDeg, boolean byPlate,
                      float decalShare) {
        int step = Math.max(2, Math.round(Math.max(f.w, f.h) / 96f));
        int cols = f.w / step;
        int rows = f.h / step;
        int[] dist = new int[cols * rows];
        int far = cols + rows;
        int stride = Math.max(1, step / 4);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                dist[r * cols + c] = opaque(sheet, f.x + c * step, f.y + r * step, step, stride) ? far : 0;
            }
        }
        chessboard(dist, cols, rows);

        double rad = Math.toRadians(facingOffsetDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float halfRight = 0f;
        float halfFwd = 0f;
        float minR = Float.MAX_VALUE, maxR = -Float.MAX_VALUE, minF = Float.MAX_VALUE, maxF = -Float.MAX_VALUE;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (dist[r * cols + c] == 0) continue;
                float lx = (c + 0.5f) * step - f.w * 0.5f;
                float ly = f.h * 0.5f - (r + 0.5f) * step;
                float right = lx * cos - ly * sin;
                float fwd = lx * sin + ly * cos;
                halfRight = Math.max(halfRight, Math.abs(right));
                halfFwd = Math.max(halfFwd, Math.abs(fwd));
                minR = Math.min(minR, right);
                maxR = Math.max(maxR, right);
                minF = Math.min(minF, fwd);
                maxF = Math.max(maxF, fwd);
            }
        }
        int regions = byPlate ? PLATES.length : 1;
        if (halfRight <= 0f || halfFwd <= 0f) return new Mask(f.w, f.h, 0f, new short[regions][0]);
        float decalPx = decalShare * Math.min(maxR - minR, maxF - minF);
        float need = decalPx * MARK_REACH;

        int[] counts = new int[regions];
        short[][] points = new short[regions][cols * rows * 2];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int d = dist[r * cols + c];
                if (d == 0 || (d - 0.5f) * step < need) continue;
                float px = (c + 0.5f) * step;
                float py = (r + 0.5f) * step;
                float lx = px - f.w * 0.5f;
                float ly = f.h * 0.5f - py;
                int p = byPlate ? plateOf(lx * cos - ly * sin, lx * sin + ly * cos, halfRight, halfFwd).ordinal() : 0;
                points[p][counts[p]++] = (short) Math.round(px);
                points[p][counts[p]++] = (short) Math.round(py);
            }
        }
        for (int p = 0; p < regions; p++) points[p] = Arrays.copyOf(points[p], counts[p]);
        return new Mask(f.w, f.h, decalPx, points);
    }

    /** The plate whose region of the hull holds hull-frame point {@code (right, fwd)}. */
    static HullLocations.Plate plateOf(float right, float fwd, float halfRight, float halfFwd) {
        if (Math.abs(fwd) / halfFwd >= Math.abs(right) / halfRight) {
            return fwd >= 0f ? HullLocations.Plate.FRONT : HullLocations.Plate.REAR;
        }
        return right >= 0f ? HullLocations.Plate.RIGHT : HullLocations.Plate.LEFT;
    }

    /**
     * The marks {@code region} of {@code mask} shows at {@code wear} (0 whole, 1 stripped) on the body seeded
     * {@code seed}, handed to {@code sink} in slot order. Returns how many were handed over.
     */
    static int place(Mask mask, int region, float wear, long seed, Sink sink) {
        return place(mask, region, SLOTS, wear, seed, sink);
    }

    /** {@link #place(Mask, int, float, long, Sink)} with {@code slots} marks for a fully stripped region. */
    static int place(Mask mask, int region, int slots, float wear, long seed, Sink sink) {
        return place(mask, region, slots, wear, seed, sink, VehicleScuffs::stageOf);
    }

    /** A slot's kind from its place among its region's slots (0 first, 1 last). */
    interface Stager {
        int kind(float stage);
    }

    /** The hull's stages: scrapes and pocks, then scorch, then gouges and dents. */
    static int stageOf(float stage) {
        return stage < SCORCH_FROM ? SCRAPE : stage < GOUGE_FROM ? SCORCH : GOUGE;
    }

    /**
     * {@link #place(Mask, int, int, float, long, Sink)} with each slot's kind chosen by {@code stager} from its place
     * among the region's slots, so a body other than a hull lays its own stages by the same slot law.
     */
    static int place(Mask mask, int region, int slots, float wear, long seed, Sink sink, Stager stager) {
        int count = mask.count(region);
        if (wear <= 0f || count == 0) return 0;
        short[] points = mask.points[region];
        int placed = 0;
        for (int slot = 0; slot < slots; slot++) {
            long h = mix(seed ^ (region + 1) * 0x9E3779B97F4A7C15L ^ (slot + 1) * 0xC2B2AE3D27D4EB4FL);
            float jitter = 0.15f + 0.7f * ((h >>> 40) & 0xFFFF) / 65535f;
            float threshold = FULL_WEAR * (slot + jitter) / slots;
            float alpha = Math.min(1f, (wear - threshold) / FADE);
            if (alpha <= 0f) break;
            int at = (int) Math.floorMod(h, (long) count) * 2;
            int kind = stager.kind((slot + 0.5f) / slots);
            float spin = ((h >>> 20) & 0x3FF) * (360f / 1024f);
            sink.mark(points[at], points[at + 1], kind, (int) ((h >>> 56) % VARIANTS), spin, alpha);
            placed++;
        }
        return placed;
    }

    /** Wear: the share of {@code max} armour gone, 0 for a location that never had any. */
    static float wear(float armor, float max) {
        return max > 0f ? Math.max(0f, 1f - armor / max) : 0f;
    }

    private static boolean opaque(BufferedImage sheet, int x0, int y0, int step, int stride) {
        for (int y = y0; y < y0 + step; y += stride) {
            for (int x = x0; x < x0 + step; x += stride) {
                if (x >= sheet.getWidth() || y >= sheet.getHeight()) return false;
                if ((sheet.getRGB(x, y) >>> 24) < OPAQUE_ALPHA) return false;
            }
        }
        return true;
    }

    /** Two-pass chessboard distance to the nearest zero cell, the grid's outside counting as zero. */
    private static void chessboard(int[] d, int cols, int rows) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                if (d[i] == 0) continue;
                int best = Math.min(r, c) + 1;
                if (r > 0) {
                    best = Math.min(best, d[i - cols] + 1);
                    if (c > 0) best = Math.min(best, d[i - cols - 1] + 1);
                    if (c < cols - 1) best = Math.min(best, d[i - cols + 1] + 1);
                }
                if (c > 0) best = Math.min(best, d[i - 1] + 1);
                d[i] = Math.min(d[i], best);
            }
        }
        for (int r = rows - 1; r >= 0; r--) {
            for (int c = cols - 1; c >= 0; c--) {
                int i = r * cols + c;
                if (d[i] == 0) continue;
                int best = Math.min(rows - 1 - r, cols - 1 - c) + 1;
                if (r < rows - 1) {
                    best = Math.min(best, d[i + cols] + 1);
                    if (c > 0) best = Math.min(best, d[i + cols - 1] + 1);
                    if (c < cols - 1) best = Math.min(best, d[i + cols + 1] + 1);
                }
                if (c < cols - 1) best = Math.min(best, d[i + 1] + 1);
                d[i] = Math.min(d[i], best);
            }
        }
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
