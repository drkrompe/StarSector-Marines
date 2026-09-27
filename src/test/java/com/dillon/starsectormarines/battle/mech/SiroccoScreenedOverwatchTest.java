package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadBeliefTestAccess;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiroccoScreenedOverwatchTest {

    private static final int THREAT_X = 70;
    private static final int THREAT_Y = 30;

    @Test
    void prefersANonSiroccoAllyBetweenItsLaneAndTheThreat() {
        Fixture f = fixture(10, 30);
        long infantry = spawnInfantry(f.sim, Faction.DEFENDER, 44, 30);

        OverwatchKillZone.OverwatchPosition position =
                OverwatchKillZone.pickOverwatchCell(f.sirocco, f.squad, f.sim);

        assertNotNull(position);
        assertEquals(infantry, position.screenId());
        assertInOverwatchBand(position);
        assertTrue(position.x() < f.sim.world().cellX(infantry));
        assertTrue(f.sim.world().cellX(infantry) < THREAT_X);
    }

    @Test
    void screenedFrontOutranksCoveredCurrentCellAndLeavesAFriendlyFireLane() {
        Fixture f = fixture(46, 30);
        long infantry = spawnInfantry(f.sim, Faction.DEFENDER, 39, 30);
        f.sim.getGrid().setCoverAtFacing(46, 30,
                NavigationGrid.FACING_E, NavigationGrid.MAX_COVER);
        f.sim.getUnitIndex().rebuild(f.sim.getRoster());

        OverwatchKillZone.OverwatchPosition position =
                OverwatchKillZone.pickOverwatchCell(f.sirocco, f.squad, f.sim);

        assertNotNull(position);
        assertEquals(infantry, position.screenId(),
                "a credible infantry front is a doctrine requirement, not a soft score bonus");
        assertTrue(position.x() < f.sim.world().cellX(infantry),
                "support should move behind the infantry instead of keeping its covered forward cell");
        assertTrue(screenLateralDistance(position, infantry, f.sim)
                        >= OverwatchKillZone.SCREEN_FIRE_LANE_CLEARANCE,
                "the representative screen must sit clear of the direct projectile ray");
    }

    @Test
    void firingLaneClearsEveryFriendlyRatherThanOnlyTheNamedScreen() {
        Fixture f = fixture(46, 30);
        long centerMarine = spawnInfantry(f.sim, Faction.DEFENDER, 39, 30);
        long flankMarine = spawnInfantry(f.sim, Faction.DEFENDER, 39, 35);
        f.sim.getGrid().setCoverAtFacing(46, 30,
                NavigationGrid.FACING_E, NavigationGrid.MAX_COVER);
        f.sim.getUnitIndex().rebuild(f.sim.getRoster());

        OverwatchKillZone.OverwatchPosition position =
                OverwatchKillZone.pickOverwatchCell(f.sirocco, f.squad, f.sim);

        assertNotNull(position);
        assertTrue(position.screenId() == centerMarine
                || position.screenId() == flankMarine);
        assertTrue(screenLateralDistance(position, centerMarine, f.sim)
                        >= OverwatchKillZone.SCREEN_FIRE_LANE_CLEARANCE,
                "an unnamed Marine may not remain in the direct firing lane");
        assertTrue(screenLateralDistance(position, flankMarine, f.sim)
                        >= OverwatchKillZone.SCREEN_FIRE_LANE_CLEARANCE,
                "the named screen is not the only friendly body that matters");
    }

    @Test
    void nonCombatantEnteringACachedFiringLaneForcesAnImmediateRepick() {
        Fixture f = fixture(46, 30);
        long screen = spawnInfantry(f.sim, Faction.DEFENDER, 39, 35);
        long movingEngineer = f.sim.spawn(new EntitySpec(
                "moving-engineer", Faction.DEFENDER, UnitType.ENGINEER,
                10, 10));
        f.sim.getUnitIndex().rebuild(f.sim.getRoster());

        OverwatchKillZone.INSTANCE.execute(f.sirocco, f.squad, f.sim);
        int originalX = f.loadout.overwatchCellX;
        int originalY = f.loadout.overwatchCellY;
        assertEquals(screen, f.loadout.overwatchScreenId);

        int rayMidpointX = Math.round((originalX + THREAT_X) / 2f);
        int rayMidpointY = Math.round((originalY + THREAT_Y) / 2f);
        f.sim.world().setCellPos(
                movingEngineer, rayMidpointX, rayMidpointY);
        f.sim.getUnitIndex().rebuild(f.sim.getRoster());

        OverwatchKillZone.INSTANCE.execute(f.sirocco, f.squad, f.sim);

        assertTrue(f.loadout.overwatchCellX != originalX
                        || f.loadout.overwatchCellY != originalY,
                "a newly obstructed cached perch must be rejected immediately");
        OverwatchKillZone.OverwatchPosition replacement =
                new OverwatchKillZone.OverwatchPosition(
                        f.loadout.overwatchCellX,
                        f.loadout.overwatchCellY,
                        f.loadout.overwatchScreenId);
        assertTrue(screenLateralDistance(replacement, movingEngineer, f.sim)
                        >= OverwatchKillZone.SCREEN_FIRE_LANE_CLEARANCE,
                "the replacement perch must clear the moving engineer");
    }

    @Test
    void alliedVehicleEnteringACachedFiringLaneForcesAnImmediateRepick() {
        Fixture f = fixture(46, 30);
        long screen = spawnInfantry(f.sim, Faction.DEFENDER, 39, 35);
        f.sim.getUnitIndex().rebuild(f.sim.getRoster());

        OverwatchKillZone.INSTANCE.execute(f.sirocco, f.squad, f.sim);
        int originalX = f.loadout.overwatchCellX;
        int originalY = f.loadout.overwatchCellY;
        assertEquals(screen, f.loadout.overwatchScreenId);

        float rayMidpointX = (originalX + THREAT_X) / 2f + 0.5f;
        float rayMidpointY = (originalY + THREAT_Y) / 2f + 0.5f;
        VehicleMission mission = new VehicleMission(
                new float[]{rayMidpointX, rayMidpointX + 1f},
                new float[]{rayMidpointY, rayMidpointY},
                new float[]{rayMidpointX + 1f, rayMidpointX},
                new float[]{rayMidpointY, rayMidpointY},
                0f, VehicleType.HEAVY_APC.capacity);
        mission.state = VehicleState.LANDED;
        long apc = f.sim.convoy().spawn(
                VehicleType.HEAVY_APC, Faction.DEFENDER, mission);

        OverwatchKillZone.INSTANCE.execute(f.sirocco, f.squad, f.sim);

        assertTrue(f.loadout.overwatchCellX != originalX
                        || f.loadout.overwatchCellY != originalY,
                "an allied vehicle must invalidate an obstructed cached perch");
        OverwatchKillZone.OverwatchPosition replacement =
                new OverwatchKillZone.OverwatchPosition(
                        f.loadout.overwatchCellX,
                        f.loadout.overwatchCellY,
                        f.loadout.overwatchScreenId);
        assertTrue(screenLateralDistance(replacement, apc, f.sim)
                        >= f.sim.physicalRadius(apc),
                "the replacement perch must clear the allied vehicle body");
    }

    @Test
    void broadFourCellFrontCountsWithoutPuttingTheMarineNearTheShotRay() {
        int width = 96;
        int height = 64;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int x = 0; x < width; x++) grid.setWalkableFloor(x, THREAT_Y);
        grid.setWalkableFloor(39, 34);
        BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(width, height));
        long sirocco = spawnMech(sim, Faction.DEFENDER,
                MechVariant.SIROCCO, 46, THREAT_Y);
        Squad squad = sim.squadOf(sirocco);
        long infantry = spawnInfantry(sim, Faction.DEFENDER, 39, 34);
        long enemy = sim.spawn(new EntitySpec(
                "corridor-enemy", Faction.MARINE, UnitType.MARINE,
                THREAT_X, THREAT_Y));
        SquadBeliefTestAccess.observeDirect(squad, enemy,
                THREAT_X, THREAT_Y, sim.getSimTickIndex());
        squad.lastSeenEnemyX = THREAT_X;
        squad.lastSeenEnemyY = THREAT_Y;
        sim.getUnitIndex().rebuild(sim.getRoster());

        OverwatchKillZone.OverwatchPosition position =
                OverwatchKillZone.pickOverwatchCell(sirocco, squad, sim);

        assertNotNull(position);
        assertEquals(infantry, position.screenId());
        assertEquals(THREAT_Y, position.y());
        assertTrue(screenLateralDistance(position, infantry, sim) > 3f,
                "the screen is a broad front, not the former three-cell ray corridor");
        assertTrue(screenLateralDistance(position, infantry, sim)
                <= OverwatchKillZone.SCREEN_AXIS_HALF_WIDTH);
    }

    @Test
    void longRangeSupportUsesPartlySpentLrmsAndMovesBehindMarines() {
        BattleSimulation sim = openSimulation();
        long sirocco = spawnMech(sim, Faction.MARINE,
                MechVariant.SIROCCO, 46, 30);
        Squad squad = sim.squadOf(sirocco);
        long infantry = spawnInfantry(sim, Faction.MARINE, 39, 30);
        long enemy = sim.spawn(new EntitySpec(
                "enemy", Faction.DEFENDER, UnitType.MARINE,
                THREAT_X, THREAT_Y));
        SquadBeliefTestAccess.observeDirect(squad, enemy,
                THREAT_X, THREAT_Y, sim.getSimTickIndex());
        squad.lastSeenEnemyX = THREAT_X;
        squad.lastSeenEnemyY = THREAT_Y;
        sim.world().setTargetId(sirocco, enemy);
        sim.getUnitIndex().rebuild(sim.getRoster());
        MechLoadoutComponent loadout = sim.world().mechLoadout(sirocco);
        MechWeaponMount lrm = loadout.mount(MechMountSlot.RIGHT_SHOULDER);
        lrm.ammo = Math.max(1, lrm.ammo - 1);

        sim.getMechDoctrineService().requestOverride(
                sirocco, MechRole.LR_SUPPORT);
        new MechDoctrineSystem(sim.getMechDoctrineService()).tick(sim);
        ExecuteMechDoctrine.INSTANCE.execute(sirocco, squad, sim);

        assertEquals(MechRole.LR_SUPPORT, loadout.effectiveRole());
        assertTrue(loadout.overwatchLongRangeBand,
                "a fresh LR order should use every available LRM instead of inheriting rearm fallback");
        assertEquals(infantry, loadout.overwatchScreenId);
        assertTrue(loadout.overwatchCellX < sim.world().cellX(infantry));
        assertFalse(Paths.isEmpty(sim.world().path(sirocco)),
                "the serialized doctrine command should author visible repositioning");
    }

    @Test
    void clearZoneAllowsBoundedStandoffAgainstAContactInsideTheMissionZone() {
        BattleSimulation sim = twoRoomSimulation();
        long bulwark = spawnMech(sim, Faction.MARINE,
                MechVariant.BULWARK, 44, 30);
        MechLoadoutComponent loadout = sim.world().mechLoadout(bulwark);
        loadout.applyBattleOverride(MechRole.LR_SUPPORT);
        Squad squad = sim.squadOf(bulwark);
        int targetZone = sim.getZoneGraph().zoneIdAt(66, 30);
        squad.assignedObjective = ObjectiveAssignment.clearZone(
                squad.id, targetZone);
        long infantry = spawnInfantry(sim, Faction.MARINE, 42, 34);
        long enemy = sim.spawn(new EntitySpec(
                "zone-contact", Faction.DEFENDER, UnitType.MARINE,
                66, 30));
        SquadBeliefTestAccess.observeDirect(squad, enemy,
                66, 30, sim.getSimTickIndex());
        squad.lastSeenEnemyX = 66;
        squad.lastSeenEnemyY = 30;
        sim.getUnitIndex().rebuild(sim.getRoster());

        OverwatchKillZone.OverwatchPosition position =
                OverwatchKillZone.pickOverwatchCell(bulwark, squad, sim);

        assertNotNull(position);
        assertEquals(infantry, position.screenId());
        assertTrue(sim.getZoneGraph().zoneIdAt(position.x(), position.y())
                        != targetZone,
                "LR support may use the bounded doorway-side perimeter while clearing a contact in-zone");
        assertTrue(position.x() < sim.world().cellX(infantry));
        assertFalse(MechAssignmentBoundary.permitsOverwatchCell(
                        bulwark, squad, 20, 30, 66, 30, sim),
                "the doctrine must not turn bounded standoff into permission to abandon the zone");
        assertFalse(MechAssignmentBoundary.permitsOverwatchCell(
                        bulwark, squad, 39, 30, 20, 30, sim),
                "the perimeter exists only while prosecuting a contact inside the assigned zone");
    }

    @Test
    void rushedSupportMayOpenDistanceIntoTheBoundedZonePerimeter() {
        // The 1.2-cell-wide Bulwark cannot cross the one-cell Sirocco doorway.
        // This test asks about the permitted retreat perimeter, not refusal of
        // a physically impossible passage, so give it a two-cell opening.
        BattleSimulation sim = twoRoomSimulation(2);
        assertTrue(ManualTerrainMotion.canStand(sim.getGrid(), 40.5f, 31f,
                MechVariant.BULWARK.radius), "the actual chassis fits the doorway centerline");
        long bulwark = spawnMech(sim, Faction.MARINE,
                MechVariant.BULWARK, 43, 30);
        Squad squad = sim.squadOf(bulwark);
        int targetZone = sim.getZoneGraph().zoneIdAt(46, 30);
        squad.assignedObjective = ObjectiveAssignment.clearZone(
                squad.id, targetZone);
        long enemy = sim.spawn(new EntitySpec(
                "rushing-zone-contact", Faction.DEFENDER, UnitType.MARINE,
                46, 30));
        SquadBeliefTestAccess.observeDirect(squad, enemy,
                46, 30, sim.getSimTickIndex());
        squad.lastSeenEnemyX = 46;
        squad.lastSeenEnemyY = 30;
        sim.world().setTargetId(bulwark, enemy);
        sim.getUnitIndex().rebuild(sim.getRoster());

        sim.getMechDoctrineService().requestOverride(
                bulwark, MechRole.LR_SUPPORT);
        new MechDoctrineSystem(sim.getMechDoctrineService()).tick(sim);
        ExecuteMechDoctrine.INSTANCE.execute(bulwark, squad, sim);

        int[] path = sim.world().path(bulwark);
        assertEquals(MechRole.LR_SUPPORT,
                sim.world().mechLoadout(bulwark).effectiveRole());
        assertFalse(Paths.isEmpty(path));
        assertTrue(Paths.destX(path) < sim.world().cellX(bulwark));
        assertTrue(sim.getZoneGraph().zoneIdAt(
                        Paths.destX(path), Paths.destY(path)) != targetZone,
                "a close in-zone threat may be answered from the same bounded perimeter");
    }

    @Test
    void overwatchGeometryUsesTheCurrentEngageableTargetAsItsThreatAxis() {
        Fixture f = fixture(42, 30);
        long currentTarget = f.sim.spawn(new EntitySpec(
                "current-target", Faction.MARINE, UnitType.MARINE,
                70, 40));
        SquadBeliefTestAccess.observeDirect(f.squad, currentTarget,
                70, 40, f.sim.getSimTickIndex());
        f.squad.lastSeenEnemyX = THREAT_X;
        f.squad.lastSeenEnemyY = THREAT_Y;
        f.sim.world().setTargetId(f.sirocco, currentTarget);

        OverwatchKillZone.INSTANCE.execute(f.sirocco, f.squad, f.sim);

        assertEquals(currentTarget, f.sim.world().targetId(f.sirocco));
        assertEquals(70, f.loadout.overwatchAxisX);
        assertEquals(40, f.loadout.overwatchAxisY,
                "movement and firing should reason about the same perceived enemy");
    }

    @Test
    void anotherSiroccoCannotPretendToBeTheFrontLine() {
        Fixture f = fixture(10, 30);
        long otherSirocco = spawnMech(
                f.sim, Faction.DEFENDER, MechVariant.SIROCCO, 44, 30);
        f.sim.world().mechLoadout(otherSirocco)
                .applyBattleOverride(MechRole.ARMORED_SUPPORT);

        OverwatchKillZone.OverwatchPosition position =
                OverwatchKillZone.pickOverwatchCell(f.sirocco, f.squad, f.sim);

        assertNotNull(position, "screening is preferred, not required");
        assertEquals(0L, position.screenId());
        assertInOverwatchBand(position);
    }

    @Test
    void unreachableOuterBandCellsAreNotCachedAsOverwatch() {
        int width = 96;
        int height = 64;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x != 20) grid.setWalkableFloor(x, y);
            }
        }
        BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(width, height));
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.HEAVY_MECH);
        long sirocco = sim.spawn(MechVariant.SIROCCO.applyTo(new EntitySpec(
                "isolated-sirocco", Faction.DEFENDER, UnitType.HEAVY_MECH,
                10, THREAT_Y).squad(squadId)));
        sim.world().attachMechLoadout(sirocco,
                MechVariant.SIROCCO.createLoadout(MechRole.LR_SUPPORT));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = sirocco;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = 10.5f;
        squad.centroidY = THREAT_Y + 0.5f;
        squad.lastSeenEnemyX = THREAT_X;
        squad.lastSeenEnemyY = THREAT_Y;

        assertNull(OverwatchKillZone.pickOverwatchCell(sirocco, squad, sim),
                "geometrically legal cells beyond a sealed wall are not usable perches");
    }

    @Test
    void rushedSupportOpensDistanceInsteadOfHoldingItsPerch() {
        Fixture f = fixture(30, 30);
        f.sim.world().setCellPos(f.enemy, 35, 30);
        f.squad.lastSeenEnemyX = 35;
        f.squad.lastSeenEnemyY = 30;

        OverwatchKillZone.INSTANCE.execute(f.sirocco, f.squad, f.sim);

        int[] path = f.sim.world().path(f.sirocco);
        assertFalse(Paths.isEmpty(path));
        float initialDistance = 5f;
        float destinationDx = Paths.destX(path) + 0.5f
                - f.sim.world().x(f.enemy);
        float destinationDy = Paths.destY(path) + 0.5f
                - f.sim.world().y(f.enemy);
        assertTrue(destinationDx * destinationDx + destinationDy * destinationDy
                        > initialDistance * initialDistance,
                "a contact inside the support posture forces an outward move");
        assertEquals(f.enemy, f.sim.world().targetId(f.sirocco));
    }

    @Test
    void enemyCannotProvideTheScreenAndUnscreenedOverwatchStillWorks() {
        Fixture f = fixture(10, 30);
        spawnInfantry(f.sim, Faction.MARINE, 44, 30);

        OverwatchKillZone.OverwatchPosition position =
                OverwatchKillZone.pickOverwatchCell(f.sirocco, f.squad, f.sim);

        assertNotNull(position);
        assertEquals(0L, position.screenId());
        assertInOverwatchBand(position);
    }

    @Test
    void leavingTheFiringAxisInvalidatesTheCachedScreen() {
        Fixture f = fixture(10, 30);
        long infantry = spawnInfantry(f.sim, Faction.DEFENDER, 44, 30);

        OverwatchKillZone.INSTANCE.execute(f.sirocco, f.squad, f.sim);
        assertEquals(infantry, f.loadout.overwatchScreenId);

        f.sim.world().setCellPos(infantry, 8, 55);
        OverwatchKillZone.INSTANCE.execute(f.sirocco, f.squad, f.sim);

        assertEquals(0L, f.loadout.overwatchScreenId,
                "the Sirocco must stop treating a departed ally as its screen");
    }

    @Test
    void mediumBandAllowsAHeavyCannonShotOfOpportunity() {
        Fixture f = fixture(45, 30);
        spawnInfantry(f.sim, Faction.DEFENDER, 55, 30);
        f.sim.world().setTargetId(f.sirocco, f.enemy);
        f.loadout.torsoAimTargetId = f.enemy;
        f.loadout.torsoOnTarget = true;

        OverwatchKillZone.INSTANCE.execute(f.sirocco, f.squad, f.sim);

        assertTrue(f.loadout.mount(MechMountSlot.ARMS).cooldown > 0f,
                "the 25-cell contact should receive the installed heavy-cannon shot");
        assertEquals(0f, f.loadout.mount(MechMountSlot.LEFT_SHOULDER).cooldown,
                "LRMs remain withheld inside the heavy-cannon band");
        assertEquals(0f, f.loadout.mount(MechMountSlot.RIGHT_SHOULDER).cooldown);
    }

    @Test
    void emptyLrmRacksInvalidateLongPerchAndCloseIntoHeavyCannonBand() {
        Fixture f = fixture(42, 30);
        f.loadout.overwatchCellX = 42;
        f.loadout.overwatchCellY = 30;
        f.loadout.overwatchAxisX = THREAT_X;
        f.loadout.overwatchAxisY = THREAT_Y;
        f.loadout.overwatchLongRangeBand = true;
        emptyLrmRacks(f.loadout);

        OverwatchKillZone.INSTANCE.execute(f.sirocco, f.squad, f.sim);

        assertFalse(f.loadout.overwatchLongRangeBand,
                "spent LRMs must invalidate the cached long-range posture");
        assertInHeavyCannonFallbackBand(f.loadout.overwatchCellX,
                f.loadout.overwatchCellY);
    }

    @Test
    void partiallyReplenishedLrmRackKeepsCannonFallbackStable() {
        Fixture f = fixture(45, 30);
        emptyLrmRacks(f.loadout);
        f.loadout.overwatchCellX = 45;
        f.loadout.overwatchCellY = 30;
        f.loadout.overwatchAxisX = THREAT_X;
        f.loadout.overwatchAxisY = THREAT_Y;
        f.loadout.overwatchLongRangeBand = false;
        f.loadout.overwatchRearming = true;
        f.loadout.mount(MechMountSlot.LEFT_SHOULDER).ammo = 1;

        OverwatchKillZone.INSTANCE.execute(f.sirocco, f.squad, f.sim);

        assertFalse(f.loadout.overwatchLongRangeBand,
                "one restored trigger must not make the Sirocco oscillate outward");
        assertEquals(1, f.loadout.mount(MechMountSlot.LEFT_SHOULDER).ammo,
                "fallback posture withholds LRMs while the racks rebuild");
        assertInHeavyCannonFallbackBand(f.loadout.overwatchCellX,
                f.loadout.overwatchCellY);
    }

    @Test
    void exhaustedRearmCycleSurvivesADoctrineRoundTrip() {
        Fixture f = fixture(45, 30);
        emptyLrmRacks(f.loadout);
        OverwatchKillZone.INSTANCE.execute(f.sirocco, f.squad, f.sim);
        assertTrue(f.loadout.overwatchRearming);

        f.loadout.applyBattleOverride(MechRole.BALANCED);
        f.loadout.mount(MechMountSlot.LEFT_SHOULDER).ammo = 1;
        f.loadout.applyBattleOverride(MechRole.LR_SUPPORT);
        OverwatchKillZone.INSTANCE.execute(f.sirocco, f.squad, f.sim);

        assertTrue(f.loadout.overwatchRearming,
                "role toggles must not erase an unfinished all-racks rearm cycle");
        assertFalse(f.loadout.overwatchLongRangeBand);
        assertEquals(1, f.loadout.mount(MechMountSlot.LEFT_SHOULDER).ammo);
        assertInHeavyCannonFallbackBand(f.loadout.overwatchCellX,
                f.loadout.overwatchCellY);
    }

    @Test
    void fullLrmRacksRestoreLongRangePosture() {
        Fixture f = fixture(45, 30);
        f.loadout.overwatchCellX = 45;
        f.loadout.overwatchCellY = 30;
        f.loadout.overwatchAxisX = THREAT_X;
        f.loadout.overwatchAxisY = THREAT_Y;
        f.loadout.overwatchLongRangeBand = false;
        f.loadout.overwatchRearming = true;
        fillLrmRacks(f.loadout);

        OverwatchKillZone.INSTANCE.execute(f.sirocco, f.squad, f.sim);

        assertTrue(f.loadout.overwatchLongRangeBand,
                "full LRM racks should restore normal overwatch doctrine");
        assertInOverwatchBand(new OverwatchKillZone.OverwatchPosition(
                f.loadout.overwatchCellX, f.loadout.overwatchCellY,
                f.loadout.overwatchScreenId));
    }

    @Test
    void finalLrmBurstFinishesBeforeDirectFireFallback() {
        Fixture f = fixture(42, 30);
        emptyLrmRacks(f.loadout);
        f.loadout.mount(MechMountSlot.LEFT_SHOULDER).burstRemaining = 1;
        f.loadout.overwatchCellX = 42;
        f.loadout.overwatchCellY = 30;
        f.loadout.overwatchAxisX = THREAT_X;
        f.loadout.overwatchAxisY = THREAT_Y;
        f.loadout.overwatchLongRangeBand = true;

        OverwatchKillZone.INSTANCE.execute(f.sirocco, f.squad, f.sim);

        assertTrue(f.loadout.overwatchLongRangeBand,
                "the last in-progress salvo should finish before the mech closes");
        assertEquals(42, f.loadout.overwatchCellX);
        assertEquals(30, f.loadout.overwatchCellY);
    }

    private static Fixture fixture(int siroccoX, int siroccoY) {
        BattleSimulation sim = openSimulation();
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.HEAVY_MECH);
        long sirocco = sim.spawn(MechVariant.SIROCCO.applyTo(new EntitySpec(
                "sirocco", Faction.DEFENDER, UnitType.HEAVY_MECH,
                siroccoX, siroccoY).squad(squadId)));
        MechLoadoutComponent loadout = MechVariant.SIROCCO.createLoadout(MechRole.LR_SUPPORT);
        sim.world().attachMechLoadout(sirocco, loadout);
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = sirocco;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = siroccoX + 0.5f;
        squad.centroidY = siroccoY + 0.5f;
        squad.lastSeenEnemyX = THREAT_X;
        squad.lastSeenEnemyY = THREAT_Y;
        long enemy = sim.spawn(new EntitySpec(
                "enemy", Faction.MARINE, UnitType.MARINE, THREAT_X, THREAT_Y));
        SquadBeliefTestAccess.observeDirect(squad, enemy,
                THREAT_X, THREAT_Y, sim.getSimTickIndex());
        return new Fixture(sim, squad, sirocco, loadout, enemy);
    }

    private static long spawnInfantry(BattleSimulation sim, Faction faction,
                                      int x, int y) {
        int squadId = sim.mintSquad(faction, UnitType.MARINE);
        long infantry = sim.spawn(new EntitySpec(
                "infantry-" + squadId, faction, UnitType.MARINE, x, y).squad(squadId));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = infantry;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = x + 0.5f;
        squad.centroidY = y + 0.5f;
        return infantry;
    }

    private static long spawnMech(BattleSimulation sim, Faction faction,
                                  MechVariant variant, int x, int y) {
        int squadId = sim.mintSquad(faction, UnitType.HEAVY_MECH);
        long mech = sim.spawn(variant.applyTo(new EntitySpec(
                variant.id + "-screen", faction, UnitType.HEAVY_MECH, x, y)
                .squad(squadId)));
        sim.world().attachMechLoadout(mech, variant.createLoadout(variant.defaultRole));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = mech;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = x + 0.5f;
        squad.centroidY = y + 0.5f;
        return mech;
    }

    private static BattleSimulation openSimulation() {
        int width = 96;
        int height = 64;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private static BattleSimulation twoRoomSimulation() {
        return twoRoomSimulation(1);
    }

    private static BattleSimulation twoRoomSimulation(int doorwayHeight) {
        int width = 96;
        int height = 64;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x != 40) grid.setWalkableFloor(x, y);
            }
        }
        for (int y = 30; y < 30 + doorwayHeight; y++) {
            grid.setWalkableFloor(40, y);
            grid.setDoorway(40, y, true);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private static float screenLateralDistance(
            OverwatchKillZone.OverwatchPosition position,
            long ally, BattleSimulation sim) {
        float startX = position.x() + 0.5f;
        float startY = position.y() + 0.5f;
        float dx = THREAT_X + 0.5f - startX;
        float dy = THREAT_Y + 0.5f - startY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float relX = sim.world().x(ally) - startX;
        float relY = sim.world().y(ally) - startY;
        return Math.abs(relX * -dy + relY * dx) / length;
    }

    private static void emptyLrmRacks(MechLoadoutComponent loadout) {
        loadout.mount(MechMountSlot.LEFT_SHOULDER).ammo = 0;
        loadout.mount(MechMountSlot.RIGHT_SHOULDER).ammo = 0;
    }

    private static void fillLrmRacks(MechLoadoutComponent loadout) {
        MechWeaponMount left = loadout.mount(MechMountSlot.LEFT_SHOULDER);
        MechWeaponMount right = loadout.mount(MechMountSlot.RIGHT_SHOULDER);
        left.ammo = left.component.ammoCapacity;
        right.ammo = right.component.ammoCapacity;
    }

    private static void assertInOverwatchBand(OverwatchKillZone.OverwatchPosition position) {
        float dx = position.x() - THREAT_X;
        float dy = position.y() - THREAT_Y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        assertTrue(distance >= OverwatchKillZone.OVERWATCH_MIN_DIST);
        assertTrue(distance <= OverwatchKillZone.OVERWATCH_MAX_DIST);
    }

    private static void assertInHeavyCannonFallbackBand(int cellX, int cellY) {
        float dx = cellX - THREAT_X;
        float dy = cellY - THREAT_Y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        assertTrue(distance >= WeaponRegistry.require(WeaponRegistry.MECH_HEAVY_CANNON_ID).range
                - OverwatchKillZone.DIRECT_FALLBACK_BAND_DEPTH);
        assertTrue(distance <= WeaponRegistry.require(WeaponRegistry.MECH_HEAVY_CANNON_ID).range,
                "fallback perch must let the unlimited heavy cannon fire");
    }

    private record Fixture(BattleSimulation sim, Squad squad, long sirocco,
                           MechLoadoutComponent loadout, long enemy) {}
}
