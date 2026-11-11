package com.dillon.starsectormarines.ops.battleview;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Bakes the creep a stood-up wall wears ({@code colony-breach-nouns.md}, Creep; {@link CreepClimb}): the shipped
 * {@code graphics/decals/creep-wall.png}, shaded by {@link CreepShading} itself, the ground creep's own model, over a
 * creep edge laid along the top of each strip, so the crust on a wall is the crust on the ground: its beaded lip,
 * plates over membrane, veins and dark outline, and the veins it lays over a cliff face where it climbs one.
 *
 * <p>Four strips of {@link #CELLS_ALONG} cells by {@link #CELLS_UP} at {@link #CELL_PX} pixels a cell, the edge
 * {@link #EDGE} cells under each strip's top: living crust, dead crust (withered: the palette's dead tone), living
 * veins, dead veins. A wall shows the top of a strip as tall as its creep stands, so the plates keep their size on the
 * ground's scale. Run with {@code gradlew.bat bakeCreepWall}.
 */
public final class CreepWallBaker {

    static final int CELL_PX = CreepClimb.SHEET_CELL_PX, CELLS_ALONG = CreepClimb.SHEET_CELLS_ALONG,
            CELLS_UP = CreepClimb.SHEET_CELLS_UP;
    /** Where the edge lies under a strip's top, cells: room for the lip's lobes above it. */
    static final float EDGE = CreepClimb.SHEET_EDGE;

    private CreepWallBaker() { }

    public static void main(String[] args) throws Exception {
        Path mod = Path.of(args.length > 0 ? args[0] : "mod");
        BufferedImage sheet = bake(CreepPalette.HIVE);
        Path png = mod.resolve(BattleSprites.SPRITE_CREEP_WALL);
        Files.createDirectories(png.getParent());
        ImageIO.write(sheet, "png", png.toFile());
        System.out.println("[creep-wall] wrote " + png + " (" + sheet.getWidth() + "x" + sheet.getHeight() + ")");
    }

    static BufferedImage bake(CreepPalette pal) {
        int w = CELLS_ALONG * CELL_PX, h = CELLS_UP * CELL_PX;
        BufferedImage out = new BufferedImage(w, h * 4, BufferedImage.TYPE_INT_ARGB);
        // Veins on a dead wall are the dead tone, as the crust's body is.
        CreepPalette dead = new CreepPalette("dead", pal.base(), pal.dead(), pal.vein(), pal.glow(), pal.dead(),
                pal.dead(), pal.wilt());
        strip(out, 0, pal, 1f, false);
        strip(out, h, pal, 0f, false);
        strip(out, h * 2, pal, 1f, true);
        strip(out, h * 3, dead, 0f, true);
        return out;
    }

    /** One strip at {@code top}: the creep below an edge along it, as crust or as the veins over a face. */
    private static void strip(BufferedImage out, int top, CreepPalette pal, float vitality, boolean veins) {
        int w = CELLS_ALONG * CELL_PX, h = CELLS_UP * CELL_PX;
        Edge field = new Edge(vitality, veins);
        // The light falls from over the wall's top, onto the lip's upper side; no shadow is cast on the wall.
        CreepShading.Sun sun = new CreepShading.Sun(0f, -1f, 1.2f, 0f);
        CreepShading.Scratch tmp = new CreepShading.Scratch();
        float[] rgba = new float[4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                CreepShading.shade(field, (x + .5f) / CELL_PX, (y + .5f) / CELL_PX, CELL_PX, 0f, pal, sun, tmp, rgba);
                int a = Math.round(CreepShading.clamp01(rgba[3]) * 255f);
                int r = Math.round(CreepShading.clamp01(rgba[0]) * 255f);
                int g = Math.round(CreepShading.clamp01(rgba[1]) * 255f);
                int b = Math.round(CreepShading.clamp01(rgba[2]) * 255f);
                out.setRGB(x, top + y, a << 24 | r << 16 | g << 8 | b);
            }
        }
    }

    /**
     * A creep edge along {@code y = EDGE}, the creep full below it: coverage climbs half a unit a cell, so the shader's
     * distance in from the edge is in cells. For veins the whole strip is a face the creep stands round.
     */
    private record Edge(float vitality, boolean veins) implements CreepShading.Field {
        @Override public void sample(float cellX, float cellY, float[] out) {
            out[0] = coverage(cellX, cellY);
            out[1] = vitality;
            out[2] = 0f;
            out[3] = 0f;
        }

        @Override public float coverage(float cellX, float cellY) {
            return CreepShading.clamp01(.5f + (cellY - EDGE) * .5f);
        }

        @Override public void ground(float cellX, float cellY, float[] out) {
            out[0] = out[1] = out[2] = out[3] = 0f;
        }

        @Override public float face(float cellX, float cellY) {
            return veins ? 1f : 0f;
        }
    }
}
