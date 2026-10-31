package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.ambient.RoomSite;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.StructureWatch;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A field that rebuilds builds a replacement on the stand of an aircraft it lost, and the building is its crew's work
 * (`air-nouns.md`, Airbase): the same turnaround, at the same servicing points, done only while somebody stands at
 * them.
 */
class AFieldsCrewRebuildsWhatItLosesTest {

    private static final int W = 40;
    private static final int H = 30;
    private static final float DT = 1f / 30f;

    private static BattleSimulation openSim(boolean hangar) {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        CellTopology topology = new CellTopology(W, H);
        if (hangar) {
            for (int x = 4; x < W - 4; x++) {
                for (int y = 4; y < H - 4; y++) topology.setRoomPurpose(x, y, RoomPurpose.HANGAR);
            }
        }
        BattleSimulation sim = new BattleSimulation(grid, topology);
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    private static AirfieldService.Berth stand(AirfieldService service, AirfieldService.Field field, int x) {
        return service.addBerth(field, LandingPad.allied(x, 15, LandingPad.Approach.SOUTH),
                ShuttleType.AEROSHUTTLE, 0f);
    }

    /** Only a field that rebuilds, only a written-off stand, and never one with the hulk of what burned there on it. */
    @Test
    void aRebuildStartsOnlyOnAClearStandOfAFieldThatRebuilds() {
        AirfieldService service = new AirfieldService();
        AirfieldService.Field rebuilds = service.addField(Faction.ALLY, null, true);
        AirfieldService.Field keeps = service.addField(Faction.ALLY, null, false);
        AirfieldService.Berth lost = stand(service, rebuilds, 10);
        AirfieldService.Berth burned = stand(service, rebuilds, 20);
        AirfieldService.Berth garrison = stand(service, keeps, 30);

        assertFalse(service.beginRebuild(lost), "a parked aircraft is not rebuilt");
        service.launch(lost);
        service.destroyed(lost);
        service.burnedOnPad(burned);
        service.launch(garrison);
        service.destroyed(garrison);

        assertTrue(service.beginRebuild(lost));
        assertEquals(AirfieldService.BerthState.REFITTING, lost.state);
        assertTrue(lost.refitWork >= AirfieldService.REBUILD_HAND_SECONDS, "a rebuild is at least the assembly");
        assertFalse(lost.airworthy(), "a replacement flies only once it is built");
        assertFalse(service.beginRebuild(burned), "a hulk takes no replacement");
        assertFalse(service.beginRebuild(garrison), "a field that does not rebuild keeps its loss");
        assertEquals(1, service.rebuildsBegun());
    }

    /**
     * A crew on the apron works a replacement up; the same field with nobody on it leaves the frame where it is.
     *
     * <p>The control is half the test: the field does not build anything by the clock.
     */
    @Test
    void aReplacementIsBuiltByTheCrewAndNotWithoutIt() {
        assertTrue(workDone(true) > 0f, "a manned apron did no work on the replacement");
        assertEquals(0f, workDone(false), 1e-4f, "an apron with nobody on it built a replacement by itself");
    }

    /** Hand-seconds of rebuild work done on one lost aircraft's stand over twenty seconds. */
    private static float workDone(boolean manned) {
        BattleSimulation sim = openSim(true);
        AirfieldService service = sim.getAirfieldService();
        AirfieldService.Field field = service.addField(Faction.ALLY, null, true);
        AirfieldService.Berth berth = stand(service, field, 20);
        List<FixtureTask> apron = AirfieldWork.onTheApron(service, sim.getGrid());
        service.installApronWork(apron);
        if (manned) {
            StructureWatch.man(sim, Faction.ALLY, RoomSite.findAll(sim.getTopology(), W, H), apron,
                    AirfieldWork.occupied(service), EnumSet.of(RoomPurpose.HANGAR), 2, null);
        }
        sim.advance(BattleSimulation.TICK_DT);
        service.launch(berth);
        sim.advance(BattleSimulation.TICK_DT);
        service.destroyed(berth);
        sim.advance(BattleSimulation.TICK_DT);
        assertEquals(AirfieldService.BerthState.REFITTING, berth.state, "the loss was not put on the rebuild");
        float start = berth.refitWork;
        for (int tick = 0; tick < 30 * 20; tick++) sim.advance(BattleSimulation.TICK_DT);
        assertNotEquals(0L, berth.airframeId, "the replacement is not standing on its stand");
        assertEquals(AirfieldService.BerthState.REFITTING, berth.state, "twenty seconds built a whole aircraft");
        return start - berth.refitWork;
    }

    /** Once the work is done the replacement is airworthy at its whole hull. */
    @Test
    void aBuiltReplacementFliesAgain() {
        BattleSimulation sim = openSim(false);
        AirfieldService service = sim.getAirfieldService();
        AirfieldService.Berth berth = stand(service, service.addField(Faction.ALLY, null, true), 20);
        AirfieldSystem system = new AirfieldSystem();
        system.tick(DT, sim, service);
        service.launch(berth);
        system.tick(DT, sim, service);
        service.destroyed(berth);
        system.tick(DT, sim, service);
        system.tick(DT, sim, service);
        assertNotEquals(0L, berth.airframeId, "the frame stands on its stand while it is built");
        assertEquals(Faction.ALLY, sim.identity().faction(berth.airframeId));

        // What the crew do, done to it.
        sim.world().setHp(berth.airframeId, berth.refitTarget);
        berth.refitWork = 0f;
        system.tick(DT, sim, service);

        assertTrue(berth.airworthy());
        assertEquals(ShuttleType.AEROSHUTTLE.maxHp, berth.hullHp, 0.5f);
    }
}
