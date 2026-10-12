package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;

/** Ground-side floor finishes belong to each packed room, including its apertures and fixture underlay. */
public final class InstallationFloors {
    private InstallationFloors() {}

    public static void dress(GenContext ctx, List<RoomPacker.Placed> rooms) {
        for (var room : rooms) {
            String material = material(room.purpose());
            for (int[] cell : room.shape().filled())
                coat(ctx, room.originX() + cell[0], room.originY() + cell[1], material);
            for (int[] cell : room.shape().wall()) {
                int x = room.originX() + cell[0], y = room.originY() + cell[1];
                if (ctx.grid.inBounds(x, y) && (ctx.grid.isDoorway(x, y) || ctx.topology.isWindow(x, y)))
                    coat(ctx, x, y, material);
            }
        }
    }

    private static void coat(GenContext ctx, int x, int y, String material) {
        if (!ctx.grid.inBounds(x, y) || ctx.topology.isWall(x, y) || ctx.topology.isWater(x, y)
                || (!ctx.grid.isWalkable(x, y) && !ctx.topology.isFixture(x, y))) return;
        // Keep specifically authored floor finishes. This replaces the shared legacy default only.
        if (ctx.topology.getSurface(x, y) == 0)
            ctx.topology.setSurface(x, y, ctx.topology.surfaceIndex(material));
    }

    static String material(RoomPurpose purpose) {
        return switch (purpose) {
            case BARRACKS, CREW_QUARTERS, MESS_HALL, CREW_LOUNGE -> "installation.accommodation-floor";
            case KEEP_THRONE, CONTROL_ROOM, CIVIC_RECEPTION, CIVIC_OFFICE, CONFERENCE_ROOM,
                    MEDICAL_RECEPTION, MEDICAL_CORRIDOR, TRIAGE, TREATMENT_ROOM,
                    PATIENT_WARD, PHARMACY -> "installation.command-floor";
            default -> "installation.concrete-floor";
        };
    }
}
