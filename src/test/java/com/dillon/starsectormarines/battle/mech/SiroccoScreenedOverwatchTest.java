package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void anotherSiroccoCannotPretendToBeTheFrontLine() {
        Fixture f = fixture(10, 30);
        long otherSirocco = spawnMech(
                f.sim, Faction.DEFENDER, MechVariant.SIROCCO, 44, 30);
        f.sim.world().mechLoadout(otherSirocco).role = MechRole.ARMORED_SUPPORT;

        OverwatchKillZone.OverwatchPosition position =
                OverwatchKillZone.pickOverwatchCell(f.sirocco, f.squad, f.sim);

        assertNotNull(position, "screening is preferred, not required");
        assertEquals(0L, position.screenId());
        assertInOverwatchBand(position);
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
    void fullLrmRacksRestoreLongRangePosture() {
        Fixture f = fixture(45, 30);
        f.loadout.overwatchCellX = 45;
        f.loadout.overwatchCellY = 30;
        f.loadout.overwatchAxisX = THREAT_X;
        f.loadout.overwatchAxisY = THREAT_Y;
        f.loadout.overwatchLongRangeBand = false;
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
        assertTrue(distance >= MechWeapon.HEAVY_CANNON.range
                - OverwatchKillZone.DIRECT_FALLBACK_BAND_DEPTH);
        assertTrue(distance <= MechWeapon.HEAVY_CANNON.range,
                "fallback perch must let the unlimited heavy cannon fire");
    }

    private record Fixture(BattleSimulation sim, Squad squad, long sirocco,
                           MechLoadoutComponent loadout, long enemy) {}
}
