package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.List;

/**
 * A big ship's gymnasium, laid out as a cruise liner's is: a two-lane running track round the whole compartment,
 * the cardio and strength floor inside it on one side, and a pool with loungers along its deck on the other.
 *
 * <p>Authored for a 28 by 14 compartment ({@code RoomRecipe.GRAND_GYM}); {@link ShipGymFitting} hands a room that
 * size here. Canonical rows run up the screen, so a machine's console, flywheel or bar is its highest row and it is
 * worked from the free cell below its foot, as in the smaller gym.
 *
 * <p>The track is the room's circulation as well as its track: every hatch opens onto it, and the floor and the pool
 * deck are reached across it. It is run on its inner lane: twelve {@link Affordance#LAP} markers round the ring, at
 * the four corners and every six or seven cells between, published in running order and each faced at the next, so
 * a runner keeps to the lane and turns the corners rather than cutting across the floor. The outer lane is left to
 * whoever is crossing to a hatch.
 */
public final class GrandGymFitting implements RoomFitting {
    static final int WIDTH = 28, HEIGHT = 14;
    private static final String KIT = "ship-gym.";
    private static final String TRACK = "ship-gym-track.track";
    private static final String LOUNGER = "ship-gym-track.lounger";

    @Override public RoomPurpose purpose() { return RoomPurpose.GYMNASIUM; }

    /** Whether a room of this canonical size takes this layout and every piece of it is installed. */
    static boolean fits(int width, int height) {
        if (width < WIDTH || height < HEIGHT) return false;
        var tiles = TileRegistry.installed();
        for (String id : List.of("treadmill", "rower", "bench-press", "weights"))
            if (tiles.doodad(KIT + id) == null) return false;
        for (String id : List.of("mat", "coping", "water")) if (tiles.block(KIT + id) == null) return false;
        return tiles.block(TRACK) != null && tiles.doodad(LOUNGER) != null;
    }

    @Override public void fit(RoomFloor floor) {
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        // The track: two lanes round the bulkheads.
        reserve(floor, 0, 0, w, 2); reserve(floor, 0, h - 2, w, 2);
        reserve(floor, 0, 0, 2, h); reserve(floor, w - 2, 0, 2, h);
        surface(floor, 0, 0, w, 2, TRACK); surface(floor, 0, h - 2, w, 2, TRACK);
        surface(floor, 0, 2, 2, h - 4, TRACK); surface(floor, w - 2, 2, 2, h - 4, TRACK);
        for (Doorway door : floor.localDoors()) floor.reserveLane(door.x() - 1, door.y() - 1, 3, 3);
        int east = w - 2, south = h - 2;
        floor.runningTrack(new int[][]{{1, 1}, {7, 1}, {14, 1}, {20, 1}, {east, 1}, {east, h / 2},
                {east, south}, {20, south}, {14, south}, {7, south}, {1, south}, {1, h / 2}});
        // The aisle between the floor and the pool deck, the cardio stands and the rack stands.
        reserve(floor, 13, 2, 1, h - 4);
        reserve(floor, 2, 7, 11, 1);
        reserve(floor, 14, 10, 12, 1);

        // Cardio: four treadmills and four rowers, worked from the aisle below their feet.
        for (int x = 3; x <= 6; x++) station(floor, KIT + "treadmill", x, 8, x, 7, x, 8);
        for (int x = 8; x <= 11; x++) station(floor, KIT + "rower", x, 8, x, 7, x, 8);
        // Strength: two presses either side of a mat.
        station(floor, KIT + "bench-press", 3, 4, 4, 3, 4, 4);
        station(floor, KIT + "bench-press", 9, 4, 10, 3, 10, 4);
        reserve(floor, 6, 3, 3, 3); surface(floor, 6, 3, 3, 3, KIT + "mat");
        task(floor, 8, 3, Affordance.EXERCISE, 7, 3); task(floor, 6, 5, Affordance.EXERCISE, 7, 5);

        // The pool: a ten by four basin inside a coping deck; water is closed, see-through terrain.
        surface(floor, 14, 4, 12, 6, KIT + "coping");
        reserve(floor, 14, 4, 12, 1); reserve(floor, 14, 9, 12, 1);
        reserve(floor, 14, 4, 1, 6); reserve(floor, 25, 4, 1, 6);
        int[] basin = floor.toLocalRect(15, 5, 10, 4);
        floor.closeOff(basin[0], basin[1], basin[2], basin[3]);
        floor.markGround(basin[0], basin[1], basin[2], basin[3], GroundKind.WATER);
        surface(floor, 15, 5, 10, 4, KIT + "water");
        // Loungers along the pool deck, and the weights racks along its far side.
        for (int x : new int[]{14, 18, 22})
            if (floor.placeAssembly(x, 2, LOUNGER, List.of())) task(floor, x + 1, 3, Affordance.UNWIND, x + 1, 2);
        for (int x : new int[]{15, 20})
            if (floor.placeAssembly(x, 11, KIT + "weights", List.of())) {
                task(floor, x, 10, Affordance.EXERCISE, x, 11);
                task(floor, x + 2, 10, Affordance.EXERCISE, x + 2, 11);
            }
    }

    private void station(RoomFloor floor, String id, int x, int y, int sx, int sy, int fx, int fy) {
        if (floor.placeAssembly(x, y, id, List.of())) task(floor, sx, sy, Affordance.EXERCISE, fx, fy);
    }

    private void task(RoomFloor floor, int sx, int sy, Affordance work, int fx, int fy) {
        int[] s = floor.toLocal(sx, sy), f = floor.toLocal(fx, fy);
        floor.fixtureTask(s[0], s[1], work, f[0], f[1]);
    }

    private void reserve(RoomFloor floor, int x, int y, int w, int h) {
        int[] r = floor.toLocalRect(x, y, w, h); floor.reserveLane(r[0], r[1], r[2], r[3]);
    }

    private void surface(RoomFloor floor, int x, int y, int w, int h, String id) {
        int[] r = floor.toLocalRect(x, y, w, h); floor.markFloorSurface(r[0], r[1], r[2], r[3], id);
    }
}
