package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;

/** Material identity belongs to the shell; combat keeps the ordinary structural wall cells. */
public final class InstallationWalls {
    public static final String PERIMETER = "installation.dark-concrete-wall";

    private InstallationWalls() {}

    /** Run after fittings and apertures so windows, doorways and fixtures keep their own surfaces. */
    public static void dress(GenContext ctx, List<RoomPacker.Placed> rooms) {
        for (var room : rooms) {
            String material = material(room.purpose());
            for (int[] cell : room.shape().wall()) {
                int x = room.originX() + cell[0], y = room.originY() + cell[1];
                if (!structural(ctx, x, y)) continue;
                String existing = ctx.topology.getSurfaceId(x, y);
                // A shared wall keeps the first building's identity; authored
                // specialty walls are not replaced by the installation default.
                if (existing == null || existing.equals("urban.wall")) {
                    ctx.topology.setSurface(x, y, ctx.topology.surfaceIndex(material));
                    ctx.topology.setWallGroundSurface(x, y, InstallationFloors.material(room.purpose()));
                }
            }
        }
    }

    public static void perimeter(GenContext ctx, boolean[][] wall) {
        int surface = ctx.topology.surfaceIndex(PERIMETER);
        for (int y = 0; y < ctx.height; y++) for (int x = 0; x < ctx.width; x++)
            if (wall[x][y] && structural(ctx, x, y)) {
                ctx.topology.setSurface(x, y, surface);
                ctx.topology.setWallGroundSurface(x, y, "installation.concrete-floor");
            }
    }

    private static boolean structural(GenContext ctx, int x, int y) {
        // Finalize has not necessarily tagged WALL yet. The stamped ring and
        // structural occupancy are the authority here, never a floor color.
        return ctx.grid.inBounds(x, y) && !ctx.grid.isWalkable(x, y)
                && !ctx.grid.isDoorway(x, y) && !ctx.topology.isFixture(x, y)
                && !ctx.topology.isWater(x, y);
    }

    static String material(RoomPurpose purpose) {
        return switch (purpose) {
            case BARRACKS, CREW_QUARTERS, MESS_HALL, CREW_LOUNGE -> "colony.masonry-wall";
            default -> "installation.light-concrete-wall";
        };
    }
}
