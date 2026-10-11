package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.tools.tilesetauthoring.GroundDamageReskinTemplate;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class GroundDamageTilesTest {
    @Test void materialPairsResolveOnlyOnDamagedFloorsAndPreserveThePerimeter() throws Exception {
        TileRegistry registry = TileRegistry.installed();
        CellTopology topology = new CellTopology(2, 2);
        var atlas = ImageIO.read(Path.of("mod/graphics/tilesets/colony-ground-damage.png").toFile());
        for (String material : GroundDamageReskinTemplate.MATERIALS) {
            topology.setSurface(0, 0, topology.surfaceIndex("colony." + material));
            topology.setTag(0, 0, CellTopology.Tag.GROUND_DAMAGED, false);
            assertNull(GroundDamageTiles.skin(registry, topology, 0, 0));
            topology.setTag(0, 0, CellTopology.Tag.GROUND_DAMAGED, true);
            var block = GroundDamageTiles.skin(registry, topology, 0, 0);
            assertEquals("colony." + material + "-damage", block.id);
            assertEquals(2, block.cells.length);
            for (int[] cell : block.cells) {
                int visible = 0;
                for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                    int alpha = atlas.getRGB(cell[0] * 64 + x, cell[1] * 64 + y) >>> 24;
                    if (alpha > 64) visible++;
                    if (x < 3 || y < 3 || x >= 61 || y >= 61)
                        assertTrue(alpha <= 4, material + " must preserve the existing tile seams");
                }
                assertTrue(visible > 70 && visible < 1800, "damage must be legible and localized");
            }
        }
        topology.setWall(0, 0, true);
        assertNull(GroundDamageTiles.skin(registry, topology, 0, 0));
        topology.setWall(0, 0, false);
        topology.setGroundKind(0, 0, CellTopology.GroundKind.RUBBLE);
        assertNull(GroundDamageTiles.skin(registry, topology, 0, 0));
        topology.setGroundKind(0, 0, CellTopology.GroundKind.INDOOR);
        topology.setSurface(0, 0, topology.surfaceIndex("urban.floor"));
        assertNull(GroundDamageTiles.skin(registry, topology, 0, 0), "unpaired art keeps its old rendering");
    }

    @Test void damagePairRejectsMissingAndTopologyDependentBlocks() throws Exception {
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(new JSONObject("{sheet:'a.png',cellPx:64,blocks:["
                + "{id:'floor',layout:'single',origin:[0,0],damageBlock:'damage'}]}"));
        assertThrows(IllegalStateException.class, registry::validateReferences);
        registry.ingestSheet(new JSONObject("{sheet:'b.png',cellPx:64,blocks:["
                + "{id:'damage',layout:'wall-faces-4x4',origin:[0,0]}]}"));
        assertThrows(IllegalStateException.class, registry::validateReferences);
    }
}
