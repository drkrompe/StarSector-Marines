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
        final NavigationGrid grid = new NavigationGrid(8, 8);
        final CellTopology topology = new CellTopology(8, 8);
        final DoodadService props = new DoodadService(grid);
        final NavigationService navigation;
        final MapEditor editor;
        final List<PropDamage.Effect> cues = new ArrayList<>();
        Ground() {
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                grid.setWalkableFloor(x, y); topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
            navigation = new NavigationService(grid, topology, false);
            editor = new MapEditor(navigation); editor.setDoodads(props);
            props.setPropEffectSink((x, y, effect, size) -> {
                assertEquals(1f, size);
                assertEquals(3.5f, x); assertEquals(3.5f, y); cues.add(effect);
            });
        }
        void prop(String id, boolean blocking, int turn, boolean mirror) {
            if (blocking) {
                grid.setWalkable(3, 3, false); grid.setSeeThrough(3, 3, true);
                topology.setFixture(3, 3, true);
            }
            props.addDoodad(new Doodad(3, 3, TileRegistry.installed().doodad(id), turn, mirror));
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
}
