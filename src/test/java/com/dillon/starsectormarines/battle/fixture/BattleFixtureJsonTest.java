package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.FittedBoat;
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
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.ReconPing;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.ShuttleArrivalPlan;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Standoff;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.FieldPresencePolicy;
import com.dillon.starsectormarines.ops.ConquestArrivalConfig;
import com.dillon.starsectormarines.ops.MarineArrivalPolicy;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BattleFixtureJsonTest {

    @Test
    void readsLegacyV2LaunchWithFullCapacityIndependentConstruction() throws Exception {
        BattleLaunchFixture launch =
                BattleFixtureTestSupport.loadLegacyConquestLaunchFixture();
        ConquestBattleFixture conquest =
                (ConquestBattleFixture) launch.construction();

        assertEquals(12, conquest.manifest().get(0).seatsPerSortie);
        assertEquals(MarineArrivalPolicy.INDEPENDENT_FULL_LOAD,
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
                                EconomicFunction.SPACEPORT),
                        SurfacePalette.ROCK, SettlementLink.ROAD),
                true);

        BattleFixture decoded = BattleFixtureJson.fromJson(
                BattleFixtureJson.toJson(fixture));

        assertEquals(fixture, decoded);
    }

    /**
     * A replay has to fly the same boats. The manifest is compared by equality,
     * so a lost fitting id shows up here as an unequal fixture rather than as a
     * quietly softer Aeroshuttle in a rerun.
     */
    @Test
    void roundTripsTheFittingsOnTheCompanysOwnBoats() throws Exception {
        FittedBoat armoured = new FittedBoat(ShuttleType.AEROSHUTTLE,
                BoatFitting.ARMOURED_PLATING, BoatFitting.TUNED_DRIVE, "boat_04");
        CivilianRescueBattleFixture fixture = new CivilianRescueBattleFixture(
                17L,
                List.of(
                        new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1),
                        new ShuttleAssignment(armoured, 2, 6),
                        new ShuttleAssignment(
                                FittedBoat.standard(ShuttleType.AEROSHUTTLE), 1, 6)),
                false,
                RiskLevel.LOW,
                12,
                new TargetProfile(3, 3, 3, 1, "independent",
                        EnumSet.of(EconomicFunction.HABITATION),
                        SurfacePalette.ROCK, SettlementLink.ROAD),
                false);

        BattleFixture decoded = BattleFixtureJson.fromJson(
                BattleFixtureJson.toJson(fixture));

        assertEquals(fixture, decoded);
        List<ShuttleAssignment> manifest =
                ((CivilianRescueBattleFixture) decoded).manifest();
        assertSame(ShuttleType.AEROSHUTTLE, manifest.get(0).airframe,
                "an employer's craft carries no fitting ids and reads back plain");
        assertEquals(armoured, manifest.get(1).airframe);
        assertEquals("boat_04", ((FittedBoat) manifest.get(1).airframe).boatId(),
                "a replay of a mission that lost a boat has to lose the same one");
        assertEquals(FittedBoat.standard(ShuttleType.AEROSHUTTLE),
                manifest.get(2).airframe);
        assertNull(((FittedBoat) manifest.get(2).airframe).boatId(),
                "a fitted frame nobody owns names no boat and must not invent one");
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
                                EconomicFunction.SPACEPORT),
                        SurfacePalette.ROCK, SettlementLink.ROAD),
                List.of(new FighterWingCommitment(FighterProfile.BROADSWORD,
                        Faction.MARINE, 2, 12f, 30f)),
                List.of(
                        new FighterWingCommitment(FighterProfile.DAGGER,
                                Faction.DEFENDER, 1, 20f, 45f),
                        new FighterWingCommitment(FighterProfile.TALON,
                                Faction.DEFENDER, 3, 10f, 15f)),
                new ShuttleArrivalPlan(MarineArrivalPolicy.PAIRED_HALF_SQUAD,
                        1, new ConquestArrivalConfig(4, 2, 1.75f)));

        BattleFixture decoded = BattleFixtureJson.fromJson(
                BattleFixtureJson.toJson(fixture));

        assertEquals(fixture, decoded);
    }

    @Test
    void roundTripsEverySabotageFactoryInputInAuthoredOrder() throws Exception {
        SabotageBattleFixture fixture = new SabotageBattleFixture(
                -8_675_309L,
                List.of(
                        new ShuttleAssignment(ShuttleType.KITE, 3, 4),
                        new ShuttleAssignment(ShuttleType.VALKYRIE, 2, 7)),
                true,
                OperationTier.VETERAN,
                RiskLevel.HIGH,
                new TargetProfile(6, 3, 5, 2, "luddic_path",
                        EnumSet.of(EconomicFunction.MINING,
                                EconomicFunction.HEAVY_INDUSTRY),
                        SurfacePalette.ROCK, SettlementLink.ROAD),
                List.of(
                        new FighterWingCommitment(FighterProfile.BROADSWORD,
                                Faction.MARINE, 2, 5f, 18f),
                        new FighterWingCommitment(FighterProfile.DAGGER,
                                Faction.MARINE, 1, 14f, 30f)),
                List.of(new FighterWingCommitment(FighterProfile.TALON,
                        Faction.DEFENDER, 3, 8f, 12f)));

        JSONObject encoded = BattleFixtureJson.toJson(fixture);
        BattleFixture decoded = BattleFixtureJson.fromJson(encoded);

        assertEquals(BattleFixtureJson.SCHEMA_VERSION,
                encoded.getInt("schemaVersion"));
        assertEquals(fixture, decoded);
        assertEquals(encoded.toString(),
                BattleFixtureJson.toJson(decoded).toString());
    }

    @Test
    void roundTripsEveryAssaultFactoryInputInAuthoredOrder() throws Exception {
        AssaultBattleFixture fixture = new AssaultBattleFixture(
                607_898L,
                List.of(
                        new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 3, 6),
                        new ShuttleAssignment(ShuttleType.KITE, 2, 4)),
                true,
                OperationTier.ESTABLISHED,
                RiskLevel.HIGH,
                new TargetProfile(6, 7, 5, 2, "independent",
                        EnumSet.of(EconomicFunction.HABITATION,
                                EconomicFunction.SPACEPORT,
                                EconomicFunction.MILITARY),
                        SurfacePalette.ROCK, SettlementLink.ROAD),
                List.of(new FighterWingCommitment(FighterProfile.BROADSWORD,
                        Faction.MARINE, 2, 5f, 18f)),
                List.of(new FighterWingCommitment(FighterProfile.TALON,
                        Faction.DEFENDER, 3, 8f, 12f)));

        JSONObject encoded = BattleFixtureJson.toJson(fixture);
        BattleFixture decoded = BattleFixtureJson.fromJson(encoded);

        assertEquals(fixture, decoded);
        assertEquals(encoded.toString(),
                BattleFixtureJson.toJson(decoded).toString());
    }

    @Test
    void roundTripsEveryRaidFactoryInputInAuthoredOrder() throws Exception {
        RaidBattleFixture fixture = new RaidBattleFixture(
                141_418L,
                List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 3, 6)),
                true, OperationTier.ESTABLISHED, RiskLevel.HIGH,
                new TargetProfile(6, 5, 4, 2, "independent",
                        EnumSet.of(EconomicFunction.SPACEPORT,
                                EconomicFunction.HEAVY_INDUSTRY),
                        SurfacePalette.ROCK, SettlementLink.ROAD),
                List.of(new FighterWingCommitment(FighterProfile.BROADSWORD,
                        Faction.MARINE, 1, 6f, 20f)),
                List.of(new FighterWingCommitment(FighterProfile.TALON,
                        Faction.DEFENDER, 2, 8f, 18f)));

        JSONObject encoded = BattleFixtureJson.toJson(fixture);
        BattleFixture decoded = BattleFixtureJson.fromJson(encoded);

        assertEquals(fixture, decoded);
        assertEquals(encoded.toString(),
                BattleFixtureJson.toJson(decoded).toString());
    }

    @Test
    void roundTripsAssaultLaunchOverlayAroundV2Construction() throws Exception {
        AssaultBattleFixture construction = new AssaultBattleFixture(
                91_441L,
                List.of(new ShuttleAssignment(
                        ShuttleType.AEROSHUTTLE, 2, 6)),
                false, OperationTier.REINFORCED, RiskLevel.MEDIUM,
                TargetProfile.NEUTRAL, List.of(), List.of());
        BattleLaunchFixture fixture = new BattleLaunchFixture(construction,
                new BattleLaunchOverlay(
                        1, List.of(), List.of(), List.of(), List.of(), 12));

        JSONObject encoded = BattleFixtureJson.toJson(fixture);
        BattleFixture decoded = BattleFixtureJson.fromJson(encoded);

        assertEquals(AssaultBattleFixture.KIND, encoded.getString("kind"));
        assertEquals(fixture, decoded);
        assertEquals(encoded.toString(),
                BattleFixtureJson.toJson(decoded).toString());
    }

    @Test
    void roundTripsSabotageLaunchOverlayAroundV2Construction() throws Exception {
        SabotageBattleFixture construction = new SabotageBattleFixture(
                48_151L,
                List.of(
                        new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6),
                        new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6)),
                true,
                OperationTier.ESTABLISHED,
                RiskLevel.MEDIUM,
                TargetProfile.NEUTRAL,
                List.of(),
                List.of());
        BattleLaunchFixture fixture = new BattleLaunchFixture(construction,
                new BattleLaunchOverlay(
                        0, List.of(), List.of(), List.of(), List.of(), 0));

        JSONObject encoded = BattleFixtureJson.toJson(fixture);
        BattleFixture decoded = BattleFixtureJson.fromJson(encoded);

        assertEquals(4, encoded.getInt("schemaVersion"));
        assertEquals(SabotageBattleFixture.KIND, encoded.getString("kind"));
        assertEquals(2, encoded.getJSONObject("construction")
                .getInt("schemaVersion"));
        assertEquals(fixture, decoded);
        assertEquals(encoded.toString(),
                BattleFixtureJson.toJson(decoded).toString());
    }

    @Test
    void roundTripsV4LaunchOverlayWithFieldPresenceAndV2Construction() throws Exception {
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
                                        MissileReplenisherComponent.ACCELERATED_FEED,
                                        MechWeaponComponent.QUARRY_BREAKER_CANNON,
                                        MechWeaponComponent.LRM_5,
                                        MechWeaponComponent.THERMAL_LANCE)))),
                37,
                FieldPresencePolicy.CAPTURE_TEAM);
        BattleLaunchFixture fixture = new BattleLaunchFixture(construction, launch);

        JSONObject encoded = BattleFixtureJson.toJson(fixture);
        BattleFixture decoded = BattleFixtureJson.fromJson(encoded);

        assertEquals(4, encoded.getInt("schemaVersion"));
        assertEquals(FieldPresencePolicy.CAPTURE_TEAM.name(),
                encoded.getJSONObject("launch").getString("fieldPresencePolicy"));
        assertEquals(2, encoded.getJSONObject("construction")
                .getInt("schemaVersion"));
        assertEquals(fixture, decoded);
        assertEquals(encoded.toString(), BattleFixtureJson.toJson(decoded).toString());
    }

    @Test
    void v3LaunchFixturesDefaultToUnrestrictedFieldPresence() throws Exception {
        BattleLaunchFixture fixture = new BattleLaunchFixture(
                canonicalFixture(), new BattleLaunchOverlay(
                        0, List.of(), List.of(), List.of(), List.of(), 0,
                        FieldPresencePolicy.CAPTURE_TEAM));
        JSONObject legacy = BattleFixtureJson.toJson(fixture);
        legacy.put("schemaVersion",
                BattleFixtureJson.PRE_FIELD_PRESENCE_LAUNCH_SCHEMA_VERSION);
        legacy.getJSONObject("launch").remove("fieldPresencePolicy");

        BattleLaunchFixture decoded = (BattleLaunchFixture)
                BattleFixtureJson.fromJson(legacy);

        assertEquals(FieldPresencePolicy.UNRESTRICTED,
                decoded.launch().fieldPresencePolicy());
    }

    /**
     * How settled the map is survives a round trip, and its absence survives it
     * too — an absent {@code sprawl} is the derived case, which is what every
     * fixture written before the field existed means.
     */
    @Test
    void carriesAStatedSprawlAndLeavesAnUnstatedOneAlone() throws Exception {
        RaidBattleFixture stated = sprawlRaidFixture(PrecinctPlan.Sprawl.DENSE);

        JSONObject encoded = BattleFixtureJson.toJson(stated);
        assertEquals("DENSE", encoded.getString("sprawl"));
        BattleFixture decoded = BattleFixtureJson.fromJson(encoded);
        assertEquals(stated, decoded);

        RaidBattleFixture silent = sprawlRaidFixture(null);
        JSONObject silentEncoded = BattleFixtureJson.toJson(silent);
        assertFalse(silentEncoded.has("sprawl"),
                "a fixture that states no sprawl must write no key, or every "
                        + "checked-in fixture stops being what it was");
        RaidBattleFixture silentDecoded =
                (RaidBattleFixture) BattleFixtureJson.fromJson(silentEncoded);
        assertNull(silentDecoded.sprawl(),
                "an absent sprawl decodes to the derived case, not to a value");
    }

    /** An unknown sprawl fails the way every other unknown enum here does. */
    @Test
    void rejectsAnUnknownSprawl() throws Exception {
        JSONObject encoded = BattleFixtureJson.toJson(
                sprawlRaidFixture(PrecinctPlan.Sprawl.DENSE));
        encoded.put("sprawl", "MEGALOPOLIS");
        assertThrows(IllegalArgumentException.class,
                () -> BattleFixtureJson.fromJson(encoded));
    }

    /**
     * How far out the force lands survives a round trip, and its absence
     * survives it too — an absent {@code standoff} is the mission type's own
     * default, which is what every fixture written before the field existed
     * means.
     */
    @Test
    void carriesAStatedStandoffAndLeavesAnUnstatedOneAlone() throws Exception {
        ConquestBattleFixture stated = standoffConquestFixture(Standoff.CLOSE);

        JSONObject encoded = BattleFixtureJson.toJson(stated);
        assertEquals("CLOSE", encoded.getString("standoff"));
        assertEquals(stated, BattleFixtureJson.fromJson(encoded));

        ConquestBattleFixture silent = standoffConquestFixture(null);
        JSONObject silentEncoded = BattleFixtureJson.toJson(silent);
        assertFalse(silentEncoded.has("standoff"),
                "a fixture that states no standoff must write no key, or every "
                        + "checked-in conquest fixture stops being what it was");
        ConquestBattleFixture silentDecoded =
                (ConquestBattleFixture) BattleFixtureJson.fromJson(silentEncoded);
        assertNull(silentDecoded.standoff(),
                "an absent standoff decodes to the default case, not to a value");
    }

    /** An unknown standoff fails the way every other unknown enum here does. */
    @Test
    void rejectsAnUnknownStandoff() throws Exception {
        JSONObject encoded = BattleFixtureJson.toJson(
                standoffConquestFixture(Standoff.CLOSE));
        encoded.put("standoff", "ADJACENT");
        assertThrows(IllegalArgumentException.class,
                () -> BattleFixtureJson.fromJson(encoded));
    }

    /**
     * How many lanes of resistance the map lays survives a round trip, and its
     * absence survives it too. Same law as the standoff: an absent {@code lanes}
     * is one per command track, which is what every fixture written before the
     * field existed means.
     */
    @Test
    void carriesAStatedLaneCountAndLeavesAnUnstatedOneAlone() throws Exception {
        ConquestBattleFixture stated = laneConquestFixture(1);
        JSONObject encoded = BattleFixtureJson.toJson(stated);
        assertEquals(1, encoded.getInt("lanes"));
        assertEquals(stated, BattleFixtureJson.fromJson(encoded));

        // Zero is a statement rather than the absence of one: it asks for the
        // map Conquest had before lanes existed, which is the control.
        ConquestBattleFixture none = laneConquestFixture(0);
        JSONObject noneEncoded = BattleFixtureJson.toJson(none);
        assertEquals(0, noneEncoded.getInt("lanes"));
        assertEquals(none, BattleFixtureJson.fromJson(noneEncoded));

        ConquestBattleFixture silent = laneConquestFixture(null);
        JSONObject silentEncoded = BattleFixtureJson.toJson(silent);
        assertFalse(silentEncoded.has("lanes"),
                "a fixture that states no lane count must write no key, or every "
                        + "checked-in conquest fixture stops being what it was");
        assertNull(((ConquestBattleFixture) BattleFixtureJson.fromJson(silentEncoded))
                        .lanes(),
                "an absent lane count decodes to the default case, not to a value");
    }

    /** A negative lane count is refused the way an unknown enum is. */
    @Test
    void rejectsANegativeLaneCount() throws Exception {
        JSONObject encoded = BattleFixtureJson.toJson(laneConquestFixture(2));
        encoded.put("lanes", -1);
        assertThrows(IllegalArgumentException.class,
                () -> BattleFixtureJson.fromJson(encoded));
    }

    private static ConquestBattleFixture laneConquestFixture(Integer lanes) {
        ConquestBattleFixture base = standoffConquestFixture(Standoff.CLOSE);
        return new ConquestBattleFixture(base.seed(), base.manifest(),
                base.enemyHasHeavyArmor(), base.tier(), base.risk(),
                base.targetProfile(), base.marineFighterSupport(),
                base.enemyFighterSupport(), base.arrivalPlan(), base.sprawl(),
                base.standoff(), lanes);
    }

    private static ConquestBattleFixture standoffConquestFixture(Standoff standoff) {
        return new ConquestBattleFixture(
                141_418L,
                List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 3, 6)),
                true, OperationTier.REINFORCED, RiskLevel.HIGH,
                new TargetProfile(6, 5, 4, 2, "independent",
                        EnumSet.of(EconomicFunction.SPACEPORT,
                                EconomicFunction.HEAVY_INDUSTRY),
                        SurfacePalette.ROCK, SettlementLink.ROAD),
                List.of(), List.of(), ShuttleArrivalPlan.legacy(), null, standoff);
    }

    private static RaidBattleFixture sprawlRaidFixture(PrecinctPlan.Sprawl sprawl) {
        return new RaidBattleFixture(
                141_418L,
                List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 3, 6)),
                true, OperationTier.ESTABLISHED, RiskLevel.HIGH,
                new TargetProfile(6, 5, 4, 2, "independent",
                        EnumSet.of(EconomicFunction.SPACEPORT,
                                EconomicFunction.HEAVY_INDUSTRY),
                        SurfacePalette.ROCK, SettlementLink.ROAD),
                List.of(), List.of(), sprawl);
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
        profileDump.put("schemaVersion", 7);
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
                                EconomicFunction.SPACEPORT),
                        SurfacePalette.ROCK, SettlementLink.ROAD),
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
