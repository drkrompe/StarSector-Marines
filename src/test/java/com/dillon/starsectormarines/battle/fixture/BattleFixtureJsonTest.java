package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BattleFixtureJsonTest {

    @Test
    void roundTripsEveryCivilianRescueFactoryInput() throws Exception {
        CivilianRescueBattleFixture fixture = new CivilianRescueBattleFixture(
                -7_113_009_551L,
                List.of(
                        new ShuttleAssignment(ShuttleType.KITE, 2),
                        new ShuttleAssignment(ShuttleType.VALKYRIE, 4)),
                true,
                RiskLevel.HIGH,
                87,
                new TargetProfile(6, 8, 5, 2, "hegemony",
                        EnumSet.of(EconomicFunction.HABITATION,
                                EconomicFunction.HEAVY_INDUSTRY,
                                EconomicFunction.SPACEPORT)),
                true);

        BattleFixture decoded = BattleFixtureJson.fromJson(
                BattleFixtureJson.toJson(fixture));

        assertEquals(fixture, decoded);
    }

    @Test
    void rejectsUnknownSchemaKindAndEnum() throws Exception {
        JSONObject valid = BattleFixtureJson.toJson(canonicalFixture());

        JSONObject badVersion = new JSONObject(valid.toString());
        badVersion.put("schemaVersion", 99);
        assertThrows(IllegalArgumentException.class,
                () -> BattleFixtureJson.fromJson(badVersion));

        JSONObject badKind = new JSONObject(valid.toString());
        badKind.put("kind", "CONQUEST");
        assertThrows(IllegalArgumentException.class,
                () -> BattleFixtureJson.fromJson(badKind));

        JSONObject badRisk = new JSONObject(valid.toString());
        badRisk.put("risk", "IMPOSSIBLE");
        assertThrows(IllegalArgumentException.class,
                () -> BattleFixtureJson.fromJson(badRisk));
    }

    @Test
    void embeddedProfileFixtureMatchesDirectProductionFactory() throws Exception {
        CivilianRescueBattleFixture fixture = canonicalFixture();
        JSONObject profileDump = new JSONObject();
        profileDump.put("schemaVersion", 6);
        profileDump.put("battleFixture", BattleFixtureJson.toJson(fixture));

        try (BattleSimulation replay = BattleFixtureJson.fromJson(profileDump).build();
             BattleSimulation direct = BattleSetup.createCivilianRescue(
                     fixture.seed(), fixture.manifest(), fixture.enemyHasHeavyArmor(),
                     fixture.risk(), fixture.swarmCount(), fixture.targetProfile(),
                     fixture.stressTest())) {
            assertEquals(initialFingerprint(direct), initialFingerprint(replay));
            for (int tick = 0; tick < 10; tick++) {
                replay.advance(BattleSimulation.TICK_DT);
            }
            assertFalse(replay.isComplete());
        }
    }

    @Test
    void checkedInFixtureUsesTheProductionFactoryHeadlessly() throws Exception {
        try (BattleSimulation sim = BattleFixtureTestSupport.loadDefaultFixture().build()) {
            assertEquals(20, defenders(sim));
            assertEquals(8, sim.getCivilianEvacuationTracker().registeredCount());
            assertEquals(20, sim.swarmTargetPopulation());
        }
    }

    private static CivilianRescueBattleFixture canonicalFixture() {
        return new CivilianRescueBattleFixture(
                5_005L,
                List.of(new ShuttleAssignment(ShuttleType.VALKYRIE, 1)),
                false,
                RiskLevel.LOW,
                20,
                new TargetProfile(5, 7, 2, 1, "independent",
                        EnumSet.of(EconomicFunction.HABITATION,
                                EconomicFunction.SPACEPORT)),
                false);
    }

    private static int defenders(BattleSimulation sim) {
        int count = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            if (sim.identity().faction(sim.liveUnitAt(i)) == Faction.DEFENDER) count++;
        }
        return count;
    }

    private static String initialFingerprint(BattleSimulation sim) {
        StringBuilder fingerprint = new StringBuilder()
                .append(sim.getGrid().getWidth()).append('x')
                .append(sim.getGrid().getHeight()).append('|')
                .append(sim.liveUnitCount()).append('|');
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        byte[] edges = grid.getEdgePassabilityArray();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                int cell = grid.index(x, y);
                fingerprint.append(topology.getGroundKind(x, y).ordinal()).append(':')
                        .append(topology.getBuildingId(x, y)).append(':')
                        .append(topology.getWallDirMask(x, y)).append(':')
                        .append(edges[cell]).append(':');
                for (CellTopology.Tag tag : CellTopology.Tag.values()) {
                    if (topology.hasTag(x, y, tag)) {
                        fingerprint.append('t').append(tag.ordinal());
                    }
                }
                fingerprint.append(';');
            }
        }
        for (Doodad doodad : sim.getDoodads()) {
            fingerprint.append('d').append(doodad.cellX).append(',')
                    .append(doodad.cellY).append(':')
                    .append(doodad.sheetPath).append(':')
                    .append(doodad.tile.col).append(',').append(doodad.tile.row)
                    .append(':').append(doodad.cover).append(';');
        }
        for (Objective objective : sim.getObjectives()) {
            fingerprint.append('o').append(objective.getClass().getName()).append(':')
                    .append(objective.owningFaction()).append(':')
                    .append(objective.displayName()).append(';');
        }
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long entity = sim.liveUnitAt(i);
            fingerprint.append(entity).append(':')
                    .append(sim.identity().faction(entity)).append(':')
                    .append(sim.identity().type(entity)).append(':')
                    .append(sim.world().cellX(entity)).append(',')
                    .append(sim.world().cellY(entity)).append(':')
                    .append(Float.floatToIntBits(sim.world().hp(entity))).append(';');
        }
        for (long aircraft : sim.getAirEntityIds()) {
            fingerprint.append('a').append(aircraft).append(':')
                    .append(sim.world().airFaction(aircraft)).append(':')
                    .append(sim.world().mission(aircraft).totalCycles).append(';');
        }
        return fingerprint.toString();
    }
}
