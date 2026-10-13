package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Building;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Cluster;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Yard;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;

/**
 * The battery fort as an authored compound layout, in one cardinally rotatable local frame: an outer wall with two
 * gates, a fire-control bunker, a magazine and a power building each standing off a main street, and the battery
 * platform in the rear court with a nest in each corner. See {@code forts.md}.
 *
 * <p>This is the same machinery the maintenance outpost is built from ({@link CompoundLayout}); what is different is
 * the host. The outpost is a whole map of its own, so it can claim ground outright. A fort is stamped into a city that
 * already exists, so {@code BatteryFortStage} has to find ground it may have (the city is prior) and then place this
 * layout on it at one of four turns. Everything here, buildings, doors, gates, the platform and the nests, rotates as
 * one.
 *
 * <p>The footprint's outermost ring of cells is the wall. Buildings stand clear of it by a yard so that no building's
 * own wall ring is also a fort wall, which keeps a breach in one from opening the other.
 */
public final class BatteryFort {

    /** Local width and height of the footprint, wall included. */
    public static final int WIDTH = 48, HEIGHT = 36;

    /** The cells of the wall ring that are left open as gates: the main gate on the west face, a postern on the north. */
    public static final List<int[]> GATE_CELLS = List.of(
            new int[]{0, 16}, new int[]{0, 17}, new int[]{0, 18},
            new int[]{22, 0}, new int[]{23, 0}, new int[]{24, 0});

    /** The open cell just outside the middle of each gate, which is where the way out to the road network begins. */
    public static final List<int[]> GATE_MOUTHS = List.of(new int[]{-1, 17}, new int[]{23, -1});

    /** The middle of the battery platform's three-by-three footprint. */
    public static final int[] PLATFORM = {36, 27};

    /** Wall nests, in the four inner corners. */
    public static final List<int[]> NESTS = List.of(
            new int[]{1, 1}, new int[]{46, 1}, new int[]{1, 34}, new int[]{46, 34});

    private static final int[] BUNKER_FLOOR = {4, 4, 8, 6};
    private static final int[] MAGAZINE_FLOOR = {34, 4, 8, 6};
    private static final int[] POWER_FLOOR = {4, 26, 8, 6};

    private BatteryFort() {}

    /** The layout: three buildings and the yards between them. Roads are the host's to cut. */
    public static CompoundLayout layout() {
        List<Building> core = new ArrayList<>();
        core.add(building(RoomPurpose.CONTROL_ROOM, BUNKER_FLOOR, new Doorway(3, 6)));
        core.add(building(RoomPurpose.ARMORY, MAGAZINE_FLOOR, new Doorway(3, 6)));
        core.add(building(RoomPurpose.ENGINE_ROOM, POWER_FLOOR, new Doorway(3, -1)));
        return new CompoundLayout(RoomShape.rectangle(WIDTH, HEIGHT), List.of(
                new Cluster("Fort buildings", core)),
                List.of(new Yard("Main street", 1, 15, WIDTH - 2, 5),
                        new Yard("Postern lane", 22, 1, 3, 14),
                        new Yard("Gun court", 32, 20, 9, 10)),
                List.of());
    }

    /** The middle of the three-by-three core of {@code aspect}, in the local frame. */
    public static int[] coreCentre(FortAspect aspect) {
        int[] floor = switch (aspect) {
            case FIRE_CONTROL -> BUNKER_FLOOR;
            case MAGAZINE -> MAGAZINE_FLOOR;
            case POWER -> POWER_FLOOR;
        };
        return new int[]{floor[0] + 3, floor[1] + 2};
    }

    /** Whether the local cell is on the wall ring and not a gate. */
    public static boolean isWall(int x, int y) {
        boolean ring = x == 0 || y == 0 || x == WIDTH - 1 || y == HEIGHT - 1;
        if (!ring) return false;
        for (int[] gate : GATE_CELLS) if (gate[0] == x && gate[1] == y) return false;
        return true;
    }

    private static Building building(RoomPurpose purpose, int[] floor, Doorway door) {
        return new Building(purpose, RoomShape.rectangle(floor[2], floor[3]), floor[0], floor[1], List.of(door));
    }
}
