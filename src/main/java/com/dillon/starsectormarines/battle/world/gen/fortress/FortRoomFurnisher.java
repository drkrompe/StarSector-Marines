package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.FurnishableRoom;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.AuthoredFitting;

/**
 * Fits a fort room from its authored layout through the same {@link RoomFloor} every fitted room goes through: lanes are
 * never furnished, a fixture is placed entire or not at all, and a fill that would seal the room from its doors is thrown
 * away whole rather than left half-built.
 */
public final class FortRoomFurnisher {

    private FortRoomFurnisher() {}

    /**
     * Furnishes {@code room} as {@code spec} says.
     *
     * @return whether the fill was kept; false means it severed the room's own circulation and the room stands bare
     */
    public static boolean furnish(GenContext ctx, FurnishableRoom room, FortRoom spec) {
        int doodads = ctx.doodads.size();
        int berths = ctx.gantries.size();
        int work = ctx.fixtureTasks.size();
        RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
        new AuthoredFitting(spec.layout()).fit(floor);
        if (floor.circulationSurvives()) {
            floor.seal();
            return true;
        }
        ctx.doodads.subList(doodads, ctx.doodads.size()).clear();
        ctx.gantries.subList(berths, ctx.gantries.size()).clear();
        ctx.fixtureTasks.subList(work, ctx.fixtureTasks.size()).clear();
        return false;
    }
}
