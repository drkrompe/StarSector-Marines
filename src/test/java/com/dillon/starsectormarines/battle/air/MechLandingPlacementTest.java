package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MechLandingPlacementTest {
    @Test
    void blockedLandingRetainsPayloadAndCreatesNoSquadThenRetriesWhenSpaceOpens() {
        NavigationGrid grid = new NavigationGrid(12, 8);
        for (int x = 1; x < 11; x++) grid.setWalkableFloor(x, 2);
        // A separate large room is nearby, but does not belong to this LZ.
        for (int y = 4; y < 7; y++) {
            for (int x = 1; x < 11; x++) grid.setWalkableFloor(x, y);
        }
        NavigationService navigation = new NavigationService(grid, new CellTopology(12, 8), false);
        UnitRosterService roster = new UnitRosterService(navigation.getUnitIndex(), null);
        ShuttleMission mission = new ShuttleMission(5.5f, 2.5f, 0, 0, 0, 0, 0, 1);
        mission.mechVariant = MechVariant.BULWARK;
        AirDeliveryContext context = new AirDeliveryContext(mission, ShuttleType.VALKYRIE,
                Faction.MARINE, navigation, roster, roster::spawn, null, null);
        assertFalse(MechSupportPayload.INSTANCE.tryDeploy(context));
        assertEquals(Squad.NO_SQUAD, mission.squadId);
        assertEquals(0, roster.liveCount());
        assertEquals(1, mission.marinesRemaining);
        // Widen this room rather than relocating its payload into the other room.
        for (int x = 1; x < 11; x++) grid.setWalkableFloor(x, 1);
        navigation.getZoneGraph().rebuild();
        assertTrue(MechSupportPayload.INSTANCE.tryDeploy(context));
        long unit = roster.denseArray()[0];
        assertEquals(2f, roster.world().y(unit));
        assertTrue(ManualTerrainMotion.canStand(grid, roster.world().x(unit), roster.world().y(unit),
                MechVariant.BULWARK.radius));
        assertEquals(1, roster.getSquad(mission.squadId).originalSize);
    }
}
