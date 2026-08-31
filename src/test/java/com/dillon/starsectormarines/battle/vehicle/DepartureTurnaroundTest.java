package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The captured departure stall. A truck delivered through the plain distance
 * gate keeps whatever heading it arrived on — only the docking maneuver lands
 * it on {@code lzDepartureFacingDeg} — so a convoy that drove in from the east
 * is parked facing west with an exit due east. Reversing a bicycle's direction
 * costs lateral room it does not have in a road: the shipped APC turns inside
 * 3.96 cells and needs about 3.15 cells of swing to come round, and the road on
 * seed 40595 gave it two. Six separate APCs stopped on the same two cells there,
 * each for the rest of the battle.
 *
 * <p>So there are two things to hold: a road wide enough must be turned around
 * in, and a road too narrow must never have been dispatched into.
 */
class DepartureTurnaroundTest {

    private static final float WEST = 90f;    // nose-west in the compass convention
    private static final float EAST = -90f;

    /** An east-west road {@code 2 * halfHeight + 1} cells tall, centred on y=75. */
    private static NavigationGrid road(int halfHeight) {
        NavigationGrid grid = new NavigationGrid(280, 168);
        for (int y = 75 - halfHeight; y <= 75 + halfHeight; y++) {
            for (int x = 200; x <= 279; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    // ---------------------------------------------------------------------
    // The dispatch-time gate: do not commit to an exit you cannot turn onto
    // ---------------------------------------------------------------------

    @Test
    void theCapturedRoadCannotBeTurnedAroundIn() {
        // Five cells tall, which is what seed 40595 actually offered.
        assertFalse(VehicleController.canReverseDirectionAt(road(2), VehicleType.HEAVY_APC,
                        220.5f, 75.5f, WEST, EAST),
                "a five-cell road cannot hold this chassis's turning circle, and"
                        + " dispatch must not promise a convoy it can leave through one");
    }

    @Test
    void aRoadWithRoomCanBeTurnedAroundIn() {
        assertTrue(VehicleController.canReverseDirectionAt(road(6), VehicleType.HEAVY_APC,
                        220.5f, 75.5f, WEST, EAST),
                "a thirteen-cell road has room for the maneuver and must not be rejected");
    }

    @Test
    void anExitThatNeedsNoReversalIsAlwaysFine() {
        // Arriving west and leaving west asks for no turn at all, so even the
        // narrowest road must pass. A gate that rejected this would starve
        // every ordinary drive-through delivery.
        assertTrue(VehicleController.canReverseDirectionAt(road(2), VehicleType.HEAVY_APC,
                        220.5f, 75.5f, WEST, WEST),
                "a straight-through exit needs no room and must not be gated");
    }

    // ---------------------------------------------------------------------
    // The runtime recovery: turn around where there is room to
    // ---------------------------------------------------------------------

    private record Rig(ConvoyService convoy, VehicleControlSystem controls,
                       long id, GroundBody body) {}

    /** A delivered APC at (220.5, 75.5) facing {@code facingDeg}, exit due east. */
    private static Rig deliveredApc(NavigationGrid grid, float facingDeg) {
        NavigationService navigation = new NavigationService(grid, new CellTopology(280, 168));
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(280, 168), null);
        ConvoyService convoy = roster.convoy();
        VehicleMission mission = new VehicleMission(
                new float[]{240.5f, 230.5f, 220.5f}, new float[]{75.5f, 75.5f, 75.5f},
                new float[]{220.5f, 250.5f, 277.5f}, new float[]{75.5f, 75.5f, 75.5f},
                0f, 4);
        long id = convoy.spawn(VehicleType.HEAVY_APC, Faction.DEFENDER, mission);
        GroundBody body = convoy.body(id);
        body.teleport(220.5f, 75.5f, facingDeg);
        body.speed = 0f;
        return new Rig(convoy, new VehicleControlSystem(convoy, navigation), id, body);
    }

    private static void driveOutbound(Rig rig, float seconds) {
        int ticks = (int) (seconds * 30f);
        for (int i = 0; i < ticks; i++) rig.controls().tick(rig.id(), 1f / 30f, false);
    }

    @Test
    void aTruckParkedFacingBackDownItsApproachTurnsAroundAndLeaves() {
        // Nine cells: too tight for the wide sweep, wide enough for the short
        // one. Before the fix this truck sat at 220.5 with no forward plan and
        // no recovery rung that could produce one, for the rest of the battle.
        Rig rig = deliveredApc(road(4), WEST);

        driveOutbound(rig, 25f);

        assertTrue(rig.body().x > 235f,
                "a misaligned departure must turn itself round and leave, not hold"
                        + " (ended at x=" + rig.body().x + ", started at 220.5)");
        assertEquals(1, rig.convoy().control(rig.id()).turnaroundsUsed,
                "and it should take exactly one maneuver to do it");
    }

    @Test
    void anAlignedDepartureNeverEngagesAManeuver() {
        // Already nose-east: ordinary forward tracking is the right tool, and a
        // maneuver here would be bought for nothing.
        Rig rig = deliveredApc(road(4), EAST);

        driveOutbound(rig, 5f);

        assertEquals(0, rig.convoy().control(rig.id()).turnaroundsUsed,
                "an aligned departure must not spend a turnaround");
        assertTrue(rig.body().x > 230f, "an aligned truck just drives away");
    }

    @Test
    void aHopelessPoseStopsTryingInsteadOfLooping() {
        // Boxed in: no maneuver can rescue this, and the vehicle must not
        // re-engage every time forward planning fails.
        NavigationGrid grid = new NavigationGrid(280, 168);
        for (int y = 74; y <= 76; y++) {
            for (int x = 219; x <= 221; x++) grid.setWalkableFloor(x, y);
        }
        Rig rig = deliveredApc(grid, WEST);

        driveOutbound(rig, 25f);

        assertTrue(rig.convoy().control(rig.id()).turnaroundsUsed
                        <= VehicleController.MAX_DEPARTURE_TURNAROUNDS,
                "a hopeless pose must not loop the maneuver (used="
                        + rig.convoy().control(rig.id()).turnaroundsUsed + ")");
        assertTrue(rig.body().x < 224f,
                "and must not escape through a wall (x=" + rig.body().x + ")");
    }
}
