package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MechSpawnPlacementTest {
    @Test
    void bulwarkUsesHalfCellCenterInAnEvenWidthCorridor() {
        NavigationGrid grid = corridor(2);
        assertFalse(ManualTerrainMotion.canStand(grid, 3.5f, 2.5f, MechVariant.BULWARK.radius));
        var point = MechSpawnPlacement.nearCell(grid, MechVariant.BULWARK.radius, 3, 2,
                (x, y) -> y >= 2 && y < 4, (x, y) -> true);
        assertNotNull(point);
        assertEquals(3f, point.y());
        assertTrue(ManualTerrainMotion.canStand(grid, point.x(), point.y(), MechVariant.BULWARK.radius));
    }

    @Test
    void narrowCorridorAdmitsSmallChassisButNeverRelocatesBulwarkElsewhere() {
        NavigationGrid grid = corridor(1);
        assertNull(MechSpawnPlacement.nearCell(grid, MechVariant.BULWARK.radius, 3, 2,
                (x, y) -> true, (x, y) -> true));
        assertNotNull(MechSpawnPlacement.nearCell(grid, MechVariant.SIROCCO.radius, 3, 2,
                (x, y) -> true, (x, y) -> true));
        assertNotNull(MechSpawnPlacement.nearCell(grid, MechVariant.HOUND.radius, 3, 2,
                (x, y) -> true, (x, y) -> true));
    }

    @Test
    void wholeBodyMustRemainInTheAuthoredDomainAndClearThinEdges() {
        NavigationGrid grid = corridor(2);
        assertNull(MechSpawnPlacement.nearCell(grid, 0.6f, 3, 2,
                (x, y) -> y >= 2 && y < 3, (x, y) -> true));
        grid.blockSharedEdge(3, 2, Direction.N);
        assertFalse(MechSpawnPlacement.canPlace(grid, 0.6f, 3.5f, 3f, (x, y) -> true));
    }

    @Test
    void exactSpawnSeedsPositionGaitAndSpatialIndexBeforeFirstTick() {
        UnitSpatialIndex index = new UnitSpatialIndex(16, 16);
        UnitRosterService roster = new UnitRosterService(index, null);
        EntitySpec spec = new EntitySpec("half cell", Faction.MARINE, UnitType.HEAVY_MECH, 2, 2)
                .mechVariant(MechVariant.BULWARK).atPosition(8f, 7f);
        long unit = roster.spawn(spec);
        assertEquals(8f, roster.world().x(unit));
        assertEquals(7f, roster.world().y(unit));
        assertEquals(8, spec.cellX);
        assertEquals(7, spec.cellY);
        MechGaitState gait = (MechGaitState) roster.entityWorld().getObject(unit,
                roster.components().MECH_GAIT_STATE, BattleComponents.MECH_GAIT_STATE_STATE);
        MechGaitState expected = MechGaitState.create(8f, 7f, 180f, MechVariant.BULWARK);
        assertEquals(expected.leftFootX(), gait.leftFootX());
        assertEquals(expected.leftFootY(), gait.leftFootY());
        assertEquals(expected.rightFootX(), gait.rightFootX());
        LongBucket found = new LongBucket();
        index.gather(8f, 7f, 0.1f, found);
        assertEquals(1, found.size);
        assertEquals(unit, found.ids[0]);
        assertFalse(MechSpawnPlacement.unoccupied(roster, 8.5f, 7f, 0.6f, 0L));
    }

    private static NavigationGrid corridor(int width) {
        NavigationGrid grid = new NavigationGrid(9, 7);
        for (int y = 2; y < 2 + width; y++) {
            for (int x = 1; x < 8; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
