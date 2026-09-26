package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

/** Pre-spawn placement only. The caller owns candidate order and semantic domain. */
public final class MechSpawnPlacement {
    private static final float[][] OFFSETS = {
            {0f, 0f}, {0.5f, 0f}, {-0.5f, 0f}, {0f, 0.5f}, {0f, -0.5f},
            {0.5f, 0.5f}, {-0.5f, 0.5f}, {0.5f, -0.5f}, {-0.5f, -0.5f}
    };

    private MechSpawnPlacement() {}

    public record Point(float x, float y) {
        public int cellX() { return (int) Math.floor(x); }
        public int cellY() { return (int) Math.floor(y); }
    }

    @FunctionalInterface
    public interface Domain { boolean contains(float x, float y); }

    /** Half-cell offsets admit wide bodies down the middle of even-width corridors. */
    public static Point nearCell(NavigationGrid grid, float radius, int cellX, int cellY,
                                 Domain domain, Domain available) {
        for (float[] offset : OFFSETS) {
            float x = cellX + 0.5f + offset[0];
            float y = cellY + 0.5f + offset[1];
            if (canPlace(grid, radius, x, y, domain) && available.contains(x, y)) {
                return new Point(x, y);
            }
        }
        return null;
    }

    /** Every cell touched by the body must belong to the caller's placement domain. */
    public static boolean canPlace(NavigationGrid grid, float radius, float x, float y,
                                    Domain domain) {
        if (!ManualTerrainMotion.canStand(grid, x, y, radius)) return false;
        for (int cy = (int) Math.floor(y - radius); cy <= (int) Math.floor(y + radius); cy++) {
            for (int cx = (int) Math.floor(x - radius); cx <= (int) Math.floor(x + radius); cx++) {
                if (overlapsCell(x, y, radius, cx, cy) && !domain.contains(cx + 0.5f, cy + 0.5f)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean overlapsCell(float x, float y, float radius, int cx, int cy) {
        float dx = Math.max(cx - x, Math.max(0f, x - cx - 1f));
        float dy = Math.max(cy - y, Math.max(0f, y - cy - 1f));
        return dx * dx + dy * dy < radius * radius - 0.000001f;
    }

    /** Reads live positions, including bodies spawned since the occupancy rebuild. */
    public static boolean unoccupied(UnitRosterService roster, float x, float y,
                                      float radius, long excluded) {
        long[] ids = roster.denseArray();
        for (int i = 0; i < roster.liveCount(); i++) {
            long id = ids[i];
            if (id == excluded || !roster.isAliveById(id) || !roster.world().hasPosition(id)) continue;
            // A parked aircraft owns its cell, not its targeting silhouette's whole disk.
            if (roster.identity().airframe(id) != null) {
                if ((int) Math.floor(x) == roster.world().cellX(id)
                        && (int) Math.floor(y) == roster.world().cellY(id)) return false;
                continue;
            }
            float separation = radius + roster.radius(id);
            float dx = roster.world().x(id) - x;
            float dy = roster.world().y(id) - y;
            if (dx * dx + dy * dy < separation * separation) return false;
        }
        return true;
    }
}
