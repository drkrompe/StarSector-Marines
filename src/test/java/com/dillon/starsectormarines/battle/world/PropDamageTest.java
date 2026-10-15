package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.model.PropDamage;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PropDamageTest {
    private static final class Ground implements AutoCloseable {
        final NavigationGrid grid = new NavigationGrid(12, 12);
        final CellTopology topology = new CellTopology(12, 12);
        final DoodadService props = new DoodadService(grid);
        final NavigationService navigation;
        final MapEditor editor;
        final List<PropDamage.Effect> cues = new ArrayList<>();
        Ground() {
            for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) {
                grid.setWalkableFloor(x, y); topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
            navigation = new NavigationService(grid, topology, false);
            editor = new MapEditor(navigation); editor.setDoodads(props);
            props.setPropEffectSink((x, y, effect, size) -> {
                Doodad prop = props.getDoodads().get(0);
                assertEquals(Math.min(prop.footprintCellsX, prop.footprintCellsY), size);
                assertEquals(3f + prop.footprintCellsX / 2f, x);
                assertEquals(3f + prop.footprintCellsY / 2f, y); cues.add(effect);
            });
        }
        void prop(String id, boolean blocking, int turn, boolean mirror) {
            Doodad prop = new Doodad(3, 3, TileRegistry.installed().doodad(id), turn, mirror);
            if (blocking) for (int y = 3; y < 3 + prop.footprintCellsY; y++)
                for (int x = 3; x < 3 + prop.footprintCellsX; x++) {
                grid.setWalkable(x, y, false); grid.setSeeThrough(x, y, true);
                topology.setFixture(x, y, true);
            }
            props.addDoodad(prop);
        }
        public void close() { navigation.close(); }
    }

    @Test void equipmentSwapsOncePerStateAndReleasesBothKindsOfCoverWithoutMasonry() {
        for (String id : PropDamage.EQUIPMENT.keySet()) for (boolean blocks : new boolean[]{false, true})
            for (int turn = 0; turn < 4; turn++) try (Ground g = new Ground()) {
                g.prop(id, blocks, turn, true);
                assertFalse(g.editor.damageWall(3, 3, 0)); assertTrue(g.cues.isEmpty());
                assertFalse(g.editor.damageWall(3, 3, 20));
                assertEquals(id + "-damaged", g.props.getDoodads().get(0).definitionId());
                int cueCount = g.cues.size();
                assertFalse(g.editor.damageWall(3, 3, 20)); assertEquals(cueCount, g.cues.size());
                assertTrue(g.editor.damageWall(3, 3, 60));
                Doodad wreck = g.props.getDoodads().get(0);
                assertEquals(id + "-destroyed", wreck.definitionId());
                assertEquals(turn, wreck.quarterTurns); assertTrue(wreck.mirrored);
                assertTrue(g.grid.isWalkable(3, 3)); assertFalse(g.grid.isDoorway(3, 3));
                assertFalse(g.topology.isFixture(3, 3));
                assertEquals(CellTopology.GroundKind.GRASS, g.topology.getGroundKind(3, 3));
                assertEquals(0, g.props.getDoodadLevelOnCell(3, 3));
                assertEquals(0, g.props.getDoodadCoverAt(3, 3));
                assertEquals(0, g.grid.getCellCoverAtFacing(2, 3, NavigationGrid.FACING_E));
                cueCount = g.cues.size();
                assertFalse(g.editor.damageWall(3, 3, 500)); assertEquals(cueCount, g.cues.size());
                assertSame(wreck, g.props.getDoodads().get(0));
            }
    }

    @Test void directDestructionEmitsOnlyTheTerminalCueAndRepeatedBlastDoesNotReplayIt() {
        try (Ground g = new Ground()) {
            g.prop("doodad.industrial-generator", false, 0, false);
            g.editor.damageWallsInRadius(3.5f, 3.5f, 2, 500, null);
            assertEquals(List.of(PropDamage.Effect.FIRE), g.cues);
            g.editor.damageWallsInRadius(3.5f, 3.5f, 2, 500, null);
            assertEquals(List.of(PropDamage.Effect.FIRE), g.cues);
        }
    }

    @Test void blastHitsTheWholeRotatedBedOnceAndDebrisReleasesBothCells() {
        for (String id : List.of("doodad.residential-bed-h", "doodad.residential-bed-head-e",
                "doodad.residential-bed-v", "doodad.residential-bed-head-n"))
            for (int turn = 0; turn < 4; turn++) try (Ground g = new Ground()) {
                g.prop(id, true, turn, false);
                Doodad bed = g.props.getDoodads().get(0);
                g.editor.damageWallsInRadius(4, 4, 3, 60, null);
                assertEquals(id + "-damaged", g.props.getDoodads().get(0).definitionId(),
                        "A single blast cannot hit the shared damage pool twice");
                g.editor.damageWallsInRadius(4, 4, 3, 40, null);
                assertEquals(id + "-destroyed", g.props.getDoodads().get(0).definitionId());
                for (int y = 3; y < 3 + bed.footprintCellsY; y++)
                    for (int x = 3; x < 3 + bed.footprintCellsX; x++) {
                        assertTrue(g.grid.isWalkable(x, y));
                        assertFalse(g.topology.isFixture(x, y));
                        assertEquals(0, g.props.getDoodadLevelOnCell(x, y));
                        assertEquals(CellTopology.GroundKind.GRASS, g.topology.getGroundKind(x, y));
                    }
            }
    }

    @Test void overlappingStaticFixtureAndWalkableCoverKeepTheirOwnProfiles() {
        try (Ground g = new Ground()) {
            g.prop("doodad.industrial-generator", true, 0, false);
            g.prop("doodad.civic-server-rack", true, 0, false);
            g.editor.damageWall(3, 3, 100);
            assertFalse(g.grid.isWalkable(3, 3)); assertTrue(g.topology.isFixture(3, 3));
            assertEquals(3, g.grid.getCellCoverAtFacing(2, 3, NavigationGrid.FACING_E));
        }
        try (Ground g = new Ground()) {
            g.prop("doodad.industrial-generator", false, 0, false);
            g.props.addStaticCover(3, 3, 1, .2f);
            g.editor.damageWall(3, 3, 100);
            assertEquals(1, g.props.getDoodadLevelOnCell(3, 3));
        }
    }

    @Test void collapsedRackBringsDownAllContainedStockAndFrameInEveryPose() {
        for(int turn=0;turn<4;turn++)try(Ground g=new Ground()) {
            g.prop("main-stores.rack",true,turn,false);
            Doodad rack=g.props.getDoodads().get(0);
            g.props.addDoodad(new Doodad(3,3,TileRegistry.installed().doodad("main-stores.cases"),turn));
            g.props.addDoodad(new Doodad(3+rack.footprintCellsX-2,3+rack.footprintCellsY-2,
                    TileRegistry.installed().doodad("main-stores.cartons"),turn));
            g.props.addDoodad(new Doodad(3,3,TileRegistry.installed().doodad("main-stores.frame"),turn));
            g.props.addDoodad(new Doodad(0,0,TileRegistry.installed().doodad("main-stores.cases"),turn));
            g.editor.damageWall(3,3,100);
            for(Doodad d:g.props.getDoodads().subList(0,4))assertTrue(d.definitionId().endsWith("-destroyed"));
            assertEquals("main-stores.cases",g.props.getDoodads().get(4).definitionId());
            for(int y=3;y<3+rack.footprintCellsY;y++)for(int x=3;x<3+rack.footprintCellsX;x++){
                assertTrue(g.grid.isWalkable(x,y));assertFalse(g.topology.isFixture(x,y));
            }
        }
    }

    @Test void beddingRegisteredBeforeItsBedCollapsesWithTheBase() {
        try(Ground g=new Ground()) {
            g.props.addDoodad(new Doodad(3,3,TileRegistry.installed().doodad("sickbay-clinic.linen-low"),0));
            g.prop("sickbay-clinic.bed",true,0,false);
            g.props.setPropEffectSink((x,y,e,size)->{});
            g.editor.damageWall(3,4,100);
            assertTrue(g.props.getDoodads().stream().allMatch(d->d.definitionId().endsWith("-destroyed")));
            assertTrue(g.grid.isWalkable(3,3));assertTrue(g.grid.isWalkable(3,4));
        }
    }

    @Test void decorativeLayersAndStoolsChangeStateWithoutCreatingOrClearingObstruction() {
        for(String id:PropDamage.DECORATIONS)try(Ground g=new Ground()) {
            g.prop(id,false,0,false);
            assertFalse(g.editor.damageWall(3,3,35));
            assertEquals(id+"-damaged",g.props.getDoodads().get(0).definitionId());
            assertTrue(g.editor.damageWall(3,3,65));
            assertEquals(id+"-destroyed",g.props.getDoodads().get(0).definitionId());
            assertTrue(g.grid.isWalkable(3,3));assertFalse(g.grid.isDoorway(3,3));
            assertEquals(0,g.props.getDoodadCoverAt(3,3));assertTrue(g.cues.isEmpty());
        }
    }
}
