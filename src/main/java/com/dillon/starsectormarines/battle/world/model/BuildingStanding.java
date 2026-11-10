package com.dillon.starsectormarines.battle.world.model;

import java.util.ArrayList;
import java.util.List;

/**
 * How much of a building still stands ({@code raid-demolish-the-works.md}): its <b>outline</b> is the wall cells that
 * enclose its floor, and its <b>standing share</b> is the part of that outline not yet knocked down. A wall cell falls
 * through the ordinary map-structure channel ({@code MapEditor.damageWall}); this only reads the result, so a building
 * brought down by charges, shells, a tank or a long fight is measured the same way.
 *
 * <p>A building is <b>demolished</b> once its standing share is at or under {@link #DEMOLISHED_AT}: half its outer wall
 * down, the recommended option of {@code raid-demolition-fall} on the owner's picks page.
 */
public final class BuildingStanding {

    private BuildingStanding() { }

    /** A building whose standing share is this or less counts as brought down. UNTUNED; owner pick pending. */
    public static final float DEMOLISHED_AT = 0.5f;

    /** Whether a cell holds a standing wall. */
    @FunctionalInterface
    public interface Walls {
        boolean standing(int x, int y);
    }

    /**
     * The outline of a building whose floor is {@code (cellsX[i], cellsY[i])}: every standing wall cell that touches a
     * floor cell, diagonals included, each once, in floor order. Read once, while the building is whole.
     */
    public static List<int[]> outline(int[] cellsX, int[] cellsY, Walls walls, int width, int height) {
        boolean[] seen = new boolean[width * height];
        List<int[]> outline = new ArrayList<>();
        for (int i = 0; i < cellsX.length; i++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int x = cellsX[i] + dx, y = cellsY[i] + dy;
                    if (x < 0 || y < 0 || x >= width || y >= height || seen[y * width + x]) continue;
                    if (!walls.standing(x, y)) continue;
                    seen[y * width + x] = true;
                    outline.add(new int[]{x, y});
                }
            }
        }
        return outline;
    }

    /**
     * The building standing on or beside cell {@code (x, y)} (the cell itself first, then its eight neighbours), or
     * null if there is none: how a machine in a hall finds the hall it falls with.
     */
    public static Building buildingAt(CellTopology topology, Buildings buildings, int x, int y) {
        for (int r = 0; r <= 1; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    int id = topology.getBuildingId(x + dx, y + dy);
                    Building building = id > 0 ? buildings.get(id) : null;
                    if (building != null) return building;
                }
            }
        }
        return null;
    }

    /** {@link #outline} of {@code building} as it stands on {@code topology}; empty for no building. */
    public static List<int[]> outline(Building building, CellTopology topology) {
        if (building == null) return List.of();
        return outline(building.cellsX, building.cellsY, topology::isWall, topology.getWidth(), topology.getHeight());
    }

    /** The share of {@code outline} still standing: 1 for a whole building, 0 for one razed; 0 for no outline. */
    public static float standingShare(List<int[]> outline, Walls walls) {
        if (outline.isEmpty()) return 0f;
        int standing = 0;
        for (int[] c : outline) if (walls.standing(c[0], c[1])) standing++;
        return standing / (float) outline.size();
    }

    /** Whether a building with this standing share counts as brought down. */
    public static boolean demolished(float standingShare) {
        return standingShare <= DEMOLISHED_AT;
    }

    /**
     * How far a building is toward demolished, 0 whole to 1 brought down, for a marker's progress: the fallen share
     * over the share that must fall.
     */
    public static float progress(float standingShare) {
        return Math.min(1f, Math.max(0f, (1f - standingShare) / (1f - DEMOLISHED_AT)));
    }
}
