package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sick bay is a ward plus the things that make it clinical, not a rank of
 * beds with a different label.
 *
 * <p>{@code AisleFitting} ranked one bed and one chest either side of an aisle,
 * which furnished the room and published its beds and read as a dormitory
 * regardless — every bed identical, nothing that was not a bed, and no gap a
 * stretcher could actually use. {@link SickBayFitting} is asked directly for the
 * things that distinction rests on: a treatment station and a dispensary
 * distinct from any bed, a genuine reserved gap beside every bed rather than
 * beds packed edge to edge, and a room that still counts as furnished before its
 * medical art exists.
 *
 * <p>Asked of one room on a synthetic floor, the way every fitting in this
 * package is tested — the subject is the fitting and the floor it fills, not a
 * generated ship.
 */
class SickBayIsAWardAndAClinicTest {

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }

    private static final int ORIGIN = 4;
    private static final int WIDTH = 14;
    private static final int HEIGHT = 10;

    /** A ward that is one bed repeated is exactly the defect this fitting exists to fix. */
    @Test
    void theSickBayIsMoreThanARankOfBeds() {
        Fitted fitted = fit(RoomPose.CANONICAL, RoomFit.STANDARD);

        assertTrue(fitted.kinds() >= 6,
                "the sick bay came out as one bed repeated, not as a ward and a clinic");
        assertTrue(fitted.survives(),
                "the sick bay's own fill severed its circulation, so it ships as bare deck");
    }

    /**
     * The three jobs a sick bay owes: beds and a treatment station kept ready,
     * a dispensary that is secured rather than open shelving, and a sink.
     */
    @Test
    void theSickBayPublishesTreatmentStorageAndWashing() {
        Fitted fitted = fit(RoomPose.CANONICAL, RoomFit.STANDARD);

        assertTrue(fitted.count(Affordance.TREAT) > 1,
                "nothing here is treatment work beyond the beds themselves");
        assertTrue(fitted.count(Affordance.STOW) > 0, "the dispensary has no counter");
        assertTrue(fitted.count(Affordance.WASH) > 0, "a clinical space with nowhere to scrub");
    }

    /**
     * The gap beside a ward bed is reserved, not merely left over. A room that
     * furnished every column would sleep more people and treat nobody, because
     * nothing could be got alongside a bed to work on whoever is in it.
     */
    @Test
    void aWardBedKeepsAReservedGapBesideIt() {
        Fitted fitted = fit(RoomPose.CANONICAL, RoomFit.STANDARD);
        int gapColumn = ORIGIN + 5;

        for (int row : new int[]{ 4, 5, 6, 11, 12, 13 }) {
            assertFalse(occupied(fitted.doodads(), gapColumn, row),
                    "the stretcher gap at column 5 was furnished over");
        }
    }

    /** A bed's own footprint survives a quarter turn, which swaps which sprite variant is correct. */
    @Test
    void aQuarterTurnedSickBayKeepsItsBedCount() {
        Fitted upright = fit(RoomPose.CANONICAL, RoomFit.STANDARD);
        Fitted turned = fit(new RoomPose(1, false), RoomFit.STANDARD);

        assertTrue(turned.count(Affordance.TREAT) > 0, "the turned sick bay lost its beds");
        assertTrue(turned.count(Affordance.TREAT) >= upright.count(Affordance.TREAT) - 1,
                "a quarter turn should not cost the room most of its beds");
        assertTrue(turned.survives(), "the turned sick bay's fill severed its circulation");
    }

    /**
     * A poorly fitted sick bay is sloppier, never one where a stretcher cannot
     * get to a bed at all — the gap is never let collapse to zero.
     */
    @Test
    void aBetterFitBerthsMoreBeds() {
        Fitted loose = fit(RoomPose.CANONICAL, RoomFit.MAKESHIFT);
        Fitted tight = fit(RoomPose.CANONICAL, RoomFit.OPTIMISED);

        assertTrue(tight.count(Affordance.TREAT) >= loose.count(Affordance.TREAT),
                "an optimised fit berths no more beds than a makeshift one");
    }

    /** A footprint too small for the clinic is left bare, honestly, rather than furnished halfway. */
    @Test
    void aRoomTooSmallForTheClinicIsLeftBare() {
        Fitted fitted = fit(RoomPose.CANONICAL, RoomFit.STANDARD, 4, 4);

        assertTrue(fitted.doodads().isEmpty(), "a room too small for the clinic was furnished anyway");
    }

    private static boolean occupied(List<Doodad> doodads, int x, int y) {
        return doodads.stream().anyMatch(d -> d.occupiesCell(x, y));
    }

    private record Fitted(List<Doodad> doodads, List<FixtureTask> tasks, boolean survives) {

        int count(Affordance affordance) {
            return (int) tasks.stream().filter(task -> task.affordance() == affordance).count();
        }

        /** Distinct pieces of art standing in the room, counted off the sprite it draws. */
        int kinds() {
            Set<String> frames = new HashSet<>();
            for (Doodad doodad : doodads) frames.add(doodad.tile.col + "," + doodad.tile.row);
            return frames.size();
        }
    }

    private static Fitted fit(RoomPose pose, RoomFit fit) {
        return fit(pose, fit, WIDTH, HEIGHT);
    }

    private static Fitted fit(RoomPose pose, RoomFit fit, int width, int height) {
        int localWidth = pose.posedWidth(width, height);
        int localHeight = pose.posedHeight(width, height);
        int mapWidth = ORIGIN + localWidth + 6;
        int mapHeight = ORIGIN + localHeight + 6;
        NavigationGrid grid = new NavigationGrid(mapWidth, mapHeight);
        CellTopology topology = new CellTopology(mapWidth, mapHeight);
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) grid.setWalkableFloor(x, y);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L),
                mapWidth, mapHeight, 7L);
        Room room = new Room(RoomShape.rectangle(localWidth, localHeight), ORIGIN, ORIGIN,
                pose, RoomPurpose.PATIENT_WARD,
                List.of(new Doorway(ORIGIN, ORIGIN + localHeight / 2)));
        RoomFloor floor = new RoomFloor(ctx, room, fit);
        new SickBayFitting().fit(floor);
        floor.dropUnreachableWork();
        return new Fitted(List.copyOf(ctx.doodads), List.copyOf(ctx.fixtureTasks),
                floor.circulationSurvives());
    }
}
