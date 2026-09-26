package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MechWreckPlacementTest {
    @Test
    void wreckLeavesEntireBodyEnvelopeOpenWhenNoSafeStepExists() {
        NavigationGrid grid = new NavigationGrid(10, 8);
        for (int y = 2; y <= 3; y++) {
            for (int x = 3; x <= 5; x++) grid.setWalkableFloor(x, y);
        }
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(10, 8), null);
        long mech = roster.spawn(new EntitySpec("trapped", Faction.MARINE, UnitType.HEAVY_MECH, 4, 3)
                .atPosition(4.5f, 3f));
        LongBucket nearby = new LongBucket();
        nearby.add(mech);
        AirframeFootprint.settleWreck(grid, new CellTopology(10, 8), roster.world(), nearby,
                4, 3, roster.identity(), roster::radius);
        assertEquals(4.5f, roster.world().x(mech));
        assertEquals(3f, roster.world().y(mech));
        assertTrue(ManualTerrainMotion.canStand(grid, 4.5f, 3f, MechVariant.BULWARK.radius));
    }

    @Test
    void wreckStepsMechAlongClearGroundToALegalHalfCellPosition() {
        NavigationGrid grid = new NavigationGrid(14, 8);
        for (int y = 2; y <= 3; y++) {
            for (int x = 1; x <= 12; x++) grid.setWalkableFloor(x, y);
        }
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(14, 8), null);
        long mech = roster.spawn(new EntitySpec("stepping", Faction.MARINE, UnitType.HEAVY_MECH, 6, 3)
                .atPosition(6.5f, 3f));
        LongBucket nearby = new LongBucket();
        nearby.add(mech);
        AirframeFootprint.settleWreck(grid, new CellTopology(14, 8), roster.world(), nearby,
                6, 3, roster.identity(), roster::radius);
        assertNotEquals(6.5f, roster.world().x(mech));
        assertEquals(3f, roster.world().y(mech));
        assertTrue(ManualTerrainMotion.canStand(grid, roster.world().x(mech), roster.world().y(mech),
                MechVariant.BULWARK.radius));
    }
}
