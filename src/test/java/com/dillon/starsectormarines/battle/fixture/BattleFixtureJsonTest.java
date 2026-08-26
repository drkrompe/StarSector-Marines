package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.ReconPing;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.OperationTier;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BattleFixtureJsonTest {

    @Test
    void readsLegacyV2LaunchWithFullCapacityIndependentConstruction() throws Exception {
        BattleLaunchFixture launch =
                BattleFixtureTestSupport.loadLegacyConquestLaunchFixture();
        ConquestBattleFixture conquest =
                (ConquestBattleFixture) launch.construction();

        assertEquals(12, conquest.manifest().get(0).seatsPerSortie);
        assertEquals(com.dillon.starsectormarines.ops.MarineArrivalPolicy.INDEPENDENT_FULL_LOAD,
                conquest.arrivalPlan().policy());
    }

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
    void roundTripsEveryConquestFactoryInputInAuthoredOrder() throws Exception {
        ConquestBattleFixture fixture = new ConquestBattleFixture(
                4_096L,
                List.of(
                        new ShuttleAssignment(ShuttleType.KITE, 3),
                        new ShuttleAssignment(ShuttleType.VALKYRIE, 2)),
                true,
                OperationTier.REINFORCED,
                RiskLevel.HIGH,
                new TargetProfile(7, 4, 6, 3, "hegemony",
                        EnumSet.of(EconomicFunction.HEAVY_INDUSTRY,
                                EconomicFunction.SPACEPORT)),
                List.of(new FighterWingCommitment(FighterProfile.BROADSWORD,
                        Faction.MARINE, 2, 12f, 30f)),
                List.of(
                        new FighterWingCommitment(FighterProfile.DAGGER,
                                Faction.DEFENDER, 1, 20f, 45f),
                        new FighterWingCommitment(FighterProfile.TALON,
                                Faction.DEFENDER, 3, 10f, 15f)));

        BattleFixture decoded = BattleFixtureJson.fromJson(
                BattleFixtureJson.toJson(fixture));

        assertEquals(fixture, decoded);
    }

    @Test
    void roundTripsV3LaunchOverlayWithV2Construction() throws Exception {
        ConquestBattleFixture construction = new ConquestBattleFixture(
                8_192L,
                List.of(new ShuttleAssignment(ShuttleType.VALKYRIE, 1)),
                false,
                OperationTier.REINFORCED,
                RiskLevel.MEDIUM,
                TargetProfile.NEUTRAL,
                List.of(),
                List.of(new FighterWingCommitment(FighterProfile.TALON,
                        Faction.DEFENDER, 1, 9f, 20f)));
        MarineSeatCommitment seat = new MarineSeatCommitment(
                "marine-17", WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID).id,
                EquipmentGrade.MILSPEC,
                new SoldierProfile(SoldierAptitude.GIFTED, 321),
                null, LayeredArmorFamily.CHARCOAL,
                60f, 5f, 0.95f, 0.9f,
                "squad-2", "Second Squad", true, 1, 0);
        BattleLaunchOverlay launch = new BattleLaunchOverlay(
                0,
                List.of(seat),
                List.of(new FighterWingCommitment(FighterProfile.BROADSWORD,
                        Faction.MARINE, 2, 4f, 15f)),
                List.of(new FighterWingCommitment(FighterProfile.DAGGER,
                        Faction.DEFENDER, 1, 7f, 30f)),
                List.of(
                        new CommandPowerCommitment(ReconPing.ID, List.of()),
                        new CommandPowerCommitment(MechSupport.ID, List.of(
                                new MechDeploymentSpec(MechVariant.SIROCCO,
                                        MechRole.LR_SUPPORT,
                                        MissileReplenisherComponent.ACCELERATED_FEED)))),
                37);
        BattleLaunchFixture fixture = new BattleLaunchFixture(construction, launch);

        JSONObject encoded = BattleFixtureJson.toJson(fixture);
        BattleFixture decoded = BattleFixtureJson.fromJson(encoded);

        assertEquals(3, encoded.getInt("schemaVersion"));
        assertEquals(2, encoded.getJSONObject("construction")
                .getInt("schemaVersion"));
        assertEquals(fixture, decoded);
        assertEquals(encoded.toString(), BattleFixtureJson.toJson(decoded).toString());
    }

    @Test
    void rejectsUnknownSchemaKindAndEnum() throws Exception {
        JSONObject valid = BattleFixtureJson.toJson(canonicalFixture());

        JSONObject badVersion = new JSONObject(valid.toString());
        badVersion.put("schemaVersion", 99);
        assertThrows(IllegalArgumentException.class,
                () -> BattleFixtureJson.fromJson(badVersion));

        JSONObject badKind = new JSONObject(valid.toString());
        badKind.put("kind", "UNKNOWN_MISSION");
        assertThrows(IllegalArgumentException.class,
                () -> BattleFixtureJson.fromJson(badKind));

        JSONObject badRisk = new JSONObject(valid.toString());
        badRisk.put("risk", "IMPOSSIBLE");
        assertThrows(IllegalArgumentException.class,
                () -> BattleFixtureJson.fromJson(badRisk));

        BattleLaunchFixture launch = new BattleLaunchFixture(
                canonicalFixture(), new BattleLaunchOverlay(
                0, List.of(), List.of(), List.of(), List.of(), 0));
        JSONObject badLaunchKind = BattleFixtureJson.toJson(launch);
        badLaunchKind.put("kind", ConquestBattleFixture.KIND);
        assertThrows(IllegalArgumentException.class,
                () -> BattleFixtureJson.fromJson(badLaunchKind));
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
