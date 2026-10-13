package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.WaterUnderSealSequence;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.story.WaterUnderSealProgress;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.NorthWeir;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaterUnderSealProjectionTest {
    private static WaterUnderSealSequence sequence() {
        var reserve = IntStream.range(0,12).mapToObj(i -> new EntitySpec("Salt Knife " + i, Faction.DEFENDER, UnitType.MILITIA, 170, 90)).toList();
        return new WaterUnderSealSequence(3, 1, List.of(1,2), reserve, new StoryBeatLog());
    }
    private static void work(WaterUnderSealSequence sequence, float dt) {
        sequence.progress().advance(dt,true,true,true,true,true,false,false);
    }
    @Test void focusFollowsCargoThenSwitchesAndNamesTheActualBuildPosition() {
        var sequence = sequence();
        var cargo = MissionObjectiveFamilies.waterUnderSeal(sequence);
        assertEquals("weir-escort", cargo.focusMarkerId());
        assertTrue(cargo.markers().stream().anyMatch(m -> m.id().equals("weir-isolation") && m.x() == NorthWeir.ISOLATION_X));
        work(sequence, WaterUnderSealProgress.UNLOAD_SECONDS);
        assertEquals("weir-north", MissionObjectiveFamilies.waterUnderSeal(sequence).focusMarkerId());
        assertFalse(MissionObjectiveFamilies.waterUnderSeal(sequence).markers().stream().anyMatch(marker -> marker.id().equals("weir-escort")));
        work(sequence, WaterUnderSealProgress.RECONNECT_SECONDS);
        assertEquals("weir-south", MissionObjectiveFamilies.waterUnderSeal(sequence).focusMarkerId());
        work(sequence, WaterUnderSealProgress.RECONNECT_SECONDS);
        var build = MissionObjectiveFamilies.waterUnderSeal(sequence);
        assertEquals("weir-road-gun", build.focusMarkerId());
        assertEquals(sequence.buildX(), build.focus().x());
        assertEquals(sequence.buildY(), build.focus().y());
        assertTrue(build.blocker().contains("owned tender"));
        assertTrue(build.focus().radius() > 0);
    }
    @Test void roomOutlineIncludesOnlyActualHintedInteriorAndLocateHasLegalFloor() {
        var sequence = sequence(); work(sequence, WaterUnderSealProgress.UNLOAD_SECONDS);
        var grid = new NavigationGrid(NorthWeir.WIDTH, NorthWeir.HEIGHT);
        var topology = new CellTopology(NorthWeir.WIDTH, NorthWeir.HEIGHT);
        grid.setWalkableFloor(81,75); topology.setBuildingKindHint(81,75,BuildingKind.INDUSTRIAL);
        grid.setWalkableFloor(80,75); topology.setBuildingKindHint(80,75,BuildingKind.INDUSTRIAL);
        topology.setBuildingKindHint(79,75,BuildingKind.INDUSTRIAL); topology.setWall(79,75,true);
        grid.setWalkableFloor(78,75); // An exterior recess inside the enclosing rectangle.
        var picture = MissionObjectiveProjection.weirFootprints(MissionObjectiveFamilies.waterUnderSeal(sequence),topology,grid);
        assertEquals(2,picture.focus().footprint().size());
        assertTrue(picture.focus().footprint().stream().allMatch(cell -> NorthWeir.insideRoom(topology,cell.x(),cell.y(),true)));
        assertTrue(grid.isWalkable(picture.focus().x(),picture.focus().y()));
        assertEquals(81,picture.focus().x());
    }
}
